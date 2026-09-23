import { HttpResponse } from 'msw'

/**
 * 서버 응답 봉투를 mock 에서도 그대로 흉내 낸다. 알맹이만 돌려주면 화면이 받는 모양이
 * 실서버와 달라져 mock 에서만 되는 코드가 만들어진다.
 */

/** `{ status: 'SUCCESS', data, error: null }` */
export function ok<T>(data: T, init?: ResponseInit) {
  return HttpResponse.json({ status: 'SUCCESS', data, error: null }, init)
}

/**
 * `{ status: 'FAIL', data: null, error: { code, message, data } }`
 *
 * 화면은 status 가 아니라 `code` 로 분기하므로 mock 도 실제 도메인 코드를 쓴다.
 * code·message 가 `error` 아래 중첩된 것까지 흉내 내야 한다 — 평평하게 두면
 * mock 에서만 오류 문구가 뜨고 실서버에서는 전부 빈 값이 된다.
 */
export function fail(status: number, code: string, message: string) {
  return HttpResponse.json(
    { status: 'FAIL', data: null, error: { code, message, data: null } },
    { status },
  )
}
