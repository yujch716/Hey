import {
  BadRequestException,
  ConflictException,
  GoneException,
  Inject,
  Injectable,
  NotFoundException,
  UnauthorizedException,
} from '@nestjs/common'
import { ConfigService } from '@nestjs/config'
import { Subject } from 'rxjs'

import { traveledIndexOf } from '../common/geo.js'
import { newId, newToken, tokensEqual } from '../common/tokens.js'
import type { LatLng, QuickReply, Stop, TripSnapshot } from '../common/types.js'
import { RoutePlanner } from '../directions/route-planner.js'
import type { ReplyDto } from './dto/reply.dto.js'
import type { CreateTripDto, LocationDto, TripEventDto } from './dto/trip.dto.js'
import { driverView } from './driver-view.js'
import { buildSnapshot } from './phase.js'
import type { CompletedBy, ShareRecord, StopRecord, TripRecord } from './trip.model.js'
import { TRIP_REPOSITORY, type TripRepository } from './trip.repository.js'

/** Emitted after every location or event so the gateway can push fresh snapshots. */
export type TripUpdate = {
  trip: TripRecord
  /** Shares that expired with this update. They get one last snapshot, then are disconnected. */
  expiredTokens: string[]
}

export type PassengerReply = {
  tripId: string
  stopOrder: number
  type: QuickReply
  position?: LatLng
}

/** A passenger's shared position for the driver. `position: null` means they stopped sharing. */
export type PassengerLocation = {
  tripId: string
  stopOrder: number
  position: LatLng | null
}

@Injectable()
export class TripsService {
  readonly updates$ = new Subject<TripUpdate>()
  readonly replies$ = new Subject<PassengerReply>()
  readonly passengerLocations$ = new Subject<PassengerLocation>()

  private readonly webBaseUrl: string
  // Location and events for one trip arrive concurrently; apply them one at a time
  private readonly locks = new Map<string, Promise<unknown>>()

  constructor(
    @Inject(TRIP_REPOSITORY) private readonly repo: TripRepository,
    @Inject(RoutePlanner) private readonly planner: RoutePlanner,
    @Inject(ConfigService) config: ConfigService,
  ) {
    this.webBaseUrl = config.get<string>('WEB_BASE_URL', 'http://localhost:5173').replace(/\/$/, '')
  }

  // ── Driver ──────────────────────────────────────────────

  async createTrip(dto: CreateTripDto) {
    const orders = dto.stops.map((s) => s.order)
    if (new Set(orders).size !== orders.length) throw new BadRequestException('stop orders must be unique')
    const sorted = [...dto.stops].sort((a, b) => a.order - b.order)
    if (dto.route) {
      for (const s of sorted) {
        if (s.routeIndex === undefined || s.routeIndex >= dto.route.length) {
          throw new BadRequestException(`stop ${s.order}: routeIndex must point into the given route`)
        }
      }
    }

    // One planned route through every stop in order. The navi only ever gets the next stop.
    const origin = { lat: dto.origin.lat, lng: dto.origin.lng }
    const planned = await this.planner.plan([origin, ...sorted.map((s) => s.position)])

    const stops: StopRecord[] = sorted.map((s, i) => ({
      id: `s${s.order}`,
      order: s.order,
      kind: s.kind,
      label: s.label,
      position: { lat: s.position.lat, lng: s.position.lng },
      routeIndex: dto.route ? s.routeIndex! : planned.legEndIndices[i],
      legSeconds: planned.legDurations[i],
    }))
    const shares: ShareRecord[] = stops
      .filter((s) => s.kind === 'pickup')
      .map((s) => ({ token: newToken(), stopId: s.id, expired: false, passengerLocation: null }))

    const trip: TripRecord = {
      id: newId(),
      driverToken: newToken(),
      createdAt: Date.now(),
      vehicle: { ...dto.vehicle },
      route: dto.route ? dto.route.map((p) => ({ lat: p.lat, lng: p.lng })) : planned.polyline,
      routeSource: dto.route ? 'client' : planned.source,
      stops,
      shares,
      progress: {
        location: null,
        traveledIndex: 0,
        currentStopOrder: stops[0].order,
        currentLegEtaSeconds: null,
        arrivedAt: {},
        completions: [],
        ended: false,
      },
    }
    await this.repo.create(trip)

    // The car app draws its map from this; stops carry their planned ETA
    const { route, stops: plannedStops } = driverView(trip)
    return {
      tripId: trip.id,
      driverToken: trip.driverToken,
      shares: shares.map((s) => ({ stopId: s.stopId, token: s.token, url: `${this.webBaseUrl}/t/${s.token}` })),
      route,
      stops: plannedStops,
    }
  }

  /** GET /trips/:tripId for the car app's map. Call after authorizeDriver. */
  async getDriverView(tripId: string) {
    const trip = await this.repo.findById(tripId)
    if (!trip) throw new NotFoundException('trip not found')
    return driverView(trip)
  }

  /** Throws unless `driverToken` belongs to `tripId`. */
  async authorizeDriver(tripId: string, driverToken: string | undefined) {
    const trip = await this.repo.findById(tripId)
    if (!trip) throw new NotFoundException('trip not found')
    if (!driverToken || !tokensEqual(trip.driverToken, driverToken)) throw new UnauthorizedException()
    return trip
  }

  updateLocation(tripId: string, dto: LocationDto) {
    return this.mutate(tripId, (trip) => {
      if (!trip.stops.some((s) => s.order === dto.currentStopOrder)) {
        throw new BadRequestException('currentStopOrder does not match a stop')
      }
      const p = trip.progress
      const position = { lat: dto.lat, lng: dto.lng }
      p.location = { ...position, heading: dto.heading, ts: dto.ts }
      p.traveledIndex = traveledIndexOf(trip.route, position, p.traveledIndex)
      p.currentLegEtaSeconds = dto.currentLegEtaSeconds
      p.currentStopOrder = dto.currentStopOrder
      return []
    })
  }

