import { computePhase, DWELL_SECONDS, type EtaInput, etaToStop, NEAR_SECONDS, type PhaseInput } from './phase.js'

// Same layout as the web dummy trip: 1 카페(경유) → 2 나 → 3 다음 픽업
function phase(overrides: Partial<PhaseInput> = {}) {
  return computePhase({
    myStop: { order: 2 },
    firstStopOrder: 1,
    metersFromStart: 500,
    myEtaSeconds: 600,
    currentStopOrder: 1,
    arrivedOrders: [],
    completedOrders: [],
    ...overrides,
  })
}

describe('computePhase', () => {
  describe('departed', () => {
    it('before the first location fix', () => {
      expect(phase({ metersFromStart: null })).toBe('departed')
    })

    it('while the car is still near where it started', () => {
      expect(phase({ metersFromStart: 149 })).toBe('departed')
    })

    it('also when my stop is the first one', () => {
      expect(phase({ myStop: { order: 1 }, metersFromStart: 20, myEtaSeconds: 600 })).toBe('departed')
    })

    it('not once the car has left the first stop behind', () => {
      expect(phase({ currentStopOrder: 2, completedOrders: [1], metersFromStart: 0 })).toBe('toMe')
    })
  })

  describe('an earlier stop (the cafe)', () => {
    it('toEarlierStop once the car is moving', () => {
      expect(phase()).toBe('toEarlierStop')
    })

    it('stays toEarlierStop even if my ETA is short, because the car stops first', () => {
      expect(phase({ myEtaSeconds: 30 })).toBe('toEarlierStop')
    })

    it('atEarlierStop after stopArrived', () => {
      expect(phase({ arrivedOrders: [1] })).toBe('atEarlierStop')
    })

    it('atEarlierStop even right at the start', () => {
      expect(phase({ arrivedOrders: [1], metersFromStart: 10 })).toBe('atEarlierStop')
    })

    it('not atEarlierStop from the navi ETA alone, without stopArrived', () => {
      expect(phase({ myEtaSeconds: 0 })).toBe('toEarlierStop')
    })
  })

  describe('heading to me', () => {
    const toMe = { currentStopOrder: 2, arrivedOrders: [1], completedOrders: [1] }

    it('toMe when the ETA is above the near threshold', () => {
      expect(phase({ ...toMe, myEtaSeconds: NEAR_SECONDS + 1 })).toBe('toMe')
    })

    it('near exactly at the threshold (120s)', () => {
      expect(phase({ ...toMe, myEtaSeconds: NEAR_SECONDS })).toBe('near')
    })

    it('still near (not arrived) at ETA 0 until the car says stopArrived', () => {
      expect(phase({ ...toMe, myEtaSeconds: 0 })).toBe('near')
    })

    it('arrived on stopArrived, whatever the ETA', () => {
      expect(phase({ ...toMe, myEtaSeconds: 300, arrivedOrders: [1, 2] })).toBe('arrived')
    })
  })

  describe('after my turn', () => {
    it('boarded on stopCompleted', () => {
      expect(phase({ currentStopOrder: 3, arrivedOrders: [1, 2], completedOrders: [1, 2] })).toBe('boarded')
    })

    it('boarded wins over stopArrived', () => {
      expect(phase({ currentStopOrder: 2, arrivedOrders: [2], completedOrders: [2] })).toBe('boarded')
    })

    it('boarded once the car heads past my stop, even if stopCompleted was lost', () => {
      expect(phase({ currentStopOrder: 3, arrivedOrders: [2] })).toBe('boarded')
    })

    it('still arrived while the car waits at my stop', () => {
      expect(phase({ currentStopOrder: 2, arrivedOrders: [1, 2], completedOrders: [1] })).toBe('arrived')
    })
  })

  describe('a later passenger (stop 3)', () => {
    const later = { myStop: { order: 3 } }

    it('sees the cafe as an earlier stop', () => {
      expect(phase({ ...later, arrivedOrders: [1] })).toBe('atEarlierStop')
    })

    it('sees the car heading to the previous pickup as toEarlierStop', () => {
      expect(phase({ ...later, currentStopOrder: 2, completedOrders: [1], myEtaSeconds: 100 })).toBe('toEarlierStop')
    })

    it('sees waiting at the previous pickup as atEarlierStop', () => {
      expect(phase({ ...later, currentStopOrder: 2, arrivedOrders: [1, 2], completedOrders: [1] })).toBe('atEarlierStop')
    })

    it('moves on once the previous pickup completes', () => {
      expect(
        phase({ ...later, currentStopOrder: 3, arrivedOrders: [1, 2], completedOrders: [1, 2], myEtaSeconds: 300 }),
      ).toBe('toMe')
    })

    it('near once its own stop is next and close', () => {
      expect(phase({ ...later, currentStopOrder: 3, completedOrders: [1, 2], myEtaSeconds: NEAR_SECONDS })).toBe('near')
    })
  })
})

describe('etaToStop', () => {
  // legSeconds: start→1 = 200, 1→2 = 150, 2→3 = 100
  const stops = [
    { order: 1, legSeconds: 200 },
    { order: 2, legSeconds: 150 },
    { order: 3, legSeconds: 100 },
  ]
  const base: EtaInput = { stops, currentStopOrder: 1, currentLegEtaSeconds: 80, currentArrivedAt: null, now: 1_000_000 }
  const eta = (target: number, overrides: Partial<EtaInput> = {}) => etaToStop({ ...base, ...overrides }, target)

  it('the current stop is the navi ETA', () => {
    expect(eta(1)).toBe(80)
  })

  it('the next stop adds its leg and one dwell', () => {
    expect(eta(2)).toBe(80 + DWELL_SECONDS + 150)
  })

  it('a stop two ahead adds both legs and a dwell for each stop before it', () => {
    expect(eta(3)).toBe(80 + DWELL_SECONDS + 150 + DWELL_SECONDS + 100)
  })

  it('heading to stop 2: stop 3 is navi ETA + leg 2→3 + one dwell', () => {
    expect(eta(3, { currentStopOrder: 2, currentLegEtaSeconds: 40 })).toBe(40 + DWELL_SECONDS + 100)
  })

  it('counts down with the navi ETA', () => {
    const far = eta(3, { currentStopOrder: 2, currentLegEtaSeconds: 90 })
    const near = eta(3, { currentStopOrder: 2, currentLegEtaSeconds: 30 })
    expect(far - near).toBe(60)
  })

  it('uses the planned leg before the first location', () => {
    expect(eta(2, { currentLegEtaSeconds: null })).toBe(200 + DWELL_SECONDS + 150)
  })

  it('while standing at the current stop, the dwell shrinks with time waited', () => {
    const arrived = base.now - 20_000
    expect(eta(2, { currentArrivedAt: arrived })).toBe(DWELL_SECONDS - 20 + 150)
    expect(eta(1, { currentArrivedAt: arrived })).toBe(0)
  })

  it('the dwell never goes negative after a long wait', () => {
    expect(eta(2, { currentArrivedAt: base.now - 600_000 })).toBe(150)
  })

  it('stops already passed are 0', () => {
    expect(eta(1, { currentStopOrder: 2 })).toBe(0)
  })
})
