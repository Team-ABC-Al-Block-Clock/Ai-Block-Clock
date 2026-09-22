import { http } from 'msw'

import type { AgentProvenance, AgentReview } from '@/api/agents'
import { API_BASE_URL } from '@/api/client'
import { cursorOk, matchKeyword } from '@/mocks/cursor'
import { AGENTS, byNewest, provenanceOf, setFavorite, toSummary } from '@/mocks/fixtures/agents'
import { fail, ok } from '@/mocks/response'
import { scene } from '@/mocks/scenario'

/**
 * 검색과 커서를 핸들러 안에서 실제로 계산한다 — 전체를 한 번에 돌려주면 화면의
 * 검색어·무한 스크롤이 아무 효과가 없어 검증이 안 된다. 필터와 정렬은 없다.
 */

/** 못 찾았을 때의 코드는 MARKET_001 하나다. */
const notFound = () => fail(404, 'MARKET_001', '에이전트를 찾을 수 없습니다.')

/** 에이전트는 있는데 온체인 기록이 없을 때. */
const noProvenance = () =>
  fail(404, 'BLOCKCHAIN_003', '에이전트의 원본 증명 기록을 찾을 수 없습니다.')

const find = (agentId: unknown) => AGENTS.find((a) => a.agentId === agentId)

/**
 * 등록 시각 역순. 상수가 아니라 함수다 — 모듈 로드 시점에 정렬해 두면 새로 등록한
 * 것이 목록에 안 나온다. 기준이 고정이라 요청마다 계산해도 순서는 같다.
 */
const published = () => [...AGENTS].sort(byNewest)

export const marketplaceHandlers = [
  http.get(`${API_BASE_URL}/marketplace/agents`, async ({ request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const url = new URL(request.url)
    const keyword = url.searchParams.get('keyword') ?? ''
    // 투영을 페이지 계산 앞에 둔다. 상세 필드를 흘려보내면 실서버에 없는 필드를
    // 화면이 몰래 쓴다.
    const filtered = published()
      .filter((a) => matchKeyword(keyword, a.name, a.summary))
      .map(toSummary)

    return cursorOk(url, filtered, (a) => a.agentId, { cursorCode: 'MARKET_003' })
  }),
  http.get(`${API_BASE_URL}/marketplace/agents/:agentId`, async ({ params, request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const agent = find(params.agentId)
    return agent ? ok(agent) : notFound()
  }),
  /** 명세상 201 이다 */
  http.post(
    `${API_BASE_URL}/marketplace/agents/:agentId/favorites`,
    async ({ params, request }) => {
      const forced = await scene(request)
      if (forced) return forced

      const result = setFavorite(params.agentId, true)
      return result ? ok(result, { status: 201 }) : notFound()
    },
  ),
  /** 즐겨찾기 해제 */
  http.delete(
    `${API_BASE_URL}/marketplace/agents/:agentId/favorites`,
    async ({ params, request }) => {
      const forced = await scene(request)
      if (forced) return forced

      const result = setFavorite(params.agentId, false)
      return result ? ok(result) : notFound()
    },
  ),
  http.get(
    `${API_BASE_URL}/marketplace/agents/:agentId/provenance`,
    async ({ params, request }) => {
      const forced = await scene(request)
      if (forced) return forced

      const agent = find(params.agentId)
      if (!agent) return notFound()

      const provenance = provenanceOf(agent)
      return provenance ? ok<AgentProvenance>(provenance) : noProvenance()
    },
  ),
  http.get(`${API_BASE_URL}/marketplace/agents/:agentId/reviews`, async ({ request, params }) => {
    const forced = await scene(request)
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
