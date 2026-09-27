import { describe, expect, it } from 'vitest'

import { CANVAS_EDGES, CANVAS_NODES, type CanvasEdge, type CanvasNodeData } from './canvasWorkflow'
import type { WorkflowDetail } from '@/api/workflows'

import { toCanvasWorkflow, toWorkflowGraph } from './workflowPayload'

const node = (id: string, over: Partial<CanvasNodeData> = {}): CanvasNodeData => ({
  id,
  name: id,
  nodeType: 'LLM',
  sourceType: 'NODE',
  sourceId: `src-${id}`,
  price: '1',
  summary: null,
  prompt: null,
  model: null,
  x: 0,
  y: 0,
  mine: true,
  floaty: '',
  ...over,
})

const link = (from: string, to: string): CanvasEdge => ({ id: `${from}__${to}`, from, to })

const placed = (
  nodeKey: string,
  over: Partial<WorkflowDetail['nodes'][number]> = {},
): WorkflowDetail['nodes'][number] => ({
  nodeKey,
  sourceType: 'NODE',
  sourceId: null,
  versionId: null,
  inputSource: 'PREV',
  name: null,
  mine: null,
  controlType: null,
  condition: null,
  ioType: null,
  fields: [],
  resultShape: null,
  resultFormat: null,
  ...over,
})

const branchLink = (from: string, to: string, when: boolean): CanvasEdge => ({
  ...link(from, to),
  when,
})

const io = (id: string, ioType: 'START' | 'END'): CanvasNodeData =>
  node(id, { nodeType: null, sourceType: 'IO', sourceId: '', mine: null, ioType })

const branch = (id: string, fromId: string): CanvasNodeData =>
  node(id, {
    nodeType: null,
    sourceType: 'CONTROL',
    sourceId: '',
    mine: null,
    controlType: 'BRANCH',
    condition: { fromId, path: 'level', operator: 'EQ', right: 'high' },
  })

