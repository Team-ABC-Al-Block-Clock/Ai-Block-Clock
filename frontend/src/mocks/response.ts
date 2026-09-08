import { HttpResponse } from 'msw'

/**
 * 서버 응답 봉투를 mock 에서도 그대로 흉내 낸다.
 *
 * 봉투를 벗기는 것은 `api/client.ts` 의 응답 인터셉터다. mock 이 봉투 없이
 * 알맹이만 돌려주면 인터셉터를 통과해도 화면이 받는 모양이 실서버와 달라져,
 * mock 에서만 되는 코드가 만들어진다.
 */

/** 성공. `{ status: 'success', data }` */
export function ok<T>(data: T, init?: ResponseInit) {
  return HttpResponse.json({ status: 'success', data }, init)
}

/**
 * 실패. `{ status: 'error', code, message }`
 *
 * 화면은 HTTP status 가 아니라 `code` 로 분기한다(`api/errors.ts`).
 * mock 에서도 실제 도메인 코드를 쓴다 — 400 하나에 COMMON_001 과 AGENT_003 이 섞여 있다.
 */
export function fail(status: number, code: string, message: string) {
  return HttpResponse.json({ status: 'error', code, message }, { status })
}
