import { http } from 'msw'

import { API_BASE_URL } from '@/api/client'
import { type AiModel, type NodeDetail } from '@/api/nodes'
import { NODE_DETAILS, OTHERS_NODE_ID } from '@/mocks/fixtures/nodes'
import { fail, ok } from '@/mocks/response'
import { scene } from '@/mocks/scenario'

/**
 * 작업 노드 mock — 조회만 있다.
 *
 * 만들기는 워크플로우 저장이 맡는다(`handlers/workflows.ts` 의 `createLlmNode`). 만든 것은
 * 탭이 살아 있는 동안만 남고, 새로 고치면 픽스처로 돌아간다.
 */

const created = new Map<string, NodeDetail>()

const DEFAULT_MODEL: AiModel = 'GEMINI_2_5_FLASH'

let serial = 0

/**
 * 저장하며 만드는 기본 노드. 서버는 프롬프트와 모델로 원가를 매긴다 —
 * mock 은 글자 수로 흉내만 낸다.
 */
export function createLlmNode(placement: {
  name?: string
  prompt?: string
  model?: AiModel
}): string {
  serial += 1
  const node: NodeDetail = {
    nodeId: `node_new_${serial}`,
    name: placement.name?.trim() || '이름 없는 노드',
    prompt: placement.prompt?.trim() ?? '',
    model: placement.model ?? DEFAULT_MODEL,
    baseCost: String(Math.max(1, Math.ceil((placement.prompt?.length ?? 0) / 40))),
    currency: 'ABC',
    refFiles: [],
  }
  created.set(node.nodeId, node)
  return node.nodeId
}

/** 저장하며 만들어졌거나 앞서 만들어 둔 원본. 견적·이름표가 이 값을 읽는다. */
export const findNode = (nodeId: string) =>
  created.get(nodeId) ?? NODE_DETAILS.find((node) => node.nodeId === nodeId)

/** 저장 검증이 방금 만든 노드를 알아야 `WORKFLOW_015` 로 되튕기지 않는다. */
export const knowsNode = (nodeId: string) => !!findNode(nodeId)

export const nodeHandlers = [
  http.get(`${API_BASE_URL}/nodes/:nodeId`, async (info) => {
    const forced = await scene(info.request)
    if (forced) return forced

    const { nodeId } = info.params as { nodeId: string }
    if (nodeId === OTHERS_NODE_ID) return fail(403, 'NODE_002', '노드에 대한 권한이 없습니다.')

    const node = created.get(nodeId) ?? NODE_DETAILS.find((it) => it.nodeId === nodeId)
    return node ? ok(node) : fail(404, 'NODE_001', '노드를 찾을 수 없습니다.')
  }),
]
