import { paths, type ScreenKey } from '@/routes/paths'

/**
 * 화면별 인증 요구. `App.tsx` 가 이 값을 보고 `<ProtectedRoute>` 로 감싼다.
 *
 * Record<ScreenKey, boolean> 이라 화면을 추가하면 공개인지 보호인지 정할 때까지
 * 컴파일이 실패한다 — 새 화면이 판단 없이 조용히 공개로 열리는 것을 막는다.
 */
export const REQUIRES_AUTH: Record<ScreenKey, boolean> = {
  /** 마켓 둘러보기까지는 지갑 없이 열어 둔다 — 가입 유입 경로다 */
  home: false,
  login: false,
  signup: false,
  search: false,
  agentDetail: false,

  /** 에이전트 등록·편집·실행·마이페이지는 지갑 세션이 있어야 한다 */
  register: true,
  editSearching: true,
  editNodes: true,
  editNodesSeller: true,
  runWaiting: true,
  runRunning: true,
  myAgents: true,
  myEdit: true,
  myUsage: true,
  mySales: true,
  myCredit: true,
}

/** 로그인 화면으로 넘길 때 실어 보내는 원래 경로. */
export interface LoginRedirectState {
  from: string
}

/**
 * 로그인 후 돌아갈 경로를 history state 에서 꺼낸다.
 *
 * state 는 사용자가 만질 수 있으므로 내부 경로인지 확인한다.
 * `//evil.com` 은 `/` 로 시작하지만 브라우저가 외부 주소로 읽는다.
 */
export function redirectTargetFrom(state: unknown): string {
  const from = (state as LoginRedirectState | null)?.from
  if (typeof from !== 'string' || !from.startsWith('/') || from.startsWith('//')) return paths.home
  return from
}
