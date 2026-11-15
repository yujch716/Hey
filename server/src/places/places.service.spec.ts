import { jest } from '@jest/globals'
import { BadGatewayException } from '@nestjs/common'

import { HttpKakaoLocalClient, type KakaoLocalClient } from './kakao-local.client.js'
import { DUMMY_PLACES, PlacesService } from './places.service.js'

describe('PlacesService', () => {
  it('passes the query and the car position to Kakao', async () => {
    const searchKeyword = jest.fn<KakaoLocalClient['searchKeyword']>(async () => [
      { name: '공차 강남역점', address: '서울 강남구 강남대로 지하 396', lat: 37.498, lng: 127.0277 },
    ])
    const service = new PlacesService({ searchKeyword })

    const res = await service.search('카페', { lat: 37.49, lng: 127.01 })
    expect(res[0].name).toBe('공차 강남역점')
    expect(searchKeyword).toHaveBeenCalledWith('카페', { lat: 37.49, lng: 127.01 })
  })

  it('reports a failing Kakao call as 502 instead of inventing places', async () => {
    const service = new PlacesService({
      searchKeyword: async () => {
        throw new Error('quota exceeded')
      },
    })
    await expect(service.search('카페')).rejects.toBeInstanceOf(BadGatewayException)
  })

  describe('without a key', () => {
    const service = new PlacesService(null)

    it('returns the dummy places whose name matches', async () => {
      expect((await service.search('역삼')).map((p) => p.name)).toEqual(['역삼역 3번 출구'])
    })

    it('returns every dummy place when nothing matches, so a demo search is never empty', async () => {
      expect(await service.search('아무거나')).toEqual(DUMMY_PLACES)
    })
  })
})

describe('HttpKakaoLocalClient', () => {
  const realFetch = global.fetch
  afterEach(() => {
    global.fetch = realFetch
  })

  it('maps Kakao documents and sorts by distance from the car', async () => {
    const fetchMock = jest.fn(async () =>
      Response.json({
        documents: [
          { place_name: '공차 강남역점', road_address_name: '서울 강남구 강남대로 지하 396', address_name: '서울 강남구 역삼동 858', x: '127.0277424', y: '37.4979890' },
          { place_name: '지번만 있는 곳', road_address_name: '', address_name: '서울 강남구 역삼동 1', x: '127.03', y: '37.5' },
        ],
      }),
    )
    global.fetch = fetchMock as unknown as typeof fetch

    const res = await new HttpKakaoLocalClient('test-key').searchKeyword('카페', { lat: 37.49, lng: 127.01 })

    expect(res).toEqual([
      { name: '공차 강남역점', address: '서울 강남구 강남대로 지하 396', lat: 37.497989, lng: 127.0277424 },
      { name: '지번만 있는 곳', address: '서울 강남구 역삼동 1', lat: 37.5, lng: 127.03 },
    ])
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(url).toContain('x=127.01')
    expect(url).toContain('y=37.49')
    expect(url).toContain('sort=distance')
    expect((init.headers as Record<string, string>).authorization).toBe('KakaoAK test-key')
  })
})
