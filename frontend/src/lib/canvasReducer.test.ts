import { describe, expect, it } from 'vitest'

import {
  canvasReducer,
  initialCanvasState,
  seededCanvasState,
  type CanvasAction,
  type CanvasState,
} from './canvasReducer'
import type { CanvasNodeData } from './canvasWorkflow'

// 구현에서 가져오지 않는다. 빌려 쓰면 구현이 틀릴 때 단언도 나란히 틀린다.
const NODE_SIZE = 168
const NODE_GAP = 24
/** 카드 두 장의 좌표가 이만큼은 떨어져 있어야 한다. */
const MIN_APART = NODE_SIZE + NODE_GAP

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

/** 자리표시자 대신 이 검사에서 쓸 것만 담은 캔버스. */
const stateOf = (over: Partial<CanvasState> = {}): CanvasState => ({
  ...initialCanvasState,
  nodes: [nodeAt('a', 0, 0), nodeAt('b', 400, 0)],
  edges: [],
  ...over,
})

/** 액션을 차례로 먹인다. 한 손짓이 여러 액션으로 갈리는 경우가 많다. */
const run = (state: CanvasState, ...actions: CanvasAction[]) => actions.reduce(canvasReducer, state)

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
const idsOf = (state: CanvasState) => state.nodes.map((n) => n.id)
const edgeIdsOf = (state: CanvasState) => state.edges.map((e) => e.id)

describe('선택', () => {
  it('카드를 누르면 그 자리에서 선택이 옮겨간다', () => {
    const after = canvasReducer(stateOf({ selId: 'b' }), { type: 'pressNode', id: 'a' })
    expect(after.selId).toBe('a')
  })

  it('카드를 누르면 선 선택이 풀린다 — 한 번에 하나만 선택된다', () => {
    const after = canvasReducer(stateOf({ selEdge: 'a__b' }), { type: 'pressNode', id: 'a' })
    expect(after.selEdge).toBeNull()
  })

  it('선을 고르면 노드 선택이 풀린다', () => {
    const after = canvasReducer(stateOf({ selId: 'a' }), { type: 'selectEdge', id: 'a__b' })
    expect(after.selId).toBeNull()
    expect(after.selEdge).toBe('a__b')
  })

  it('같은 선을 다시 고르면 선택이 풀린다', () => {
    const after = run(
      stateOf(),
      { type: 'selectEdge', id: 'a__b' },
      { type: 'selectEdge', id: 'a__b' },
    )
    expect(after.selEdge).toBeNull()
  })

  it('빈 곳을 누르면 둘 다 풀린다', () => {
    const after = canvasReducer(stateOf({ selId: 'a', selEdge: 'a__b' }), {
      type: 'clearSelection',
    })
    expect(after.selId).toBeNull()
    expect(after.selEdge).toBeNull()
  })
})

describe('드래그', () => {
  it('누르면 그 카드를 끌기 시작한다', () => {
    const after = canvasReducer(stateOf(), { type: 'pressNode', id: 'a' })
    expect(after.dragId).toBe('a')
  })

  it('자리를 확정하면 좌표가 옮겨가고 끌기가 끝난다', () => {
    const after = run(
      stateOf(),
      { type: 'pressNode', id: 'a' },
      { type: 'settleNode', id: 'a', x: 300, y: 500 },
    )
    expect(after.nodes[0]).toMatchObject({ id: 'a', x: 300, y: 500 })
    expect(after.dragId).toBeNull()
  })

  it('확정하는 자리는 그대로 쓴다 — 겹침은 부르는 쪽에서 이미 풀었다', () => {
    const after = canvasReducer(stateOf(), { type: 'settleNode', id: 'b', x: 10, y: 10 })
    expect(after.nodes[1]).toMatchObject({ id: 'b', x: 10, y: 10 })
  })

  it('끌다 말면 좌표는 그대로다', () => {
    const after = run(stateOf(), { type: 'pressNode', id: 'a' }, { type: 'endDrag' })
    expect(after.dragId).toBeNull()
    expect(after.nodes[0]).toMatchObject({ x: 0, y: 0 })
  })

  it('미끄러지는 중에 지운 노드가 되살아나지 않는다', () => {
    const after = run(
      stateOf(),
      { type: 'pressNode', id: 'a' },
      // 손을 뗀 뒤 미끄러짐이 끝나기 전에 Delete 를 누른 경우
      { type: 'removeSelected' },
      { type: 'settleNode', id: 'a', x: 300, y: 500 },
    )
    expect(idsOf(after)).toEqual(['b'])
  })
})

