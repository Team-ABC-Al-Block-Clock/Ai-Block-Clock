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
}

/** 서버 LedgerEventType 의 SETTLEMENT 은 내 계정을 거치지 않아 오지 않는다. */
export type CreditTransactionType = 'CHARGE' | 'RESERVE' | 'REFUND'

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
