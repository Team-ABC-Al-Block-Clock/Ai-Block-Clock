import { http } from 'msw'

import { API_BASE_URL } from '@/api/client'
import type {
  EdgePlacement,
  NodePlacement,
  PaletteScope,
  WorkflowDetail,
  WorkflowGraph,
} from '@/api/workflows'
import { cursorOk, matchKeyword } from '@/mocks/cursor'
import { PLACEABLE_ITEMS, byMineThenUsed } from '@/mocks/fixtures/nodePalette'
import { createLlmNode, knowsNode } from '@/mocks/handlers/nodes'
import { WORKFLOWS } from '@/mocks/fixtures/workflows'
import { fail, ok } from '@/mocks/response'
import { scene } from '@/mocks/scenario'

/** `/workflows` 목록과 삭제는 마이페이지 화면이 쓰는 묶음이라 `mypage.ts` 에 있다. */

/** 명세 기본값. 마켓 목록(9)과 다르다. */
const PAGE_SIZE = 10

const isScope = (value: unknown): value is PaletteScope => value === 'MINE' || value === 'ALL'

/** 서버가 막는 한계(`GraphLimits`). 넘으면 저장 단계에서 거절한다. */
const MAX_NODES = 20

/**
 * 저장된 워크플로우. localStorage 에 둔다 — 메모리에만 두면 주소창으로 들어가는 순간
 * 새로고침이라 방금 저장한 것도 404 가 된다. 불러오기를 확인할 길이 아예 없어진다.
 *
 * 되돌리려면 `localStorage.removeItem('mockWorkflows')`.
 */
const SAVED_KEY = 'mockWorkflows'

function readSaved(): Record<string, WorkflowDetail> {
  try {
    return JSON.parse(localStorage.getItem(SAVED_KEY) ?? '{}') as Record<string, WorkflowDetail>
  } catch {
    return {}
  }
}

function writeSaved(all: Record<string, WorkflowDetail>) {
  try {
    localStorage.setItem(SAVED_KEY, JSON.stringify(all))
  } catch {
    /* 접근 불가 환경 */
  }
}

/**
 * 픽스처 워크플로우는 저장소에 없다. 마이페이지 목록이 거는 편집 링크가 전부 404 가 되지 않게
 * 구성을 지어 준다 — 목록에는 이름뿐이라 내 기본 노드로 한 줄을 만든다.
 */
function fixtureDetail(workflowId: string): WorkflowDetail | undefined {
  const flow = WORKFLOWS.find((item) => item.workflowId === workflowId)
  if (!flow) return undefined

  const picks = PLACEABLE_ITEMS.filter((item) => item.mine).slice(0, 3)
  const graph: WorkflowGraph = {
    nodes: picks.map((item, i) => ({
      nodeKey: `n${i + 1}`,
      sourceType: item.itemType,
      sourceId: item.itemId,
    })),
    edges: picks.slice(1).map((_, i) => ({
      edgeKey: `e${i + 1}`,
      sourceNodeKey: `n${i + 1}`,
      targetNodeKey: `n${i + 2}`,
    })),
  }
  return toDetail(workflowId, 1, flow.name, flow.summary, graph)
}

/** 저장된 것이 없으면 픽스처로 지어 준다. 실행 견적 mock 도 같은 저장소를 읽는다. */
export const findWorkflowDetail = (workflowId: string) =>
  readSaved()[workflowId] ?? fixtureDetail(workflowId)

function putSaved(detail: WorkflowDetail) {
  const all = readSaved()
  all[detail.workflowId] = detail
  writeSaved(all)
}

/** 팔레트에 실리지 않아도 서버는 앞서 만든 노드를 안다. */
const isPlaceable = (sourceId: string) =>
  PLACEABLE_ITEMS.some((item) => item.itemId === sourceId) || knowsNode(sourceId)

/**
 * 서버가 그래프를 받아 줄지. 거절하면 오류 응답을, 통과하면 null 을 준다.
 *
 * 실서버와 코드까지 맞춘다 — 여기서 뭉개면 화면이 mock 에서만 통과한다.
 */
