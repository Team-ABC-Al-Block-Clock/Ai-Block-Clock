import { describe, expect, it } from 'vitest'

import {
  CANVAS_EDGES,
  CANVAS_NODES,
  NODE_GAP,
  NODE_SIZE,
  PLACED_FLOATY,
  canConnect,
  canSellWorkflow,
  connect,
  disconnect,
  edgeGeometry,
  edgeId,
  edgePath,
  moveNode,
  overlaps,
  type CanvasBounds,
  placeNode,
  resolveOverlap,
  portPoint,
  removeNode,
  type CanvasNodeData,
} from './canvasWorkflow'

const seed = { id: 'p1', name: '번역', nodeType: 'LLM', mine: true } as const

/** 화면에서 잰 범위를 흉내 낸다. 네 방향 모두 카드 절반까지만 넘어간다. */
const HALF = NODE_SIZE / 2
const BOUNDS: CanvasBounds = {
  minX: -HALF,
  maxX: 1062 - HALF,
  minY: -HALF,
  maxY: 900 - HALF,
}

/** 좌표만 보면 되는 검사에서 쓰는 최소 노드. */
const nodeAt = (id: string, x: number, y: number): CanvasNodeData => ({
  id,
  name: id,
  nodeType: 'TOOL',
  x,
  y,
  mine: true,
  floaty: '',
})

