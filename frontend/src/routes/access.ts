import { paths, type ScreenKey } from '@/routes/paths'
import type { SessionEndReason } from '@/stores/authStore'

/**
 * 화면별 인증 요구. `App.tsx` 가 이 값을 보고 `<ProtectedRoute>` 로 감싼다.
 * `Record<ScreenKey, boolean>` 이라 화면을 추가하면 공개·보호를 정할 때까지 컴파일이 실패한다.
 */
export const REQUIRES_AUTH: Record<ScreenKey, boolean> = {
  /** 마켓 둘러보기까지는 지갑 없이 열어 둔다 — 가입 유입 경로다 */
  home: false,
  login: false,
  search: false,
  agentDetail: false,

  /** 가입 화면은 서명이 끝난 뒤 프로필만 채우는 자리다 — 세션 없이 들어오면
   *  저장할 곳이 없어 로그인부터 시작해야 한다 */
  signup: true,

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
  /** 쓰던 중 세션이 끊겨 밀려온 것이면 그 이유 */
  endedBy?: SessionEndReason | null
}

/** 첫 글자만 `/` 여야 한다. `//evil.com` 과 `/\evil.com`(브라우저가 백슬래시를
 *  슬래시로 읽는다) 은 `/` 로 시작하지만 외부 주소가 된다. */
const INTERNAL_PATH = /^\/(?![/\\])/

/** 밀려온 이유. 스스로 로그아웃하고 온 것과 구분해 안내를 띄운다. */
export function endReasonFrom(state: unknown): SessionEndReason | null {
  const endedBy = (state as LoginRedirectState | null)?.endedBy
  return endedBy === 'expired' || endedBy === 'disconnected' || endedBy === 'account'
    ? endedBy
    : null
}

/** 로그인 후 돌아갈 경로. state 는 사용자가 만질 수 있어 내부 경로인지 확인한다. */
export function redirectTargetFrom(state: unknown): string {
  const from = (state as LoginRedirectState | null)?.from
  if (typeof from !== 'string' || !INTERNAL_PATH.test(from)) return paths.home
  return from
}
