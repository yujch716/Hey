import { HttpException, Inject, Logger, type OnModuleDestroy } from '@nestjs/common'
import {
  ConnectedSocket,
  MessageBody,
  type OnGatewayInit,
  SubscribeMessage,
  WebSocketGateway,
  WebSocketServer,
} from '@nestjs/websockets'
import type { Subscription } from 'rxjs'
import type { Namespace, Socket } from 'socket.io'

import { TripsService } from '../trips/trips.service.js'

type JoinPayload =
  | { role: 'passenger'; token: string }
  | { role: 'driver'; tripId: string; driverToken: string }

const shareRoom = (token: string) => `share:${token}`
const driverRoom = (tripId: string) => `driver:${tripId}`

/**
 * namespace /live
 *   passenger: emit 'join' { role: 'passenger', token }          → receives 'snapshot' (TripSnapshot)
 *   driver:    emit 'join' { role: 'driver', tripId, driverToken } → receives
 *                'reply' { stopOrder, type, position? }
 *                'passengerLocation' { stopOrder, lat, lng }
 *                'passengerLocationStopped' { stopOrder }   (sharing turned off, or the share expired)
 * A bad join gets 'joinError' { status, message } and is disconnected.
 * CORS is set on the adapter in main.ts.
 */
@WebSocketGateway({ namespace: '/live' })
export class LiveGateway implements OnGatewayInit, OnModuleDestroy {
  @WebSocketServer() private readonly server: Namespace

  private readonly logger = new Logger(LiveGateway.name)
  private subscriptions: Subscription[] = []

  constructor(@Inject(TripsService) private readonly trips: TripsService) {}

  afterInit() {
    this.subscriptions = [
      this.trips.updates$.subscribe(({ trip, expiredTokens }) => {
        for (const { token, snapshot } of this.trips.snapshotsFor(trip)) {
          const share = trip.shares.find((s) => s.token === token)!
          // Expired shares already got their final snapshot with the update that expired them
          if (share.expired && !expiredTokens.includes(token)) continue
          this.server.to(shareRoom(token)).emit('snapshot', snapshot)
        }
        for (const token of expiredTokens) {
          this.server.in(shareRoom(token)).disconnectSockets(true)
          const stopId = trip.shares.find((s) => s.token === token)!.stopId
          const stopOrder = trip.stops.find((s) => s.id === stopId)!.order
          this.server.to(driverRoom(trip.id)).emit('passengerLocationStopped', { stopOrder })
        }
      }),
      this.trips.replies$.subscribe(({ tripId, ...reply }) => {
        this.server.to(driverRoom(tripId)).emit('reply', reply)
      }),
      this.trips.passengerLocations$.subscribe(({ tripId, stopOrder, position }) => {
        const room = this.server.to(driverRoom(tripId))
        if (position) room.emit('passengerLocation', { stopOrder, ...position })
        else room.emit('passengerLocationStopped', { stopOrder })
      }),
    ]
  }

  onModuleDestroy() {
    for (const s of this.subscriptions) s.unsubscribe()
  }

  @SubscribeMessage('join')
  async join(@ConnectedSocket() client: Socket, @MessageBody() body: JoinPayload) {
    try {
      if (body?.role === 'passenger' && typeof body.token === 'string') {
        const { trip, stop } = await this.trips.resolveShare(body.token)
        await client.join(shareRoom(body.token))
        // Send the current state right away instead of waiting for the next fix
        const mine = this.trips.snapshotsFor(trip).find((s) => s.token === body.token)!
        client.emit('snapshot', mine.snapshot)
        return { ok: true, stopOrder: stop.order }
      }
      if (body?.role === 'driver' && typeof body.tripId === 'string') {
        await this.trips.authorizeDriver(body.tripId, body.driverToken)
        await client.join(driverRoom(body.tripId))
        return { ok: true }
      }
      throw new HttpException('invalid join payload', 400)
    } catch (err) {
      const status = err instanceof HttpException ? err.getStatus() : 500
      const message = err instanceof Error ? err.message : 'join failed'
      if (status === 500) this.logger.error(err)
      client.emit('joinError', { status, message })
      client.disconnect(true)
    }
  }
}
