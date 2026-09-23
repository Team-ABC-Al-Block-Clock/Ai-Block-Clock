import { describe, expect, it } from 'vitest'

import { CANVAS_EDGES, CANVAS_NODES, type CanvasEdge, type CanvasNodeData } from './canvasWorkflow'
import { toWorkflowGraph } from './workflowPayload'

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
