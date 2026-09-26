import type { AiModel } from '@/api/nodes'
import type {
  ConditionLiteral,
  ConditionOperator,
  ControlType,
  IoType,
  ItemNodeType,
  PlacedSourceType,
  ResultFormat,
  ResultShape,
} from '@/api/workflows'

import { roundedPath, routeEdge } from './edgeRoute'
import type { ContentBox } from './viewport'

/** 노드 카드의 한 변(px). 좌표는 카드의 왼쪽 위 모서리이다. */
export const NODE_SIZE = 168
/** 카드 사이에 남기는 최소 간격(px). */
const NODE_GAP = 24
/**
 * 카드 왼쪽 위 좌표가 들어갈 수 있는 범위.
 *
 * 설계 좌표에 박아 둘 수 없다 — 패널은 `--edge` 만큼 오른쪽으로 밀려 있고 그 값이 창 크기에
 * 따라 달라진다. 화면에서 잰 값을 화면 쪽에서 넘겨준다.
 */
export interface CanvasBounds {
  minX: number
  maxX: number
  minY: number
  maxY: number
}

/** 어디에도 걸리지 않는 범위. 화면을 잴 수 없을 때와 검사에서 쓴다. */
export const NO_BOUNDS: CanvasBounds = {
  minX: -Infinity,
  maxX: Infinity,
  minY: -Infinity,
  maxY: Infinity,
}

/** 범위 안으로만 자른다. 겹침은 보지 않는다 — 드래그 중에는 커서를 그대로 따라가야 한다. */
export function clampToBounds(
  x: number,
  y: number,
  bounds: CanvasBounds,
): { x: number; y: number } {
  return {
    x: Math.min(Math.max(x, bounds.minX), bounds.maxX),
    y: Math.min(Math.max(y, bounds.minY), bounds.maxY),
  }
}

/** 편집·대기·실행 화면이 함께 그리는 워크플로우의 노드. */
export interface CanvasNodeData {
  /** 같은 항목을 두 번 놓을 수 있어 이름으로는 가를 수 없다. */
  id: string
  name: string
  /** 팔레트에서 온 것만. 제어·입출력 노드는 종류를 `controlType`·`ioType` 이 들고 있어 null */
  nodeType: ItemNodeType | null
  /** 팔레트 항목의 `itemType`. 서버는 이 값으로 노드·에이전트·제어·입출력을 가른다 */
  sourceType: PlacedSourceType
  /** 제어 노드만. 그 밖에는 비운다 */
  controlType?: ControlType | null
  /** 입출력 노드만. 그 밖에는 비운다 */
  ioType?: IoType | null
  /** 분기 노드만. 조건을 채우지 않으면 저장이 물린다 */
  condition?: CanvasCondition | null
  /** 끝 노드만. 고르는 화면은 아직 없고 기본값을 싣고 다닌다 */
  resultShape?: ResultShape | null
  resultFormat?: ResultFormat | null
  /**
   * 팔레트 항목의 `itemId`. 화면용 `id` 와 달리 서버가 아는 원본을 가리킨다.
   *
   * 캔버스에서 갓 만든 기본 노드는 아직 원본이 없어 빈 문자열이다 — 저장할 때 서버가 만든다.
   */
  sourceId: string
  /** 기본 노드가 그 자리에 적은 프롬프트. 에이전트는 null */
  prompt: string | null
  /** 기본 노드가 쓸 모델. 에이전트는 null */
  model: AiModel | null
  /** 에이전트의 누적 실행 횟수. 모르면 비운다 — 0 과 「모름」은 다르다 */
  executionCount?: number | null
  /** 에이전트를 등록한 사람. 모르면 비운다 */
  owner?: string | null
  /** 팔레트 항목의 `price`. 팔레트에서 못 찾은 노드는 null 이다 */
  price: string | null
  /** 팔레트 항목의 `summary`. 에이전트는 내부 구성 대신 이것을 보여준다 */
  summary: string | null
  x: number
  y: number
  /** 판매 등록을 막는 자리인가. 제어·입출력은 판정 대상이 아니라 null */
  mine: boolean | null
  /** 계속 도는 모션. 드래그가 끝나면 이것만 되돌린다. */
  floaty: string
  /**
   * 등장 모션. 처음 한 번만 재생한다. 되돌리면 카드가 사라졌다 다시 나타나기 때문이다.
   *
   * `backwards` 로 채운다 — `both` 면 끝난 뒤에도 `transform: none` 을 붙들어 `floaty` 를 덮는다.
   */
  enter?: string
}

