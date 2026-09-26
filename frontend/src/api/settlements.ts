import { api } from '@/api/client'
import type { CursorPage, CursorParams } from '@/api/page'

/** 판매로 받은 정산금. 잔액은 `GET /credits/balance` 의 payoutAmount 다. */

export interface Settlement {
  executionId: string
  stepId: string
  /** 실행할 때의 노드 이름. 노드를 지워도 남는다 */
  nodeName: string | null
  /** 캔버스에 바로 놓인 노드면 null */
  agentId: string | null
  amount: string
  /** 내 에이전트를 내가 실행해 받은 것 */
  selfUse: boolean
  settledAt: string
}

export interface SettlementQuery extends CursorParams {
  /** YYYY-MM-DD, UTC. to 와 함께 주거나 둘 다 생략한다 */
  from?: string
  /** YYYY-MM-DD, UTC, 그날 포함 */
  to?: string
  agentId?: string
}

export type SettlementPeriodUnit = 'DAY' | 'MONTH'

export interface SettlementStatistics {
  unit: SettlementPeriodUnit
  totalAmount: string
  selfUseAmount: string
  /** 한 실행에서 여러 단계로 받아도 한 번이다 */
  executionCount: number
  /** 오래된 것부터, 받은 것이 없는 기간은 빠진다 */
  periods: { periodStart: string; amount: string; executionCount: number }[]
  /** 많이 번 것부터. 캔버스 노드로 받은 것은 agentId null 한 줄로 모인다 */
  agents: { agentId: string | null; amount: string; executionCount: number }[]
}

export interface SettlementStatisticsQuery {
  from?: string
  to?: string
  agentId?: string
  unit?: SettlementPeriodUnit
}

/** 기본 20건·최대 50건. */
export const getSettlements = (params: SettlementQuery = {}) =>
  api.get<CursorPage<Settlement>>('/settlements', { params })

export const getSettlementStatistics = (params: SettlementStatisticsQuery = {}) =>
  api.get<SettlementStatistics>('/settlements/statistics', { params })
