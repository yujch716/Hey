import { traveledIndexOf } from './geo.js'

const route = [
  { lat: 37.49, lng: 127.01 },
  { lat: 37.49, lng: 127.02 },
  { lat: 37.5, lng: 127.02 },
  { lat: 37.49, lng: 127.02 }, // doubles back over vertex 1
  { lat: 37.49, lng: 127.03 },
]

describe('traveledIndexOf', () => {
  it('is the start of the closest segment', () => {
    expect(traveledIndexOf(route, { lat: 37.4901, lng: 127.015 })).toBe(0)
    expect(traveledIndexOf(route, { lat: 37.495, lng: 127.0201 })).toBe(1)
  })

  it('counts a vertex the car is standing on as passed', () => {
    expect(traveledIndexOf(route, route[1])).toBe(1)
  })

  it('never goes backwards on a route that doubles back', () => {
    expect(traveledIndexOf(route, { lat: 37.4901, lng: 127.0199 }, 3)).toBe(3)
  })
})
