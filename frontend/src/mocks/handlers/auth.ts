import { http } from 'msw'

import type { AuthTokens, UserProfile, WalletMessage } from '@/api/auth'
import { API_BASE_URL } from '@/api/client'
import { PROFILE } from '@/mocks/fixtures/user'
import { fail, ok } from '@/mocks/response'
import { scene } from '@/mocks/scenario'

/**
 * 인증 · 계정 mock. `api/auth.ts` 의 함수와 일대일로 맞춘다.
 *
 * 상태를 모듈에 들고 있어서 PATCH 결과가 다음 GET 에 보인다.
 * 새로고침하면 픽스처 초기값으로 돌아간다.
 */
let profile: UserProfile = { ...PROFILE }

/** 재발급은 항상 성공으로 둔다. 실패 경로는 `?mockFail=AUTH_003&mockFailStatus=401` 로 본다. */
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
    return issueTokens()
  }),

  /** 인터셉터가 401(AUTH_007) 을 받고 부른다. */
  http.post(`${API_BASE_URL}/auth/reissue`, () => issueTokens()),
  http.post(`${API_BASE_URL}/auth/logout`, async () => {
    const forced = await scene()
    if (forced) return forced
    return ok(null)
  }),
  http.get(`${API_BASE_URL}/users/me`, async () => {
    const forced = await scene()
    if (forced) return forced
    return ok<UserProfile>(profile)
  }),
  http.patch(`${API_BASE_URL}/users/me`, async ({ request }) => {
    const forced = await scene()
    if (forced) return forced

    const patch = (await request.json()) as { nickname?: string; introduction?: string }
    if (patch.nickname !== undefined && patch.nickname.trim() === '') {
      return fail(400, 'COMMON_001', '닉네임은 비워 둘 수 없습니다.')
    }

    profile = { ...profile, ...patch }
    return ok<UserProfile>(profile)
  }),

  /** 계정 복구 불가 고지 확인 */
  http.patch(`${API_BASE_URL}/users/me/onboarding`, async () => {
    const forced = await scene()
    if (forced) return forced

    profile = { ...profile, onboardingCompleted: true }
    return ok(null)
  }),
]
