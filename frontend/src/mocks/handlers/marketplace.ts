import { http } from 'msw'

import type { AgentCategory, AgentDetail, AgentProvenance, AgentReview, Tier } from '@/api/agents'
import { API_BASE_URL } from '@/api/client'
import { AGENTS } from '@/mocks/fixtures/agents'
import { pageParams, paginate } from '@/mocks/paginate'
import { fail, ok } from '@/mocks/response'
import { scene } from '@/mocks/scenario'

/**
 * 마켓플레이스 mock.
 *
 * 검색·필터·정렬·페이지네이션을 핸들러 안에서 실제로 계산한다. 전체를 한 번에
 * 돌려주면 화면의 검색어·필터·페이지 이동이 아무 효과가 없어 검증이 안 된다.
 */

/** 태그가 아직 없어 카테고리로 대신한다. */
function matchKeyword(agent: AgentDetail, keyword: string) {
  const q = keyword.trim().toLowerCase()
  if (!q) return true
  return (
    agent.name.toLowerCase().includes(q) ||
    agent.description.toLowerCase().includes(q) ||
    agent.category.includes(q)
  )
}

/** 평가 0건은 평점순에서 뒤로 보낸다. */
function compare(sort: string) {
  return (a: AgentDetail, b: AgentDetail) => {
    switch (sort) {
      case 'LATEST':
        return b.createdAt.localeCompare(a.createdAt)
      case 'TIER':
        return a.tier - b.tier || b.runCount - a.runCount
      case 'RATING':
        return (b.rating ?? -1) - (a.rating ?? -1) || b.ratingCount - a.ratingCount
      default:
        // POPULAR
        return b.runCount - a.runCount
    }
  }
}

export const marketplaceHandlers = [
  http.get(`${API_BASE_URL}/marketplace/agents`, async ({ request }) => {
    const forced = await scene()
    if (forced) return forced

    const url = new URL(request.url)
    const keyword = url.searchParams.get('keyword') ?? ''
    const category = url.searchParams.get('category') as AgentCategory | null
    const tierRaw = url.searchParams.get('tier')
    const sort = url.searchParams.get('sort') ?? 'POPULAR'

    if (tierRaw && !['1', '2', '3', '4'].includes(tierRaw)) {
      return fail(400, 'COMMON_001', 'tier 는 1~4 만 가능합니다.')
    }
    const tier = tierRaw ? (Number(tierRaw) as Tier) : null

    const filtered = AGENTS.filter(
      (a) =>
        a.visibility === 'PUBLIC' &&
        matchKeyword(a, keyword) &&
        (!category || a.category === category) &&
        (!tier || a.tier === tier),
    ).sort(compare(sort))

    const { page, size } = pageParams(url)
    return ok(paginate(filtered, page, size))
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
      { length: Math.min(agent.ratingCount, 24) },
      (_, i) => ({
        reviewId: `rev_${agent.agentId}_${String(i + 1).padStart(3, '0')}`,
        authorNickname: ['이싸피', '김싸피', '박싸피', '최싸피', '정싸피'][i % 5],
        rating: Math.max(1, Math.min(5, Math.round((agent.rating ?? 3) + ((i % 3) - 1)))),
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