/** 놓인 노드에 주는 모션. 자리를 잡은 뒤라 등장 모션은 주지 않는다. */
const PLACED_FLOATY = 'floaty 6.6s ease-in-out infinite'

/**
 * 다섯 칸짜리 예시 구성. 화면 초기값으로 쓰지 않는다 — 캔버스는 빈 상태로 연다.
 * 그래프 함수를 검사할 때 손으로 짜는 대신 쓰는 표본이다.
 */
export const CANVAS_NODES: CanvasNodeData[] = [
  {
    id: 'seed-1',
    name: '파일 수집',
    nodeType: 'TRIGGER',
    sourceType: 'NODE',
    sourceId: 'node_001',
    price: '4',
    summary: null,
    prompt: null,
    model: null,
    x: 12,
    y: 400,
    mine: true,
    floaty: 'floaty 6.4s ease-in-out infinite',
    enter: 'uiIn .7s .18s cubic-bezier(.2,.8,.25,1) backwards',
  },
  {
    id: 'seed-2',
    name: '내용 분석',
    nodeType: 'LLM',
    sourceType: 'NODE',
    sourceId: 'node_002',
    price: '12',
    summary: null,
    prompt: null,
    model: null,
    x: 209,
    y: 320,
    mine: true,
    floaty: 'floaty 6.9s .3s ease-in-out infinite',
    enter: 'uiIn .7s .26s cubic-bezier(.2,.8,.25,1) backwards',
  },
  {
    id: 'seed-3',
    name: '분류 및 태깅',
    nodeType: 'TOOL',
    sourceType: 'NODE',
    sourceId: 'node_003',
    price: '6',
    summary: null,
    prompt: null,
    model: null,
    x: 406,
    y: 440,
    mine: true,
    floaty: 'floaty 7.1s .8s ease-in-out infinite',
    enter: 'uiIn .7s .34s cubic-bezier(.2,.8,.25,1) backwards',
  },
  {
    id: 'seed-4',
    name: '요약 생성',
    nodeType: 'LLM',
    sourceType: 'NODE',
    sourceId: 'node_004',
    price: '15',
    summary: null,
    prompt: null,
    model: null,
    x: 603,
    y: 330,
    mine: true,
    floaty: 'floaty 6.8s .4s ease-in-out infinite',
    enter: 'uiIn .7s .42s cubic-bezier(.2,.8,.25,1) backwards',
  },
  {
    id: 'seed-5',
    name: '결과 전달',
    nodeType: 'TOOL',
    sourceType: 'NODE',
    sourceId: 'node_005',
    price: '3',
    summary: null,
    prompt: null,
    model: null,
    x: 800,
    y: 430,
    mine: true,
    floaty: 'floaty 7.6s 1.2s ease-in-out infinite',
    enter: 'uiIn .7s .5s cubic-bezier(.2,.8,.25,1) backwards',
  },
]

/**
 * 분기 조건. 서버가 받는 `left`(`<nodeKey>.<경로>`)를 캔버스에서는 둘로 나눠 든다.
 *
 * 노드 키는 저장할 때 비로소 매겨진다(`n1…nN`). 조건에 키를 박아 두면 노드 하나를 지우거나
 * 순서가 바뀔 때마다 조건이 딴 곳을 가리킨다 — 캔버스 id 로 들고 있다가 보낼 때 옮긴다.
 */
export interface CanvasCondition {
  /** 조건이 읽는, 이 분기보다 앞에 놓인 기본 노드의 캔버스 id */
  fromId: string
  /** 그 결과 안의 경로. 예: `competition_level` */
  path: string
  operator: ConditionOperator
  right: ConditionLiteral | ConditionLiteral[]
}

/** 노드를 잇는 선. `from` 의 출력에서 `to` 의 입력으로 간다. */
export interface CanvasEdge {
  id: string
  from: string
  to: string
  /** 분기에서 나가는 선만. 조건이 참인 갈래가 `true` 다 */
  when?: boolean | null
}

/** 선의 이름은 양 끝에서. */
function edgeId(from: string, to: string): string {
  return `${from}__${to}`
}

const seedEdge = (from: string, to: string): CanvasEdge => ({ id: edgeId(from, to), from, to })

export const CANVAS_EDGES: CanvasEdge[] = [
  seedEdge('seed-1', 'seed-2'),
  seedEdge('seed-2', 'seed-3'),
  seedEdge('seed-3', 'seed-4'),
  seedEdge('seed-4', 'seed-5'),
]

