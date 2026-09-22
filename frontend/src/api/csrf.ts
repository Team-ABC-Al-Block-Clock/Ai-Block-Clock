import { api } from '@/api/client'

/**
 * CSRF 토큰 캐시.
 *
 * 서버가 상태를 바꾸는 요청에만 토큰 헤더를 요구한다. 토큰은 세션에 하나뿐이고
 * (`HttpSessionCsrfTokenRepository`) 세션이 살아 있는 한 바뀌지 않는다. 그래서 한 번 받아
 * 계속 쓰고, 세션이 갈리는 때(로그인·로그아웃·401)에만 `clearCsrfToken` 으로 버린다.
 *
 * 발급을 부를 때마다 **다른 문자열**이 오는 것은 회전이 아니다. 서버가 BREACH 방어로
 * 매번 다른 값을 XOR 해 내보낼 뿐이라, 먼저 받은 값도 계속 유효하다(실측 확인).
 * 값이 다르다고 최신 것을 다시 받으러 가면 왕복만 는다.
 *
 * `client.ts` 와 서로 import 한다. 양쪽 다 모듈 평가 중에 상대를 부르지 않고
 * 여기 export 가 호이스팅되는 함수 선언이라 평가 순서에 걸리지 않는다.
 */
export interface CsrfToken {
  /** 헤더 이름도 서버가 준 값을 쓴다. 임의로 지정하면 서버가 바꿀 때 조용히 403 이 된다 */
  headerName: string
  token: string
}

let cached: CsrfToken | null = null
let inflight: Promise<CsrfToken> | null = null

/**
 * 캐시가 있으면 그대로, 없으면 한 번만 발급받는다.
 *
 * 저장 버튼 두 개가 동시에 나가도 발급은 한 번이다. 세션 쿠키가 없는 첫 발급이 동시에
 * 두 번 나가면 세션이 두 개 열리는데, 브라우저에 남는 쿠키는 나중 것 하나뿐이라 먼저 받은
 * 토큰은 버려진 세션 것이 된다 — 다른 세션의 토큰은 403 이다(실측 확인).
 */
export function getCsrfToken(): Promise<CsrfToken> {
  if (cached) return Promise.resolve(cached)
  inflight ??= api
    .get<CsrfToken>('/auth/csrf')
    .then((issued) => {
      cached = issued
      return issued
    })
    .finally(() => {
      inflight = null
    })
  return inflight
}

/** 세션이 갈렸거나 서버가 토큰을 거절했다 — 다음 쓰기 요청이 다시 받아 온다. */
export function clearCsrfToken(): void {
  cached = null
  inflight = null
}
