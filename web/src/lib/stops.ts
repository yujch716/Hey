import type { CurrentStop, Stop } from '@/types'

/**
 * What a passenger sees on a pin or step. The server's label is never written from one passenger's
 * point of view, so the wording is decided here: my stop is "내 위치", other passengers are just "픽업",
 * and only waypoints (카페 등) keep the driver's label.
 */
export function stopLabel(stop: Pick<Stop, 'kind' | 'label' | 'isMine'>) {
  if (stop.isMine) return '내 위치'
  return stop.kind === 'pickup' ? '픽업' : stop.label
}

/** A stop in a sentence: "카페" for a waypoint, "2번 정류지" for another passenger (never a name). */
export function stopName(stop: CurrentStop) {
  return stop.kind === 'waypoint' ? stop.label : `${stop.order}번 정류지`
}
