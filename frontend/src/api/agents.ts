import { api } from '@/api/client'
import type { CursorPage, CursorParams } from '@/api/page'

/** 마켓 목록 항목. 내부 구조와 등급은 내려오지 않는다. */
export interface AgentSummary {
  agentId: string
  name: string
  /** 한 줄 설명 */
  summary: string
  iconUrl: string | null
  /** 실행 1회당 판매 가격. 소수점 오차를 막기 위해 문자열이다 — 숫자로 되돌리지 않는다. */
  price: string
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

/** 상세 화면의 스크린샷. 유형별 최대 5장 */
export interface AgentImage {
  imageId: string
  type: 'INPUT' | 'OUTPUT'
  url: string
  /** 같은 유형 안에서의 정렬 순서 */
  order: number
}

/**
 * 마켓 상세. `AgentSummary` 를 확장하지 않는다 — `owner` 모양이 다르고,
 * 상속으로 필드가 섞이면 목록이 무엇을 걸러야 하는지 흐려진다.
 */
export interface AgentDetail {
  agentId: string
  name: string
  /** 한 줄 설명 */
  summary: string
  /** 상세 설명 */
  description: string
  /** 실행 예시. 안 적었으면 null */
  usageExample: string | null
  iconUrl: string | null
  images: AgentImage[]
  /** 실행 1회당 판매 가격. 소수점 오차를 막기 위해 문자열이다 — 숫자로 되돌리지 않는다 */
  price: string
  executionCount: number
  /** 실행 성공률 0~1. 실행이 0회면 0 이므로 화면은 executionCount 로 가른다 */
  successRate: number
  /** 평점 평균. 리뷰가 없으면 null */
  averageRating: number | null
  reviewCount: number
  favorited: boolean
  favoriteCount: number
  owner: { userId: string; nickname: string; profileImageUrl: string | null }
  createdAt: string
}

/** 등록·해제가 같은 모양이다. 상세를 refetch 하지 않고 이 값으로 캐시를 덮는다. */
export interface AgentFavorite {
  agentId: string
  favorited: boolean
  favoriteCount: number
}

/**
 * 등록 화면이 모으는 텍스트만 담았다. 아이콘·스크린샷 업로드와 워크플로우 연결,
 * 가격 산정은 명세가 오면 붙인다.
 */
export interface CreateAgentRequest {
  name: string
  summary: string
  description: string
  usageExample: string
  onboardingMessage: string
}

/** 화면이 상세로 이동하는 데 필요한 것은 ID 뿐이다. */
export interface CreatedAgent {
  agentId: string
}

export const createAgent = (body: CreateAgentRequest) => api.post<CreatedAgent>('/agents', body)

/**
 * 판매 중인 내 에이전트. `AgentSummary` 를 확장하지 않는다 — 명세에 없는 평점·리뷰 수가
 * 딸려 들어오면 화면이 그것을 몰래 쓰고, 백엔드를 붙이는 날 `undefined` 가 된다.
 */
export interface MyAgent {
  agentId: string
  name: string
  /** 한 줄 설명 */
  summary: string
  iconUrl: string | null
  /** 실행 1회당 판매 가격(단가). 소수점 오차를 막기 위해 문자열이다 — 숫자로 되돌리지 않는다. */
  price: string
  /** 누적 수익. 단가와 같은 이유로 문자열이다 — 숫자로 되돌리지 않는다. */
  revenue: string
  /** 누적 실행 횟수 */
  executionCount: number
  /** 내가 즐겨찾기했는지 여부 */
  favorited: boolean
  /** 카드 스파크라인에 쓸 최근 7일 실행 횟수. 통계 API 를 따로 부르지 않게 함께 온다 */
  trend: number[]
  createdAt: string
}

/** 화면의 정렬 옵션은 이 유니온에서 파생한다. */
export type MyAgentSort = 'LATEST' | 'EXECUTIONS' | 'REVENUE'

export interface MyAgentsQuery extends CursorParams {
  /** 이름 또는 한 줄 설명 검색어 */
  keyword?: string
  /** 기본값 LATEST */
  sort?: MyAgentSort
}

/** 이 목록에 있다는 것이 곧 즐겨찾기했다는 뜻이라 `favorited` 가 없다. */
export interface FavoriteAgent {
  agentId: string
  name: string
  /** 한 줄 설명 */
  summary: string
  iconUrl: string | null
  /** 판매 가격(단가) */
  price: string
  /** 누적 실행 횟수 */
  executionCount: number
  owner: { nickname: string }
  /** 즐겨찾기한 시각. 이 값 역순이 목록 순서다 */
  favoritedAt: string
}

export interface UsedAgent {
  agentId: string
  name: string
  /** 한 줄 설명 */
  summary: string
  iconUrl: string | null
  /** 1회 실행 가격(단가) */
  price: string
  owner: { nickname: string }
  /** 요청자 본인이 실행한 횟수. 마켓 카드의 `executionCount`(전체 누적)와 다른 값이라 이름을 구분한다 */
  myExecutionCount: number
  /** 이 에이전트에 요청자가 쓴 누적 금액 */
  totalSpent: string
  /** 요청자가 마지막으로 실행한 시각 */
  lastExecutedAt: string
  /** 평점 평균. 리뷰가 없으면 null */
  averageRating: number | null
  reviewCount: number
  favorited: boolean
}

export type UsedAgentSort = 'LAST_EXECUTED' | 'MOST_EXECUTED'

export interface UsedAgentsQuery extends CursorParams {
  /** 이름 또는 한 줄 설명 검색어 */
  keyword?: string
  /** 기본값 LAST_EXECUTED */
  sort?: UsedAgentSort
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

/** 검색어뿐이다. 필터는 없고 순서는 등록 시각 역순 하나로 고정이다. */
export interface MarketplaceQuery extends CursorParams {
  /** 이름 또는 한 줄 설명 검색어 */
  keyword?: string
}

export const getMarketplaceAgents = (params: MarketplaceQuery = {}) =>
  api.get<CursorPage<AgentSummary>>('/marketplace/agents', { params })

export const getMarketplaceAgent = (agentId: string) =>
  api.get<AgentDetail>(`/marketplace/agents/${agentId}`)

/** 멱등이다 — 이미 즐겨찾기한 것을 다시 등록해도 오류가 아니고 현재 상태가 온다. */
export const addAgentFavorite = (agentId: string) =>
  api.post<AgentFavorite>(`/marketplace/agents/${agentId}/favorites`)

/** 멱등이다 — 즐겨찾기하지 않은 것을 해제해도 오류가 아니고 현재 상태가 온다. */
export const removeAgentFavorite = (agentId: string) =>
  api.delete<AgentFavorite>(`/marketplace/agents/${agentId}/favorites`)

export const getAgentProvenance = (agentId: string) =>
  api.get<AgentProvenance>(`/marketplace/agents/${agentId}/provenance`)

export const getAgentReviews = (agentId: string, params: CursorParams = {}) =>
  api.get<CursorPage<AgentReview>>(`/marketplace/agents/${agentId}/reviews`, { params })

/** 내가 등록해 판매 중인 에이전트 */
export const getMyAgents = (params: MyAgentsQuery = {}) =>
  api.get<CursorPage<MyAgent>>('/agents', { params })

/** 검색어도 정렬도 받지 않는다 — 즐겨찾기한 시각 역순 고정이다. */
export const getMyFavorites = (params: CursorParams = {}) =>
  api.get<CursorPage<FavoriteAgent>>('/users/me/favorites', { params })

/** 내가 한 번이라도 실행한 에이전트 */
export const getUsedAgents = (params: UsedAgentsQuery = {}) =>
  api.get<CursorPage<UsedAgent>>('/users/me/used-agents', { params })
