import { Inject, Injectable, Logger, Optional } from '@nestjs/common'

import { distance } from '../common/geo.js'
import type { LatLng } from '../common/types.js'
import { type DirectedRoute, KAKAO_DIRECTIONS_CLIENT, type KakaoDirectionsClient } from './kakao-directions.client.js'

/** Road distance is roughly 1.3× the straight line in a city */
const DETOUR_FACTOR = 1.3
const FALLBACK_SPEED_MPS = 25 / 3.6

export type PlannedRoute = DirectedRoute & { source: 'kakao' | 'straight' }

/**
 * Plans the route through the trip's points once, at trip creation.
 * Kakao when a client is configured, otherwise (or on failure) straight lines, for development and tests.
 */
@Injectable()
export class RoutePlanner {
  private readonly logger = new Logger(RoutePlanner.name)

  constructor(@Optional() @Inject(KAKAO_DIRECTIONS_CLIENT) private readonly kakao: KakaoDirectionsClient | null) {}

  async plan(points: LatLng[]): Promise<PlannedRoute> {
    if (this.kakao) {
      try {
        return { ...(await this.kakao.directions(points)), source: 'kakao' }
      } catch (err) {
        this.logger.warn(`kakao directions failed, using straight lines: ${(err as Error).message}`)
      }
    }
    return { ...straightLineRoute(points), source: 'straight' }
  }
}

/** Straight segments between the points; leg time = distance × 1.3 at 25 km/h. */
export function straightLineRoute(points: LatLng[]): DirectedRoute {
  const legDurations: number[] = []
  for (let i = 1; i < points.length; i++) {
    legDurations.push(Math.round((distance(points[i - 1], points[i]) * DETOUR_FACTOR) / FALLBACK_SPEED_MPS))
  }
  return {
    polyline: points.map((p) => ({ lat: p.lat, lng: p.lng })),
    legDurations,
    legEndIndices: legDurations.map((_, i) => i + 1),
  }
}
