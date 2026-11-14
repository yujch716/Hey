/**
 * Plays a passenger: opens a share link and prints every snapshot pushed over socket.io.
 *
 *   yarn watch-share <token>
 *   yarn watch-share <token> late      (also sends a quick reply after joining)
 */
import { io } from 'socket.io-client'

import type { TripSnapshot } from '../src/common/types.ts'

const SERVER_URL = process.env.SERVER_URL ?? 'http://localhost:3000'
const [token, replyType] = process.argv.slice(2)
if (!token) {
  console.error('usage: yarn watch-share <token> [out|late|waitHere]')
  process.exit(1)
}

const log = (msg: string) => console.log(`${new Date().toTimeString().slice(0, 8)}  ${msg}`)

async function main() {
  const res = await fetch(`${SERVER_URL}/share/${token}`)
  log(`GET /share/:token → ${res.status}`)
  if (!res.ok) {
    console.log(await res.text())
    process.exit(1)
  }
  const view = (await res.json()) as { vehicle: { model: string; plate: string }; stops: { order: number; label: string; isMine: boolean }[] }
  log(`vehicle ${view.vehicle.model} ${view.vehicle.plate}`)
  log(`stops   ${view.stops.map((s) => `${s.order}.${s.label}${s.isMine ? '(나)' : ''}`).join('  ')}`)

  const socket = io(`${SERVER_URL}/live`, { transports: ['websocket'] })
  let last = 0
  socket.on('connect', () => {
    socket.emit('join', { role: 'passenger', token }, async (ack: unknown) => {
      log(`joined  ${JSON.stringify(ack)}`)
      if (replyType) {
        const r = await fetch(`${SERVER_URL}/share/${token}/replies`, {
          method: 'POST',
          headers: { 'content-type': 'application/json' },
          body: JSON.stringify({ type: replyType }),
        })
        log(`POST /share/:token/replies ${replyType} → ${r.status}`)
      }
    })
  })
  socket.on('snapshot', (s: TripSnapshot) => {
    const now = Date.now()
    const gap = last ? `+${((now - last) / 1000).toFixed(1)}s` : 'first'
    last = now
    log(`snapshot ${gap.padEnd(6)} ${s.phase.padEnd(10)} eta ${String(s.etaSeconds).padStart(4)}s  stop ${s.currentStopOrder}  idx ${s.traveledIndex}`)
  })
  socket.on('joinError', (e) => log(`joinError ${JSON.stringify(e)}`))
  socket.on('disconnect', (reason) => {
    log(`disconnected (${reason})`)
    process.exit(0)
  })
}

void main()