/** `from` 에서 선을 따라 `to` 에 닿는지. */
function reaches(edges: CanvasEdge[], from: string, to: string): boolean {
  const seen = new Set<string>()
  const todo = [from]
  while (todo.length > 0) {
    const at = todo.pop()!
    if (at === to) return true
    if (seen.has(at)) continue
    seen.add(at)
    for (const edge of edges) if (edge.from === at) todo.push(edge.to)
  }
  return false
}

/**
 * 이어도 되는 짝인지. 서버가 받는 트리 모양을 그을 때부터 지킨다.
 *
 * 들어오는 선은 자리마다 하나다 — 여럿이면 어느 것을 기다릴지 알 수 없고, 서버도 합류를
 * 받지 않는다. 나가는 선도 하나다. 갈래를 내려면 분기 노드를 놓아야 한다 — 일하는 노드에서
 * 두 갈래가 나가면 어느 쪽으로 갈지 정할 곳이 없다.
 *
 * `when` 은 분기에서 나가는 선이 어느 갈래인지다. 분기는 참·거짓을 하나씩만 갖는다.
 */
export function canConnect(
  nodes: CanvasNodeData[],
  edges: CanvasEdge[],
  from: string,
  to: string,
  when?: boolean,
): boolean {
  if (from === to) return false

  const source = nodes.find((node) => node.id === from)
  const target = nodes.find((node) => node.id === to)
  if (!source || !target) return false

  // 시작 앞과 종료 뒤에는 아무것도 올 수 없다.
  if (target.ioType === 'START' || source.ioType === 'END') return false
  // 시작과 종료를 맞붙이면 하는 일이 없는 워크플로우가 된다.
  if (source.ioType === 'START' && target.ioType === 'END') return false
  if (edges.some((edge) => edge.to === to)) return false

  const out = edges.filter((edge) => edge.from === from)
  if (source.controlType) {
    if (when === undefined) return false
    if (out.some((edge) => edge.when === when)) return false
  } else if (when !== undefined || out.length > 0) {
    return false
  }

  return !reaches(edges, to, from)
}

/** 선 연결. 이을 수 없는 짝이면 목록 그대로. `when` 은 분기에서 나가는 선에만 준다. */
export function connect(
  nodes: CanvasNodeData[],
  edges: CanvasEdge[],
  from: string,
  to: string,
  when?: boolean,
): CanvasEdge[] {
  if (!canConnect(nodes, edges, from, to, when)) return edges
  return [...edges, { id: edgeId(from, to), from, to, ...(when === undefined ? {} : { when }) }]
}

/** 선 하나 삭제. 없는 id 면 목록 그대로. */
export function disconnect(edges: CanvasEdge[], id: string): CanvasEdge[] {
  const left = edges.filter((edge) => edge.id !== id)
  return left.length === edges.length ? edges : left
}

/** 노드 삭제. 거기 붙어 있던 선도 함께. */
export function removeNode(
  nodes: CanvasNodeData[],
  edges: CanvasEdge[],
  id: string,
): { nodes: CanvasNodeData[]; edges: CanvasEdge[] } {
  if (!nodes.some((node) => node.id === id)) return { nodes, edges }
  return {
    // 지운 자리를 읽던 조건은 함께 비운다. 남겨 두면 가리킬 곳이 없는 채로 저장까지 가서,
    // 저장 버튼은 켜져 있는데 눌러도 되지 않는 상태에 갇힌다.
    nodes: nodes
      .filter((node) => node.id !== id)
      .map((node) => (node.condition?.fromId === id ? { ...node, condition: null } : node)),
    edges: edges.filter((edge) => edge.from !== id && edge.to !== id),
  }
}

/**
 * 포트가 카드 높이의 가운데에서 벗어나는 거리.
 *
 * 분기만 오른쪽에 둘을 낸다 — 참이 위, 거짓이 아래다. 카드를 그리는 쪽과 선을 긋는 쪽이
 * 같은 값을 봐야 점과 선이 어긋나지 않는다.
 */
const BRANCH_PORT_DY = 30

export const portOffsetY = (when?: boolean): number =>
  when === undefined ? 0 : when ? -BRANCH_PORT_DY : BRANCH_PORT_DY

/** 선이 드나드는 자리. 입력은 왼쪽, 출력은 오른쪽. */
export function portPoint(
  node: Pick<CanvasNodeData, 'x' | 'y'>,
  side: 'in' | 'out',
  when?: boolean,
): { x: number; y: number } {
  return {
    x: side === 'out' ? node.x + NODE_SIZE : node.x,
    y: node.y + NODE_SIZE / 2 + portOffsetY(when),
  }
}