describe('팔레트에서 놓기', () => {
  it('목록 끝에 붙는다', () => {
    const after = canvasReducer(stateOf(), { type: 'placeNode', seed, x: 700, y: 400 })
    expect(idsOf(after)).toEqual(['a', 'b', 'p1'])
    expect(after.nodes[2]).toMatchObject({ name: '번역', x: 700, y: 400 })
  })

  it('이미 찬 자리에 놓으면 밀려난다', () => {
    const after = canvasReducer(stateOf(), { type: 'placeNode', seed, x: 5, y: 5 })
    const placed = after.nodes[2]
    expect(Math.hypot(placed.x - 0, placed.y - 0)).toBeGreaterThanOrEqual(MIN_APART - 0.5)
  })

  it('범위를 주면 그 안에만 놓인다', () => {
    const after = canvasReducer(stateOf(), {
      type: 'placeNode',
      seed,
      x: 9999,
      y: 9999,
      bounds: { minX: 0, maxX: 800, minY: 0, maxY: 600 },
    })
    const placed = after.nodes[2]
    expect(placed.x).toBeLessThanOrEqual(800)
    expect(placed.y).toBeLessThanOrEqual(600)
  })

  it('놓아도 선택은 옮겨가지 않는다 — 놓은 카드를 바로 지우면 안 된다', () => {
    const after = canvasReducer(stateOf({ selId: 'a' }), { type: 'placeNode', seed, x: 700, y: 0 })
    expect(after.selId).toBe('a')
  })
})

describe('분기 조건', () => {
  const branchState = (condition: CanvasState['nodes'][number]['condition'] = null) =>
    stateOf({
      nodes: [
        {
          ...stateOf({}).nodes[0],
          id: 'br',
          sourceType: 'CONTROL',
          controlType: 'BRANCH',
          condition,
        },
      ],
    })

  it('고른 분기의 조건을 갈아 끼운다', () => {
    const next = { fromId: 'a', path: 'level', operator: 'EQ' as const, right: 'high' }
    const after = canvasReducer(branchState(), { type: 'editCondition', id: 'br', condition: next })
    expect(after.nodes[0].condition).toEqual(next)
  })

  // 일하는 노드에는 조건 자리가 없다. 잘못 들어가면 저장 페이로드에 낄 곳이 없다.
  it('분기가 아니면 건드리지 않는다', () => {
    const before = stateOf({})
    const after = canvasReducer(before, {
      type: 'editCondition',
      id: before.nodes[0].id,
      condition: { fromId: 'a', path: 'level', operator: 'EQ', right: 'high' },
    })
    expect(after.nodes[0].condition).toBeUndefined()
  })
})

describe('포트 연결', () => {
  const linking = stateOf({ linkFrom: { id: 'a', side: 'out' } })

  it('출력에서 끌어 입력에 놓으면 이어진다', () => {
    const after = canvasReducer(linking, { type: 'endLink', targetId: 'b', port: { side: 'in' } })
    expect(edgeIdsOf(after)).toEqual(['a__b'])
    expect(after.linkFrom).toBeNull()
  })

  it('입력에서 끌어 출력에 놓아도 방향은 출력 → 입력이다', () => {
    const after = canvasReducer(stateOf({ linkFrom: { id: 'b', side: 'in' } }), {
      type: 'endLink',
      targetId: 'a',
      port: { side: 'out' },
    })
    expect(after.edges[0]).toMatchObject({ from: 'a', to: 'b' })
  })

  it('같은 쪽 포트끼리는 잇지 않는다', () => {
    const after = canvasReducer(linking, { type: 'endLink', targetId: 'b', port: { side: 'out' } })
    expect(after.edges).toHaveLength(0)
    expect(after.linkFrom).toBeNull()
  })

  it('고리가 되는 짝은 잇지 않는다', () => {
    const after = canvasReducer(
      stateOf({ edges: [{ id: 'a__b', from: 'a', to: 'b' }], linkFrom: { id: 'b', side: 'out' } }),
      { type: 'endLink', targetId: 'a', port: { side: 'in' } },
    )
    expect(edgeIdsOf(after)).toEqual(['a__b'])
  })

  it('입력이 찬 노드로도 잇는다 — 합류', () => {
    const after = canvasReducer(
      stateOf({
        nodes: [nodeAt('a', 0, 0), nodeAt('b', 400, 0), nodeAt('c', 800, 0)],
        edges: [{ id: 'a__b', from: 'a', to: 'b' }],
        linkFrom: { id: 'c', side: 'out' },
      }),
      { type: 'endLink', targetId: 'b', port: { side: 'in' } },
    )
    expect(edgeIdsOf(after)).toEqual(['a__b', 'c__b'])
  })

  it('포트가 아닌 곳에서 손을 떼면 끌던 선만 사라진다', () => {
    const after = canvasReducer(linking, { type: 'cancelLink' })
    expect(after.linkFrom).toBeNull()
    expect(after.edges).toHaveLength(0)
  })

  it('끌기 시작하면 출발점을 기억한다', () => {
    const after = canvasReducer(stateOf(), { type: 'startLink', from: { id: 'a', side: 'out' } })
    expect(after.linkFrom).toEqual({ id: 'a', side: 'out' })
  })

  it('끄는 도중 출발 노드를 지우면 끌던 선도 함께 사라진다', () => {
    const after = run(
      stateOf({ selId: 'a' }),
      { type: 'startLink', from: { id: 'a', side: 'out' } },
      { type: 'removeSelected' },
    )
    expect(after.linkFrom).toBeNull()
  })

  it('없는 노드에서 나가는 선은 잇지 않는다', () => {
    // 이어 두면 화면에 그려지지 않아 지울 수 없는데, 입력 하나 규칙에는 잡혀 b 가 영영 막힌다.
    const after = run(
      stateOf({ selId: 'a', linkFrom: { id: 'a', side: 'out' } }),
      { type: 'removeSelected' },
      { type: 'endLink', targetId: 'b', port: { side: 'in' } },
    )
    expect(after.edges).toHaveLength(0)
  })

  it('지운 노드를 거쳐도 남은 노드는 계속 선을 받는다', () => {
    const after = run(
      stateOf({ nodes: [nodeAt('a', 0, 0), nodeAt('b', 400, 0), nodeAt('c', 0, 400)], selId: 'a' }),
      { type: 'startLink', from: { id: 'a', side: 'out' } },
      { type: 'removeSelected' },
      { type: 'endLink', targetId: 'b', port: { side: 'in' } },
      { type: 'startLink', from: { id: 'c', side: 'out' } },
      { type: 'endLink', targetId: 'b', port: { side: 'in' } },
    )
    expect(edgeIdsOf(after)).toEqual(['c__b'])
  })
})

