import type { LatLng, Stop, TripSnapshot } from '@/types'

import { KakaoMapView } from './KakaoMapView'
import { MockMapView } from './MockMapView'

/** Extra point on the map: a passenger's shared position, or where they said they'd wait */
export type MapPin = { key: string; position: LatLng; label: string; tone: 'passenger' | 'wait' }

export type MapViewProps = {
  route: LatLng[]
  stops: Stop[]
  snapshot: TripSnapshot
  me: LatLng | null
  /** Pin text. Default is the passenger's view (내 위치 / 픽업 / waypoint name). */
  labelOf?: (stop: Stop) => string
  pins?: MapPin[]
}

const KAKAO_KEY = import.meta.env.VITE_KAKAO_MAP_KEY as string | undefined

/** Kakao map when a JavaScript key is set (.env), otherwise an offline mock map for demos. */
export function MapView(props: MapViewProps) {
  return KAKAO_KEY ? <KakaoMapView appkey={KAKAO_KEY} {...props} /> : <MockMapView {...props} />
}

/** Which route vertices are behind the car, and which belong to my leg vs. later legs. */
export function splitRoute(route: LatLng[], stops: Stop[], snapshot: TripSnapshot) {
  const mineIndex = stops.find((s) => s.isMine)!.routeIndex
  const i = snapshot.traveledIndex
  const car = { lat: snapshot.car.lat, lng: snapshot.car.lng }
  const traveled = [...route.slice(0, i + 1), car]
  const toMe = i < mineIndex ? [car, ...route.slice(i + 1, mineIndex + 1)] : []
  const later = route.slice(Math.max(mineIndex, i + 1))
  if (i >= mineIndex) later.unshift(car)
  return { traveled, toMe, later }
}
