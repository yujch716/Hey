import type { LatLng } from '../common/types.js'

export type Place = {
  name: string
  address: string
  lat: number
  lng: number
}

/** Wraps Kakao Local keyword search so tests can swap in a fake. */
export interface KakaoLocalClient {
  /** Throws on any failure. `near` sorts results by distance from that point. */
  searchKeyword(query: string, near?: LatLng): Promise<Place[]>
}

export const KAKAO_LOCAL_CLIENT = Symbol('KAKAO_LOCAL_CLIENT')

const ENDPOINT = 'https://dapi.kakao.com/v2/local/search/keyword.json'
const TIMEOUT_MS = 5_000
const MAX_RESULTS = 15

type KakaoKeywordResponse = {
  documents?: { place_name: string; road_address_name: string; address_name: string; x: string; y: string }[]
}

/** https://developers.kakao.com/docs/latest/ko/local/dev-guide#search-by-keyword */
export class HttpKakaoLocalClient implements KakaoLocalClient {
  constructor(private readonly restApiKey: string) {}

  async searchKeyword(query: string, near?: LatLng): Promise<Place[]> {
    const params = new URLSearchParams({ query, size: String(MAX_RESULTS) })
    if (near) {
      params.set('x', String(near.lng))
      params.set('y', String(near.lat))
      params.set('sort', 'distance')
    }
    const res = await fetch(`${ENDPOINT}?${params}`, {
      headers: { authorization: `KakaoAK ${this.restApiKey}` },
      signal: AbortSignal.timeout(TIMEOUT_MS),
    })
    if (!res.ok) throw new Error(`kakao local ${res.status}`)

    const body = (await res.json()) as KakaoKeywordResponse
    return (body.documents ?? []).map((d) => ({
      name: d.place_name,
      address: d.road_address_name || d.address_name,
      lat: Number(d.y),
      lng: Number(d.x),
    }))
  }
}
