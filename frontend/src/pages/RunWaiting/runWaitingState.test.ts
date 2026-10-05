import { describe, expect, it } from 'vitest'

import type { CostEstimate } from '@/api/executions'
import { ApiError } from '@/api/errors'
import type { CanvasNodeData } from '@/lib/canvas/canvasWorkflow'

import {
  clamp,
  costRatio,
  failedBeforeReserve,
  itemsNoticeOf,
  leadAgentOf,
  runNoticeOf,
} from './runWaitingState'

const node = (id: string, sourceType: CanvasNodeData['sourceType']) =>
  ({ id, name: id, sourceType }) as CanvasNodeData

const estimate = (estimatedAmount: string, availableAmount: string): CostEstimate => ({
  estimatedAmount,
  availableAmount,
  shortageAmount: '0.000000',
  executable: true,
  items: [],
})

describe('clamp', () => {
  it('넘치면 끝을 줄임표로 바꾸고, 넘치지 않으면 그대로 둔다', () => {
    expect(clamp('가나다라마', 4)).toBe('가나다…')
    expect(clamp('가나다', 4)).toBe('가나다')
  })
})

describe('leadAgentOf', () => {
  const nodes = [node('n1', 'NODE'), node('a2', 'AGENT'), node('a1', 'AGENT')]

  it('연결 순서에서 가장 앞의 에이전트를 고른다', () => {
    const chainAt = new Map([
      ['n1', 1],
      ['a1', 2],
      ['a2', 3],
    ])
    expect(leadAgentOf(nodes, chainAt)?.id).toBe('a1')
  })

  it('갈래가 있어 순서를 모르면 배열 순서를 따른다', () => {
    expect(leadAgentOf(nodes, null)?.id).toBe('a2')
  })

  it('에이전트가 없으면 undefined', () => {
    expect(leadAgentOf([node('n1', 'NODE')], null)).toBeUndefined()
  })
})

describe('costRatio', () => {
  it('잔액 대비 견적을 1 까지만 채운다', () => {
    expect(costRatio(estimate('25', '100'))).toBe(0.25)
    expect(costRatio(estimate('300', '100'))).toBe(1)
  })

  it('잔액이 0 이면 견적이 있을 때만 가득 채운다', () => {
    expect(costRatio(estimate('5', '0'))).toBe(1)
    expect(costRatio(estimate('0', '0'))).toBe(0)
  })

  it('견적이 없으면 0', () => {
    expect(costRatio(undefined)).toBe(0)
  })
})

describe('failedBeforeReserve', () => {
  it('떼어 두기 전에 끊긴 코드만 참이다', () => {
    expect(failedBeforeReserve(new ApiError('x', { code: 'PAYMENT_008', status: 400 }))).toBe(true)
    expect(failedBeforeReserve(new ApiError('x', { code: 'EXECUTION_009', status: 400 }))).toBe(
      true,
    )
  })

  it('코드가 없거나 모르는 실패는 접수됐을 수 있어 거짓이다', () => {
    expect(failedBeforeReserve(new ApiError('x', { status: 502 }))).toBe(false)
    expect(failedBeforeReserve(new ApiError('x'))).toBe(false)
    expect(failedBeforeReserve(new Error('x'))).toBe(false)
  })
})

describe('runNoticeOf', () => {
  it('서버 문구를 그대로 쓴다', () => {
    expect(runNoticeOf(new ApiError('잔액이 부족합니다', { status: 400 }), 'w1')).toBe(
      '잔액이 부족합니다',
    )
  })

  it('응답을 못 받았으면 다시 시도하라고 안내한다', () => {
    expect(runNoticeOf(new ApiError('Network Error'), 'w1')).toBe(
      '실행을 맡기지 못했습니다. 잠시 후 다시 시도해 주세요',
    )
  })

  it('실패가 없으면 워크플로우가 없을 때만 안내한다', () => {
    expect(runNoticeOf(null, undefined)).toBe('저장된 워크플로우를 열어야 실행할 수 있습니다')
    expect(runNoticeOf(null, 'w1')).toBe('')
  })
})

describe('itemsNoticeOf', () => {
  const base = { workflowId: 'w1', pending: false, failed: false, count: 2 }

  it('목록이 비어 보이는 이유를 앞선 것부터 고른다', () => {
    expect(itemsNoticeOf({ ...base, workflowId: undefined, pending: true })).toBe(
      '저장된 워크플로우를 열면 단계별 비용을 확인할 수 있습니다',
    )
    expect(itemsNoticeOf({ ...base, pending: true })).toBe('견적을 계산하는 중입니다')
    expect(itemsNoticeOf({ ...base, failed: true })).toBe('견적을 불러오지 못했습니다')
    expect(itemsNoticeOf({ ...base, count: 0 })).toBe('놓인 노드가 없습니다')
  })

  it('보여 줄 줄이 있으면 null', () => {
    expect(itemsNoticeOf(base)).toBeNull()
  })
})
