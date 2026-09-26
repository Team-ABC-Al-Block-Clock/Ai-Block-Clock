import type { WithdrawalStatus } from '@/api/withdrawals'
import { ApiError, WITHDRAWAL_ERROR } from '@/api/errors'
import { dateTimeLabel } from '@/lib/format'

const STATUS_TEXT: Record<WithdrawalStatus, { title: string; note: string }> = {
  REQUESTED: {
    title: '심사 대기',
    note: '관리자 심사를 기다리고 있습니다. 심사 전에는 취소할 수 있습니다.',
  },
  APPROVED: { title: '승인', note: '승인됐습니다. 곧 지갑으로 보냅니다.' },
  PAYING: {
    title: '지갑으로 보내는 중',
    note: '체인에 전송을 보냈습니다. 보통 30초 안에 끝납니다.',
  },
  PAID: { title: '지급 완료', note: '연결된 지갑으로 abcToken 을 보냈습니다.' },
  CANCELED: { title: '취소', note: '신청을 취소했습니다. 묶였던 금액은 정산금으로 돌아왔습니다.' },
  REJECTED: { title: '반려', note: '신청이 반려됐습니다. 묶였던 금액은 정산금으로 돌아왔습니다.' },
}

export const withdrawalStatusText = (status: WithdrawalStatus) =>
  STATUS_TEXT[status] ?? { title: status, note: '' }

const availableAtOf = (data: unknown) =>
  typeof data === 'object' && data !== null && 'availableAt' in data
    ? String((data as { availableAt: unknown }).availableAt)
    : null

export function withdrawalErrorText(error: unknown): string {
  if (!(error instanceof ApiError)) return '출금을 신청하지 못했습니다.'
  if (error.isNetworkError) return '서버에 닿지 못했습니다. 연결을 확인하고 다시 신청해 주세요.'
  switch (error.code) {
    case WITHDRAWAL_ERROR.AMOUNT_OUT_OF_RANGE:
      return '한 번에 1 ~ 1,000 ABC 까지 출금할 수 있습니다.'
    case WITHDRAWAL_ERROR.PAYOUT_INSUFFICIENT:
    case WITHDRAWAL_ERROR.CONVERSION_INSUFFICIENT:
      return '정산금이 부족합니다.'
    case WITHDRAWAL_ERROR.ALREADY_IN_PROGRESS:
      return '처리 중인 출금이 있어 새로 신청할 수 없습니다.'
    case WITHDRAWAL_ERROR.TOO_FREQUENT: {
      const at = availableAtOf(error.data)
      return at
        ? `출금은 24시간에 한 번입니다. ${dateTimeLabel(at)} 부터 다시 신청할 수 있습니다.`
        : '출금은 24시간에 한 번입니다.'
    }
    case WITHDRAWAL_ERROR.ALREADY_REQUESTED:
      return '같은 신청을 처리하고 있습니다. 잠시 뒤 다시 확인해 주세요.'
    case WITHDRAWAL_ERROR.INVALID_STATE:
      return '이미 심사가 끝나 취소할 수 없습니다.'
    case WITHDRAWAL_ERROR.IDENTITY_NOT_VERIFIED:
      return '본인 확인을 승인받아야 출금할 수 있습니다.'
    default:
      return error.message || '출금을 신청하지 못했습니다.'
  }
}
