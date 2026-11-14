import { cn } from '@/lib/utils'
import type { Stop } from '@/types'

/** Shared by the Kakao map (as custom overlays) and the offline mock map. Each is centered on its point. */

export function CarMarker({ heading }: { heading: number }) {
  return (
    <div className="relative -translate-x-1/2 -translate-y-1/2" aria-label="운전자 차량">
      <div className="grid size-11 place-items-center rounded-full border-[3px] border-white bg-ink shadow-[0_2px_8px_rgba(26,36,51,0.35)]">
        <svg
          viewBox="0 0 24 24"
          className="size-5 transition-transform duration-700"
          style={{ transform: `rotate(${heading}deg)` }}
          aria-hidden
        >
          <path d="M12 3 L19 20 L12 16 L5 20 Z" fill="white" />
        </svg>
      </div>
    </div>
  )
}

export function StopPin({ stop, passed }: { stop: Stop; passed: boolean }) {
  return (
    <div className="flex -translate-x-1/2 -translate-y-[15px] flex-col items-center" aria-label={`${stop.order}번째 정류지 ${stop.label}`}>
      <span
        className={cn(
          'grid size-[30px] place-items-center rounded-full border-[3px] text-[13px] font-extrabold shadow-[0_1px_4px_rgba(26,36,51,0.3)]',
          stop.isMine ? 'border-ink bg-signal text-ink' : 'border-white bg-white text-ink ring-2 ring-ink',
          passed && !stop.isMine && 'opacity-45',
        )}
      >
        {stop.order}
      </span>
      <span
        className={cn(
          'mt-1 whitespace-nowrap rounded-md px-1.5 py-0.5 text-[11px] font-semibold',
          stop.isMine ? 'bg-ink text-white' : 'bg-white/90 text-ink',
          passed && !stop.isMine && 'opacity-45',
        )}
      >
        {stop.label}
      </span>
    </div>
  )
}

export function MeDot() {
  return (
    <div className="relative -translate-x-1/2 -translate-y-1/2" aria-label="내 현재 위치">
      <span className="absolute inset-0 rounded-full bg-route/40 [animation:halo_2s_ease-out_infinite]" />
      <span className="relative block size-4 rounded-full border-[3px] border-white bg-route shadow-[0_1px_4px_rgba(26,36,51,0.35)]" />
    </div>
  )
}
