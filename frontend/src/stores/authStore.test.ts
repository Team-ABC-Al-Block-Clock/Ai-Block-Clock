import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { UserProfile } from '@/api/auth'

/**
 * 인증 세션의 상태 전이. 확인하는 것은 **응답이 늦게 도착했을 때**다.
 *
 * 로그인은 요청을 두 번(로그인 → 프로필) 기다리고, 부팅 복구도 프로필 응답을 기다린다.
 * 그 사이에 세션이 갈릴 수 있다. 늦게 도착한 응답이 이미 지나간 세션을 되살리면 로그인
 * 직후 튕기거나 죽은 세션으로 보호 화면이 열린다. 브라우저에서 손으로 누르면 타이밍이
 * 맞아야 재현되므로 응답 도착 순서를 여기서 직접 정한다.
 */

/** 응답 시점을 시나리오가 정하도록, api 두 모듈을 이 객체로 갈아끼운다.
 *  `vi.mock` 은 호이스팅되므로 `vi.hoisted` 로 먼저 만든다. */
const h = vi.hoisted(() => ({
  getMyProfile: vi.fn<() => Promise<unknown>>(),
  loginWithWallet: vi.fn<() => Promise<unknown>>(),
  logout: vi.fn<() => Promise<unknown>>(),
  /** 인터셉터가 회복 불가능한 401 을 알려 오는 자리 — 스토어가 등록한다 */
  onAuthFailure: (() => {}) as (code?: string) => void,
  clearCache: vi.fn(),
}))

vi.mock('@/api/client', () => ({
  setOnAuthFailure: (handler: (code?: string) => void) => {
    h.onAuthFailure = handler
  },
}))

vi.mock('@/api/auth', () => ({
  getMyProfile: () => h.getMyProfile(),
  loginWithWallet: () => h.loginWithWallet(),
  logout: () => h.logout(),
}))

vi.mock('@/lib/queryClient', () => ({
  queryClient: { clear: () => h.clearCache() },
}))

/** 지갑은 `wallet` 아래에 있다(7절) — 스토어가 거기서 주소를 꺼내는지도 함께 본다. */
const PROFILE = {
  userId: 'u1',
  nickname: '이싸피',
  wallet: { address: '0xaaa' },
} as unknown as UserProfile

/** 로그인 응답에는 토큰이 없다 — 세션은 쿠키로만 온다. */
const SESSION = { expiresAt: '2026-09-28T00:00:00.000Z' }

const CREDENTIALS = { walletAddress: '0xaaa', signature: '0xsig' }

/** 손으로 풀어 주는 promise — 시나리오가 응답 도착 시점을 정한다. */
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
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
  h.getMyProfile.mockReset()
  h.loginWithWallet.mockReset().mockResolvedValue(SESSION)
  h.logout.mockReset().mockResolvedValue(undefined)
  h.clearCache.mockReset()
})

