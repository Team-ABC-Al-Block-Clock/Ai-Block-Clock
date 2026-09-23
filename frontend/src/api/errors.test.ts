import { describe, expect, it } from 'vitest'

import { isApiErrorBody } from '@/api/errors'

/**
 * 실패 봉투 판정. 여기가 틀리면 code·message 가 통째로 버려지고,
 * `client.ts` 의 403 재시도가 권한 거절까지 다시 보내거나 CSRF 거절을 놓친다.
 */
describe('isApiErrorBody', () => {
  it('우리 실패 봉투를 알아본다', () => {
    const body = { status: 'FAIL', data: null, error: { code: 'AUTH_005', message: '없음' } }
    expect(isApiErrorBody(body)).toBe(true)
  })

  it('error.data 가 붙어 있어도 알아본다', () => {
    const body = {
      status: 'FAIL',
      data: null,
      error: { code: 'COMMON_001', message: '검증 실패', data: { nickname: '너무 깁니다' } },
    }
    expect(isApiErrorBody(body)).toBe(true)
  })

  // 서버의 CSRF·인증 거절은 컨트롤러 앞에서 막혀 Spring 기본 본문으로 온다.
  // `error` 키는 있지만 문자열이라 여기서 걸러져야 403 재시도가 걸린다.
  it('Spring 기본 오류 본문은 아니다', () => {
    const body = {
      timestamp: '2026-09-22T07:29:18.094Z',
      status: 403,
      error: 'Forbidden',
      path: '/api/v1/probe',
    }
    expect(isApiErrorBody(body)).toBe(false)
  })

  it('성공 봉투는 아니다 — error 가 null 이다', () => {
    expect(isApiErrorBody({ status: 'SUCCESS', data: { id: 1 }, error: null })).toBe(false)
  })

  it.each([
    ['빈 객체', {}],
    ['error 가 빈 객체', { error: {} }],
    ['code 만 있음', { error: { code: 'AUTH_005' } }],
    ['message 만 있음', { error: { message: '없음' } }],
    ['code 가 문자열이 아님', { error: { code: 500, message: '없음' } }],
    ['error 가 배열', { error: [] }],
    ['null', null],
    ['문자열', '<!doctype html>'],
  ])('%s 는 아니다', (_, body) => {
    expect(isApiErrorBody(body)).toBe(false)
  })
})
