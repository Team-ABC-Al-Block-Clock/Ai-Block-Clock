import { api } from '@/api/client'
import type { CursorPage, CursorParams } from '@/api/page'

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

/** 상세 화면의 스크린샷. 입력·출력 구분이 필요해 `type` 을 같이 준다. 유형별 최대 5장. */
export interface AgentImage {
  imageId: string
  type: 'INPUT' | 'OUTPUT'
  url: string
  /** 같은 유형 안에서의 정렬 순서 */
  order: number
}

/**
 * 마켓 상세. **확정** — GET /marketplace/agents/{agentId} 명세와 1:1 이다.
 *
 * 노드 개수·이름·연결 관계·프롬프트·AI 모델과 등급·티어는 내려오지 않는다.
 * 평균 실행 시간도 집계하지 않고, 등록이 곧 공개라 visibility 도 없다.
 *
 * `AgentSummary` 를 확장하지 않는다 — `owner` 모양이 다르고(userId·profileImageUrl),
 * 상속으로 필드가 섞이면 목록 응답이 무엇을 걸러야 하는지 흐려진다.
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
  /** 실행 1회당 판매 가격. 소수점 오차를 막기 위해 문자열이다 — 숫자로 되돌리지 않는다. */
  price: string
  currency: string
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

/**
 * 즐겨찾기 등록·해제 응답. **확정** — 두 응답이 같은 모양이다.
 *
 * 명세 「화면이 다시 조회하지 않게 갱신된 횟수를 함께 반환한다」 —
 * 상세를 refetch 하지 않고 이 값으로 캐시를 덮는다.
 */
export interface AgentFavorite {
  agentId: string
  favorited: boolean
  favoriteCount: number
}

/** 내가 등록한 에이전트. GET /agents — **추정** (명세 미확정) */
export interface MyAgent extends AgentSummary {
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

/** 내가 등록한 에이전트 */
export const getMyAgents = (params: CursorParams = {}) =>
  api.get<CursorPage<MyAgent>>('/agents', { params })