describe('늦게 도착한 응답이 지나간 세션을 되살리지 않는다', () => {
  it('부팅 복구가 늦게 실패해도 그 사이 성공한 로그인을 덮지 않는다', async () => {
    const booting = deferred<unknown>()
    // 첫 번째 조회는 부팅 복구, 두 번째는 로그인 뒤 조회다.
    h.getMyProfile.mockReturnValueOnce(booting.promise).mockResolvedValue(PROFILE)
    const useAuthStore = await freshStore()

    const restoring = useAuthStore.getState().restore()
    await settle()
    expect(useAuthStore.getState().status).toBe('restoring')

    await useAuthStore.getState().signIn(CREDENTIALS)
    expect(useAuthStore.getState().status).toBe('authenticated')

    booting.reject(new Error('401')) // 이제서야 복구 실패가 도착한다
    await restoring
    await settle()
    expect(useAuthStore.getState().status).toBe('authenticated')
  })

  it('401 로 끊은 뒤 도착한 프로필이 세션을 되살리지 않는다', async () => {
    const booting = deferred<unknown>()
    h.getMyProfile.mockReturnValue(booting.promise)
    const useAuthStore = await freshStore()

    const restoring = useAuthStore.getState().restore()
    await settle()
    expect(useAuthStore.getState().status).toBe('restoring')

    h.onAuthFailure() // 인터셉터가 회복 불가능한 401 을 알려 온다
    expect(useAuthStore.getState().status).toBe('unauthenticated')

    booting.resolve(PROFILE)
    await restoring
    await settle()
    expect(useAuthStore.getState().status).toBe('unauthenticated')
  })

  it('로그인 도중 세션이 끊기면 서버 세션까지 끊고 null 을 준다', async () => {
    // 쿠키는 이미 브라우저에 저장됐고 HttpOnly 라 JS 가 지울 수 없다. 서버에 끊어
    // 달라고 하지 않으면 화면은 로그아웃 상태인데 새로고침 한 번에 되살아난다.
    const login = deferred<unknown>()
    h.loginWithWallet.mockReturnValue(login.promise)
    h.getMyProfile.mockResolvedValue(PROFILE)
    const useAuthStore = await freshStore()

    const signing = useAuthStore.getState().signIn(CREDENTIALS)
    await settle()
    useAuthStore.getState().clearSession()
    login.resolve(SESSION)

    await expect(signing).resolves.toBeNull()
    await settle()
    expect(useAuthStore.getState().status).toBe('unauthenticated')
    expect(h.logout).toHaveBeenCalledTimes(1)
  })

  it('버려진 로그인이 그 뒤에 성공한 로그인의 세션을 끊지 않는다', async () => {
    // 서명 중 취소하고 다시 연결을 누르면 로그인이 두 번 시작된다. 로그아웃 요청은
    // "지금 브라우저에 있는 쿠키" 를 끊으므로, 늦게 끝난 첫 번째가 이것을 보내면
    // 두 번째 로그인이 방금 받은 쿠키가 지워져 로그인 직후 튕긴다.
    const first = deferred<unknown>()
    h.loginWithWallet.mockReturnValueOnce(first.promise).mockResolvedValue(SESSION)
    h.getMyProfile.mockResolvedValue(PROFILE)
    const useAuthStore = await freshStore()

    const abandoned = useAuthStore.getState().signIn(CREDENTIALS)
    await settle()
    await useAuthStore.getState().signIn(CREDENTIALS) // 다시 연결 — 이쪽 쿠키가 최신이다
    expect(useAuthStore.getState().status).toBe('authenticated')

    first.resolve(SESSION) // 이제서야 버린 로그인이 끝난다
    await expect(abandoned).resolves.toBeNull()
    await settle()

    expect(h.logout).not.toHaveBeenCalled()
    expect(useAuthStore.getState().status).toBe('authenticated')
  })
})