/**
 * 포트에서 포트로 잇는 꺾은선. `avoid` 는 지나면 안 되는 카드의 한가운데.
 *
 * 길을 못 찾으면 곧은 선. 그런 배치는 거의 없다.
 */
export function edgePath(
  from: { x: number; y: number },
  to: { x: number; y: number },
  avoid: { x: number; y: number }[] = [],
): string {
  return roundedPath(routeEdge(from, to, avoid) ?? [from, to])
}

/** 선 목록 → 그릴 좌표. 양 끝 중 하나라도 없으면 버린다. */
export function edgeGeometry(
  nodes: CanvasNodeData[],
  edges: CanvasEdge[],
): { id: string; from: string; to: string; d: string }[] {
  // 제 카드도 가로지르면 안 된다. 포트에서 나가는 짧은 구간만 예외.
  const avoid = nodes.map((node) => ({ x: node.x + NODE_SIZE / 2, y: node.y + NODE_SIZE / 2 }))
  return edges.flatMap((edge) => {
    const from = nodes.find((node) => node.id === edge.from)
    const to = nodes.find((node) => node.id === edge.to)
    if (!from || !to) return []
    // 분기는 출력이 둘이라 선도 제 갈래의 점에서 나가야 한다.
    return [
      {
        ...edge,
        d: edgePath(portPoint(from, 'out', edge.when ?? undefined), portPoint(to, 'in'), avoid),
      },
    ]
  })
}

/** 놓인 카드 전부를 감싸는 사각형. 빈 캔버스면 null — 맞출 것이 없다. */
export function nodesBounds(nodes: CanvasNodeData[]): ContentBox | null {
  if (nodes.length === 0) return null
  return {
    minX: Math.min(...nodes.map((node) => node.x)),
    minY: Math.min(...nodes.map((node) => node.y)),
    maxX: Math.max(...nodes.map((node) => node.x)) + NODE_SIZE,
    maxY: Math.max(...nodes.map((node) => node.y)) + NODE_SIZE,
  }
}

/**
 * 이 자리보다 앞에 놓인 노드들. 가까운 것부터.
 *
 * 트리라 부모는 자리마다 하나여서 선을 거슬러 한 줄로 올라가면 된다. 분기 조건이 읽을 수
 * 있는 것도 이 안의 기본 노드뿐이다(`WorkflowGraph.ensureConditionReadsUpstream`).
 */
export function upstreamOf(
  nodes: CanvasNodeData[],
  edges: CanvasEdge[],
  id: string,
): CanvasNodeData[] {
  const parentOf = new Map(edges.map((edge) => [edge.to, edge.from]))
  const byId = new Map(nodes.map((node) => [node.id, node]))

  const walked: CanvasNodeData[] = []
  const seen = new Set<string>([id])
  for (let at = parentOf.get(id); at && !seen.has(at); at = parentOf.get(at)) {
    seen.add(at)
    const node = byId.get(at)
    if (node) walked.push(node)
  }
  return walked
}

/**
 * 실행을 막는 까닭 한 줄. 실행할 수 있으면 `null`.
 *
 * 저장은 그 전에도 된다 — 짓다 만 것을 두고 나갔다가 이어서 고칠 수 있어야 한다. 실행만
 * 막는다. 어디서 시작해 어디서 끝나는지 정해지지 않으면 서버가 돌릴 자리를 모른다.
 */
export function runProblem(nodes: CanvasNodeData[], edges: CanvasEdge[]): string | null {
  const { problem, order } = checkGraph(nodes, edges)
  if (!order) return problem

  const has = (type: IoType) => order.nodes.some((node) => node.ioType === type)
  // 갈래 끝마다 종료를 붙일 수는 없다 — 서버가 종료를 하나만 받는다. 하나라도 있으면 된다.
  if (!has('START') && !has('END')) return '시작·종료 노드는 필수입니다'
  if (!has('START')) return '시작 노드를 놓아 주세요'
  if (!has('END')) return '종료 노드를 놓아 주세요'
  return null
}

/**
 * 다른 사람의 에이전트가 하나라도 섞이면 팔 수 없다. 빈 캔버스도 등록할 것이 없다.
 *
 * 제어·입출력은 `mine` 이 null 이다 — 가리킬 원본이 없어 판정 대상이 아니라 막지 않는다.
 */