describe('placeNode', () => {
  it('목록 끝에 붙이고 원래 목록은 건드리지 않는다', () => {
    const before: CanvasNodeData[] = [nodeAt('a', 0, 200)]
    const after = placeNode(before, seed, 300, 400)

    expect(before).toHaveLength(1)
    expect(after).toHaveLength(2)
    expect(after[1]).toMatchObject({ id: 'p1', name: '번역', x: 300, y: 400 })
  })

  it('화면 밖으로는 카드 절반까지만 나간다', () => {
    const [placed] = placeNode([], seed, -9999, -9999, BOUNDS)
    expect(placed).toMatchObject({ x: -HALF, y: -HALF })
  })

  it('아래로도 절반까지만 나간다', () => {
    const [placed] = placeNode([], seed, 300, 9999, BOUNDS)
    expect(placed.y).toBe(BOUNDS.maxY)
  })

  it('패널 뒤로도 절반까지만 놓인다', () => {
    const [placed] = placeNode([], seed, 9999, 400, BOUNDS)
    expect(placed.x).toBe(BOUNDS.maxX)
  })

  it('범위를 주지 않으면 아무 데나 놓인다 — 화면을 못 잴 때', () => {
    const [placed] = placeNode([], seed, 9999, 9999)
    expect(placed).toMatchObject({ x: 9999, y: 9999 })
  })

  it('등장 모션 없이 떠 있는 모션만 준다 — 이미 놓인 자리에서 튀지 않게', () => {
    const [placed] = placeNode([], seed, 300, 400)
    expect(placed.floaty).toBe(PLACED_FLOATY)
    expect(placed.enter).toBeUndefined()
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

  it('화면 왼쪽·위로 옮겨도 절반은 남는다', () => {
    const [moved] = moveNode([nodeAt('a', 300, 400)], 'a', -900, -900, BOUNDS)
    expect(moved).toMatchObject({ x: -HALF, y: -HALF })
  })

  it('패널을 넘어가도 절반은 남는다', () => {
    const [moved] = moveNode([nodeAt('a', 300, 400)], 'a', 9999, 400, BOUNDS)
    expect(moved).toMatchObject({ x: BOUNDS.maxX, y: 400 })
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

  it('자리표시자가 서로 겹치지 않는다', () => {
    for (const node of CANVAS_NODES) {
      expect(overlaps(CANVAS_NODES, node.id, node.x, node.y)).toBe(false)
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

  it('벽이 없어도 되풀이가 끝난다 — 겹침만 풀고 멈춘다', () => {
    const nodes = [nodeAt('a', 0, 0), nodeAt('b', 100, 0), nodeAt('c', 50, 100)]
    const moved = resolveOverlap(nodes, 'x', 50, 40)
    expect(Number.isFinite(moved.x) && Number.isFinite(moved.y)).toBe(true)
    expect(overlaps(nodes, 'x', moved.x, moved.y)).toBe(false)
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
})

describe('패널 경계와 겹침이 함께 걸릴 때', () => {
  it('패널 앞에서 밀려도 다른 카드와 겹치지 않는다', () => {
    const nodes = [nodeAt('a', BOUNDS.maxX, 400)]
    const moved = resolveOverlap(nodes, 'x', 9999, 420, BOUNDS)
    expect(moved.x).toBeLessThanOrEqual(BOUNDS.maxX)
    expect(overlaps(nodes, 'x', moved.x, moved.y)).toBe(false)
  })

  it('패널 앞에 두 장이 있어도 서로 비킨다', () => {
    const nodes = [nodeAt('a', BOUNDS.maxX, 300), nodeAt('b', BOUNDS.maxX, 500)]
    const moved = resolveOverlap(nodes, 'x', 9999, 400, BOUNDS)
    expect(moved.x).toBeLessThanOrEqual(BOUNDS.maxX)
    expect(overlaps(nodes, 'x', moved.x, moved.y)).toBe(false)
  })

  it('네 방향이 다 막혀도 범위 안에서 답을 낸다', () => {
    const nodes = [nodeAt('a', 300, 300), nodeAt('b', 480, 300), nodeAt('c', 390, 460)]
    const moved = resolveOverlap(nodes, 'x', 390, 340, BOUNDS)
    expect(moved.x).toBeGreaterThanOrEqual(BOUNDS.minX)
    expect(moved.x).toBeLessThanOrEqual(BOUNDS.maxX)
    expect(moved.y).toBeGreaterThanOrEqual(BOUNDS.minY)
    expect(moved.y).toBeLessThanOrEqual(BOUNDS.maxY)
  })
})

describe('portPoint', () => {
  it('출력은 오른쪽 변의 가운데다', () => {
    expect(portPoint({ x: 12, y: 400 }, 'out')).toEqual({ x: 12 + NODE_SIZE, y: 484 })
  })

  it('입력은 왼쪽 변의 가운데다', () => {
    expect(portPoint({ x: 209, y: 320 }, 'in')).toEqual({ x: 209, y: 404 })
  })
})

describe('edgePath', () => {
  it('막는 카드가 없으면 꺾이지 않는다', () => {
    expect(edgePath({ x: 180, y: 404 }, { x: 409, y: 404 })).toBe('M180 404 L409 404')
  })

  it('양 끝은 포트에 정확히 닿는다', () => {
    const d = edgePath({ x: 180, y: 484 }, { x: 409, y: 404 }, [{ x: 300, y: 440 }])
    expect(d.startsWith('M180 484')).toBe(true)
    expect(d.endsWith('L409 404')).toBe(true)
  })
})

describe('edgeGeometry', () => {
  it('선이 포트에서 포트로 이어진다', () => {
    for (const edge of edgeGeometry(CANVAS_NODES)) {
      const from = CANVAS_NODES.find((n) => n.id === edge.from)!
      const to = CANVAS_NODES.find((n) => n.id === edge.to)!
      const 시작 = portPoint(from, 'out')
      const 끝 = portPoint(to, 'in')
      expect(edge.d.startsWith(`M${시작.x} ${시작.y}`)).toBe(true)
      expect(edge.d.endsWith(`${끝.x} ${끝.y}`)).toBe(true)
    }
  })

  it('노드를 옮기면 선이 따라온다', () => {
    const 전 = edgeGeometry(CANVAS_NODES)[0].d
    const 후 = edgeGeometry(moveNode(CANVAS_NODES, 'seed-1', 12, 700))[0].d
    expect(후).not.toBe(전)
    expect(후.startsWith('M180 784')).toBe(true)
  })

  it('한쪽 노드가 없는 선은 버린다', () => {
    const 없앰 = CANVAS_NODES.filter((n) => n.id !== 'seed-1')
    expect(edgeGeometry(없앰).map((e) => e.id)).toEqual([
      edgeId('seed-2', 'seed-3'),
      edgeId('seed-3', 'seed-4'),
      edgeId('seed-4', 'seed-5'),
    ])
  })
})

describe('CANVAS_EDGES', () => {
  it('선의 id 가 겹치지 않는다', () => {
    expect(new Set(CANVAS_EDGES.map((e) => e.id)).size).toBe(CANVAS_EDGES.length)
  })

  it('양 끝이 모두 실제 노드를 가리킨다', () => {
    const ids = new Set(CANVAS_NODES.map((n) => n.id))
    for (const edge of CANVAS_EDGES) {
      expect(ids.has(edge.from)).toBe(true)
      expect(ids.has(edge.to)).toBe(true)
    }
  })

  it('자기 자신을 잇는 선은 없다', () => {
    expect(CANVAS_EDGES.every((e) => e.from !== e.to)).toBe(true)
  })
})

describe('canConnect', () => {
  const 사슬 = [
    { id: 'a__b', from: 'a', to: 'b' },
    { id: 'b__c', from: 'b', to: 'c' },
  ]

  it('빈 입력이면 이을 수 있다', () => {
    expect(canConnect(사슬, 'a', 'd')).toBe(true)
  })

  it('입력이 이미 찬 노드로는 못 잇는다 — 선은 하나만 받는다', () => {
    expect(canConnect(사슬, 'a', 'c')).toBe(false)
  })

  it('출력은 여러 갈래로 나뉠 수 있다', () => {
    const 갈래 = connect(사슬, 'a', 'd')
    expect(갈래).toHaveLength(3)
    expect(canConnect(갈래, 'a', 'e')).toBe(true)
  })

  it('자기 자신은 못 잇는다', () => {
    expect(canConnect(사슬, 'a', 'a')).toBe(false)
  })

  it('이미 있는 선은 다시 못 잇는다', () => {
    expect(canConnect(사슬, 'a', 'b')).toBe(false)
  })

  it('바로 되돌아가는 선은 막는다', () => {
    expect(canConnect(사슬, 'b', 'a')).toBe(false)
  })

  it('멀리 돌아 고리가 되는 선도 막는다', () => {
    expect(canConnect(사슬, 'c', 'a')).toBe(false)
  })

  it('고리가 아니어도 입력이 찼으면 막는다', () => {
    expect(canConnect([...사슬, { id: 'a__d', from: 'a', to: 'd' }], 'd', 'c')).toBe(false)
  })
})

describe('connect', () => {
  it('이을 수 있으면 끝에 붙인다', () => {
    const after = connect([], 'a', 'b')
    expect(after).toHaveLength(1)
    expect(after[0]).toMatchObject({ id: edgeId('a', 'b'), from: 'a', to: 'b' })
  })

  it('이을 수 없으면 목록이 그대로다', () => {
    const before = [{ id: 'a__b', from: 'a', to: 'b' }]
    expect(connect(before, 'b', 'a')).toBe(before)
    expect(connect(before, 'a', 'a')).toBe(before)
    expect(connect(before, 'a', 'b')).toBe(before)
  })

  it('원래 목록을 건드리지 않는다', () => {
    const before = [{ id: 'a__b', from: 'a', to: 'b' }]
    connect(before, 'b', 'c')
    expect(before).toHaveLength(1)
  })

  it('자리표시자에 이어 붙여도 고리가 생기지 않는다', () => {
    expect(canConnect(CANVAS_EDGES, 'seed-5', 'seed-1')).toBe(false)
    // seed-5 는 이미 seed-4 에게서 받고 있다
    expect(canConnect(CANVAS_EDGES, 'seed-1', 'seed-5')).toBe(false)
  })
})

describe('disconnect', () => {
  const edges = [
    { id: 'a__b', from: 'a', to: 'b' },
    { id: 'b__c', from: 'b', to: 'c' },
  ]

  it('고른 선만 지운다', () => {
    expect(disconnect(edges, 'a__b').map((e) => e.id)).toEqual(['b__c'])
  })

  it('없는 id 면 목록이 그대로다', () => {
    expect(disconnect(edges, 'zzz')).toBe(edges)
  })

  it('원래 목록을 건드리지 않는다', () => {
    disconnect(edges, 'a__b')
    expect(edges).toHaveLength(2)
  })
})

describe('removeNode', () => {
  it('노드와 거기 붙은 선을 함께 지운다', () => {
    const after = removeNode(CANVAS_NODES, CANVAS_EDGES, 'seed-2')
    expect(after.nodes.map((n) => n.id)).not.toContain('seed-2')
    expect(after.edges.every((e) => e.from !== 'seed-2' && e.to !== 'seed-2')).toBe(true)
  })

  it('가운데 노드를 지우면 앞뒤 선이 모두 사라진다 — 이어 붙이지는 않는다', () => {
    const after = removeNode(CANVAS_NODES, CANVAS_EDGES, 'seed-3')
    expect(after.edges).toHaveLength(CANVAS_EDGES.length - 2)
  })

  it('지운 뒤 남은 노드의 입력이 비어 다시 이을 수 있다', () => {
    const after = removeNode(CANVAS_NODES, CANVAS_EDGES, 'seed-2')
    expect(canConnect(after.edges, 'seed-1', 'seed-3')).toBe(true)
  })

  it('없는 id 면 둘 다 그대로다', () => {
    const after = removeNode(CANVAS_NODES, CANVAS_EDGES, 'zzz')
    expect(after.nodes).toBe(CANVAS_NODES)
    expect(after.edges).toBe(CANVAS_EDGES)
  })
})
