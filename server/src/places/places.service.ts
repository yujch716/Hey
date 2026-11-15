import { BadGatewayException, Inject, Injectable, Logger, Optional } from '@nestjs/common'

import type { LatLng } from '../common/types.js'
import { KAKAO_LOCAL_CLIENT, type KakaoLocalClient, type Place } from './kakao-local.client.js'

/** Fixed places around 강남역 (same area as the demo trip), used when there is no Kakao key. */
export const DUMMY_PLACES: Place[] = [
  { name: '카페 hey 강남점', address: '서울 강남구 강남대로 382', lat: 37.4945, lng: 127.0245 },
  { name: '강남역 11번 출구', address: '서울 강남구 강남대로 396', lat: 37.4986, lng: 127.0281 },
  { name: '역삼역 3번 출구', address: '서울 강남구 테헤란로 156', lat: 37.5005, lng: 127.03 },
  { name: '신논현역 6번 출구', address: '서울 강남구 봉은사로 102', lat: 37.5048, lng: 127.0255 },
  { name: '교보타워', address: '서울 서초구 강남대로 465', lat: 37.508, lng: 127.027 },
]

@Injectable()
export class PlacesService {
  private readonly logger = new Logger(PlacesService.name)

  constructor(@Optional() @Inject(KAKAO_LOCAL_CLIENT) private readonly kakao: KakaoLocalClient | null) {}

  async search(query: string, near?: LatLng): Promise<Place[]> {
    if (!this.kakao) return dummySearch(query)
    try {
      return await this.kakao.searchKeyword(query, near)
    } catch (err) {
      // A real key that fails is a real outage: say so rather than invent places
      this.logger.warn(`kakao local search failed: ${(err as Error).message}`)
      throw new BadGatewayException('place search is unavailable')
    }
  }
}

/** Name matches first; if nothing matches, every dummy place, so a demo search never comes back empty. */
function dummySearch(query: string): Place[] {
  const q = query.trim()
  const hits = DUMMY_PLACES.filter((p) => p.name.includes(q) || p.address.includes(q))
  return hits.length > 0 ? hits : DUMMY_PLACES
}
