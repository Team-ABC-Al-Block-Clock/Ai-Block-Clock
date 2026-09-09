import { useEffect } from 'react'

import { useAuthStore } from '@/stores/authStore'

/**
 * 부팅 시 Refresh Token 쿠키로 세션을 한 번 되살린다. Access Token 은 메모리에만
 * 있어, 이걸 하지 않으면 로그인한 사용자가 새로고침 한 번에 로그인 화면으로 튕긴다.
 *
 * 구독하지 않고 `getState()` 로 부른다 — 값을 읽는 게 아니라 한 번 실행하는 것이다.
 */
export function useSessionRestore() {
  useEffect(() => {
    void useAuthStore.getState().restore()
  }, [])
}