describe('toWorkflowGraph — 분기·입출력', () => {
  /** 시작 → a → 분기 →(참) t / (거짓) f. 한 갈래 끝에만 종료가 붙는다. */
  const scene = () => ({
    nodes: [io('s', 'START'), node('a'), branch('br', 'a'), node('t'), io('e', 'END'), node('f')],
    edges: [
      link('s', 'a'),
      link('a', 'br'),
      branchLink('br', 't', true),
      branchLink('br', 'f', false),
      link('t', 'e'),
    ],
  })

  it('제어·입출력은 원본 대신 종류를 싣는다', () => {
    const { nodes, edges } = scene()
    const graph = toWorkflowGraph(nodes, edges)
    const byKey = new Map(graph!.nodes.map((placed) => [placed.nodeKey, placed]))
    const keyOf = (id: string) =>
      Object.entries(graph!.nodeIdByKey).find(([, value]) => value === id)![0]

    expect(byKey.get(keyOf('s'))).toEqual({
      nodeKey: keyOf('s'),
      sourceType: 'IO',
      ioType: 'START',
    })
    // 끝 노드는 결과 구조·형식이 둘 다 있어야 서버가 받는다.
    expect(byKey.get(keyOf('e'))).toEqual({
      nodeKey: keyOf('e'),
      sourceType: 'IO',
      ioType: 'END',
      resultShape: 'DOCUMENT',
      resultFormat: 'PDF',
    })
    expect(byKey.get(keyOf('br'))).toMatchObject({ sourceType: 'CONTROL', controlType: 'BRANCH' })
  })

  // 노드 키는 저장할 때 매겨진다. 캔버스 id 를 그대로 보내면 서버가 못 찾는다.
  it('조건의 left 를 노드 키로 옮긴다', () => {
    const { nodes, edges } = scene()
    const graph = toWorkflowGraph(nodes, edges)
    const keyOf = (id: string) =>
      Object.entries(graph!.nodeIdByKey).find(([, value]) => value === id)![0]
    const placed = graph!.nodes.find((it) => it.controlType === 'BRANCH')

    expect(placed?.condition).toEqual({
      left: `${keyOf('a')}.level`,
      operator: 'EQ',
      right: 'high',
    })
  })

  it('분기에서 나가는 선에만 when 이 붙는다', () => {
    const { nodes, edges } = scene()
    const graph = toWorkflowGraph(nodes, edges)
    const withWhen = graph!.edges.filter((edge) => edge.when !== undefined)

    expect(withWhen).toHaveLength(2)
    expect(withWhen.map((edge) => edge.when).sort()).toEqual([false, true])
  })

  // 서버는 참 갈래를 먼저 끝까지 간다. 순서가 다르면 다시 불러올 때 자리가 흔들린다.
  it('참 갈래를 먼저 훑는다', () => {
    const { nodes, edges } = scene()
    const graph = toWorkflowGraph(nodes, edges)
    const order = graph!.nodes.map((placed) => graph!.nodeIdByKey[placed.nodeKey])

    expect(order).toEqual(['s', 'a', 'br', 't', 'e', 'f'])
  })

  // 노드가 제 값을 들고 있으면 그것을 보낸다. 기본값이 덮어쓰면 다시 저장할 때 값이 바뀐다.
  it('끝 노드가 든 결과 형식을 그대로 싣는다', () => {
    const { nodes, edges } = scene()
    const kept = nodes.map((it) =>
      it.id === 'e' ? { ...it, resultShape: 'TABLE' as const, resultFormat: 'CSV' as const } : it,
    )
    const graph = toWorkflowGraph(kept, edges)
    const end = graph!.nodes.find((it) => it.ioType === 'END')

    expect(end).toMatchObject({ resultShape: 'TABLE', resultFormat: 'CSV' })
  })

  // 고르는 화면이 없어도 불러온 입력란은 다시 저장할 때 잃지 않는다.
  it('시작 노드의 입력란을 그대로 싣는다', () => {
    const fields = [
      { key: 'brand', label: '브랜드', type: 'TEXT' as const, required: true, choices: [] },
    ]
    const { nodes, edges } = scene()
    nodes[0] = { ...nodes[0], fields }
    const graph = toWorkflowGraph(nodes, edges)

    expect(graph?.nodes[0]).toMatchObject({ ioType: 'START', fields })
  })

  it('시작 노드에는 결과 형식을 싣지 않는다', () => {
    const { nodes, edges } = scene()
    const start = toWorkflowGraph(nodes, edges)!.nodes.find((it) => it.ioType === 'START')

    expect(start).not.toHaveProperty('resultShape')
    expect(start).not.toHaveProperty('resultFormat')
  })

  it('조건이 없는 분기는 저장할 수 없다', () => {
    const { nodes, edges } = scene()
    const noCondition = nodes.map((it) => (it.id === 'br' ? { ...it, condition: null } : it))
    expect(toWorkflowGraph(noCondition, edges)).toBeNull()
  })
})

