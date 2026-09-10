import { http } from 'msw'

import type { CreateAgentRequest, CreatedAgent } from '@/api/agents'
import { API_BASE_URL } from '@/api/client'
import { addAgent, myAgents } from '@/mocks/fixtures/agents'
import { CREDIT_BALANCE, CREDIT_TRANSACTIONS } from '@/mocks/fixtures/user'
import { WORKFLOWS } from '@/mocks/fixtures/workflows'
import { cursorOk } from '@/mocks/cursor'
import { fail, ok } from '@/mocks/response'
import { scene } from '@/mocks/scenario'

/** 마이페이지 mock. 도메인이 셋이지만 화면 하나가 같이 쓰므로 묶어 둔다. */
export const mypageHandlers = [
  /** 내가 등록한 에이전트 */
  http.get(`${API_BASE_URL}/agents`, async ({ request }) => {
    const forced = await scene()
    if (forced) return forced

    return cursorOk(new URL(request.url), myAgents(), (a) => a.agentId)
  }),
  /**
   * 에이전트 등록. **명세 미확정** — 등록 화면이 넘기는 텍스트만 받는다.
   *
   * 즐겨찾기처럼 픽스처를 실제로 바꾼다. 안 바꾸면 등록 완료 화면의 「상세 페이지 보기」가
   * 존재하지 않는 ID 로 가고, 목록·마이페이지에도 안 나와 흐름을 확인할 수 없다.
   */
  http.post(`${API_BASE_URL}/agents`, async ({ request }) => {
    const forced = await scene()
    if (forced) return forced

    const body = (await request.json()) as Partial<CreateAgentRequest>
    // 이름과 한 줄 설명은 목록 카드가 반드시 그리는 값이다. 빈 값을 통과시키면
    // 목록에 이름 없는 카드가 생기고 검색에도 걸리지 않는다.
    if (!body.name?.trim() || !body.summary?.trim())
      return fail(400, 'COMMON_001', '입력값이 올바르지 않습니다.')

    const agent = addAgent({
      name: body.name.trim(),
      summary: body.summary.trim(),
      description: body.description?.trim() ?? '',
      usageExample: body.usageExample?.trim() ?? '',
      onboardingMessage: body.onboardingMessage?.trim() ?? '',
    })
    return ok<CreatedAgent>({ agentId: agent.agentId }, { status: 201 })
  }),
  http.get(`${API_BASE_URL}/workflows`, async ({ request }) => {
    const forced = await scene()
    if (forced) return forced

    return cursorOk(new URL(request.url), WORKFLOWS, (w) => w.workflowId)
  }),
  http.get(`${API_BASE_URL}/credits/balance`, async () => {
    const forced = await scene()
    if (forced) return forced
    return ok(CREDIT_BALANCE)
  }),
  http.get(`${API_BASE_URL}/credits/transactions`, async ({ request }) => {
    const forced = await scene()
    if (forced) return forced

    return cursorOk(new URL(request.url), CREDIT_TRANSACTIONS, (t) => t.transactionId)
  }),
]
