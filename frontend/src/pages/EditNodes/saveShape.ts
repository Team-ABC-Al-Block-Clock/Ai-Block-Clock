import type { WorkflowGraph } from '@/api/workflows'
import type { CanvasEdge, CanvasNodeData } from '@/lib/canvas/canvasWorkflow'
import { withSources } from '@/lib/canvas/canvasReducer'
import { toWorkflowGraph } from '@/lib/canvas/workflowPayload'

/** 저장할 내용을 글자 하나로 만든다. 저장한 것과 이것을 견줘 「저장됨」을 되돌린다. */
export const shapeOf = (graph: WorkflowGraph | null, name: string) =>
  JSON.stringify({ graph, name })

/**
 * 저장에 성공한 뒤 「저장된 상태」로 기록할 모양.
 *
 * 보낸 그대로를 기록하면 안 된다. 새로 만든 노드는 이름 · 프롬프트를 실어 보내지만, 저장이 끝나면
 * 서버가 준 원본 id 가 카드에 되쓰이고 그다음부터는 그 id 만 실린다. 보낸 모양과 되쓴 뒤의 모양이
 * 달라서, 방금 저장했는데도 「바뀐 것이 있다」 로 보이고 실행하기가 저장을 한 번 더 보낸다.
 *
 * 보낸 시점의 노드에 되쓴 것만 얹어 다시 계산한다 — 요청이 나가는 동안 바뀐 것은 섞지 않는다.
 */
export function settledShape(
  sentNodes: CanvasNodeData[],
  sentEdges: CanvasEdge[],
  sentName: string,
  sourceIdByNodeId: Record<string, string>,
) {
  return shapeOf(toWorkflowGraph(withSources(sentNodes, sourceIdByNodeId), sentEdges), sentName)
}
