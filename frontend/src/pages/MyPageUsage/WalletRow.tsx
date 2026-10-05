import type { CreditHistoryItem } from '@/api/credits'
import ListRow from '@/components/mypage/ListRow'
import { priceLabel, shortAddress, timeLabel } from '@/lib/format'
import { historyStatus } from '@/lib/wallet/withdrawalMessage'

import { TONE_COLORS } from './config'

/** 충전 · 출금 탭의 한 줄. */
export default function WalletRow({ item: h }: { item: CreditHistoryItem }) {
  const status = historyStatus(h.type, h.status)
  return (
    <ListRow>
      <div style={{ width: '170px' }}>{timeLabel(h.requestedAt)}</div>
      <div
        className="font-semibold"
        style={{ width: '120px', color: h.type === 'CHARGE' ? '#9ad0b4' : '#ffb0cd' }}
      >
        {h.type === 'CHARGE' ? '충전' : '출금'}
      </div>
      <div className="font-semibold text-white" style={{ width: '190px' }}>
        {priceLabel(h.amount)}
      </div>
      <div
        className="truncate font-semibold"
        style={{ width: '220px', color: TONE_COLORS[status.tone] }}
        title={h.rejectReason ?? undefined}
      >
        {status.label}
      </div>
      <div style={{ width: '160px', color: 'rgba(255,255,255,.7)' }}>
        {h.transactionHash ? shortAddress(h.transactionHash) : '—'}
      </div>
    </ListRow>
  )
}