describe('정상 경로', () => {
  it('복구가 성공하면 프로필의 wallet.address 를 쓴다', async () => {
    h.getMyProfile.mockResolvedValue(PROFILE)
    const useAuthStore = await freshStore()

    await useAuthStore.getState().restore()

    expect(useAuthStore.getState().status).toBe('authenticated')
    expect(useAuthStore.getState().walletAddress).toBe('0xaaa')
  })

  it('StrictMode 로 두 번 불려도 프로필 조회는 한 번만 나간다', async () => {
    h.getMyProfile.mockResolvedValue(PROFILE)
    const useAuthStore = await freshStore()

    await Promise.all([useAuthStore.getState().restore(), useAuthStore.getState().restore()])

    expect(h.getMyProfile).toHaveBeenCalledTimes(1)
    expect(useAuthStore.getState().status).toBe('authenticated')
  })

  it('로그인이 성공하면 프로필을 돌려준다', async () => {
    h.getMyProfile.mockResolvedValue(PROFILE)
    const useAuthStore = await freshStore()

    await expect(useAuthStore.getState().signIn(CREDENTIALS)).resolves.toBe(PROFILE)
  })

  it('쿠키가 없으면(프로필 401) 미인증이 된다', async () => {
    h.getMyProfile.mockRejectedValue(new Error('401'))
    const useAuthStore = await freshStore()

    await useAuthStore.getState().restore()

    expect(useAuthStore.getState().status).toBe('unauthenticated')
  })

  it('로그인 실패는 화면으로 던지고 미인증으로 남는다', async () => {
    h.getMyProfile.mockRejectedValue(new Error('401'))
    const useAuthStore = await freshStore()

    await expect(useAuthStore.getState().signIn(CREDENTIALS)).rejects.toThrow('401')
    expect(useAuthStore.getState().status).toBe('unauthenticated')
  })

  it('로그인 요청이 실패해도 기다리던 복구가 멈추지 않는다', async () => {
    // 로그인은 기다리기 전에 세션 번호를 올려 진행 중인 부팅 복구를 쫓아낸다.
    // 그 로그인이 실패했는데 상태를 끝내지 않으면 복구는 제 응답을 버리고,
    // 화면은 '세션을 확인하고 있어요' 에서 새로고침 전까지 나오지 못한다.
    const booting = deferred<unknown>()
    h.getMyProfile.mockReturnValue(booting.promise)
    h.loginWithWallet.mockRejectedValue(new Error('AUTH_004'))
    const useAuthStore = await freshStore()

    const restoring = useAuthStore.getState().restore()
    await settle()
    expect(useAuthStore.getState().status).toBe('restoring')

    await expect(useAuthStore.getState().signIn(CREDENTIALS)).rejects.toThrow('AUTH_004')
    expect(useAuthStore.getState().status).toBe('unauthenticated')

    booting.resolve(PROFILE)
    await restoring
    await settle()
    expect(useAuthStore.getState().status).toBe('unauthenticated')
  })

  it('로그아웃이 401 로 끝나도 사유를 남기지 않는다', async () => {
    // 쿠키가 이미 없으면 서버가 401 을 준다. 그때 인터셉터가 먼저 사유를 남기고
    // `signOut` 의 finally 가 뒤따르는데, 본인이 누른 로그아웃에 안내가 붙으면 안 된다.
    h.getMyProfile.mockResolvedValue(PROFILE)
    const useAuthStore = await freshStore()
    await useAuthStore.getState().restore()

    h.logout.mockImplementation(() => {
      h.onAuthFailure('AUTH_018')
      return Promise.reject(new Error('401'))
    })

    await expect(useAuthStore.getState().signOut()).rejects.toThrow('401')

    expect(useAuthStore.getState().status).toBe('unauthenticated')
    expect(useAuthStore.getState().endedBy).toBeNull()
  })

  it('로그아웃 요청이 실패해도 세션은 끊는다', async () => {
    h.getMyProfile.mockResolvedValue(PROFILE)
    h.logout.mockRejectedValue(new Error('500'))
    const useAuthStore = await freshStore()
    await useAuthStore.getState().restore()

    await expect(useAuthStore.getState().signOut()).rejects.toThrow('500')

    expect(useAuthStore.getState().status).toBe('unauthenticated')
  })
})

