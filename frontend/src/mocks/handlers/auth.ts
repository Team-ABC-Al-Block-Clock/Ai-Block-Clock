import { http } from 'msw'

import type { AuthTokens, UpdatedProfile, UserProfile, WalletMessage } from '@/api/auth'
import { PROFILE_LIMITS } from '@/api/auth'
import { API_BASE_URL } from '@/api/client'
import { AUTH_ERROR } from '@/api/errors'
import { PROFILE } from '@/mocks/fixtures/user'
import { fail, ok } from '@/mocks/response'
import { scene } from '@/mocks/scenario'

/**
 * 지갑 세션과 프로필은 localStorage 에 둔다 — 모듈 변수로 들고 있으면 새로고침마다
 * 픽스처로 돌아가서 "저장했는데 안 남는다" 로 보인다.
 */
const PROFILE_KEY = 'mockProfile'
/** 처음 보는 주소는 온보딩 전으로 취급해 가입 화면을 태운다. */
const KNOWN_WALLETS_KEY = 'mockKnownWallets'

/** 픽스처 위에 덮는다 — 필드가 늘어나도 옛 저장값이 깨지지 않는다. */
function readProfile(): UserProfile {
  try {
    const raw = localStorage.getItem(PROFILE_KEY)
    return raw ? { ...PROFILE, ...(JSON.parse(raw) as Partial<UserProfile>) } : { ...PROFILE }
  } catch {
    return { ...PROFILE }
  }
}

function readKnownWallets(): string[] {
  try {
    const raw = localStorage.getItem(KNOWN_WALLETS_KEY)
    return raw ? (JSON.parse(raw) as string[]) : []
  } catch {
    return []
  }
}

function rememberWallet(address: string): void {
  try {
    const known = readKnownWallets()
    if (!known.includes(address)) {
      localStorage.setItem(KNOWN_WALLETS_KEY, JSON.stringify([...known, address]))
    }
  } catch {
    /* 접근 불가 환경 */
  }
}

function writeProfile(next: UserProfile): void {
  try {
    localStorage.setItem(PROFILE_KEY, JSON.stringify(next))
  } catch {
    /* 접근 불가 환경 — 이번 세션에서만 값이 살아 있다 */
  }
}

/** 쿠키를 세팅하지 않아 localStorage 로 흉내 낸다 — 새로고침 후 재발급을 확인해야 한다. */
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
      message: `ABC AI Block Clock 로그인\nnonce: ${nonce}`,
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

    // 실제 검증은 서버가 주소를 복원해서 한다. mock 은 모양만 본다 —
    // 그래야 자리표시자 서명으로 로그인되던 예전 동작이 되살아나지 않는다.
    if (!/^0x[0-9a-fA-F]{130}$/.test(signature)) {
      return fail(400, 'COMMON_001', '서명이 올바르지 않습니다.')
    }

    // 처음 보는 지갑이면 온보딩 전으로 되돌려 가입 화면 분기를 확인할 수 있게 한다.
    if (!readKnownWallets().includes(walletAddress)) {
      writeProfile({ ...readProfile(), onboardingCompleted: false })
    }
    rememberWallet(walletAddress)

    writeSession(walletAddress)
    return issueTokens()
  }),

  /**
   * 여기만 `scene()` 을 건너뛴다 — `?mockFail=` 로 실패 UI 를 볼 때 재발급까지 실패하면
   * 그 화면이 로그인으로 튕긴다. 재발급 실패는 세션을 지워서 만든다.
   */
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

    // 주소는 세션에서 가져온다 — 픽스처 주소를 돌려주면 어느 지갑으로 들어왔는지 모른다.
    const stored = readProfile()
    return ok<UserProfile>({ ...stored, wallet: { ...stored.wallet, address: walletAddress } })
  }),
  http.patch(`${API_BASE_URL}/users/me`, async ({ request }) => {
    const forced = await scene()
    if (forced) return forced

    // 프로필 이미지가 같은 요청에 실려 multipart 다
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
    let next = readProfile()
    if (typeof nickname === 'string') next = { ...next, nickname }
    if (typeof introduction === 'string') next = { ...next, introduction }
    writeProfile(next)

    return ok<UpdatedProfile>({
      userId: next.userId,
      nickname: next.nickname,
      introduction: next.introduction,
      profileImageUrl: next.profileImageUrl,
      updatedAt: new Date().toISOString(),
    })
  }),

  /** 계정 복구 불가 고지 확인 */
  http.patch(`${API_BASE_URL}/users/me/onboarding`, async () => {
    const forced = await scene()
    if (forced) return forced

    writeProfile({ ...readProfile(), onboardingCompleted: true })
    return ok(null)
  }),
]
