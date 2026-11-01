import { describe, expect, it } from 'vitest'

import { PANE_CONTENT_WIDTH, PANE_HEIGHT } from '@/components/CanvasPane'
import { ZOOM_MIN, fitViewport } from '@/lib/viewport'

import { CANVAS_EDGES, CANVAS_NODES } from '@/test/canvasFixture'

import {
  NO_BOUNDS,
  canConnect,
  graphProblem,
  runProblem,
  chainPositions,
  clampToBounds,
  canSellWorkflow,
  sellProblem,
  connect,
  disconnect,
  edgeGeometry,
  edgePath,
  chainEdges,
  linearOrder,
  moveNodeTo,
  nodesBounds,
  seedCanvasNodes,
  type CanvasBounds,
  placeNode,
  resolveOverlap,
  portPoint,
  removeNode,
  treeOrder,
  upstreamOf,
  type CanvasEdge,
  type CanvasNodeData,
} from './canvasWorkflow'

const seed = {
  id: 'p1',
  name: '번역',
  nodeType: 'LLM',
  sourceType: 'NODE',
  sourceId: 'node_009',
  price: '11',
  summary: null,
  prompt: null,
  model: null,
  mine: true,
} as const

// 아래 다섯은 구현에서 가져오지 않는다. 빌려 쓰면 구현이 틀릴 때 단언도 나란히 틀린다.
const NODE_SIZE = 168
const NODE_GAP = 24
/** 카드 두 장의 좌표가 이만큼은 떨어져 있어야 한다. */
const MIN_APART = NODE_SIZE + NODE_GAP
const PLACED_FLOATY = 'floaty 6.6s ease-in-out infinite'
const edgeId = (from: string, to: string) => `${from}__${to}`

/** 화면에서 잰 범위를 흉내 낸다. 네 방향 모두 카드 절반까지만 넘어간다. */
const HALF = NODE_SIZE / 2
const BOUNDS: CanvasBounds = {
  minX: -HALF,
  maxX: 1062 - HALF,
  minY: -HALF,
  maxY: 900 - HALF,
}

/** 어느 카드와도 맞닿지 않는가. */
const apart = (nodes: CanvasNodeData[], id: string, x: number, y: number) =>
  nodes.every((node) => node.id === id || Math.hypot(x - node.x, y - node.y) >= MIN_APART)

