import { distance } from '../common/geo.js'
import type { LatLng, Phase, TripSnapshot } from '../common/types.js'
import type { StopRecord, TripRecord } from './trip.model.js'

export const NEAR_SECONDS = 120
/** Time allowed at every stop the car still has to visit before mine */
export const DWELL_SECONDS = 60
/** Until the car is this far from where it started, it has "just departed" */
const DEPARTED_METERS = 150

export type PhaseInput = {
  myStop: Pick<StopRecord, 'order'>
  firstStopOrder: number
  /** null before the first location */
  metersFromStart: number | null
  myEtaSeconds: number
  currentStopOrder: number
  arrivedOrders: number[]
  completedOrders: number[]
}

/**
 * Phase of one passenger's stop. Pure: everything comes from what the car reported.
 * Arrival and completion are the car app's calls (stopArrived / stopCompleted); the ETA only decides `near`.
 */
export function computePhase(input: PhaseInput): Phase {
  const mine = input.myStop.order
  const current = input.currentStopOrder

  if (input.completedOrders.includes(mine)) return 'boarded'
  // The car is already heading past my stop: treat it as boarded even if the completion was missed
  if (current > mine) return 'boarded'
  if (input.arrivedOrders.includes(mine)) return 'arrived'

  if (current === mine) {
    if (input.myEtaSeconds <= NEAR_SECONDS && input.metersFromStart !== null) return 'near'
  } else if (input.arrivedOrders.includes(current) && !input.completedOrders.includes(current)) {
    return 'atEarlierStop'
  }

  const justStarted =
    current === input.firstStopOrder &&
    (input.metersFromStart === null || input.metersFromStart < DEPARTED_METERS)
  if (justStarted) return 'departed'
  return current === mine ? 'toMe' : 'toEarlierStop'
}

export type EtaInput = {
  /** In visiting order, with each leg's planned seconds */
  stops: Pick<StopRecord, 'order' | 'legSeconds'>[]
  currentStopOrder: number
  /** Navi ETA to the current stop. Null before the first location: use the planned leg instead. */
  currentLegEtaSeconds: number | null
  /** When the car arrived at the current stop, if it is standing there */
  currentArrivedAt: number | null
  now: number
}

/**
 * Seconds until the car reaches `targetOrder`.
 * The navi only knows the current leg; later legs come from the planned route,
 * plus DWELL_SECONDS for every stop still to visit before the target (less what has already been waited).
 */
export function etaToStop(input: EtaInput, targetOrder: number): number {
  const { stops, currentStopOrder } = input
  if (targetOrder < currentStopOrder) return 0
  const cur = stops.findIndex((s) => s.order === currentStopOrder)
  const target = stops.findIndex((s) => s.order === targetOrder)
  if (cur < 0 || target < 0) return 0

  const currentLeg = input.currentArrivedAt !== null ? 0 : (input.currentLegEtaSeconds ?? stops[cur].legSeconds)
  if (target === cur) return Math.max(0, Math.round(currentLeg))

  const waited = input.currentArrivedAt !== null ? (input.now - input.currentArrivedAt) / 1000 : 0
  let eta = currentLeg + Math.max(0, DWELL_SECONDS - waited) // the current stop
  for (let i = cur + 1; i <= target; i++) {
    eta += stops[i].legSeconds
    if (i < target) eta += DWELL_SECONDS
  }
  return Math.max(0, Math.round(eta))
}

/** ETA inputs from a trip's progress, shared by the passenger snapshot and the driver view. */
export function etaInputFor(trip: TripRecord, now: number): EtaInput {
  const p = trip.progress
  const currentArrived =
    p.currentStopOrder in p.arrivedAt && !p.completions.some((c) => c.order === p.currentStopOrder)
  return {
    stops: trip.stops,
    currentStopOrder: p.currentStopOrder,
    currentLegEtaSeconds: p.currentLegEtaSeconds,
    currentArrivedAt: currentArrived ? p.arrivedAt[p.currentStopOrder] : null,
    now,
  }
}

/** The TripSnapshot one passenger (identified by their stop) receives. */
export function buildSnapshot(trip: TripRecord, myStop: StopRecord, now = Date.now()): TripSnapshot {
  const p = trip.progress
  const completedOrders = p.completions.map((c) => c.order)
  const myEtaSeconds = etaToStop(etaInputFor(trip, now), myStop.order)

  const start: LatLng = trip.route[0]
  const phase = computePhase({
    myStop,
    firstStopOrder: trip.stops[0].order,
    metersFromStart: p.location ? distance(start, p.location) : null,
    myEtaSeconds,
    currentStopOrder: p.currentStopOrder,
    arrivedOrders: Object.keys(p.arrivedAt).map(Number),
    completedOrders,
  })
  const car = p.location
    ? { lat: p.location.lat, lng: p.location.lng, heading: p.location.heading }
    : { lat: start.lat, lng: start.lng, heading: 0 }
  const currentStop = trip.stops.find((s) => s.order === p.currentStopOrder)!

  return {
    phase,
    car,
    traveledIndex: p.traveledIndex,
    etaSeconds: phase === 'arrived' || phase === 'boarded' ? 0 : myEtaSeconds,
    updatedAt: p.location?.ts ?? trip.createdAt,
    currentStopOrder: p.currentStopOrder,
    currentStop: { order: currentStop.order, kind: currentStop.kind, label: currentStop.label },
  }
}
