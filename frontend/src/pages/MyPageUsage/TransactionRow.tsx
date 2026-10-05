import type { CreditTransaction } from '@/api/credits'
import ListRow from '@/components/mypage/ListRow'
import { priceLabel, shortAddress, timeLabel } from '@/lib/format'

import { TYPE_COLORS, TYPE_LABELS } from './config'

/** 사용 내역 탭의 한 줄. */
export default function TransactionRow({ item: h }: { item: CreditTransaction }) {
  return (
    <ListRow>
      <div style={{ width: '170px' }}>{timeLabel(h.occurredAt)}</div>
      <div
        className="font-semibold"
        style={{ width: '120px', color: TYPE_COLORS[h.transactionType] }}
      >
        {TYPE_LABELS[h.transactionType] ?? h.transactionType}
      </div>
      <div className="font-semibold text-white" style={{ width: '190px' }}>
        {priceLabel(h.amount)}
      </div>
      {/* 실행 상세 화면이 생기면 링크를 붙인다. */}
      <div style={{ width: '220px', color: 'rgba(255,255,255,.7)' }}>{h.executionId ?? '—'}</div>
      <div style={{ width: '160px', color: 'rgba(255,255,255,.7)' }}>
        {h.transactionHash ? shortAddress(h.transactionHash) : '—'}
      </div>
    </ListRow>
  )
}