describe('toWorkflowGraph', () => {
  it('선을 따라간 순서로 키를 매긴다', () => {
    const graph = toWorkflowGraph(
      [node('a'), node('b'), node('c')],
      [link('a', 'b'), link('b', 'c')],
    )

    expect(graph?.nodes.map((n) => n.nodeKey)).toEqual(['n1', 'n2', 'n3'])
    expect(graph?.edges).toEqual([
      { edgeKey: 'e1', sourceNodeKey: 'n1', targetNodeKey: 'n2' },
      { edgeKey: 'e2', sourceNodeKey: 'n2', targetNodeKey: 'n3' },
    ])
  })

  // 배열 순서로 키를 매기면 선과 어긋나 서버가 엉뚱한 순서로 실행한다.
  it('놓인 순서가 아니라 이어진 순서를 따른다', () => {
    const graph = toWorkflowGraph(
      [node('c'), node('a'), node('b')],
      [link('a', 'b'), link('b', 'c')],
    )

    expect(graph?.nodes.map((n) => n.sourceId)).toEqual(['src-a', 'src-b', 'src-c'])
  })

  it('서버가 아는 원본을 그대로 싣는다 — 에이전트도 섞인다', () => {
    const graph = toWorkflowGraph(
      [node('a'), node('b', { sourceType: 'AGENT', sourceId: 'agt_001', nodeType: 'AGENT' })],
      [link('a', 'b')],
    )

    expect(graph?.nodes).toEqual([
      { nodeKey: 'n1', sourceType: 'NODE', sourceId: 'src-a' },
      { nodeKey: 'n2', sourceType: 'AGENT', sourceId: 'agt_001' },
    ])
  })

  it('새로 만들 노드는 출력 형식을 함께 싣는다', () => {
    const output = { kind: 'JSON' as const, example: { level: 'high' } }
    const graph = toWorkflowGraph(
      [node('a', { nodeType: 'LLM', sourceId: '', prompt: '분류한다', output })],
      [],
    )

    expect(graph?.nodes[0]).toMatchObject({ nodeType: 'LLM', prompt: '분류한다', output })
  })

  it('노드가 하나면 선은 빈 배열이다', () => {
    const graph = toWorkflowGraph([node('a')], [])

    expect(graph?.nodes).toHaveLength(1)
    expect(graph?.edges).toEqual([])
  })

  it('한 줄이 아니면 만들지 않는다', () => {
    const branched = toWorkflowGraph(
      [node('a'), node('b'), node('c')],
      [link('a', 'b'), link('a', 'c')],
    )
    expect(branched).toBeNull()
    expect(toWorkflowGraph([], [])).toBeNull()
  })

  // 캔버스 id 를 그대로 쓰면 UUID 두 개가 붙은 선 키가 73자가 되어 서버가 거절한다.
  it('원본 id 가 길어도 키는 64자를 넘지 않는다', () => {
    const uuid = (n: number) => `9f1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c${String(n).padStart(2, '0')}`
    const graph = toWorkflowGraph([node(uuid(1)), node(uuid(2))], [link(uuid(1), uuid(2))])

    const keys = [
      ...(graph?.nodes.map((n) => n.nodeKey) ?? []),
      ...(graph?.edges.flatMap((e) => [e.edgeKey, e.sourceNodeKey, e.targetNodeKey]) ?? []),
    ]
    expect(keys).not.toHaveLength(0)
    for (const key of keys) expect(key.length).toBeLessThanOrEqual(64)
  })

  it('자리표시자 워크플로우를 그대로 보낼 수 있다', () => {
    const graph = toWorkflowGraph(CANVAS_NODES, CANVAS_EDGES)

    expect(graph?.nodes.map((n) => n.sourceId)).toEqual([
      'node_001',
      'node_002',
      'node_003',
      'node_004',
      'node_005',
    ])
    expect(graph?.edges).toHaveLength(4)
  })
})

