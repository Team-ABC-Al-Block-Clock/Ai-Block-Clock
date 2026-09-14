import { http } from 'msw'

import type {
  AuthTokens,
  SignupResult,
  UpdatedProfile,
  UserProfile,
  WalletMessage,
} from '@/api/auth'
import { PROFILE_LIMITS } from '@/api/auth'
import { API_BASE_URL } from '@/api/client'
import { AUTH_ERROR, USER_ERROR } from '@/api/errors'
import { PROFILE } from '@/mocks/fixtures/user'
import { fail, ok } from '@/mocks/response'
import { scene } from '@/mocks/scenario'

/**
 * 인증 · 계정 mock. `api/auth.ts` 의 함수와 일대일로 맞춘다.
 *
 * 지갑 세션과 프로필은 localStorage 에 둔다 — 모듈 변수로 들고 있으면
 * 새로고침마다 픽스처로 돌아가서 "저장했는데 안 남는다" 로 보인다.
 */
const PROFILE_KEY = 'mockProfile'
/** 한 번이라도 로그인한 지갑. 처음 보는 주소는 온보딩 전으로 취급해 가입 화면을 태운다. */
const KNOWN_WALLETS_KEY = 'mockKnownWallets'

/** 저장된 것이 있으면 픽스처 위에 덮는다. 필드가 늘어나도 옛 저장값이 깨지지 않는다. */
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

/**
 * 이미지 파트 검증. 가입과 프로필 수정이 같은 규칙을 쓴다(명세 USER_004·USER_005).
 * 파일이 아니면 검사할 것이 없다 — 안 보낸 것은 「그대로 두라」는 뜻이다.
 */
function rejectBadImage(image: FormDataEntryValue | null) {
  if (!(image instanceof File)) return null

  const { types, maxBytes } = PROFILE_LIMITS.image
  if (!(types as readonly string[]).includes(image.type)) {
    return fail(400, USER_ERROR.IMAGE_TYPE, '프로필 이미지는 PNG 또는 JPG 여야 합니다.')
  }
  if (image.size > maxBytes) {
    return fail(400, USER_ERROR.IMAGE_SIZE, '프로필 이미지는 5MB 를 넘을 수 없습니다.')
  }
  return null
}

/**
 * 고른 이미지를 data URL 로 바꾼다 — mock 에는 파일을 둘 CDN 이 없고, blob URL 은
 * 새로고침하면 죽어서 「저장했는데 안 남는다」가 된다.
 *
 * base64 는 원본의 약 1.37배라 큰 파일은 localStorage 한도를 넘는다. 그때는 `writeProfile`
 * 이 조용히 실패해 이번 세션에서만 보인다. mock 으로 확인할 때 쓰는 이미지는 작다.
 */
