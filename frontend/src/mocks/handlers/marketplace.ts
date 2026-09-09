import { http } from 'msw'

import type { AgentDetail, AgentProvenance, AgentReview, AgentSummary } from '@/api/agents'
import { API_BASE_URL } from '@/api/client'
import { cursorPage, cursorParams } from '@/mocks/cursor'
import { AGENTS } from '@/mocks/fixtures/agents'
import { pageParams, paginate } from '@/mocks/paginate'
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

/**
 * 목록에는 상세 필드(`tokenUsage`)를 내려주지 않는다.
 *
 * 픽스처를 그대로 흘려보내면 실서버에 없는 필드를 화면이 몰래 쓰게 되고,
 * 백엔드를 붙이는 날 조용히 `undefined` 가 된다.
 */
function toSummary(agent: AgentDetail): AgentSummary {
  return {
    agentId: agent.agentId,
    name: agent.name,
    summary: agent.summary,
    iconUrl: agent.iconUrl,
    price: agent.price,
    currency: agent.currency,
    executionCount: agent.executionCount,
    averageRating: agent.averageRating,
    reviewCount: agent.reviewCount,
    favorited: agent.favorited,
    owner: { nickname: agent.owner.nickname },
    createdAt: agent.createdAt,
  }
}

/** 명세상 검색 대상은 이름과 한 줄 설명뿐이다. */
function matchKeyword(agent: AgentDetail, keyword: string) {
  const q = keyword.trim().toLowerCase()
  if (!q) return true
  return agent.name.toLowerCase().includes(q) || agent.summary.toLowerCase().includes(q)
}

/** 등록 시각 역순. 커서가 이 순서를 전제하므로 요청마다 같아야 한다.
 *  공개 범위 필터는 없다 — 등록 여부가 곧 공개 여부다(명세 참고사항). */
const PUBLISHED = [...AGENTS].sort((a, b) => b.createdAt.localeCompare(a.createdAt))

export const marketplaceHandlers = [
  http.get(`${API_BASE_URL}/marketplace/agents`, async ({ request }) => {
    const forced = await scene()
    if (forced) return forced

    const url = new URL(request.url)
    const { cursor, size } = cursorParams(url)
    if (size === null) return fail(400, 'COMMON_001', '조회 조건이 올바르지 않습니다.')

    const keyword = url.searchParams.get('keyword') ?? ''
    const filtered = PUBLISHED.filter((a) => matchKeyword(a, keyword))

    const page = cursorPage(filtered, (a) => a.agentId, cursor, size)
    if (!page)
      return fail(400, 'MARKET_003', '조회 기준이 만료되었습니다. 처음부터 다시 불러오세요.')

    return ok({ ...page, content: page.content.map(toSummary) })
  }),
  http.get(`${API_BASE_URL}/marketplace/agents/:agentId`, async ({ params }) => {
    const forced = await scene()
    if (forced) return forced

    const agent = AGENTS.find((a) => a.agentId === params.agentId)
    if (!agent) return fail(404, 'AGENT_001', '에이전트를 찾을 수 없습니다.')
    return ok(agent)
  }),
  http.get(`${API_BASE_URL}/marketplace/agents/:agentId/provenance`, async ({ params }) => {
    const forced = await scene()
    if (forced) return forced

    const agent = AGENTS.find((a) => a.agentId === params.agentId)
    if (!agent) return fail(404, 'AGENT_001', '에이전트를 찾을 수 없습니다.')

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

    const agent = AGENTS.find((a) => a.agentId === params.agentId)
    if (!agent) return fail(404, 'AGENT_001', '에이전트를 찾을 수 없습니다.')

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

    const url = new URL(request.url)
    const { page, size } = pageParams(url)
    return ok(paginate(reviews, page, size))
  }),
]