function rejectGraph(graph: WorkflowGraph, name: unknown) {
  if (typeof name !== 'string' || !name.trim() || name.length > 40)
    return fail(400, 'COMMON_001', '워크플로우 이름이 올바르지 않습니다.')
  if (!graph.nodes?.length) return fail(400, 'WORKFLOW_014', '노드를 하나 이상 놓아야 합니다.')
  if (graph.nodes.length > MAX_NODES)
    return fail(400, 'WORKFLOW_006', '노드 수가 허용 범위를 넘었습니다.')

  const keys = new Set(graph.nodes.map((node) => node.nodeKey))
  if (keys.size !== graph.nodes.length)
    return fail(400, 'WORKFLOW_013', '노드 키가 중복되었습니다.')
  // 기본 노드는 저장하며 만들어진다 — 가리킬 원본이 아직 없는 것이 정상이다.
  if (graph.nodes.some((node) => needsSource(node) && !isPlaceable(node.sourceId ?? '')))
    return fail(404, 'WORKFLOW_015', '배치할 노드를 찾을 수 없습니다.')
  if (graph.nodes.some((node) => isNewLlm(node) && !node.prompt?.trim()))
    return fail(400, 'COMMON_001', '요청 값이 올바르지 않습니다.', {
      prompt: '프롬프트를 입력해야 합니다.',
    })

  const edges = graph.edges ?? []
  if (edges.some((edge) => !keys.has(edge.sourceNodeKey) || !keys.has(edge.targetNodeKey)))
    return fail(400, 'WORKFLOW_013', '연결선이 존재하지 않는 노드를 가리킵니다.')
  // 트리다 — 들어오는 선은 자리마다 하나뿐이라 합류가 없다.
  const ins = new Set<string>()
  if (edges.some((edge) => !ins.add(edge.targetNodeKey)))
    return fail(400, 'WORKFLOW_013', '한 노드에 두 선이 들어올 수 없습니다.')

  const out = new Map<string, EdgePlacement[]>()
  for (const edge of edges)
    out.set(edge.sourceNodeKey, [...(out.get(edge.sourceNodeKey) ?? []), edge])

  if (graph.nodes.filter((node) => node.ioType === 'START').length > 1)
    return fail(400, 'WORKFLOW_013', '시작 노드는 하나만 놓을 수 있습니다.')
  if (graph.nodes.filter((node) => node.ioType === 'END').length > 1)
    return fail(400, 'WORKFLOW_013', '종료 노드는 하나만 놓을 수 있습니다.')
  // `NodePlacementRequest.matchesIoType` — 끝 노드는 둘 다 있어야 하고 시작은 둘 다 없어야 한다.
  if (
    graph.nodes.some(
      (node) => node.ioType === 'END' && (node.resultShape == null || node.resultFormat == null),
    )
  )
    return fail(400, 'COMMON_001', '요청 값이 올바르지 않습니다.', {
      resultShape: '끝 노드는 결과 구조와 형식을 보내야 합니다.',
    })
  if (
    graph.nodes.some(
      (node) => node.ioType === 'START' && (node.resultShape != null || node.resultFormat != null),
    )
  )
    return fail(400, 'COMMON_001', '요청 값이 올바르지 않습니다.', {
      resultShape: '시작 노드에는 결과 구조와 형식을 보낼 수 없습니다.',
    })

  for (const node of graph.nodes) {
    const next = out.get(node.nodeKey) ?? []
    if (node.sourceType === 'CONTROL') {
      if (!node.controlType || !node.condition)
        return fail(400, 'WORKFLOW_013', '분기 노드에는 조건이 있어야 합니다.')
      if (
        next.length !== 2 ||
        next.filter((edge) => edge.when === true).length !== 1 ||
        next.filter((edge) => edge.when === false).length !== 1
      )
        return fail(400, 'WORKFLOW_013', '분기에서는 참·거짓 두 갈래가 나가야 합니다.')
      continue
    }
    if (next.some((edge) => edge.when != null))
      return fail(400, 'WORKFLOW_013', '분기가 아닌 노드의 선에는 갈래를 붙일 수 없습니다.')
    if (next.length > 1) return fail(400, 'WORKFLOW_013', '노드에서 나가는 선은 하나뿐입니다.')
    if (node.ioType === 'END' && next.length > 0)
      return fail(400, 'WORKFLOW_013', '종료 노드에서는 선이 나갈 수 없습니다.')
  }

  const heads = [...keys].filter((key) => !ins.has(key))
  // 머리가 없으면 전부 고리 안이다. 둘 이상이면 덩어리가 갈라져 있다.
  if (heads.length === 0) return fail(400, 'WORKFLOW_005', '연결이 고리를 이룹니다.')
  if (heads.length > 1) return fail(400, 'WORKFLOW_013', '맨 앞 노드는 하나여야 합니다.')
  if (graph.nodes.some((node) => node.ioType === 'START' && node.nodeKey !== heads[0]))
    return fail(400, 'WORKFLOW_013', '시작 노드가 맨 앞에 있어야 합니다.')

  // 개수만으로는 고리를 못 거른다 — A→B, B→A 도 위를 통과한다. 머리에서 다 닿는지 본다.
  let walked = 0
  const walk = (key: string) => {
    walked += 1
    for (const edge of out.get(key) ?? []) walk(edge.targetNodeKey)
  }
  walk(heads[0])
  if (walked !== graph.nodes.length)
    return fail(400, 'WORKFLOW_005', '연결이 고리를 이루거나 끊겨 있습니다.')

  return null
}

