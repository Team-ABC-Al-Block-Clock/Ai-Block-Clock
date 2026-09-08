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
 * 인증 세션의 단일 소스. 지갑 주소·프로필·연결 상태를 여기서만 들고 있다.
 *
 * Access Token 은 이 스토어가 아니라 `lib/tokenStorage.ts` 에 둔다.
 * axios 인터셉터는 React 트리 밖에서 돌아 훅을 쓸 수 없고, 두 곳에 같은 토큰을
 * 복사해 두면 한쪽만 갱신됐을 때 조용히 갈라진다. 스토어는 토큰의 수명(발급·폐기)만
 * 관리하고 값은 tokenStorage 를 거친다.
 */

/** 연결 상태. `idle`·`restoring` 은 "아직 모른다" 이고 미인증이 아니다. */
export type AuthStatus = 'idle' | 'restoring' | 'authenticated' | 'unauthenticated'

interface AuthState {
  status: AuthStatus
  walletAddress: string | null
  profile: UserProfile | null

  /** 앱 시작 시 한 번 — Refresh Token 쿠키로 세션을 되살린다 */
  restore: () => Promise<void>
  /** 서명 검증을 서버에 맡기고 세션을 연다. 실패는 그대로 던져 화면이 안내한다 */
  signIn: (params: { walletAddress: string; signature: string }) => Promise<void>
  /** 서버 쿠키까지 지운다 */
  signOut: () => Promise<void>
  /** 클라이언트 상태만 비운다 — 회복 불가능한 401 을 받았을 때 */
  clearSession: () => void
}

/** 세션이 없는 상태. 로그아웃·인증 실패·복구 실패가 모두 같은 모양으로 끝나야 한다. */
const SIGNED_OUT = {
  status: 'unauthenticated',
  walletAddress: null,
  profile: null,
} satisfies Partial<AuthState>

/** 세션 확인이 끝나기 전인지. 이때 미인증으로 보면 새로고침마다 로그인 화면이 한 번 스친다. */
export const isSessionPending = (status: AuthStatus) => status === 'idle' || status === 'restoring'

export const useAuthStore = create<AuthState>((set, get) => ({
  status: 'idle',
  walletAddress: null,
  profile: null,

  restore: async () => {
    // 부팅 때 한 번만. StrictMode 는 effect 를 두 번 태우고, 재발급이 두 번 나가면
    // 서버가 이전 Refresh Token 을 폐기해 두 번째가 세션을 끊는다.
    if (get().status !== 'idle') return
    set({ status: 'restoring' })

    if (!(await restoreSession())) {
      set(SIGNED_OUT)
      return
    }

    try {
      const profile = await getMyProfile()
      set({ status: 'authenticated', profile, walletAddress: profile.walletAddress })
    } catch {
      // 토큰은 받았는데 프로필을 못 읽은 상태로 두면 주소 없이 인증된 척하는 화면이 된다.
      tokenStorage.clear()
      set(SIGNED_OUT)
    }
  },

  signIn: async ({ walletAddress, signature }) => {
    const tokens = await loginWithWallet({ walletAddress, signature })
    tokenStorage.set(tokens.accessToken)

    try {
      // 지갑이 준 주소를 그대로 믿지 않고 서버 프로필의 주소로 맞춘다.
      const profile = await getMyProfile()
      set({ status: 'authenticated', profile, walletAddress: profile.walletAddress })
    } catch (error) {
      tokenStorage.clear()
      set(SIGNED_OUT)
      throw error
    }
  },

  signOut: async () => {
    try {
      await requestLogout()
    } finally {
      // 서버 응답이 실패해도 클라이언트 세션은 반드시 끊는다.
      tokenStorage.clear()
      set(SIGNED_OUT)
    }
  },

  clearSession: () => {
    tokenStorage.clear()
    set(SIGNED_OUT)
  },
}))

/** 회복 불가능한 401 을 api 계층이 알려 온다.
 *  상태만 비우고 화면 이동은 ProtectedRoute 가 status 를 보고 한다 —
 *  api 계층이 라우터를 알지 않도록 콜백으로 갈라 둔 것이다. */
setOnAuthFailure(() => {
  useAuthStore.getState().clearSession()
})
