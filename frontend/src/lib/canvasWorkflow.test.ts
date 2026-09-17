import { describe, expect, it } from 'vitest'

import {
  CANVAS_BOTTOM,
  CANVAS_NODES,
  CANVAS_TOP,
  CANVAS_WIDTH,
  NODE_GAP,
  NODE_SIZE,
  PLACED_ANIMATION,
  canSellWorkflow,
  clampToCanvas,
  moveNode,
  overlaps,
  placeNode,
  resolveOverlap,
  type CanvasNodeData,
} from './canvasWorkflow'

const seed = { id: 'p1', name: '번역', nodeType: 'LLM', mine: true } as const

/** 좌표만 보면 되는 검사에서 쓰는 최소 노드. */
const nodeAt = (id: string, x: number, y: number): CanvasNodeData => ({
  id,
  name: id,
  nodeType: 'TOOL',
  x,
  y,
  mine: true,
  animation: '',
})

describe('clampToCanvas', () => {
  it('작업면 안이면 그대로 둔다', () => {
    expect(clampToCanvas(300, 400)).toEqual({ x: 300, y: 400 })
  })

  it('왼쪽·위로 넘치면 경계로 당긴다', () => {
    expect(clampToCanvas(-50, -50)).toEqual({ x: 0, y: CANVAS_TOP })
  })

  it('오른쪽 패널 아래로 숨지 않게 카드 한 장만큼 남긴다', () => {
    expect(clampToCanvas(9999, 400).x).toBe(CANVAS_WIDTH - NODE_SIZE)
  })

  it('아래 툴바에 가리지 않게 자른다', () => {
    expect(clampToCanvas(300, 9999).y).toBe(CANVAS_BOTTOM - NODE_SIZE)
  })
})

describe('placeNode', () => {
  it('목록 끝에 붙이고 원래 목록은 건드리지 않는다', () => {
    const before: CanvasNodeData[] = [nodeAt('a', 0, 200)]
    const after = placeNode(before, seed, 300, 400)

    expect(before).toHaveLength(1)
    expect(after).toHaveLength(2)
    expect(after[1]).toMatchObject({ id: 'p1', name: '번역', x: 300, y: 400 })
  })

  it('놓은 자리가 작업면 밖이면 잘라서 넣는다', () => {
    const [placed] = placeNode([], seed, -80, 9999)
    expect(placed).toMatchObject({ x: 0, y: CANVAS_BOTTOM - NODE_SIZE })
  })

  it('등장 모션 없이 떠 있는 모션만 준다 — 이미 놓인 자리에서 튀지 않게', () => {
    const [placed] = placeNode([], seed, 300, 400)
    expect(placed.animation).toBe(PLACED_ANIMATION)
  })

  it('같은 항목을 두 번 놓아도 id 로 갈린다', () => {
    const twice = placeNode(placeNode([], seed, 100, 300), { ...seed, id: 'p2' }, 400, 300)
    expect(twice.map((n) => n.id)).toEqual(['p1', 'p2'])
    expect(new Set(twice.map((n) => n.id)).size).toBe(2)
  })
})

describe('moveNode', () => {
  it('id 가 맞는 노드만 옮긴다', () => {
    const moved = moveNode([nodeAt('a', 0, 200), nodeAt('b', 400, 300)], 'b', 500, 350)
    expect(moved[0]).toMatchObject({ x: 0, y: 200 })
    expect(moved[1]).toMatchObject({ x: 500, y: 350 })
  })

  it('옮길 때도 작업면 밖으로 나가지 않는다', () => {
    const [moved] = moveNode([nodeAt('a', 300, 400)], 'a', 9999, -80)
    expect(moved).toMatchObject({ x: CANVAS_WIDTH - NODE_SIZE, y: CANVAS_TOP })
  })

  it('없는 id 면 목록이 그대로다', () => {
    const before = [nodeAt('a', 0, 200)]
    expect(moveNode(before, 'zzz', 500, 500)).toEqual(before)
  })

  it('원래 목록을 건드리지 않는다', () => {
    const before = [nodeAt('a', 0, 200)]
    moveNode(before, 'a', 500, 500)
    expect(before[0]).toMatchObject({ x: 0, y: 200 })
  })
})

