import type { ItemNodeType } from '@/api/workflows'

/** 노드 카드의 한 변(px). 좌표는 카드의 왼쪽 위 모서리이다. */
export const NODE_SIZE = 168
/** 작업면 폭 — 캔버스(1440)에서 오른쪽 패널(460)을 뺀 나머지. */
export const CANVAS_WIDTH = 980
/** 위는 안내 문구, 아래는 배율 툴바가 차지한다. 노드는 그 사이에만 놓인다. */
export const CANVAS_TOP = 110
export const CANVAS_BOTTOM = 940

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

/**
 * 카드가 작업면 밖으로 나가지 않게 자른다.
 *
 * 오른쪽 패널 아래로 숨거나 툴바에 가리면 다시 집을 방법이 없다 — 놓을 때도 옮길 때도 거른다.
 */
export function clampToCanvas(x: number, y: number): { x: number; y: number } {
  return {
    x: Math.min(Math.max(x, 0), CANVAS_WIDTH - NODE_SIZE),
    y: Math.min(Math.max(y, CANVAS_TOP), CANVAS_BOTTOM - NODE_SIZE),
  }
}

/** 팔레트에서 끌어온 항목을 목록 끝에 놓는다. 좌표는 카드의 왼쪽 위. */
export function placeNode(
  nodes: CanvasNodeData[],
  seed: Pick<CanvasNodeData, 'id' | 'name' | 'nodeType' | 'mine'>,
  x: number,
  y: number,
): CanvasNodeData[] {
  return [...nodes, { ...seed, ...clampToCanvas(x, y), animation: PLACED_ANIMATION }]
}

/** 놓인 노드를 옮긴다. 없는 id 면 목록을 그대로 돌려준다. */
export function moveNode(
  nodes: CanvasNodeData[],
  id: string,
  x: number,
  y: number,
): CanvasNodeData[] {
  return nodes.map((node) => (node.id === id ? { ...node, ...clampToCanvas(x, y) } : node))
}
