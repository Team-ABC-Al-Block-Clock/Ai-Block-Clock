import type { ReactElement } from 'react'
import { Navigate, useLocation } from 'react-router-dom'

import { paths } from '@/routes/paths'
import { useAuthStore } from '@/stores/authStore'

/**
 * 프로필을 채우지 않은 계정을 가입 화면에 붙잡아 둔다.
 *
 * 서버는 첫 서명에서 계정을 먼저 만들고 프로필은 비워 둔다. 그래서 "로그인은
 * 됐지만 이름이 없는" 상태가 실재하고, 로그인 직후 한 번 보내는 것만으로는
 * 막지 못한다 — 브라우저 뒤로가기나 주소 직접 입력으로 그냥 들어온다.
 * 그 상태로는 마이페이지에 이름이 비고 에이전트에 판매자가 없다.
 *
 * `ProtectedRoute` 와 나눠 둔 이유: 그쪽은 "세션이 있는가" 만 본다.
 * 홈·검색처럼 공개된 화면도 가입을 마치기 전에는 막아야 한다.
 * 둘러보기만 하려면 가입 화면의 뒤로가기로 세션을 끊고 나가면 된다.
 */
export default function OnboardingGate({ children }: { children: ReactElement }) {
  // 세션이 아직 확인 중이면 프로필도 모른다 — 그때 보내면 새로고침마다 화면이 튄다.
  const pending = useAuthStore(
    (s) => s.status === 'authenticated' && s.profile?.onboardingCompleted === false,
  )
  const { pathname } = useLocation()

  if (pending && pathname !== paths.signup) return <Navigate to={paths.signup} replace />

  return children
}
