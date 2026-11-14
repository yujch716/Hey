import type { Vehicle } from '@/types'

/** What the passenger looks for at the curb: shape and colour first, then the plate. */
export function VehicleCard({ vehicle }: { vehicle: Vehicle }) {
  return (
    <section aria-label="타야 할 차량" className="flex items-center gap-4">
      <CarSilhouette color={vehicle.colorHex} body={vehicle.bodyType} />
      <div className="min-w-0 flex-1">
        <p className="text-[19px] font-bold leading-tight tracking-[-0.02em]">{vehicle.model}</p>
        <p className="mt-0.5 text-[14px] text-muted">{vehicle.colorName}</p>
      </div>
      <Plate value={vehicle.plate} />
    </section>
  )
}

/** Korean-style white plate, so it reads like the real thing at a glance. */
function Plate({ value }: { value: string }) {
  return (
    <div
      className="shrink-0 rounded-[6px] border-2 border-ink bg-white px-2.5 py-1 text-[17px] font-extrabold tracking-[0.02em] tabular-nums"
      aria-label={`차량번호 ${value}`}
    >
      {value}
    </div>
  )
}

function CarSilhouette({ color, body }: { color: string; body: Vehicle['bodyType'] }) {
  // Side view; SUV gets a taller cabin. Outline keeps light colours (흰색, 은색) visible on white.
  const cabin =
    body === 'suv'
      ? 'M16 22 L24 9 Q26 6 30 6 L58 6 Q62 6 64 9 L72 22 Z'
      : 'M18 22 L28 11 Q30 9 34 9 L56 9 Q60 9 63 12 L72 22 Z'
  return (
    <svg viewBox="0 0 92 40" className="h-11 w-[84px] shrink-0" aria-hidden>
      <path d={cabin} fill={color} stroke="#1a2433" strokeWidth="1.6" strokeLinejoin="round" />
      <path d="M30 20 L35 12 L46 12 L46 20 Z M49 20 L49 12 L58 12 Q60 12 62 14 L66 20 Z" fill="#c9d2dc" />
      <path
        d="M6 30 Q6 22 14 21 L78 21 Q86 22 87 28 L87 31 Q87 33 85 33 L8 33 Q6 33 6 31 Z"
        fill={color}
        stroke="#1a2433"
        strokeWidth="1.6"
        strokeLinejoin="round"
      />
      <circle cx="24" cy="33" r="6" fill="#1a2433" />
      <circle cx="24" cy="33" r="2.4" fill="#c9d2dc" />
      <circle cx="70" cy="33" r="6" fill="#1a2433" />
      <circle cx="70" cy="33" r="2.4" fill="#c9d2dc" />
    </svg>
  )
}
