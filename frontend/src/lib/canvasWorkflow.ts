import type { ItemNodeType } from '@/api/workflows'

/** 노드 카드의 한 변(px). 좌표는 카드의 왼쪽 위 모서리이다. */
export const NODE_SIZE = 168
/** 카드 사이에 남기는 최소 간격(px). 맞닿으면 두 개가 한 덩어리로 보인다. */
export const NODE_GAP = 10
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

function clampTo(x: number, y: number, bounds: CanvasBounds): { x: number; y: number } {
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
  x: number
  y: number
  mine: boolean
  animation: string
}

/** 놓인 노드에 주는 기본 모션. 자리를 잡은 뒤라 등장 모션은 주지 않는다. */
export const PLACED_ANIMATION = 'floaty 6.6s ease-in-out infinite'

export const CANVAS_NODES: CanvasNodeData[] = [
  {
    id: 'seed-1',
    name: '파일 수집',
    nodeType: 'TRIGGER',
    x: 12,
    y: 400,
    mine: true,
    animation: 'floaty 6.4s ease-in-out infinite, uiIn .7s .18s cubic-bezier(.2,.8,.25,1) both',
  },
  {
    id: 'seed-2',
    name: '내용 분석',
    nodeType: 'LLM',
    x: 209,
    y: 320,
    mine: true,
    animation: 'floaty 6.9s .3s ease-in-out infinite, uiIn .7s .26s cubic-bezier(.2,.8,.25,1) both',
  },
  {
    id: 'seed-3',
    name: '분류 및 태깅',
    nodeType: 'TOOL',
    x: 406,
    y: 440,
    mine: true,
    animation: 'floaty 7.1s .8s ease-in-out infinite, uiIn .7s .34s cubic-bezier(.2,.8,.25,1) both',
  },
  {
    id: 'seed-4',
    name: '요약 생성',
    nodeType: 'LLM',
    x: 603,
    y: 330,
    mine: true,
    animation: 'floaty 6.8s .4s ease-in-out infinite, uiIn .7s .42s cubic-bezier(.2,.8,.25,1) both',
  },
  {
    id: 'seed-5',
    name: '결과 전달',
    nodeType: 'TOOL',
    x: 800,
    y: 430,
    mine: true,
    animation: 'floaty 7.6s 1.2s ease-in-out infinite, uiIn .7s .5s cubic-bezier(.2,.8,.25,1) both',
  },
]

/** 다른 사람의 에이전트가 하나라도 섞이면 팔 수 없다. 빈 캔버스도 등록할 것이 없다. */
export function canSellWorkflow(nodes: CanvasNodeData[]): boolean {
  return nodes.length > 0 && nodes.every((node) => node.mine)
}

/** 겹치는 카드가 하나라도 있는지. */
export function overlaps(nodes: CanvasNodeData[], id: string, x: number, y: number): boolean {
  return nodes.some(
    (node) => node.id !== id && Math.hypot(x - node.x, y - node.y) < NODE_SIZE + NODE_GAP,
  )
}

/**
 * 원하는 자리 둘레를 돌며 가장 가까운 빈자리를 찾는다.
 *
 * 카드 두 장 사이에 정확히 끼면 밀어내는 방향이 서로 상쇄돼 되풀이해도 빠져나가지 못한다.
 * 그때 쓰는 마지막 수단이라 가운데부터 한 겹씩 넓혀 가며 본다.
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
      const spot = clampTo(
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
 * 갈 수 있는 자리는 `bounds` 가 정한다 — 패널 뒤로는 못 가고, 화면 밖으로는 절반까지다.
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
  let point = clampTo(x, y, bounds)

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
    point = clampTo(point.x + ux * push, point.y + uy * push, bounds)
  }
  // 밀어내기로 못 풀었으면 둘레에서 빈자리를 찾는다.
  return findFreeSpot(nodes, id, point.x, point.y, bounds) ?? point
}

/** 팔레트에서 끌어온 항목을 목록 끝에 놓는다. 좌표는 카드의 왼쪽 위. */
export function placeNode(
  nodes: CanvasNodeData[],
  seed: Pick<CanvasNodeData, 'id' | 'name' | 'nodeType' | 'mine'>,
  x: number,
  y: number,
  bounds: CanvasBounds = NO_BOUNDS,
): CanvasNodeData[] {
  return [
    ...nodes,
    { ...seed, ...resolveOverlap(nodes, seed.id, x, y, bounds), animation: PLACED_ANIMATION },
  ]
}

/** 놓인 노드를 옮긴다. 없는 id 면 목록을 그대로 돌려준다. */
export function moveNode(
  nodes: CanvasNodeData[],
  id: string,
  x: number,
  y: number,
  bounds: CanvasBounds = NO_BOUNDS,
): CanvasNodeData[] {
  return nodes.map((node) =>
    node.id === id ? { ...node, ...resolveOverlap(nodes, id, x, y, bounds) } : node,
  )
}
