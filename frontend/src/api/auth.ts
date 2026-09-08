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

/** POST /auth/wallet/message — 서명할 메시지를 발급받는다. */
export const requestWalletMessage = (walletAddress: string) =>
  api.post<WalletMessage>('/auth/wallet/message', { walletAddress })

/**
 * POST /auth/wallet/login — 서명을 검증하고 가입 또는 로그인한다.
 * Refresh Token 은 응답 바디가 아니라 HttpOnly 쿠키로 내려온다.
 */
export const loginWithWallet = (params: { walletAddress: string; signature: string }) =>
  api.post<AuthTokens>('/auth/wallet/login', params)

/** POST /auth/logout */
export const logout = () => api.post<void>('/auth/logout')

/** GET /users/me */
export const getMyProfile = () => api.get<UserProfile>('/users/me')

/** PATCH /users/me */
export const updateMyProfile = (params: { nickname?: string; introduction?: string }) =>
  api.patch<UserProfile>('/users/me', params)

/** PATCH /users/me/onboarding — 계정 복구 불가 고지 확인 (ACC-005) */
export const completeOnboarding = () => api.patch<void>('/users/me/onboarding')
