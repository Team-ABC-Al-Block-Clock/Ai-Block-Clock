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

/** 네트워크는 사용자가 고르는 값이 아니라 서버 배포 설정이다 — 서버가 알려준다(7절). */
export interface ConnectedWallet {
  provider: string
  address: string
  /** MVP 는 `POLYGON_AMOY` */
  network: string
  chainId: number
}

/** GET /users/me. 지갑은 `wallet` 아래다 — 평평하게 읽으면 실서버에서만 undefined 가 된다. */
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

/** GET /users/me — 프로필과 연결된 지갑. 명세 확정(7절) */
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

/** 명세가 정한 제약. 화면 검증과 mock 이 같은 값을 본다. */
export const PROFILE_LIMITS = {
  nickname: { min: 2, max: 20 },
  introduction: { max: 40 },
  /** 프로필 이미지. 화면이 고르는 즉시 막아 5MB 를 올렸다가 거절당하는 왕복을 없앤다. */
  image: { maxBytes: 5 * 1024 * 1024, types: ['image/png', 'image/jpeg'] },
} as const

/**
 * PATCH /users/me — 보내지 않은 필드는 기존 값을 유지한다.
 *
 * 명세가 `multipart/form-data` 를 요구한다(프로필 이미지가 같은 요청에 실린다).
 * Content-Type 은 지정하지 않는다 — axios 가 boundary 까지 붙여 준다(7절).
 *
 * 이미지는 교체(`profileImageFile`)와 삭제(`removeProfileImage`)가 갈린다. 파일을 **안 보내는
 * 것**은 "그대로 두라"는 뜻이라 삭제를 표현할 수 없어서 플래그가 따로 있다. 둘을 같이 보내면
 * 서버가 USER_007 로 거절하므로 화면에서도 한쪽만 정해 보낸다.
 */
export const updateMyProfile = (params: {
  nickname?: string
  introduction?: string
  profileImageFile?: File
  removeProfileImage?: boolean
}) => {
  const body = new FormData()
  if (params.nickname !== undefined) body.append('nickname', params.nickname)
  if (params.introduction !== undefined) body.append('introduction', params.introduction)
  if (params.profileImageFile) body.append('profileImageFile', params.profileImageFile)
  // FormData 는 값을 문자열로 만든다. mock 도 'true' 로 읽는다.
  if (params.removeProfileImage) body.append('removeProfileImage', 'true')
  return api.patch<UpdatedProfile>('/users/me', body)
}

/**
 * POST /users/me/signup — 프로필과 두 가지 동의를 한 번에 저장하고 가입을 마친다.
 *
 * 저장과 동의 기록이 서버에서 한 트랜잭션이라 화면이 호출 순서를 고민할 필요가 없다.
 * 나눠 부르면 첫 호출만 성공한 채로 끊길 수 있고, 그러면 이름만 있고 가입은 안 끝난
 * 계정이 남는다.
 *
 * 명세가 `multipart/form-data` 를 요구한다(프로필 이미지가 같은 요청에 실린다).
 * Content-Type 은 지정하지 않는다 — axios 가 boundary 까지 붙여 준다(7절).
 *
 * 동의 두 값은 필수다. 화면에서 버튼을 막는 것만으로는 동의를 받았다는 기록이
 * 어디에도 남지 않는다.
 *
 * 프로필 이미지는 선택이다. 안 보내면 서버가 `profileImageUrl` 을 null 로 준다.
 *
 * 응답은 `GET /users/me` 와 같은 `UserProfile` 이다 — 지갑도 객체로 온다. 따로 타입을 두면
 * 같은 것이 두 모양으로 갈린다.
 */
export const signup = (params: {
  nickname: string
  introduction?: string
  profileImageFile?: File
  termsAgreed: boolean
  recoveryNoticeConfirmed: boolean
}) => {
  const body = new FormData()
  body.append('nickname', params.nickname)
  // 선택 항목이라 비어 있으면 필드를 빼고 보낸다 — 명세가 보낸 것만 반영한다.
  if (params.introduction) body.append('introduction', params.introduction)
  // 선택 항목이다. 넣는 것을 빠뜨려도 타입은 통과하므로 `auth.test.ts` 가 파트를 직접 센다.
  if (params.profileImageFile) body.append('profileImageFile', params.profileImageFile)
  // FormData 는 값을 문자열로 만든다. 'true' / 'false' 로 나가는 것을 mock 도 같은 규칙으로 읽는다.
  body.append('termsAgreed', String(params.termsAgreed))
  body.append('recoveryNoticeConfirmed', String(params.recoveryNoticeConfirmed))
  return api.post<UserProfile>('/users/me/signup', body)
}