/** 좌표만 보면 되는 검사에서 쓰는 최소 노드. */
const nodeAt = (id: string, x: number, y: number): CanvasNodeData => ({
  id,
  name: id,
  nodeType: 'LLM',
  sourceType: 'NODE',
  sourceId: `src-${id}`,
  price: '1',
  summary: null,
  prompt: null,
  model: null,
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

describe('clampToBounds', () => {
  it('범위 안이면 그대로 둔다', () => {
    expect(clampToBounds(300, 400, BOUNDS)).toEqual({ x: 300, y: 400 })
  })

  it('화면 왼쪽·위로 끌어도 절반은 남는다', () => {
    expect(clampToBounds(-900, -900, BOUNDS)).toEqual({ x: -HALF, y: -HALF })
  })

  it('패널을 넘어가도 절반은 남는다', () => {
    expect(clampToBounds(9999, 400, BOUNDS)).toEqual({ x: BOUNDS.maxX, y: 400 })
  })

  it('아래로도 절반까지만 내려간다', () => {
    expect(clampToBounds(300, 9999, BOUNDS)).toEqual({ x: 300, y: BOUNDS.maxY })
  })

  it('겹침은 보지 않는다 — 끄는 동안에는 커서를 그대로 따라간다', () => {
    expect(clampToBounds(305, 405, NO_BOUNDS)).toEqual({ x: 305, y: 405 })
  })
})

describe('moveNodeTo', () => {
  it('겹치는 자리도 그대로 놓는다 — 자리는 부르는 쪽이 이미 정했다', () => {
    const [, moved] = moveNodeTo([nodeAt('a', 300, 400), nodeAt('b', 0, 0)], 'b', 305, 405)
    expect(moved).toMatchObject({ x: 305, y: 405 })
  })

  it('없는 id 면 목록이 그대로다', () => {
    const before = [nodeAt('a', 0, 200)]
    expect(moveNodeTo(before, 'zzz', 500, 500)).toEqual(before)
  })

  it('원래 목록을 건드리지 않는다', () => {
    const before = [nodeAt('a', 0, 200)]
    moveNodeTo(before, 'a', 500, 500)
    expect(before[0]).toMatchObject({ x: 0, y: 200 })
  })
})

describe('canSellWorkflow', () => {
  it('분기가 있어도 작업 노드가 전부 내 것이면 팔 수 있다', () => {
    const nodes = [
      nodeAt('a', 0, 0),
      { ...nodeAt('br', 0, 0), controlType: 'BRANCH' as const, mine: null },
    ]
    expect(canSellWorkflow(nodes)).toBe(true)
    expect(sellProblem(nodes)).toBeNull()
  })
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
  // 제어·입출력만 놓인 구성은 팔 것이 없다.
  it('제어·입출력만 있으면 팔 수 없다', () => {
    const start = {
      ...nodeAt('s', 0, 0),
      nodeType: null,
      sourceType: 'IO' as const,
      sourceId: '',
      mine: null,
      ioType: 'START' as const,
    }
    expect(canSellWorkflow([start])).toBe(false)
    expect(canSellWorkflow([start, nodeAt('a', 0, 0)])).toBe(true)
  })
})

describe('linearOrder', () => {
  const chain = (...ids: string[]) => ids.map((id, i) => nodeAt(id, i * 200, 300))
  const link = (from: string, to: string) => ({ id: edgeId(from, to), from, to })
  const ids = (order: CanvasNodeData[] | null) => order?.map((node) => node.id) ?? null

  it('한 줄이면 머리부터 순서를 낸다', () => {
    const order = linearOrder(chain('a', 'b', 'c'), [link('a', 'b'), link('b', 'c')])
    expect(ids(order)).toEqual(['a', 'b', 'c'])
  })

  // 놓인 순서가 아니라 선을 따라간 순서여야 한다. 좌표나 배열 순서로 줄 세우면 뒤집힌다.
  it('놓인 순서가 뒤죽박죽이어도 선을 따라간다', () => {
    const nodes = [nodeAt('c', 0, 0), nodeAt('a', 0, 0), nodeAt('b', 0, 0)]
    expect(ids(linearOrder(nodes, [link('a', 'b'), link('b', 'c')]))).toEqual(['a', 'b', 'c'])
  })

  it('노드 하나에 선이 없어도 한 줄이다', () => {
    expect(ids(linearOrder(chain('a'), []))).toEqual(['a'])
  })

  it('빈 캔버스는 저장할 것이 없다', () => {
    expect(linearOrder([], [])).toBeNull()
  })

  it('갈라지면 한 줄이 아니다', () => {
    const nodes = chain('a', 'b', 'c')
    expect(linearOrder(nodes, [link('a', 'b'), link('a', 'c')])).toBeNull()
  })

  it('합쳐져도 한 줄이 아니다', () => {
    const nodes = chain('a', 'b', 'c')
    expect(linearOrder(nodes, [link('a', 'c'), link('b', 'c')])).toBeNull()
  })

  it('따로 떨어진 노드가 있으면 한 줄이 아니다', () => {
    const nodes = chain('a', 'b', 'c')
    expect(linearOrder(nodes, [link('a', 'b')])).toBeNull()
  })

  it('선만 이어 두고 노드를 안 놓으면 한 줄이 아니다', () => {
    expect(linearOrder(chain('a', 'b'), [link('a', 'b'), link('b', 'zzz')])).toBeNull()
  })

  // 머리가 없는 고리는 무한히 돌 수 있는 자리다. 개수로 먼저 걸러진다.
  it('고리만 있으면 머리가 없어 한 줄이 아니다', () => {
    const nodes = chain('a', 'b')
    expect(linearOrder(nodes, [link('a', 'b'), link('b', 'a')])).toBeNull()
  })

  it('자리표시자 워크플로우는 한 줄이다', () => {
    expect(ids(linearOrder(CANVAS_NODES, CANVAS_EDGES))).toEqual([
      'seed-1',
      'seed-2',
      'seed-3',
      'seed-4',
      'seed-5',
    ])
  })
})

describe('treeOrder — 선 차례', () => {
  const link = (from: string, to: string) => ({ id: edgeId(from, to), from, to })
  const branch = (id: string) => ({
    ...nodeAt(id, 0, 0),
    nodeType: null,
    sourceType: 'CONTROL' as const,
    sourceId: '',
    mine: null,
    controlType: 'BRANCH' as const,
    condition: { fromId: 'a', path: 'level', operator: 'EQ' as const, right: 'high' },
  })

  /**
   * 서버는 노드를 훑은 뒤 노드마다 제 갈래를 모은다(`WorkflowGraph.edgesInOrder`).
   * 차례가 어긋나면 다시 불러올 때 선 키가 달라진다.
   */
  it('노드를 먼저, 그 노드의 갈래를 모아서 센다', () => {
    const nodes = [
      nodeAt('a', 0, 0),
      branch('br'),
      nodeAt('t', 0, 0),
      nodeAt('f', 0, 0),
      nodeAt('z', 0, 0),
    ]
    const edges = [
      link('a', 'br'),
      { ...link('br', 't'), when: true },
      { ...link('br', 'f'), when: false },
      link('t', 'z'),
    ]
    const order = treeOrder(nodes, edges)

    expect(order?.nodes.map((n) => n.id)).toEqual(['a', 'br', 't', 'z', 'f'])
    expect(order?.edges.map((e) => e.id)).toEqual(['a__br', 'br__t', 'br__f', 't__z'])
  })

  // 서버 `walk` 는 들어오는 선을 다 채운 뒤에야 합류를 센다.
  it('합류는 두 갈래를 다 지난 뒤에 센다', () => {
    const nodes = [
      nodeAt('a', 0, 0),
      branch('br'),
      nodeAt('t', 0, 0),
      nodeAt('f', 0, 0),
      nodeAt('j', 0, 0),
    ]
    const edges = [
      link('a', 'br'),
      { ...link('br', 't'), when: true },
      { ...link('br', 'f'), when: false },
      link('t', 'j'),
      link('f', 'j'),
    ]
    const order = treeOrder(nodes, edges)

    expect(order?.nodes.map((n) => n.id)).toEqual(['a', 'br', 't', 'f', 'j'])
    expect(order?.edges.map((e) => e.id)).toEqual(['a__br', 'br__t', 'br__f', 't__j', 'f__j'])
  })
})

describe('upstreamOf', () => {
  const link = (from: string, to: string) => ({ id: edgeId(from, to), from, to })
  const nodes = ['a', 'br', 't', 'f', 'j', 'z'].map((id) => nodeAt(id, 0, 0))
  const edges = [
    link('a', 'br'),
    { ...link('br', 't'), when: true },
    { ...link('br', 'f'), when: false },
    link('t', 'j'),
    link('f', 'j'),
    link('j', 'z'),
  ]

  it('한 줄이면 앞의 것 전부, 가까운 것부터', () => {
    expect(upstreamOf(nodes, edges, 't').map((n) => n.id)).toEqual(['br', 'a'])
  })

  // 한쪽 갈래에만 있는 노드는 다른 갈래로 오면 값이 없다.
  it('합류 뒤에서는 어느 길로 와도 거치는 것만', () => {
    expect(upstreamOf(nodes, edges, 'z').map((n) => n.id)).toEqual(['j', 'br', 'a'])
  })
})

describe('graphProblem', () => {
  const link = (from: string, to: string) => ({ id: edgeId(from, to), from, to })
  const io = (id: string, ioType: 'START' | 'END') => ({
    ...nodeAt(id, 0, 0),
    nodeType: null,
    sourceType: 'IO' as const,
    sourceId: '',
    mine: null,
    ioType,
  })
  const branch = (id: string, withCondition = true) => ({
    ...nodeAt(id, 0, 0),
    nodeType: null,
    sourceType: 'CONTROL' as const,
    sourceId: '',
    mine: null,
    controlType: 'BRANCH' as const,
    condition: withCondition
      ? { fromId: 'a', path: 'level', operator: 'EQ' as const, right: 'high' }
      : null,
  })
  const LOOSE = '노드를 빠짐없이 하나로 이어 주세요'

  it('빈 캔버스', () => {
    expect(graphProblem([], [])).toBe('노드를 하나 이상 놓아 주세요')
  })

  it('저장된 출력 예시에 빈 하위 객체가 있어도 저장·실행을 막는다', () => {
    const nodes = [
      { ...nodeAt('a', 0, 0), output: { kind: 'JSON' as const, example: { meta: {} } } },
    ]
    expect(graphProblem(nodes, [])).toMatch(/빈 객체/)
    expect(runProblem(nodes, [])).toMatch(/빈 객체/)
  })

  it('조건 없는 분기', () => {
    const nodes = [nodeAt('a', 0, 0), branch('br', false)]
    expect(graphProblem(nodes, [link('a', 'br')])).toBe('분기 노드에 조건을 넣어 주세요')
  })

  it('한쪽만 이은 분기', () => {
    const nodes = [nodeAt('a', 0, 0), branch('br'), nodeAt('t', 0, 0)]
    const edges = [link('a', 'br'), { ...link('br', 't'), when: true }]
    expect(graphProblem(nodes, edges)).toBe('분기의 true·false 갈래를 하나씩 이어 주세요')
  })

  it('끊긴 자리', () => {
    const nodes = [nodeAt('a', 0, 0), nodeAt('b', 0, 0), nodeAt('c', 0, 0)]
    expect(graphProblem(nodes, [link('a', 'b')])).toBe(LOOSE)
  })

  // 지운 자리를 가리키는 조건이 남으면 저장 버튼은 켜져 있는데 눌러도 되지 않는다.
  it('조건이 읽던 노드가 사라지면 막는다', () => {
    const nodes = [nodeAt('a', 0, 0), branch('br')]
    const after = removeNode(nodes, [link('a', 'br')], 'a')

    expect(after.nodes[0].condition).toBeNull()
    expect(graphProblem(after.nodes, after.edges)).toBe('분기 노드에 조건을 넣어 주세요')
  })

  it('조건이 읽는 노드가 분기 앞에 없으면 막는다', () => {
    // 조건은 a 를 읽는데 a 는 분기 뒤에 있다.
    const nodes = [branch('br'), nodeAt('a', 0, 0), nodeAt('t', 0, 0), nodeAt('f', 0, 0)]
    const edges = [
      { ...link('br', 't'), when: true },
      { ...link('br', 'f'), when: false },
      link('t', 'a'),
    ]
    expect(graphProblem(nodes, edges)).toBe('분기 조건이 읽을 노드를 다시 골라 주세요')
  })

  it('조건은 에이전트도 읽는다', () => {
    const agent = { ...nodeAt('a', 0, 0), sourceType: 'AGENT' as const, nodeType: 'AGENT' as const }
    const nodes = [agent, branch('br'), nodeAt('t', 0, 0), nodeAt('f', 0, 0)]
    const edges = [
      link('a', 'br'),
      { ...link('br', 't'), when: true },
      { ...link('br', 'f'), when: false },
    ]
    expect(graphProblem(nodes, edges)).toBeNull()
  })

  it('이을 수 있는 구성이면 까닭이 없다', () => {
    const nodes = [io('s', 'START'), nodeAt('a', 0, 0)]
    expect(graphProblem(nodes, [link('s', 'a')])).toBeNull()
  })

  /**
   * 아래는 `canConnect` 와 팔레트 칩이 그을 때 막아 화면에서는 나올 수 없다.
   * 서버에서 불러온 구성이 규칙을 어겼을 때 `treeOrder` 가 엉뚱한 차례를 내지 않는지만 본다.
   */
  describe('그을 때 이미 막힌 것들', () => {
    const blocked = (nodes: CanvasNodeData[], edges: CanvasEdge[]) => {
      expect(graphProblem(nodes, edges)).toBe(LOOSE)
      expect(treeOrder(nodes, edges)).toBeNull()
    }

    it('머리가 둘', () => {
      blocked(
        [nodeAt('a', 0, 0), nodeAt('b', 0, 0), nodeAt('c', 0, 0)],
        [link('a', 'c'), link('b', 'c')],
      )
    })

    it('일하는 노드에서 낸 갈래', () => {
      blocked(
        [nodeAt('a', 0, 0), nodeAt('b', 0, 0), nodeAt('c', 0, 0)],
        [link('a', 'b'), link('a', 'c')],
      )
    })

    it('종료에서 나가는 선', () => {
      blocked([io('e', 'END'), nodeAt('a', 0, 0)], [link('e', 'a')])
    })

    it('시작이 맨 앞이 아님', () => {
      blocked([nodeAt('a', 0, 0), io('s', 'START')], [link('a', 's')])
    })

    it('시작·종료가 둘씩', () => {
      blocked([io('s1', 'START'), io('s2', 'START')], [])
      blocked([io('e1', 'END'), io('e2', 'END')], [])
    })
  })
})

describe('runProblem', () => {
  const link = (from: string, to: string) => ({ id: edgeId(from, to), from, to })
  const io = (id: string, ioType: 'START' | 'END') => ({
    ...nodeAt(id, 0, 0),
    nodeType: null,
    sourceType: 'IO' as const,
    sourceId: '',
    mine: null,
    ioType,
  })

  it('시작에서 종료까지 이어지면 실행할 수 있다', () => {
    const nodes = [io('s', 'START'), nodeAt('a', 0, 0), io('e', 'END')]
    expect(runProblem(nodes, [link('s', 'a'), link('a', 'e')])).toBeNull()
  })

  it('원본 출력 예시를 고친 뒤에는 기존 분기 값의 종류를 다시 검사한다', () => {
    const nodes: CanvasNodeData[] = [
      io('s', 'START'),
      { ...nodeAt('a', 0, 0), output: { kind: 'JSON', example: { level: 'high' } } },
      {
        ...nodeAt('br', 0, 0),
        sourceType: 'CONTROL',
        controlType: 'BRANCH',
        condition: { fromId: 'a', path: 'level', operator: 'EQ', right: 'high' },
      },
      nodeAt('t', 0, 0),
      nodeAt('f', 0, 0),
      io('e', 'END'),
    ]
    const edges = [
      link('s', 'a'),
      link('a', 'br'),
      { ...link('br', 't'), when: true },
      { ...link('br', 'f'), when: false },
      link('t', 'e'),
      link('f', 'e'),
    ]
    expect(runProblem(nodes, edges)).toBeNull()
    const changed = nodes.map((node) =>
      node.id === 'a'
        ? { ...node, output: { kind: 'JSON' as const, example: { level: 3 } } }
        : node,
    )
    expect(runProblem(changed, edges)).toMatch(/비교 값을 다시 입력/)
  })

  it('시작이 없으면 그것부터 말한다', () => {
    const nodes = [nodeAt('a', 0, 0), io('e', 'END')]
    expect(runProblem(nodes, [link('a', 'e')])).toBe('시작 노드를 놓아 주세요')
  })

  it('종료가 없으면 그것부터 말한다', () => {
    const nodes = [io('s', 'START'), nodeAt('a', 0, 0)]
    expect(runProblem(nodes, [link('s', 'a')])).toBe('종료 노드를 놓아 주세요')
  })

  it('저장은 되어도 실행은 막힌다 — 일감만 이어 둔 구성', () => {
    const nodes = [nodeAt('a', 0, 0), nodeAt('b', 0, 0)]
    expect(treeOrder(nodes, [link('a', 'b')])).not.toBeNull()
    expect(graphProblem(nodes, [link('a', 'b')])).toBeNull()
    expect(runProblem(nodes, [link('a', 'b')])).toBe('시작·종료 노드는 필수입니다')
  })

  // 저장은 서버가 받지만 엔진이 `BRANCH_ON_TEXT_OUTPUT` 으로 물린다. 돈을 떼기 전에 막는다.
  it('분기가 글 출력 노드를 읽으면 실행을 막는다', () => {
    const text = { ...nodeAt('a', 0, 0), output: { kind: 'TEXT' as const, example: null } }
    const branch = {
      ...nodeAt('br', 0, 0),
      nodeType: null,
      sourceType: 'CONTROL' as const,
      sourceId: '',
      mine: null,
      controlType: 'BRANCH' as const,
      condition: { fromId: 'a', path: 'level', operator: 'EQ' as const, right: 'high' },
    }
    const nodes = [
      io('s', 'START'),
      text,
      branch,
      nodeAt('t', 0, 0),
      nodeAt('f', 0, 0),
      io('e', 'END'),
    ]
    const edges = [
      link('s', 'a'),
      link('a', 'br'),
      { ...link('br', 't'), when: true },
      { ...link('br', 'f'), when: false },
      link('f', 'e'),
      link('t', 'e'),
    ]

    expect(graphProblem(nodes, edges)).toBeNull()
    expect(runProblem(nodes, edges)).toMatch(/JSON/)
  })

  // 서버가 종료로 가는 선을 엔진에 싣지 않아 갈래 하나뿐인 분기가 된다.
  it('분기 갈래가 바로 종료로 가면 잇지 않고, 불러온 구성은 실행을 막는다', () => {
    const branch = {
      ...nodeAt('br', 0, 0),
      nodeType: null,
      sourceType: 'CONTROL' as const,
      sourceId: '',
      mine: null,
      controlType: 'BRANCH' as const,
      condition: { fromId: 'a', path: 'level', operator: 'EQ' as const, right: 'high' },
    }
    const nodes = [io('s', 'START'), nodeAt('a', 0, 0), branch, nodeAt('t', 0, 0), io('e', 'END')]
    const edges = [
      link('s', 'a'),
      link('a', 'br'),
      { ...link('br', 't'), when: true },
      link('t', 'e'),
    ]

    expect(canConnect(nodes, edges, 'br', 'e', false)).toBe(false)
    expect(runProblem(nodes, [...edges, { ...link('br', 'e'), when: false }])).toMatch(
      /노드를 하나 이상 거쳐/,
    )
  })

  // 시작과 종료만 맞붙이면 하는 일이 없다. 그을 때부터 막는다.
  it('시작과 종료를 바로 잇지 못한다', () => {
    const nodes = [io('s', 'START'), io('e', 'END')]
    expect(canConnect(nodes, [], 's', 'e')).toBe(false)
  })
})

describe('chainPositions', () => {
  const chain = (...ids: string[]) => ids.map((id, i) => nodeAt(id, i * 200, 300))
  const link = (from: string, to: string) => ({ id: edgeId(from, to), from, to })
  const io = (id: string, ioType: 'START' | 'END') => ({
    ...nodeAt(id, 0, 0),
    nodeType: null,
    sourceType: 'IO' as const,
    sourceId: '',
    mine: null,
    ioType,
  })
  /** 시작 → … → 종료. 번호는 이 모양이 갖춰진 뒤에야 나온다. */
  const run = (...ids: string[]) => {
    const nodes = [io('s', 'START'), ...ids.map((id) => nodeAt(id, 0, 0)), io('e', 'END')]
    const keys = ['s', ...ids, 'e']
    return { nodes, edges: keys.slice(1).map((id, i) => link(keys[i], id)) }
  }

  it('놓은 순서가 아니라 이어진 차례로 번호를 준다', () => {
    // c 를 먼저 놓았지만 선으로는 맨 끝이다.
    const { nodes, edges } = run('a', 'b', 'c')
    const at = chainPositions([nodes[3], ...nodes.slice(0, 3), nodes[4]], edges)

    expect(at?.get('a')).toBe(0)
    expect(at?.get('b')).toBe(1)
    expect(at?.get('c')).toBe(2)
  })

  // 어디서 시작해 어디서 끝나는지 정해지기 전에는 실행할 수 없다. 번호도 주지 않는다.
  it('시작·종료가 붙기 전에는 차례가 없다', () => {
    const nodes = chain('a', 'b')
    expect(chainPositions(nodes, [link('a', 'b')])).toBeNull()

    const { nodes: half, edges } = run('a')
    // 종료를 떼면 다시 없다.
    expect(chainPositions(half.slice(0, 2), edges.slice(0, 1))).toBeNull()
  })

  // 갈래가 있으면 차례라는 것이 없다. 지어내면 "이 순서로 실행된다" 로 잘못 읽힌다.
  it('갈라지면 차례가 없다', () => {
    const nodes = chain('a', 'b', 'c')
    expect(chainPositions(nodes, [link('a', 'b'), link('a', 'c')])).toBeNull()
  })

  it('따로 떨어져 있어도 차례가 없다', () => {
    expect(chainPositions(chain('a', 'b', 'c'), [link('a', 'b')])).toBeNull()
  })

  it('일감이 하나면 그것이 첫 번째다', () => {
    const { nodes, edges } = run('a')
    expect(chainPositions(nodes, edges)?.get('a')).toBe(0)
  })

  // 시작이 1 번을 가져가면 첫 일감이 2 번으로 밀린다.
  it('제어·입출력은 번호를 세지 않는다', () => {
    const { nodes, edges } = run('a', 'b')
    const at = chainPositions(nodes, edges)

    expect(at?.get('a')).toBe(0)
    expect(at?.get('b')).toBe(1)
    expect(at?.has('s')).toBe(false)
    expect(at?.has('e')).toBe(false)
  })
})

describe('CANVAS_NODES', () => {
  it('자리표시자 노드의 id 가 겹치지 않는다', () => {
    expect(new Set(CANVAS_NODES.map((n) => n.id)).size).toBe(CANVAS_NODES.length)
  })

  it('자리표시자가 서로 겹치지 않는다', () => {
    for (const node of CANVAS_NODES) {
      expect(apart(CANVAS_NODES, node.id, node.x, node.y)).toBe(true)
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
    expect(apart(nodes, 'b', moved.x, moved.y)).toBe(true)
  })

  it('완전히 포개도 방향을 찾아 뗀다', () => {
    const nodes = [nodeAt('a', 300, 400)]
    const moved = resolveOverlap(nodes, 'b', 300, 400)
    expect(apart(nodes, 'b', moved.x, moved.y)).toBe(true)
  })

  it('여러 개가 둘러싸도 빈자리를 찾는다', () => {
    const nodes = [nodeAt('a', 200, 300), nodeAt('b', 380, 300), nodeAt('c', 290, 480)]
    const moved = resolveOverlap(nodes, 'x', 295, 380)
    expect(apart(nodes, 'x', moved.x, moved.y)).toBe(true)
  })

  it('벽이 없어도 되풀이가 끝난다 — 겹침만 풀고 멈춘다', () => {
    const nodes = [nodeAt('a', 0, 0), nodeAt('b', 100, 0), nodeAt('c', 50, 100)]
    const moved = resolveOverlap(nodes, 'x', 50, 40)
    expect(Number.isFinite(moved.x) && Number.isFinite(moved.y)).toBe(true)
    expect(apart(nodes, 'x', moved.x, moved.y)).toBe(true)
  })

  it('맞닿지 않게 간격을 남긴다', () => {
    const nodes = [nodeAt('a', 300, 400)]
    const moved = resolveOverlap(nodes, 'b', 330, 400)
    expect(Math.hypot(moved.x - 300, moved.y - 400)).toBeGreaterThanOrEqual(MIN_APART - 0.5)
  })
})

describe('겹침은 놓을 때도 옮길 때도 막는다', () => {
  it('이미 있는 자리에 놓으면 밀려난다', () => {
    const base = [nodeAt('a', 300, 400)]
    const [, placed] = placeNode(base, seed, 305, 405)
    expect(apart(base, placed.id, placed.x, placed.y)).toBe(true)
  })

  it('다른 카드 위로 옮기면 밀려난다', () => {
    const base = [nodeAt('a', 300, 400), nodeAt('b', 0, 200)]
    // 화면이 밟는 순서 그대로 — 자리를 먼저 풀고, 푼 자리에 그대로 넣는다.
    const settled = resolveOverlap(base, 'b', 302, 402)
    const after = moveNodeTo(base, 'b', settled.x, settled.y)
    const b = after.find((n) => n.id === 'b')!
    expect(apart(after, 'b', b.x, b.y)).toBe(true)
  })
})

describe('패널 경계와 겹침이 함께 걸릴 때', () => {
  it('패널 앞에서 밀려도 다른 카드와 겹치지 않는다', () => {
    const nodes = [nodeAt('a', BOUNDS.maxX, 400)]
    const moved = resolveOverlap(nodes, 'x', 9999, 420, BOUNDS)
    expect(moved.x).toBeLessThanOrEqual(BOUNDS.maxX)
    expect(apart(nodes, 'x', moved.x, moved.y)).toBe(true)
  })

  it('패널 앞에 두 장이 있어도 서로 비킨다', () => {
    const nodes = [nodeAt('a', BOUNDS.maxX, 300), nodeAt('b', BOUNDS.maxX, 500)]
    const moved = resolveOverlap(nodes, 'x', 9999, 400, BOUNDS)
    expect(moved.x).toBeLessThanOrEqual(BOUNDS.maxX)
    expect(apart(nodes, 'x', moved.x, moved.y)).toBe(true)
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
    expect(portPoint({ x: 12, y: 400 }, 'out')).toEqual({ x: 180, y: 484 })
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
    for (const edge of edgeGeometry(CANVAS_NODES, CANVAS_EDGES)) {
      const from = CANVAS_NODES.find((n) => n.id === edge.from)!
      const to = CANVAS_NODES.find((n) => n.id === edge.to)!
      const 시작 = portPoint(from, 'out')
      const 끝 = portPoint(to, 'in')
      expect(edge.d.startsWith(`M${시작.x} ${시작.y}`)).toBe(true)
      expect(edge.d.endsWith(`${끝.x} ${끝.y}`)).toBe(true)
    }
  })

  it('노드를 옮기면 선이 따라온다', () => {
    const 전 = edgeGeometry(CANVAS_NODES, CANVAS_EDGES)[0].d
    const 후 = edgeGeometry(moveNodeTo(CANVAS_NODES, 'seed-1', 12, 700), CANVAS_EDGES)[0].d
    expect(후).not.toBe(전)
    expect(후.startsWith('M180 784')).toBe(true)
  })

  it('한쪽 노드가 없는 선은 버린다', () => {
    const 없앰 = CANVAS_NODES.filter((n) => n.id !== 'seed-1')
    expect(edgeGeometry(없앰, CANVAS_EDGES).map((e) => e.id)).toEqual([
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
  /** a → b → c. 넷째 d 와 다섯째 e 는 아직 아무 데도 붙어 있지 않다. */
  const 노드 = ['a', 'b', 'c', 'd', 'e'].map((id) => nodeAt(id, 0, 0))
  const 사슬 = [
    { id: 'a__b', from: 'a', to: 'b' },
    { id: 'b__c', from: 'b', to: 'c' },
  ]

  const 분기 = (id: string) => ({
    ...nodeAt(id, 0, 0),
    nodeType: null,
    sourceType: 'CONTROL' as const,
    sourceId: '',
    mine: null,
    controlType: 'BRANCH' as const,
  })
  const io = (id: string, ioType: 'START' | 'END') => ({
    ...nodeAt(id, 0, 0),
    nodeType: null,
    sourceType: 'IO' as const,
    sourceId: '',
    mine: null,
    ioType,
  })

  it('빈 입력이면 이을 수 있다', () => {
    expect(canConnect(노드, 사슬, 'c', 'd')).toBe(true)
  })

  it('입력이 찬 노드로도 잇는다 — 합류', () => {
    expect(canConnect(노드, 사슬, 'd', 'c')).toBe(true)
  })

  it('분기의 참·거짓을 한 노드로 보내지는 못한다', () => {
    const nodes = [분기('br'), nodeAt('d', 0, 0)]
    const 참 = [{ id: 'br__d', from: 'br', to: 'd', when: true }]
    expect(canConnect(nodes, 참, 'br', 'd', false)).toBe(false)
  })

  // 갈래를 내려면 분기 노드를 놓아야 한다. 일하는 노드는 어느 쪽으로 갈지 정할 곳이 없다.
  it('일하는 노드에서 두 갈래는 못 낸다', () => {
    expect(canConnect(노드, 사슬, 'a', 'd')).toBe(false)
  })

  it('자기 자신은 못 잇는다', () => {
    expect(canConnect(노드, 사슬, 'a', 'a')).toBe(false)
  })

  it('이미 있는 선은 다시 못 잇는다', () => {
    expect(canConnect(노드, 사슬, 'a', 'b')).toBe(false)
  })

  it('바로 되돌아가는 선은 막는다', () => {
    expect(canConnect(노드, 사슬, 'b', 'a')).toBe(false)
  })

  it('멀리 돌아 고리가 되는 선도 막는다', () => {
    expect(canConnect(노드, 사슬, 'c', 'a')).toBe(false)
  })

  it('분기는 참·거짓을 하나씩 낸다', () => {
    const nodes = [분기('br'), nodeAt('d', 0, 0), nodeAt('e', 0, 0), nodeAt('f', 0, 0)]
    const 참 = [{ id: 'br__d', from: 'br', to: 'd', when: true }]

    expect(canConnect(nodes, 참, 'br', 'e', false)).toBe(true)
    // 같은 갈래를 두 번 잇지는 못한다.
    expect(canConnect(nodes, 참, 'br', 'e', true)).toBe(false)
    // 갈래를 고르지 않은 선도 받지 않는다.
    expect(canConnect(nodes, 참, 'br', 'e')).toBe(false)
  })

  it('일하는 노드의 선에는 갈래를 붙일 수 없다', () => {
    expect(canConnect(노드, [], 'a', 'b', true)).toBe(false)
  })

  it('시작 앞과 종료 뒤로는 잇지 못한다', () => {
    const nodes = [io('s', 'START'), io('e', 'END'), nodeAt('a', 0, 0)]
    expect(canConnect(nodes, [], 'a', 's')).toBe(false)
    expect(canConnect(nodes, [], 'e', 'a')).toBe(false)
    expect(canConnect(nodes, [], 's', 'a')).toBe(true)
    expect(canConnect(nodes, [], 'a', 'e')).toBe(true)
  })

  it('캔버스에 없는 노드와는 잇지 않는다', () => {
    expect(canConnect(노드, 사슬, 'a', 'zzz')).toBe(false)
  })
})

describe('connect', () => {
  const 노드 = ['a', 'b', 'c'].map((id) => nodeAt(id, 0, 0))

  it('이을 수 있으면 끝에 붙인다', () => {
    const after = connect(노드, [], 'a', 'b')
    expect(after).toHaveLength(1)
    expect(after[0]).toMatchObject({ id: edgeId('a', 'b'), from: 'a', to: 'b' })
  })

  it('이을 수 없으면 목록이 그대로다', () => {
    const before = [{ id: 'a__b', from: 'a', to: 'b' }]
    expect(connect(노드, before, 'b', 'a')).toBe(before)
    expect(connect(노드, before, 'a', 'a')).toBe(before)
    expect(connect(노드, before, 'a', 'b')).toBe(before)
  })

  it('원래 목록을 건드리지 않는다', () => {
    const before = [{ id: 'a__b', from: 'a', to: 'b' }]
    connect(노드, before, 'b', 'c')
    expect(before).toHaveLength(1)
  })

  it('자리표시자에 이어 붙여도 고리가 생기지 않는다', () => {
    expect(canConnect(CANVAS_NODES, CANVAS_EDGES, 'seed-5', 'seed-1')).toBe(false)
    // seed-5 는 이미 seed-4 에게서 받고 있다
    expect(canConnect(CANVAS_NODES, CANVAS_EDGES, 'seed-1', 'seed-5')).toBe(false)
  })
})

describe('disconnect', () => {
  const edges = [
    { id: 'a__b', from: 'a', to: 'b' },
    { id: 'b__c', from: 'b', to: 'c' },
  ]

  it('선택한 선만 지운다', () => {
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
    expect(canConnect(after.nodes, after.edges, 'seed-1', 'seed-3')).toBe(true)
  })

  it('없는 id 면 둘 다 그대로다', () => {
    const after = removeNode(CANVAS_NODES, CANVAS_EDGES, 'zzz')
    expect(after.nodes).toBe(CANVAS_NODES)
    expect(after.edges).toBe(CANVAS_EDGES)
  })
})

describe('seedCanvasNodes', () => {
  const seeds = Array.from({ length: 8 }, (_, i) => ({
    id: `ag_${i}`,
    name: `에이전트 ${i}`,
    nodeType: 'AGENT' as const,
    sourceType: 'AGENT' as const,
    sourceId: `ag_${i}`,
    price: '10',
    summary: null,
    prompt: null,
    model: null,
    mine: false,
  }))

  it('선택한 순서대로 놓고 선은 잇지 않는다', () => {
    const nodes = seedCanvasNodes(seeds)

    expect(nodes.map((n) => n.id)).toEqual(seeds.map((s) => s.id))
    // 선은 여기서 만들지 않는다 — 이을 수 있는 짝인지는 스키마가 정한다.
    expect(edgeGeometry(nodes, [])).toEqual([])
  })

  it('어느 배치에서도 둘이 겹치지 않는다', () => {
    for (const layout of ['row', 'grid'] as const) {
      const nodes = seedCanvasNodes(seeds, layout)
      for (const a of nodes) {
        for (const b of nodes) {
          if (a.id === b.id) continue
          expect(Math.hypot(a.x - b.x, a.y - b.y)).toBeGreaterThanOrEqual(NODE_SIZE)
        }
      }
    }
  })

  it('가로로 한 줄 — 오른쪽으로만 가고 높이가 위아래로 엇갈린다', () => {
    // 왼쪽에서 오른쪽으로 잇는 것이 곧 실행 순서다. 높이는 두 칸을 번갈아 쓸 뿐이다.
    const nodes = seedCanvasNodes(seeds)

    expect(nodes.map((n) => n.x)).toEqual([...nodes.map((n) => n.x)].sort((a, b) => a - b))
    expect(new Set(nodes.map((n) => n.y)).size).toBe(2)
    expect(nodes[0].y).toBe(nodes[2].y)
    expect(nodes[0].y).not.toBe(nodes[1].y)
  })

  it('모아 보여줄 때는 작업면 안에 들어간다', () => {
    // 찾기 화면의 미리보기는 팬·줌이 없다. 패널 뒤로 넘어가면 선택한 것이 보이지 않는다.
    for (const node of seedCanvasNodes(seeds, 'grid')) {
      expect(node.x).toBeGreaterThanOrEqual(0)
      expect(node.x + NODE_SIZE).toBeLessThanOrEqual(980)
      expect(node.y + NODE_SIZE).toBeLessThanOrEqual(1024)
    }
  })

  it('등장 모션은 순서대로 조금씩 늦게 시작한다', () => {
    const delays = seedCanvasNodes(seeds).map((node) =>
      Number(node.enter?.match(/ ([\d.]+)s c/)?.[1]),
    )

    expect(delays).toEqual([...delays].sort((a, b) => a - b))
    expect(new Set(delays).size).toBe(seeds.length)
  })

  it('빈 목록이면 빈 캔버스다', () => {
    expect(seedCanvasNodes([])).toEqual([])
  })
})

describe('nodesBounds', () => {
  it('카드 크기까지 감싼다 — 좌표만 재면 오른쪽·아래가 잘린다', () => {
    const bounds = nodesBounds(CANVAS_NODES)!

    expect(bounds.minX).toBe(Math.min(...CANVAS_NODES.map((n) => n.x)))
    expect(bounds.maxX).toBe(Math.max(...CANVAS_NODES.map((n) => n.x)) + NODE_SIZE)
    expect(bounds.maxY).toBe(Math.max(...CANVAS_NODES.map((n) => n.y)) + NODE_SIZE)
  })

  it('빈 캔버스면 null — 맞출 것이 없다', () => {
    expect(nodesBounds([])).toBeNull()
  })
})

/** 선택한 것을 안고 편집 화면에 들어갔을 때 첫 화면에 다 보이는가. */
describe('한 줄 배치와 첫 배율', () => {
  const pick = (count: number) =>
    Array.from({ length: count }, (_, i) => ({
      id: `ag_${i}`,
      name: `에이전트 ${i}`,
      nodeType: 'AGENT' as const,
      sourceType: 'AGENT' as const,
      sourceId: `ag_${i}`,
      price: '10',
      summary: null,
      prompt: null,
      model: null,
      mine: false,
    }))

  const fitFor = (count: number, layout: 'row' | 'grid' = 'row') =>
    fitViewport(nodesBounds(seedCanvasNodes(pick(count), layout))!, {
      width: PANE_CONTENT_WIDTH,
      height: PANE_HEIGHT,
    })

  it('다섯 장까지는 화면을 건드리지 않는다', () => {
    for (const count of [1, 3, 5]) expect(fitFor(count).k).toBe(1)
  })

  it('여섯 장부터 저절로 줄어든다', () => {
    expect(fitFor(6).k).toBeLessThan(1)
    expect(fitFor(20).k).toBeLessThan(fitFor(6).k)
  })

  it('모아 보여줄 때도 마지막 줄이 작업면 아래로 넘지 않는다', () => {
    // 찾기 화면의 미리보기는 팬·줌이 없다. 넘치면 선택한 것이 잘려 보이지 않는다.
    for (const count of [12, 16, 20]) {
      const nodes = seedCanvasNodes(pick(count), 'grid')
      const view = fitFor(count, 'grid')
      const bottom = (Math.max(...nodes.map((n) => n.y)) + NODE_SIZE) * view.k + view.ty

      expect(bottom).toBeLessThanOrEqual(PANE_HEIGHT + 0.001)
    }
  })

  it('모든 장이 작업면 안에 있다', () => {
    for (const count of [6, 12, 20]) {
      const view = fitFor(count)

      for (const node of seedCanvasNodes(pick(count))) {
        expect(node.x * view.k + view.tx).toBeGreaterThanOrEqual(-0.001)
        expect((node.x + NODE_SIZE) * view.k + view.tx).toBeLessThanOrEqual(
          PANE_CONTENT_WIDTH + 0.001,
        )
        expect(node.y * view.k + view.ty).toBeGreaterThanOrEqual(-0.001)
        expect((node.y + NODE_SIZE) * view.k + view.ty).toBeLessThanOrEqual(PANE_HEIGHT + 0.001)
      }
    }
  })

  it('스물한 장부터는 축소가 바닥에 걸려 오른쪽이 넘어간다 — 그래서 고르는 쪽에서 자른다', () => {
    const view = fitFor(21)
    const nodes = seedCanvasNodes(pick(21))
    const right = (Math.max(...nodes.map((n) => n.x)) + NODE_SIZE) * view.k + view.tx

    expect(view.k).toBe(ZOOM_MIN)
    expect(right).toBeGreaterThan(PANE_CONTENT_WIDTH)
  })
})

describe('chainEdges', () => {
  const nodes = (count: number) =>
    seedCanvasNodes(
      Array.from({ length: count }, (_, i) => ({
        id: `ag_${i}`,
        name: `에이전트 ${i}`,
        nodeType: 'AGENT' as const,
        sourceType: 'AGENT' as const,
        sourceId: `ag_${i}`,
        price: '10',
        summary: null,
        prompt: null,
        model: null,
        mine: false,
      })),
    )

  it('놓인 순서대로 앞에서 뒤로 잇는다', () => {
    const edges = chainEdges(nodes(4))

    expect(edges.map((e) => [e.from, e.to])).toEqual([
      ['ag_0', 'ag_1'],
      ['ag_1', 'ag_2'],
      ['ag_2', 'ag_3'],
    ])
  })

  it('이은 것이 그래프 규칙을 지킨다 — 입력은 하나뿐이고 고리가 없다', () => {
    const list = nodes(5)
    const edges = chainEdges(list)

    // 들어오는 선이 둘인 노드가 없다.
    const incoming = edges.map((e) => e.to)
    expect(new Set(incoming).size).toBe(incoming.length)
    // 끝에서 처음으로 되돌아가는 선은 이을 수 없다.
    expect(canConnect(nodes(5), edges, 'ag_4', 'ag_0')).toBe(false)
  })

  it('한 장이거나 없으면 이을 곳이 없다', () => {
    expect(chainEdges(nodes(1))).toEqual([])
    expect(chainEdges([])).toEqual([])
  })

  it('그린 선이 양 끝을 모두 찾는다', () => {
    const list = nodes(3)

    expect(edgeGeometry(list, chainEdges(list))).toHaveLength(2)
  })
})