export function canSellWorkflow(nodes: CanvasNodeData[]): boolean {
  // 제어·입출력만 놓인 구성은 팔 것이 없다. 하나는 내 것이어야 한다.
  return nodes.some((node) => node.mine === true) && nodes.every((node) => node.mine !== false)
}

/**
 * 카드에 박는 순번. 한 줄이 아니면 `null` — 그때는 번호를 감춘다.
 *
 * 놓은 순서로 매기면 선과 어긋난 번호가 박혀 "이 차례로 실행된다" 고 잘못 읽힌다.
 * 갈래가 있으면 차례라는 것이 아예 없으므로 지어내지 않는다.
 *
 * 제어·입출력은 세지 않는다.
 *
 * 시작에서 종료까지 이어지기 전에도 번호를 주지 않는다. 아직 실행할 수 없는 구성인데
 * 번호가 붙으면 "이대로 돌아간다" 로 읽힌다.
 */
export function chainPositions(
  nodes: CanvasNodeData[],
  edges: CanvasEdge[],
): Map<string, number> | null {
  const order = linearOrder(nodes, edges)
  if (!order) return null
  if (order[0].ioType !== 'START' || order[order.length - 1].ioType !== 'END') return null

  const positions = new Map<string, number>()
  for (const node of order) {
    if (node.controlType || node.ioType) continue
    positions.set(node.id, positions.size)
  }
  return positions
}

/**
 * 한 줄로 이어진 순서. 갈라지거나 합쳐지거나 끊겨 있으면 `null`.
 *
 * 캔버스는 갈래를 만들 수 있게 두고(`canConnect`) 여기서 판정만 한다 — 서버가 아직 선형만
 * 받으므로(`WORKFLOW_013`) 저장 버튼 옆에 가능 여부를 띄우는 데 쓴다. 서버가 갈래를 받게 되면
 * 이 함수만 풀면 되고 편집 화면은 그대로다.
 *
 * 판정과 순서를 함께 낸다. 저장할 때 이 순서로 `n1…nN` 키를 매기기 때문에 두 번 훑을 이유가 없다.
 */
export function linearOrder(nodes: CanvasNodeData[], edges: CanvasEdge[]): CanvasNodeData[] | null {
  if (nodes.length === 0) return null
  // 한 줄이면 선은 노드보다 정확히 하나 적다. 남거나 모자라면 끊겼거나 겹친 것이다.
  if (edges.length !== nodes.length - 1) return null

  const byId = new Map(nodes.map((node) => [node.id, node]))
  const next = new Map<string, string>()
  const incoming = new Map<string, number>()
  for (const edge of edges) {
    if (!byId.has(edge.from) || !byId.has(edge.to)) return null
    if (next.has(edge.from)) return null
    next.set(edge.from, edge.to)
    const count = (incoming.get(edge.to) ?? 0) + 1
    if (count > 1) return null
    incoming.set(edge.to, count)
  }

  const heads = nodes.filter((node) => !incoming.has(node.id))
  if (heads.length !== 1) return null

  // 들어오는 선이 하나뿐이라 같은 노드를 두 번 밟지 않는다 — 고리에 갇히지 않는다.
  const order: CanvasNodeData[] = []
  let at: string | undefined = heads[0].id
  while (at !== undefined) {
    order.push(byId.get(at)!)
    at = next.get(at)
  }
  // 따로 떨어진 덩어리가 있으면 머리에서 다 닿지 못한다.
  return order.length === nodes.length ? order : null
}

/** 참 갈래를 먼저. 분기가 아닌 자리는 나가는 선이 하나뿐이라 그대로다. */
const branchFirst = (next: CanvasEdge[]) =>
  [...next].sort((a, b) => Number(b.when === true) - Number(a.when === true))

/** 분기 조건이 읽는 자리가 아직 제 앞에 있는 기본 노드인가. */
function readsUpstream(
  nodes: CanvasNodeData[],
  edges: CanvasEdge[],
  branch: CanvasNodeData,
): boolean {
  const from = branch.condition?.fromId
  if (!from) return false
  return upstreamOf(nodes, edges, branch.id).some(
    (node) => node.id === from && node.sourceType === 'NODE',
  )
}

interface GraphCheck {
  /** 저장할 수 없는 까닭. 저장할 수 있으면 null */
  problem: string | null
  /** 저장할 수 있을 때의 훑는 차례. 아니면 null */
  order: { nodes: CanvasNodeData[]; edges: CanvasEdge[] } | null
}

