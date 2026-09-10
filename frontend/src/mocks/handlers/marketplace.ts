import { http } from 'msw'

import type { AgentFavorite, AgentProvenance, AgentReview } from '@/api/agents'
import { API_BASE_URL } from '@/api/client'
import { cursorOk, cursorPage, cursorParams } from '@/mocks/cursor'
import { AGENTS, byNewest, toSummary } from '@/mocks/fixtures/agents'
import { fail, ok } from '@/mocks/response'
import { scene } from '@/mocks/scenario'

/**
 * 마켓플레이스 mock.
 *
 * 검색과 커서 페이지네이션을 핸들러 안에서 실제로 계산한다. 전체를 한 번에 돌려주면
 * 화면의 검색어·무한 스크롤이 아무 효과가 없어 검증이 안 된다.
 *
 * 필터와 정렬은 없다 — 카테고리·등급·공개 범위 필터는 명세에 두지 않고, 순서는
 * 등록 시각 역순 하나로 고정이다.
 */

/** 에이전트를 못 찾았을 때. 명세가 확정한 코드는 MARKET_001 하나다. */
const notFound = () => fail(404, 'MARKET_001', '에이전트를 찾을 수 없습니다.')

const find = (agentId: unknown) => AGENTS.find((a) => a.agentId === agentId)

/** 명세상 검색 대상은 이름과 한 줄 설명뿐이다. */
function matchKeyword(name: string, summary: string, keyword: string) {
  const q = keyword.trim().toLowerCase()
  if (!q) return true
  return name.toLowerCase().includes(q) || summary.toLowerCase().includes(q)
}

/**
 * 즐겨찾기를 픽스처에 반영한다.
 *
 * 이 파일의 다른 핸들러와 달리 상태를 바꾼다. 안 바꾸면 토글한 뒤 다시 조회하면
 * 원래대로 돌아가고, 멱등 처리도 확인할 수 없다.
 *
 * 멱등 — 이미 같은 상태면 횟수를 건드리지 않고 현재 상태를 준다(명세 참고사항).
 */
function setFavorite(agentId: unknown, next: boolean): AgentFavorite | null {
  const agent = find(agentId)
  if (!agent) return null
  if (agent.favorited !== next) {
    agent.favorited = next
    agent.favoriteCount += next ? 1 : -1
  }
  return { agentId: agent.agentId, favorited: agent.favorited, favoriteCount: agent.favoriteCount }
}

/**
 * 등록 시각 역순. 공개 범위 필터는 없다 — 등록 여부가 곧 공개 여부다(명세 참고사항).
 *
 * 상수가 아니라 함수다 — 등록(POST /agents)이 픽스처에 항목을 더하는데, 모듈 로드
 * 시점에 한 번 정렬해 두면 등록해도 목록에 안 나온다. 커서는 이 순서를 전제하지만
 * 정렬 기준(등록 시각)이 고정이라 요청마다 다시 계산해도 순서는 같다.
 */
const published = () => [...AGENTS].sort(byNewest)

export const marketplaceHandlers = [
  http.get(`${API_BASE_URL}/marketplace/agents`, async ({ request }) => {
    const forced = await scene()
    if (forced) return forced

    const url = new URL(request.url)
    const { cursor, size } = cursorParams(url)
    if (size === null) return fail(400, 'COMMON_001', '조회 조건이 올바르지 않습니다.')

    const keyword = url.searchParams.get('keyword') ?? ''
    const filtered = published().filter((a) => matchKeyword(a.name, a.summary, keyword))

    const page = cursorPage(filtered, (a) => a.agentId, cursor, size)
    if (!page)
      return fail(400, 'MARKET_003', '조회 기준이 만료되었습니다. 처음부터 다시 불러오세요.')

    return ok({ ...page, content: page.content.map(toSummary) })
  }),
  http.get(`${API_BASE_URL}/marketplace/agents/:agentId`, async ({ params }) => {
    const forced = await scene()
    if (forced) return forced

    const agent = find(params.agentId)
    return agent ? ok(agent) : notFound()
  }),
  /** 즐겨찾기 등록. 명세상 201 이다. */
  http.post(`${API_BASE_URL}/marketplace/agents/:agentId/favorites`, async ({ params }) => {
    const forced = await scene()
    if (forced) return forced

    const result = setFavorite(params.agentId, true)
    return result ? ok(result, { status: 201 }) : notFound()
  }),
  /** 즐겨찾기 해제 */
  http.delete(`${API_BASE_URL}/marketplace/agents/:agentId/favorites`, async ({ params }) => {
    const forced = await scene()
    if (forced) return forced

    const result = setFavorite(params.agentId, false)
    return result ? ok(result) : notFound()
  }),
  http.get(`${API_BASE_URL}/marketplace/agents/:agentId/provenance`, async ({ params }) => {
    const forced = await scene()
    if (forced) return forced

    const agent = find(params.agentId)
    if (!agent) return notFound()

    const txHash = `0x${agent.agentId.replace(/\D/g, '').padStart(6, '0').repeat(10).slice(0, 64)}`
    return ok<AgentProvenance>({
      txHash,
      network: 'Polygon Amoy',
      ownerAddress: '0x1f9c4b2e7a0d5c83916fe2b47c0a91d3e5b8072a',
      registeredAt: agent.createdAt,
      explorerUrl: `https://amoy.polygonscan.com/tx/${txHash}`,
    })
  }),
  http.get(`${API_BASE_URL}/marketplace/agents/:agentId/reviews`, async ({ request, params }) => {
    const forced = await scene()
    if (forced) return forced

    const agent = find(params.agentId)
    if (!agent) return notFound()

    const reviews: AgentReview[] = Array.from(
      { length: Math.min(agent.reviewCount, 24) },
      (_, i) => ({
        reviewId: `rev_${agent.agentId}_${String(i + 1).padStart(3, '0')}`,
        authorNickname: ['이싸피', '김싸피', '박싸피', '최싸피', '정싸피'][i % 5],
        rating: Math.max(1, Math.min(5, Math.round((agent.averageRating ?? 3) + ((i % 3) - 1)))),
        content: [
          '입력 형식만 맞추면 결과가 안정적입니다.',
          '가끔 표를 놓치는데 재실행하면 잘 나옵니다.',
          '설명대로 동작합니다. 단가도 납득할 만합니다.',
        ][i % 3],
        createdAt: `2026-09-0${(i % 8) + 1}T12:00:00+09:00`,
      }),
    )

    return cursorOk(new URL(request.url), reviews, (r) => r.reviewId)
  }),
]
