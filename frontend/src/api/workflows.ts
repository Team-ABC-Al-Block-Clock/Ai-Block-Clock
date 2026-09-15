import { api } from '@/api/client'
import type { CursorPage, CursorParams } from '@/api/page'

export interface WorkflowSummary {
  workflowId: string
  name: string
  /** 한 줄 설명 */
  summary: string
  executionCount: number
  /** 아직 실행하지 않았으면 null */
  lastExecutedAt: string | null
}

export type WorkflowSort = 'LAST_EXECUTED' | 'LATEST'

export interface WorkflowQuery extends CursorParams {
  keyword?: string
  /** 기본값 LAST_EXECUTED */
  sort?: WorkflowSort
}

export interface DeletedWorkflow {
  workflowId: string
  deletedAt: string
}

/** 에이전트로 등록하지 않은 내 워크플로우 */
export const getMyWorkflows = (params: WorkflowQuery = {}) =>
  api.get<CursorPage<WorkflowSummary>>('/workflows', { params })

/**
 * 멱등이 아니다. 에이전트로 등록됐거나(WORKFLOW_009) 실행이 진행 중이면(WORKFLOW_010)
 * 409 로 거절된다 — 화면은 `code` 로 갈라 다른 문구를 낸다.
 */
export const deleteWorkflow = (workflowId: string) =>
  api.delete<DeletedWorkflow>(`/workflows/${workflowId}`)

/** 직접 만들 수 있는 노드 종류 */
export type NodeType = 'TRIGGER' | 'LLM' | 'TOOL'

/** 검색 결과의 유형. 남의 에이전트는 내부 구성을 공개하지 않아 'AGENT' 로만 온다. */
export type ItemNodeType = NodeType | 'AGENT'

/** 판매자 모드는 내 것만, 구매자 모드는 마켓까지. 진입 경로가 정한다. */
export type PaletteScope = 'MINE' | 'ALL'

export interface PlaceableItem {
  itemId: string
  itemType: 'NODE' | 'AGENT'
  name: string
  nodeType: ItemNodeType
  iconUrl: string | null
  /** 화면이 색으로 구분한다. 남의 에이전트가 섞이면 등록할 수 없는 워크플로우가 된다 */
  mine: boolean
  owner: { nickname: string }
  executionCount: number
  /** 실행 1회당 단가 */
  price: string
  currency: string
  summary: string | null
}

export interface NodePalettePage extends CursorPage<PlaceableItem> {
  scope: PaletteScope
}

export interface NodePaletteQuery extends CursorParams {
  scope: PaletteScope
  /** 이름으로만 찾는다 */
  keyword?: string
}

/**
 * 캔버스에 놓을 수 있는 항목. 내 노드와 마켓 에이전트가 한 목록에 섞여 온다.
 * 내 것을 먼저, 그다음 많이 쓰인 순이다.
 */
export const getNodePalette = (params: NodePaletteQuery) =>
  api.get<NodePalettePage>('/workflows/node-palette', { params })
