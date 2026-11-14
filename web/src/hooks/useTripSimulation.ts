import { useEffect, useMemo, useState } from 'react'

import { cumulative, pointAlong } from '@/lib/geo'
import type { Phase, Trip, TripSnapshot } from '@/types'

/**
 * Dummy stand-in for the server's WebSocket stream.
 * Later: replace with a hook that subscribes to /trips/:token and returns the same TripSnapshot.
 *
 * URL params for demos and screenshots:
 *   ?state=departed|toWaypoint|atWaypoint|toMe|near|arrived|boarded  start in that phase
 *   &freeze=1                                                        stop the clock there
 */
const SPEED = 4 // m/s, about 15 km/h in stop-and-go city traffic
const WAYPOINT_DWELL = 180 // s spent at the cafe
const BOARDING_DELAY = 90 // s between arrival and the door-open event
const TIME_SCALE = 12 // simulated seconds per real second
const TICK_MS = 3000 // how often the "server" pushes a new position
const NEAR_SECONDS = 120

export function useTripSimulation(trip: Trip) {
  const cum = useMemo(() => cumulative(trip.route), [trip.route])
  const mine = trip.stops.find((s) => s.isMine)!
  const firstWaypoint = trip.stops.find((s) => s.kind === 'waypoint')

  const timeline = useMemo(() => {
    const dWay = firstWaypoint ? cum.at(firstWaypoint.routeIndex)! : 0
    const dMe = cum.at(mine.routeIndex)!
    const t1 = dWay / SPEED
    const t2 = t1 + (firstWaypoint ? WAYPOINT_DWELL : 0)
    const t3 = t2 + (dMe - dWay) / SPEED
    const t4 = t3 + BOARDING_DELAY
    return { dWay, dMe, t1, t2, t3, t4 }
  }, [cum, firstWaypoint, mine.routeIndex])

  const { startAt, freeze } = useMemo(() => {
    const params = new URLSearchParams(window.location.search)
    const state = params.get('state') as Phase | null
    const { t1, t2, t3, t4 } = timeline
    const starts: Record<Phase, number> = {
      departed: 5,
      toWaypoint: t1 * 0.45,
      atWaypoint: t1 + 40,
      toMe: t2 + 20,
      near: t3 - 100,
      arrived: t3 + 5,
      boarded: t4 + 1,
    }
    return { startAt: state ? starts[state] : 0, freeze: params.get('freeze') === '1' }
  }, [timeline])

  const [simTime, setSimTime] = useState(startAt)
  const [updatedAt, setUpdatedAt] = useState(() => Date.now())

  useEffect(() => {
    if (freeze) return
    const id = setInterval(() => {
      setSimTime((t) => t + (TICK_MS / 1000) * TIME_SCALE)
      setUpdatedAt(Date.now())
    }, TICK_MS)
    return () => clearInterval(id)
  }, [freeze])

  const snapshot: TripSnapshot = useMemo(() => {
    const { dWay, dMe, t1, t2, t3, t4 } = timeline
    const t = simTime
    const meters = t < t1 ? SPEED * t : t < t2 ? dWay : t < t3 ? dWay + SPEED * (t - t2) : dMe
    const p = pointAlong(trip.route, cum, meters)
    const etaSeconds = Math.max(0, t3 - t)

    let phase: Phase
    if (t < t1) phase = t < 20 ? 'departed' : 'toWaypoint'
    else if (t < t2) phase = 'atWaypoint'
    else if (t < t3) phase = etaSeconds <= NEAR_SECONDS ? 'near' : 'toMe'
    else if (t < t4) phase = 'arrived'
    else phase = 'boarded'

    const currentStopOrder = t < t2 && firstWaypoint ? firstWaypoint.order : mine.order

    return {
      phase,
      car: { lat: p.lat, lng: p.lng, heading: p.heading },
      traveledIndex: p.traveledIndex,
      etaSeconds,
      updatedAt,
      currentStopOrder,
    }
  }, [simTime, timeline, trip.route, cum, updatedAt, firstWaypoint, mine.order])

  return snapshot
}

/** Re-renders every second so "n초 전 갱신" stays honest. */
export function useNow(intervalMs = 1000) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), intervalMs)
    return () => clearInterval(id)
  }, [intervalMs])
  return now
}
