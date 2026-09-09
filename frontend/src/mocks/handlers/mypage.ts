import { http } from 'msw'

import { API_BASE_URL } from '@/api/client'
import { MY_AGENTS } from '@/mocks/fixtures/agents'
import { CREDIT_BALANCE, CREDIT_TRANSACTIONS } from '@/mocks/fixtures/user'
import { WORKFLOWS } from '@/mocks/fixtures/workflows'
import { cursorOk } from '@/mocks/cursor'
import { ok } from '@/mocks/response'
import { scene } from '@/mocks/scenario'

/** 마이페이지 mock. 도메인이 셋이지만 화면 하나가 같이 쓰므로 묶어 둔다. */
export const mypageHandlers = [
  /** 내가 등록한 에이전트 */
  http.get(`${API_BASE_URL}/agents`, async ({ request }) => {
    const forced = await scene()
    if (forced) return forced

    return cursorOk(new URL(request.url), MY_AGENTS, (a) => a.agentId)
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
