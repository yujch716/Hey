import type { LatLng } from '@/types'

const R = 6371000

export function distance(a: LatLng, b: LatLng) {
  const toRad = (d: number) => (d * Math.PI) / 180
  const dLat = toRad(b.lat - a.lat)
  const dLng = toRad(b.lng - a.lng)
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.sin(dLng / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(h))
}

export function bearing(a: LatLng, b: LatLng) {
  const toRad = (d: number) => (d * Math.PI) / 180
  const y = Math.sin(toRad(b.lng - a.lng)) * Math.cos(toRad(b.lat))
  const x =
    Math.cos(toRad(a.lat)) * Math.sin(toRad(b.lat)) -
    Math.sin(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.cos(toRad(b.lng - a.lng))
  return ((Math.atan2(y, x) * 180) / Math.PI + 360) % 360
}

/** Cumulative distance (m) at each vertex of a polyline. */
export function cumulative(route: LatLng[]) {
  const out = [0]
  for (let i = 1; i < route.length; i++) out.push(out[i - 1] + distance(route.at(i - 1)!, route.at(i)!))
  return out
}

/** Point `meters` along the polyline, with heading and the last passed vertex. */
export function pointAlong(route: LatLng[], cum: number[], meters: number) {
  const total = cum.at(-1)!
  const m = Math.max(0, Math.min(total, meters))
  let i = 1
  while (i < cum.length - 1 && cum.at(i)! < m) i++
  const a = route.at(i - 1)!
  const b = route.at(i)!
  const segLen = cum.at(i)! - cum.at(i - 1)!
  const t = segLen === 0 ? 0 : (m - cum.at(i - 1)!) / segLen
  return {
    lat: a.lat + (b.lat - a.lat) * t,
    lng: a.lng + (b.lng - a.lng) * t,
    heading: bearing(a, b),
    traveledIndex: i - 1,
  }
}