describe('canSellWorkflow', () => {
  it('내 것만 있으면 팔 수 있다', () => {
    expect(canSellWorkflow([nodeAt('a', 0, 200), nodeAt('b', 400, 300)])).toBe(true)
  })

  it('다른 사람의 것이 하나라도 섞이면 팔 수 없다', () => {
    const mixed = placeNode([nodeAt('a', 0, 200)], { ...seed, mine: false }, 400, 300)
    expect(canSellWorkflow(mixed)).toBe(false)
  })

  it('빈 캔버스는 등록할 것이 없다', () => {
    expect(canSellWorkflow([])).toBe(false)
  })
})

describe('CANVAS_NODES', () => {
  it('자리표시자 노드의 id 가 겹치지 않는다', () => {
    expect(new Set(CANVAS_NODES.map((n) => n.id)).size).toBe(CANVAS_NODES.length)
  })

  it('자리표시자가 전부 작업면 안에 있다', () => {
    for (const node of CANVAS_NODES) {
      expect(clampToCanvas(node.x, node.y)).toEqual({ x: node.x, y: node.y })
    }
  })
})

describe('resolveOverlap', () => {
  it('겹치는 것이 없으면 그대로 둔다', () => {
    const nodes = [nodeAt('a', 0, 200)]
    expect(resolveOverlap(nodes, 'b', 600, 500)).toEqual({ x: 600, y: 500 })
  })

  it('자기 자신은 겹침으로 세지 않는다', () => {
    const nodes = [nodeAt('a', 300, 400)]
    expect(resolveOverlap(nodes, 'a', 300, 400)).toEqual({ x: 300, y: 400 })
  })

  it('겹쳐 놓으면 떨어뜨린다', () => {
    const nodes = [nodeAt('a', 300, 400)]
    const moved = resolveOverlap(nodes, 'b', 320, 410)
    expect(overlaps(nodes, 'b', moved.x, moved.y)).toBe(false)
  })

  it('완전히 포개도 방향을 찾아 뗀다', () => {
    const nodes = [nodeAt('a', 300, 400)]
    const moved = resolveOverlap(nodes, 'b', 300, 400)
    expect(overlaps(nodes, 'b', moved.x, moved.y)).toBe(false)
  })

  it('여러 개가 둘러싸도 빈자리를 찾는다', () => {
    const nodes = [nodeAt('a', 200, 300), nodeAt('b', 380, 300), nodeAt('c', 290, 480)]
    const moved = resolveOverlap(nodes, 'x', 295, 380)
    expect(overlaps(nodes, 'x', moved.x, moved.y)).toBe(false)
  })

  it('밀어낸 자리도 작업면 안이다', () => {
    const nodes = [nodeAt('a', 800, 760)]
    const moved = resolveOverlap(nodes, 'x', 805, 765)
    expect(clampToCanvas(moved.x, moved.y)).toEqual(moved)
  })

  it('맞닿지 않게 간격을 남긴다', () => {
    const nodes = [nodeAt('a', 300, 400)]
    const moved = resolveOverlap(nodes, 'b', 330, 400)
    expect(Math.hypot(moved.x - 300, moved.y - 400)).toBeGreaterThanOrEqual(
      NODE_SIZE + NODE_GAP - 0.5,
    )
  })
})

describe('겹침은 놓을 때도 옮길 때도 막는다', () => {
  it('이미 있는 자리에 놓으면 밀려난다', () => {
    const base = [nodeAt('a', 300, 400)]
    const [, placed] = placeNode(base, seed, 305, 405)
    expect(overlaps(base, placed.id, placed.x, placed.y)).toBe(false)
  })

  it('다른 카드 위로 옮기면 밀려난다', () => {
    const base = [nodeAt('a', 300, 400), nodeAt('b', 0, 200)]
    const after = moveNode(base, 'b', 302, 402)
    const b = after.find((n) => n.id === 'b')!
    expect(overlaps(after, 'b', b.x, b.y)).toBe(false)
  })

  it('자리표시자 다섯 개가 서로 겹치지 않는다', () => {
    for (const node of CANVAS_NODES) {
      expect(overlaps(CANVAS_NODES, node.id, node.x, node.y)).toBe(false)
    }
  })
})
