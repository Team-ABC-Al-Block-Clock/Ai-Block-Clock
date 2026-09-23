import type { ItemNodeType, SourceType } from '@/api/workflows'

import { roundedPath, routeEdge } from './edgeRoute'

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

/**
 * 편집·대기·실행 화면이 함께 그리는 워크플로우.
 *
 * 워크플로우 저장·조회 API 가 아직 없어 자리표시자를 한 곳에 두고 세 화면이 같이 읽는다.
 */
export interface CanvasNodeData {
  /** 같은 항목을 두 번 놓을 수 있어 이름으로는 가를 수 없다. */
  id: string
  name: string
  nodeType: ItemNodeType
  /** 팔레트 항목의 `itemType`. 서버는 이 값으로 노드와 에이전트를 가른다 */
  sourceType: SourceType
  /** 팔레트 항목의 `itemId`. 화면용 `id` 와 달리 서버가 아는 원본을 가리킨다 */
  sourceId: string
  x: number
  y: number
  mine: boolean
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

export const CANVAS_NODES: CanvasNodeData[] = [
  {
    id: 'seed-1',
    name: '파일 수집',
    nodeType: 'TRIGGER',
    sourceType: 'NODE',
    sourceId: 'node_001',
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
    x: 800,
    y: 430,
    mine: true,
    floaty: 'floaty 7.6s 1.2s ease-in-out infinite',
    enter: 'uiIn .7s .5s cubic-bezier(.2,.8,.25,1) backwards',
  },
]

/** 노드를 잇는 선. `from` 의 출력에서 `to` 의 입력으로 간다. */
export interface CanvasEdge {
  id: string
  from: string
  to: string
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
 * 이어도 되는 짝인지. 자기 자신·중복·고리를 막는다.
 *
 * 입력은 하나만. 들어오는 선이 여럿이면 어느 것을 기다릴지 알 수 없다. 출력은 여러 갈래 가능.
 */
export function canConnect(edges: CanvasEdge[], from: string, to: string): boolean {
  if (from === to) return false
  if (edges.some((edge) => edge.to === to)) return false
  return !reaches(edges, to, from)
}

/** 선 연결. 이을 수 없는 짝이면 목록 그대로. */
export function connect(edges: CanvasEdge[], from: string, to: string): CanvasEdge[] {
  if (!canConnect(edges, from, to)) return edges
  return [...edges, { id: edgeId(from, to), from, to }]
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
    nodes: nodes.filter((node) => node.id !== id),
    edges: edges.filter((edge) => edge.from !== id && edge.to !== id),
  }
}

/** 선이 드나드는 자리. 입력은 왼쪽, 출력은 오른쪽, 둘 다 카드 높이의 가운데. */
export function portPoint(
  node: Pick<CanvasNodeData, 'x' | 'y'>,
  side: 'in' | 'out',
): { x: number; y: number } {
  return { x: side === 'out' ? node.x + NODE_SIZE : node.x, y: node.y + NODE_SIZE / 2 }
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
  edges: CanvasEdge[] = CANVAS_EDGES,
): { id: string; from: string; to: string; d: string }[] {
  // 제 카드도 가로지르면 안 된다. 포트에서 나가는 짧은 구간만 예외.
  const avoid = nodes.map((node) => ({ x: node.x + NODE_SIZE / 2, y: node.y + NODE_SIZE / 2 }))
  return edges.flatMap((edge) => {
    const from = nodes.find((node) => node.id === edge.from)
    const to = nodes.find((node) => node.id === edge.to)
    if (!from || !to) return []
    return [{ ...edge, d: edgePath(portPoint(from, 'out'), portPoint(to, 'in'), avoid) }]
  })
}

/** 다른 사람의 에이전트가 하나라도 섞이면 팔 수 없다. 빈 캔버스도 등록할 것이 없다. */
export function canSellWorkflow(nodes: CanvasNodeData[]): boolean {
  return nodes.length > 0 && nodes.every((node) => node.mine)
}

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
  seed: Pick<CanvasNodeData, 'id' | 'name' | 'nodeType' | 'sourceType' | 'sourceId' | 'mine'>,
  x: number,
  y: number,
  bounds: CanvasBounds = NO_BOUNDS,
): CanvasNodeData[] {
  return [
    ...nodes,
    { ...seed, ...resolveOverlap(nodes, seed.id, x, y, bounds), floaty: PLACED_FLOATY },
  ]
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
