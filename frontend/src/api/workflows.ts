import { api } from '@/api/client'
import type { CursorPage, CursorParams } from '@/api/page'

/** 워크플로우 API. 필드명은 추정이다 — `api/page.ts` 참고. */

/** 구성 노드가 전부 본인 소유면 캡슐화 가능, 타인 에이전트가 섞이면 실행 전용. */
export type WorkflowKind = 'ENCAPSULABLE' | 'RUN_ONLY'

export type ExecutionStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'

export interface WorkflowSummary {
  workflowId: string
  name: string
  description: string
  kind: WorkflowKind
  nodeCount: number
  /** 구성 노드 단가 합계 */
  estimatedCost: number
  lastExecutedAt: string | null
  lastExecutionStatus: ExecutionStatus | null
  updatedAt: string
}

export const getMyWorkflows = (params: CursorParams = {}) =>
  api.get<CursorPage<WorkflowSummary>>('/workflows', { params })
