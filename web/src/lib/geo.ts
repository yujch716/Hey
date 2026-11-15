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

/**
 * Last route vertex the car has passed (same rule as server/src/common/geo.ts):
 * the start of the closest segment, searching forward from `fromIndex` so it never jumps back,
 * and no further than `toIndex` (the stop the car is heading to) so a route that passes the
 * same street again later can't pull the car ahead of itself.
 */
export function traveledIndexOf(route: LatLng[], p: LatLng, fromIndex = 0, toIndex = route.length - 1) {
  if (route.length < 2) return 0
  const last = Math.max(1, Math.min(toIndex, route.length - 1))
  let best = Math.max(0, Math.min(fromIndex, last - 1))
  let bestDist = Infinity
  for (let i = best; i < last; i++) {
    const d = distanceToSegment(p, route[i], route[i + 1])
    if (d < bestDist) {
      bestDist = d
      best = i
    }
  }
  return distance(p, route[best + 1]) < 1 ? best + 1 : best
}

function distanceToSegment(p: LatLng, a: LatLng, b: LatLng) {
  const kx = 111320 * Math.cos((p.lat * Math.PI) / 180)
  const ky = 110540
  const ax = (a.lng - p.lng) * kx
  const ay = (a.lat - p.lat) * ky
  const dx = (b.lng - p.lng) * kx - ax
  const dy = (b.lat - p.lat) * ky - ay
  const len2 = dx * dx + dy * dy
  const t = len2 === 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2))
  return Math.hypot(ax + t * dx, ay + t * dy)
}
