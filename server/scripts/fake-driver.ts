/**
 * Plays the car app against a running server, with the stops of the web dummy trip.
 *
 *   yarn fake-driver                     (server at http://localhost:3000)
 *   SERVER_URL=http://... TICK_MS=1000 yarn fake-driver
 *
 * Like the car app it sends only where it starts and the stops; the server plans the route
 * (Kakao, or straight lines without a key). The navi is routed to one stop at a time, so each
 * location carries only the ETA to the current stop. At every stop: stopArrived → (wait) →
 * stopCompleted, then on to the next. Passenger replies are printed the way the car reads them out.
 */
import { io } from 'socket.io-client'

import { dummyTrip } from '../../web/src/data/dummyTrip.ts'
import { cumulative, pointAlong } from '../src/common/geo.ts'
import type { LatLng } from '../src/common/types.ts'

const SERVER_URL = process.env.SERVER_URL ?? 'http://localhost:3000'
const TICK_MS = Number(process.env.TICK_MS ?? 3000)
const STEP_METERS = 60 // distance covered per tick; keeps the whole run around three minutes
// The fake navi's ETA: remaining distance × 1.3 at 25 km/h, the same estimate as the server's straight-line fallback
const NAVI_SECONDS_PER_METER = 1.3 / (25 / 3.6)
const WAYPOINT_TICKS = 5 // how long the car waits at the cafe
const BOARDING_TICKS = 3 // between arriving and the door closing

type PlannedStop = { order: number; kind: 'waypoint' | 'pickup'; label: string; routeIndex: number; etaSeconds: number }

const REPLY_TTS: Record<string, string> = { out: '나왔어요', late: '5분 늦어요', waitHere: '여기서 기다릴게요' }

const origin = dummyTrip.route[0]
const stopsToSend = dummyTrip.stops
  .map(({ order, kind, label, position }) => ({ order, kind, label, position }))
  .sort((a, b) => a.order - b.order)

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))
const log = (msg: string) => console.log(`${new Date().toTimeString().slice(0, 8)}  ${msg}`)

async function api(path: string, body: unknown, driverToken?: string) {
  const res = await fetch(`${SERVER_URL}${path}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', ...(driverToken && { authorization: `Bearer ${driverToken}` }) },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(`${path} → ${res.status} ${await res.text()}`)
  return res.status === 204 ? undefined : res.json()
}

async function main() {
  // Like the car app: the response carries the planned route and each stop's planned ETA
  const created = (await api('/trips', { vehicle: dummyTrip.vehicle, origin, stops: stopsToSend })) as {
    tripId: string
    driverToken: string
    shares: { stopId: string; token: string; url: string }[]
    route: LatLng[]
    stops: PlannedStop[]
  }
  const { tripId, driverToken, route, stops } = created
  const cum = cumulative(route)

  console.log(`\ntrip ${tripId}`)
  console.log(`driverToken ${driverToken}`)
  console.log(`route ${route.length} points (${route.length === stops.length + 1 ? 'straight-line fallback' : 'Kakao directions'})`)
  console.log(`planned ETAs ${stops.map((s) => `${s.order}:${s.etaSeconds}s`).join(' ')}\n`)
  console.log('share links:')
  for (const s of created.shares) {
    const stop = stops.find((st) => `s${st.order}` === s.stopId)!
    console.log(`  ${stop.order} ${stop.label.padEnd(6)} ${s.url}`)
    console.log(`             token=${s.token}`)
  }
  console.log()

  // Listen for passenger replies like the car app would
  const socket = io(`${SERVER_URL}/live`, { transports: ['websocket'] })
  socket.on('connect', () => socket.emit('join', { role: 'driver', tripId, driverToken }))
  socket.on('reply', (r: { stopOrder: number; type: string }) =>
    log(`🔊 TTS: ${r.stopOrder}번 정류지 "${REPLY_TTS[r.type] ?? r.type}"`),
  )
  socket.on('joinError', (e) => log(`driver join failed: ${JSON.stringify(e)}`))

  let meters = 0

  const sendLocation = async (stop: PlannedStop) => {
    const p = pointAlong(route, cum, meters)
    const legEta = Math.round(Math.max(0, cum[stop.routeIndex] - meters) * NAVI_SECONDS_PER_METER)
    await api(
      `/trips/${tripId}/location`,
      { lat: p.lat, lng: p.lng, heading: p.heading, currentLegEtaSeconds: legEta, currentStopOrder: stop.order, ts: Date.now() },
      driverToken,
    )
    log(`location  ${String(Math.round(meters)).padStart(4)}m  → stop ${stop.order}  navi ETA ${legEta}s`)
  }
  const event = async (type: string, extra: { stopOrder?: number; by?: string } = {}) => {
    await api(`/trips/${tripId}/events`, { type, ...extra }, driverToken)
    log(`event     ${type}${extra.stopOrder ? ` (stop ${extra.stopOrder}${extra.by ? `, by ${extra.by}` : ''})` : ''}`)
  }
  const wait = async (stop: PlannedStop, ticks: number) => {
    for (let i = 0; i < ticks; i++) {
      await sendLocation(stop)
      await sleep(TICK_MS)
    }
  }

  // The navi gets one destination at a time: drive there, arrive, finish, then the next
  for (const stop of stops) {
    const target = cum[stop.routeIndex]
    while (meters < target) {
      meters = Math.min(target, meters + STEP_METERS)
      await sendLocation(stop)
      await sleep(TICK_MS)
    }
    await event('stopArrived', { stopOrder: stop.order })
    if (stop.kind === 'waypoint') {
      await wait(stop, WAYPOINT_TICKS)
      await event('stopCompleted', { stopOrder: stop.order, by: 'departed' }) // car pulled away from the cafe
    } else {
      await wait(stop, BOARDING_TICKS)
      await event('stopCompleted', { stopOrder: stop.order, by: 'door' }) // rear door opened and closed
    }
  }

  await event('tripEnded')
  socket.close()
  log('done')
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
