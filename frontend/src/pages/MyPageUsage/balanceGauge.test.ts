import { describe, expect, it } from 'vitest'

import { GAUGE_LENGTH, gaugeArc } from './balanceGauge'

describe('gaugeArc', () => {
  it('사용 가능 / (사용 가능 + 실행 중) 만큼 고리를 채운다', () => {
    expect(gaugeArc('30', '10')).toBeCloseTo(GAUGE_LENGTH * 0.75)
    expect(gaugeArc('5', '0')).toBe(GAUGE_LENGTH)
  })

  it('잔액이 없거나 아직 모르면 빈 고리다', () => {
    expect(gaugeArc('0', '0')).toBe(0)
    expect(gaugeArc(undefined, undefined)).toBe(0)
  })
})
