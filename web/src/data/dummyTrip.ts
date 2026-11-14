import type { Trip } from '@/types'

// Dummy trip around 서울 강남: car → 1 카페(경유) → 2 나 → 3 다른 탑승자
// Labels are what the driver typed: neutral, never "내 위치" (the web decides that per passenger).
const route = [
  { lat: 37.4905, lng: 127.012 },
  { lat: 37.492, lng: 127.016 },
  { lat: 37.4935, lng: 127.021 },
  { lat: 37.4945, lng: 127.0245 }, // 3: 카페
  { lat: 37.4965, lng: 127.0262 },
  { lat: 37.499, lng: 127.0281 },
  { lat: 37.5005, lng: 127.03 }, // 6: 나
  { lat: 37.5035, lng: 127.0296 },
  { lat: 37.506, lng: 127.0285 },
  { lat: 37.508, lng: 127.027 }, // 9: 다음 픽업
]

export const dummyTrip: Trip = {
  vehicle: {
    model: '아반떼',
    colorName: '흰색',
    colorHex: '#f4f5f7',
    bodyType: 'sedan',
    plate: '123가 4567',
  },
  route,
  stops: [
    { id: 's1', order: 1, kind: 'waypoint', label: '카페', position: route[3], routeIndex: 3, isMine: false },
    { id: 's2', order: 2, kind: 'pickup', label: '픽업', position: route[6], routeIndex: 6, isMine: true },
    { id: 's3', order: 3, kind: 'pickup', label: '픽업', position: route[9], routeIndex: 9, isMine: false },
  ],
}

// Where the passenger is standing when they share location (a few meters off the stop)
export const dummyMyLocation = { lat: 37.5009, lng: 127.0311 }