async function toDataUrl(file: File) {
  const bytes = new Uint8Array(await file.arrayBuffer())
  let binary = ''
  // 한 번에 펼치면 인자가 너무 많아 스택이 넘친다.
  const CHUNK = 0x8000
  for (let i = 0; i < bytes.length; i += CHUNK) {
    binary += String.fromCharCode(...bytes.subarray(i, i + CHUNK))
  }
  return `data:${file.type};base64,${btoa(binary)}`
}

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
    // 요청에 nonce 가 없어 어떤 메시지에 서명했는지 알 수 없다(아래 주석 참고).
    if (!/^0x[0-9a-fA-F]{130}$/.test(signature)) {
      return fail(400, 'COMMON_001', '서명이 올바르지 않습니다.')
    }

    // 실서버는 계정이 없으면 만들고 프로필이 빈 채로 준다. 여기서는 처음 보는
    // 지갑이면 온보딩 전으로 되돌려 가입 화면 분기를 확인할 수 있게 한다.
    if (!readKnownWallets().includes(walletAddress)) {
      writeProfile({ ...readProfile(), onboardingCompleted: false })
    }
    rememberWallet(walletAddress)

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
    const stored = readProfile()
    return ok<UserProfile>({ ...stored, wallet: { ...stored.wallet, address: walletAddress } })
  }),
  http.patch(`${API_BASE_URL}/users/me`, async ({ request }) => {
    const forced = await scene()
    if (forced) return forced

    // 명세가 multipart 다 — 프로필 이미지가 같은 요청에 실린다
    const form = await request.formData()
    const nickname = form.get('nickname')
    const introduction = form.get('introduction')
    const image = form.get('profileImageFile')
    const removeImage = form.get('removeProfileImage') === 'true'

    // 교체와 삭제를 같이 보내면 어느 쪽인지 정할 수 없다.
    if (image instanceof File && removeImage) {
      return fail(400, USER_ERROR.IMAGE_CONFLICT, '이미지 교체와 삭제를 함께 보낼 수 없습니다.')
    }

    const { nickname: nickLimit, introduction: introLimit } = PROFILE_LIMITS
    if (
      typeof nickname === 'string' &&
      (nickname.length < nickLimit.min || nickname.length > nickLimit.max)
    ) {
      return fail(
        400,
        USER_ERROR.PROFILE_INVALID,
        `이름은 ${nickLimit.min}~${nickLimit.max}자여야 합니다.`,
      )
    }
    if (typeof introduction === 'string' && introduction.length > introLimit.max) {
      return fail(
        400,
        USER_ERROR.PROFILE_INVALID,
        `한 줄 소개는 ${introLimit.max}자를 넘을 수 없습니다.`,
      )
    }

    const badImage = rejectBadImage(image)
    if (badImage) return badImage

    // 보내지 않은 필드는 그대로 둔다
    let next = readProfile()
    if (typeof nickname === 'string') next = { ...next, nickname }
    if (typeof introduction === 'string') next = { ...next, introduction }
    if (image instanceof File) next = { ...next, profileImageUrl: await toDataUrl(image) }
    if (removeImage) next = { ...next, profileImageUrl: null }
    writeProfile(next)

    return ok<UpdatedProfile>({
      userId: next.userId,
      nickname: next.nickname,
      introduction: next.introduction,
      profileImageUrl: next.profileImageUrl,
      updatedAt: new Date().toISOString(),
    })
  }),

  /**
   * 회원가입 완료 — 프로필과 두 가지 동의를 한 번에 저장한다.
   *
   * 검사 순서가 중요하다. 중복 호출 거부가 저장보다 앞에 와야 두 번째 요청이
   * 이름을 덮어쓰고 나서 409 를 내는 일이 없다.
   */
  http.post(`${API_BASE_URL}/users/me/signup`, async ({ request }) => {
    const forced = await scene()
    if (forced) return forced

    if (!readSession()) return fail(401, AUTH_ERROR.MISSING, '인증이 필요합니다.')

    const current = readProfile()
    if (current.onboardingCompleted) {
      return fail(409, USER_ERROR.ALREADY_SIGNED_UP, '이미 가입 절차를 마친 계정입니다.')
    }

    const form = await request.formData()
    const nickname = form.get('nickname')
    const introduction = form.get('introduction')
    const image = form.get('profileImageFile')

    if (typeof nickname !== 'string' || nickname.trim() === '') {
      return fail(400, 'COMMON_001', '이름이 필요합니다.')
    }

    const { nickname: nickLimit, introduction: introLimit } = PROFILE_LIMITS
    if (nickname.length < nickLimit.min || nickname.length > nickLimit.max) {
      return fail(
        400,
        USER_ERROR.PROFILE_INVALID,
        `이름은 ${nickLimit.min}~${nickLimit.max}자여야 합니다.`,
      )
    }
    if (typeof introduction === 'string' && introduction.length > introLimit.max) {
      return fail(
        400,
        USER_ERROR.PROFILE_INVALID,
        `한 줄 소개는 ${introLimit.max}자를 넘을 수 없습니다.`,
      )
    }

    // FormData 는 값을 문자열로 만든다. api/auth.ts 가 String(boolean) 으로 넣는 것과 짝이다.
    if (form.get('termsAgreed') !== 'true' || form.get('recoveryNoticeConfirmed') !== 'true') {
      return fail(
        400,
        USER_ERROR.CONSENT_REQUIRED,
        '약관 동의와 계정 복구 불가 고지 확인이 필요합니다.',
      )
    }

    const badImage = rejectBadImage(image)
    if (badImage) return badImage

    const next: UserProfile = {
      ...current,
      nickname,
      introduction: typeof introduction === 'string' && introduction ? introduction : null,
      onboardingCompleted: true,
    }
    writeProfile(next)

    return ok<SignupResult>({
      userId: next.userId,
      // 명세가 평평한 문자열로 준다 — GET /users/me 의 wallet 객체와 모양이 다르다.
      walletAddress: readSession() ?? next.wallet.address,
      nickname: next.nickname,
      introduction: next.introduction,
      profileImageUrl: next.profileImageUrl,
      onboardingCompleted: true,
      createdAt: next.createdAt,
    })
  }),
]
