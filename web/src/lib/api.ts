import type { TripState } from '@/hooks/tripSource'
import type { LatLng, QuickReply, Trip, TripSnapshot } from '@/types'

/**
 * Without VITE_API_BASE_URL the server is assumed on port 3000 of whatever host served this page,
 * so the same dev build works from localhost, the emulator's WebView (10.0.2.2) and a phone (MacBook IP).
 */
export const API_BASE_URL = (
  (import.meta.env.VITE_API_BASE_URL as string | undefined) || `${window.location.protocol}//${window.location.hostname}:3000`
).replace(/\/$/, '')
export const WS_URL = ((import.meta.env.VITE_WS_URL as string | undefined) || API_BASE_URL).replace(/\/$/, '')

export type ShareResult = Exclude<TripState, { status: 'loading' }>

/** GET /share/:token. Never throws: network trouble comes back as `error`. */
export async function fetchShare(token: string): Promise<ShareResult> {
  try {
    const res = await fetch(`${API_BASE_URL}/share/${encodeURIComponent(token)}`)
    if (res.status === 404) return { status: 'notFound' }
    if (res.status === 410) return { status: 'expired' }
    if (!res.ok) return { status: 'error' }
    const { vehicle, route, stops, snapshot } = (await res.json()) as Trip & { snapshot: TripSnapshot }
    return { status: 'ready', trip: { vehicle, route, stops }, snapshot }
  } catch {
    return { status: 'error' }
  }
}

export function postReply(token: string, type: QuickReply, position?: LatLng) {
  return send('POST', `/share/${encodeURIComponent(token)}/replies`, { type, ...(position && { position }) })
}

export function postLocation(token: string, position: LatLng) {
  return send('POST', `/share/${encodeURIComponent(token)}/location`, position)
}

export function deleteLocation(token: string) {
  return send('DELETE', `/share/${encodeURIComponent(token)}/location`)
}

async function send(method: 'POST' | 'DELETE', path: string, body?: unknown) {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers: body === undefined ? undefined : { 'content-type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!res.ok) throw new Error(`${method} ${path} → ${res.status}`)
}
