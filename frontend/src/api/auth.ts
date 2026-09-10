import { api } from '@/api/client'

/**
 * 인증 · 계정 API.
 *
 * 도메인 모듈은 엔드포인트 하나당 함수 하나를 export 한다.
 * 화면에서 URL 문자열을 쓰지 않고 이 함수를 통해서만 호출한다.
 * 경로가 바뀌어도 고칠 곳이 여기 하나다.
 */

export interface WalletMessage {
  /** 지갑으로 서명할 원문 */
  message: string
  /** 재사용 공격을 막는 일회용 값 */
  nonce: string
}

export interface AuthTokens {
  accessToken: string
  tokenType: string
  expiresIn: number
}

export interface UserProfile {
  userId: string
  nickname: string
  walletAddress: string
  introduction: string | null
  profileImageUrl: string | null
  onboardingCompleted: boolean
}

/** 서명할 메시지를 발급받는다. */
export const requestWalletMessage = (walletAddress: string) =>
  api.post<WalletMessage>('/auth/wallet/message', { walletAddress })

/**
 * POST /auth/wallet/login — 서명을 검증하고 가입 또는 로그인한다.
 * Refresh Token 은 응답 바디가 아니라 HttpOnly 쿠키로 내려온다.
 */

export const loginWithWallet = (params: { walletAddress: string; signature: string }) =>
  api.post<AuthTokens>('/auth/wallet/login', params)

export const logout = () => api.post<void>('/auth/logout')

export const getMyProfile = () => api.get<UserProfile>('/users/me')

/** PATCH /users/me 응답. GET 과 달리 지갑 주소·온보딩 여부가 없다 —
 *  스토어에 넣을 때 통째로 덮으면 그 두 값이 사라진다. */
export interface UpdatedProfile {
  userId: string
  nickname: string
  introduction: string | null
  profileImageUrl: string | null
  updatedAt: string
}

/** 명세가 정한 길이. 화면 검증과 mock 이 같은 값을 본다. */
export const PROFILE_LIMITS = {
  nickname: { min: 2, max: 20 },
  introduction: { max: 40 },
} as const

/**
 * PATCH /users/me — 보내지 않은 필드는 기존 값을 유지한다.
 *
 * 명세가 `multipart/form-data` 를 요구한다(프로필 이미지가 같은 요청에 실린다).
 * Content-Type 은 지정하지 않는다 — axios 가 boundary 까지 붙여 준다(7절).
 */
export const updateMyProfile = (params: { nickname?: string; introduction?: string }) => {
  const body = new FormData()
  if (params.nickname !== undefined) body.append('nickname', params.nickname)
  if (params.introduction !== undefined) body.append('introduction', params.introduction)
  return api.patch<UpdatedProfile>('/users/me', body)
}

/** 계정 복구 불가 고지 확인 (ACC-005) */
export const completeOnboarding = () => api.patch<void>('/users/me/onboarding')
