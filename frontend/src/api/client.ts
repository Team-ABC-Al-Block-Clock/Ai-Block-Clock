import axios, { AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'

import { AUTH_ERROR, ApiError, isApiErrorBody } from '@/api/errors'
import { env } from '@/lib/env'
import { tokenStorage } from '@/lib/tokenStorage'

/** 성공 응답 봉투. { "status": "success", "data": ... } */
interface ApiSuccessBody<T> {
  status: 'success'
  data: T
}

/** POST /auth/reissue 응답. Refresh Token 은 바디가 아니라 Set-Cookie 로 온다. */
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
 * 인증이 완전히 실패했을 때 호출된다.
 * 인증 스토어가 등록해서 상태 정리와 로그인 화면 이동을 처리한다.
 * api 계층이 라우터를 직접 알지 않도록 콜백으로 뺐다.
 */
let onAuthFailure: (() => void) | null = null
export function setOnAuthFailure(handler: (() => void) | null) {
  onAuthFailure = handler
}

// ── 요청 인터셉터 ────────────────────────────────────────────────
client.interceptors.request.use((config) => {
  const token = tokenStorage.get()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// ── 토큰 재발급 (단일 실행) ──────────────────────────────────────
// 서버가 재발급 때마다 Refresh Token 을 교체하고 이전 것을 폐기한다.
// 재발급이 두 번 나가면 두 번째는 폐기된 토큰을 보내 AUTH_004 가 되고,
// 명세상 그 경우 서버가 세션을 종료한다. 반드시 한 번만 보내야 한다.
let refreshing: Promise<string> | null = null

async function requestReissue(): Promise<string> {
  // 인터셉터를 타지 않는 맨 axios 로 보낸다.
  // client 로 보내면 이 요청의 401 이 다시 재발급을 부르며 재귀한다.
  // baseURL 은 client 것을 참조한다 — 직접 조립하면 설정이 두 곳으로 갈린다.
  const { data } = await axios.post<ApiSuccessBody<ReissuedToken>>(
    '/auth/reissue',
    undefined, // Refresh Token 은 쿠키로 자동 전송된다. 바디는 없다.
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

function failAuth() {
  tokenStorage.clear()
  onAuthFailure?.()
}

// ── 응답 인터셉터 ────────────────────────────────────────────────
client.interceptors.response.use(
  // 성공: 봉투를 벗겨 data 만 넘긴다. 화면이 res.data.data 를 몰라도 된다.
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

    // 응답 자체를 못 받은 경우 — 네트워크 끊김, 타임아웃
    if (!response) {
      throw new ApiError('서버에 연결할 수 없습니다. 네트워크를 확인해 주세요.', { cause: error })
    }

    const body = response.data
    const code = isApiErrorBody(body) ? body.code : undefined
    const message = isApiErrorBody(body) ? body.message : error.message

    if (response.status === 401 && config) {
      // Access Token 만료만 재발급 대상이다.
      // 미전달(AUTH_005)·유효하지 않음(AUTH_006)은 재발급해도 결과가 같다.
      if (code === AUTH_ERROR.EXPIRED && !config._retried) {
        config._retried = true
        try {
          const accessToken = await reissueOnce()
          config.headers.Authorization = `Bearer ${accessToken}`
          return client.request(config)
        } catch {
          // Refresh Token 쪽 실패(AUTH_001~004)는 회복 수단이 없다.
          failAuth()
          throw new ApiError('세션이 만료되었습니다. 다시 로그인해 주세요.', {
            code,
            status: 401,
            cause: error,
          })
        }
      }

      failAuth()
    }

    throw new ApiError(message, { code, status: response.status, cause: error })
  },
)

/**
 * 타입이 붙은 요청 헬퍼.
 * 응답 인터셉터가 봉투를 벗기므로 반환값이 곧 data 다.
 */
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
 * 앱 시작 시 세션 복구.
 * Access Token 은 메모리에만 있어 새로고침하면 사라진다.
 * Refresh Token 쿠키가 살아 있으면 조용히 다시 받아온다.
 *
 * 인증 스토어(S15P21A403-180)가 부팅 시 한 번 호출한다.
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