describe('삭제', () => {
  const linked = stateOf({ edges: [{ id: 'a__b', from: 'a', to: 'b' }] })

  it('노드를 지우면 거기 붙은 선도 함께 사라진다', () => {
    const after = canvasReducer({ ...linked, selId: 'a' }, { type: 'removeSelected' })
    expect(idsOf(after)).toEqual(['b'])
    expect(after.edges).toHaveLength(0)
  })

  it('노드를 지우면 선택과 호버도 함께 풀린다', () => {
    const after = canvasReducer({ ...linked, selId: 'a', hovId: 'a' }, { type: 'removeSelected' })
    expect(after.selId).toBeNull()
    expect(after.hovId).toBeNull()
  })

  it('선이 선택돼 있으면 선만 지운다', () => {
    const after = canvasReducer({ ...linked, selEdge: 'a__b' }, { type: 'removeSelected' })
    expect(idsOf(after)).toEqual(['a', 'b'])
    expect(after.edges).toHaveLength(0)
    expect(after.selEdge).toBeNull()
  })

  it('선 선택이 노드 선택보다 앞선다', () => {
    const after = canvasReducer(
      { ...linked, selId: 'a', selEdge: 'a__b' },
      { type: 'removeSelected' },
    )
    expect(idsOf(after)).toEqual(['a', 'b'])
  })

  it('고른 것이 없으면 아무것도 바뀌지 않는다', () => {
    const before = linked
    expect(canvasReducer(before, { type: 'removeSelected' })).toBe(before)
  })

  it('없는 노드가 선택돼 있어도 목록은 그대로다', () => {
    const before = stateOf({ selId: 'zzz' })
    expect(canvasReducer(before, { type: 'removeSelected' })).toBe(before)
  })
})

describe('원본을 건드리지 않는다', () => {
  it('액션을 먹여도 앞 상태의 목록은 그대로다', () => {
    const before = stateOf()
    const nodes = before.nodes
    const edges = before.edges
    run(
      before,
      { type: 'placeNode', seed, x: 700, y: 400 },
      { type: 'startLink', from: { id: 'a', side: 'out' } },
      { type: 'endLink', targetId: 'b', port: { side: 'in' } },
    )
    expect(before.nodes).toBe(nodes)
    expect(before.edges).toBe(edges)
    expect(nodes).toHaveLength(2)
    expect(edges).toHaveLength(0)
  })
})

describe('seededCanvasState', () => {
  const seeds = ['ag_1', 'ag_2', 'ag_3'].map((id, i) => ({
    id,
    name: `에이전트 ${i}`,
    nodeType: 'AGENT' as const,
    sourceType: 'AGENT' as const,
    sourceId: id,
    price: '10',
    summary: null,
    prompt: null,
    model: null,
    mine: false,
  }))

  it('선택한 것만 올리고 선택한 순서대로 잇는다', () => {
    const state = seededCanvasState(seeds)

    expect(state.nodes.map((n) => n.id)).toEqual(['ag_1', 'ag_2', 'ag_3'])
    expect(state.edges.map((e) => [e.from, e.to])).toEqual([
      ['ag_1', 'ag_2'],
      ['ag_2', 'ag_3'],
    ])
  })

  it('자리표시자가 섞이지 않는다', () => {
    // 선택한 적 없는 데모 노드가 남으면 무엇을 선택했는지 알 수 없다.
    const state = seededCanvasState(seeds)

    expect(state.nodes.some((n) => n.id.startsWith('seed-'))).toBe(false)
  })

  it('선택한 것이 없으면 자리표시자로 시작한다', () => {
    expect(seededCanvasState([])).toBe(initialCanvasState)
    expect(seededCanvasState()).toBe(initialCanvasState)
  })
})
