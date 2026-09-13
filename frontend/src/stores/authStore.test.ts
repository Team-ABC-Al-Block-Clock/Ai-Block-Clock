import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { UserProfile } from '@/api/auth'

/**
 * 인증 세션의 상태 전이. 확인하는 것은 **응답이 늦게 도착했을 때**다.
 *
 * 복구·로그인은 요청을 두 번(토큰 → 프로필) 기다리고, 그 사이에 세션이 갈릴 수 있다.
 * 늦게 도착한 응답이 이미 지나간 세션을 되살리면 로그인 직후 튕기거나 죽은 세션으로
 * 보호 화면이 열린다. 브라우저에서 손으로 누르면 타이밍이 맞아야 재현되므로
 * 응답 도착 순서를 여기서 직접 정한다.
 */

/** 응답 시점을 시나리오가 정하도록, api 세 모듈을 이 객체로 갈아끼운다.
 *  `vi.mock` 은 호이스팅되므로 `vi.hoisted` 로 먼저 만든다. */
const h = vi.hoisted(() => ({
  token: null as string | null,
  restoreSession: vi.fn<() => Promise<boolean>>(),
  getMyProfile: vi.fn<() => Promise<unknown>>(),
  loginWithWallet: vi.fn<() => Promise<unknown>>(),
  logout: vi.fn<() => Promise<unknown>>(),
  /** 인터셉터가 회복 불가능한 401 을 알려 오는 자리 — 스토어가 등록한다 */
  onAuthFailure: (() => {}) as () => void,
}))

vi.mock('@/api/client', () => ({
  restoreSession: () => h.restoreSession(),
  setOnAuthFailure: (handler: () => void) => {
    h.onAuthFailure = handler
  },
}))

vi.mock('@/api/auth', () => ({
  getMyProfile: () => h.getMyProfile(),
  loginWithWallet: () => h.loginWithWallet(),
  logout: () => h.logout(),
}))

vi.mock('@/lib/tokenStorage', () => ({
  tokenStorage: {
    get: () => h.token,
    set: (token: string) => {
      h.token = token
    },
    clear: () => {
      h.token = null
    },
  },
}))

/** 지갑은 `wallet` 아래에 있다(7절) — 스토어가 거기서 주소를 꺼내는지도 함께 본다. */
const PROFILE = {
  userId: 'u1',
  nickname: '이싸피',
  wallet: { address: '0xaaa' },
} as unknown as UserProfile

const CREDENTIALS = { walletAddress: '0xaaa', signature: '0xsig' }

/** 손으로 풀어 주는 promise — 시나리오가 응답 도착 시점을 정한다. */
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((res) => {
    resolve = res
  })
  return { promise, resolve }
}

/** 대기 중인 마이크로태스크를 흘려보낸다. */
const settle = () => new Promise((r) => setTimeout(r, 0))

/**
 * 시나리오마다 스토어 모듈을 새로 로드한다.
 * 세션 번호(epoch)가 모듈 전역이라 시나리오끼리 공유되면 결과가 섞인다.
 */
async function freshStore() {
  vi.resetModules()
  const { useAuthStore } = await import('@/stores/authStore')
  return useAuthStore
}

beforeEach(() => {
  h.token = null
  h.restoreSession.mockReset()
  h.getMyProfile.mockReset()
  h.loginWithWallet.mockReset()
  h.logout.mockReset().mockResolvedValue(undefined)
})

describe('늦게 도착한 응답이 지나간 세션을 되살리지 않는다', () => {
  it('부팅 복구가 늦게 실패해도 그 사이 성공한 로그인을 덮지 않는다', async () => {
    const reissue = deferred<boolean>()
    h.restoreSession.mockReturnValue(reissue.promise)
    h.loginWithWallet.mockResolvedValue({ accessToken: 'fresh-token' })
    h.getMyProfile.mockResolvedValue(PROFILE)
    const useAuthStore = await freshStore()

    const restoring = useAuthStore.getState().restore()
    await settle()
    expect(useAuthStore.getState().status).toBe('restoring')

    await useAuthStore.getState().signIn(CREDENTIALS)
    expect(useAuthStore.getState().status).toBe('authenticated')

    reissue.resolve(false) // 이제서야 재발급 실패가 도착한다
    await restoring
    await settle()
    expect(useAuthStore.getState().status).toBe('authenticated')
    expect(h.token).toBe('fresh-token')
  })

  it('401 로 끊은 뒤 도착한 프로필이 세션을 되살리지 않는다', async () => {
    const profile = deferred<unknown>()
    h.restoreSession.mockResolvedValue(true)
    h.getMyProfile.mockReturnValue(profile.promise)
    const useAuthStore = await freshStore()

    const restoring = useAuthStore.getState().restore()
    await settle()
    expect(useAuthStore.getState().status).toBe('restoring')

    h.onAuthFailure() // 인터셉터가 회복 불가능한 401 을 알려 온다
    expect(useAuthStore.getState().status).toBe('unauthenticated')

    profile.resolve(PROFILE)
    await restoring
    await settle()
    expect(useAuthStore.getState().status).toBe('unauthenticated')
    expect(h.token).toBeNull()
  })

  it('로그인 도중 세션이 끊기면 늦게 온 토큰을 버리고 null 을 준다', async () => {
    // null 이 아니라 조용히 끝내면, 화면은 로그인이 된 줄 알고 홈으로 넘어간다.
    const login = deferred<unknown>()
    h.restoreSession.mockResolvedValue(false)
    h.loginWithWallet.mockReturnValue(login.promise)
    h.getMyProfile.mockResolvedValue(PROFILE)
    const useAuthStore = await freshStore()

    const signing = useAuthStore.getState().signIn(CREDENTIALS)
    await settle()
    useAuthStore.getState().clearSession()
    login.resolve({ accessToken: 'stale-token' })
    await expect(signing).resolves.toBeNull()
    await settle()
    expect(useAuthStore.getState().status).toBe('unauthenticated')
    expect(h.token).toBeNull()
  })
})