/**
 * 그을 때 이미 막은 것들에 쓰는 문구.
 *
 * 합류·일하는 노드의 갈래·종료에서 나가는 선·맨 앞이 아닌 시작·둘씩 놓인 시작과 종료는
 * `canConnect` 와 팔레트 칩이 손대기 전에 막는다. 그래도 검사는 남긴다 — 서버에서 불러온
 * 구성이 규칙을 어길 수 있고, 그때 `treeOrder` 가 엉뚱한 차례를 내면 저장이 조용히 망가진다.
 * 화면에 뜰 자리가 없으니 따로 문구를 두지 않는다.
 */
const LOOSE = '노드를 빠짐없이 하나로 이어 주세요'

function checkGraph(nodes: CanvasNodeData[], edges: CanvasEdge[]): GraphCheck {
  const fail = (problem: string): GraphCheck => ({ problem, order: null })

  if (nodes.length === 0) return fail('노드를 하나 이상 놓아 주세요')
  if (nodes.filter((node) => node.ioType === 'START').length > 1) return fail(LOOSE)
  if (nodes.filter((node) => node.ioType === 'END').length > 1) return fail(LOOSE)

  const byId = new Map(nodes.map((node) => [node.id, node]))
  const out = new Map<string, CanvasEdge[]>()
  const targeted = new Set<string>()
  for (const edge of edges) {
    if (!byId.has(edge.from) || !byId.has(edge.to)) return fail(LOOSE)
    // 들어오는 선이 둘이면 합류다. 서버는 트리만 받는다.
    if (targeted.has(edge.to)) return fail(LOOSE)
    targeted.add(edge.to)
    out.set(edge.from, [...(out.get(edge.from) ?? []), edge])
  }

  for (const node of nodes) {
    const next = out.get(node.id) ?? []
    if (node.controlType) {
      if (!node.condition) return fail('분기 노드에 조건을 넣어 주세요')
      // 조건이 읽던 자리가 지워지거나 뒤로 밀렸을 수 있다. 서버도 같은 것을 본다
      // (`WorkflowGraph.ensureConditionReadsUpstream`).
      if (!readsUpstream(nodes, edges, node))
        return fail('분기 조건이 읽을 노드를 다시 골라 주세요')
      // 참 하나 거짓 하나. 한쪽만 걸려 있으면 아직 저장할 수 없다.
      if (
        next.length !== 2 ||
        next.filter((edge) => edge.when === true).length !== 1 ||
        next.filter((edge) => edge.when === false).length !== 1
      )
        return fail('분기의 true·false 갈래를 하나씩 이어 주세요')
      continue
    }
    if (next.length > 1 || next.some((edge) => edge.when != null)) return fail(LOOSE)
    if (node.ioType === 'END' && next.length > 0) return fail(LOOSE)
  }

  const roots = nodes.filter((node) => !targeted.has(node.id))
  if (roots.length !== 1) return fail(LOOSE)
  // 시작 노드를 놓았으면 그것이 맨 앞이어야 한다.
  if (nodes.some((node) => node.ioType === 'START' && node.id !== roots[0].id)) return fail(LOOSE)

  const walked: CanvasNodeData[] = []
  const walkedEdges: CanvasEdge[] = []
  const walk = (id: string) => {
    walked.push(byId.get(id)!)
    for (const edge of branchFirst(out.get(id) ?? [])) {
      walkedEdges.push(edge)
      walk(edge.to)
    }
  }
  walk(roots[0].id)

  // 루트에서 못 닿은 자리가 남으면 끊겼거나 고리다.
  if (walked.length !== nodes.length) return fail(LOOSE)
  return { problem: null, order: { nodes: walked, edges: walkedEdges } }
}

/**
 * 저장할 수 있는 모양이면 서버가 훑는 순서, 아니면 `null`.
 *
 * 서버가 받는 그래프는 트리뿐이다(`WorkflowGraph.walk`) — 루트 하나, 들어오는 선은 자리마다
 * 하나(합류 불가), 분기는 참·거짓 두 갈래, 종료 노드는 나가는 선이 없다.
 *
 * 선도 같은 순서로 낸다 — 노드 키와 선 키가 서버가 돌려주는 차례와 같아야 다시 불러왔을 때
 * 자리가 흔들리지 않는다.
 */
export const treeOrder = (nodes: CanvasNodeData[], edges: CanvasEdge[]) =>
  checkGraph(nodes, edges).order

/**
 * 저장을 막는 까닭 한 줄. 저장할 수 있으면 `null`.
 *
 * 어겨도 서버가 막지만 코드가 `WORKFLOW_013` 하나뿐이라, 다녀와서는 어디가 잘못인지 짚어
 * 줄 수 없다. 그을 때부터 막고 여기서 까닭을 말한다.
 */
