import { http } from 'msw'

import type { CreateAgentRequest, CreatedAgent, MyAgentSort, UsedAgentSort } from '@/api/agents'
import { API_BASE_URL } from '@/api/client'
import type { DeletedWorkflow, WorkflowSort } from '@/api/workflows'
import { cursorOk, matchKeyword } from '@/mocks/cursor'
import { addAgent, byNewest, favoriteAgents, myAgents, usedAgents } from '@/mocks/fixtures/agents'
import { CREDIT_BALANCE, CREDIT_TRANSACTIONS } from '@/mocks/fixtures/user'
import { WORKFLOWS, toWorkflowSummary } from '@/mocks/fixtures/workflows'
import { fail, ok } from '@/mocks/response'
import { scene } from '@/mocks/scenario'

/** 도메인이 셋이지만 화면 하나가 같이 쓰므로 묶어 둔다. */

/** 마켓 목록(9)과 다르다. */
const PAGE_SIZE = 10

const invalidQuery = () => fail(400, 'COMMON_001', '조회 조건이 올바르지 않습니다.')

/**
 * 없으면 기본값, 있으면 유효해야 한다. 조용히 기본값으로 떨어뜨리면 화면이 잘못된 값을
 * 보내도 mock 은 통과해 실서버에 붙이는 날에만 400 이 난다.
 */
function readSort<T extends string>(url: URL, allowed: readonly T[], fallback: T): T | null {
  const raw = url.searchParams.get('sort')
  if (raw === null || raw === '') return fallback
  return (allowed as readonly string[]).includes(raw) ? (raw as T) : null
}

const keywordOf = (url: URL) => url.searchParams.get('keyword') ?? ''

/**
 * 마지막 실행 역순. 아직 실행하지 않은 것은 뒤로 밀린다. 문자열 비교로는 안 된다 —
 * 픽스처는 `+09:00`, 새로 만든 것은 `Z` 라 날짜가 같으면 순서가 뒤집힌다.
 */
function byLastExecuted(
  a: { lastExecutedAt: string | null },
  b: { lastExecutedAt: string | null },
) {
  if (a.lastExecutedAt === null) return b.lastExecutedAt === null ? 0 : 1
  if (b.lastExecutedAt === null) return -1
  return Date.parse(b.lastExecutedAt) - Date.parse(a.lastExecutedAt)
}

const MY_AGENT_SORTS: readonly MyAgentSort[] = ['LATEST', 'EXECUTIONS', 'REVENUE']
const WORKFLOW_SORTS: readonly WorkflowSort[] = ['LAST_EXECUTED', 'LATEST']
const USED_AGENT_SORTS: readonly UsedAgentSort[] = ['LAST_EXECUTED', 'MOST_EXECUTED']

