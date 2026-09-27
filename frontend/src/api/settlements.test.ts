import { describe, expect, it } from 'vitest'

import { getSettlementStatistics, getSettlements } from '@/api/settlements'
import { settlements } from '@/mocks/fixtures/settlements'

describe('getSettlements', () => {
  it('기본 20건과 다음 커서를 준다', async () => {
    const page = await getSettlements()

    expect(page.content).toHaveLength(20)
    expect(page.hasNext).toBe(true)
  })

  it('커서를 이어 받으면 앞 장과 겹치지 않는다', async () => {
    const first = await getSettlements({ size: 10 })
    const second = await getSettlements({ size: 10, cursor: first.nextCursor ?? undefined })

    const keys = first.content.map((s) => s.executionId + s.stepId + s.settledAt)
    expect(second.content.some((s) => keys.includes(s.executionId + s.stepId + s.settledAt))).toBe(
      false,
    )
  })

  it('기간을 한쪽만 주면 COMMON_001 이다', async () => {
    await expect(getSettlements({ from: '2026-09-01' })).rejects.toMatchObject({
      code: 'COMMON_001',
      status: 400,
    })
  })
})

describe('getSettlementStatistics', () => {
  it('합계 · 기간별 · 에이전트별 합이 서로 맞는다', async () => {
    const stats = await getSettlementStatistics({ from: '2026-09-01', to: '2026-09-25' })
    const sum = (xs: { amount: string }[]) => xs.reduce((a, x) => a + Number(x.amount), 0)

    expect(sum(stats.periods)).toBeCloseTo(Number(stats.totalAmount), 5)
    expect(sum(stats.agents)).toBeCloseTo(Number(stats.totalAmount), 5)
    expect(Number(stats.selfUseAmount)).toBeLessThanOrEqual(Number(stats.totalAmount))
  })

  it('기간별은 오래된 것부터, 에이전트별은 많이 번 것부터다', async () => {
    const stats = await getSettlementStatistics()
    const days = stats.periods.map((p) => p.periodStart)
    const amounts = stats.agents.map((a) => Number(a.amount))

    expect(days).toEqual([...days].sort())
    expect(amounts).toEqual([...amounts].sort((a, b) => b - a))
  })

  it('캔버스 노드로 받은 것은 agentId null 한 줄로 모인다', async () => {
    const stats = await getSettlementStatistics()

    expect(stats.agents.filter((a) => a.agentId === null)).toHaveLength(1)
    expect(settlements().some((s) => s.agentId === null)).toBe(true)
  })
})
