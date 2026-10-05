import type { CreditTransactionType } from '@/api/credits'
import type { HistoryTone } from '@/lib/wallet/withdrawalMessage'

/** 마켓(9)·마이페이지 목록(10)과 다르다. */
export const PAGE_SIZE = 20

export type ListTab = 'usage' | 'wallet'

export const LIST_TABS = [
  { key: 'usage', label: '사용 내역' },
  { key: 'wallet', label: '충전 · 출금' },
] as const satisfies readonly { key: ListTab; label: string }[]

export const TYPE_LABELS: Record<CreditTransactionType, string> = {
  CHARGE: '충전',
  RESERVE: '사용',
  REFUND: '환불',
  PAYOUT_CONVERSION: '정산금 전환',
}

export const WALLET_POLL_MS = 15_000
/** 기간 전체를 50건씩 읽는다. 너무 길면 여기서 끊어 요청이 끝없이 늘지 않게 한다. */
export const TREND_PAGE_LIMIT = 20

export const TONE_COLORS: Record<HistoryTone, string> = {
  done: '#9ad0b4',
  progress: '#c9a9dd',
  closed: '#ff8a8a',
}

/** 들어오는 것은 초록, 나가는 것은 분홍. */
export const TYPE_COLORS: Record<CreditTransactionType, string> = {
  CHARGE: '#9ad0b4',
  RESERVE: '#ffb0cd',
  REFUND: '#9ad0b4',
  PAYOUT_CONVERSION: '#9ad0b4',
}
