import { useMemo } from 'react'

import { type MapPin, MapView } from '@/components/map/MapView'
import { useDriverStream } from '@/hooks/useDriverStream'
import { useNow } from '@/hooks/useTripSimulation'
import { cn } from '@/lib/utils'
import type { Stop, TripSnapshot } from '@/types'

/** Driver's pin text: waypoints by name, pickups by order only (no names). */
const driverLabel = (s: Stop) => (s.kind === 'waypoint' ? s.label : `${s.order}번`)

/**
 * /d/:tripId#token=… — the car app shows this in a WebView on its trip panel.
 * Read-only, landscape, full bleed (no 430px phone frame). The token stays in the hash so it never reaches a server log.
 */
export function DriverMapScreen({ tripId, token }: { tripId: string; token: string }) {
  const stream = useDriverStream(tripId, token)
  const now = useNow()

  const derived = useMemo(() => {
    if (stream.status !== 'ready') return null
    const { view, waits, traveledIndex } = stream
    const current = view.stops.find((s) => s.order === view.currentStopOrder) ?? view.stops[view.stops.length - 1]
    const car = view.car ?? { ...view.route[0], heading: 0, ts: 0 }

    // The map components speak the passenger's types: "mine" is the stop the car is heading to
    const stops: Stop[] = view.stops.map((s) => ({
      id: `s${s.order}`,
      order: s.order,
      kind: s.kind,
      label: s.label,
      position: s.position,
      routeIndex: s.routeIndex,
      isMine: s.order === current.order,
    }))
    const snapshot: TripSnapshot = {
      phase: 'toMe',
      car: { lat: car.lat, lng: car.lng, heading: car.heading },
      traveledIndex,
      etaSeconds: current.etaSeconds,
      updatedAt: car.ts,
      currentStopOrder: current.order,
      currentStop: { order: current.order, kind: current.kind, label: current.label },
    }
    const pins: MapPin[] = [
      ...view.stops.flatMap((s) =>
        s.passengerLocation ? [{ key: `p${s.order}`, position: s.passengerLocation, label: `${s.order}번 탑승자`, tone: 'passenger' as const }] : [],
      ),
      ...Object.entries(waits).map(([order, position]) => ({
        key: `w${order}`,
        position,
        label: `${order}번 · 여기서 기다려요`,
        tone: 'wait' as const,
      })),
    ]
    return { view, current, stops, snapshot, pins }
  }, [stream])

  if (stream.status === 'loading') return <Message text="지도를 불러오는 중이에요" />
  if (stream.status === 'unauthorized') return <Message text="운행 정보를 찾을 수 없어요" />
  if (stream.status === 'error' || !derived) return <Message text="서버에 연결할 수 없어요" />

  const { view, current, stops, snapshot, pins } = derived
  const secondsAgo = view.car ? Math.max(0, Math.round((now - view.car.ts) / 1000)) : null

  return (
    <div className="relative h-[100dvh] w-full overflow-hidden bg-ground">
      <MapView route={view.route} stops={stops} snapshot={snapshot} me={null} labelOf={driverLabel} pins={pins} showTraveled={false} />

      <div className="pointer-events-none absolute left-3 top-3 z-10 rounded-xl bg-white/95 px-4 py-3 shadow-[0_4px_14px_rgba(26,36,51,0.18)]">
        {view.ended ? (
          <p className="text-[17px] font-bold">운행이 끝났어요</p>
        ) : (
          <>
            <p className="text-[17px] font-bold">
              {current.kind === 'waypoint' ? current.label : `${current.order}번 정류지`}
              {current.status === 'arrived' ? ' · 도착' : ` · ${Math.max(1, Math.ceil(current.etaSeconds / 60))}분`}
            </p>
            <p className={cn('mt-0.5 text-[13px] tabular-nums', stream.connected ? 'text-muted' : 'text-red-600')}>
              {secondsAgo === null ? '위치 기다리는 중' : secondsAgo <= 1 ? '방금 갱신' : `${secondsAgo}초 전 갱신`}
              {stream.connected ? '' : ' · 연결 끊김'}
            </p>
          </>
        )}
      </div>
    </div>
  )
}

function Message({ text }: { text: string }) {
  return <div className="grid h-[100dvh] w-full place-items-center bg-paper text-[18px] text-muted">{text}</div>
}