export const graphProblem = (nodes: CanvasNodeData[], edges: CanvasEdge[]) =>
  checkGraph(nodes, edges).problem

/** 겹치는 카드가 하나라도 있는지. */
function overlaps(nodes: CanvasNodeData[], id: string, x: number, y: number): boolean {
  return nodes.some(
    (node) => node.id !== id && Math.hypot(x - node.x, y - node.y) < NODE_SIZE + NODE_GAP,
  )
}

/**
 * 원하는 자리 둘레를 돌며 가장 가까운 빈자리를 찾는다.
 * 카드 두 장 사이에 끼면 밀어내는 방향이 상쇄돼 빠져나가지 못한다. 그때의 마지막 수단.
 */
function findFreeSpot(
  nodes: CanvasNodeData[],
  id: string,
  x: number,
  y: number,
  bounds: CanvasBounds,
): { x: number; y: number } | null {
  const step = (NODE_SIZE + NODE_GAP) / 4
  for (let ring = 1; ring <= 24; ring++) {
    for (let i = 0; i < 16; i++) {
      const angle = (i / 16) * Math.PI * 2
      const spot = clampToBounds(
        x + Math.cos(angle) * ring * step,
        y + Math.sin(angle) * ring * step,
        bounds,
      )
      if (!overlaps(nodes, id, spot.x, spot.y)) return spot
    }
  }
  return null
}

/**
 * 다른 카드와 겹치지 않는 자리로 민다.
 *
 * 카드가 원이라 모서리가 아니라 중심 거리로 본다. 가장 깊이 겹친 것부터 중심선 바깥으로
 * 밀어내고, 밀린 자리가 또 겹치면 되풀이한다 — 한 번에 밀면 옆 카드 위로 올라간다.
 *
 * 갈 수 있는 자리는 `bounds` 가 정한다 — 화면 밖으로도 패널 뒤로도 카드 절반까지다.
 */
export function resolveOverlap(
  nodes: CanvasNodeData[],
  id: string,
  x: number,
  y: number,
  bounds: CanvasBounds = NO_BOUNDS,
): { x: number; y: number } {
  const others = nodes.filter((node) => node.id !== id)
  const apart = NODE_SIZE + NODE_GAP
  let point = clampToBounds(x, y, bounds)

  // 밀린 자리가 또 다른 카드와 겹칠 수 있다. 되풀이 횟수는 막아 둔다.
  for (let step = 0; step < 12; step++) {
    let deepest: { dx: number; dy: number; gap: number; depth: number } | null = null
    for (const other of others) {
      const dx = point.x - other.x
      const dy = point.y - other.y
      const gap = Math.hypot(dx, dy)
      const depth = apart - gap
      if (depth > (deepest?.depth ?? 0)) deepest = { dx, dy, gap, depth }
    }
    if (!deepest) return point

    // 완전히 포개졌으면 방향이 없다 — 오른쪽으로 뗀다.
    const ux = deepest.gap === 0 ? 1 : deepest.dx / deepest.gap
    const uy = deepest.gap === 0 ? 0 : deepest.dy / deepest.gap
    // 딱 맞닿는 거리까지만 밀면 부동소수 오차로 다시 겹침으로 읽힌다.
    const push = deepest.depth + 0.01
    point = clampToBounds(point.x + ux * push, point.y + uy * push, bounds)
  }
  // 밀어내기로 못 풀었으면 둘레에서 빈자리를 찾는다.
  return findFreeSpot(nodes, id, point.x, point.y, bounds) ?? point
}

/** 팔레트에서 드래그해 온 항목을 목록 끝에 놓는다. 좌표는 카드의 왼쪽 위. */
export function placeNode(
  nodes: CanvasNodeData[],
  seed: Pick<
    CanvasNodeData,
    | 'id'
    | 'name'
    | 'nodeType'
    | 'sourceType'
    | 'sourceId'
    | 'price'
    | 'summary'
    | 'prompt'
    | 'model'
    | 'executionCount'
    | 'owner'
    | 'mine'
  >,
  x: number,
  y: number,
  bounds: CanvasBounds = NO_BOUNDS,
): CanvasNodeData[] {
  return [
    ...nodes,
    { ...seed, ...resolveOverlap(nodes, seed.id, x, y, bounds), floaty: PLACED_FLOATY },
  ]
}

