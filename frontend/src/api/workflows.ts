import { api } from '@/api/client'
import type { CursorPage, CursorParams } from '@/api/page'

/** 워크플로우 API. **확정** — 마이페이지 API 명세 MYP-002·MYP-005. */

/**
 * 내 워크플로우 목록 항목. **확정** — MYP-002 명세와 1:1 이다.
 *
 * 노드 개수와 검증 상태는 반환하지 않는다 — 화면에 표시하지 않고, 목록에서 그것으로 판단할
 * 일이 없기 때문이다(명세 참고사항 「필드 최소화」).
 */
export interface WorkflowSummary {
  workflowId: string
  name: string
  /** 한 줄 설명 */
  summary: string
  /** 누적 실행 횟수 */
  executionCount: number
  /** 마지막 실행 시각. 아직 실행하지 않았으면 null */
  lastExecutedAt: string | null
}

/** 내 워크플로우 탭의 정렬 기준. 화면의 정렬 옵션은 이 유니온에서 파생한다. */
export type WorkflowSort = 'LAST_EXECUTED' | 'LATEST'

export interface WorkflowQuery extends CursorParams {
  /** 워크플로우 이름 검색어 */
  keyword?: string
  /** 기본값 LAST_EXECUTED */
  sort?: WorkflowSort
}

/**
 * 삭제 응답. **확정** — MYP-005.
 *
 * 화면이 되돌릴 수 없는 동작을 마쳤음을 알리는 데 필요한 것은 ID 와 시각뿐이다.
 */
export interface DeletedWorkflow {
  workflowId: string
  deletedAt: string
}

/** 에이전트로 등록하지 않은 내 워크플로우. MYP-002 */
export const getMyWorkflows = (params: WorkflowQuery = {}) =>
  api.get<CursorPage<WorkflowSummary>>('/workflows', { params })

/**
 * 워크플로우 삭제. MYP-005 — 멱등이 아니다.
 *
 * 에이전트로 등록됐거나(WORKFLOW_009) 실행이 진행 중이면(WORKFLOW_010) 409 로 거절된다.
 * 화면은 `code` 로 갈라 다른 문구를 낸다 — status 만 보면 둘을 구분할 수 없다.
 */
export const deleteWorkflow = (workflowId: string) =>
  api.delete<DeletedWorkflow>(`/workflows/${workflowId}`)
