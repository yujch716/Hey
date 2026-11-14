import type { LatLng } from '../common/types.js'

/** A driving route through points in order: the polyline, and how long each leg takes. */
export type DirectedRoute = {
  polyline: LatLng[]
  /** seconds for leg i: points[i] → points[i + 1] */
  legDurations: number[]
  /** polyline index where leg i ends, i.e. where points[i + 1] sits on the polyline */
  legEndIndices: number[]
}

/** Wraps Kakao Mobility directions so tests can swap in a fake. */
export interface KakaoDirectionsClient {
  /** Throws on any failure; the caller falls back to straight lines. */
  directions(points: LatLng[]): Promise<DirectedRoute>
}

export const KAKAO_DIRECTIONS_CLIENT = Symbol('KAKAO_DIRECTIONS_CLIENT')

const ENDPOINT = 'https://apis-navi.kakaomobility.com/v1/waypoints/directions'
const MAX_WAYPOINTS = 30
const TIMEOUT_MS = 5_000

type KakaoResponse = {
  routes?: {
    result_code: number
    result_msg: string
    sections?: { duration: number; roads: { vertexes: number[] }[] }[]
  }[]
}

/** Kakao Mobility multi-waypoint directions (REST). https://developers.kakaomobility.com */
export class HttpKakaoDirectionsClient implements KakaoDirectionsClient {
  constructor(private readonly restApiKey: string) {}

  async directions(points: LatLng[]): Promise<DirectedRoute> {
    if (points.length < 2) throw new Error('need at least two points')
    if (points.length - 2 > MAX_WAYPOINTS) throw new Error(`more than ${MAX_WAYPOINTS} waypoints`)

    const xy = (p: LatLng) => ({ x: p.lng, y: p.lat })
    const res = await fetch(ENDPOINT, {
      method: 'POST',
      headers: { authorization: `KakaoAK ${this.restApiKey}`, 'content-type': 'application/json' },
      body: JSON.stringify({
        origin: xy(points[0]),
        destination: xy(points[points.length - 1]),
        waypoints: points.slice(1, -1).map(xy),
        priority: 'RECOMMEND',
      }),
      signal: AbortSignal.timeout(TIMEOUT_MS),
    })
    if (!res.ok) throw new Error(`kakao directions ${res.status}`)

    const route = ((await res.json()) as KakaoResponse).routes?.[0]
    if (!route || route.result_code !== 0 || !route.sections) {
      throw new Error(`kakao directions: ${route?.result_msg ?? 'no route'}`)
    }
    if (route.sections.length !== points.length - 1) throw new Error('kakao directions: section count mismatch')

    const polyline: LatLng[] = [points[0]]
    const legEndIndices: number[] = []
    for (const section of route.sections) {
      for (const road of section.roads) {
        for (let i = 0; i + 1 < road.vertexes.length; i += 2) {
          const p = { lat: road.vertexes[i + 1], lng: road.vertexes[i] }
          const last = polyline[polyline.length - 1]
          if (last.lat !== p.lat || last.lng !== p.lng) polyline.push(p)
        }
      }
      legEndIndices.push(polyline.length - 1)
    }
    return { polyline, legDurations: route.sections.map((s) => s.duration), legEndIndices }
  }
}
