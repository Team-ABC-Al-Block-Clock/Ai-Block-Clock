import axios, { AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'

import { clearCsrfToken, getCsrfToken } from '@/api/csrf'
import { ApiError, isApiErrorBody } from '@/api/errors'
import { env } from '@/lib/env'

/** `{ status: 'SUCCESS', data }` — 값으로 분기하지 않고 봉투 모양만 본다. */
interface ApiSuccessBody<T> {
  status: 'SUCCESS' | 'FAIL'
  data: T
}

/** API 루트. mock 핸들러(`mocks/handlers/*`)가 같은 값을 쓴다 —
 *  한쪽만 바뀌면 mock 이 요청을 못 잡고 조용히 실서버로 나간다. */
export const API_BASE_URL = `${env.apiBaseUrl}/v1`

export const client = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30_000,
  // Content-Type 은 지정하지 않는다. 여기에 application/json 을 박으면
  // FormData 가 JSON 문자열로 바뀌어 나간다.
  // 세션이 SESSION HttpOnly 쿠키로 오간다. 이게 없으면 모든 요청이 AUTH_005 다.
  withCredentials: true,
})

/** 서버가 토큰을 요구하는 메서드. GET·HEAD·OPTIONS 는 면제다. */
const CSRF_METHODS = new Set(['post', 'put', 'patch', 'delete'])

const needsCsrf = (config: AxiosRequestConfig) =>
  CSRF_METHODS.has((config.method ?? 'get').toLowerCase())

/** 재시도는 한 번뿐이다. 표시가 없으면 403 이 끝없이 돈다. */
interface RetriedConfig extends InternalAxiosRequestConfig {
  csrfRetried?: boolean
  /** 실제로 실어 보낸 토큰. 403 을 받았을 때 이 값만 버린다 — 둘 다 문자열이라 재시도 병합을 탄다 */
  csrfSent?: string
}

/**
 * 쓰기 요청에 CSRF 토큰을 붙인다.
 *
 * 부팅 때 미리 받지 않고 첫 쓰기에서 받는다 — 읽고 떠나는 방문자에게 요청 한 건을
 * 더 들이지 않으려고. 발급이 실패하면 요청을 보내지 않고 그대로 실패한다.
 * 헤더 없이 내보내 403 을 받는 것보다 원인이 드러난다.
 */
client.interceptors.request.use(async (config) => {
  if (!needsCsrf(config)) return config
  const { headerName, token } = await getCsrfToken()
  config.headers.set(headerName, token)
  ;(config as RetriedConfig).csrfSent = token
  return config
})

/**
 * 회복 불가능한 인증 실패를 스토어에 알린다 — api 계층이 라우터를 알지 않게 콜백으로 뺐다.
 * 코드는 넘기지 않는다. 서버가 401 을 `AUTH_005` 하나로만 답해서 가를 것이 없다.
 */
export type AuthFailureHandler = () => void

let onAuthFailure: AuthFailureHandler | null = null
export function setOnAuthFailure(handler: AuthFailureHandler | null) {
  onAuthFailure = handler
}

client.interceptors.response.use(
  // 봉투를 벗겨 data 만 넘긴다 — 화면이 res.data.data 를 몰라도 된다.
  (response) => {
    const body = response.data
    // 실패 봉투는 벗기지 않는다. 2xx 로 오면 `data: null` 이 성공값으로 둔갑한다.
    if (isApiErrorBody(body)) return body
    if (typeof body === 'object' && body !== null && 'status' in body && 'data' in body) {
      return (body as ApiSuccessBody<unknown>).data
    }
    return body
  },

  (error: unknown) => {
    // 요청 인터셉터(CSRF 발급)가 던진 것은 이미 우리 오류다. 다시 싸면 code·status 가 지워진다.
    if (error instanceof ApiError) throw error

    if (!(error instanceof AxiosError)) {
      throw new ApiError('알 수 없는 오류가 발생했습니다.', { cause: error })
    }

    const response = error.response

    if (!response) {
      throw new ApiError('서버에 연결할 수 없습니다. 네트워크를 확인해 주세요.', { cause: error })
    }

    // 개발 중 502 는 대개 MSW 가 요청을 못 잡아 프록시로 샌 것이다.
    if (import.meta.env.DEV && env.useMock && response.status === 502) {
      console.error(
        '[mocks] 502 — MSW 가 요청을 잡지 못했습니다. 페이지를 새로고침하세요.',
        error.config?.url,
      )
    }

    const body = response.data
    const code = isApiErrorBody(body) ? body.error.code : undefined
    const message = isApiErrorBody(body) ? body.error.message : error.message

    // CSRF 토큰이 죽었다 — 세션이 갈렸는데 캐시를 버리지 못한 경우다. 한 번만 다시 받아 보낸다.
    // 도메인 코드가 붙은 403 은 권한 거절이라 건드리지 않는다. 서버의 CSRF 검사는
    // 컨트롤러 앞에서 막아 우리 오류 봉투를 만들지 못하므로 code 가 없다.
    const config = error.config as RetriedConfig | undefined
    if (
      response.status === 403 &&
      code === undefined &&
      config &&
      needsCsrf(config) &&
      !config.csrfRetried
    ) {
      config.csrfRetried = true
      clearCsrfToken(config.csrfSent)
      return client.request(config)
    }

    // 401 은 전부 회복 불가다 — 자동 재발급 API 가 없어 지갑 서명 로그인부터 다시 한다.
    // 세션이 사라졌으니 거기 매인 토큰도 죽은 값이다.
    if (response.status === 401) {
      clearCsrfToken()
      onAuthFailure?.()
    }

    throw new ApiError(message, { code, status: response.status, cause: error })
  },
)

/** 인터셉터가 봉투를 벗기므로 반환값이 곧 data 다. */
export const api = {
  get: <T>(url: string, config?: AxiosRequestConfig) => client.get<T, T>(url, config),
  post: <T>(url: string, body?: unknown, config?: AxiosRequestConfig) =>
    client.post<T, T>(url, body, config),
  put: <T>(url: string, body?: unknown, config?: AxiosRequestConfig) =>
    client.put<T, T>(url, body, config),
  patch: <T>(url: string, body?: unknown, config?: AxiosRequestConfig) =>
    client.patch<T, T>(url, body, config),
  delete: <T>(url: string, config?: AxiosRequestConfig) => client.delete<T, T>(url, config),
}