/** 원본을 가리켜야 하는 자리. 저장하며 만들 기본 노드와 제어·입출력은 뺀다. */
const needsSource = (node: NodePlacement) =>
  !isNewLlm(node) && node.sourceType !== 'CONTROL' && node.sourceType !== 'IO'

/** 원본 없이 이름·프롬프트만 실려 온 기본 노드. 저장하며 만든다. */
const isNewLlm = (node: NodePlacement) =>
  node.sourceType === 'NODE' && node.nodeType === 'LLM' && !node.sourceId

const toDetail = (
  workflowId: string,
  revision: number,
  name: string,
  description: string | null,
  graph: WorkflowGraph,
): WorkflowDetail => ({
  workflowId,
  name,
  description,
  revision,
  nodes: graph.nodes.map((node) => ({
    nodeKey: node.nodeKey,
    sourceType: node.sourceType,
    // 저장하며 만든 노드는 여기서 id 를 얻는다. 화면은 이 값으로 상세를 다시 읽는다.
    sourceId: needsSource(node) || isNewLlm(node) ? (node.sourceId ?? createLlmNode(node)) : null,
    versionId: null,
    controlType: node.controlType ?? null,
    condition: node.condition ?? null,
    ioType: node.ioType ?? null,
    resultShape: node.resultShape ?? null,
    resultFormat: node.resultFormat ?? null,
    inputSource: 'PREV' as const,
  })),
  edges: graph.edges ?? [],
  lastExecutedAt: null,
  updatedAt: new Date().toISOString(),
})

const saved = ({ workflowId, name, revision, nodes }: WorkflowDetail) => ({
  workflowId,
  name,
  revision,
  // 저장하며 만든 노드는 여기서 id 를 받는다. 화면이 이 값을 캔버스에 되쓴다.
  nodes: nodes.map((node) => ({
    nodeKey: node.nodeKey,
    sourceType: node.sourceType,
    sourceId: node.sourceId,
  })),
})

export const workflowsHandlers = [
  http.get(`${API_BASE_URL}/workflows/node-palette`, async ({ request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const url = new URL(request.url)
    const scope = url.searchParams.get('scope')
    // 필수값이다. 없을 때 ALL 로 떨어뜨리면 MINE 으로 물어도 다른 사람의 에이전트가 섞인다.
    if (!isScope(scope)) return fail(400, 'COMMON_001', '조회 조건이 올바르지 않습니다.')

    const keyword = url.searchParams.get('keyword') ?? ''
    const items = PLACEABLE_ITEMS.filter((item) => scope === 'ALL' || item.mine)
      // 명세상 검색 대상은 이름뿐이다. 한 줄 설명은 보지 않는다.
      .filter((item) => matchKeyword(keyword, item.name))
      .sort(byMineThenUsed)

    return cursorOk(url, items, (item) => item.itemId, {
      defaultSize: PAGE_SIZE,
      cursorCode: 'MARKET_003',
      extra: { scope },
    })
  }),

  http.post(`${API_BASE_URL}/workflows`, async ({ request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const body = (await request.json()) as WorkflowGraph & { name: string; description?: string }
    const rejected = rejectGraph(body, body.name)
    if (rejected) return rejected

    const detail = toDetail(crypto.randomUUID(), 1, body.name, body.description ?? null, body)
    putSaved(detail)
    return ok(saved(detail), { status: 201 })
  }),

  http.put(`${API_BASE_URL}/workflows/:workflowId`, async ({ params, request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const id = String(params.workflowId)
    const before = findWorkflowDetail(id)
    if (!before) return fail(404, 'WORKFLOW_002', '워크플로우를 찾을 수 없습니다.')

    const body = (await request.json()) as WorkflowGraph & { name: string; revision: number }
    const rejected = rejectGraph(body, body.name)
    if (rejected) return rejected
    // 낙관적 잠금. 화면이 들고 있던 회차가 저장된 것과 다르면 그 사이 누군가 고쳤다.
    if (body.revision !== before.revision)
      return fail(409, 'WORKFLOW_016', '워크플로우가 다른 요청에 의해 수정되었습니다.')

    // 설명은 만들 때만 받는다 — 수정 요청에 자리가 없어 그대로 둔다.
    const after = toDetail(id, before.revision + 1, body.name, before.description, body)
    putSaved(after)
    return ok(saved(after))
  }),

  http.get(`${API_BASE_URL}/workflows/:workflowId`, async ({ params, request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const detail = findWorkflowDetail(String(params.workflowId))
    if (!detail) return fail(404, 'WORKFLOW_002', '워크플로우를 찾을 수 없습니다.')
    return ok(detail)
  }),
]
