import { useQuery } from '@tanstack/react-query'

import {
  getCreditTransactions,
  type CreditTransaction,
  type CreditTransactionType,
} from '@/api/credits'
import { queryKeys } from '@/lib/queryKeys'
import {
  dailySeries,
  TREND_HEIGHT,
  TREND_WIDTH,
  trendPath,
  trendTicks,
} from '@/lib/stats/salesTrend'
import { usagePeriods, usageTotals } from '@/lib/stats/usageTrend'

import { TREND_PAGE_LIMIT } from './config'

type Range = { from?: string; to?: string }

/** 한 유형을 50건씩 끝까지 읽는다. TREND_PAGE_LIMIT 장에서 끊는다. */
async function readAll(transactionType: CreditTransactionType, range: Range) {
  const rows: CreditTransaction[] = []
  let cursor: string | undefined
  for (let i = 0; i < TREND_PAGE_LIMIT; i++) {
    const page = await getCreditTransactions({ ...range, transactionType, size: 50, cursor })
    rows.push(...page.content)
    if (!page.hasNext || !page.nextCursor) return { rows, cut: false }
    cursor = page.nextCursor
  }
  return { rows, cut: true }
}

/** 기간 안의 사용 금액 · 실행 수 추이. */
export function useUsageTrend(range: Range) {
  const trend = useQuery({
    queryKey: queryKeys.credits.usageTrend(range),
    // 사용량에 드는 것은 선차감과 환불뿐이다. 둘만 따로, 나란히 읽는다.
    queryFn: async () => {
      const [reserves, refunds] = await Promise.all([
        readAll('RESERVE', range),
        readAll('REFUND', range),
      ])
      // 끊긴 채로 합계를 보여 주면 적게 쓴 것처럼 보인다. 끊겼다고 알린다.
      return { rows: [...reserves.rows, ...refunds.rows], cut: reserves.cut || refunds.cut }
    },
  })
  const trendRows = trend.data?.rows ?? []
  const totals = usageTotals(trendRows)
  const series = trend.data ? dailySeries(usagePeriods(trendRows), range) : []
  const spendLine = trendPath(
    series.map((p) => p.amount),
    TREND_WIDTH,
    TREND_HEIGHT,
  )
  const runLine = trendPath(
    series.map((p) => p.executionCount),
    TREND_WIDTH,
    TREND_HEIGHT,
    40,
  )

  return {
    isLoaded: trend.data !== undefined,
    isError: trend.isError,
    cut: trend.data?.cut ?? false,
    totals,
    spendLine,
    runLine,
    ticks: trendTicks(series),
  }
}