  handleEvent(tripId: string, dto: TripEventDto) {
    return this.mutate(tripId, (trip) => {
      const p = trip.progress
      if (dto.type === 'tripEnded') {
        p.ended = true
        return expire(trip.shares)
      }

      const stop = trip.stops.find((s) => s.order === dto.stopOrder)
      if (!stop) throw new BadRequestException('stopOrder does not match a stop')

      switch (dto.type) {
        case 'stopArrived':
          p.arrivedAt[stop.order] ??= Date.now()
          return []
        case 'stopCompleted':
          if (!dto.by) throw new BadRequestException('stopCompleted needs by: door | button | departed')
          return complete(trip, stop, dto.by)
      }
    })
  }

  // ── Passenger ───────────────────────────────────────────

  /** The trip and stop behind a live share token. 404 if unknown, 410 if expired. */
  async resolveShare(token: string) {
    const trip = await this.repo.findByShareToken(token)
    const share = trip?.shares.find((s) => tokensEqual(s.token, token))
    if (!trip || !share) throw new NotFoundException('share not found')
    if (share.expired) throw new GoneException('share expired')
    const stop = trip.stops.find((s) => s.id === share.stopId)!
    return { trip, stop }
  }

  async getShareView(token: string) {
    const { trip, stop } = await this.resolveShare(token)
    const stops: Stop[] = trip.stops.map((s) => ({ ...s, isMine: s.id === stop.id }))
    return { vehicle: trip.vehicle, route: trip.route, stops, snapshot: buildSnapshot(trip, stop) }
  }

  async reply(token: string, dto: ReplyDto) {
    const { trip, stop } = await this.resolveShare(token)
    this.replies$.next({
      tripId: trip.id,
      stopOrder: stop.order,
      type: dto.type,
      ...(dto.position && { position: { lat: dto.position.lat, lng: dto.position.lng } }),
    })
  }

  /** Keeps the last shared position for GET /trips/:tripId and forwards it to the driver live. */
  async shareLocation(token: string, position: LatLng | null) {
    const { trip: found, stop } = await this.resolveShare(token)
    await this.withLock(found.id, async () => {
      // Re-read under the lock so a concurrent location or event isn't overwritten
      const trip = await this.repo.findById(found.id)
      const share = trip?.shares.find((s) => s.token === token)
      if (!trip || !share) throw new NotFoundException('share not found')
      if (share.expired) throw new GoneException('share expired')
      share.passengerLocation = position && { lat: position.lat, lng: position.lng, at: Date.now() }
      await this.repo.save(trip)
    })
    this.passengerLocations$.next({
      tripId: found.id,
      stopOrder: stop.order,
      position: position && { lat: position.lat, lng: position.lng },
    })
  }

  /** Current snapshot for every share of the trip, keyed by token. */
  snapshotsFor(trip: TripRecord): { token: string; snapshot: TripSnapshot }[] {
    return trip.shares.map((share) => ({
      token: share.token,
      snapshot: buildSnapshot(trip, trip.stops.find((s) => s.id === share.stopId)!),
    }))
  }

  // ── Internals ───────────────────────────────────────────

  /** Load, change, save and broadcast one trip under its lock. `change` returns newly expired tokens. */
  private mutate(tripId: string, change: (trip: TripRecord) => string[]) {
    return this.withLock(tripId, async () => {
      const trip = await this.repo.findById(tripId)
      if (!trip) throw new NotFoundException('trip not found')
      if (trip.progress.ended) throw new ConflictException('trip already ended')
      const expiredTokens = [...change(trip), ...completePassedStops(trip)]
      await this.repo.save(trip)
      this.updates$.next({ trip, expiredTokens })
    })
  }

  private withLock<T>(key: string, fn: () => Promise<T>): Promise<T> {
    const prev = this.locks.get(key) ?? Promise.resolve()
    const next = prev.catch(() => undefined).then(fn)
    this.locks.set(key, next)
    void next
      .catch(() => undefined)
      .finally(() => {
        if (this.locks.get(key) === next) this.locks.delete(key)
      })
    return next
  }
}

/**
 * A stop is done: the passenger boarded (pickup) or the errand is finished (waypoint).
 * The car then heads to the next stop, even before the next location fix says so.
 * Returns the share tokens this expires.
 */
function complete(trip: TripRecord, stop: StopRecord, by: CompletedBy) {
  const p = trip.progress
  if (!p.completions.some((c) => c.order === stop.order)) p.completions.push({ order: stop.order, by, at: Date.now() })
  if (p.currentStopOrder === stop.order) {
    const next = trip.stops.find((s) => s.order > stop.order)
    if (next) {
      p.currentStopOrder = next.order
      p.currentLegEtaSeconds = null // the navi hasn't routed to the next stop yet; fall back to the plan
    }
  }
  return expire(trip.shares.filter((s) => s.stopId === stop.id))
}

/**
 * If the car is already heading past a stop that never got stopCompleted (a lost event),
 * complete it now so the passenger isn't stuck on "arrived".
 */
function completePassedStops(trip: TripRecord) {
  const p = trip.progress
  const done = new Set(p.completions.map((c) => c.order))
  return trip.stops
    .filter((s) => s.order < p.currentStopOrder && !done.has(s.order))
    .flatMap((s) => complete(trip, s, 'departed'))
}

function expire(shares: ShareRecord[]) {
  const tokens: string[] = []
  for (const s of shares) {
    if (s.expired) continue
    s.expired = true
    s.passengerLocation = null
    tokens.push(s.token)
  }
  return tokens
}
