// Source of truth for the shapes the web reads. web/src/types.ts must match this file; change both together.

export type LatLng = { lat: number; lng: number }

export type StopKind = 'waypoint' | 'pickup'

export type Stop = {
  id: string
  order: number // 1-based visiting order
  kind: StopKind
  label: string // shown on the pin, never a name or phone number
  position: LatLng
  routeIndex: number // index into Trip.route where this stop sits
  isMine: boolean
}

export type Vehicle = {
  model: string
  colorName: string
  colorHex: string
  bodyType: 'sedan' | 'suv'
  plate: string
}

export type Phase =
  | 'departed'
  | 'toEarlierStop' // heading to a stop before mine (a waypoint or another passenger)
  | 'atEarlierStop' // standing at a stop before mine
  | 'toMe'
  | 'near'
  | 'arrived'
  | 'boarded'

export type TripSnapshot = {
  phase: Phase
  car: LatLng & { heading: number }
  traveledIndex: number // last route vertex already passed
  etaSeconds: number // to my stop
  updatedAt: number // epoch ms of the last position update
  currentStopOrder: number // next stop the car is heading to
  currentStop: CurrentStop // the stop the car is heading to or standing at
}

export type CurrentStop = Pick<Stop, 'order' | 'kind' | 'label'>

export type Trip = {
  vehicle: Vehicle
  route: LatLng[]
  stops: Stop[]
}

export type QuickReply = 'out' | 'late' | 'waitHere'
