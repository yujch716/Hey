import { MapPin, WifiOff } from 'lucide-react'
import type { ReactNode } from 'react'

import { MapView } from '@/components/map/MapView'
import { QuickReplies } from '@/components/QuickReplies'
import { StatusBanner } from '@/components/StatusBanner'
import { VehicleCard } from '@/components/VehicleCard'
import type { TripSource } from '@/hooks/tripSource'
import { useNow } from '@/hooks/useTripSimulation'
import { cn } from '@/lib/utils'
import type { QuickReply } from '@/types'

/** The passenger screen. Knows nothing about where `source` gets its data. */
export function TripScreen({ source }: { source: TripSource }) {
  const { state } = source

  switch (state.status) {
    case 'loading':
      return <Frame><EndScreen headline="불러오는 중이에요" note="잠시만 기다려 주세요" /></Frame>
    case 'notFound':
      return <Frame><EndScreen headline="링크를 찾을 수 없어요" note="받은 주소를 다시 확인해 주세요" /></Frame>
    case 'expired':
      return <Frame><EndScreen headline="끝난 픽업이에요" note="이 링크는 더 이상 열리지 않아요" /></Frame>
    case 'error':
      return (
        <Frame>
          <EndScreen headline="연결할 수 없어요" note="네트워크를 확인하고 다시 열어 주세요">
            <button
              type="button"
              onClick={() => window.location.reload()}
              className="mt-6 rounded-xl bg-ink px-5 py-3 text-[15px] font-semibold text-white focus-visible:outline-2 focus-visible:outline-route"
            >
              다시 시도
            </button>
          </EndScreen>
        </Frame>
      )
    case 'ready':
      return <Frame><LiveTrip source={source} trip={state.trip} snapshot={state.snapshot} /></Frame>
  }
}

type ReadyState = Extract<TripSource['state'], { status: 'ready' }>

function LiveTrip({ source, trip, snapshot }: { source: TripSource } & Omit<ReadyState, 'status'>) {
  const now = useNow()
  const { location } = source
  const done = snapshot.phase === 'boarded'

  const reply = (type: QuickReply) =>
    source.sendReply(type, type === 'waitHere' && location.sharing ? (location.position ?? undefined) : undefined)

  return (
    <>
      <StatusBanner snapshot={snapshot} stops={trip.stops} vehicle={trip.vehicle} now={now} />

      {/* In the flow, not over the map, so it never hides a pin: the map just gets shorter */}
      {!source.connected && !done && (
        <p role="status" className="relative z-10 flex items-center gap-2 bg-ink px-5 py-2.5 text-[13px] font-medium text-white">
          <WifiOff className="size-4 shrink-0" aria-hidden />
          연결이 끊겼어요. 다시 연결하는 중이에요
        </p>
      )}

      <main className={cn('relative min-h-0 flex-1', done && 'grayscale')}>
        <MapView route={trip.route} stops={trip.stops} snapshot={snapshot} me={location.sharing && !done ? location.position : null} />
      </main>

      <footer className="relative z-10 -mt-4 rounded-t-[20px] bg-white px-5 pb-[max(20px,env(safe-area-inset-bottom))] pt-5 shadow-[0_-6px_20px_rgba(26,36,51,0.08)]">
        <VehicleCard vehicle={trip.vehicle} />

        {!done && (
          <>
            <hr className="my-4 border-line" />
            <QuickReplies onSend={reply} />
            <button
              type="button"
              onClick={location.sharing ? location.stop : location.start}
              aria-pressed={location.sharing}
              className="mt-3 flex w-full items-center justify-center gap-1.5 py-1 text-[13px] text-muted focus-visible:outline-2 focus-visible:outline-route"
            >
              <MapPin className="size-3.5" aria-hidden />
              {location.sharing ? '내 위치 공유 중이에요. 그만 공유하기' : '내 위치도 운전자에게 보여주기'}
            </button>
          </>
        )}
      </footer>
    </>
  )
}

/** Phone-width frame: full screen on a phone, centered column on a desktop. */
export function Frame({ children }: { children: ReactNode }) {
  return (
    <div className="mx-auto flex h-[100dvh] w-full max-w-[430px] flex-col overflow-hidden bg-white sm:shadow-[0_0_0_1px_rgba(26,36,51,0.06),0_20px_60px_rgba(26,36,51,0.18)]">
      {children}
    </div>
  )
}

/** Full-screen message in the same tone as the boarded banner. */
export function EndScreen({ headline, note, children }: { headline: string; note: string; children?: ReactNode }) {
  return (
    <section className="flex flex-1 flex-col justify-center bg-paper px-6" aria-live="polite">
      <h1 className="text-[34px] font-extrabold leading-[1.1] tracking-[-0.03em]">{headline}</h1>
      <p className="mt-3 text-[15px] leading-snug text-muted">{note}</p>
      {children && <div>{children}</div>}
    </section>
  )
}
