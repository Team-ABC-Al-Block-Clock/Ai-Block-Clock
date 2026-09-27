import type {
  EdgePlacement,
  NodePlacement,
  ResultFormat,
  ResultShape,
  WorkflowDetail,
  WorkflowGraph,
} from '@/api/workflows'

/**
 * 끝 노드가 낼 결과. 고르는 화면이 없어 여기서 정한다.
 *
 * 실행 결과 화면이 내려받을 수 있는 것이 PDF 라(`ExecutionResultHarvester` 가 JSON 과 PDF 를
 * 거둔다) 문서·PDF 로 둔다. 화면이 생기면 노드가 제 값을 들고 다닌다.
 */
const DEFAULT_RESULT: { shape: ResultShape; format: ResultFormat } = {
  shape: 'DOCUMENT',
  format: 'PDF',
}
import type { AiModel, NodeOutput } from '@/api/nodes'
import { controlKindName } from '@/lib/format'
import {
  NODE_SIZE,
  type CanvasCondition,
  type CanvasEdge,
  type CanvasNodeData,
  treeOrder,
} from '@/lib/canvasWorkflow'

/**
 * 캔버스를 서버가 받는 그래프로 바꾼다. 저장할 수 없는 모양이면 `null`.
 *
 * 키는 여기서 새로 매긴다. 캔버스의 `id`(`placed-3`)와 선의 `id`(`a__b`)를 그대로 쓰면
 * 서버의 64자 제한에 걸린다 — 노드 id 가 UUID 가 되면 선 키가 73자다. 워크플로우 안에서만
 * 통하면 되는 값이라 훑은 차례대로 `n1…nN` · `e1…eM` 으로 짧게 붙인다.
 *
 * 좌표는 보내지 않는다. 서버가 저장하지 않아서(`NodePlacementRequest`) 다시 열면 이어진
 * 모양만 남는다 — 그것으로 다시 늘어놓는다(`toCanvasWorkflow`).
 *
 * `inputSource` 도 보내지 않는다. 고르는 화면이 없어 서버 기본값(`PREV`)에 맡긴다.
 */
export function toWorkflowGraph(
  nodes: CanvasNodeData[],
  edges: CanvasEdge[],
): (WorkflowGraph & { nodeIdByKey: Record<string, string> }) | null {
  const order = treeOrder(nodes, edges)
  if (!order) return null

  const keyOf = new Map(order.nodes.map((node, i) => [node.id, `n${i + 1}`]))
  const nodeIdByKey = Object.fromEntries(order.nodes.map((node, i) => [`n${i + 1}`, node.id]))

  const placements: NodePlacement[] = []
  for (const node of order.nodes) {
    const placed = { nodeKey: keyOf.get(node.id)!, sourceType: node.sourceType }

    if (node.sourceType === 'CONTROL') {
      // 조건이 읽는 앞 노드는 여기서야 키를 얻는다. 조건이 비어 있으면 서버가 물린다.
      const left = node.condition && keyOf.get(node.condition.fromId)
      if (!node.condition || !left) return null
      placements.push({
        ...placed,
        controlType: node.controlType ?? 'BRANCH',
        condition: {
          left: `${left}.${node.condition.path}`,
          operator: node.condition.operator,
          right: node.condition.right,
        },
      })
      continue
    }

    if (node.sourceType === 'IO') {
      if (!node.ioType) return null
      // 끝 노드는 결과 구조와 형식이 둘 다 있어야 서버가 받는다
      // (`NodePlacementRequest.matchesIoType`). 고르는 화면이 아직 없어 기본값을 싣는다.
      placements.push({
        ...placed,
        ioType: node.ioType,
        ...(node.ioType === 'START' && node.fields?.length ? { fields: node.fields } : {}),
        ...(node.ioType === 'END'
          ? {
              resultShape: node.resultShape ?? DEFAULT_RESULT.shape,
              resultFormat: node.resultFormat ?? DEFAULT_RESULT.format,
            }
          : {}),
      })
      continue
    }

    // 캔버스에서 적은 기본 노드는 원본이 없다. 이름·프롬프트를 실어 보내면 서버가 만든다.
    if (node.sourceType === 'NODE' && !node.sourceId) {
      placements.push({
        ...placed,
        nodeType: 'LLM',
        name: node.name,
        prompt: node.prompt ?? '',
        ...(node.model ? { model: node.model } : {}),
        ...(node.output ? { output: node.output } : {}),
      })
      continue
    }

    placements.push({ ...placed, sourceId: node.sourceId })
  }

  const links: EdgePlacement[] = order.edges.map((edge, i) => ({
    edgeKey: `e${i + 1}`,
    sourceNodeKey: keyOf.get(edge.from)!,
    targetNodeKey: keyOf.get(edge.to)!,
    ...(edge.when == null ? {} : { when: edge.when }),
  }))

  return { nodes: placements, edges: links, nodeIdByKey }
}

