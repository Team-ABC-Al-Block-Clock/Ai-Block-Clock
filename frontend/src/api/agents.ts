import { api } from '@/api/client'
import type { CursorPage, CursorParams, Page, PageParams } from '@/api/page'

/** 에이전트 · 마켓플레이스 API. */

/** Tier 1(Platinum) ~ Tier 4(Bronze).
 *  등급은 내부 자료라 목록 응답에 포함되지 않고 정렬 기준으로도 쓰이지 않는다.
 *  단가를 만드는 근거로만 남아 있다. */
export type Tier = 1 | 2 | 3 | 4

/** 단가 = Base Cost × (1 + Premium Rate) */
export const PREMIUM_RATE: Record<Tier, number> = { 1: 0.5, 2: 0.3, 3: 0.1, 4: 0 }

/** ⚠︎ 추정. 명세에 열거되어 있지 않아 화면 문안에서 추린 잠정값.
 *  목록에는 카테고리 필드도 필터도 없다 — 상세 화면에서만 쓴다. */
export type AgentCategory = '문서' | '데이터' | '개발' | '커뮤니케이션' | '리서치' | '금융'

/** ⚠︎ 추정. 등록 여부가 곧 공개 여부라 목록에는 공개 범위 필터가 없다. */
export type Visibility = 'PUBLIC' | 'PRIVATE'

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

/** ⚠︎ 상세 명세는 아직 오지 않았다. 목록에서 확정된 필드명을 그대로 물려받고,
 *  아래 추가 필드는 추정이다. */
export interface AgentDetail extends AgentSummary {
  category: AgentCategory
  tier: Tier
  baseCost: number
  tokenUsage: number
  premiumRate: number
  visibility: Visibility
  /** 0~1 */
  successRate: number
  nodeCount: number
  avgDurationSec: number
  /** 공개되는 것은 입출력 스키마뿐이다. 내부 노드 구성은 내려오지 않는다. */
  inputSchema: string
  outputSchema: string
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
