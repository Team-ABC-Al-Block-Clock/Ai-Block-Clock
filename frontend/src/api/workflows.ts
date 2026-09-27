import { api } from '@/api/client'
import type { AiModel, NodeOutput } from '@/api/nodes'
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
  sourceType: PlacedSourceType
  /** `sourceType` 이 NODE 일 때만 보낸다. `sourceId` 와 함께 보내면 종류를 대조한다 */
  nodeType?: NodeType
  /** 원본 노드 또는 에이전트의 id. 저장하며 만들 `LLM` 과 제어·입출력 노드는 비운다 */
  sourceId?: string
  /** 새로 만들 노드의 이름. `LLM` 일 때만 */
  name?: string
  /** 새로 만들 노드의 프롬프트 원문. `LLM` 일 때만 */
  prompt?: string
  /** 새로 만들 노드가 쓸 모델. 비우면 서버 기본 모델 */
  model?: AiModel
  /** 새로 만들 노드의 출력 형식. 비우면 `TEXT` */
  output?: NodeOutput
  /** `sourceType` 이 CONTROL 일 때만. 조건과 함께 보내야 한다 */
  controlType?: ControlType
  /** `controlType` 이 BRANCH 일 때만. 빠지면 서버가 물린다 */
  condition?: BranchCondition
  /** `sourceType` 이 IO 일 때만 */
  ioType?: IoType
  /** 시작 노드만. 실행 화면이 이것으로 입력란을 그린다 */
  fields?: InputField[]
  /** 끝 노드만. 서버가 둘 다 있어야 받는다(`NodePlacementRequest.matchesIoType`) */
  resultShape?: ResultShape
  resultFormat?: ResultFormat
}

export interface EdgePlacement {
  edgeKey: string
  sourceNodeKey: string
  targetNodeKey: string
  /** 분기에서 나가는 선만. 조건이 참인 갈래가 `true` 다. 나머지 선은 비운다 */
  when?: boolean | null
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
  sourceType: PlacedSourceType
  /** 제어·입출력 노드면 null */
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
 * (제어·입출력 노드만 null). 프롬프트는 여기 실려 오지 않아 `GET /nodes/{id}` 로 따로 읽는다.
 */
export interface PlacedNode {
  nodeKey: string
  sourceType: PlacedSourceType
  /** 제어·입출력 노드면 null */
  sourceId: string | null
  versionId: string | null
  inputSource: 'USER' | 'PREV' | 'PREV_AND_USER'
  /** 원본을 찾지 못하면 null */
  name: string | null
  /** 판매 등록을 막지 않는가. 서버가 판정한다. 제어·입출력은 null */
  mine: boolean | null
  /** 제어 노드가 아니면 null */
  controlType: ControlType | null
  /** 분기 노드가 아니면 null */
  condition: BranchCondition | null
  /** 입출력 노드가 아니면 null */
  ioType: IoType | null
  /** 시작 노드가 아니면 빈 배열 */
  fields: InputField[]
  /** 끝 노드가 아니면 null */
  resultShape: ResultShape | null
  resultFormat: ResultFormat | null
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
export type NodeType = 'LLM'

/** 검색 결과의 유형. 다른 사람의 에이전트는 내부 구성을 공개하지 않아 'AGENT' 로만 온다. */
export type ItemNodeType = NodeType | 'AGENT'

/** 팔레트에 오르는 것. 원본이 있어 `sourceId` 로 가리킬 수 있다. */
export type SourceType = 'NODE' | 'AGENT'

/** 캔버스에 놓을 수 있는 것 전부. 제어·입출력은 원본이 없어 `sourceId` 가 없다. */
export type PlacedSourceType = SourceType | 'CONTROL' | 'IO'

/** 제어 노드 종류. 서버가 받는 것은 아직 분기뿐이다 — 병합·반복은 타입이 없다 */
export type ControlType = 'BRANCH'

/** 입출력 노드 종류 */
export type IoType = 'START' | 'END'

/** 시작 노드가 실행할 때 받을 입력란 하나. */
export interface InputField {
  /** 영문 소문자로 시작. `comment` 는 쓸 수 없다 */
  key: string
  label: string
  type: 'TEXT' | 'LONG_TEXT' | 'NUMBER' | 'CHOICE' | 'BOOLEAN'
  required: boolean
  /** `CHOICE` 일 때만 */
  choices: string[]
}

/**
 * 끝 노드가 내는 결과의 구조와 형식.
 *
 * 내려받을 때 고르는 `api/executions.ts` 의 `ResultFormat`(JSON·PDF)과는 다른 값이다 —
 * 이쪽은 워크플로우가 무엇을 만들지를 정한다.
 */
export type ResultShape = 'TEXT' | 'DOCUMENT' | 'TABLE'
export type ResultFormat = 'PDF' | 'MD' | 'XLSX' | 'CSV'

/** 제어·입출력 노드의 종류. 한 노드는 이 중 하나를 갖는다 */
export type ControlKind = ControlType | IoType

/** 분기 조건의 비교 방법. `IN` 이면 `right` 가 배열이다 */
export type ConditionOperator = 'EQ' | 'NE' | 'GT' | 'GE' | 'LT' | 'LE' | 'IN'

/** 비교할 수 있는 값. 서버는 문자열·숫자·불리언만 받는다 */
export type ConditionLiteral = string | number | boolean

/**
 * 분기 조건. 앞 노드 결과의 값 하나를 리터럴과 견준다.
 *
 * `left` 는 어느 길로 와도 이 분기 앞에 있는 노드·에이전트만 가리킨다. 제어·입출력은 서버가
 * `WORKFLOW_013` 으로 물린다(`WorkflowGraph.ensureConditionReadsUpstream`).
 */
export interface BranchCondition {
  /** `<nodeKey>.<결과 안의 경로>`. 예: `n1.competition_level` */
  left: string
  operator: ConditionOperator
  right: ConditionLiteral | ConditionLiteral[]
}

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
  /**
   * 판매자가 등록할 때 적은 인사말과 입력 예시. 실행 대기 화면이 첫 에이전트의 것을 쓴다.
   *
   * 아직 서버가 싣지 않아 늘 `undefined` 다 — `AgentEntity` 에 저장은 되는데 응답에 자리가
   * 없어 열어 달라고 요청해 두었다. 그때까지는 화면이 기본 문구로 떨어진다.
   */
  onboardingMessage?: string | null
  usageExample?: string | null
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