describe('정상 경로', () => {
  it('복구가 성공하면 프로필의 wallet.address 를 쓴다', async () => {
    h.restoreSession.mockResolvedValue(true)
    h.getMyProfile.mockResolvedValue(PROFILE)
    const useAuthStore = await freshStore()

    await useAuthStore.getState().restore()

    expect(useAuthStore.getState().status).toBe('authenticated')
    expect(useAuthStore.getState().walletAddress).toBe('0xaaa')
  })

  it('StrictMode 로 두 번 불려도 재발급은 한 번만 나간다', async () => {
    // 재발급이 두 번 나가면 서버가 이전 Refresh Token 을 폐기해 두 번째가 세션을 끊는다.
    h.restoreSession.mockResolvedValue(true)
    h.getMyProfile.mockResolvedValue(PROFILE)
    const useAuthStore = await freshStore()

    await Promise.all([useAuthStore.getState().restore(), useAuthStore.getState().restore()])

    expect(h.restoreSession).toHaveBeenCalledTimes(1)
    expect(useAuthStore.getState().status).toBe('authenticated')
  })

  it('로그인이 성공하면 프로필을 돌려준다', async () => {
    h.restoreSession.mockResolvedValue(false)
    h.loginWithWallet.mockResolvedValue({ accessToken: 't' })
    h.getMyProfile.mockResolvedValue(PROFILE)
    const useAuthStore = await freshStore()

    await expect(useAuthStore.getState().signIn(CREDENTIALS)).resolves.toBe(PROFILE)
  })

  it('복구가 실패하면 미인증이 된다', async () => {
    h.restoreSession.mockResolvedValue(false)
    const useAuthStore = await freshStore()

    await useAuthStore.getState().restore()

    expect(useAuthStore.getState().status).toBe('unauthenticated')
  })

  it('프로필을 못 받으면 인증된 척하지 않고 토큰도 버린다', async () => {
    h.restoreSession.mockResolvedValue(true)
    h.getMyProfile.mockRejectedValue(new Error('401'))
    const useAuthStore = await freshStore()

    await useAuthStore.getState().restore()

    expect(useAuthStore.getState().status).toBe('unauthenticated')
    expect(h.token).toBeNull()
  })

  it('로그인 실패는 화면으로 던지고 미인증으로 남는다', async () => {
    h.restoreSession.mockResolvedValue(false)
    h.loginWithWallet.mockResolvedValue({ accessToken: 't' })
    h.getMyProfile.mockRejectedValue(new Error('401'))
    const useAuthStore = await freshStore()

    await expect(useAuthStore.getState().signIn(CREDENTIALS)).rejects.toThrow('401')
    expect(useAuthStore.getState().status).toBe('unauthenticated')
  })

  it('로그아웃 요청이 실패해도 세션은 끊는다', async () => {
    h.restoreSession.mockResolvedValue(true)
    h.getMyProfile.mockResolvedValue(PROFILE)
    h.logout.mockRejectedValue(new Error('500'))
    const useAuthStore = await freshStore()
    await useAuthStore.getState().restore()

    await expect(useAuthStore.getState().signOut()).rejects.toThrow('500')

    expect(useAuthStore.getState().status).toBe('unauthenticated')
    expect(h.token).toBeNull()
  })
})

describe('세션이 끝난 이유를 남긴다', () => {
  // 로그인 화면이 이 값으로 안내를 고른다. 스스로 누른 로그아웃에 안내가 붙으면
  // 사고처럼 보이고, 지갑이 끊겨 밀려난 사람에게 안 붙으면 이유를 알 수 없다.
  async function authenticated() {
    h.restoreSession.mockResolvedValue(true)
    h.getMyProfile.mockResolvedValue(PROFILE)
    const useAuthStore = await freshStore()
    await useAuthStore.getState().restore()
    return useAuthStore
  }

  it('스스로 누른 로그아웃은 사유가 없다', async () => {
    const useAuthStore = await authenticated()
    await useAuthStore.getState().signOut()
    expect(useAuthStore.getState().endedBy).toBeNull()
  })

  it('지갑 연결 해제는 disconnected 로 남는다', async () => {
    const useAuthStore = await authenticated()
    await useAuthStore.getState().signOut('disconnected')
    expect(useAuthStore.getState().endedBy).toBe('disconnected')
  })

  it('지갑 계정 교체는 account 로 남는다', async () => {
    const useAuthStore = await authenticated()
    await useAuthStore.getState().signOut('account')
    expect(useAuthStore.getState().endedBy).toBe('account')
  })

  it('회복 불가능한 401 은 expired 로 남는다', async () => {
    const useAuthStore = await authenticated()
    useAuthStore.getState().clearSession()
    expect(useAuthStore.getState().endedBy).toBe('expired')
  })

  it('다시 로그인하면 지난 사유가 지워진다', async () => {
    h.loginWithWallet.mockResolvedValue({ accessToken: 't' })
    const useAuthStore = await authenticated()
    useAuthStore.getState().clearSession()

    await useAuthStore.getState().signIn(CREDENTIALS)

    expect(useAuthStore.getState().endedBy).toBeNull()
  })
})
