import { create } from 'zustand'

import {
  getMyProfile,
  loginWithWallet,
  logout as requestLogout,
  type UserProfile,
} from '@/api/auth'
import { restoreSession, setOnAuthFailure } from '@/api/client'
import { tokenStorage } from '@/lib/tokenStorage'

/**
 * 인증 세션의 단일 소스 — 지갑 주소·프로필·연결 상태.
 *
 * Access Token 은 `lib/tokenStorage.ts` 에 둔다. axios 인터셉터가 React 트리 밖에서
 * 읽어야 하고, 두 곳에 복사해 두면 한쪽만 갱신됐을 때 조용히 갈라진다.
 */

/** `idle`·`restoring` 은 "아직 모른다" 이고 미인증이 아니다. */
export type AuthStatus = 'idle' | 'restoring' | 'authenticated' | 'unauthenticated'

/** 세션이 왜 끝났는지. 스스로 누른 로그아웃은 이유가 없다(`null`) —
 *  본인이 아는 일에 안내를 띄우면 사고처럼 보인다. */
export type SessionEndReason = 'expired' | 'disconnected' | 'account'

interface AuthState {
  status: AuthStatus
  walletAddress: string | null
  profile: UserProfile | null
  /** 쓰던 중 세션이 끊긴 이유. 로그인 화면이 이 값으로 안내를 고른다 */
  endedBy: SessionEndReason | null

  /** 부팅 시 한 번 — Refresh Token 쿠키로 세션을 되살린다 */
  restore: () => Promise<void>
  /** 실패는 그대로 던져 화면이 안내한다. 성공해도 그 사이 세션이 갈렸으면
   *  이 로그인은 버려지고 `null` 이 온다 — 호출부가 모르고 지나가지 않도록 */
  signIn: (params: { walletAddress: string; signature: string }) => Promise<UserProfile | null>
  /** 서버 쿠키까지 지운다. 사유를 주면 로그인 화면이 이유를 띄운다 */
  signOut: (endedBy?: SessionEndReason) => Promise<void>
  /** 클라이언트 상태만 비운다 — 회복 불가능한 401 */
  clearSession: () => void
  /** 바뀐 필드만 덮는다 — 내 정보를 고치면 화면들이 같이 바뀐다.
   *  병합을 스토어가 맡는 이유: PATCH 응답에 지갑 주소가 없어서 호출부가
   *  통째로 넣으면 그 값이 조용히 사라진다 */
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
 *  늦게 도착한 응답이 지나간 세션을 되살리지 못하게. 자세한 것은 문서 7절. */
let epoch = 0

export const useAuthStore = create<AuthState>((set, get) => {
  /** 세션을 끊는다. 번호를 올려 진행 중인 복구·로그인이 뒤늦게 인증 상태를 쓰지 못하게 한다. */
  const dropSession = (endedBy: SessionEndReason | null = null) => {
    epoch += 1
    tokenStorage.clear()
    set({ ...SIGNED_OUT, endedBy })
  }

  return {
    status: 'idle',
    walletAddress: null,
    profile: null,
    endedBy: null,

    restore: async () => {
      // 부팅 때 한 번만. StrictMode 의 두 번째 effect 로 재발급이 두 번 나가면
      // 서버가 이전 Refresh Token 을 폐기해 세션이 끊긴다.
      if (get().status !== 'idle') return
      const mine = ++epoch
      set({ status: 'restoring' })

      if (!(await restoreSession())) {
        if (mine === epoch) dropSession()
        return
      }

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
        // 프로필 없이 인증된 척하는 화면이 되지 않도록 끊는다.
        if (mine === epoch) dropSession()
      }
    },

    signIn: async ({ walletAddress, signature }) => {
      // 사용자가 누른 로그인이 부팅 복구를 이긴다.
      const mine = ++epoch
      const tokens = await loginWithWallet({ walletAddress, signature })
      // 그 사이 세션이 갈렸다 — 이 토큰은 버리고, 로그인이 안 됐다고 알린다.
      if (mine !== epoch) return null
      tokenStorage.set(tokens.accessToken)

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
        dropSession(endedBy ?? null)
      }
    },

    clearSession: () => dropSession('expired'),

    setProfile: (patch) => {
      // 세션이 끝난 뒤 늦게 도착한 저장 응답이 화면을 되살리지 않게 한다.
      const { status, profile } = get()
      if (status !== 'authenticated' || !profile) return
      set({ profile: { ...profile, ...patch } })
    },
  }
})

/** 회복 불가능한 401 — 상태만 비우고 화면 이동은 ProtectedRoute 가 한다.
 *  api 계층이 라우터를 알지 않도록 콜백으로 갈라 둔 것이다. */
setOnAuthFailure(() => {
  useAuthStore.getState().clearSession()
})
