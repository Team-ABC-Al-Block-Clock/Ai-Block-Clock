import { http } from 'msw'

import { API_BASE_URL } from '@/api/client'
import {
  NODE_NAME_MAX,
  REF_FILE_LIMITS,
  type AiModel,
  type NodeDetail,
  type NodeSaveBody,
  type RefFile,
} from '@/api/nodes'
import { NODE_DETAILS, OTHERS_NODE_ID } from '@/mocks/fixtures/nodes'
import { fail, ok } from '@/mocks/response'
import { scene } from '@/mocks/scenario'

/**
 * 작업 노드 mock — 조회와 참고 자료 업로드·삭제.
 *
 * 만들기는 워크플로우 저장이 맡는다(`handlers/workflows.ts` 의 `createLlmNode`).
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

/** 고친 노드를 남긴다. 픽스처 노드도 여기에 복사해 두면 다음 조회부터 바뀐 값이 선다. */
function saveNode(node: NodeDetail) {
  const all = readCreated()
  all[node.nodeId] = node
  writeCreated(all)
}

/** 저장하며 만들어졌거나 앞서 만들어 둔 원본. 견적·이름표가 이 값을 읽는다. */
export const findNode = (nodeId: string): NodeDetail | undefined =>
  readCreated()[nodeId] ?? NODE_DETAILS.find((node) => node.nodeId === nodeId)

/** 저장 검증이 방금 만든 노드를 알아야 `WORKFLOW_015` 로 되튕기지 않는다. */
export const knowsNode = (nodeId: string) => !!findNode(nodeId)

export const nodeHandlers = [
  http.put(`${API_BASE_URL}/nodes/:nodeId`, async (info) => {
    const forced = await scene(info.request)
    if (forced) return forced

    const { nodeId } = info.params as { nodeId: string }
    if (nodeId === OTHERS_NODE_ID) return fail(403, 'NODE_002', '노드에 대한 권한이 없습니다.')

    const node = findNode(nodeId)
    if (!node) return fail(404, 'NODE_001', '노드를 찾을 수 없습니다.')

    const body = (await info.request.json()) as NodeSaveBody
    const violations: Record<string, string> = {}
    if (typeof body?.name !== 'string' || !body.name.trim() || body.name.length > NODE_NAME_MAX)
      violations.name = '이름이 올바르지 않습니다.'
    if (typeof body?.prompt !== 'string' || !body.prompt.trim())
      violations.prompt = '프롬프트를 입력해야 합니다.'
    if (Object.keys(violations).length)
      return fail(400, 'COMMON_001', '요청 값이 올바르지 않습니다.', violations)

    const next: NodeDetail = {
      ...node,
      name: body.name.trim(),
      prompt: body.prompt.trim(),
      model: body.model ?? DEFAULT_MODEL,
      // 원가는 프롬프트와 모델로 다시 매겨진다.
      baseCost: String(Math.max(1, Math.ceil(body.prompt.trim().length / 40))),
    }
    saveNode(next)
    return ok(next)
  }),

  http.post(`${API_BASE_URL}/nodes/:nodeId/ref-files`, async (info) => {
    const forced = await scene(info.request)
    if (forced) return forced

    const { nodeId } = info.params as { nodeId: string }
    if (nodeId === OTHERS_NODE_ID) return fail(403, 'NODE_002', '노드에 대한 권한이 없습니다.')

    const node = findNode(nodeId)
    if (!node) return fail(404, 'NODE_001', '노드를 찾을 수 없습니다.')

    const form = await info.request.formData()
    const file = form.get('file')
    if (!(file instanceof File)) return fail(400, 'COMMON_001', '요청 값이 올바르지 않습니다.')

    // 서버 `RefFileValidator` 와 같은 순서로 본다 — 화면이 코드로 갈라서 순서가 어긋나면 안 된다.
    if (file.size === 0) return fail(400, 'NODE_006', '빈 파일은 붙일 수 없습니다.')
    if (!REF_FILE_LIMITS.extensions.some((ext) => file.name.toLowerCase().endsWith(ext)))
      return fail(400, 'NODE_006', 'PDF · 텍스트 · 마크다운 · CSV 만 붙일 수 있습니다.')

    const attached = node.refFiles.reduce((sum, kept) => sum + kept.size, 0)
    if (attached + file.size > REF_FILE_LIMITS.maxTotalBytes)
      return fail(413, 'NODE_007', '노드에 붙인 참고 자료의 합계가 10MB 를 넘습니다.')

    const saved: RefFile = {
      refFileId: `ref_${Date.now().toString(36)}_${node.refFiles.length + 1}`,
      filename: file.name,
      size: file.size,
    }
    saveNode({ ...node, refFiles: [...node.refFiles, saved] })
    return ok(saved, { status: 201 })
  }),

  http.delete(`${API_BASE_URL}/nodes/:nodeId/ref-files/:refFileId`, async (info) => {
    const forced = await scene(info.request)
    if (forced) return forced

    const { nodeId, refFileId } = info.params as { nodeId: string; refFileId: string }
    if (nodeId === OTHERS_NODE_ID) return fail(403, 'NODE_002', '노드에 대한 권한이 없습니다.')

    const node = findNode(nodeId)
    if (!node) return fail(404, 'NODE_001', '노드를 찾을 수 없습니다.')
    if (!node.refFiles.some((file) => file.refFileId === refFileId))
      return fail(404, 'NODE_005', '참고 자료를 찾을 수 없습니다.')

    saveNode({ ...node, refFiles: node.refFiles.filter((file) => file.refFileId !== refFileId) })
    return ok(null)
  }),

  http.get(`${API_BASE_URL}/nodes/:nodeId`, async (info) => {
    const forced = await scene(info.request)
    if (forced) return forced

    const { nodeId } = info.params as { nodeId: string }
    if (nodeId === OTHERS_NODE_ID) return fail(403, 'NODE_002', '노드에 대한 권한이 없습니다.')

    const node = findNode(nodeId)
    return node ? ok(node) : fail(404, 'NODE_001', '노드를 찾을 수 없습니다.')
  }),
]
