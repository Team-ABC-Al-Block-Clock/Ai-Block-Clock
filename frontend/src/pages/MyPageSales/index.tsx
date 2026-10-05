import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { Fragment, useState } from 'react'
import Sidebar from '@/components/mypage/Sidebar'
import PeriodMenu from '@/components/mypage/PeriodMenu'
import MyPageFrame from '@/components/mypage/MyPageFrame'
import MyPageHeader from '@/components/mypage/MyPageHeader'
import TrendChart from '@/components/mypage/TrendChart'
import { getMyAgents } from '@/api/agents'
import { getCreditBalance } from '@/api/credits'
import { getSettlementStatistics, getSettlements } from '@/api/settlements'
import { priceLabel, runLabel, timeLabel } from '@/lib/format'
import { growthPercent, monthRanges, PERIODS, rangeOf } from '@/lib/stats/period'
import { queryKeys } from '@/lib/queryKeys'
import {
  dailySeries,
  TREND_HEIGHT,
  TREND_WIDTH,
  trendPath,
  trendTicks,
} from '@/lib/stats/salesTrend'
import { paths } from '@/routes/paths'

const PAGE_SIZE = 20
const SHARE_COLORS = ['#e9aec4', '#dba18b', '#a094c2']

const shareOf = (amount: string, total: number) =>
  total > 0 ? Math.round((Number(amount) / total) * 1000) / 10 : 0