describe('세션이 끝난 이유를 남긴다', () => {
  // 로그인 화면이 이 값으로 안내를 고른다. 스스로 누른 로그아웃에 안내가 붙으면
  // 사고처럼 보이고, 지갑이 끊겨 밀려난 사람에게 안 붙으면 이유를 알 수 없다.
  async function authenticated() {
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

  it('회복 불가능한 401 은 기본이 expired 다', async () => {
    const useAuthStore = await authenticated()
    useAuthStore.getState().clearSession()
    expect(useAuthStore.getState().endedBy).toBe('expired')
  })

  // 인터셉터가 코드를 넘겨 주지 않으면 스토어가 이유를 지어내고, 다른 곳에서
  // 재로그인해 끊긴 세션도 "만료" 로 안내된다.
  it.each([
    ['AUTH_014', 'revoked'],
    ['AUTH_005', 'invalid'],
    ['AUTH_006', 'invalid'],
    ['AUTH_018', 'invalid'],
    ['AUTH_013', 'expired'],
    [undefined, 'expired'],
  ])('401 코드 %s 는 %s 로 남는다', async (code, reason) => {
    const useAuthStore = await authenticated()
    h.onAuthFailure(code)
    expect(useAuthStore.getState().endedBy).toBe(reason)
  })

  it('인증된 적이 없으면 401 에 사유를 붙이지 않는다', async () => {
    // 첫 방문은 쿠키가 없어 부팅 복구가 401 로 끝난다. 여기에 사유가 붙으면
    // 한 번도 로그인하지 않은 사람이 "세션이 만료되었습니다" 를 본다.
    h.getMyProfile.mockRejectedValue(new Error('401'))
    const useAuthStore = await freshStore()

    await useAuthStore.getState().restore()

    expect(useAuthStore.getState().status).toBe('unauthenticated')
    expect(useAuthStore.getState().endedBy).toBeNull()
  })

  it('세션이 끊기면 쿼리 캐시도 비운다', async () => {
    // 스토어만 비우면 staleTime 동안 마이페이지·잔액이 이전 계정 값을 보여 준다.
    const useAuthStore = await authenticated()
    expect(h.clearCache).not.toHaveBeenCalled()

    await useAuthStore.getState().signOut()

    expect(h.clearCache).toHaveBeenCalledTimes(1)
  })

  it('401 이 연달아 와도 처음 남긴 사유가 남는다', async () => {
    // 한 화면이 목록 여러 개를 동시에 부르면 쿠키가 끊긴 순간 401 도 함께 온다.
    // 두 번째부터는 이미 미인증이라 사유를 새로 적을 수 없는데, 그때 지워 버리면
    // 아무 안내 없이 로그인 화면으로 떨어진다.
    const useAuthStore = await authenticated()

    h.onAuthFailure('AUTH_013') // expired
    h.onAuthFailure('AUTH_005') // 뒤따라 도착한 401
    h.onAuthFailure('AUTH_005')

    expect(useAuthStore.getState().endedBy).toBe('expired')
  })

  it('로그아웃이 401 로 끝나도 밀려난 사유는 그대로 남는다', async () => {
    // 지갑 연결이 끊겨 로그아웃하는데 서버 세션이 이미 없으면 401 이 온다.
    // 인터셉터가 먼저 `invalid` 를 남기지만, 사용자에게 보여야 할 이유는 지갑 쪽이다.
    const useAuthStore = await authenticated()
    h.logout.mockImplementation(() => {
      h.onAuthFailure('AUTH_005')
      return Promise.reject(new Error('401'))
    })

    await expect(useAuthStore.getState().signOut('disconnected')).rejects.toThrow('401')

    expect(useAuthStore.getState().endedBy).toBe('disconnected')
  })

  it('인증된 적이 없으면 쿼리 캐시를 비우지 않는다', async () => {
    // 비로그인 방문도 부팅 복구 401 로 같은 길을 지나간다. 여기서 비우면 로그인과
    // 무관한 공개 화면(상세·검색)의 쿼리까지 날아가 재요청과 깜빡임이 남는다.
    h.getMyProfile.mockRejectedValue(new Error('401'))
    const useAuthStore = await freshStore()

    await useAuthStore.getState().restore()

    expect(useAuthStore.getState().status).toBe('unauthenticated')
    expect(h.clearCache).not.toHaveBeenCalled()
  })

  it('다시 로그인하면 지난 사유가 지워진다', async () => {
    const useAuthStore = await authenticated()
    useAuthStore.getState().clearSession()

    await useAuthStore.getState().signIn(CREDENTIALS)

    expect(useAuthStore.getState().endedBy).toBeNull()
  })
})
