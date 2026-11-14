import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { io } from 'socket.io-client'
import { toast } from 'sonner'

import { deleteLocation, fetchShare, postLocation, postReply, WS_URL } from '@/lib/api'
import type { LatLng, QuickReply, TripSnapshot } from '@/types'

import type { LocationShare, TripSource, TripState } from './tripSource'

const LOCATION_SEND_MS = 10_000

/**
 * Live mode at `/t/:token`: initial data from GET /share/:token, then 'snapshot' pushes
 * over socket.io /live. Returns the same shape as the demo hook.
 */
export function useTripStream(token: string): TripSource {
  const [state, setState] = useState<TripState>({ status: 'loading' })
  const [connected, setConnected] = useState(true)

  useEffect(() => {
    let cancelled = false
    let socket: ReturnType<typeof io> | null = null

    void fetchShare(token).then((result) => {
      if (cancelled) return
      setState(result)
      if (result.status !== 'ready') return

      socket = io(`${WS_URL}/live`)
      socket.on('connect', () => {
        setConnected(true)
        socket!.emit('join', { role: 'passenger', token })
      })
      socket.on('snapshot', (snapshot: TripSnapshot) =>
        setState((s) => (s.status === 'ready' ? { ...s, snapshot } : s)),
      )
      socket.on('joinError', (e: { status: number }) => {
        // e.g. the server restarted and forgot the trip (404), or the pickup ended while we were away (410)
        if (e.status === 404) setState({ status: 'notFound' })
        else if (e.status === 410) setState({ status: 'expired' })
      })
      socket.on('disconnect', (reason) => {
        if (reason === 'io server disconnect') {
          // The server closes a share once it expires. Keep the boarded screen if that's what we were showing.
          setState((s) =>
            s.status === 'notFound' || (s.status === 'ready' && s.snapshot.phase === 'boarded') ? s : { status: 'expired' },
          )
          return
        }
        setConnected(false) // socket.io reconnects on its own
      })
      socket.on('connect_error', () => setConnected(false))
    })

    return () => {
      cancelled = true
      socket?.close()
    }
  }, [token])

  const sendReply = useCallback((type: QuickReply, position?: LatLng) => postReply(token, type, position), [token])

  const active = state.status === 'ready' && state.snapshot.phase !== 'boarded'
  const location = useLocationShare(token, active)

  return { state, connected: connected || state.status !== 'ready', sendReply, location }
}

/**
 * Browser geolocation → POST /share/:token/location about every 10 s.
 * Stops (and DELETEs) when turned off, when `active` goes false (boarded), or on unmount.
 */
function useLocationShare(token: string, active: boolean): LocationShare {
  const [wanted, setWanted] = useState(false)
  const [position, setPosition] = useState<LatLng | null>(null)
  const latest = useRef<LatLng | null>(null)
  const sharing = wanted && active
  // Read in the effect cleanup: after boarding the server already told the driver, so skip the DELETE.
  // A layout effect, so it is updated before the passive cleanup below runs.
  const activeRef = useRef(active)
  useLayoutEffect(() => {
    activeRef.current = active
  }, [active])

  useEffect(() => {
    if (!sharing) return

    let posted = false
    let lastSent = 0
    const send = (p: LatLng) => {
      lastSent = Date.now()
      posted = true
      postLocation(token, p).catch(() => {}) // the next fix retries
    }

    const watchId = navigator.geolocation.watchPosition(
      (pos) => {
        const p = { lat: pos.coords.latitude, lng: pos.coords.longitude }
        latest.current = p
        setPosition(p)
        if (Date.now() - lastSent >= LOCATION_SEND_MS) send(p)
      },
      (err) => {
        if (err.code !== err.PERMISSION_DENIED) return // timeouts and bad fixes: keep waiting
        toast.error('위치 권한이 꺼져 있어요', { description: '브라우저 설정에서 위치 권한을 허용하면 공유할 수 있어요' })
        setWanted(false)
      },
      { enableHighAccuracy: true, maximumAge: 5_000, timeout: 30_000 },
    )
    // watchPosition stays quiet while standing still; resend so the driver knows we're still sharing
    const resend = setInterval(() => {
      if (latest.current && Date.now() - lastSent >= LOCATION_SEND_MS) send(latest.current)
    }, LOCATION_SEND_MS)

    return () => {
      navigator.geolocation.clearWatch(watchId)
      clearInterval(resend)
      latest.current = null
      setPosition(null)
      if (posted && activeRef.current) deleteLocation(token).catch(() => {})
    }
  }, [sharing, token])

  return {
    sharing,
    position,
    start: () => {
      if ('geolocation' in navigator) setWanted(true)
      else toast.error('이 브라우저에서는 위치를 공유할 수 없어요')
    },
    stop: () => setWanted(false),
  }
}
