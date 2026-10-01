import { describe, expect, it } from 'vitest'

import { dailySeries, trendPath, trendTicks } from '@/lib/stats/salesTrend'

describe('dailySeries', () => {
  it('받은 것이 없는 날을 0 으로 채운다', () => {
    const series = dailySeries(
      [
        { periodStart: '2026-09-01', amount: '1.500000', executionCount: 1 },
        { periodStart: '2026-09-03', amount: '2.000000', executionCount: 2 },
      ],
      { from: '2026-09-01', to: '2026-09-04' },
    )

    expect(series).toEqual([
      { day: '2026-09-01', amount: 1.5, executionCount: 1 },
      { day: '2026-09-02', amount: 0, executionCount: 0 },
      { day: '2026-09-03', amount: 2, executionCount: 2 },
      { day: '2026-09-04', amount: 0, executionCount: 0 },
    ])
  })

  it('기간이 없으면 받은 날만 쓴다', () => {
    const series = dailySeries([{ periodStart: '2026-09-01', amount: '1', executionCount: 1 }], {})

    expect(series).toEqual([{ day: '2026-09-01', amount: 1, executionCount: 1 }])
  })
})

describe('trendPath', () => {
  it('0 은 바닥, 최댓값은 위 여백에 붙는다', () => {
    expect(trendPath([0, 4, 2], 100, 50, 10)).toBe('M0 50 L50 10 L100 30')
  })

  it('전부 0 이어도 NaN 없이 바닥에 눕는다', () => {
    expect(trendPath([0, 0], 100, 50)).toBe('M0 50 L100 50')
  })
})

describe('trendTicks', () => {
  it('처음 · 끝을 포함해 다섯 칸을 고르게 뽑는다', () => {
    const series = Array.from({ length: 9 }, (_, i) => ({ day: `2026-09-0${i + 1}` }))

    expect(trendTicks(series)).toEqual(['09-01', '09-03', '09-05', '09-07', '09-09'])
  })

  it('하루뿐이면 그날 하나다', () => {
    expect(trendTicks([{ day: '2026-09-01' }])).toEqual(['09-01'])
  })
})
