import { useInfiniteQuery, useQuery } from '@tanstack/react-query'

import { getMyAgents } from '@/api/agents'
import { getCreditBalance } from '@/api/credits'
import { getSettlementStatistics, getSettlements } from '@/api/settlements'
import { priceLabel, runLabel, timeLabel } from '@/lib/format'
import { queryKeys } from '@/lib/queryKeys'
import { growthPercent, monthRanges, rangeOf } from '@/lib/stats/period'
import {
  dailySeries,
  TREND_HEIGHT,
  TREND_WIDTH,
  trendPath,
  trendTicks,
} from '@/lib/stats/salesTrend'
import { paths } from '@/routes/paths'

import { AGENT_COLUMNS, ENTRY_COLUMNS, PAGE_SIZE, type SalesTab } from './config'
import { growthLabel, shareOf, shareSlots, tableRows } from './salesView'

/** 판매 내역 화면의 통계 · 정산 목록. 기간과 목록 탭에 따라 다시 읽는다. */
export function useSalesData({ days, tab }: { days: number; tab: SalesTab }) {
  const range = rangeOf(days)
  const listParams = { ...range, size: PAGE_SIZE }

  const balance = useQuery({
    queryKey: queryKeys.credits.balance(),
    queryFn: getCreditBalance,
  })
  const statistics = useQuery({
    queryKey: queryKeys.settlements.statistics.list({ ...range, unit: 'DAY' }),
    queryFn: () => getSettlementStatistics({ ...range, unit: 'DAY' }),
  })
  // 총 매출은 기간 선택과 상관없는 누적이다.
  const lifetime = useQuery({
    queryKey: queryKeys.settlements.statistics.list({ unit: 'MONTH' }),
    queryFn: () => getSettlementStatistics({ unit: 'MONTH' }),
  })
  const months = monthRanges()
  const thisMonth = useQuery({
    queryKey: queryKeys.settlements.statistics.list({ ...months.current, unit: 'MONTH' }),
    queryFn: () => getSettlementStatistics({ ...months.current, unit: 'MONTH' }),
  })
  const lastMonth = useQuery({
    queryKey: queryKeys.settlements.statistics.list({ ...months.previous, unit: 'MONTH' }),
    queryFn: () => getSettlementStatistics({ ...months.previous, unit: 'MONTH' }),
  })
  const entries = useInfiniteQuery({
    queryKey: queryKeys.settlements.transactions.list(listParams),
    queryFn: ({ pageParam }) => getSettlements({ ...listParams, cursor: pageParam }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    enabled: tab === 'entry',
  })
  // 정산 응답에는 에이전트 이름이 없다. 많이 번 순으로 한 장이면 통계 줄 대부분이 잡힌다.
  const agentNames = useQuery({
    queryKey: queryKeys.agents.list({ sort: 'REVENUE', size: 30 }),
    queryFn: () => getMyAgents({ sort: 'REVENUE', size: 30 }),
    select: (page) => new Map(page.content.map((a) => [a.agentId, a.name])),
  })
  const agentName = (agentId: string | null) =>
    agentId === null ? '—' : (agentNames.data?.get(agentId) ?? '에이전트')

  const stats = statistics.data
  const total = Number(stats?.totalAmount ?? 0)
  // 누적 에이전트 호출 수의 합. 한 실행에서 에이전트 둘을 쓰면 두 번 센다.
  const agentCalls = (lifetime.data?.agents ?? [])
    .filter((a) => a.agentId !== null)
    .reduce((sum, a) => sum + a.executionCount, 0)
  const growth =
    thisMonth.data && lastMonth.data
      ? growthPercent(Number(thisMonth.data.totalAmount), Number(lastMonth.data.totalAmount))
      : null
  const metrics: { label: string; value: string; delta: string; href?: string }[] = [
    {
      label: '총 사용 횟수',
      value: lifetime.data ? runLabel(agentCalls) : '—',
      delta: '직접 호출 포함',
    },
    {
      label: '총 매출',
      value: lifetime.data ? priceLabel(lifetime.data.totalAmount) : '—',
      delta: '누적 매출액',
    },
    {
      label: '정산금',
      value: balance.data ? priceLabel(balance.data.payoutAmount) : '—',
      delta: '즉시 출금 가능',
      href: paths.my.credit,
    },
    {
      label: '월간 매출',
      value: thisMonth.data ? priceLabel(thisMonth.data.totalAmount) : '—',
      delta: lastMonth.data ? growthLabel(growth) : '',
    },
  ]

  const series = stats ? dailySeries(stats.periods, range) : []
  const line = trendPath(
    series.map((p) => p.amount),
    TREND_WIDTH,
    TREND_HEIGHT,
  )
  const ticks = trendTicks(series)

  // 판매는 에이전트로만 일어난다. 에이전트 없는 묶음은 합계에만 남기고 순위에서는 뺀다.
  const soldAgents = (stats?.agents ?? []).filter((a) => a.agentId !== null)
  const topAgent = soldAgents[0]
  const shares = shareSlots(soldAgents, total, agentName)

  const byAgentTab = tab === 'agent'
  const columns = byAgentTab ? AGENT_COLUMNS : ENTRY_COLUMNS
  // 판매는 에이전트로만 일어난다. 에이전트 없는 노드 줄은 목록에서 뺀다(합계에는 남는다).
  const data = byAgentTab
    ? soldAgents.map((a) => [
        agentName(a.agentId),
        runLabel(a.executionCount),
        priceLabel(a.amount),
        `${shareOf(a.amount, total)}%`,
      ])
    : (entries.data?.pages.flatMap((page) => page.content) ?? [])
        .filter((e) => e.agentId !== null)
        .map((e) => [
          timeLabel(e.settledAt),
          // 이름 목록은 수익 상위 30개뿐이다. 못 찾으면 실행 때 남은 노드 이름을 쓴다.
          e.agentId && !agentNames.data?.has(e.agentId)
            ? (e.nodeName ?? '에이전트')
            : agentName(e.agentId),
          priceLabel(e.amount),
          e.selfUse ? '직접 사용' : '판매',
        ])
  const rows = tableRows(data, columns, byAgentTab)
  const current = byAgentTab ? statistics : entries

  return {
    metrics,
    line,
    ticks,
    /** 기간 안에 받은 정산금이 하나도 없다 */
    noSales: Boolean(stats) && total === 0,
    statisticsError: statistics.isError,
    topAmount: topAgent ? priceLabel(topAgent.amount) : '—',
    topName: topAgent ? agentName(topAgent.agentId) : '',
    shares,
    columns,
    rows,
    isPending: current.isPending,
    isError: current.isError,
    isEmpty: !current.isPending && !current.isError && rows.length === 0,
    hasMore: !byAgentTab && entries.hasNextPage,
    isFetchingMore: entries.isFetchingNextPage,
    fetchMore: () => void entries.fetchNextPage(),
  }
}
