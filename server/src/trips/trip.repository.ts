import type { TripRecord } from './trip.model.js'

/**
 * Storage boundary. Memory for now; PostgreSQL/Redis later implement the same interface.
 * Everything is async so a real store can drop in without touching callers.
 */
export interface TripRepository {
  create(trip: TripRecord): Promise<void>
  findById(tripId: string): Promise<TripRecord | undefined>
  findByShareToken(token: string): Promise<TripRecord | undefined>
  save(trip: TripRecord): Promise<void>
}

export const TRIP_REPOSITORY = Symbol('TRIP_REPOSITORY')
