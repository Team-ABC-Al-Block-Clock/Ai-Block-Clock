import type { CreditTransaction } from '@/api/credits'
import type { SettlementStatistics } from '@/api/settlements'

/**
 * 사용 내역을 날짜별 사용량으로 묶는다. 서버에 사용량 통계가 없어 화면이 직접 센다.
 * 사용 코인은 선차감에서 환불을 뺀 값이고, 실행 수는 선차감이 생긴 실행 ID 수다.
 * 모양을 정산 통계의 periods 와 맞춰 판매 내역과 같은 그래프 함수를 쓴다.
 */
export function usagePeriods(rows: CreditTransaction[]): SettlementStatistics['periods'] {
  const days = new Map<string, { spent: number; executions: Set<string> }>()
  for (const row of rows) {
    if (row.transactionType !== 'RESERVE' && row.transactionType !== 'REFUND') continue
    const day = row.occurredAt.slice(0, 10)
    const bucket = days.get(day) ?? { spent: 0, executions: new Set<string>() }
    // 선차감은 음수로 온다. 부호를 뒤집어 더하면 환불은 저절로 빠진다.
    bucket.spent -= Number(row.amount)
    if (row.transactionType === 'RESERVE' && row.executionId) bucket.executions.add(row.executionId)
    days.set(day, bucket)
  }
  return [...days]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([periodStart, b]) => ({
      periodStart,
      amount: Math.max(0, b.spent).toFixed(6),
      executionCount: b.executions.size,
    }))
}

/** 기간 전체의 사용 코인과 실행 수. 날짜별 실행 수를 더하면 자정을 넘긴 실행이 두 번 세진다. */
export function usageTotals(rows: CreditTransaction[]) {
  const executions = new Set<string>()
  let spent = 0
  for (const row of rows) {
    if (row.transactionType !== 'RESERVE' && row.transactionType !== 'REFUND') continue
    spent -= Number(row.amount)
    if (row.transactionType === 'RESERVE' && row.executionId) executions.add(row.executionId)
  }
  return { spent: Math.max(0, spent), executionCount: executions.size }
}
