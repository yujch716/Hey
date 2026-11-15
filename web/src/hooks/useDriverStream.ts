import { useEffect, useState } from 'react'
import { io } from 'socket.io-client'

import { WS_URL } from '@/lib/api'
import { type DriverView, fetchDriverView } from '@/lib/driverApi'
import { traveledIndexOf } from '@/lib/geo'
import type { LatLng } from '@/types'

const POLL_MS = 3_000

export type DriverStream =
  | { status: 'loading' }
  | { status: 'unauthorized' }
  | { status: 'error' }
  | {
      status: 'ready'
      view: DriverView
      /** stopOrder → where that passenger said "여기서 기다릴게요" (kept only on this screen) */
      waits: Record<number, LatLng>
      connected: boolean
      /** Last route vertex the car passed (the driver API doesn't send it) */
      traveledIndex: number
    }

/**
 * The driver map's data. The driver room gets no car snapshots, so the car and the stop
 * statuses are polled from GET /trips/:tripId (every 3 s, the car's own send rate);
 * passenger replies and shared positions arrive over socket.io as they happen.
 */
export function useDriverStream(tripId: string, driverToken: string): DriverStream {
  const [state, setState] = useState<DriverStream>({ status: 'loading' })
  // Separate from `state`: the socket can connect before the first poll answers
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    let cancelled = false

    const poll = async () => {
      const r = await fetchDriverView(tripId, driverToken)
      if (cancelled) return
      setState((s) => {
        if (r.status !== 'ready') return s.status === 'ready' && r.status === 'error' ? s : { status: r.status }
        const prev = s.status === 'ready' ? s : { waits: {}, traveledIndex: 0 }
        // A completed stop's "wait here" pin is no longer useful
        const done = new Set(r.view.stops.filter((x) => x.status === 'completed').map((x) => x.order))
        const waits = Object.fromEntries(Object.entries(prev.waits).filter(([order]) => !done.has(Number(order))))
        const heading = r.view.stops.find((x) => x.order === r.view.currentStopOrder)
        const traveledIndex = r.view.car
          ? traveledIndexOf(r.view.route, r.view.car, prev.traveledIndex, heading?.routeIndex ?? r.view.route.length - 1)
          : 0
        return { status: 'ready', view: r.view, waits, connected: false, traveledIndex }
      })
    }
    void poll()
    const timer = setInterval(() => void poll(), POLL_MS)

    const socket = io(`${WS_URL}/live`)
    const update = (fn: (s: Extract<DriverStream, { status: 'ready' }>) => Extract<DriverStream, { status: 'ready' }>) =>
      setState((s) => (s.status === 'ready' ? fn(s) : s))
    const setPassenger = (stopOrder: number, position: (LatLng & { at: number }) | undefined) =>
      update((s) => ({
        ...s,
        view: { ...s.view, stops: s.view.stops.map((x) => (x.order === stopOrder ? { ...x, passengerLocation: position } : x)) },
      }))

    socket.on('connect', () => {
      socket.emit('join', { role: 'driver', tripId, driverToken })
      setConnected(true)
    })
    socket.on('disconnect', () => setConnected(false))
    socket.on('passengerLocation', (p: { stopOrder: number; lat: number; lng: number }) =>
      setPassenger(p.stopOrder, { lat: p.lat, lng: p.lng, at: Date.now() }),
    )
    socket.on('passengerLocationStopped', (p: { stopOrder: number }) => setPassenger(p.stopOrder, undefined))
    // Replies are messages only: a position pins the spot on this map, nothing else changes
    socket.on('reply', (r: { stopOrder: number; type: string; position?: LatLng }) => {
      if (r.type === 'waitHere' && r.position) update((s) => ({ ...s, waits: { ...s.waits, [r.stopOrder]: r.position! } }))
    })

    return () => {
      cancelled = true
      clearInterval(timer)
      socket.close()
    }
  }, [tripId, driverToken])

  return state.status === 'ready' ? { ...state, connected } : state
}
