import { API_BASE_URL } from '@/lib/api'
import type { LatLng, StopKind } from '@/types'

/**
 * GET /trips/:tripId (driver only). Mirrors server/src/trips/driver-view.ts;
 * not in types.ts because passengers never see it.
 */
export type DriverStopStatus = 'upcoming' | 'current' | 'arrived' | 'completed'

export type DriverStop = {
  order: number
  kind: StopKind
  label: string
  position: LatLng
  routeIndex: number
  legSeconds: number
  status: DriverStopStatus
  etaSeconds: number
  completedBy?: 'door' | 'button' | 'departed'
  passengerLocation?: LatLng & { at: number }
}

export type DriverView = {
  tripId: string
  ended: boolean
  route: LatLng[]
  car: (LatLng & { heading: number; ts: number }) | null
  currentStopOrder: number
  currentLegEtaSeconds: number | null
  stops: DriverStop[]
}

export type DriverViewResult =
  | { status: 'ready'; view: DriverView }
  | { status: 'unauthorized' } // 401/404: wrong link
  | { status: 'error' }

export async function fetchDriverView(tripId: string, driverToken: string): Promise<DriverViewResult> {
  try {
    const res = await fetch(`${API_BASE_URL}/trips/${encodeURIComponent(tripId)}`, {
      headers: { authorization: `Bearer ${driverToken}` },
    })
    if (res.status === 401 || res.status === 404) return { status: 'unauthorized' }
    if (!res.ok) return { status: 'error' }
    return { status: 'ready', view: (await res.json()) as DriverView }
  } catch {
    return { status: 'error' }
  }
}
