import { api } from '@/api/client'
import type { ServerModel } from '@/api/nodes'
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

/** 에이전트로 등록하지 않은 내 워크플로우. */
export const getMyWorkflows = (params: WorkflowQuery = {}) =>
  api.get<CursorPage<WorkflowSummary>>('/workflows', { params })

/** 무엇을 어디에 놓았는가. 좌표는 서버가 저장하지 않아 보내지 않는다(`lib/workflowPayload.ts`). */
/**
 * 노드 배치.
 *
 * 기본 노드(`LLM`)는 캔버스에서 그 자리에 적은 것이라 원본이 아직 없다 — `name`·`prompt`
 * 를 실어 보내면 저장할 때 서버가 만든다. 에이전트와 이미 만들어진 노드는 `sourceId` 로
 * 가리킨다.
 */
export interface NodePlacement {
  /** 이 워크플로우 안에서만 통하는 키. 최대 64자 */
  nodeKey: string
  sourceType: SourceType
  /** `sourceType` 이 NODE 일 때만 보낸다. `sourceId` 와 함께 보내면 종류를 대조한다 */
  nodeType?: NodeType
  /** 원본 노드 또는 에이전트의 id. 저장하며 만들 `LLM` 은 비운다 */
  sourceId?: string
  /** 새로 만들 노드의 이름. `LLM` 일 때만 */
  name?: string
  /** 새로 만들 노드의 프롬프트 원문. `LLM` 일 때만 */
  prompt?: string
  /** 새로 만들 노드가 쓸 모델. 비우면 서버 기본 모델 */
  model?: ServerModel
}

export interface EdgePlacement {
  edgeKey: string
  sourceNodeKey: string
  targetNodeKey: string
}

/** 저장·수정이 함께 싣는 부분. 노드는 최소 하나, 노드가 하나면 선은 빈 배열이다. */
export interface WorkflowGraph {
  nodes: NodePlacement[]
  edges: EdgePlacement[]
}

/** 예상 차감액은 여기 없다 — 실행 견적 API(`POST /executions/cost-estimates`)가 따로 준다. */
/** 저장하며 만들어진 노드가 받은 id. 보낸 `nodeKey` 로 되짚는다. */
export interface PlacedNodeId {
  nodeKey: string
  sourceType: SourceType
  /** 제어 노드면 null */
  sourceId: string | null
}

export interface SavedWorkflow {
  workflowId: string
  name: string
  /** 다음 수정 요청에 그대로 넣는다. 어긋나면 `WORKFLOW_016` */
  revision: number
  /**
   * 놓인 노드. 본문으로 실어 보낸 새 단계는 여기서 `sourceId` 를 받는다.
   * 다음 수정 요청에는 그 id 를 그대로 보내야 노드가 또 만들어지지 않는다.
   */
  nodes: PlacedNodeId[]
}

/** 서버가 돌려주는 배치. `versionId` 는 에이전트 노드만 갖는다. */
/**
 * 저장된 배치. 보낼 때와 달리 서버가 이미 원본을 만들어 둔 뒤라 `sourceId` 가 채워져 온다
 * (제어 노드만 null). 이름·프롬프트는 여기 실려 오지 않아 `GET /nodes/{id}` 로 따로 읽는다.
 */
export interface PlacedNode {
  nodeKey: string
  sourceType: SourceType
  sourceId: string
  versionId: string | null
  inputSource: 'USER' | 'PREV' | 'PREV_AND_USER'
}

export interface WorkflowDetail extends SavedWorkflow {
  description: string | null
  /** 선을 따라간 연결 순서대로 온다 — 캔버스는 이 순서로 다시 늘어놓는다 */
  nodes: PlacedNode[]
  edges: EdgePlacement[]
  lastExecutedAt: string | null
  updatedAt: string
}

/**
 * 새 워크플로우를 만든다. 201 로 온다.
 *
 * `description` 은 만들 때만 반영된다 — 수정 요청에는 자리가 없다.
 */
export const saveWorkflow = (body: WorkflowGraph & { name: string; description?: string }) =>
  api.post<SavedWorkflow>('/workflows', body)

/**
 * 저장된 구성을 통째로 갈아 끼운다.
 *
 * `revision` 은 마지막으로 읽은 회차다. 그 사이 다른 요청이 먼저 고쳤으면 `WORKFLOW_016` 으로
 * 거절된다 — 화면은 다시 불러오게 안내한다.
 */
export const replaceWorkflow = (
  workflowId: string,
  body: WorkflowGraph & { name: string; revision: number },
) => api.put<SavedWorkflow>(`/workflows/${workflowId}`, body)

/** 캔버스를 복원할 때 읽는다. */
export const getWorkflow = (workflowId: string) =>
  api.get<WorkflowDetail>(`/workflows/${workflowId}`)

/** 멱등이 아니다 — 에이전트로 등록됐거나 실행이 진행 중이면 409 로 거절된다. 화면은 `code` 로 갈라 다른 문구를 낸다. */
export const deleteWorkflow = (workflowId: string) =>
  api.delete<DeletedWorkflow>(`/workflows/${workflowId}`)

/** 직접 만들 수 있는 노드 종류 */
export type NodeType = 'TRIGGER' | 'LLM' | 'TOOL'

/** 검색 결과의 유형. 다른 사람의 에이전트는 내부 구성을 공개하지 않아 'AGENT' 로만 온다. */
export type ItemNodeType = NodeType | 'AGENT'

/** 캔버스에 놓은 것이 기본 노드인지 에이전트인지. 서버 `SourceType` 을 그대로 따른다. */
export type SourceType = 'NODE' | 'AGENT'

/** 편집 화면은 늘 ALL 로 불러온다. */
export type PaletteScope = 'MINE' | 'ALL'

export interface PlaceableItem {
  itemId: string
  itemType: SourceType
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
