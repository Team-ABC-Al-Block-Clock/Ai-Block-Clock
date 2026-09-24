import { http } from 'msw'

import { API_BASE_URL } from '@/api/client'
import { NODE_NAME_MAX, type NodeDetail, type NodeSaveBody } from '@/api/nodes'
import { NODE_DETAILS, OTHERS_NODE_ID, PLACED_NODE_ID } from '@/mocks/fixtures/nodes'
import { fail, ok } from '@/mocks/response'
import { scene } from '@/mocks/scenario'

/**
 * 작업 노드 mock.
 *
 * 고친 내용은 탭이 살아 있는 동안만 남는다 — 편집이 화면에 반영되는지 보는 것이 목적이고,
 * 새로 고치면 픽스처로 돌아간다.
 */

const edited = new Map<string, NodeDetail>()
/** 지운 것은 픽스처에 그대로 남아 있어 따로 기억해야 다시 살아나지 않는다. */
const removed = new Set<string>()

const DEFAULT_MODEL = 'GEMINI_2_5_FLASH' as const

const findNode = (nodeId: string) =>
  removed.has(nodeId)
    ? undefined
    : (edited.get(nodeId) ?? NODE_DETAILS.find((node) => node.nodeId === nodeId))

const notFound = () => fail(404, 'NODE_001', '노드를 찾을 수 없습니다.')
const forbidden = () => fail(403, 'NODE_002', '노드에 대한 권한이 없습니다.')

/** 서버 검증 순서를 그대로 따른다 — 여기서 뭉개면 화면이 mock 에서만 통과한다. */
function rejectBody(body: NodeSaveBody) {
  if (typeof body?.name !== 'string' || !body.name.trim() || body.name.length > NODE_NAME_MAX)
    return fail(400, 'COMMON_001', '노드 이름이 올바르지 않습니다.')
  if (typeof body?.prompt !== 'string' || !body.prompt.trim())
    return fail(400, 'NODE_003', '프롬프트를 입력해야 합니다.')
  return null
}

let created = 0

export const nodeHandlers = [
  http.get(`${API_BASE_URL}/nodes/:nodeId`, async (info) => {
    const forced = await scene(info.request)
    if (forced) return forced

    const { nodeId } = info.params as { nodeId: string }
    if (nodeId === OTHERS_NODE_ID) return forbidden()

    const node = findNode(nodeId)
    return node ? ok(node) : notFound()
  }),

  http.post(`${API_BASE_URL}/nodes`, async (info) => {
    const forced = await scene(info.request)
    if (forced) return forced

    const body = (await info.request.json()) as NodeSaveBody
    const rejected = rejectBody(body)
    if (rejected) return rejected

    created += 1
    const node: NodeDetail = {
      nodeId: `node_new_${created}`,
      name: body.name,
      prompt: body.prompt,
      model: body.model ?? DEFAULT_MODEL,
      baseCost: '5',
      currency: 'ABC',
      refFiles: [],
    }
    edited.set(node.nodeId, node)
    return ok(node, { status: 201 })
  }),

  http.put(`${API_BASE_URL}/nodes/:nodeId`, async (info) => {
    const forced = await scene(info.request)
    if (forced) return forced

    const { nodeId } = info.params as { nodeId: string }
    if (nodeId === OTHERS_NODE_ID) return forbidden()

    const node = findNode(nodeId)
    if (!node) return notFound()

    const body = (await info.request.json()) as NodeSaveBody
    const rejected = rejectBody(body)
    if (rejected) return rejected

    const next: NodeDetail = {
      ...node,
      name: body.name,
      prompt: body.prompt,
      model: body.model ?? DEFAULT_MODEL,
    }
    edited.set(nodeId, next)
    return ok(next)
  }),

  http.delete(`${API_BASE_URL}/nodes/:nodeId`, async (info) => {
    const forced = await scene(info.request)
    if (forced) return forced

    const { nodeId } = info.params as { nodeId: string }
    if (nodeId === OTHERS_NODE_ID) return forbidden()
    if (!findNode(nodeId)) return notFound()
    // 픽스처 워크플로우가 이 노드를 물고 있다.
    if (nodeId === PLACED_NODE_ID)
      return fail(409, 'NODE_004', '워크플로우나 에이전트에 배치된 노드는 삭제할 수 없습니다.')

    edited.delete(nodeId)
    removed.add(nodeId)
    return ok({ nodeId, deletedAt: new Date().toISOString() })
  }),
]
