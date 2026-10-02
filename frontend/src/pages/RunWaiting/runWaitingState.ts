import type { CostEstimate } from '@/api/executions'
import { ApiError } from '@/api/errors'
import type { CanvasNodeData } from '@/lib/canvas/canvasWorkflow'

import { INSUFFICIENT } from './config'

/** 넘치면 끝을 줄임표로 바꾼다. 자른 티를 내야 잘린 문장으로 읽히지 않는다. */
export const clamp = (text: string, max: number) =>
  text.length > max ? `${text.slice(0, max - 1).trimEnd()}…` : text

/**
 * 맨 앞 에이전트. 판매자가 적어 둔 인사말과 입력 예시를 이 화면이 빌려 쓴다.
 *
 * 차례는 `chainAt` 을 따르되, 갈래가 있으면 그것이 `null` 이라 서버가 준 배열 순서로
 * 물러선다 — 선을 따라간 순서로 오므로 그때도 앞쪽이 앞이다.
 */
export function leadAgentOf(nodes: CanvasNodeData[], chainAt: Map<string, number> | null) {
  const agents = nodes.filter((node) => node.sourceType === 'AGENT')
  if (!chainAt) return agents[0]
  return agents
    .slice()
    .sort((a, b) => (chainAt.get(a.id) ?? Infinity) - (chainAt.get(b.id) ?? Infinity))[0]
}

/** 잔액 대비 견적. 잔액이 0 이면 나눌 수 없어, 쓸 돈이 없는데 견적이 있으면 막대를 가득 채운다. */
export function costRatio(cost: CostEstimate | undefined) {
  if (!cost) return 0
  const available = Number(cost.availableAmount)
  const estimated = Number(cost.estimatedAmount)
  if (available > 0) return Math.min(1, estimated / available)
  return estimated > 0 ? 1 : 0
}

/**
 * 떼어 두기 전에 끊긴 것이 확실한 코드. 이때만 요청 식별자를 버린다 —
 * 502·504 는 code 조차 없고 이미 차감된 뒤일 수 있어, 새 값을 만들면 두 번 빠진다.
 */
const BEFORE_RESERVE = new Set([
  'COMMON_001',
  'WORKFLOW_002',
  'WORKFLOW_003',
  'WORKFLOW_014',
  'EXECUTION_003',
  'EXECUTION_005',
  // 입력란 검사(`ExecutionInputValidator`)는 선차감 앞에서 돈다.
  'EXECUTION_009',
  'FILE_002',
  INSUFFICIENT,
])

/** 접수가 확실히 안 된 실패인가. 그 밖에는 접수 여부를 알 수 없으므로 요청 식별자를 지킨다. */
export const failedBeforeReserve = (error: unknown) =>
  error instanceof ApiError && !!error.code && BEFORE_RESERVE.has(error.code)

/**
 * 버튼 아래 한 줄. 서버 문구를 그대로 쓴다 — 코드마다 지어내면 새 코드가 「알 수 없는 오류」가 된다.
 * 409 도 감추지 않는다. 같은 탭 연타는 isPending 이 막으므로 실제로는 다른 탭·새로고침에서 온다.
 */
export function runNoticeOf(error: Error | null, workflowId: string | undefined) {
  if (error)
    return error instanceof ApiError && !error.isNetworkError
      ? error.message
      : '실행을 맡기지 못했습니다. 잠시 후 다시 시도해 주세요'
  return workflowId ? '' : '저장된 워크플로우를 열어야 실행할 수 있습니다'
}

/** 목록이 비어 보이는 이유를 자리에 적는다 — 빈 상자만 두면 노드가 없는 것처럼 읽힌다. */
export function itemsNoticeOf({
  workflowId,
  pending,
  failed,
  count,
}: {
  workflowId: string | undefined
  pending: boolean
  failed: boolean
  count: number
}) {
  if (!workflowId) return '저장된 워크플로우를 열면 단계별 비용을 확인할 수 있습니다'
  if (pending) return '견적을 계산하는 중입니다'
  if (failed) return '견적을 불러오지 못했습니다'
  if (!count) return '놓인 노드가 없습니다'
  return null
}
