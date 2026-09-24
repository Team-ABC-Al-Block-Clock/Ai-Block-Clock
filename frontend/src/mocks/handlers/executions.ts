import { http } from 'msw'

import { API_BASE_URL } from '@/api/client'
import type { CostItem } from '@/api/executions'
import { CREDIT_BALANCE } from '@/mocks/fixtures/credits'
import { PLACEABLE_ITEMS } from '@/mocks/fixtures/nodePalette'
import { fail, ok } from '@/mocks/response'
import { scene } from '@/mocks/scenario'
import { findWorkflowDetail } from '@/mocks/handlers/workflows'

/**
 * 실행 도메인 mock. 견적은 저장된 워크플로우를 읽어 자리마다 팔레트 단가를 매긴다 —
 * 픽스처를 손으로 두면 캔버스를 고쳐도 금액이 그대로다.
 */

/** 금액은 서버와 같이 소수 여섯 자리 문자열로 맞춘다. */
const money = (value: number) => value.toFixed(6)

export const executionHandlers = [
  http.post(`${API_BASE_URL}/executions/cost-estimates`, async ({ request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const { workflowId } = (await request.json()) as { workflowId?: string }
    if (!workflowId) return fail(400, 'COMMON_001', '워크플로우를 지정해 주세요.')

    const detail = findWorkflowDetail(workflowId)
    if (!detail) return fail(404, 'WORKFLOW_002', '워크플로우를 찾을 수 없습니다.')

    const items: CostItem[] = detail.nodes.map((node) => {
      const item = PLACEABLE_ITEMS.find((it) => it.itemId === node.sourceId)
      return {
        nodeKey: node.nodeKey,
        sourceType: node.sourceType,
        name: item?.name ?? '알 수 없는 노드',
        price: money(Number(item?.price ?? 0)),
      }
    })

    const estimated = items.reduce((sum, item) => sum + Number(item.price), 0)
    const available = Number(CREDIT_BALANCE.availableAmount)
    const shortage = Math.max(0, estimated - available)

    return ok({
      estimatedAmount: money(estimated),
      availableAmount: money(available),
      shortageAmount: money(shortage),
      executable: shortage === 0,
      items,
    })
  }),
]
