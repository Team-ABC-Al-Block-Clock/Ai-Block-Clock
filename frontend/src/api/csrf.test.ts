import { AxiosError } from 'axios'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { logout } from '@/api/auth'
import { API_BASE_URL, api, client } from '@/api/client'
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

/**
 * 서버의 CSRF 거절. 컨트롤러 앞에서 막혀 우리 오류 봉투가 아니라 Spring 기본 본문이 온다.
 * 실서버에서 받아 온 모양 그대로다 — `error` 가 객체가 아니라 문자열인 것이 핵심이다.
 */
const forbidden = () =>
  HttpResponse.json(
    {
      timestamp: '2026-09-22T07:29:18.094Z',
      status: 403,
      error: 'Forbidden',
      path: '/api/v1/probe',
    },
    { status: 403 },
  )

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

  it('받은 토큰은 다음 쓰기에 다시 쓴다 — 세션이 살아 있으면 계속 유효하다', async () => {
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

  // 각자 버리고 각자 받으면 발급이 두 번 나간다. 세션 쿠키가 없을 때는 세션이 두 개 열리고
  // 먼저 받은 쪽이 버려진 세션 토큰을 들게 되는데, 재시도 표시가 이미 서 있어 그대로 실패한다.
  it('같은 토큰으로 동시에 403 을 받아도 새 토큰은 한 번만 받는다', async () => {
    const sent: (string | null)[] = []
    server.use(
      http.post(PROBE, ({ request }) => {
        const token = request.headers.get('X-CSRF-TOKEN')
        sent.push(token)
        return token === 'token-1' ? forbidden() : ok('saved')
      }),
    )

    await expect(Promise.all([api.post('/probe'), api.post('/probe')])).resolves.toEqual([
      'saved',
      'saved',
    ])

    expect(issued).toEqual(['token-1', 'token-2'])
    expect(sent.filter((t) => t === 'token-2')).toHaveLength(2)
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

  // axios 는 이미 직렬화해 둔 본문을 들고 재시도하면서 transformRequest 를 한 번 더 돌린다.
  // 지금은 문자열·FormData 모두 그대로 통과하지만, axios 를 올릴 때 조용히 깨질 자리다.
  it('재시도에도 JSON 본문이 그대로 실린다', async () => {
    const bodies: unknown[] = []
    server.use(
      http.post(PROBE, async ({ request }) => {
        bodies.push(await request.json())
        return bodies.length === 1 ? forbidden() : ok('saved')
      }),
    )

    await expect(api.post('/probe', { nickname: '한서윤', age: 3 })).resolves.toBe('saved')
    expect(bodies).toEqual([
      { nickname: '한서윤', age: 3 },
      { nickname: '한서윤', age: 3 },
    ])
  })

  // jsdom 은 multipart 를 직렬화하지 못해 MSW 까지 보낼 수 없다(`auth.test.ts` 의 같은 이유).
  // 어댑터를 가로채 재시도가 같은 FormData 를 그대로 다시 들고 나가는지만 본다.
  it('재시도에도 multipart 본문이 그대로 실린다', async () => {
    const previous = client.defaults.adapter
    const seen: unknown[] = []

    client.defaults.adapter = (config) => {
      if (config.url?.endsWith('/auth/csrf')) {
        const token = `token-${issued.length + 1}`
        issued.push(token)
        return Promise.resolve({
          data: { status: 'SUCCESS', data: { headerName: 'X-CSRF-TOKEN', token } },
          status: 200,
          statusText: 'OK',
          headers: {},
          config,
        })
      }

      seen.push(config.data)
      if (seen.length === 1) {
        return Promise.reject(
          new AxiosError('403', 'ERR_BAD_REQUEST', config, null, {
            data: { timestamp: '', status: 403, error: 'Forbidden', path: '/probe' },
            status: 403,
            statusText: 'Forbidden',
            headers: {},
            config,
          }),
        )
      }
      return Promise.resolve({
        data: { status: 'SUCCESS', data: 'saved' },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      })
    }

    const form = new FormData()
    form.append('nickname', '한서윤')

    try {
      await expect(api.post('/probe', form)).resolves.toBe('saved')
    } finally {
      client.defaults.adapter = previous
    }

    expect(seen).toHaveLength(2)
    expect(seen[1]).toBe(form)
    expect((seen[1] as FormData).get('nickname')).toBe('한서윤')
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
      http.post(PROBE, () => fail(401, AUTH_ERROR.NOT_IDENTIFIED, '요청자를 확인할 수 없습니다.')),
      http.post(`${API_BASE_URL}/probe-again`, () => ok(null)),
    )

    await expect(api.post('/probe')).rejects.toBeInstanceOf(ApiError)
    await api.post('/probe-again')

    expect(issued).toEqual(['token-1', 'token-2'])
  })

  // 발급 진행 중에 세션이 갈리면, 늦게 도착한 토큰은 죽은 세션 것이다.
  it('발급을 기다리는 사이 버렸으면 그 토큰은 캐시에 남기지 않는다', async () => {
    let release!: () => void
    const held = new Promise<void>((resolve) => (release = resolve))
    server.use(
      http.get(`${API_BASE_URL}/auth/csrf`, async () => {
        const token = `token-${issued.length + 1}`
        issued.push(token)
        await held
        return ok({ headerName: 'X-CSRF-TOKEN', token })
      }),
      http.post(PROBE, () => ok(null)),
    )

    const pending = api.post('/probe')
    // 발급이 실제로 나간 뒤에 버려야 한다. 먼저 버리면 그냥 새로 받을 뿐이다.
    await vi.waitFor(() => expect(issued).toHaveLength(1))
    clearCsrfToken()
    release()
    await pending

    await api.post('/probe')

    expect(issued).toEqual(['token-1', 'token-2'])
  })
})

describe('발급이 제대로 안 될 때', () => {
  it('발급이 실패하면 요청을 보내지 않고 원인을 그대로 알린다', async () => {
    let attempts = 0
    server.use(
      http.get(`${API_BASE_URL}/auth/csrf`, () => fail(500, 'COMMON_002', '서버 오류입니다.')),
      http.post(PROBE, () => {
        attempts += 1
        return ok(null)
      }),
    )

    // 인터셉터가 던진 ApiError 를 응답 인터셉터가 다시 싸면 code·status 가 지워진다.
    await expect(api.post('/probe')).rejects.toMatchObject({
      code: 'COMMON_002',
      status: 500,
      isNetworkError: false,
    })
    expect(attempts).toBe(0)
  })

  it('봉투는 맞는데 토큰이 비었으면 헤더 없이 내보내지 않는다', async () => {
    let attempts = 0
    server.use(
      http.get(`${API_BASE_URL}/auth/csrf`, () => ok({})),
      http.post(PROBE, () => {
        attempts += 1
        return ok(null)
      }),
    )

    await expect(api.post('/probe')).rejects.toBeInstanceOf(ApiError)
    expect(attempts).toBe(0)

    // 캐시에 빈 값이 굳으면 새로고침 전까지 모든 쓰기가 헤더 없이 나간다.
    await expect(api.post('/probe')).rejects.toBeInstanceOf(ApiError)
    expect(attempts).toBe(0)
  })
})
