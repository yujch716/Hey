import { useEffect, useRef } from 'react'
import { CustomOverlayMap, Map, Polyline, useKakaoLoader } from 'react-kakao-maps-sdk'

import type { LatLng } from '@/types'

import { splitRoute, type MapViewProps } from './MapView'
import { CarMarker, MeDot, StopPin } from './markers'

/**
 * Kakao Maps JS. Needs a JavaScript key from developers.kakao.com,
 * with this site's domain (e.g. http://localhost:5173) registered under 플랫폼 > Web.
 */
export function KakaoMapView({ appkey, route, stops, snapshot, me }: MapViewProps & { appkey: string }) {
  const [loading, error] = useKakaoLoader({ appkey })
  const { traveled, toMe, later } = splitRoute(route, stops, snapshot)
  const wrapper = useRef<HTMLDivElement>(null)
  const map = useRef<kakao.maps.Map | null>(null)

  // Kakao maps don't notice their box changing (e.g. the "연결이 끊겼어요" bar pushing the map down)
  useEffect(() => {
    const el = wrapper.current
    if (!el) return
    const ro = new ResizeObserver(() => map.current?.relayout())
    ro.observe(el)
    return () => ro.disconnect()
  }, [loading])

  if (error) {
    return (
      <div className="grid h-full place-items-center bg-ground p-6 text-center text-sm text-muted">
        지도를 불러오지 못했어요. 카카오 개발자 콘솔에 이 주소가 등록돼 있는지 확인해 주세요.
      </div>
    )
  }
  if (loading) return <div className="h-full bg-ground" />

  const onCreate = (created: kakao.maps.Map) => {
    map.current = created
    const bounds = new kakao.maps.LatLngBounds()
    ;[...route, ...(me ? [me] : [])].forEach((p) => bounds.extend(new kakao.maps.LatLng(p.lat, p.lng)))
    created.setBounds(bounds, 48, 32, 48, 32)
  }

  return (
    <div ref={wrapper} className="h-full w-full">
      <Map center={route[0]} level={5} className="h-full w-full" onCreate={onCreate}>
        <Polyline path={traveled} strokeColor="#a7b0bb" strokeWeight={6} strokeOpacity={1} />
        <Polyline path={later} strokeColor="#2e62f5" strokeWeight={5} strokeOpacity={0.35} strokeStyle="shortdot" />
        {toMe.length > 1 && <Polyline path={toMe} strokeColor="#2e62f5" strokeWeight={7} strokeOpacity={1} />}

        {stops.map((s) => (
          <CustomOverlayMap key={s.id} position={s.position} xAnchor={0} yAnchor={0}>
            <StopPin stop={s} passed={s.order < snapshot.currentStopOrder} />
          </CustomOverlayMap>
        ))}

        {me && (
          <CustomOverlayMap position={me as LatLng} xAnchor={0} yAnchor={0}>
            <MeDot />
          </CustomOverlayMap>
        )}

        <CustomOverlayMap position={{ lat: snapshot.car.lat, lng: snapshot.car.lng }} xAnchor={0} yAnchor={0} zIndex={3}>
          <CarMarker heading={snapshot.car.heading} />
        </CustomOverlayMap>
      </Map>
    </div>
  )
}
