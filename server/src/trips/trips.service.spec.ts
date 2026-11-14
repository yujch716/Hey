import { BadRequestException, GoneException, NotFoundException, UnauthorizedException } from '@nestjs/common'
import type { ConfigService } from '@nestjs/config'

import type { KakaoDirectionsClient } from '../directions/kakao-directions.client.js'
import { RoutePlanner } from '../directions/route-planner.js'
import type { CreateTripDto, LocationDto } from './dto/trip.dto.js'
import { InMemoryTripRepository } from './in-memory-trip.repository.js'
import { DWELL_SECONDS } from './phase.js'
import { TripsService, type TripUpdate } from './trips.service.js'

const origin = { lat: 37.4905, lng: 127.012 }
const cafe = { lat: 37.4945, lng: 127.0245 }
const me = { lat: 37.5005, lng: 127.03 }
const next = { lat: 37.508, lng: 127.027 }

// What the car app sends: no route, the server plans it
const createDto: CreateTripDto = {
  vehicle: { model: '아반떼', colorName: '흰색', colorHex: '#f4f5f7', bodyType: 'sedan', plate: '123가 4567' },
  origin,
  stops: [
    { order: 2, kind: 'pickup', label: '픽업', position: me },
    { order: 1, kind: 'waypoint', label: '카페', position: cafe },
    { order: 3, kind: 'pickup', label: '픽업', position: next },
  ],
}

// Kakao stand-in: legs of 300 s, 200 s, 100 s with a couple of road vertexes in between
const fakeKakao: KakaoDirectionsClient = {
  directions: async (points) => ({
    polyline: [points[0], { lat: 37.492, lng: 127.016 }, points[1], points[2], { lat: 37.504, lng: 127.0295 }, points[3]],
    legDurations: [300, 200, 100],
    legEndIndices: [2, 3, 5],
  }),
}

function setup(kakao: KakaoDirectionsClient | null = fakeKakao) {
  const config = { get: (key: string, fallback: string) => (key === 'WEB_BASE_URL' ? 'https://nawa.test/' : fallback) }
  const service = new TripsService(new InMemoryTripRepository(), new RoutePlanner(kakao), config as unknown as ConfigService)
  const updates: TripUpdate[] = []
  service.updates$.subscribe((u) => updates.push(u))
  return { service, updates }
}

const at = (p: { lat: number; lng: number }, currentStopOrder: number, currentLegEtaSeconds: number): LocationDto => ({
  ...p,
  heading: 0,
  currentLegEtaSeconds,
  currentStopOrder,
  ts: 1_700_000_000_000,
})

