import { describe, expect, it } from 'vitest'

import type { CreditTransaction } from '@/api/credits'
import { usagePeriods } from '@/lib/stats/usageTrend'

const row = (
  transactionType: CreditTransaction['transactionType'],
  amount: string,
  occurredAt: string,
  executionId: string | null = null,
): CreditTransaction => ({
  transactionId: 1,
  transactionType,
  amount,
  occurredAt,
  executionId,
  transactionHash: null,
})

describe('usagePeriods', () => {
  it('선차감에서 환불을 빼고 실행 ID 를 한 번씩 센다', () => {
    const periods = usagePeriods([
      row('RESERVE', '-30.000000', '2026-09-02T10:00:00Z', 'exe_2'),
      row('REFUND', '10.000000', '2026-09-02T10:05:00Z', 'exe_2'),
      row('RESERVE', '-5.000000', '2026-09-02T11:00:00Z', 'exe_3'),
      row('RESERVE', '-12.000000', '2026-09-01T09:00:00Z', 'exe_1'),
    ])

    expect(periods).toEqual([
      { periodStart: '2026-09-01', amount: '12.000000', executionCount: 1 },
      { periodStart: '2026-09-02', amount: '25.000000', executionCount: 2 },
    ])
  })

  it('충전과 정산금 옮기기는 사용량이 아니다', () => {
    expect(
      usagePeriods([
        row('CHARGE', '500.000000', '2026-09-01T00:00:00Z'),
        row('PAYOUT_CONVERSION', '5.000000', '2026-09-01T00:00:00Z'),
      ]),
    ).toEqual([])
  })
})