describe('toCanvasWorkflow — 분기·입출력', () => {
  const detail: WorkflowDetail = {
    workflowId: 'wf-1',
    name: '갈래',
    description: null,
    revision: 1,
    lastExecutedAt: null,
    updatedAt: '2026-09-26T00:00:00Z',
    nodes: [
      placed('n1', { sourceType: 'IO', sourceId: null, ioType: 'START' }),
      placed('n2', { sourceId: 'node_001' }),
      placed('n3', {
        sourceType: 'CONTROL',
        sourceId: null,
        controlType: 'BRANCH',
        condition: { left: 'n2.level', operator: 'EQ', right: 'high' },
      }),
      placed('n4', { sourceId: 'node_002' }),
      placed('n5', { sourceType: 'IO', sourceId: null, ioType: 'END' }),
    ],
    edges: [
      { edgeKey: 'e1', sourceNodeKey: 'n1', targetNodeKey: 'n2' },
      { edgeKey: 'e2', sourceNodeKey: 'n2', targetNodeKey: 'n3' },
      { edgeKey: 'e3', sourceNodeKey: 'n3', targetNodeKey: 'n4', when: true },
      { edgeKey: 'e4', sourceNodeKey: 'n3', targetNodeKey: 'n5', when: false },
    ],
  }
  const canvas = () => toCanvasWorkflow(detail, () => undefined)

  it('종류를 그대로 되살린다', () => {
    const { nodes } = canvas()
    expect(nodes.map((it) => it.ioType ?? it.controlType ?? '-')).toEqual([
      'START',
      '-',
      'BRANCH',
      '-',
      'END',
    ])
  })

  // 팔레트에 없어 이름을 못 찾는다. 「알 수 없는 노드」로 굳으면 무엇인지 알 수 없다.
  it('제어·입출력은 종류로 이름을 채운다', () => {
    expect(canvas().nodes.map((it) => it.name)).toEqual([
      '시작',
      '알 수 없는 노드',
      '조건 분기',
      '알 수 없는 노드',
      '종료',
    ])
  })

  it('조건의 노드 키를 캔버스 id 로 되돌린다', () => {
    const { nodes } = canvas()
    const branchNode = nodes.find((it) => it.controlType === 'BRANCH')
    const source = nodes[1]

    expect(branchNode?.condition).toEqual({
      fromId: source.id,
      path: 'level',
      operator: 'EQ',
      right: 'high',
    })
  })

  it('선의 갈래를 그대로 싣는다', () => {
    expect(canvas().edges.map((edge) => edge.when)).toEqual([undefined, undefined, true, false])
  })

  // 갈래가 생기면 한 줄이 아니다. 깊이로 가로, 형제로 세로를 가른다.
  // 합류는 두 갈래 중 더 깊은 쪽 다음 칸에 둬야 선이 뒤로 꺾이지 않는다.
  it('합류는 가장 깊은 부모 다음 칸에 놓는다', () => {
    const joined: WorkflowDetail = {
      ...detail,
      nodes: [...detail.nodes, placed('n6', { sourceId: 'node_003' })],
      edges: [
        ...detail.edges.slice(0, 3),
        { edgeKey: 'e4', sourceNodeKey: 'n3', targetNodeKey: 'n6', when: false },
        { edgeKey: 'e5', sourceNodeKey: 'n4', targetNodeKey: 'n5' },
        { edgeKey: 'e6', sourceNodeKey: 'n6', targetNodeKey: 'n5' },
      ],
    }
    const [, , br, t, end] = toCanvasWorkflow(joined, () => undefined).nodes

    expect(end.x).toBeGreaterThan(t.x)
    expect(t.x).toBeGreaterThan(br.x)
  })

  it('참 갈래는 분기 위로, 거짓 갈래는 아래로 뻗는다', () => {
    const [, , br, yes, no] = canvas().nodes

    expect(yes.y).toBeLessThan(br.y)
    expect(no.y).toBeGreaterThan(br.y)
  })

  it('갈라진 자리는 같은 가로줄에 겹치지 않는다', () => {
    const { nodes } = canvas()
    const [yes, no] = [nodes[3], nodes[4]]

    expect(yes.x).toBe(no.x)
    expect(yes.y).not.toBe(no.y)
  })
})

