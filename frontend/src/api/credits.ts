import { api } from '@/api/client'
import type { Page, PageParams } from '@/api/page'

/**
 * ABC 크레딧 API. 필드명은 추정이다 — `api/page.ts` 참고.
 *
 * ABC 는 사용자 간 전송이 없어서 상대 계정 필드가 없다.
 * 공급자 정산금은 별개 자산이므로 여기에 섞지 않는다.
 */

export interface CreditBalance {
  balance: number
  /** 실행 선차감으로 잠긴 금액 */
  locked: number
  updatedAt: string
}

export type CreditTransactionType = 'CHARGE' | 'USE' | 'REFUND'

export interface CreditTransaction {
  transactionId: string
  type: CreditTransactionType
  /** 부호 없는 절대값. 증감 방향은 `type` 이 정한다. */
  amount: number
  balanceAfter: number
  executionId: string | null
  agentName: string | null
  createdAt: string
}

export const getCreditBalance = () => api.get<CreditBalance>('/credits/balance')

export const getCreditTransactions = (params: PageParams = {}) =>
  api.get<Page<CreditTransaction>>('/credits/transactions', { params })
