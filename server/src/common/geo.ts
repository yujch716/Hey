import type { LatLng } from './types.js'

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
  for (let i = 1; i < route.length; i++) out.push(out[i - 1] + distance(route[i - 1], route[i]))
  return out
}

/** Point `meters` along the polyline, with heading and the last passed vertex. */
export function pointAlong(route: LatLng[], cum: number[], meters: number) {
  const total = cum[cum.length - 1]
  const m = Math.max(0, Math.min(total, meters))
  let i = 1
  while (i < cum.length - 1 && cum[i] < m) i++
  const a = route[i - 1]
  const b = route[i]
  const segLen = cum[i] - cum[i - 1]
  const t = segLen === 0 ? 0 : (m - cum[i - 1]) / segLen
  return {
    lat: a.lat + (b.lat - a.lat) * t,
    lng: a.lng + (b.lng - a.lng) * t,
    heading: bearing(a, b),
    traveledIndex: i - 1,
  }
}

/**
 * Last route vertex the car has passed: the start of the route segment closest to `p`.
 * Only searches forward from `fromIndex` so a route that doubles back never jumps backwards.
 */
export function traveledIndexOf(route: LatLng[], p: LatLng, fromIndex = 0) {
  if (route.length < 2) return 0
  let best = Math.max(0, Math.min(fromIndex, route.length - 2))
  let bestDist = Infinity
  for (let i = best; i < route.length - 1; i++) {
    const d = distanceToSegment(p, route[i], route[i + 1])
    if (d < bestDist) {
      bestDist = d
      best = i
    }
  }
  // Standing exactly on (or past) the next vertex counts as having passed it
  const next = route[best + 1]
  if (distance(p, next) < 1) return best + 1
  return best
}

// Flat-earth projection is plenty for city-scale segments
function distanceToSegment(p: LatLng, a: LatLng, b: LatLng) {
  const kx = 111320 * Math.cos((p.lat * Math.PI) / 180)
  const ky = 110540
  const ax = (a.lng - p.lng) * kx
  const ay = (a.lat - p.lat) * ky
  const bx = (b.lng - p.lng) * kx
  const by = (b.lat - p.lat) * ky
  const dx = bx - ax
  const dy = by - ay
  const len2 = dx * dx + dy * dy
  const t = len2 === 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2))
  return Math.hypot(ax + t * dx, ay + t * dy)
}