/**
 * 불러올 때 카드를 늘어놓는 자리. 깊이마다 오른쪽으로, 같은 깊이의 형제는 아래로.
 *
 * 한 줄이면 깊이가 곧 차례라 예전처럼 위아래로 엇갈린다 — 일직선으로 두면 카드 사이 선이
 * 짧은 수평선이라 어디서 어디로 가는지가 보이지 않는다.
 */
const RESTORE_STEP = NODE_SIZE + 29
const RESTORE_ROW = NODE_SIZE + 24
const RESTORE_X0 = 12
/** 짝수 깊이는 아래, 홀수 깊이는 위. */
const RESTORE_Y = [430, 325] as const

/** 팔레트가 아는 표시용 정보. 서버 응답에 종류·단가가 없어 밖에서 채워 준다. */
export interface NodeLabel {
  name: string
  nodeType: CanvasNodeData['nodeType']
  price: string
  summary: string | null
  /** 기본 노드만. 우측 패널이 그대로 보여주고 다시 저장할 때 실려 나간다 */
  prompt?: string | null
  model?: AiModel | null
  output?: NodeOutput | null
  /** 에이전트만. 우측 패널이 단가 옆에 함께 보여준다 */
  executionCount?: number | null
  owner?: string | null
  mine: boolean
}

/** 서버의 `<nodeKey>.<경로>` 를 캔버스 id 로 옮긴다. 가리키는 자리가 없으면 비운다. */
function toCanvasCondition(
  condition: WorkflowDetail['nodes'][number]['condition'],
  idOf: Map<string, string>,
): CanvasCondition | null {
  if (!condition) return null
  const dot = condition.left.indexOf('.')
  const fromId = dot < 0 ? undefined : idOf.get(condition.left.slice(0, dot))
  if (!fromId) return null
  return {
    fromId,
    path: condition.left.slice(dot + 1),
    operator: condition.operator,
    right: condition.right,
  }
}

/**
 * 저장된 워크플로우를 캔버스로 되돌린다.
 *
 * 서버는 좌표를 저장하지 않아 이어진 모양만 온다. 선을 따라 깊이를 재 다시 늘어놓으면
 * 원래 배치와 달라도 읽는 데는 지장이 없다.
 *
 * 이름·판매 판정은 서버가 준다. 단가·종류·프롬프트는 주지 않아 밖에서 찾아 넘겨받고, 못
 * 찾으면 자리만 만들어 둔다 — 카드가 통째로 사라지면 저장된 구성이 바뀌어 버린다.
 */
