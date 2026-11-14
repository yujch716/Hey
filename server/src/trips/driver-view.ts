import type { LatLng, StopKind } from '../common/types.js'
import { etaInputFor, etaToStop } from './phase.js'
import type { CompletedBy, TripRecord } from './trip.model.js'

export type DriverStopStatus = 'upcoming' | 'current' | 'arrived' | 'completed'

export type DriverStop = {
  order: number
  kind: StopKind
  label: string
  position: LatLng
  routeIndex: number
  /** Planned driving time from the previous stop (or the start) */
  legSeconds: number
  status: DriverStopStatus
  /** Seconds until the car gets there; 0 once arrived or completed. At creation this is the planned ETA. */
  etaSeconds: number
  completedBy?: CompletedBy
  /** Pickups only, while that passenger is sharing their position */
  passengerLocation?: LatLng & { at: number }
}

/** Everything the car app's map needs. Pure. */
export function driverView(trip: TripRecord, now = Date.now()) {
  const p = trip.progress
  const eta = etaInputFor(trip, now)

  const stops: DriverStop[] = trip.stops.map((s) => {
    const completion = p.completions.find((c) => c.order === s.order)
    const arrived = s.order in p.arrivedAt
    const status: DriverStopStatus = completion
      ? 'completed'
      : arrived
        ? 'arrived'
        : s.order === p.currentStopOrder
          ? 'current'
          : 'upcoming'
    const share = trip.shares.find((sh) => sh.stopId === s.id && !sh.expired)
    return {
      order: s.order,
      kind: s.kind,
      label: s.label,
      position: s.position,
      routeIndex: s.routeIndex,
      legSeconds: s.legSeconds,
      status,
      etaSeconds: status === 'completed' || status === 'arrived' ? 0 : etaToStop(eta, s.order),
      ...(completion && { completedBy: completion.by }),
      ...(share?.passengerLocation && { passengerLocation: share.passengerLocation }),
    }
  })

  return {
    tripId: trip.id,
    ended: p.ended,
    routeSource: trip.routeSource,
    route: trip.route,
    car: p.location,
    currentStopOrder: p.currentStopOrder,
    currentLegEtaSeconds: p.currentLegEtaSeconds,
    stops,
  }
}
