import { useEffect } from 'react'

import { useAuthStore } from '@/stores/authStore'

/**
 * 부팅 시 세션을 한 번 확인한다. `SESSION` 쿠키는 HttpOnly 라 JS 가 있는지조차
 * 알 수 없어서, 이걸 하지 않으면 쿠키가 살아 있는 사용자도 미인증으로 그려진다.
 *
 * 구독하지 않고 `getState()` 로 부른다 — 값을 읽는 게 아니라 한 번 실행하는 것이다.
 */
export function useSessionRestore() {
  useEffect(() => {
    void useAuthStore.getState().restore()
  }, [])
}