export const mypageHandlers = [
  /** 내 에이전트 목록 (판매 중) */
  http.get(`${API_BASE_URL}/agents`, async ({ request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const url = new URL(request.url)
    const sort = readSort(url, MY_AGENT_SORTS, 'LATEST')
    if (sort === null) return invalidQuery()

    const keyword = keywordOf(url)
    const list = myAgents().filter((a) => matchKeyword(keyword, a.name, a.summary))
    // 수익은 문자열이다. 정렬만 하므로 여기서만 숫자로 읽는다.
    if (sort === 'EXECUTIONS') list.sort((a, b) => b.executionCount - a.executionCount)
    else if (sort === 'REVENUE') list.sort((a, b) => Number(b.revenue) - Number(a.revenue))
    else list.sort(byNewest)

    return cursorOk(url, list, (a) => a.agentId, { defaultSize: PAGE_SIZE })
  }),
  /**
   * 픽스처를 실제로 바꾼다 — 안 바꾸면 등록 완료 화면의 「상세 페이지 보기」가
   * 없는 ID 로 가고 목록에도 안 나와 흐름을 확인할 수 없다.
   */
  http.post(`${API_BASE_URL}/agents`, async ({ request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const body = (await request.json()) as Partial<CreateAgentRequest>
    // 빈 값을 통과시키면 목록에 이름 없는 카드가 생기고 검색에도 안 걸린다.
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
  /** 검색어도 정렬도 없다. 즐겨찾기한 시각 역순 고정. */
  http.get(`${API_BASE_URL}/users/me/favorites`, async ({ request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const url = new URL(request.url)
    const list = favoriteAgents().sort(
      (a, b) => Date.parse(b.favoritedAt) - Date.parse(a.favoritedAt),
    )

    return cursorOk(url, list, (a) => a.agentId, {
      defaultSize: PAGE_SIZE,
      cursorCode: 'MARKET_003',
    })
  }),
  /** 사용한 에이전트 목록 */
  http.get(`${API_BASE_URL}/users/me/used-agents`, async ({ request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const url = new URL(request.url)
    const sort = readSort(url, USED_AGENT_SORTS, 'LAST_EXECUTED')
    if (sort === null) return invalidQuery()

    const keyword = keywordOf(url)
    const list = usedAgents().filter((a) => matchKeyword(keyword, a.name, a.summary))
    if (sort === 'MOST_EXECUTED') list.sort((a, b) => b.myExecutionCount - a.myExecutionCount)
    else list.sort(byLastExecuted)

    return cursorOk(url, list, (a) => a.agentId, {
      defaultSize: PAGE_SIZE,
      cursorCode: 'MARKET_003',
    })
  }),
  /** 내 워크플로우 목록 */
  http.get(`${API_BASE_URL}/workflows`, async ({ request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const url = new URL(request.url)
    const sort = readSort(url, WORKFLOW_SORTS, 'LAST_EXECUTED')
    if (sort === null) return invalidQuery()

    // 워크플로우는 이름만 검색한다 — 에이전트 목록과 다르다.
    const keyword = keywordOf(url)
    const list = WORKFLOWS.filter((w) => matchKeyword(keyword, w.name))
    if (sort === 'LATEST') list.sort(byNewest)
    else list.sort(byLastExecuted)

    return cursorOk(url, list.map(toWorkflowSummary), (w) => w.workflowId, {
      defaultSize: PAGE_SIZE,
    })
  }),
  /**
   * 픽스처에서 실제로 지운다 — 안 지우면 모달을 닫아도 목록이 그대로다.
   * 소유자 아님(WORKFLOW_003)은 만들지 않는다. 인증 연동 전이라 흉내 낼 근거가 없다.
   */
  http.delete(`${API_BASE_URL}/workflows/:workflowId`, async ({ params, request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const at = WORKFLOWS.findIndex((w) => w.workflowId === params.workflowId)
    if (at < 0) return fail(404, 'WORKFLOW_002', '워크플로우를 찾을 수 없습니다.')

    const workflow = WORKFLOWS[at]
    // 거절되는 두 건은 지우지 않는다 — 화면의 코드별 문구가 여기서만 드러난다.
    if (workflow.registeredAsAgent)
      return fail(409, 'WORKFLOW_009', '에이전트로 등록된 워크플로우는 삭제할 수 없습니다.')
    if (workflow.running)
      return fail(409, 'WORKFLOW_010', '실행이 진행 중인 워크플로우는 삭제할 수 없습니다.')

    WORKFLOWS.splice(at, 1)
    return ok<DeletedWorkflow>({
      workflowId: workflow.workflowId,
      deletedAt: new Date().toISOString(),
    })
  }),
  http.get(`${API_BASE_URL}/credits/balance`, async ({ request }) => {
    const forced = await scene(request)
    if (forced) return forced
    return ok(CREDIT_BALANCE)
  }),
  http.get(`${API_BASE_URL}/credits/transactions`, async ({ request }) => {
    const forced = await scene(request)
    if (forced) return forced

    return cursorOk(new URL(request.url), CREDIT_TRANSACTIONS, (t) => t.transactionId)
  }),
]
