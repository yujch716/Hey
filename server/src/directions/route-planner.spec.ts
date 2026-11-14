import { jest } from '@jest/globals'

import { distance } from '../common/geo.js'
import { HttpKakaoDirectionsClient, type KakaoDirectionsClient } from './kakao-directions.client.js'
import { RoutePlanner, straightLineRoute } from './route-planner.js'

const a = { lat: 37.49, lng: 127.01 }
const b = { lat: 37.5, lng: 127.02 }
const c = { lat: 37.51, lng: 127.02 }

describe('straightLineRoute', () => {
  it('joins the points and estimates each leg at 1.3× distance, 25 km/h', () => {
    const r = straightLineRoute([a, b, c])
    expect(r.polyline).toEqual([a, b, c])
    expect(r.legEndIndices).toEqual([1, 2])
    expect(r.legDurations[0]).toBe(Math.round((distance(a, b) * 1.3) / (25 / 3.6)))
    expect(r.legDurations).toHaveLength(2)
  })
})

describe('RoutePlanner', () => {
  const kakaoRoute = { polyline: [a, { lat: 37.495, lng: 127.015 }, b, c], legDurations: [300, 120], legEndIndices: [2, 3] }

  it('uses Kakao when a client is configured', async () => {
    const kakao: KakaoDirectionsClient = { directions: async () => kakaoRoute }
    await expect(new RoutePlanner(kakao).plan([a, b, c])).resolves.toEqual({ ...kakaoRoute, source: 'kakao' })
  })

  it('falls back to straight lines when Kakao fails', async () => {
    const kakao: KakaoDirectionsClient = {
      directions: async () => {
        throw new Error('quota exceeded')
      },
    }
    const r = await new RoutePlanner(kakao).plan([a, b, c])
    expect(r.source).toBe('straight')
    expect(r.polyline).toEqual([a, b, c])
  })

  it('uses straight lines without a client (no KAKAO_REST_API_KEY)', async () => {
    expect((await new RoutePlanner(null).plan([a, b])).source).toBe('straight')
  })
})

describe('HttpKakaoDirectionsClient', () => {
  const realFetch = global.fetch
  afterEach(() => {
    global.fetch = realFetch
  })

  it('turns sections into legs and joins the road vertexes into one polyline', async () => {
    const fetchMock = jest.fn(async () =>
      Response.json({
        routes: [
          {
            result_code: 0,
            result_msg: '길찾기 성공',
            sections: [
              { duration: 300, roads: [{ vertexes: [127.01, 37.49, 127.015, 37.495] }, { vertexes: [127.015, 37.495, 127.02, 37.5] }] },
              { duration: 120, roads: [{ vertexes: [127.02, 37.5, 127.02, 37.51] }] },
            ],
          },
        ],
      }),
    )
    global.fetch = fetchMock as unknown as typeof fetch

    const r = await new HttpKakaoDirectionsClient('test-key').directions([a, b, c])

    expect(r.legDurations).toEqual([300, 120])
    expect(r.polyline).toEqual([a, { lat: 37.495, lng: 127.015 }, b, c])
    expect(r.legEndIndices).toEqual([2, 3])
    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect((init.headers as Record<string, string>).authorization).toBe('KakaoAK test-key')
    expect(JSON.parse(init.body as string)).toMatchObject({ origin: { x: 127.01, y: 37.49 }, waypoints: [{ x: 127.02, y: 37.5 }] })
  })

  it('throws when Kakao reports no route', async () => {
    global.fetch = (async () =>
      Response.json({ routes: [{ result_code: 104, result_msg: '출발지와 도착지가 너무 가깝습니다' }] })) as typeof fetch
    await expect(new HttpKakaoDirectionsClient('k').directions([a, b])).rejects.toThrow('너무 가깝습니다')
  })
})
