import { useState } from 'react'

import { dummyMyLocation, dummyTrip } from '@/data/dummyTrip'
import { useTripSimulation } from '@/hooks/useTripSimulation'

import type { TripSource } from './tripSource'

/** Demo mode at `/`: the dummy simulation, with ?state=&freeze=&me= for screenshots. */
export function useDemoTrip(): TripSource {
  const trip = dummyTrip
  const snapshot = useTripSimulation(trip)
  const [sharing, setSharing] = useState(() => new URLSearchParams(window.location.search).get('me') === '1')

  return {
    state: { status: 'ready', trip, snapshot },
    connected: true,
    sendReply: async () => {},
    location: {
      sharing,
      position: sharing ? dummyMyLocation : null,
      start: () => setSharing(true),
      stop: () => setSharing(false),
    },
  }
}
