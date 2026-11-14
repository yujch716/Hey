import { MapPin } from 'lucide-react'
import { useState } from 'react'
import { Toaster } from 'sonner'

import { MapView } from '@/components/map/MapView'
import { QuickReplies } from '@/components/QuickReplies'
import { StatusBanner } from '@/components/StatusBanner'
import { VehicleCard } from '@/components/VehicleCard'
import { dummyMyLocation, dummyTrip } from '@/data/dummyTrip'
import { useNow, useTripSimulation } from '@/hooks/useTripSimulation'
import { cn } from '@/lib/utils'
import type { QuickReply } from '@/types'

export default function App() {
  const trip = dummyTrip
  const snapshot = useTripSimulation(trip)
  const now = useNow()
  const [sent, setSent] = useState<QuickReply | null>(null)
  const [sharing, setSharing] = useState(() => new URLSearchParams(window.location.search).get('me') === '1')

  const done = snapshot.phase === 'boarded'

  return (
    // Phone-width frame: full screen on a phone, centered column on a desktop.
    <div className="mx-auto flex h-[100dvh] w-full max-w-[430px] flex-col overflow-hidden bg-white sm:shadow-[0_0_0_1px_rgba(26,36,51,0.06),0_20px_60px_rgba(26,36,51,0.18)]">
      <StatusBanner snapshot={snapshot} stops={trip.stops} vehicle={trip.vehicle} now={now} />

      <main className={cn('relative min-h-0 flex-1', done && 'grayscale')}>
        <MapView route={trip.route} stops={trip.stops} snapshot={snapshot} me={sharing && !done ? dummyMyLocation : null} />
      </main>

      <footer className="relative z-10 -mt-4 rounded-t-[20px] bg-white px-5 pb-[max(20px,env(safe-area-inset-bottom))] pt-5 shadow-[0_-6px_20px_rgba(26,36,51,0.08)]">
        <VehicleCard vehicle={trip.vehicle} />

        {!done && (
          <>
            <hr className="my-4 border-line" />
            <QuickReplies sent={sent} onSend={setSent} />
            <button
              type="button"
              onClick={() => setSharing((v) => !v)}
              aria-pressed={sharing}
              className="mt-3 flex w-full items-center justify-center gap-1.5 py-1 text-[13px] text-muted focus-visible:outline-2 focus-visible:outline-route"
            >
              <MapPin className="size-3.5" aria-hidden />
              {sharing ? '내 위치 공유 중이에요. 그만 공유하기' : '내 위치도 운전자에게 보여주기'}
            </button>
          </>
        )}
      </footer>

      <Toaster position="top-center" toastOptions={{ className: 'font-sans' }} />
    </div>
  )
}
