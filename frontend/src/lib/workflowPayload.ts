import type { EdgePlacement, NodePlacement, WorkflowGraph } from '@/api/workflows'
import { type CanvasEdge, type CanvasNodeData, linearOrder } from '@/lib/canvasWorkflow'

/**
 * 캔버스를 서버가 받는 그래프로 바꾼다. 한 줄이 아니면 `null`.
 *
 * 키는 여기서 새로 매긴다. 캔버스의 `id`(`placed-3`)와 선의 `id`(`a__b`)를 그대로 쓰면
 * 서버의 64자 제한에 걸린다 — 노드 id 가 UUID 가 되면 선 키가 73자다. 워크플로우 안에서만
 * 통하면 되는 값이라 순서대로 `n1…nN` · `e1…eN-1` 로 짧게 붙인다.
 *
 * 좌표는 보내지 않는다. 서버가 저장하지 않아서(`NodePlacementRequest`) 다시 열면
 * 순서만 남는다. 한 줄이라 그 순서로 좌우에 다시 늘어놓을 수 있다.
 *
 * `inputSource` 도 보내지 않는다. 고르는 화면이 없어 서버 기본값(`PREV`)에 맡긴다.
 */
export function toWorkflowGraph(
  nodes: CanvasNodeData[],
  edges: CanvasEdge[],
): WorkflowGraph | null {
  const order = linearOrder(nodes, edges)
  if (!order) return null

  const keyOf = new Map(order.map((node, i) => [node.id, `n${i + 1}`]))
  const placements: NodePlacement[] = order.map((node) => ({
    nodeKey: keyOf.get(node.id)!,
    sourceType: node.sourceType,
    sourceId: node.sourceId,
  }))
  // 순서가 이미 한 줄이라 이웃끼리 이으면 선 목록이 그대로 나온다.
  const links: EdgePlacement[] = order.slice(1).map((node, i) => ({
    edgeKey: `e${i + 1}`,
    sourceNodeKey: keyOf.get(order[i].id)!,
    targetNodeKey: keyOf.get(node.id)!,
  }))

  return { nodes: placements, edges: links }
}
