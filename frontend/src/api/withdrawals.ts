import { api } from '@/api/client'

/** 정산금 출금. 충전한 ABC 는 서비스 안에서만 쓰는 이용권이라 출금되지 않는다. */

/** REQUESTED → APPROVED → PAYING → PAID. CANCELED · REJECTED · FAILED 로 빠지고, 확인이 늦으면 ON_HOLD 에 머문다. */
export type WithdrawalStatus =
  | 'REQUESTED'
  | 'APPROVED'
  | 'PAYING'
  | 'ON_HOLD'
  | 'PAID'
  | 'CANCELED'
  | 'REJECTED'
  | 'FAILED'

/** 아직 끝나지 않은 출금. 이런 출금이 있으면 새로 신청하지 못한다 */
export const IN_PROGRESS_WITHDRAWAL: readonly WithdrawalStatus[] = [
  'REQUESTED',
  'APPROVED',
  'PAYING',
  'ON_HOLD',
]

export interface Withdrawal {
  withdrawalId: string
  status: WithdrawalStatus
  amount: string
  /** 신청할 때의 로그인 지갑 */
  walletAddress: string
  requestedAt: string
  reviewedAt: string | null
  rejectReason: string | null
}

export const WITHDRAWAL_LIMITS = {
  MIN_AMOUNT: 1,
  MAX_AMOUNT: 1000,
  INTERVAL_HOURS: 24,
} as const

/** 202. requestId 는 누를 때마다 새로, 재시도는 같은 값으로 보낸다. */
export const createWithdrawal = (params: { requestId: string; amount: string }) =>
  api.post<Withdrawal>('/withdrawals', params)

/** REQUESTED 일 때만 된다. */
export const cancelWithdrawal = (withdrawalId: string) =>
  api.post<Withdrawal>(`/withdrawals/${withdrawalId}/cancel`)
