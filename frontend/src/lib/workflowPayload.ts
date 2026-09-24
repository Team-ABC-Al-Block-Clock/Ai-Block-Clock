import type { EdgePlacement, NodePlacement, WorkflowDetail, WorkflowGraph } from '@/api/workflows'
import { NODE_SIZE, type CanvasEdge, type CanvasNodeData, linearOrder } from '@/lib/canvasWorkflow'

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

/**
 * 불러올 때 카드를 늘어놓는 자리. 자리표시자와 같은 보폭·높낮이로 지그재그를 만든다.
 *
 * 한 줄로 곧게 놓으면 카드 사이 선이 짧은 수평선이라 잘 보이지 않는다. 위아래로 엇갈리게
 * 두면 선이 휘어 어디서 어디로 가는지가 드러난다.
 */
const RESTORE_STEP = NODE_SIZE + 29
const RESTORE_X0 = 12
/** 짝수 번째는 아래, 홀수 번째는 위. */
const RESTORE_Y = [430, 325] as const

/** 팔레트가 아는 표시용 정보. 서버 응답에 이름·종류·단가가 없어 밖에서 채워 준다. */
export interface NodeLabel {
  name: string
  nodeType: CanvasNodeData['nodeType']
  price: string
  summary: string | null
  mine: boolean
}

/**
 * 저장된 워크플로우를 캔버스로 되돌린다.
 *
 * 서버는 좌표를 저장하지 않아 순서만 온다. 한 줄이라 왼쪽부터 같은 간격으로 늘어놓으면
 * 원래 배치와 달라도 읽는 데는 지장이 없다.
 *
 * 이름·종류·내 것인지도 서버가 주지 않는다(`NodeResponse` 는 `sourceId` 까지다). 팔레트에서
 * 찾아 넘겨받고, 못 찾으면 자리만 만들어 둔다 — 카드가 통째로 사라지면 저장된 구성이
 * 바뀌어 버린다.
 */
export function toCanvasWorkflow(
  detail: WorkflowDetail,
  describe: (sourceId: string) => NodeLabel | undefined,
): { nodes: CanvasNodeData[]; edges: CanvasEdge[] } {
  const idOf = new Map(detail.nodes.map((node, i) => [node.nodeKey, `loaded-${i + 1}`]))

  const nodes = detail.nodes.map((node, i) => {
    const label = describe(node.sourceId)
    return {
      id: idOf.get(node.nodeKey)!,
      name: label?.name ?? '알 수 없는 노드',
      nodeType: label?.nodeType ?? (node.sourceType === 'AGENT' ? 'AGENT' : 'TOOL'),
      sourceType: node.sourceType,
      sourceId: node.sourceId,
      // 모르는 노드의 단가는 지어내지 않는다. 카드가 0 원짜리로 보이면 견적과 어긋난다.
      price: label?.price ?? null,
      summary: label?.summary ?? null,
      x: RESTORE_X0 + i * RESTORE_STEP,
      y: RESTORE_Y[i % RESTORE_Y.length],
      // 모르는 것은 남의 것으로 둔다. 잘못 팔 수 있게 두는 쪽이 더 나쁘다.
      mine: label?.mine ?? false,
      floaty: '',
    } satisfies CanvasNodeData
  })

  const edges = detail.edges.flatMap((edge) => {
    const from = idOf.get(edge.sourceNodeKey)
    const to = idOf.get(edge.targetNodeKey)
    return from && to ? [{ id: `${from}__${to}`, from, to }] : []
  })

  return { nodes, edges }
}
