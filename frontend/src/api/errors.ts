/**
 * 서버 실패 응답 봉투.
 *   { "status": "error", "code": "AGENT_001", "message": "..." }
 */
export interface ApiErrorBody {
  status: 'error'
  code: string
  message: string
}

/**
 * 정규화된 API 오류.
 *
 * 화면에서는 HTTP status 대신 `code` 로 분기한다.
 * 400 하나에도 COMMON_001(입력값 누락)과 AGENT_003(visibility 값 오류)이 섞여 있어
 * status 만으로는 무엇이 잘못됐는지 구분할 수 없다.
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
 * 인증 실패 코드. 401 안에서도 처리가 갈린다.
 *
 * AUTH_001~004 는 Refresh Token(HttpOnly 쿠키) 문제라 재발급이 불가능하다.
 * AUTH_005~007 은 Access Token 문제이고, 이 중 만료(007)만 재발급으로 회복된다.
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

/**
 * 프로필·가입 실패 코드. 회원가입 완료와 프로필 수정이 함께 쓴다.
 *
 * 화면이 코드로 분기하는 것은 ALREADY_SIGNED_UP 하나지만 나머지도 적어 둔다 —
 * mock 핸들러가 여기를 보고 응답해야 지어낸 코드가 섞이지 않는다.
 * 이름 누락은 도메인 코드가 아니라 COMMON_001 이라 여기 없다.
 */
export const USER_ERROR = {
  /** 이름이 2~20자가 아니거나 한 줄 소개가 40자를 넘음 */
  PROFILE_INVALID: 'USER_002',
  /** 약관 동의 또는 계정 복구 불가 고지 확인이 빠짐 */
  CONSENT_REQUIRED: 'USER_003',
  /** 프로필 이미지가 PNG·JPG 가 아님 */
  IMAGE_TYPE: 'USER_004',
  /** 프로필 이미지가 5MB 를 넘음 */
  IMAGE_SIZE: 'USER_005',
  /** 이미 가입 절차를 마친 사용자가 다시 호출 — 409 */
  ALREADY_SIGNED_UP: 'USER_006',
  /** 이미지 교체와 삭제를 한 요청에 같이 보냄 */
  IMAGE_CONFLICT: 'USER_007',
} as const

/** 응답 본문이 서버의 실패 봉투 모양인지 확인한다. */
export function isApiErrorBody(body: unknown): body is ApiErrorBody {
  if (typeof body !== 'object' || body === null) return false
  const b = body as Record<string, unknown>
  return b.status === 'error' && typeof b.code === 'string' && typeof b.message === 'string'
}
