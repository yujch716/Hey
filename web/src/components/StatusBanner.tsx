import { stopLabel, stopName } from '@/lib/stops'
import { cn } from '@/lib/utils'
import type { Phase, Stop, TripSnapshot, Vehicle } from '@/types'

type Props = {
  snapshot: TripSnapshot
  stops: Stop[]
  vehicle: Vehicle
  now: number
}

const SIGNAL_PHASES: Phase[] = ['near', 'arrived']

function minutes(sec: number) {
  return Math.max(1, Math.round(sec / 60))
}

function clock(ms: number) {
  return new Intl.DateTimeFormat('ko-KR', { hour: 'numeric', minute: '2-digit' }).format(ms)
}

function copyFor(snapshot: TripSnapshot, stops: Stop[], vehicle: Vehicle) {
  const { phase, currentStop } = snapshot
  const mine = stops.find((s) => s.isMine)!
  const nextIsMine = stops.find((s) => s.order > currentStop.order)?.isMine ?? false
  const thenMe = nextIsMine ? '. 그다음이 내 차례예요' : ''
  const waypoint = currentStop.kind === 'waypoint'

  switch (phase) {
    case 'departed':
      return {
        headline: '출발했어요',
        note: currentStop.order < mine.order ? `${stopName(currentStop)}에 먼저 들렀다 와요` : '나에게 오는 중이에요',
      }
    case 'toEarlierStop':
      return {
        headline: null,
        note: waypoint
          ? `${currentStop.label}에 들르는 중이에요${thenMe}`
          : `${stopName(currentStop)}로 가는 중이에요${thenMe}`,
      }
    case 'atEarlierStop':
      return {
        headline: null,
        note: waypoint
          ? `${currentStop.label}에 잠깐 들렀어요${thenMe || '. 곧 다시 출발해요'}`
          : `${stopName(currentStop)}에서 태우는 중이에요${thenMe}`,
      }
    case 'toMe':
      return { headline: null, note: `이제 ${mine.order}번째, 나에게 오는 중이에요` }
    case 'near':
      return { headline: '나와 주세요', note: `${vehicle.colorName} ${vehicle.model}가 곧 도착해요` }
    case 'arrived':
      return { headline: '도착했어요', note: `${vehicle.colorName} ${vehicle.model}, ${vehicle.plate}를 찾아 타세요` }
    case 'boarded':
      return { headline: '탑승 완료', note: '위치 공유가 끝났어요. 이 링크는 더 이상 열리지 않아요' }
  }
}

export function StatusBanner({ snapshot, stops, vehicle, now }: Props) {
  const { phase, etaSeconds } = snapshot
  const signal = SIGNAL_PHASES.includes(phase)
  const done = phase === 'boarded'
  const copy = copyFor(snapshot, stops, vehicle)
  const secondsAgo = Math.max(0, Math.round((now - snapshot.updatedAt) / 1000))
  const showEta = !done && phase !== 'arrived'

  return (
    <header
      className={cn(
        'relative z-10 px-5 pb-4 pt-5 transition-colors duration-500',
        signal ? 'bg-signal' : done ? 'bg-paper' : 'bg-white',
      )}
      aria-live="polite"
    >
      <div className="flex items-end justify-between gap-3">
        <div className="min-w-0">
          {copy.headline ? (
            <h1 className="text-[40px] font-extrabold leading-[1.05] tracking-[-0.03em]">{copy.headline}</h1>
          ) : (
            <h1 className="flex items-baseline gap-1.5 leading-none">
              <span className="text-[64px] font-extrabold tracking-[-0.05em] tabular-nums">{minutes(etaSeconds)}</span>
              <span className="text-[22px] font-bold tracking-[-0.02em]">분 뒤 도착</span>
            </h1>
          )}
        </div>
        {showEta && (
          <div className="shrink-0 pb-1 text-right">
            <div className={cn('text-[13px]', signal ? 'text-ink/70' : 'text-muted')}>도착 예정</div>
            <div className="text-[17px] font-bold tabular-nums">{clock(now + etaSeconds * 1000)}</div>
          </div>
        )}
      </div>

      <p className={cn('mt-2 text-[15px] leading-snug', signal ? 'text-ink' : 'text-muted')}>{copy.note}</p>

      <StepTrack snapshot={snapshot} stops={stops} signal={signal} />

      {!done && (
        <p className={cn('mt-3 text-[12px] tabular-nums', signal ? 'text-ink/60' : 'text-muted')}>
          {secondsAgo <= 1 ? '방금 갱신' : `${secondsAgo}초 전 갱신`}
        </p>
      )}
    </header>
  )
}

function StepTrack({ snapshot, stops, signal }: { snapshot: TripSnapshot; stops: Stop[]; signal: boolean }) {
  const { phase, currentStopOrder } = snapshot
  const passedMine = phase === 'arrived' || phase === 'boarded'

  const isDone = (s: Stop) =>
    s.order < currentStopOrder || (s.isMine && passedMine) || (phase === 'atEarlierStop' && s.order === currentStopOrder)
  const isCurrent = (s: Stop) => !isDone(s) && s.order === currentStopOrder

  const nodes = [{ key: 'start', label: '출발', done: true, current: false, mine: false }, ...stops.map((s) => ({
    key: s.id,
    label: stopLabel(s),
    done: isDone(s),
    current: isCurrent(s),
    mine: s.isMine,
  }))]

  return (
    <ol className="mt-4 flex items-start" aria-label="이동 순서">
      {nodes.map((n, i) => {
        return (
          <li key={n.key} className="flex flex-1 flex-col items-center last:flex-none">
            <div className="flex h-[26px] w-full items-center">
              <span
                className={cn(
                  'grid size-[22px] shrink-0 place-items-center rounded-full border-2 text-[11px] font-bold',
                  n.mine && 'size-[26px] text-[12px]',
                  n.done
                      ? 'border-ink bg-ink text-white'
                      : n.current
                        ? cn('border-ink', signal ? 'bg-signal' : 'bg-white')
                        : cn('border-ink/25 text-ink/40', signal ? 'bg-signal' : 'bg-white'),
                )}
              >
                {i === 0 ? '' : i}
              </span>
              {i < nodes.length - 1 && (
                <span
                  className={cn(
                    'mx-1 h-[3px] flex-1 rounded-full',
                    nodes.at(i + 1)!.done || nodes.at(i + 1)!.current ? 'bg-ink' : 'bg-ink/15',
                  )}
                />
              )}
            </div>
            <span
              className={cn(
                'mt-1.5 self-start whitespace-nowrap text-[12px]',
                n.mine ? 'font-bold' : n.done || n.current ? 'font-medium' : 'text-ink/50',
              )}
            >
              {n.label}
            </span>
          </li>
        )
      })}
    </ol>
  )
}
