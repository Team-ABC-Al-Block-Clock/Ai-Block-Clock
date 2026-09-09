import { api } from '@/api/client'
import type { CursorPage, CursorParams, Page, PageParams } from '@/api/page'

/** 에이전트 · 마켓플레이스 API. */

/**
 * 마켓 목록 항목. **확정** — GET /marketplace/agents 명세와 1:1 이다.
 *
 * 내부 구조(노드 구성·연결 관계·프롬프트)와 등급은 내려오지 않는다.
 */
export interface AgentSummary {
  agentId: string
  name: string
  /** 한 줄 설명 */
  summary: string
  iconUrl: string | null
  /** 실행 1회당 판매 가격. 소수점 오차를 막기 위해 문자열이다 — 숫자로 되돌리지 않는다. */
  price: string
  /** 사용자에게 노출하는 단위는 'ABC' */
  currency: string
  /** 누적 실행 횟수 */
  executionCount: number
  /** 평점 평균. 리뷰가 없으면 null */
  averageRating: number | null
  reviewCount: number
  /** 로그인한 사용자가 즐겨찾기했는지. 미로그인이면 항상 false */
  favorited: boolean
  owner: { nickname: string }
  createdAt: string
}

/** ⚠︎ 상세 명세는 아직 오지 않았다. 목록에서 확정된 필드명을 물려받고, 화면이 실제로
 *  읽는 것만 둔다 — 추측으로 필드를 늘려두면 명세가 왔을 때 무엇이 사실인지 알 수 없다.
 *  `tokenUsage` 는 마이페이지의 "내 에이전트"(GET /agents)가 쓴다. */
export interface AgentDetail extends AgentSummary {
  tokenUsage: number
}

export interface AgentProvenance {
  txHash: string
  network: string
  ownerAddress: string
  registeredAt: string
  explorerUrl: string
}

export interface AgentReview {
  reviewId: string
  authorNickname: string
  rating: number
  content: string
  createdAt: string
}

/**
 * 목록 조회 조건. 검색어뿐이다.
 *
 * 카테고리·등급·공개 범위 필터는 명세에 없고(화면에 표시할 자리가 없고, 등급은 내부 자료,
 * 등록 여부가 곧 공개 여부다), 정렬도 두지 않는다 — 등록 시각 역순 하나로 고정이다.
 */
export interface MarketplaceQuery extends CursorParams {
  /** 이름 또는 한 줄 설명 검색어 */
  keyword?: string
}

export const getMarketplaceAgents = (params: MarketplaceQuery = {}) =>
  api.get<CursorPage<AgentSummary>>('/marketplace/agents', { params })

export const getMarketplaceAgent = (agentId: string) =>
  api.get<AgentDetail>(`/marketplace/agents/${agentId}`)

export const getAgentProvenance = (agentId: string) =>
  api.get<AgentProvenance>(`/marketplace/agents/${agentId}/provenance`)

export const getAgentReviews = (agentId: string, params: PageParams = {}) =>
  api.get<Page<AgentReview>>(`/marketplace/agents/${agentId}/reviews`, { params })

/** 내가 등록한 에이전트 */
export const getMyAgents = (params: PageParams = {}) =>
  api.get<Page<AgentDetail>>('/agents', { params })
