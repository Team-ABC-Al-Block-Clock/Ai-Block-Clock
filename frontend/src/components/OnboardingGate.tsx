import type { ReactElement } from 'react'
import { Navigate, useLocation } from 'react-router-dom'

import { paths } from '@/routes/paths'
import { useAuthStore } from '@/stores/authStore'

/** 프로필을 채우지 않은 계정을 가입 화면에 붙잡아 둔다.
 *
 *  `ProtectedRoute` 는 "세션이 있는가" 만 보므로 따로 둔다 — 공개 화면도 막아야 한다(4절). */
export default function OnboardingGate({ children }: { children: ReactElement }) {
  // authenticated 일 때만 본다 — 확인 중에 보내면 새로고침마다 화면이 튄다
  const pending = useAuthStore(
    (s) => s.status === 'authenticated' && s.profile?.onboardingCompleted === false,
  )
  const { pathname } = useLocation()

  if (pending && pathname !== paths.signup) return <Navigate to={paths.signup} replace />

  return children
}
