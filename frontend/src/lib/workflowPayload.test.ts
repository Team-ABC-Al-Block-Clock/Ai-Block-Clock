import { describe, expect, it } from 'vitest'

import { CANVAS_EDGES, CANVAS_NODES, type CanvasEdge, type CanvasNodeData } from './canvasWorkflow'
import type { WorkflowDetail } from '@/api/workflows'

import { toCanvasWorkflow, toWorkflowGraph } from './workflowPayload'

const node = (id: string, over: Partial<CanvasNodeData> = {}): CanvasNodeData => ({
  id,
  name: id,
  nodeType: 'TOOL',
  sourceType: 'NODE',
  sourceId: `src-${id}`,
  x: 0,
  y: 0,
  mine: true,
  floaty: '',
  ...over,
})

const link = (from: string, to: string): CanvasEdge => ({ id: `${from}__${to}`, from, to })

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
      ? { name: '파일 수집', nodeType: 'TRIGGER' as const, mine: true }
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

  it('팔레트가 아는 노드는 이름과 종류를 채운다', () => {
    const [first] = toCanvasWorkflow(detail(2), known).nodes

    expect(first).toMatchObject({ name: '파일 수집', nodeType: 'TRIGGER', mine: true })
  })

  // 카드를 빼 버리면 저장된 구성이 조용히 바뀐다. 자리는 남기고 이름만 비운다.
  it('모르는 노드도 자리를 남기고 남의 것으로 둔다', () => {
    const nodes = toCanvasWorkflow(detail(2), known).nodes

    expect(nodes).toHaveLength(2)
    expect(nodes[1]).toMatchObject({ name: '알 수 없는 노드', mine: false })
    expect(nodes[1].sourceId).toBe('node_002')
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
