import { describe, expect, it } from 'vitest'

import { canvasReducer, initialCanvasState } from '@/lib/canvas/canvasReducer'
import { toWorkflowGraph } from '@/lib/canvas/workflowPayload'
import { CANVAS_EDGES, CANVAS_NODES } from '@/test/canvasFixture'

import { settledShape, shapeOf } from './saveShape'

const NAME = '문서 요약 흐름'

/** 마지막 카드만 캔버스에서 새로 적은 기본 노드다. 저장할 때 서버가 만든다. */
const sentNodes = CANVAS_NODES.map((node) =>
  node.id === 'seed-5' ? { ...node, sourceId: '', prompt: '세 줄로 요약해 주세요' } : node,
)
const attached = { 'seed-5': 'node_new' }

/** 저장이 끝난 뒤 화면이 계산하는 「지금 보낼 모양」 — 리듀서가 원본 id 를 되쓴 상태에서. */
const pendingAfterSave = () => {
  const before = { ...initialCanvasState, nodes: sentNodes, edges: CANVAS_EDGES }
  const after = canvasReducer(before, { type: 'attachSources', sourceIdByNodeId: attached })
  return shapeOf(toWorkflowGraph(after.nodes, after.edges), NAME)
}

describe('settledShape', () => {
  it('새 노드가 든 저장은 보낸 모양과 원본 id 를 되쓴 뒤의 모양이 다르다', () => {
    const sentShape = shapeOf(toWorkflowGraph(sentNodes, CANVAS_EDGES), NAME)
    expect(sentShape).not.toBe(pendingAfterSave())
  })

  it('원본 id 를 되쓴 뒤의 모양을 기록해, 방금 저장한 구성이 「저장됨」으로 보인다', () => {
    expect(settledShape(sentNodes, CANVAS_EDGES, NAME, attached)).toBe(pendingAfterSave())
  })

  it('새 노드가 없으면 보낸 모양 그대로다', () => {
    expect(settledShape(CANVAS_NODES, CANVAS_EDGES, NAME, {})).toBe(
      shapeOf(toWorkflowGraph(CANVAS_NODES, CANVAS_EDGES), NAME),
    )
  })
})
