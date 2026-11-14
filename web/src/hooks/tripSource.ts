import type { LatLng, QuickReply, Trip, TripSnapshot } from '@/types'

/**
 * What the trip screen needs, wherever the data comes from (demo simulation or the server).
 * Screen components only ever see this shape.
 */
export type TripSource = {
  state: TripState
  /** False while the live connection is down. The last snapshot stays on screen. */
  connected: boolean
  /** Rejects if the driver could not be told, so the button can be reset. */
  sendReply: (type: QuickReply, position?: LatLng) => Promise<void>
  location: LocationShare
}

export type TripState =
  | { status: 'loading' }
  | { status: 'notFound' } // unknown link
  | { status: 'expired' } // the pickup is over (boarded or trip ended)
  | { status: 'error' } // server unreachable
  | { status: 'ready'; trip: Trip; snapshot: TripSnapshot }

export type LocationShare = {
  sharing: boolean
  /** Where the passenger is, once a fix has come in */
  position: LatLng | null
  start: () => void
  stop: () => void
}
