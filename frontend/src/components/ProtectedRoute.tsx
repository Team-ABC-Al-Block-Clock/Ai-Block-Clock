import type { ReactElement } from 'react'
import { Navigate, useLocation } from 'react-router-dom'

import type { LoginRedirectState } from '@/routes/access'
import { paths } from '@/routes/paths'
import { isSessionPending, useAuthStore } from '@/stores/authStore'

/**
 * 인증이 필요한 화면을 감싼다. 어느 화면인지는 `routes/access.ts` 가 정한다.
 * 미인증이면 막힌 경로를 state 로 넘겨 로그인 화면으로 보내고,
 * 로그인이 끝나면 `redirectTargetFrom` 이 그 경로를 꺼낸다.
 */
export default function ProtectedRoute({ children }: { children: ReactElement }) {
  const status = useAuthStore((s) => s.status)
  const location = useLocation()

  // 확인이 끝나기 전에 로그인 화면을 그리면 새로고침마다 화면이 튄다.
  if (isSessionPending(status)) {
    return (
      <div className="text-muted absolute inset-0 flex items-center justify-center text-sm tracking-[-.01em]">
        세션을 확인하고 있어요
      </div>
    )
  }

  if (status !== 'authenticated') {
    // replace — 막힌 경로가 히스토리에 남으면 뒤로 가기가 다시 여기로 튕긴다.
    return (
      <Navigate
        to={paths.login}
        replace
        state={
          { from: location.pathname + location.search + location.hash } satisfies LoginRedirectState
        }
      />
    )
  }

  return children
}
