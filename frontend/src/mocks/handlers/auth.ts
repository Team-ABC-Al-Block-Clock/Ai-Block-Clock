import { http } from 'msw'

import type { AuthTokens, UpdatedProfile, UserProfile, WalletMessage } from '@/api/auth'
import { PROFILE_LIMITS } from '@/api/auth'
import { API_BASE_URL } from '@/api/client'
import { AUTH_ERROR } from '@/api/errors'
import { PROFILE } from '@/mocks/fixtures/user'
import { fail, ok } from '@/mocks/response'
import { scene } from '@/mocks/scenario'

/**
 * 인증 · 계정 mock. `api/auth.ts` 의 함수와 일대일로 맞춘다.
 *
 * 상태를 모듈에 들고 있어서 PATCH 결과가 다음 GET 에 보인다.
 * 새로고침하면 픽스처 초기값으로 돌아간다 — 지갑 세션만 예외다(아래).
 */
let profile: UserProfile = { ...PROFILE }

/**
 * Refresh Token 쿠키 대역. mock 은 쿠키를 세팅하지 않아 localStorage 로 흉내 낸다 —
 * 그래야 새로고침 후 재발급으로 세션이 되살아나는 경로를 확인할 수 있다.
 */
const SESSION_KEY = 'mockWalletSession'

function readSession(): string | null {
  try {
    return localStorage.getItem(SESSION_KEY)
  } catch {
    return null
  }
}

function writeSession(walletAddress: string | null) {
  try {
    if (walletAddress) localStorage.setItem(SESSION_KEY, walletAddress)
    else localStorage.removeItem(SESSION_KEY)
  } catch {
    /* 접근 불가 환경 */
  }
}

function issueTokens() {
  return ok<AuthTokens>({
    accessToken: `mock-access-token.${Date.now()}`,
    tokenType: 'Bearer',
    expiresIn: 3600,
  })
}

export const authHandlers = [
  /** 서명할 nonce 발급 */
  http.post(`${API_BASE_URL}/auth/wallet/message`, async ({ request }) => {
    const forced = await scene()
    if (forced) return forced

    const { walletAddress } = (await request.json()) as { walletAddress?: string }
    if (!walletAddress) return fail(400, 'COMMON_001', '지갑 주소가 필요합니다.')

    const nonce = crypto.randomUUID()
    return ok<WalletMessage>({
      message: `ABC AI Block Clock 로그인에 서명합니다.\nnonce: ${nonce}`,
      nonce,
    })
  }),

  /** 서명 검증 후 가입 또는 로그인 */
  http.post(`${API_BASE_URL}/auth/wallet/login`, async ({ request }) => {
    const forced = await scene()
    if (forced) return forced

    const { walletAddress, signature } = (await request.json()) as {
      walletAddress?: string
      signature?: string
    }
    if (!walletAddress || !signature) return fail(400, 'COMMON_001', '입력값이 올바르지 않습니다.')

    writeSession(walletAddress)
    return issueTokens()
  }),

  /** 인터셉터가 401(AUTH_007) 을 받고, 부팅 시 `restoreSession` 이 부른다.
   *  세션이 없으면 실서버처럼 거절한다 — 늘 성공을 주면 로그인 없이도 인증된다.
   *
   *  여기만 `scene()` 을 건너뛴다. `?mockFail=` 로 화면의 실패 UI 를 볼 때 재발급까지
   *  실패하면 그 화면이 로그인으로 튕긴다. 재발급 실패는 세션을 지워서 만든다. */
  http.post(`${API_BASE_URL}/auth/reissue`, () => {
    if (!readSession()) return fail(401, AUTH_ERROR.REFRESH_MISSING, '로그인이 필요합니다.')
    return issueTokens()
  }),
  http.post(`${API_BASE_URL}/auth/logout`, async () => {
    const forced = await scene()
    if (forced) return forced

    writeSession(null)
    return ok(null)
  }),
  http.get(`${API_BASE_URL}/users/me`, async () => {
    const forced = await scene()
    if (forced) return forced

    const walletAddress = readSession()
    if (!walletAddress) return fail(401, AUTH_ERROR.MISSING, '인증이 필요합니다.')

    // 주소는 픽스처가 아니라 로그인한 세션에서 가져온다 — 픽스처 주소를 그대로
    // 돌려주면 어느 지갑으로 들어왔는지 화면에서 확인할 수 없다.
    return ok<UserProfile>({ ...profile, walletAddress })
  }),
  http.patch(`${API_BASE_URL}/users/me`, async ({ request }) => {
    const forced = await scene()
    if (forced) return forced

    // 명세가 multipart 다 — 프로필 이미지가 같은 요청에 실린다
    const form = await request.formData()
    const nickname = form.get('nickname')
    const introduction = form.get('introduction')

    const { nickname: nickLimit, introduction: introLimit } = PROFILE_LIMITS
    if (
      typeof nickname === 'string' &&
      (nickname.length < nickLimit.min || nickname.length > nickLimit.max)
    ) {
      return fail(400, 'USER_002', `이름은 ${nickLimit.min}~${nickLimit.max}자여야 합니다.`)
    }
    if (typeof introduction === 'string' && introduction.length > introLimit.max) {
      return fail(400, 'USER_002', `한 줄 소개는 ${introLimit.max}자를 넘을 수 없습니다.`)
    }

    // 보내지 않은 필드는 그대로 둔다
    if (typeof nickname === 'string') profile = { ...profile, nickname }
    if (typeof introduction === 'string') profile = { ...profile, introduction }

    return ok<UpdatedProfile>({
      userId: profile.userId,
      nickname: profile.nickname,
      introduction: profile.introduction,
      profileImageUrl: profile.profileImageUrl,
      updatedAt: new Date().toISOString(),
    })
  }),

  /** 계정 복구 불가 고지 확인 */
  http.patch(`${API_BASE_URL}/users/me/onboarding`, async () => {
    const forced = await scene()
    if (forced) return forced

    profile = { ...profile, onboardingCompleted: true }
    return ok(null)
  }),
]
