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

/** 멱등이 아니다. 에이전트로 등록됐거나 실행이 진행 중이면 409 로 거절된다. */
export const deleteWorkflow = (workflowId: string) =>
  api.delete<DeletedWorkflow>(`/workflows/${workflowId}`)

/** 직접 만들 수 있는 노드 종류 */
export type NodeType = 'TRIGGER' | 'LLM' | 'TOOL'

/** 검색 결과의 유형. 다른 사람의 에이전트는 내부 구성을 공개하지 않아 'AGENT' 로만 온다. */
export type ItemNodeType = NodeType | 'AGENT'

/** 편집 화면은 늘 ALL 로 불러온다. */
export type PaletteScope = 'MINE' | 'ALL'

export interface PlaceableItem {
  itemId: string
  itemType: 'NODE' | 'AGENT'
  name: string
  nodeType: ItemNodeType
  iconUrl: string | null
  /** 내 에이전트에만 MY 칩을 붙인다. 다른 사람의 에이전트가 섞이면 마켓에 판매 등록할 수 없다.  */
  mine: boolean
  owner: { nickname: string }
  executionCount: number
  /** 실행 1회당 단가 */
  price: string
  summary: string | null
}

export interface NodePalettePage extends CursorPage<PlaceableItem> {
  scope: PaletteScope
}

export interface NodePaletteQuery extends CursorParams {
  scope: PaletteScope
  /** 이름으로만 검색 */
  keyword?: string
}

/**
 * 캔버스에 놓을 수 있는 항목. 내 에이전트와 마켓 에이전트를 함께 보여준다.
 * 자신의 것을 위로 올리고, 많이 실행된 순으로 나타낸다.
 */
export const getNodePalette = (params: NodePaletteQuery) =>
  api.get<NodePalettePage>('/workflows/node-palette', { params })
