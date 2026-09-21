/** `{ status: 'error', code, message }` */
export interface ApiErrorBody {
  status: 'error'
  code: string
  message: string
}

/**
 * 화면은 HTTP status 대신 `code` 로 분기한다 — 400 하나에 입력값 누락과
 * 값 검증 실패가 섞여 있어 status 만으로는 무엇이 잘못됐는지 알 수 없다.
 */
export class ApiError extends Error {
  /** 서버가 준 도메인 오류 코드. 네트워크 오류 등 응답이 없으면 undefined */
  readonly code?: string
  /** HTTP 상태 코드. 응답을 못 받았으면 undefined */
  readonly status?: number

  constructor(message: string, options: { code?: string; status?: number; cause?: unknown } = {}) {
    super(message, { cause: options.cause })
    this.name = 'ApiError'
    this.code = options.code
    this.status = options.status
  }

  /** 응답을 받지 못한 경우(네트워크 끊김, 타임아웃) */
  get isNetworkError() {
    return this.status === undefined
  }
}

/**
 * 401 안에서도 처리가 갈린다. AUTH_001~004 는 Refresh Token 문제라 재발급이 불가능하고,
 * AUTH_005~007 은 Access Token 문제이며 이 중 만료(007)만 회복된다.
 */
export const AUTH_ERROR = {
  /** Refresh Token 쿠키 미전달 */
  REFRESH_MISSING: 'AUTH_001',
  /** Refresh Token 형식·서명 오류 */
  REFRESH_INVALID: 'AUTH_002',
  /** Refresh Token 만료 */
  REFRESH_EXPIRED: 'AUTH_003',
  /** 이미 폐기된 Refresh Token 재사용 — 서버가 세션을 종료한다 */
  REFRESH_REUSED: 'AUTH_004',

  /** Access Token 미전달 */
  MISSING: 'AUTH_005',
  /** Access Token 유효하지 않음 */
  INVALID: 'AUTH_006',
  /** Access Token 만료 — 재발급을 시도할 수 있는 유일한 경우 */
  EXPIRED: 'AUTH_007',
} as const

export function isApiErrorBody(body: unknown): body is ApiErrorBody {
  if (typeof body !== 'object' || body === null) return false
  const b = body as Record<string, unknown>
  return b.status === 'error' && typeof b.code === 'string' && typeof b.message === 'string'
}
