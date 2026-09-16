import axios, { AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'

import { AUTH_ERROR, ApiError, isApiErrorBody } from '@/api/errors'
import { env } from '@/lib/env'
import { tokenStorage } from '@/lib/tokenStorage'

/** `{ status: 'success', data }` */
interface ApiSuccessBody<T> {
  status: 'success'
  data: T
}

/** Refresh Token 은 바디가 아니라 Set-Cookie 로 온다. */
interface ReissuedToken {
  accessToken: string
  tokenType: string
  expiresIn: number
}

/** 재발급을 이미 시도한 요청인지 표시한다. 무한 재시도를 막는다. */
interface RetriableConfig extends InternalAxiosRequestConfig {
  _retried?: boolean
}

/** API 루트. mock 핸들러(`mocks/handlers/*`)가 같은 값을 쓴다 —
 *  한쪽만 바뀌면 mock 이 요청을 못 잡고 조용히 실서버로 나간다. */
export const API_BASE_URL = `${env.apiBaseUrl}/v1`

export const client = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30_000,
  // Content-Type 은 지정하지 않는다. 여기에 application/json 을 박으면
  // FormData 가 JSON 문자열로 바뀌어 나간다.
  // Refresh Token 이 HttpOnly 쿠키로 오간다. 이게 없으면 재발급이 항상 AUTH_001 이다.
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

client.interceptors.request.use((config) => {
  const token = tokenStorage.get()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 서버가 재발급 때마다 Refresh Token 을 교체하고 이전 것을 폐기한다. 두 번 나가면
// 두 번째는 폐기된 토큰을 보내 AUTH_004 가 되고 서버가 세션을 끝낸다.
let refreshing: Promise<string> | null = null

async function requestReissue(): Promise<string> {
  // 맨 axios 로 보낸다 — client 로 보내면 이 요청의 401 이 다시 재발급을 부르며 재귀한다.
  const { data } = await axios.post<ApiSuccessBody<ReissuedToken>>(
    '/auth/reissue',
    undefined, // Refresh Token 은 쿠키로 간다
    { baseURL: client.defaults.baseURL, withCredentials: true },
  )
  const accessToken = data.data.accessToken
  tokenStorage.set(accessToken)
  return accessToken
}

/** 진행 중인 재발급이 있으면 그 결과를 함께 기다린다. */
function reissueOnce(): Promise<string> {
  refreshing ??= requestReissue().finally(() => {
    refreshing = null
  })
  return refreshing
}

function failAuth(code?: string) {
  tokenStorage.clear()
  onAuthFailure?.(code)
}

/** 인터셉터를 타지 않은 재발급 오류에서 도메인 코드만 꺼낸다. */
function codeOf(error: unknown): string | undefined {
  const body = error instanceof AxiosError ? error.response?.data : undefined
  return isApiErrorBody(body) ? body.code : undefined
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

  async (error: unknown) => {
    if (!(error instanceof AxiosError)) {
      throw new ApiError('알 수 없는 오류가 발생했습니다.', { cause: error })
    }

    const response = error.response
    const config = error.config as RetriableConfig | undefined

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
    const code = isApiErrorBody(body) ? body.code : undefined
    const message = isApiErrorBody(body) ? body.message : error.message

    if (response.status === 401 && config) {
      // 만료만 재발급 대상이다. 미전달·무효는 재발급해도 결과가 같다.
      if (code === AUTH_ERROR.EXPIRED && !config._retried) {
        config._retried = true
        try {
          const accessToken = await reissueOnce()
          config.headers.Authorization = `Bearer ${accessToken}`
          return client.request(config)
        } catch (reissueError) {
          // 원인은 재발급 응답에 있다 — 원 요청의 코드를 넘기면 폐기된 세션까지
          // '만료' 로 안내하게 된다.
          failAuth(codeOf(reissueError))
          throw new ApiError('세션이 만료되었습니다. 다시 로그인해 주세요.', {
            code,
            status: 401,
            cause: error,
          })
        }
      }

      failAuth(code)
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

/**
 * 앱 시작 시 세션 복구. Access Token 은 메모리에만 있어 새로고침하면 사라지고,
 * Refresh Token 쿠키가 살아 있으면 조용히 다시 받아온다.
 */
export async function restoreSession(): Promise<boolean> {
  try {
    await reissueOnce()
    return true
  } catch {
    tokenStorage.clear()
    return false
  }
}
