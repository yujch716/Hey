import { useLayoutEffect, useMemo, useRef, useState } from 'react'

import { cn } from '@/lib/utils'
import type { LatLng } from '@/types'

import { splitRoute, type MapViewProps } from './MapView'
import { CarMarker, LabeledPin, MeDot, StopPin } from './markers'

/**
 * Offline stand-in for the Kakao map: a drawn street grid with the same overlays.
 * Used when VITE_KAKAO_MAP_KEY is not set, so the page works (and screenshots) without a key.
 */
export function MockMapView({ route, stops, snapshot, me, labelOf, pins = [], showTraveled = true }: MapViewProps) {
  const ref = useRef<HTMLDivElement>(null)
  const [size, setSize] = useState({ w: 400, h: 400 })
  const [animate, setAnimate] = useState(false)

  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return
    const ro = new ResizeObserver(([e]) => setSize({ w: e.contentRect.width, h: e.contentRect.height }))
    ro.observe(el)
    // Only glide between position updates, not from the initial layout.
    const t = setTimeout(() => setAnimate(true), 500)
    return () => {
      ro.disconnect()
      clearTimeout(t)
    }
  }, [])

  const project = useMemo(() => {
    const pts = [...route, ...(me ? [me] : []), ...pins.map((p) => p.position)]
    const lats = pts.map((p) => p.lat)
    const lngs = pts.map((p) => p.lng)
    const minLat = Math.min(...lats), maxLat = Math.max(...lats)
    const minLng = Math.min(...lngs), maxLng = Math.max(...lngs)
    const cLat = (minLat + maxLat) / 2, cLng = (minLng + maxLng) / 2
    const kx = Math.cos((cLat * Math.PI) / 180)
    const pad = 0.72
    const s = Math.min((size.w * pad) / ((maxLng - minLng) * kx), (size.h * pad) / (maxLat - minLat))
    return (p: LatLng) => ({ x: (p.lng - cLng) * kx * s + size.w / 2, y: -(p.lat - cLat) * s + size.h / 2 })
  }, [route, me, pins, size])

  const { traveled, toMe, later } = splitRoute(route, stops, snapshot)
  const toPath = (pts: LatLng[]) =>
    pts.map((p, i) => {
      const { x, y } = project(p)
      return `${i ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`
    }).join(' ')

  const car = project(snapshot.car)
  const passedOrder = snapshot.currentStopOrder

  // Fake city blocks: a slightly rotated street grid, a couple of parks
  const grid = useMemo(() => {
    const lines: string[] = []
    const step = 46
    for (let x = -size.h; x < size.w + size.h; x += step) lines.push(`M${x} -40 L${x + size.h * 0.18} ${size.h + 40}`)
    for (let y = -40; y < size.h + 80; y += step * 1.15) lines.push(`M-40 ${y} L${size.w + 40} ${y - size.w * 0.12}`)
    return lines.join(' ')
  }, [size])

  return (
    <div ref={ref} className="relative h-full w-full overflow-hidden bg-ground" role="img" aria-label="차량 위치와 이동 경로 지도">
      <svg width={size.w} height={size.h} className="absolute inset-0" aria-hidden>
        <rect x={size.w * 0.08} y={size.h * 0.12} width={size.w * 0.22} height={size.h * 0.16} rx="10" fill="#d6e6d3" />
        <rect x={size.w * 0.62} y={size.h * 0.66} width={size.w * 0.3} height={size.h * 0.2} rx="10" fill="#d6e6d3" />
        <path d={grid} stroke="#ffffff" strokeWidth="7" fill="none" />
        <path d={toPath(route)} stroke="#ffffff" strokeWidth="16" fill="none" strokeLinejoin="round" strokeLinecap="round" />
        {showTraveled && (
          <path d={toPath(traveled)} stroke="#a7b0bb" strokeWidth="6" fill="none" strokeLinejoin="round" strokeLinecap="round" />
        )}
        <path d={toPath(later)} stroke="#2e62f5" strokeOpacity="0.35" strokeWidth="6" fill="none" strokeLinejoin="round" strokeLinecap="round" strokeDasharray="2 9" />
        {toMe.length > 1 && (
          <path d={toPath(toMe)} stroke="#2e62f5" strokeWidth="7" fill="none" strokeLinejoin="round" strokeLinecap="round" />
        )}
      </svg>

      {stops.map((s) => {
        const p = project(s.position)
        return (
          <div key={s.id} className="absolute left-0 top-0" style={{ transform: `translate(${p.x}px, ${p.y}px)` }}>
            <StopPin stop={s} passed={s.order < passedOrder} label={labelOf?.(s)} />
          </div>
        )
      })}

      {pins.map((pin) => (
        <div key={pin.key} className="absolute left-0 top-0" style={{ transform: `translate(${project(pin.position).x}px, ${project(pin.position).y}px)` }}>
          <LabeledPin label={pin.label} tone={pin.tone} />
        </div>
      ))}

      {me && (
        <div className="absolute left-0 top-0" style={{ transform: `translate(${project(me).x}px, ${project(me).y}px)` }}>
          <MeDot />
        </div>
      )}

      <div
        className={cn('absolute left-0 top-0', animate && 'transition-transform duration-[3000ms] ease-linear')}
        style={{ transform: `translate(${car.x}px, ${car.y}px)` }}
      >
        <CarMarker heading={snapshot.car.heading} />
      </div>

      <span className="absolute right-3 top-2 rounded bg-white/80 px-1.5 py-0.5 text-[10px] text-muted">데모 지도 · 카카오맵 키를 넣으면 실제 지도로 바뀌어요</span>
    </div>
  )
}
