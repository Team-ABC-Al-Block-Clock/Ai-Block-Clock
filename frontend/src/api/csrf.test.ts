import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'

import { logout } from '@/api/auth'
import { API_BASE_URL, api } from '@/api/client'
import { clearCsrfToken } from '@/api/csrf'
import { ApiError, AUTH_ERROR } from '@/api/errors'
import { fail, ok } from '@/mocks/response'
import { server } from '@/test/server'

/**
 * CSRF 토큰 부착과 재발급. 인터셉터 · 캐시 · mock 을 한 번에 통과시킨다.
 *
 * 토큰 캐시는 모듈 변수라 테스트끼리 샌다 — 매번 비우고 시작한다.
 * 발급 핸들러를 매번 다른 토큰으로 덮어써서 "다시 받아 왔는지" 를 값으로 확인한다.
 */
const PROBE = `${API_BASE_URL}/probe`

let issued: string[] = []

beforeEach(() => {
  clearCsrfToken()
  issued = []
  server.use(
    http.get(`${API_BASE_URL}/auth/csrf`, () => {
      const token = `token-${issued.length + 1}`
      issued.push(token)
      return ok({ headerName: 'X-CSRF-TOKEN', token })
    }),
  )
})

/** 서버의 CSRF 거절. 컨트롤러 앞에서 막히므로 우리 오류 봉투가 아니다. */
const forbidden = () => HttpResponse.json({ error: 'Forbidden' }, { status: 403 })

describe('CSRF 토큰 부착', () => {
  it('쓰기에만 붙인다 — 읽기는 면제라 토큰을 받지도 않는다', async () => {
    const sent: (string | null)[] = []
    const record = ({ request }: { request: Request }) => {
      sent.push(request.headers.get('X-CSRF-TOKEN'))
      return ok(null)
    }
    server.use(http.get(PROBE, record), http.post(PROBE, record))

    await api.get('/probe')
    expect(issued).toHaveLength(0)

    await api.post('/probe')
    expect(sent).toEqual([null, 'token-1'])
  })

  it('쓰기가 동시에 나가도 발급은 한 번이다', async () => {
    server.use(http.post(PROBE, () => ok(null)))

    await Promise.all([api.post('/probe'), api.post('/probe'), api.post('/probe')])

    // 세션 쿠키가 없는 첫 발급이 두 번 나가면 세션이 두 개 열리고, 브라우저는 나중 쿠키만 남긴다.
    expect(issued).toEqual(['token-1'])
  })

  it('받은 토큰은 다음 쓰기에 다시 쓴다 — 서버가 세션 안에서 같은 값을 재사용한다', async () => {
    server.use(http.post(PROBE, () => ok(null)))

    await api.post('/probe')
    await api.post('/probe')

    expect(issued).toEqual(['token-1'])
  })
})

describe('토큰이 죽었을 때', () => {
  it('봉투 없는 403 이면 다시 받아 한 번 재시도한다', async () => {
    const sent: (string | null)[] = []
    server.use(
      http.post(PROBE, ({ request }) => {
        sent.push(request.headers.get('X-CSRF-TOKEN'))
        return sent.length === 1 ? forbidden() : ok('saved')
      }),
    )

    await expect(api.post('/probe')).resolves.toBe('saved')
    // 재시도는 새 토큰으로 나가야 한다. 같은 값으로 다시 보내면 또 거절이다.
    expect(sent).toEqual(['token-1', 'token-2'])
  })

  it('재시도까지 403 이면 그대로 실패한다 — 끝없이 돌지 않는다', async () => {
    let attempts = 0
    server.use(
      http.post(PROBE, () => {
        attempts += 1
        return forbidden()
      }),
    )

    await expect(api.post('/probe')).rejects.toBeInstanceOf(ApiError)
    expect(attempts).toBe(2)
  })

  it('오류 봉투가 붙은 403 은 권한 거절이라 재시도하지 않는다', async () => {
    let attempts = 0
    server.use(
      http.post(PROBE, () => {
        attempts += 1
        // 코드 값 자체는 상관없다 — 우리 봉투가 붙어 있다는 것이 판정 기준이다.
        return fail(403, 'COMMON_001', '권한이 없습니다.')
      }),
    )

    await expect(api.post('/probe')).rejects.toMatchObject({ code: 'COMMON_001' })
    expect(attempts).toBe(1)
  })
})

describe('세션이 갈릴 때', () => {
  it('로그아웃하면 버린다 — 다음 쓰기가 새 토큰을 받는다', async () => {
    // 세션 유무는 이 테스트의 관심이 아니다. mock 의 세션 장부 대신 응답만 고정한다.
    server.use(
      http.post(`${API_BASE_URL}/auth/logout`, () => ok(null)),
      http.post(PROBE, () => ok(null)),
    )

    await api.post('/probe')
    await logout()
    await api.post('/probe')

    expect(issued).toEqual(['token-1', 'token-2'])
  })

  it('로그아웃이 실패해도 버린다 — 세션은 끊겼는데 토큰만 남는 쪽이 나쁘다', async () => {
    server.use(
      http.post(`${API_BASE_URL}/auth/logout`, () => HttpResponse.json({}, { status: 500 })),
      http.post(PROBE, () => ok(null)),
    )

    await api.post('/probe')
    await expect(logout()).rejects.toBeInstanceOf(ApiError)
    await api.post('/probe')

    expect(issued).toEqual(['token-1', 'token-2'])
  })

  it('401 이면 버린다 — 세션이 사라졌으니 거기 매인 토큰도 죽었다', async () => {
    server.use(
      http.post(PROBE, () => fail(401, AUTH_ERROR.EXPIRED, '세션이 만료되었습니다.')),
      http.post(`${API_BASE_URL}/probe-again`, () => ok(null)),
    )

    await expect(api.post('/probe')).rejects.toBeInstanceOf(ApiError)
    await api.post('/probe-again')

    expect(issued).toEqual(['token-1', 'token-2'])
  })
})