export default function MyPageSales() {
  const [tab, setTab] = useState('entry')
  const [periodAt, setPeriodAt] = useState(1)

  const period = PERIODS[periodAt]
  const range = rangeOf(period.days)
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
  const growthLabel =
    growth === null ? '지난달 매출 없음' : `${growth >= 0 ? '+' : ''}${growth.toLocaleString()}%`
  const metrics = [
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
      delta: lastMonth.data ? growthLabel : '',
    },
  ].map((m, i) => ({
    ...m,
    anim: 'rise .7s ' + (0.14 + i * 0.04).toFixed(2) + 's cubic-bezier(.2,.8,.25,1) both',
  }))

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
  // 2 ~ 4위 칸은 늘 셋이다. 순위가 모자라면 빈 칸을 0 으로 채운다.
  const shares = [1, 2, 3].map((rank, i) => {
    const a = soldAgents[rank]
    return {
      label: a ? agentName(a.agentId) : '—',
      value: `${priceLabel(a?.amount ?? 0)} (${a ? shareOf(a.amount, total) : 0}%)`,
      width: `${a ? shareOf(a.amount, total) : 0}%`,
      color: SHARE_COLORS[i],
      anim: 'growBar .8s ' + (0.4 + i * 0.12).toFixed(2) + 's cubic-bezier(.2,.8,.25,1) both',
    }
  })

  const byAgent = {
    columns: [
      { label: '에이전트', w: '380px' },
      { label: '호출 수', w: '160px' },
      { label: '받은 정산금', w: '200px' },
      { label: '비중', w: '120px' },
    ],
    data: soldAgents.map((a) => [
      agentName(a.agentId),
      runLabel(a.executionCount),
      priceLabel(a.amount),
      `${shareOf(a.amount, total)}%`,
    ]),
  }
  // 판매는 에이전트로만 일어난다. 에이전트 없는 노드 줄은 목록에서 뺀다(합계에는 남는다).
  const entryRows = (entries.data?.pages.flatMap((page) => page.content) ?? []).filter(
    (e) => e.agentId !== null,
  )
  const byEntry = {
    columns: [
      { label: '일시', w: '190px' },
      { label: '에이전트', w: '370px' },
      { label: '정산금', w: '190px' },
      { label: '구분', w: '110px' },
    ],
    data: entryRows.map((e) => [
      timeLabel(e.settledAt),
      // 이름 목록은 수익 상위 30개뿐이다. 못 찾으면 실행 때 남은 노드 이름을 쓴다.
      e.agentId && !agentNames.data?.has(e.agentId)
        ? (e.nodeName ?? '에이전트')
        : agentName(e.agentId),
      priceLabel(e.amount),
      e.selfUse ? '직접 사용' : '판매',
    ]),
  }
  const byAgentTab = tab === 'agent'
  const view = byAgentTab ? byAgent : byEntry
  const amountAt = 2
  const rows = view.data.map((cells) => ({
    cells: cells.map((text, i) => ({
      text,
      w: view.columns[i].w,
      color:
        !byAgentTab && i === cells.length - 1
          ? text === '판매'
            ? '#9ad0b4'
            : '#ff8a8a'
          : i === 0 && byAgentTab
            ? 'var(--color-body)'
            : i === amountAt
              ? '#fff'
              : 'rgba(255,255,255,.84)',
      weight: i === amountAt || (!byAgentTab && i === cells.length - 1) ? '600' : '400',
    })),
  }))
  const current = byAgentTab ? statistics : entries
  const isEmpty = !current.isPending && !current.isError && rows.length === 0

  const LILAC = '#c9a9dd'
  const columns = view.columns
  const fgAgent = byAgentTab ? '#fff' : 'rgba(255,240,247,.6)'
  const fgEntry = byAgentTab ? 'rgba(255,240,247,.6)' : '#fff'
  const barAgent = byAgentTab ? LILAC : 'transparent'
  const barEntry = byAgentTab ? 'transparent' : LILAC
  const handlePickByAgent = () => setTab('agent')
  const handlePickByEntry = () => setTab('entry')

  return (
    <MyPageFrame>
      <Sidebar active="sales" />

      <MyPageHeader
        title="판매 내역"
        description="내 에이전트를 판매한 정산금과 출금할 수 있는 금액을 확인합니다."
        descriptionWidth={700}
      />

      <PeriodMenu at={periodAt} onPick={setPeriodAt} />

      <div
        className="absolute flex"
        style={{ left: '442px', top: '246px', width: '900px', gap: '16px' }}
      >
        {metrics.map((m, i) => (
          <Fragment key={i}>
            <div
              className="bg-surface-card box-border flex flex-none flex-col justify-center"
              style={{
                width: '212px',
                height: '140px',
                padding: '18px',
                borderRadius: '16px',
                animation: `${m.anim}`,
              }}
            >
              <div>
                <div
                  className="text-[14px] tracking-[-.01em]"
                  style={{ color: 'rgba(255,236,246,.62)', lineHeight: '1.3' }}
                >
                  {m.label}
                </div>
                <div
                  className="text-[26px] font-semibold tracking-[-.02em] text-white"
                  style={{ marginTop: '10px', lineHeight: '1.1' }}
                >
                  {m.value}
                </div>
                {m.href ? (
                  <a
                    href={m.href}
                    className="block text-[13px] hover:text-white"
                    style={{ marginTop: '8px', color: '#dcaebf', lineHeight: '1.2' }}
                  >
                    {m.delta}
                  </a>
                ) : (
                  <div
                    className="text-[13px]"
                    style={{ marginTop: '8px', color: '#dcaebf', lineHeight: '1.2' }}
                  >
                    {m.delta}
                  </div>
                )}
              </div>
            </div>
          </Fragment>
        ))}
      </div>

      <div
        className="bg-surface-card absolute box-border"
        style={{
          left: '442px',
          top: '400px',
          width: '574px',
          height: '244px',
          padding: '20px',
          borderRadius: '16px',
          animation: 'rise .7s .22s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div className="text-[17px] font-semibold tracking-[-.01em] text-white">정산금 추이</div>
        <TrendChart id="sales" line={line} ticks={ticks} marginTop={18}>
          {stats && total === 0 && (
            <div
              className="absolute left-0 flex items-center justify-center text-[13.5px]"
              style={{ top: '0', width: '534px', height: '134px', color: 'rgba(255,236,246,.55)' }}
            >
              {period.label}에 받은 정산금이 없습니다.
            </div>
          )}
          {statistics.isError && (
            <div
              className="absolute left-0 flex items-center justify-center text-[13.5px]"
              style={{ top: '0', width: '534px', height: '134px', color: '#ffb0cd' }}
            >
              정산 통계를 불러오지 못했습니다.
            </div>
          )}
        </TrendChart>
      </div>

      <div
        className="bg-surface-card absolute box-border"
        style={{
          left: '1036px',
          top: '400px',
          width: '306px',
          height: '244px',
          padding: '20px',
          borderRadius: '16px',
          animation: 'rise .7s .26s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div className="text-[17px] font-semibold tracking-[-.01em] text-white">판매 금액</div>
        <div
          className="truncate text-[26px] font-semibold tracking-[-.02em] text-white"
          style={{ marginTop: '10px', lineHeight: '1.2' }}
        >
          {topAgent ? priceLabel(topAgent.amount) : '—'}
        </div>
        <div className="text-[12.5px]" style={{ marginTop: '4px', color: 'rgba(255,236,246,.6)' }}>
          {topAgent ? agentName(topAgent.agentId) : ''}
        </div>
        <div className="flex flex-col" style={{ marginTop: '14px', gap: '10px' }}>
          {shares.map((t, i) => (
            <Fragment key={i}>
              <div>
                <div className="flex justify-between" style={{ alignItems: 'baseline' }}>
                  <div className="text-[12.5px]" style={{ color: 'rgba(255,236,246,.62)' }}>
                    {t.label}
                  </div>
                  <div className="text-[12.5px] font-semibold text-white">{t.value}</div>
                </div>
                <div
                  className="overflow-hidden bg-[rgba(255,255,255,.09)]"
                  style={{ marginTop: '5px', width: '266px', height: '7px', borderRadius: '4px' }}
                >
                  <div
                    style={{
                      width: `${t.width}`,
                      height: '7px',
                      borderRadius: '4px',
                      background: `${t.color}`,
                      transformOrigin: 'left center',
                      animation: `${t.anim}`,
                    }}
                  ></div>
                </div>
              </div>
            </Fragment>
          ))}
        </div>
      </div>

      <div
        className="bg-surface-card absolute box-border overflow-hidden"
        style={{
          left: '442px',
          top: '665px',
          width: '900px',
          height: '330px',
          padding: '16px 20px',
          borderRadius: '16px',
          animation: 'rise .7s .3s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div className="flex items-center justify-between" style={{ height: '30px' }}>
          <div className="flex" style={{ gap: '20px', height: '30px' }}>
            <div
              className="relative flex cursor-pointer items-start text-[16px] font-semibold tracking-[-.01em]"
              onClick={handlePickByEntry}
              style={{ height: '30px', color: `${fgEntry}`, transition: 'color .16s' }}
            >
              건별
              <div
                className="absolute left-0"
                style={{
                  bottom: '0',
                  width: '100%',
                  height: '2px',
                  borderRadius: '1px',
                  background: `${barEntry}`,
                }}
              ></div>
            </div>
            <div
              className="relative flex cursor-pointer items-start text-[16px] font-semibold tracking-[-.01em]"
              onClick={handlePickByAgent}
              style={{ height: '30px', color: `${fgAgent}`, transition: 'color .16s' }}
            >
              에이전트별
              <div
                className="absolute left-0"
                style={{
                  bottom: '0',
                  width: '100%',
                  height: '2px',
                  borderRadius: '1px',
                  background: `${barAgent}`,
                }}
              ></div>
            </div>
          </div>
          <div className="text-[13px]" style={{ color: 'rgba(255,236,246,.55)' }}>
            {period.label}
          </div>
        </div>

        <div
          className="flex text-[13.5px] font-semibold"
          style={{ marginTop: '14px', color: 'rgba(255,236,246,.72)' }}
        >
          {columns.map((c, i) => (
            <Fragment key={i}>
              <div style={{ width: `${c.w}` }}>{c.label}</div>
            </Fragment>
          ))}
        </div>

        <div
          className="flex flex-col overflow-y-auto"
          style={{ marginTop: '8px', height: '196px' }}
        >
          {rows.map((r, i) => (
            <Fragment key={i}>
              <div
                style={{
                  height: '44px',
                  color: 'var(--color-body)',
                  fontVariantNumeric: 'tabular-nums',
                  borderRadius: '6px',
                  transition: 'background .14s',
                }}
                className="hover:bg-surface-inset flex flex-none items-center text-[15.5px]"
              >
                {r.cells.map((c, i) => (
                  <Fragment key={i}>
                    <div
                      className="overflow-hidden whitespace-nowrap"
                      style={{
                        width: `${c.w}`,
                        color: `${c.color}`,
                        fontWeight: `${c.weight}`,
                        textOverflow: 'ellipsis',
                      }}
                    >
                      {c.text}
                    </div>
                  </Fragment>
                ))}
              </div>
            </Fragment>
          ))}
          {current.isPending && (
            <div className="text-[13px]" style={{ color: 'rgba(255,236,246,.55)' }}>
              정산 내역을 불러오는 중입니다.
            </div>
          )}
          {current.isError && (
            <div className="text-[13px]" style={{ color: '#ffb0cd' }}>
              정산 내역을 불러오지 못했습니다.
            </div>
          )}
          {isEmpty && (
            <div className="text-[13px]" style={{ color: 'rgba(255,236,246,.55)' }}>
              {period.label}에 받은 정산금이 없습니다.
            </div>
          )}
        </div>

        <div className="flex" style={{ marginTop: '12px', justifyContent: 'flex-end' }}>
          {!byAgentTab && entries.hasNextPage && (
            <button
              type="button"
              onClick={() => void entries.fetchNextPage()}
              disabled={entries.isFetchingNextPage}
              style={{ gap: '6px', color: 'rgba(255,240,247,.78)', transition: 'color .16s' }}
              className="flex cursor-pointer items-center text-[13px] font-semibold hover:text-[#fff]"
            >
              {entries.isFetchingNextPage ? '불러오는 중' : '더 보기'}
              <span className="text-[13.5px]">→</span>
            </button>
          )}
        </div>
      </div>
    </MyPageFrame>
  )
}
