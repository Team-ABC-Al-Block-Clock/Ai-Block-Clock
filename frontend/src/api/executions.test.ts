import { afterEach, describe, expect, it } from 'vitest'

import { ApiError } from '@/api/errors'
import { estimateCost } from '@/api/executions'
import { saveWorkflow, type WorkflowGraph } from '@/api/workflows'
import { CREDIT_BALANCE } from '@/mocks/fixtures/credits'

/** api 레이어 + 실행 mock 을 한 번에 통과시킨다. 견적은 저장된 워크플로우를 읽는다. */

afterEach(() => {
  localStorage.removeItem('mockWorkflows')
})

/** `agt_001` 이 150 이라 다섯 개면 750 — 잔액 640 을 넘긴다. */
const chainOf = (sourceId: string, count: number): WorkflowGraph => ({
  nodes: Array.from({ length: count }, (_, i) => ({
    nodeKey: `n${i + 1}`,
    sourceType: sourceId.startsWith('agt') ? ('AGENT' as const) : ('NODE' as const),
    sourceId,
  })),
  edges: Array.from({ length: count - 1 }, (_, i) => ({
    edgeKey: `e${i + 1}`,
    sourceNodeKey: `n${i + 1}`,
    targetNodeKey: `n${i + 2}`,
  })),
})

describe('estimateCost', () => {
  it('픽스처 워크플로우의 노드 단가를 더해 준다', async () => {
    // wf_001 은 node_001·002·003 — 4 + 12 + 6.
    const estimate = await estimateCost('wf_001')

    expect(estimate.items).toHaveLength(3)
    expect(estimate.estimatedAmount).toBe('22.000000')
    expect(estimate.availableAmount).toBe(CREDIT_BALANCE.availableAmount)
  })

  it('금액은 전부 소수점 여섯 자리 문자열이다', async () => {
    const estimate = await estimateCost('wf_001')

    for (const value of [
      estimate.estimatedAmount,
      estimate.availableAmount,
      estimate.shortageAmount,
    ])
      expect(value).toMatch(/^\d+\.\d{6}$/)
    expect(estimate.items.every((item) => /^\d+\.\d{6}$/.test(item.price))).toBe(true)
  })

  it('한 줄은 명세의 네 필드만 담는다', async () => {
    const [first] = (await estimateCost('wf_001')).items

    expect(Object.keys(first).sort()).toEqual(['name', 'nodeKey', 'sourceType', 'price'].sort())
  })

  it('잔액이 넉넉하면 부족액 0 에 실행 가능이다', async () => {
    const estimate = await estimateCost('wf_001')

    expect(estimate.shortageAmount).toBe('0.000000')
    expect(estimate.executable).toBe(true)
  })

  it('잔액을 넘으면 부족액과 함께 실행 불가를 준다', async () => {
    const saved = await saveWorkflow({ ...chainOf('agt_001', 5), name: '비싼 흐름' })

    const estimate = await estimateCost(saved.workflowId)

    expect(estimate.estimatedAmount).toBe('750.000000')
    expect(estimate.shortageAmount).toBe('110.000000')
    expect(estimate.executable).toBe(false)
  })

  it('저장한 구성이 그대로 견적에 반영된다', async () => {
    const saved = await saveWorkflow({ ...chainOf('node_004', 2), name: '요약 두 번' })

    const estimate = await estimateCost(saved.workflowId)

    // node_004 가 15 — 캔버스를 고치면 금액도 따라 움직여야 한다.
    expect(estimate.estimatedAmount).toBe('30.000000')
    expect(estimate.items.map((item) => item.name)).toEqual(['요약 생성', '요약 생성'])
  })

  it('없는 워크플로우는 WORKFLOW_002 다', async () => {
    await expect(estimateCost('wf_999')).rejects.toMatchObject({ code: 'WORKFLOW_002' })
    await expect(estimateCost('wf_999')).rejects.toBeInstanceOf(ApiError)
  })
})
