import { http } from 'msw'

import { API_BASE_URL } from '@/api/client'
import type { PaletteScope, WorkflowDetail, WorkflowGraph } from '@/api/workflows'
import { cursorOk, matchKeyword } from '@/mocks/cursor'
import { PLACEABLE_ITEMS, byMineThenUsed } from '@/mocks/fixtures/nodePalette'
import { fail, ok } from '@/mocks/response'
import { scene } from '@/mocks/scenario'

/** `/workflows` 목록과 삭제는 마이페이지 화면이 쓰는 묶음이라 `mypage.ts` 에 있다. */

/** 명세 기본값. 마켓 목록(9)과 다르다. */
const PAGE_SIZE = 10

const isScope = (value: unknown): value is PaletteScope => value === 'MINE' || value === 'ALL'

/** 서버가 막는 한계(`GraphLimits`). 넘으면 저장 단계에서 거절한다. */
const MAX_NODES = 20

/**
 * 저장된 워크플로우. 새로고침하면 비워진다 — 쿠키 세션과 달리 되살릴 근거가 없다.
 * 화면이 확인할 것은 「저장 → 회차가 오른다 → 어긋나면 거절」 이 흐름이다.
 */
const SAVED = new Map<string, WorkflowDetail>()

const PLACEABLE = new Set(PLACEABLE_ITEMS.map((item) => item.itemId))

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
  if (graph.nodes.some((node) => !PLACEABLE.has(node.sourceId)))
    return fail(404, 'WORKFLOW_015', '배치할 노드를 찾을 수 없습니다.')

  const edges = graph.edges ?? []
  if (edges.some((edge) => !keys.has(edge.sourceNodeKey) || !keys.has(edge.targetNodeKey)))
    return fail(400, 'WORKFLOW_013', '연결선이 존재하지 않는 노드를 가리킵니다.')
  // 한 줄이면 선이 노드보다 하나 적고, 드나드는 선이 저마다 하나뿐이다.
  const outs = new Set(edges.map((edge) => edge.sourceNodeKey))
  const ins = new Set(edges.map((edge) => edge.targetNodeKey))
  const linear =
    edges.length === graph.nodes.length - 1 &&
    outs.size === edges.length &&
    ins.size === edges.length
  if (!linear) return fail(400, 'WORKFLOW_013', '노드는 한 줄로 이어져야 합니다.')

  return null
}

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
  nodes: graph.nodes.map((node) => ({ ...node, versionId: null, inputSource: 'PREV' })),
  edges: graph.edges ?? [],
  estimatedCost: '0',
  currency: 'ABC',
  lastExecutedAt: null,
  updatedAt: new Date().toISOString(),
})

const saved = ({ workflowId, name, revision, estimatedCost, currency }: WorkflowDetail) => ({
  workflowId,
  name,
  revision,
  estimatedCost,
  currency,
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
    SAVED.set(detail.workflowId, detail)
    return ok(saved(detail), { status: 201 })
  }),

  http.put(`${API_BASE_URL}/workflows/:workflowId`, async ({ params, request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const id = String(params.workflowId)
    const before = SAVED.get(id)
    if (!before) return fail(404, 'WORKFLOW_002', '워크플로우를 찾을 수 없습니다.')

    const body = (await request.json()) as WorkflowGraph & { name: string; revision: number }
    const rejected = rejectGraph(body, body.name)
    if (rejected) return rejected
    // 낙관적 잠금. 화면이 들고 있던 회차가 저장된 것과 다르면 그 사이 누군가 고쳤다.
    if (body.revision !== before.revision)
      return fail(409, 'WORKFLOW_016', '워크플로우가 다른 요청에 의해 수정되었습니다.')

    // 설명은 만들 때만 받는다 — 수정 요청에 자리가 없어 그대로 둔다.
    const after = toDetail(id, before.revision + 1, body.name, before.description, body)
    SAVED.set(id, after)
    return ok(saved(after))
  }),

  http.get(`${API_BASE_URL}/workflows/:workflowId`, async ({ params, request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const detail = SAVED.get(String(params.workflowId))
    if (!detail) return fail(404, 'WORKFLOW_002', '워크플로우를 찾을 수 없습니다.')
    return ok(detail)
  }),
]
