/** `{ status: 'FAIL', data: null, error: { code, message, data } }` — `support/response/ApiResult` */
export interface ApiErrorBody {
  status: string
  error: {
    code: string
    message: string
    /** 오류를 짚어 주는 값. 응답마다 모양이 다르고 없으면 null */
    data?: unknown
  }
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
  /** 실패 봉투의 `error.data` */
  readonly data?: unknown

  constructor(
    message: string,
    options: { code?: string; status?: number; data?: unknown; cause?: unknown } = {},
  ) {
    super(message, { cause: options.cause })
    this.name = 'ApiError'
    this.code = options.code
    this.status = options.status
    this.data = options.data
  }

  /** 응답을 받지 못한 경우(네트워크 끊김, 타임아웃) */
  get isNetworkError() {
    return this.status === undefined
  }
}

/**
 * 세션 인증 실패 코드. 이름은 서버의 `ErrorType` 을 그대로 따른다.
 *
 * 만료·폐기를 가르는 코드는 없다. 쿠키가 없든 세션이 만료됐든 알 수 없는 세션이든
 * 서버는 `ApiAuthenticationEntryPoint` 에서 `AUTH_005` 하나로 답한다. 만료 판정을
 * Spring Session 이 하고 만료된 세션은 조회조차 되지 않아 서버도 「없음」과 「만료」를
 * 구분하지 않는다(백엔드 `docs/SESSION_AUTH.md`).
 *
 * 어느 쪽이든 회복 수단이 없다 — 지갑 서명 로그인부터 다시 한다.
 */
export const AUTH_ERROR = {
  /** 쿠키가 없거나, 세션이 만료됐거나, 알 수 없는 세션이다 */
  NOT_IDENTIFIED: 'AUTH_005',
  /** 세션은 있으나 요청자를 특정할 수 없다 — 서버 `ErrorType` 에 선언만 있고 아직 던지는 곳이 없다 */
  NOT_IDENTIFIABLE: 'AUTH_006',
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

/**
 * 노드·참고 자료 실패 코드.
 *
 * `NODE_006` 은 빈 파일과 형식 불가가 함께 쓴다 — 응답만으로는 사유를 가를 수 없어,
 * 제출 전 검사(`describeRefFileProblem`)가 먼저 막고 이 문구는 그물로만 둔다.
 */
export const NODE_ERROR = {
  /** 없는 노드 — 404 */
  NOT_FOUND: 'NODE_001',
  /** 다른 곳에서 이미 지워진 자료 — 404 */
  REF_FILE_NOT_FOUND: 'NODE_005',
  /** 빈 파일이거나 받지 않는 형식 — 400 */
  REF_FILE_REJECTED: 'NODE_006',
  /** 노드에 붙은 자료의 합계가 10MB 를 넘음 — 413 */
  REF_FILE_TOO_LARGE: 'NODE_007',
} as const

export const EXECUTION_ERROR = {
  RESULT_NOT_FOUND: 'EXECUTION_007',
} as const

/**
 * ABC 충전 실패 코드.
 *
 * 토큰은 이미 지갑에서 나간 뒤라 어느 코드든 「다시 보내세요」로 끝낼 수 없다.
 * 화면 문구는 `lib/chargeMessage.ts` 가 만든다.
 */
export const CHARGE_ERROR = {
  /** 없는 충전 요청 — 404 */
  NOT_FOUND: 'PAYMENT_001',
  /** 남의 충전 요청 — 403 */
  FORBIDDEN: 'PAYMENT_002',
  /** 다른 사용자가 이미 이 거래로 충전을 요청함 — 409 */
  TRANSACTION_ALREADY_USED: 'PAYMENT_003',
  /** 금액이 0 이하이거나 소수 6자리를 넘음 — 400 */
  INVALID_AMOUNT: 'PAYMENT_005',
  /** 로그인한 지갑이 입금 보관 지갑 — 403 */
  SENDER_IS_CUSTODY: 'PAYMENT_009',
} as const

/** 출금 · 정산금 옮기기 실패 코드. 화면 문구는 `lib/withdrawalMessage.ts` 가 만든다. */
export const WITHDRAWAL_ERROR = {
  /** 1 ~ 1,000 ABC 밖 — 400, data 에 minimumAmount · maximumAmount */
  AMOUNT_OUT_OF_RANGE: 'PAYMENT_010',
  /** 정산금 부족 — 409, data 에 payoutAmount */
  PAYOUT_INSUFFICIENT: 'PAYMENT_011',
  /** 처리 중인 출금이 있음 — 409 */
  ALREADY_IN_PROGRESS: 'PAYMENT_012',
  /** 24시간이 안 지남 — 409, data 에 availableAt */
  TOO_FREQUENT: 'PAYMENT_013',
  /** 같은 requestId 가 동시에 들어옴 — 409 */
  ALREADY_REQUESTED: 'PAYMENT_014',
  NOT_FOUND: 'PAYMENT_015',
  /** 이미 심사가 끝나 취소할 수 없음 — 409 */
  INVALID_STATE: 'PAYMENT_016',
  /** 본인 확인 미승인 — 403 */
  IDENTITY_NOT_VERIFIED: 'PAYMENT_017',
  /** 옮길 정산금 부족 — 409, data 에 payoutAmount */
  CONVERSION_INSUFFICIENT: 'PAYMENT_018',
} as const

/**
 * 응답 본문이 서버의 실패 봉투 모양인지 확인한다.
 *
 * `status` 값('FAIL')이 아니라 `error` 안에 code·message 가 있는지로 본다. 서버의 CSRF·인증
 * 거절은 Spring 기본 오류 본문(`{ timestamp, status, error: 'Forbidden', path }`)으로 오는데,
 * 거기도 `error` 키가 있지만 문자열이라 여기서 걸러진다.
 *
 * 이 판정이 틀리면 code 와 message 가 통째로 버려져 화면의 코드 분기와 401 사유가 사라진다.
 */
export function isApiErrorBody(body: unknown): body is ApiErrorBody {
  if (typeof body !== 'object' || body === null) return false
  const { error } = body as { error?: unknown }
  if (typeof error !== 'object' || error === null) return false
  const e = error as Record<string, unknown>
  return typeof e.code === 'string' && typeof e.message === 'string'
}
