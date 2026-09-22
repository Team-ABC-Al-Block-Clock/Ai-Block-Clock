import axios, { AxiosError, type AxiosRequestConfig } from 'axios'

import { ApiError, isApiErrorBody } from '@/api/errors'
import { env } from '@/lib/env'

/** `{ status: 'success', data }` */
interface ApiSuccessBody<T> {
  status: 'success'
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
  // sessionToken 이 HttpOnly 쿠키로 오간다. 이게 없으면 모든 요청이 AUTH_005 다.
  withCredentials: true,
})

/**
 * 회복 불가능한 인증 실패를 스토어에 알린다 — api 계층이 라우터를 알지 않게 콜백으로 뺐다.
 * 코드를 함께 넘긴다. 만료와 폐기는 로그인 화면이 띄울 문구가 다르다.
 */
export type AuthFailureHandler = (code?: string) => void

let onAuthFailure: AuthFailureHandler | null = null
export function setOnAuthFailure(handler: AuthFailureHandler | null) {
  onAuthFailure = handler
}

client.interceptors.response.use(
  // 봉투를 벗겨 data 만 넘긴다 — 화면이 res.data.data 를 몰라도 된다.
  (response) => {
    const body = response.data
    if (typeof body === 'object' && body !== null && 'status' in body && 'data' in body) {
      return (body as ApiSuccessBody<unknown>).data
    }
    return body
  },

  (error: unknown) => {
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

    // 401 은 전부 회복 불가다 — 자동 재발급 API 가 없어 지갑 서명 로그인부터 다시 한다.
    if (response.status === 401) onAuthFailure?.(code)

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
