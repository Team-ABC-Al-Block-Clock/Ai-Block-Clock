import { create } from 'zustand'

import {
  getMyProfile,
  loginWithWallet,
  logout as requestLogout,
  type UserProfile,
} from '@/api/auth'
import { setOnAuthFailure } from '@/api/client'
import { AUTH_ERROR } from '@/api/errors'
import { queryClient } from '@/lib/queryClient'

/**
 * 인증 세션의 단일 소스.
 *
 * 세션은 `sessionToken` HttpOnly 쿠키라 JS 가 값을 읽을 수 없다. 그래서 스토어가
 * 들고 있는 것은 토큰이 아니라 「서버가 나를 알아보는가」의 결과뿐이다.
 */

/** `idle`·`restoring` 은 "아직 모른다" 이고 미인증이 아니다. */
export type AuthStatus = 'idle' | 'restoring' | 'authenticated' | 'unauthenticated'

/** 세션이 왜 끝났는지. 스스로 누른 로그아웃은 이유가 없다(`null`) —
 *  본인이 아는 일에 안내를 띄우면 사고처럼 보인다.
 *
 *  타입과 런타임 검사가 이 배열 하나에서 나온다. 따로 두었더니 `routes/access.ts` 가
 *  세 개만 통과시켜 `revoked`·`invalid` 안내가 화면에 닿지 못했다. */
export const SESSION_END_REASONS = [
  'expired',
  'revoked',
  'invalid',
  'disconnected',
  'account',
] as const

export type SessionEndReason = (typeof SESSION_END_REASONS)[number]

/** 시간이 지나 끊긴 것과 서버가 끊은 것은 사용자가 할 일이 달라 문구를 나눈다. */
export function endReasonFromAuthCode(code?: string): SessionEndReason {
  switch (code) {
    case AUTH_ERROR.ENDED:
      return 'revoked'
    case AUTH_ERROR.MISSING:
    case AUTH_ERROR.UNIDENTIFIABLE:
    case AUTH_ERROR.INVALID:
      return 'invalid'
    default:
      // 만료와 코드를 못 읽은 경우.
      return 'expired'
  }
}

interface AuthState {
  status: AuthStatus
  walletAddress: string | null
  profile: UserProfile | null
  /** 쓰던 중 세션이 끊긴 이유. 로그인 화면이 이 값으로 안내를 고른다 */
  endedBy: SessionEndReason | null

  /** 부팅 시 한 번 — 쿠키가 살아 있는지는 프로필을 물어봐야 안다 */
  restore: () => Promise<void>
  /** 실패는 그대로 던져 화면이 안내한다. 성공해도 그 사이 세션이 갈렸으면
   *  이 로그인은 버려지고 `null` 이 온다 — 호출부가 모르고 지나가지 않도록 */
  signIn: (params: { walletAddress: string; signature: string }) => Promise<UserProfile | null>
  /** 서버 쿠키까지 지운다. 사유를 주면 로그인 화면이 이유를 띄운다 */
  signOut: (endedBy?: SessionEndReason) => Promise<void>
  /** 클라이언트 상태만 비운다 — 회복 불가능한 401 */
  clearSession: (endedBy?: SessionEndReason) => void
  /** 바뀐 필드만 덮는다. PATCH 응답에 지갑 주소가 없어 통째로 넣으면 그 값이 사라진다 */
  setProfile: (patch: Partial<UserProfile>) => void
}

/** 로그아웃·인증 실패·복구 실패가 모두 같은 모양으로 끝나야 한다. */
const SIGNED_OUT = {
  status: 'unauthenticated',
  walletAddress: null,
  profile: null,
  endedBy: null,
} satisfies Partial<AuthState>

/** 이때 미인증으로 그리면 새로고침마다 로그인 화면이 한 번 스친다. */
export const isSessionPending = (status: AuthStatus) => status === 'idle' || status === 'restoring'

/** 세션이 갈릴 때마다 올라간다. 시작할 때 받은 번호가 최신일 때만 상태를 쓴다 —
 *  늦게 도착한 응답이 지나간 세션을 되살리지 못하게. */
let epoch = 0