describe('toCanvasWorkflow', () => {
  const detail = (nodeCount: number): WorkflowDetail => ({
    workflowId: 'wf-1',
    name: '문서 정리',
    description: null,
    revision: 3,
    nodes: Array.from({ length: nodeCount }, (_, i) => ({
      nodeKey: `n${i + 1}`,
      sourceType: 'NODE' as const,
      sourceId: `node_00${i + 1}`,
      versionId: null,
      inputSource: 'PREV' as const,
      name: null,
      mine: null,
      controlType: null,
      condition: null,
      ioType: null,
      fields: [],
      resultShape: null,
      resultFormat: null,
    })),
    edges: Array.from({ length: Math.max(0, nodeCount - 1) }, (_, i) => ({
      edgeKey: `e${i + 1}`,
      sourceNodeKey: `n${i + 1}`,
      targetNodeKey: `n${i + 2}`,
    })),
    lastExecutedAt: null,
    updatedAt: '2026-09-23T00:00:00Z',
  })

  const known = (sourceId: string) =>
    sourceId === 'node_001'
      ? {
          name: '파일 수집',
          nodeType: 'LLM' as const,
          price: '4',
          summary: '폴더를 훑어 새 파일을 집어 온다',
          prompt: null,
          model: null,
          mine: true,
        }
      : undefined

  it('순서대로 왼쪽부터 같은 보폭으로 늘어놓는다', () => {
    const canvas = toCanvasWorkflow(detail(3), known)

    expect(canvas.nodes.map((n) => n.sourceId)).toEqual(['node_001', 'node_002', 'node_003'])
    const xs = canvas.nodes.map((n) => n.x)
    expect(xs[1] - xs[0]).toBe(xs[2] - xs[1])
    expect(xs[0]).toBeLessThan(xs[1])
  })

  // 곧게 늘어놓으면 카드 사이 선이 짧은 수평선이라 어디로 가는지 보이지 않는다.
  it('위아래로 엇갈리게 놓아 선이 드러나게 한다', () => {
    const ys = toCanvasWorkflow(detail(4), known).nodes.map((n) => n.y)

    expect(new Set(ys).size).toBe(2)
    expect(ys[0]).not.toBe(ys[1])
    // 한 칸 건너뛰면 같은 높이로 돌아온다.
    expect(ys[2]).toBe(ys[0])
    expect(ys[3]).toBe(ys[1])
  })

  it('선은 새 id 로 다시 잇는다', () => {
    const canvas = toCanvasWorkflow(detail(3), known)
    const ids = canvas.nodes.map((n) => n.id)

    expect(canvas.edges).toEqual([
      { id: `${ids[0]}__${ids[1]}`, from: ids[0], to: ids[1] },
      { id: `${ids[1]}__${ids[2]}`, from: ids[1], to: ids[2] },
    ])
  })

  it('팔레트가 아는 노드는 이름과 종류와 단가를 채운다', () => {
    const [first] = toCanvasWorkflow(detail(2), known).nodes

    expect(first).toMatchObject({ name: '파일 수집', nodeType: 'LLM', price: '4', mine: true })
  })

  // 카드를 빼 버리면 저장된 구성이 조용히 바뀐다. 자리는 남기고 이름만 비운다.
  it('모르는 노드도 자리를 남기고 남의 것으로 둔다', () => {
    const nodes = toCanvasWorkflow(detail(2), known).nodes

    expect(nodes).toHaveLength(2)
    expect(nodes[1]).toMatchObject({ name: '알 수 없는 노드', mine: false })
    expect(nodes[1].sourceId).toBe('node_002')
    // 0 으로 채우면 카드가 공짜로 보여 견적과 어긋난다.
    expect(nodes[1].price).toBeNull()
  })

  it('이름과 판매 판정은 서버 값이 먼저다', () => {
    const served = detail(1)
    served.nodes[0] = { ...served.nodes[0], name: '서버 이름', mine: false }
    const [first] = toCanvasWorkflow(served, known).nodes

    expect(first).toMatchObject({ name: '서버 이름', mine: false, price: '4' })
  })

  it('되돌린 캔버스를 그대로 다시 보낼 수 있다', () => {
    const canvas = toCanvasWorkflow(detail(3), known)
    const graph = toWorkflowGraph(canvas.nodes, canvas.edges)

    expect(graph?.nodes.map((n) => n.sourceId)).toEqual(['node_001', 'node_002', 'node_003'])
    expect(graph?.edges).toHaveLength(2)
  })

  it('노드 하나면 선이 없다', () => {
    const canvas = toCanvasWorkflow(detail(1), known)

    expect(canvas.nodes).toHaveLength(1)
    expect(canvas.edges).toEqual([])
  })
})
