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

/**
 * 저장하며 만들어진 노드.
 *
 * 워크플로우가 `localStorage` 에 남는데 노드만 메모리에 두면, 새로고침한 뒤 배치는 있고
 * 원본이 없어 이름과 프롬프트가 통째로 사라진다. 같은 자리에 함께 남긴다.
 */
const CREATED_KEY = 'abc:mock-created-nodes'

function readCreated(): Record<string, NodeDetail> {
  try {
    return JSON.parse(localStorage.getItem(CREATED_KEY) ?? '{}') as Record<string, NodeDetail>
  } catch {
    return {}
  }
}

function writeCreated(all: Record<string, NodeDetail>) {
  try {
    localStorage.setItem(CREATED_KEY, JSON.stringify(all))
  } catch {
    /* 접근 불가 환경 */
  }
}

const DEFAULT_MODEL: AiModel = 'GEMINI_2_5_FLASH'

/**
 * 저장하며 만드는 기본 노드. 서버는 프롬프트와 모델로 원가를 매긴다 —
 * mock 은 글자 수로 흉내만 낸다.
 */
export function createLlmNode(placement: {
  name?: string
  prompt?: string
  model?: AiModel
}): string {
  const all = readCreated()
  const node: NodeDetail = {
    // 새로고침을 견뎌야 해서 일련번호를 메모리에 두지 않는다.
    nodeId: `node_new_${Date.now().toString(36)}_${Object.keys(all).length + 1}`,
    name: placement.name?.trim() || '이름 없는 노드',
    prompt: placement.prompt?.trim() ?? '',
    model: placement.model ?? DEFAULT_MODEL,
    baseCost: String(Math.max(1, Math.ceil((placement.prompt?.length ?? 0) / 40))),
    currency: 'ABC',
    refFiles: [],
  }
  all[node.nodeId] = node
  writeCreated(all)
  return node.nodeId
}

/** 저장하며 만들어졌거나 앞서 만들어 둔 원본. 견적·이름표가 이 값을 읽는다. */
export const findNode = (nodeId: string): NodeDetail | undefined =>
  readCreated()[nodeId] ?? NODE_DETAILS.find((node) => node.nodeId === nodeId)

/** 저장 검증이 방금 만든 노드를 알아야 `WORKFLOW_015` 로 되튕기지 않는다. */
export const knowsNode = (nodeId: string) => !!findNode(nodeId)

export const nodeHandlers = [
  http.get(`${API_BASE_URL}/nodes/:nodeId`, async (info) => {
    const forced = await scene(info.request)
    if (forced) return forced

    const { nodeId } = info.params as { nodeId: string }
    if (nodeId === OTHERS_NODE_ID) return fail(403, 'NODE_002', '노드에 대한 권한이 없습니다.')

    const node = findNode(nodeId)
    return node ? ok(node) : fail(404, 'NODE_001', '노드를 찾을 수 없습니다.')
  }),
]
