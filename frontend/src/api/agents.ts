import { api } from '@/api/client'
import type { Page, PageParams } from '@/api/page'

/** 에이전트 · 마켓플레이스 API. 필드명은 추정이다 — `api/page.ts` 참고. */

/** Tier 1(Platinum) ~ Tier 4(Bronze) */
export type Tier = 1 | 2 | 3 | 4

/** 단가 = Base Cost × (1 + Premium Rate) */
export const PREMIUM_RATE: Record<Tier, number> = { 1: 0.5, 2: 0.3, 3: 0.1, 4: 0 }

/** 명세에 열거되어 있지 않아 화면 문안에서 추린 잠정값. */
export type AgentCategory = '문서' | '데이터' | '개발' | '커뮤니케이션' | '리서치' | '금융'

export type Visibility = 'PUBLIC' | 'PRIVATE'

export interface AgentSummary {
  agentId: string
  name: string
  description: string
  category: AgentCategory
  tier: Tier
  /** 베이지안 보정 평점 0~5. 평가가 없으면 null */
  rating: number | null
  ratingCount: number
  runCount: number
  /** 0~1 */
  successRate: number
  /** 1회 호출 단가(ABC) */
  unitPrice: number
  favoriteCount: number
  ownerNickname: string
  createdAt: string
}

export interface AgentDetail extends AgentSummary {
  baseCost: number
  tokenUsage: number
  premiumRate: number
  visibility: Visibility
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

export interface MarketplaceQuery extends PageParams {
  keyword?: string
  category?: AgentCategory
  tier?: Tier
  sort?: 'POPULAR' | 'LATEST' | 'TIER' | 'RATING'
}

export const getMarketplaceAgents = (params: MarketplaceQuery = {}) =>
  api.get<Page<AgentSummary>>('/marketplace/agents', { params })

export const getMarketplaceAgent = (agentId: string) =>
  api.get<AgentDetail>(`/marketplace/agents/${agentId}`)

export const getAgentProvenance = (agentId: string) =>
  api.get<AgentProvenance>(`/marketplace/agents/${agentId}/provenance`)

export const getAgentReviews = (agentId: string, params: PageParams = {}) =>
  api.get<Page<AgentReview>>(`/marketplace/agents/${agentId}/reviews`, { params })

/** 내가 등록한 에이전트 */
export const getMyAgents = (params: PageParams = {}) =>
  api.get<Page<AgentDetail>>('/agents', { params })
