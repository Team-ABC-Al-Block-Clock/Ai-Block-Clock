import { api } from '@/api/client'
import type { CursorPage, CursorParams } from '@/api/page'

/**
 * ABC 크레딧 API. 판매 정산금은 장부 계정부터 달라 여기 섞지 않는다.
 * 금액은 소수점 오차를 피하려고 전부 문자열이다 — 화면은 받은 그대로 표시한다.
 */

export interface CreditBalance {
  availableAmount: string
  /** 실행이 선차감해 잠근 금액 */
  reservedAmount: string
  /** 판매로 받은 정산금. 실행에는 못 쓰고 출금하거나 ABC 로 옮긴다 */
  payoutAmount: string
}

/** 서버 LedgerEventType 의 SETTLEMENT 은 내 계정을 거치지 않아 오지 않는다. */
export type CreditTransactionType = 'CHARGE' | 'RESERVE' | 'REFUND' | 'PAYOUT_CONVERSION'

export interface CreditTransaction {
  /** 서버가 Long 이라 숫자다 */
  transactionId: number
  transactionType: CreditTransactionType
  /** 음수에만 부호가 붙는다. 양수는 그대로 — 화면이 부호를 만들지 않는다 */
  amount: string
  occurredAt: string
  /** 실행 때문에 생긴 줄만 값이 있다 */
  executionId: string | null
  /** 온체인 거래가 얽힌 줄(충전)만 값이 있다 */
  transactionHash: string | null
}

export interface CreditTransactionQuery extends CursorParams {
  transactionType?: CreditTransactionType
  /** YYYY-MM-DD, UTC */
  from?: string
  /** YYYY-MM-DD, UTC, 그날 포함 */
  to?: string
}

export const getCreditBalance = () => api.get<CreditBalance>('/credits/balance')

/** 기본 20건·최대 50건. from·to 는 둘 다 주거나 둘 다 생략한다. */
export const getCreditTransactions = (params: CreditTransactionQuery = {}) =>
  api.get<CursorPage<CreditTransaction>>('/credits/transactions', { params })

/** 확인 중 · 적립 완료 · 실패. CONFIRMED·FAILED 가 되면 더 묻지 않는다. */
export type ChargeStatus = 'PENDING' | 'CONFIRMED' | 'FAILED'

/** 실패한 이유. 화면 문구는 `lib/chargeMessage.ts` 가 만든다. */
export type ChargeFailureReason =
  | 'ONCHAIN_TRANSACTION_FAILED'
  | 'VERIFICATION_TIMEOUT'
  | 'SENDER_WALLET_MISMATCH'
  | 'CUSTODY_ADDRESS_MISMATCH'
  | 'DEPOSIT_TOKEN_CONTRACT_MISMATCH'
  | 'AMOUNT_MISMATCH'
  | 'CHAIN_ID_MISMATCH'
  | 'TRANSACTION_HASH_MISMATCH'
  | 'TRANSACTION_ALREADY_USED'
  | 'USER_CANCELED'

/** 접수 응답과 상태 조회 응답이 같은 모양이다 — 화면도 한 코드로 그린다. */
export interface ChargeStatusView {
  chargeId: string
  status: ChargeStatus
  /** 실패했을 때만 값이 있다 */
  failureReason: ChargeFailureReason | null
  /** 소문자로 온다 */
  transactionHash: string
  requestedAmount: string
  /** 완료됐을 때만 값이 있다 */
  creditedAmount: string | null
  requestedAt: string
  confirmedAt: string | null
}

/**
 * POST /credits/charges — 지갑이 준 거래 해시를 그대로 넘긴다. 영수증은 기다리지 않는다.
 *
 * 기다리는 동안 새로고침하면 해시가 어디에도 안 남아 토큰만 나간 상태가 된다.
 * 서버는 접수하면서 체인에 한 번 물어보므로 이미 적힌 거래는 그 자리에서 CONFIRMED 로 온다.
 *
 * 같은 해시를 다시 보내면 새로 만들지 않고 앞의 요청을 돌려준다 — 재시도를 막을 필요가 없다.
 * `amount` 는 ABC 단위(소수 6자리까지)다. 10^18 을 곱한 값이 아니다.
 */
export const createCharge = (params: { transactionHash: string; amount: string }) =>
  api.post<ChargeStatusView>('/credits/charges', params)

export const getCharge = (chargeId: string) =>
  api.get<ChargeStatusView>(`/credits/charges/${chargeId}`)

export interface PayoutConversion {
  /** 같은 requestId 를 다시 보냈으면 처음 옮긴 금액이다 */
  convertedAmount: string
  balance: CreditBalance
}

/** 정산금 → ABC 한 방향뿐이다. requestId 는 누를 때마다 새로, 재시도는 같은 값으로 보낸다. */
export const convertPayout = (params: { requestId: string; amount: string }) =>
  api.post<PayoutConversion>('/credits/payout-conversions', params)

export type CreditHistoryType = 'CHARGE' | 'WITHDRAWAL'

/** 충전과 출금이 같은 칸을 쓴다. id 는 type 마다 따로 매겨 화면 key 는 type + id 다. */
export interface CreditHistoryItem {
  type: CreditHistoryType
  id: string
  /** CHARGE 면 ChargeStatus, WITHDRAWAL 이면 WithdrawalStatus */
  status: string
  amount: string
  walletAddress: string
  /** 출금은 체인에 보낸 뒤(PAYING 부터)에만 있다 */
  transactionHash: string | null
  requestedAt: string
  /** 충전은 적립 시각, 출금은 지급 완료 시각 */
  completedAt: string | null
  /** 충전 실패 때만 */
  failureReason: ChargeFailureReason | null
  /** 출금 반려 때만 */
  rejectReason: string | null
}

export interface CreditHistoryQuery extends CursorParams {
  type?: CreditHistoryType
}

/** 요청 시각 최신순, 기본 20건·최대 50건. PAYING · ON_HOLD 는 이 목록을 다시 불러 확인한다. */
export const getCreditHistory = (params: CreditHistoryQuery = {}) =>
  api.get<CursorPage<CreditHistoryItem>>('/credits/history', { params })
