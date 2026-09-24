import { api } from '@/api/client'
import type { SourceType } from '@/api/workflows'

/**
 * 실행 도메인 API. 금액은 소수점 오차를 피하려고 전부 문자열이다.
 * 견적은 `/executions`, 시작·조회는 `/runs` 다 — 서버가 이유를 밝힌 적 없어 그대로 따른다.
 */

/** 견적의 한 줄. 에이전트는 속을 펼치지 않고 판매가 하나로 온다. */
export interface CostItem {
  /** 캔버스에서 이 자리를 가리키는 키(`n1`…) */
  nodeKey: string
  sourceType: SourceType
  name: string
  /** 노드는 원가, 에이전트는 판매가 */
  price: string
}

export interface CostEstimate {
  /** 놓인 노드의 원가와 에이전트 판매가를 더한 값 */
  estimatedAmount: string
  /** 다른 실행에 잠긴 금액은 빠져 있다 */
  availableAmount: string
  /** 넉넉하면 '0.000000' */
  shortageAmount: string
  executable: boolean
  /** 연결 순서대로 온다 */
  items: CostItem[]
}

/**
 * 실행 전 예상 차감액. 조회지만 명세가 POST 라 CSRF 토큰이 붙는다.
 * 계산만 하고 차감하지 않는다 — 화면이 막는 것은 안내이지 최종 판정이 아니다.
 */
export const estimateCost = (workflowId: string) =>
  api.post<CostEstimate>('/executions/cost-estimates', { workflowId })