export const useAuthStore = create<AuthState>((set, get) => {
  /**
   * 세션을 끊는다. 번호를 올려 진행 중인 복구·로그인이 뒤늦게 인증 상태를 쓰지 못하게 한다.
   *
   * `force` 는 사용자가 스스로 끝낸 세션(`signOut`)이다 — 준 사유를 그대로 쓴다.
   * 밀려난 경우(401)는 먼저 적힌 사유가 이긴다.
   */
  const dropSession = (endedBy: SessionEndReason | null = null, { force = false } = {}) => {
    // `endedBy` 는 「쓰던 중 끊겼다」는 뜻이다. 인증된 적이 없으면 알릴 이유가 없다 —
    // 첫 방문의 401 에 "세션이 만료되었습니다" 가 붙으면 사고처럼 보인다.
    const { status, endedBy: recorded } = get()
    const wasAuthenticated = status === 'authenticated'
    epoch += 1
    // 쿼리 캐시까지 비운다. 스토어만 비우면 staleTime(60초) 동안 마이페이지·잔액이
    // 이전 계정 값을 보여 준다 — 쿠키 세션은 7일이라 같은 브라우저에서 지갑을
    // 갈아타는 일이 흔하다. 세션을 끊는 길은 여기 하나뿐이라 호출부를 늘리지 않는다.
    queryClient.clear()
    // 먼저 적힌 사유를 지킨다. 401 은 한 화면에서 여러 개가 동시에 오고, 두 번째부터는
    // 이미 미인증이라 사유를 새로 적을 수 없다 — 덮어쓰게 두면 안내가 통째로 사라진다.
    set({
      ...SIGNED_OUT,
      endedBy: force ? endedBy : (recorded ?? (wasAuthenticated ? endedBy : null)),
    })
  }

  return {
    status: 'idle',
    walletAddress: null,
    profile: null,
    endedBy: null,

    restore: async () => {
      // 쿠키는 HttpOnly 라 있는지 없는지를 JS 가 알 수 없다. 프로필을 물어보는 것이
      // 유일한 확인 수단이고, 401 이면 미인증이다.
      //
      // 부팅 때 한 번만 — StrictMode 의 두 번째 effect 로 같은 요청이 또 나갈 이유가 없다.
      if (get().status !== 'idle') return
      const mine = ++epoch
      set({ status: 'restoring' })

      try {
        const profile = await getMyProfile()
        if (mine !== epoch) return
        set({
          status: 'authenticated',
          profile,
          walletAddress: profile.wallet.address,
          endedBy: null,
        })
      } catch {
        // 401 이든 서버 오류든 인증된 척하는 화면이 되지 않도록 끊는다.
        if (mine === epoch) dropSession()
      }
    },

    signIn: async ({ walletAddress, signature }) => {
      // 사용자가 누른 로그인이 부팅 복구를 이긴다.
      const mine = ++epoch
      await loginWithWallet({ walletAddress, signature })

      if (mine !== epoch) {
        // 그 사이 세션이 갈렸다. 쿠키는 이미 브라우저에 저장됐고 HttpOnly 라 JS 가
        // 지울 수 없으니 서버에 끊어 달라고 한다 — 두면 새로고침 한 번에 되살아난다.
        void requestLogout().catch(() => undefined)
        return null
      }

      try {
        // 지갑이 준 주소가 아니라 서버 프로필의 주소를 쓴다.
        const profile = await getMyProfile()
        if (mine !== epoch) return null
        set({
          status: 'authenticated',
          profile,
          walletAddress: profile.wallet.address,
          endedBy: null,
        })
        return profile
      } catch (error) {
        if (mine === epoch) dropSession()
        throw error
      }
    },

    signOut: async (endedBy) => {
      // 지갑 연결 해제는 여기서 하지 않는다 — 스토어는 wagmi 를 모르고,
      // 세션 만료로 끊길 때는 지갑을 유지해야 한다. `App.tsx` 의 useLogout 참고.
      try {
        await requestLogout()
      } finally {
        // 사용자가 누른 로그아웃은 서버 응답이 실패해도 이긴다.
        // 로그아웃이 401 이면 인터셉터가 먼저 남긴 사유를 여기서 덮어야 한다.
        dropSession(endedBy ?? null, { force: true })
      }
    },

    clearSession: (endedBy = 'expired') => dropSession(endedBy),

    setProfile: (patch) => {
      // 세션이 끝난 뒤 늦게 도착한 저장 응답이 화면을 되살리지 않게 한다.
      const { status, profile } = get()
      if (status !== 'authenticated' || !profile) return
      set({ profile: { ...profile, ...patch } })
    },
  }
})

/** 상태만 비우고 화면 이동은 ProtectedRoute 가 한다 — api 계층은 라우터를 모른다. */
setOnAuthFailure((code) => {
  useAuthStore.getState().clearSession(endReasonFromAuthCode(code))
})
