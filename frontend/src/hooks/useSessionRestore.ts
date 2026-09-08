import { useEffect } from 'react'

import { useAuthStore } from '@/stores/authStore'

/**
 * 앱이 뜨면 Refresh Token 쿠키로 세션을 한 번 되살린다.
 *
 * Access Token 은 메모리에만 있어 새로고침하면 사라진다. 이걸 하지 않으면
 * 로그인한 사용자가 새로고침 한 번에 로그인 화면으로 튕긴다.
 *
 * 스토어를 구독하지 않고 `getState()` 로 부른다 — 값을 읽는 게 아니라
 * 한 번 실행하는 것이라 구독하면 불필요한 리렌더만 생긴다.
 */
export function useSessionRestore() {
  useEffect(() => {
    void useAuthStore.getState().restore()
  }, [])
}
