import { describe, expect, it } from 'vitest'

import { growthPercent, monthRanges } from '@/lib/period'

describe('monthRanges', () => {
  it('지난달도 같은 날까지만 잘라 비교한다', () => {
    expect(monthRanges(Date.parse('2026-09-27T03:00:00Z'))).toEqual({
      current: { from: '2026-09-01', to: '2026-09-27' },
      previous: { from: '2026-08-01', to: '2026-08-27' },
    })
  })

  it('1월이면 지난달은 작년 12월이다', () => {
    expect(monthRanges(Date.parse('2027-01-05T00:00:00Z')).previous).toEqual({
      from: '2026-12-01',
      to: '2026-12-05',
    })
  })

  it('지난달이 더 짧으면 말일에서 멈춘다', () => {
    expect(monthRanges(Date.parse('2026-03-31T00:00:00Z')).previous).toEqual({
      from: '2026-02-01',
      to: '2026-02-28',
    })
  })
})

describe('growthPercent', () => {
  it('소수 한 자리까지 증감률을 준다', () => {
    expect(growthPercent(150, 120)).toBe(25)
    expect(growthPercent(90, 120)).toBe(-25)
  })

  it('지난달이 0 이면 null 이다', () => {
    expect(growthPercent(10, 0)).toBeNull()
  })
})