describe('TripsService', () => {
  it('issues one share link per pickup stop', async () => {
    const { service } = setup()
    const res = await service.createTrip(createDto)

    expect(res.shares.map((s) => s.stopId)).toEqual(['s2', 's3'])
    expect(res.shares[0].url).toBe(`https://nawa.test/t/${res.shares[0].token}`)
    expect(res.shares[0].token).toMatch(/^[A-Za-z0-9_-]{22}$/)
    expect(res.driverToken).not.toBe(res.shares[0].token)
  })

  it('plans the route at creation and places each stop where its leg ends', async () => {
    const { service } = setup()
    const { shares } = await service.createTrip(createDto)
    const view = await service.getShareView(shares[0].token)

    expect(view.route).toHaveLength(6)
    expect(view.stops.map((s) => [s.order, s.routeIndex, s.isMine])).toEqual([
      [1, 2, false],
      [2, 3, true],
      [3, 5, false],
    ])
    expect(view.snapshot.phase).toBe('departed')
    // Before any location: planned legs + one dwell at the cafe
    expect(view.snapshot.etaSeconds).toBe(300 + DWELL_SECONDS + 200)
  })

  it('works without Kakao, joining the stops with straight lines', async () => {
    const { service } = setup(null)
    const { shares } = await service.createTrip(createDto)
    const view = await service.getShareView(shares[0].token)

    expect(view.route).toEqual([origin, cafe, me, next])
    expect(view.stops.map((s) => s.routeIndex)).toEqual([1, 2, 3])
    expect(view.snapshot.etaSeconds).toBeGreaterThan(DWELL_SECONDS)
  })

  it('keeps a route the client sends, but still needs routeIndex for it', async () => {
    const { service } = setup()
    const route = [origin, cafe, me, next]
    await expect(service.createTrip({ ...createDto, route })).rejects.toBeInstanceOf(BadRequestException)

    const { shares } = await service.createTrip({
      ...createDto,
      route,
      stops: createDto.stops.map((s) => ({ ...s, routeIndex: s.order })),
    })
    expect((await service.getShareView(shares[0].token)).route).toEqual(route)
  })

  it('rejects a wrong driver token', async () => {
    const { service } = setup()
    const { tripId } = await service.createTrip(createDto)
    await expect(service.authorizeDriver(tripId, 'nope')).rejects.toBeInstanceOf(UnauthorizedException)
    await expect(service.authorizeDriver('missing', 'nope')).rejects.toBeInstanceOf(NotFoundException)
  })

  it('derives later ETAs from the navi ETA plus planned legs and dwells', async () => {
    const { service } = setup()
    const { tripId, shares } = await service.createTrip(createDto)
    await service.updateLocation(tripId, at({ lat: 37.493, lng: 127.02 }, 1, 120))
    const mine = (await service.getShareView(shares[0].token)).snapshot
    const later = (await service.getShareView(shares[1].token)).snapshot

    expect(mine).toMatchObject({
      phase: 'toEarlierStop',
      etaSeconds: 120 + DWELL_SECONDS + 200,
      currentStop: { order: 1, kind: 'waypoint', label: '카페' },
      updatedAt: 1_700_000_000_000,
    })
    expect(later.etaSeconds).toBe(120 + DWELL_SECONDS + 200 + DWELL_SECONDS + 100)
  })

  it('walks one stop at a time: arrived → completed → next stop', async () => {
    const { service } = setup()
    const { tripId, shares } = await service.createTrip(createDto)
    const phaseOf = async (i: number) => (await service.getShareView(shares[i].token)).snapshot.phase

    await service.handleEvent(tripId, { type: 'stopArrived', stopOrder: 1 })
    expect(await phaseOf(0)).toBe('atEarlierStop')

    await service.handleEvent(tripId, { type: 'stopCompleted', stopOrder: 1, by: 'departed' })
    const view = await service.getShareView(shares[0].token)
    expect(view.snapshot.currentStopOrder).toBe(2)
    // The navi hasn't routed to stop 2 yet: the planned leg stands in
    expect(view.snapshot.etaSeconds).toBe(200)

    await service.handleEvent(tripId, { type: 'stopArrived', stopOrder: 2 })
    expect(await phaseOf(0)).toBe('arrived')
    expect(await phaseOf(1)).toBe('atEarlierStop')
  })

  it('expires the share on stopCompleted, after one last boarded snapshot', async () => {
    const { service, updates } = setup()
    const { tripId, shares } = await service.createTrip(createDto)
    await service.handleEvent(tripId, { type: 'stopCompleted', stopOrder: 1, by: 'button' })
    await service.handleEvent(tripId, { type: 'stopCompleted', stopOrder: 2, by: 'door' })

    const last = updates.at(-1)!
    expect(last.expiredTokens).toEqual([shares[0].token])
    expect(service.snapshotsFor(last.trip).map((s) => s.snapshot.phase)).toEqual(['boarded', 'toMe'])
    expect(last.trip.progress.completions.map((c) => [c.order, c.by])).toEqual([
      [1, 'button'],
      [2, 'door'],
    ])
    await expect(service.getShareView(shares[0].token)).rejects.toBeInstanceOf(GoneException)
    await expect(service.getShareView(shares[1].token)).resolves.toBeDefined()
  })

  it('needs `by` on stopCompleted', async () => {
    const { service } = setup()
    const { tripId } = await service.createTrip(createDto)
    await expect(service.handleEvent(tripId, { type: 'stopCompleted', stopOrder: 1 })).rejects.toThrow('needs by')
  })

  it('completes a stop the car drove past if stopCompleted was lost', async () => {
    const { service, updates } = setup()
    const { tripId, shares } = await service.createTrip(createDto)
    await service.updateLocation(tripId, at(me, 3, 60))

    const last = updates.at(-1)!
    expect(last.expiredTokens).toEqual([shares[0].token])
    expect(last.trip.progress.completions.map((c) => [c.order, c.by])).toEqual([
      [1, 'departed'],
      [2, 'departed'],
    ])
  })

  it('expires every share when the trip ends and refuses further updates', async () => {
    const { service } = setup()
    const { tripId, shares } = await service.createTrip(createDto)
    await service.handleEvent(tripId, { type: 'tripEnded' })

    await expect(service.getShareView(shares[0].token)).rejects.toBeInstanceOf(GoneException)
    await expect(service.handleEvent(tripId, { type: 'tripEnded' })).rejects.toThrow('trip already ended')
  })

  it('applies concurrent updates in order without losing any', async () => {
    const { service } = setup()
    const { tripId, shares } = await service.createTrip(createDto)
    await Promise.all([
      service.handleEvent(tripId, { type: 'stopArrived', stopOrder: 1 }),
      service.handleEvent(tripId, { type: 'stopCompleted', stopOrder: 1, by: 'departed' }),
      service.handleEvent(tripId, { type: 'stopArrived', stopOrder: 2 }),
    ])
    expect((await service.getShareView(shares[0].token)).snapshot.phase).toBe('arrived')
  })

  it('rejects a location whose currentStopOrder is not a stop', async () => {
    const { service } = setup()
    const { tripId } = await service.createTrip(createDto)
    await expect(service.updateLocation(tripId, at(origin, 9, 0))).rejects.toThrow('currentStopOrder does not match a stop')
  })

  it('forwards passenger replies with their stop order', async () => {
    const { service } = setup()
    const { tripId, shares } = await service.createTrip(createDto)
    const replies: unknown[] = []
    service.replies$.subscribe((r) => replies.push(r))
    await service.reply(shares[0].token, { type: 'late' })

    expect(replies).toEqual([{ tripId, stopOrder: 2, type: 'late' }])
  })

  it('forwards shared passenger locations, and their end, to the driver', async () => {
    const { service } = setup()
    const { tripId, shares } = await service.createTrip(createDto)
    const seen: unknown[] = []
    service.passengerLocations$.subscribe((l) => seen.push(l))
    await service.shareLocation(shares[0].token, { lat: 37.5, lng: 127.03 })
    await service.shareLocation(shares[0].token, null)

    expect(seen).toEqual([
      { tripId, stopOrder: 2, position: { lat: 37.5, lng: 127.03 } },
      { tripId, stopOrder: 2, position: null },
    ])
  })

  describe('driver view', () => {
    it('POST /trips returns the planned route and each stop with its planned ETA', async () => {
      const { service } = setup()
      const res = await service.createTrip(createDto)

      expect(res.route).toHaveLength(6)
      expect(res.stops.map((s) => [s.order, s.routeIndex, s.legSeconds, s.status, s.etaSeconds])).toEqual([
        [1, 2, 300, 'current', 300],
        [2, 3, 200, 'upcoming', 300 + DWELL_SECONDS + 200],
        [3, 5, 100, 'upcoming', 300 + DWELL_SECONDS + 200 + DWELL_SECONDS + 100],
      ])
    })

    it('GET /trips/:tripId follows progress: statuses, how stops completed, live ETAs', async () => {
      const { service } = setup()
      const { tripId } = await service.createTrip(createDto)
      await service.handleEvent(tripId, { type: 'stopArrived', stopOrder: 1 })
      await service.handleEvent(tripId, { type: 'stopCompleted', stopOrder: 1, by: 'button' })
      await service.updateLocation(tripId, at({ lat: 37.497, lng: 127.027 }, 2, 90))
      const view = await service.getDriverView(tripId)

      expect(view.car).toMatchObject({ lat: 37.497, lng: 127.027 })
      expect(view.currentStopOrder).toBe(2)
      expect(view.stops.map((s) => [s.order, s.status, s.etaSeconds, s.completedBy])).toEqual([
        [1, 'completed', 0, 'button'],
        [2, 'current', 90, undefined],
        [3, 'upcoming', 90 + DWELL_SECONDS + 100, undefined],
      ])

      await service.handleEvent(tripId, { type: 'stopArrived', stopOrder: 2 })
      expect((await service.getDriverView(tripId)).stops[1]).toMatchObject({ status: 'arrived', etaSeconds: 0 })
    })

    it('shows a passenger position while they share it, and drops it when they stop', async () => {
      const { service } = setup()
      const { tripId, shares } = await service.createTrip(createDto)
      await service.shareLocation(shares[1].token, { lat: 37.5081, lng: 127.0272 })

      const sharing = await service.getDriverView(tripId)
      expect(sharing.stops[2].passengerLocation).toMatchObject({ lat: 37.5081, lng: 127.0272 })
      expect(sharing.stops[1].passengerLocation).toBeUndefined()

      await service.shareLocation(shares[1].token, null)
      expect((await service.getDriverView(tripId)).stops[2].passengerLocation).toBeUndefined()
    })

    it('forgets a passenger position once their stop completes', async () => {
      const { service } = setup()
      const { tripId, shares } = await service.createTrip(createDto)
      await service.shareLocation(shares[0].token, me)
      await service.handleEvent(tripId, { type: 'stopCompleted', stopOrder: 2, by: 'door' })

      const trip = (await service.getDriverView(tripId)).stops[1]
      expect(trip.passengerLocation).toBeUndefined()
      await expect(service.shareLocation(shares[0].token, me)).rejects.toBeInstanceOf(GoneException)
    })

    it('a shared location does not lose a concurrent car update', async () => {
      const { service } = setup()
      const { tripId, shares } = await service.createTrip(createDto)
      await Promise.all([
        service.shareLocation(shares[0].token, me),
        service.handleEvent(tripId, { type: 'stopArrived', stopOrder: 1 }),
      ])
      const view = await service.getDriverView(tripId)
      expect(view.stops[0].status).toBe('arrived')
      expect(view.stops[1].passengerLocation).toBeDefined()
    })
  })
})
