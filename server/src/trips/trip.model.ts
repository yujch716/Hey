import type { LatLng, StopKind, Vehicle } from '../common/types.js'

export type StopRecord = {
  id: string
  order: number
  kind: StopKind
  label: string
  position: LatLng
  routeIndex: number
  /** Planned driving time to this stop from the previous one (or from the start), seconds */
  legSeconds: number
}

/** One share link per pickup stop. Expires when that stop completes or the trip ends. */
export type ShareRecord = {
  token: string
  stopId: string
  expired: boolean
  /** Last position the passenger chose to share, for the driver's map. Cleared when they stop or the share expires. */
  passengerLocation: (LatLng & { at: number }) | null
}

export type CarLocation = LatLng & { heading: number; ts: number }

/** How the car app decided a stop was done. `departed` also covers the server's own fallback. */
export type CompletedBy = 'door' | 'button' | 'departed'

/** What the car has reported so far. Everything the passenger sees is derived from this. */
export type TripProgress = {
  location: CarLocation | null
  traveledIndex: number
  currentStopOrder: number
  /** Navi ETA to the current stop, seconds. Null until the first location. */
  currentLegEtaSeconds: number | null
  /** stopOrder → epoch ms of stopArrived */
  arrivedAt: Record<number, number>
  completions: { order: number; by: CompletedBy; at: number }[]
  ended: boolean
}

export type TripRecord = {
  id: string
  driverToken: string
  createdAt: number
  vehicle: Vehicle
  route: LatLng[]
  routeSource: 'kakao' | 'straight' | 'client'
  stops: StopRecord[]
  shares: ShareRecord[]
  progress: TripProgress
}