export function toCanvasWorkflow(
  detail: WorkflowDetail,
  describe: (sourceId: string) => NodeLabel | undefined,
): { nodes: CanvasNodeData[]; edges: CanvasEdge[] } {
  const idOf = new Map(detail.nodes.map((node, i) => [node.nodeKey, `loaded-${i + 1}`]))

  // 합류는 부모가 여럿이다. 가장 깊은 부모 다음 칸에 둬야 선이 뒤로 꺾이지 않는다.
  const parentsOf = new Map<string, string[]>()
  for (const edge of detail.edges)
    parentsOf.set(edge.targetNodeKey, [
      ...(parentsOf.get(edge.targetNodeKey) ?? []),
      edge.sourceNodeKey,
    ])
  const depths = new Map<string, number>()
  const depthOf = (key: string): number => {
    const seen = depths.get(key)
    if (seen !== undefined) return seen
    // 고리가 섞여 와도 끝없이 되돌지 않게 먼저 자리를 잡아 둔다.
    depths.set(key, 0)
    const parents = parentsOf.get(key) ?? []
    const at = parents.length ? Math.max(...parents.map(depthOf)) + 1 : 0
    depths.set(key, at)
    return at
  }
  // 갈래는 참을 위 줄, 거짓을 아래 줄로 벌리고 합류는 들어온 줄들의 가운데로 돌아온다.
  // 상세는 부모를 자식보다 먼저 싣는다(`WorkflowGraph.nodesInOrder`).
  const lanes = new Map<string, number>()
  for (const node of detail.nodes) {
    const incoming = detail.edges.filter((edge) => edge.targetNodeKey === node.nodeKey)
    const from = incoming.map(
      (edge) => (lanes.get(edge.sourceNodeKey) ?? 0) + (edge.when == null ? 0 : edge.when ? -1 : 1),
    )
    lanes.set(node.nodeKey, from.length ? from.reduce((a, b) => a + b, 0) / from.length : 0)
  }
  const taken = new Set<string>()
  const laneOf = (key: string, depth: number) => {
    let lane = lanes.get(key) ?? 0
    // 갈래 안의 갈래가 옆 갈래와 같은 자리에 오면 바깥으로 민다.
    while (taken.has(`${depth}:${lane}`)) lane += lane < 0 ? -1 : 1
    taken.add(`${depth}:${lane}`)
    return lane
  }

  const nodes = detail.nodes.map((node) => {
    const control = node.controlType
    const io = node.ioType
    const label = node.sourceId ? describe(node.sourceId) : undefined
    const depth = depthOf(node.nodeKey)
    const lane = laneOf(node.nodeKey, depth)

    return {
      id: idOf.get(node.nodeKey)!,
      // 제어·입출력은 가리킬 원본이 없어 팔레트에서 이름을 찾을 수 없다.
      name:
        control || io
          ? controlKindName(control ?? io!)
          : (node.name ?? label?.name ?? '알 수 없는 노드'),
      // 제어·입출력은 종류를 `controlType`·`ioType` 이 든다.
      nodeType:
        control || io ? null : (label?.nodeType ?? (node.sourceType === 'AGENT' ? 'AGENT' : 'LLM')),
      sourceType: node.sourceType,
      controlType: control,
      ioType: io,
      fields: io === 'START' ? node.fields : null,
      condition: toCanvasCondition(node.condition, idOf),
      resultShape: node.resultShape,
      resultFormat: node.resultFormat,
      sourceId: node.sourceId ?? '',
      // 모르는 노드의 단가는 지어내지 않는다. 카드가 0 원짜리로 보이면 견적과 어긋난다.
      price: label?.price ?? null,
      summary: label?.summary ?? null,
      // 기본 노드는 여기 담겨 와야 우측 패널이 채워지고 다시 저장할 때도 살아남는다.
      prompt: label?.prompt ?? null,
      model: label?.model ?? null,
      output: label?.output ?? null,
      executionCount: label?.executionCount ?? null,
      owner: label?.owner ?? null,
      x: RESTORE_X0 + depth * RESTORE_STEP,
      y: RESTORE_Y[depth % RESTORE_Y.length] + lane * RESTORE_ROW,
      // 서버 판정이 먼저다. 모르는 것은 남의 것으로 둔다 — 잘못 팔 수 있게 두는 쪽이 더 나쁘다.
      mine: control || io ? null : (node.mine ?? label?.mine ?? false),
      floaty: '',
    } satisfies CanvasNodeData
  })

  const edges = detail.edges.flatMap((edge) => {
    const from = idOf.get(edge.sourceNodeKey)
    const to = idOf.get(edge.targetNodeKey)
    return from && to
      ? [{ id: `${from}__${to}`, from, to, ...(edge.when == null ? {} : { when: edge.when }) }]
      : []
  })

  return { nodes, edges }
}
