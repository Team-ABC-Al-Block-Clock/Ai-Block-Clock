import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { Fragment, useEffect, useState } from 'react'
import Sidebar from '../components/Sidebar'
import MyPageFrame from '@/components/MyPageFrame'
import { getMyAgents } from '@/api/agents'
import { getCreditBalance } from '@/api/credits'
import { getSettlementStatistics, getSettlements } from '@/api/settlements'
import { priceLabel, runLabel, timeLabel } from '@/lib/format'
import { growthPercent, monthRanges, PERIODS, rangeOf } from '@/lib/period'
import { queryKeys } from '@/lib/queryKeys'
import { dailySeries, trendPath } from '@/lib/salesTrend'
import { paths } from '@/routes/paths'

const PAGE_SIZE = 20
const CHART_W = 534
const CHART_H = 146
const SHARE_COLORS = ['#e9aec4', '#dba18b', '#a094c2']

const shareOf = (amount: string, total: number) =>
  total > 0 ? Math.round((Number(amount) / total) * 1000) / 10 : 0

export default function MyPageSales() {
  const [tab, setTab] = useState('entry')
  const [periodAt, setPeriodAt] = useState(1)
  const [periodOpen, setPeriodOpen] = useState(false)

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
    CHART_W,
    CHART_H,
  )
  const ticks =
    series.length > 1
      ? [0, 1, 2, 3, 4].map((k) => series[Math.round(((series.length - 1) * k) / 4)].day.slice(5))
      : series.map((p) => p.day.slice(5))

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
  const entryRows = entries.data?.pages.flatMap((page) => page.content) ?? []
  const byEntry = {
    columns: [
      { label: '일시', w: '190px' },
      { label: '에이전트', w: '370px' },
      { label: '정산금', w: '190px' },
      { label: '구분', w: '110px' },
    ],
    data: entryRows.map((e) => [
      timeLabel(e.settledAt),
      agentName(e.agentId),
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
  const handlePickPeriod = (at: number) => {
    setPeriodAt(at)
    setPeriodOpen(false)
  }

  useEffect(() => {
    if (!periodOpen) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setPeriodOpen(false)
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [periodOpen])

  return (
    <MyPageFrame>
      <Sidebar active="sales" />

      <div
        className="absolute text-[30px] font-semibold tracking-[-.02em] text-white"
        style={{
          left: '442px',
          top: '158px',
          textShadow: '0 2px 20px rgba(35,8,45,.6)',
          animation: 'rise .7s .06s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        판매 내역
      </div>
      <div
        className="absolute text-[13px] tracking-[-.01em]"
        style={{
          left: '442px',
          top: '206px',
          width: '700px',
          color: 'rgba(255,236,246,.7)',
          animation: 'rise .7s .1s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        내 에이전트를 판매한 정산금과 출금할 수 있는 금액을 확인합니다.
      </div>

      <div className="absolute" style={{ left: '1210px', top: '160px', width: '128px', zIndex: 5 }}>
        {/* 등장 모션은 버튼에만 건다 — transform 이 걸린 조상 안에서는 아래 메뉴의 유리가 죽는다. */}
        <button
          type="button"
          onClick={() => setPeriodOpen(!periodOpen)}
          style={{
            width: '128px',
            height: '38px',
            borderRadius: '19px',
            padding: '0 15px',
            animation: 'rise .7s .12s cubic-bezier(.2,.8,.25,1) both',
          }}
          className="box-border flex cursor-pointer items-center justify-between bg-[rgba(255,255,255,.09)] hover:bg-[rgba(255,255,255,.18)]"
        >
          <div className="text-[12px]" style={{ color: 'rgba(255,240,247,.84)' }}>
            {period.label}
          </div>
          <div
            style={{
              width: '0',
              height: '0',
              borderLeft: '4px solid transparent',
              borderRight: '4px solid transparent',
              borderTop: '5px solid rgba(255,255,255,.6)',
            }}
          ></div>
        </button>

        {/* 바깥을 덮어 어디를 눌러도 닫는다 — `profile-menu` 와 같은 방식이다. */}
        {periodOpen && (
          <div
            className="fixed inset-0"
            style={{ zIndex: 4 }}
            onClick={() => setPeriodOpen(false)}
          ></div>
        )}

        {periodOpen && (
          <div
            className="bg-surface-menu relative box-border flex flex-col overflow-hidden"
            style={{
              marginTop: '6px',
              width: '128px',
              borderRadius: '12px',
              padding: '4px',
              // veil 보다 위에 와야 항목이 눌린다.
              zIndex: 5,
              backdropFilter: 'blur(18px) saturate(115%)',
              WebkitBackdropFilter: 'blur(18px) saturate(115%)',
            }}
          >
            {PERIODS.map((p, i) => (
              <button
                key={p.label}
                type="button"
                onClick={() => handlePickPeriod(i)}
                style={{
                  height: '32px',
                  padding: '0 11px',
                  borderRadius: '8px',
                  color: i === periodAt ? '#f0dcea' : 'rgba(255,240,247,.78)',
                  background: i === periodAt ? 'rgba(185,141,169,.28)' : 'transparent',
                }}
                className="flex cursor-pointer items-center text-[12px] hover:bg-[rgba(255,255,255,.12)]"
              >
                {p.label}
              </button>
            ))}
          </div>
        )}
      </div>

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
                  className="text-[12.5px] tracking-[-.01em]"
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
                    className="block text-[11.5px] hover:text-white"
                    style={{ marginTop: '8px', color: '#dcaebf', lineHeight: '1.2' }}
                  >
                    {m.delta}
                  </a>
                ) : (
                  <div
                    className="text-[11.5px]"
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
        <div className="relative" style={{ marginTop: '18px', width: '534px', height: '158px' }}>
          <div
            className="absolute top-0 left-0 bg-[rgba(255,255,255,.09)]"
            style={{ width: '534px', height: '1px' }}
          ></div>
          <div
            className="absolute left-0 bg-[rgba(255,255,255,.09)]"
            style={{ top: '40px', width: '534px', height: '1px' }}
          ></div>
          <div
            className="absolute left-0 bg-[rgba(255,255,255,.09)]"
            style={{ top: '80px', width: '534px', height: '1px' }}
          ></div>
          <div
            className="absolute left-0 bg-[rgba(255,255,255,.09)]"
            style={{ top: '120px', width: '534px', height: '1px' }}
          ></div>
          <svg
            className="absolute top-0 left-0"
            width="534"
            height="134"
            viewBox="0 0 534 146"
            preserveAspectRatio="none"
            fill="none"
            style={{ overflow: 'visible' }}
          >
            <defs>
              <linearGradient id="salesLine" x1="0" y1="1" x2="1" y2="0">
                <stop offset="0%" stopColor="#dba18b" />
                <stop offset="100%" stopColor="#e3b7c8" />
              </linearGradient>
              <linearGradient id="salesFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#ffb0cd" stopOpacity=".34" />
                <stop offset="100%" stopColor="#ffb0cd" stopOpacity="0" />
              </linearGradient>
            </defs>
            {line && (
              <path d={`${line} L${CHART_W} ${CHART_H} L0 ${CHART_H} Z`} fill="url(#salesFill)" />
            )}
            {line && (
              <path
                d={line}
                fill="none"
                stroke="url(#salesLine)"
                strokeWidth="2.4"
                strokeLinecap="round"
                strokeLinejoin="round"
                pathLength={1400}
                strokeDasharray="1400"
                style={{ animation: 'drawUp 1.5s .35s cubic-bezier(.3,.7,.2,1) both' }}
              />
            )}
          </svg>
          {stats && total === 0 && (
            <div
              className="absolute left-0 flex items-center justify-center text-[12px]"
              style={{ top: '0', width: '534px', height: '134px', color: 'rgba(255,236,246,.55)' }}
            >
              {period.label}에 받은 정산금이 없습니다.
            </div>
          )}
          {statistics.isError && (
            <div
              className="absolute left-0 flex items-center justify-center text-[12px]"
              style={{ top: '0', width: '534px', height: '134px', color: '#ffb0cd' }}
            >
              정산 통계를 불러오지 못했습니다.
            </div>
          )}
          <div
            className="absolute left-0 flex justify-between text-[10px]"
            style={{ top: '142px', width: '534px', color: 'rgba(255,236,246,.5)' }}
          >
            {ticks.map((t, i) => (
              <span key={i}>{t.replace('-', '/')}</span>
            ))}
          </div>
        </div>
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
        <div className="text-[11px]" style={{ marginTop: '4px', color: 'rgba(255,236,246,.6)' }}>
          {topAgent ? agentName(topAgent.agentId) : ''}
        </div>
        <div className="flex flex-col" style={{ marginTop: '14px', gap: '10px' }}>
          {shares.map((t, i) => (
            <Fragment key={i}>
              <div>
                <div className="flex justify-between" style={{ alignItems: 'baseline' }}>
                  <div className="text-[11px]" style={{ color: 'rgba(255,236,246,.62)' }}>
                    {t.label}
                  </div>
                  <div className="text-[11px] font-semibold text-white">{t.value}</div>
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
              <span className="text-[12px]">→</span>
            </button>
          )}
        </div>
      </div>
    </MyPageFrame>
  )
}
