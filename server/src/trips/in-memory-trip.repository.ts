import { Injectable } from '@nestjs/common'

import type { TripRecord } from './trip.model.js'
import type { TripRepository } from './trip.repository.js'

@Injectable()
export class InMemoryTripRepository implements TripRepository {
  private readonly trips = new Map<string, TripRecord>()
  private readonly tripIdByShareToken = new Map<string, string>()

  async create(trip: TripRecord) {
    this.trips.set(trip.id, structuredClone(trip))
    for (const share of trip.shares) this.tripIdByShareToken.set(share.token, trip.id)
  }

  async findById(tripId: string) {
    const trip = this.trips.get(tripId)
    return trip && structuredClone(trip)
  }

  async findByShareToken(token: string) {
    const tripId = this.tripIdByShareToken.get(token)
    return tripId === undefined ? undefined : this.findById(tripId)
  }

  async save(trip: TripRecord) {
    this.trips.set(trip.id, structuredClone(trip))
  }
}
