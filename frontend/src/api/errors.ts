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
 * 세션 쿠키 실패 코드. 다섯 가지 모두 회복 수단이 없다 — 지갑 서명 로그인부터 다시 한다.
 * 문구만 갈려서 `stores/authStore.ts` 의 `endReasonFromAuthCode` 가 사유로 옮긴다.
 */
export const AUTH_ERROR = {
  /** sessionToken 쿠키 미전달 — 로그인 전이거나 쿠키가 저장되지 않았다 */
  MISSING: 'AUTH_005',
  /** 세션은 있으나 요청자를 특정할 수 없다 */
  UNIDENTIFIABLE: 'AUTH_006',
  /** 세션 만료 */
  EXPIRED: 'AUTH_013',
  /** 로그아웃했거나 다른 곳에서 재로그인해 이 세션이 종료됐다 */
  ENDED: 'AUTH_014',
  /** 알 수 없거나 잘못된 세션 토큰 */
  INVALID: 'AUTH_018',
} as const

/**
 * 프로필·가입 실패 코드. 회원가입 완료와 프로필 수정이 함께 쓴다.
 *
 * 화면이 코드로 분기하는 것은 ALREADY_SIGNED_UP 하나지만 나머지도 적어 둔다 —
 * mock 핸들러가 여기를 보고 응답해야 지어낸 코드가 섞이지 않는다.
 * 이름 누락은 도메인 코드가 아니라 COMMON_001 이다.
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
