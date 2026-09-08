import { http } from 'msw'

import type { AuthTokens, UserProfile, WalletMessage } from '@/api/auth'
import { API_BASE_URL } from '@/api/client'
import { fail, ok } from '@/mocks/response'

/**
 * 인증 · 계정 mock. `api/auth.ts` 의 함수와 일대일로 맞춘다.
 *
 * 상태를 모듈에 들고 있어서 PATCH 결과가 다음 GET 에 보인다.
 * 새로고침하면 초기값으로 돌아간다 — mock 이므로 그걸로 충분하다.
 */
let profile: UserProfile = {
  userId: 'u_1001',
  nickname: '이싸피',
  walletAddress: '0x1f9c4b2e7a0d5c83916fe2b47c0a91d3e5b8072a',
  introduction: '온체인 리서치 에이전트를 만듭니다.',
  profileImageUrl: null,
  onboardingCompleted: true,
}

/** Refresh Token 은 HttpOnly 쿠키라 JS 가 만들 수 없다.
 *  mock 에서는 재발급이 항상 성공하는 것으로 둔다 — 실패 경로를 확인하려면
 *  이 함수 대신 `fail(401, 'AUTH_003', …)` 을 돌려주면 된다. */
function issueTokens() {
  return ok<AuthTokens>({
    accessToken: `mock-access-token.${Date.now()}`,
    tokenType: 'Bearer',
    expiresIn: 3600,
  })
}

export const authHandlers = [
  /** POST /auth/wallet/message */
  http.post(`${API_BASE_URL}/auth/wallet/message`, async ({ request }) => {
    const { walletAddress } = (await request.json()) as { walletAddress?: string }
    if (!walletAddress) return fail(400, 'COMMON_001', '지갑 주소가 필요합니다.')

    const nonce = crypto.randomUUID()
    return ok<WalletMessage>({
      message: `ABC AI Block Clock 로그인에 서명합니다.\nnonce: ${nonce}`,
      nonce,
    })
  }),

  /** POST /auth/wallet/login */
  http.post(`${API_BASE_URL}/auth/wallet/login`, async ({ request }) => {
    const { walletAddress, signature } = (await request.json()) as {
      walletAddress?: string
      signature?: string
    }
    if (!walletAddress || !signature) return fail(400, 'COMMON_001', '입력값이 올바르지 않습니다.')
    return issueTokens()
  }),

  /** POST /auth/reissue — 인터셉터가 401(AUTH_007) 을 받고 부른다. */
  http.post(`${API_BASE_URL}/auth/reissue`, () => issueTokens()),

  /** POST /auth/logout */
  http.post(`${API_BASE_URL}/auth/logout`, () => ok(null)),

  /** GET /users/me */
  http.get(`${API_BASE_URL}/users/me`, () => ok<UserProfile>(profile)),

  /** PATCH /users/me */
  http.patch(`${API_BASE_URL}/users/me`, async ({ request }) => {
    const patch = (await request.json()) as { nickname?: string; introduction?: string }
    if (patch.nickname !== undefined && patch.nickname.trim() === '') {
      return fail(400, 'COMMON_001', '닉네임은 비워 둘 수 없습니다.')
    }

    profile = { ...profile, ...patch }
    return ok<UserProfile>(profile)
  }),

  /** PATCH /users/me/onboarding — 계정 복구 불가 고지 확인 (ACC-005) */
  http.patch(`${API_BASE_URL}/users/me/onboarding`, () => {
    profile = { ...profile, onboardingCompleted: true }
    return ok(null)
  }),
]
