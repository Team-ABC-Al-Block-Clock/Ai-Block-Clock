import { api } from '@/api/client'

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

/** 네트워크는 사용자가 고르는 값이 아니라 배포 설정이라 서버가 알려준다. */
export interface ConnectedWallet {
  provider: string
  address: string
  /** MVP 는 `POLYGON_AMOY` */
  network: string
  chainId: number
}

/** 지갑은 `wallet` 아래다 — 평평하게 읽으면 실서버에서만 undefined 가 된다. */
export interface UserProfile {
  userId: string
  nickname: string
  introduction: string | null
  profileImageUrl: string | null
  wallet: ConnectedWallet
  /** 로그인 분기와 `OnboardingGate` 가 본다 */
  onboardingCompleted: boolean
  createdAt: string
}

export const requestWalletMessage = (walletAddress: string) =>
  api.post<WalletMessage>('/auth/wallet/message', { walletAddress })

/** 서명을 검증하고 가입 또는 로그인한다. Refresh Token 은 HttpOnly 쿠키로 온다. */
export const loginWithWallet = (params: { walletAddress: string; signature: string }) =>
  api.post<AuthTokens>('/auth/wallet/login', params)

export const logout = () => api.post<void>('/auth/logout')

export const getMyProfile = () => api.get<UserProfile>('/users/me')

/** GET 과 달리 지갑·온보딩 여부가 없다 — 스토어를 통째로 덮으면 그 둘이 사라진다. */
export interface UpdatedProfile {
  userId: string
  nickname: string
  introduction: string | null
  profileImageUrl: string | null
  updatedAt: string
}

/** 화면 검증과 mock 이 같은 값을 본다. */
export const PROFILE_LIMITS = {
  nickname: { min: 2, max: 20 },
  introduction: { max: 40 },
} as const

/**
 * 보내지 않은 필드는 기존 값을 유지한다. 프로필 이미지가 같은 요청에 실려
 * multipart 라 Content-Type 을 지정하지 않는다 — axios 가 boundary 까지 붙인다.
 */
export const updateMyProfile = (params: { nickname?: string; introduction?: string }) => {
  const body = new FormData()
  if (params.nickname !== undefined) body.append('nickname', params.nickname)
  if (params.introduction !== undefined) body.append('introduction', params.introduction)
  return api.patch<UpdatedProfile>('/users/me', body)
}

/** 계정 복구 불가 고지 확인 */
export const completeOnboarding = () => api.patch<void>('/users/me/onboarding')