/** 추천에서 선택한 것을 처음부터 놓아 둘 때의 보폭. 데모 배치와 같은 간격이다. */
const SEED_STEP = 197
/** 선택한 것을 모아 보여줄 때. 좁은 폭에 여러 장이 들어가야 한다. */
const SEED_GRID = { x: 60, y: 300, perRow: 4 }
/** 한 칸씩 어긋나게 둔다 — 격자로 세우면 잇지도 않은 것이 이미 이어진 순서처럼 보인다. */
const SEED_GRID_STAGGER = 44
/**
 * 편집으로 넘어갔을 때. 왼쪽에서 오른쪽으로 잇는 순서가 곧 실행 순서다.
 *
 * 높이를 한 칸씩 엇갈린다 — 자리표시자 배치와 같은 결이고, 일직선보다 카드 사이
 * 간격이 넓어져 선을 걸기 쉽다.
 */
const SEED_ROW = { x: 12, y: 388, zigzag: 84 }

/** 선택한 것을 어떻게 늘어놓을지. 찾기 화면은 모아 보여주고, 편집 화면은 한 줄로 편다. */
export type SeedLayout = 'grid' | 'row'

function seedPoint(layout: SeedLayout, i: number): { x: number; y: number } {
  if (layout === 'row') {
    return { x: SEED_ROW.x + i * SEED_STEP, y: SEED_ROW.y + (i % 2) * SEED_ROW.zigzag }
  }
  return {
    x: SEED_GRID.x + (i % SEED_GRID.perRow) * SEED_STEP,
    y: SEED_GRID.y + Math.floor(i / SEED_GRID.perRow) * SEED_STEP + (i % 2) * SEED_GRID_STAGGER,
  }
}

/**
 * 추천에서 선택한 에이전트를 캔버스 첫 상태로 늘어놓는다.
 *
 * 자리만 정한다. 잇는 것은 `chainEdges` 가 따로 맡는다 — 미리보기는 모아 보여주기만 하고
 * 편집 화면만 이어서 열기 때문이다.
 *
 * 한 줄 배치는 작업면 폭(980)을 넘길 수 있다. 다섯 장까지 그대로 들어가고, 그 뒤로는
 * 화면이 통째로 축소돼 다 보인다 — 줄을 접으면 어디까지가 순서인지 흐려진다.
 *
 * 축소가 먹히는 것은 스무 장까지다. 그 뒤로는 `ZOOM_MIN` 에 걸려 오른쪽이 패널 뒤로
 * 넘어가므로, 부르는 쪽에서 개수를 자른다 (`EditSearching` 의 `MAX_PICKED`).
 */
export function seedCanvasNodes(
  seeds: Pick<
    CanvasNodeData,
    | 'id'
    | 'name'
    | 'nodeType'
    | 'sourceType'
    | 'sourceId'
    | 'price'
    | 'summary'
    | 'prompt'
    | 'model'
    | 'executionCount'
    | 'owner'
    | 'mine'
  >[],
  layout: SeedLayout = 'row',
): CanvasNodeData[] {
  const placed = seeds.reduce<CanvasNodeData[]>((nodes, seed, i) => {
    const { x, y } = seedPoint(layout, i)
    return placeNode(nodes, seed, x, y)
  }, [])
  return placed.map((node, i) => ({
    ...node,
    enter: `uiIn .7s ${(0.18 + i * 0.08).toFixed(2)}s cubic-bezier(.2,.8,.25,1) backwards`,
  }))
}

/**
 * 놓인 순서대로 앞에서 뒤로 잇는다. 한 장뿐이면 이을 곳이 없다.
 *
 * 이을 수 있는 짝인지는 스키마가 정하지만, 그 판정은 저장·실행 시점에 AI 가 한다.
 * 선택한 순서가 곧 실행 순서라 먼저 이어 두는 편이 손이 덜 간다 — 아니면 사용자가
 * 매번 같은 선을 손으로 긋는다. 틀린 순서는 선을 눌러 지우면 된다.
 */
export function chainEdges(nodes: CanvasNodeData[]): CanvasEdge[] {
  return nodes
    .slice(1)
    .reduce<CanvasEdge[]>((edges, node, i) => connect(nodes, edges, nodes[i].id, node.id), [])
}

/** 이미 정해진 자리에 그대로 놓는다. 겹침은 부르는 쪽에서 이미 풀었다. */
export function moveNodeTo(
  nodes: CanvasNodeData[],
  id: string,
  x: number,
  y: number,
): CanvasNodeData[] {
  return nodes.map((node) => (node.id === id ? { ...node, x, y } : node))
}
