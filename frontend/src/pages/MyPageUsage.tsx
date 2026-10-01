import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { Fragment, useState } from 'react'
import Sidebar from '../components/mypage/Sidebar'
import PeriodMenu from '@/components/mypage/PeriodMenu'
import MyPageFrame from '@/components/mypage/MyPageFrame'
import type { CreditTransaction, CreditTransactionType } from '@/api/credits'
import { getCreditBalance, getCreditHistory, getCreditTransactions } from '@/api/credits'
import StatusDot from '@/components/StatusDot'
import { amountLabel, priceLabel, runLabel, shortAddress, timeLabel } from '@/lib/format'
import { PERIODS, rangeOf } from '@/lib/stats/period'
import { queryKeys } from '@/lib/queryKeys'
import { dailySeries, trendPath, trendTicks } from '@/lib/stats/salesTrend'
import { usagePeriods, usageTotals } from '@/lib/stats/usageTrend'
import { historyStatus, type HistoryTone } from '@/lib/wallet/withdrawalMessage'

/** 마켓(9)·마이페이지 목록(10)과 다르다. */
const PAGE_SIZE = 20

const TYPE_LABELS: Record<CreditTransactionType, string> = {
  CHARGE: '충전',
  RESERVE: '사용',
  REFUND: '환불',
  PAYOUT_CONVERSION: '정산금 전환',
}

/** 들어오는 것은 초록, 나가는 것은 분홍. */
const WALLET_POLL_MS = 15_000
const CHART_W = 534
const CHART_H = 146
/** 기간 전체를 50건씩 읽는다. 너무 길면 여기서 끊어 요청이 끝없이 늘지 않게 한다. */
const TREND_PAGE_LIMIT = 20
const TONE_COLORS: Record<HistoryTone, string> = {
  done: '#9ad0b4',
  progress: '#c9a9dd',
  closed: '#ff8a8a',
}

const TYPE_COLORS: Record<CreditTransactionType, string> = {
  CHARGE: '#9ad0b4',
  RESERVE: '#ffb0cd',
  REFUND: '#9ad0b4',
  PAYOUT_CONVERSION: '#9ad0b4',
}

/** 한 유형을 50건씩 끝까지 읽는다. TREND_PAGE_LIMIT 장에서 끊는다. */
async function readAll(
  transactionType: CreditTransactionType,
  range: { from?: string; to?: string },
) {
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

export default function MyPageUsage() {
  const [periodAt, setPeriodAt] = useState(1)

  const period = PERIODS[periodAt]
  const range = rangeOf(period.days)
  const listParams = { ...range, size: PAGE_SIZE }

  const balance = useQuery({
    queryKey: queryKeys.credits.balance(),
    queryFn: getCreditBalance,
  })

  const transactions = useInfiniteQuery({
    queryKey: queryKeys.credits.transactions.list(listParams),
    queryFn: ({ pageParam }) => getCreditTransactions({ ...listParams, cursor: pageParam }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  })

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
    CHART_W,
    CHART_H,
  )
  const runLine = trendPath(
    series.map((p) => p.executionCount),
    CHART_W,
    CHART_H,
    40,
  )
  const ticks = trendTicks(series)

  const [listTab, setListTab] = useState<'usage' | 'wallet'>('usage')
  // 충전 · 출금은 기간 조건이 없다. 처리 중인 줄이 있으면 다시 불러 상태를 따라간다.
  const wallet = useInfiniteQuery({
    queryKey: queryKeys.credits.history.list({ size: PAGE_SIZE }),
    queryFn: ({ pageParam }) => getCreditHistory({ size: PAGE_SIZE, cursor: pageParam }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    enabled: listTab === 'wallet',
    refetchInterval: (query) =>
      query.state.data?.pages[0]?.content.some(
        (h) => historyStatus(h.type, h.status).tone === 'progress',
      )
        ? WALLET_POLL_MS
        : false,
  })
  const walletRows = wallet.data?.pages.flatMap((page) => page.content) ?? []
  const walletTab = listTab === 'wallet'
  const list = walletTab ? wallet : transactions

  const history = transactions.data?.pages.flatMap((page) => page.content) ?? []
  // 불러오는 중을 빈 목록으로 보면 요청마다 '없음' 이 깜빡인다.
  const isEmpty =
    !list.isPending && !list.isError && (walletTab ? walletRows : history).length === 0

  const available = balance.data?.availableAmount
  const reserved = balance.data?.reservedAmount
  // 실행에 묶인 것까지가 전체다. 잔액이 0 이면 나눗셈이 NaN 이라 빈 고리로 둔다.
  const total = Number(available ?? 0) + Number(reserved ?? 0)
  const gaugeArc = total > 0 ? (452.39 * Number(available ?? 0)) / total : 0

  return (
    <MyPageFrame>
      <Sidebar active="usage" />

      <div
        className="absolute text-[30px] font-semibold tracking-[-.02em] text-white"
        style={{
          left: '442px',
          top: '158px',
          textShadow: '0 2px 20px rgba(35,8,45,.6)',
          animation: 'rise .7s .06s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        사용 내역
      </div>
      <div
        className="absolute text-[13px] tracking-[-.01em]"
        style={{
          left: '442px',
          top: '206px',
          width: '620px',
          color: 'rgba(255,236,246,.7)',
          animation: 'rise .7s .1s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        ABC 사용량과 잔액, 충전·출금 내역을 확인합니다.
      </div>

      <PeriodMenu at={periodAt} onPick={setPeriodAt} />

      <div
        className="bg-surface-card absolute box-border"
        style={{
          left: '442px',
          top: '250px',
          width: '574px',
          height: '296px',
          padding: '20px',
          borderRadius: '16px',
          animation: 'rise .7s .22s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div className="text-[17px] font-semibold tracking-[-.01em] text-white">사용량 추이</div>
        <div
          className="text-[13px] tracking-[-.01em]"
          style={{ marginTop: '5px', width: '534px', color: 'rgba(255,236,246,.6)' }}
        >
          {trend.data
            ? `${period.label} 총 사용 ${priceLabel(totals.spent)} · 실행 ${runLabel(totals.executionCount)}${trend.data.cut ? ' · 최근 내역 일부 기준' : ''}`
            : trend.isError
              ? '사용량을 불러오지 못했습니다.'
              : '사용량을 불러오는 중입니다.'}
        </div>
        <div className="flex" style={{ marginTop: '10px', gap: '18px' }}>
          <div className="flex items-center" style={{ gap: '7px' }}>
            <StatusDot color="#e9aec4" />
            <div
              className="text-[12.5px] whitespace-nowrap"
              style={{ color: 'var(--color-muted)' }}
            >
              사용 금액
            </div>
          </div>
          <div className="flex items-center" style={{ gap: '7px' }}>
            <StatusDot color="#dba18b" />
            <div
              className="text-[12.5px] whitespace-nowrap"
              style={{ color: 'var(--color-muted)' }}
            >
              실행 수
            </div>
          </div>
        </div>

        <div className="relative" style={{ marginTop: '16px', width: '534px', height: '158px' }}>
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
              <linearGradient id="spendLine" x1="0" y1="1" x2="1" y2="0">
                <stop offset="0%" stopColor="#dba18b" />
                <stop offset="100%" stopColor="#e3b7c8" />
              </linearGradient>
              <linearGradient id="spendFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#ffb0cd" stopOpacity=".34" />
                <stop offset="100%" stopColor="#ffb0cd" stopOpacity="0" />
              </linearGradient>
            </defs>
            {spendLine && (
              <path
                d={`${spendLine} L${CHART_W} ${CHART_H} L0 ${CHART_H} Z`}
                fill="url(#spendFill)"
              />
            )}
            {spendLine && (
              <path
                d={spendLine}
                fill="none"
                stroke="url(#spendLine)"
                strokeWidth="2.4"
                strokeLinecap="round"
                strokeLinejoin="round"
                pathLength={1400}
                strokeDasharray="1400"
                style={{ animation: 'drawUp 1.5s .35s cubic-bezier(.3,.7,.2,1) both' }}
              />
            )}
            {runLine && (
              <path
                d={runLine}
                fill="none"
                stroke="#dba18b"
                strokeOpacity=".62"
                strokeWidth="1.8"
                strokeLinecap="round"
                strokeLinejoin="round"
                pathLength={1400}
                strokeDasharray="1400"
                style={{ animation: 'drawUp 1.5s .5s cubic-bezier(.3,.7,.2,1) both' }}
              />
            )}
          </svg>
          <div
            className="absolute left-0 flex justify-between text-[12px]"
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
          top: '250px',
          width: '306px',
          height: '296px',
          padding: '20px',
          borderRadius: '16px',
          animation: 'rise .7s .26s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div className="text-[17px] font-semibold tracking-[-.01em] text-white">ABC 잔액</div>
        <div className="relative" style={{ marginTop: '8px', width: '266px', height: '160px' }}>
          <svg
            className="absolute top-0 left-0"
            width="266"
            height="160"
            viewBox="0 0 266 160"
            fill="none"
          >
            <g transform="translate(133,80) rotate(-90) scale(0.98)">
              <circle r="72" fill="none" stroke="rgba(255,255,255,.12)" strokeWidth="11" />
              <circle
                r="72"
                fill="none"
                stroke="#c9a9dd"
                strokeWidth="11"
                strokeLinecap="round"
                strokeDasharray={`${gaugeArc} 452.39`}
                style={{ animation: 'gaugeIn 1.1s .5s cubic-bezier(.3,.7,.2,1) both' }}
              />
            </g>
          </svg>
          <div className="absolute left-0 text-center" style={{ top: '52px', width: '266px' }}>
            <div
              className="text-[30px] font-semibold tracking-[-.02em] text-white"
              style={{ lineHeight: '1.1' }}
            >
              {available ? amountLabel(available) : '—'}
            </div>
            <div
              className="text-[13px]"
              style={{ marginTop: '3px', color: 'rgba(255,236,246,.62)' }}
            >
              ABC 사용 가능
            </div>
          </div>
        </div>
        <div
          className="flex justify-center text-[13px]"
          style={{ marginTop: '10px', gap: '14px', color: 'rgba(255,236,246,.72)' }}
        >
          <div className="flex items-center" style={{ gap: '6px' }}>
            <StatusDot color="#c9a9dd" />
            사용 가능 {available ? amountLabel(available) : '—'}
          </div>
          <div className="flex items-center" style={{ gap: '6px' }}>
            <StatusDot color="rgba(255,255,255,.3)" />
            실행 중 {reserved ? amountLabel(reserved) : '—'}
          </div>
        </div>
        {/* PAY-004 — 남에게 ABC 가 가는 길은 정산금 적립 하나뿐이다. */}
        <div
          className="text-center text-[13px]"
          style={{ marginTop: '8px', color: 'rgba(255,236,246,.55)' }}
        >
          ABC 는 양도할 수 없어요
        </div>
      </div>

      <div
        className="bg-surface-card absolute box-border overflow-hidden"
        style={{
          left: '442px',
          top: '570px',
          width: '900px',
          height: '425px',
          padding: '16px 20px',
          borderRadius: '16px',
          animation: 'rise .7s .34s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div className="flex" style={{ gap: '20px', height: '30px' }}>
          <div
            className="relative flex cursor-pointer items-start text-[16px] font-semibold tracking-[-.01em]"
            onClick={() => setListTab('usage')}
            style={{
              height: '30px',
              color: listTab === 'usage' ? '#fff' : 'rgba(255,240,247,.6)',
              transition: 'color .16s',
            }}
          >
            사용 내역
            <div
              className="absolute left-0"
              style={{
                bottom: '0',
                width: '100%',
                height: '2px',
                borderRadius: '1px',
                background: listTab === 'usage' ? '#c9a9dd' : 'transparent',
              }}
            ></div>
          </div>
          <div
            className="relative flex cursor-pointer items-start text-[16px] font-semibold tracking-[-.01em]"
            onClick={() => setListTab('wallet')}
            style={{
              height: '30px',
              color: listTab === 'wallet' ? '#fff' : 'rgba(255,240,247,.6)',
              transition: 'color .16s',
            }}
          >
            충전 · 출금
            <div
              className="absolute left-0"
              style={{
                bottom: '0',
                width: '100%',
                height: '2px',
                borderRadius: '1px',
                background: listTab === 'wallet' ? '#c9a9dd' : 'transparent',
              }}
            ></div>
          </div>
        </div>
        <div
          className="flex text-[13.5px] font-semibold"
          style={{ marginTop: '14px', color: 'rgba(255,236,246,.72)' }}
        >
          <div style={{ width: '170px' }}>날짜/시간</div>
          <div style={{ width: '120px' }}>{walletTab ? '구분' : '유형'}</div>
          <div style={{ width: '190px' }}>금액</div>
          <div style={{ width: '220px' }}>{walletTab ? '상태' : '실행'}</div>
          <div style={{ width: '160px' }}>트랜잭션</div>
        </div>
        <div
          className="flex flex-col overflow-y-auto"
          style={{ marginTop: '8px', height: '292px' }}
        >
          {walletTab &&
            walletRows.map((h) => {
              const status = historyStatus(h.type, h.status)
              return (
                <div
                  key={h.type + h.id}
                  style={{
                    height: '44px',
                    color: 'var(--color-body)',
                    fontVariantNumeric: 'tabular-nums',
                    borderRadius: '6px',
                    transition: 'background .14s',
                  }}
                  className="hover:bg-surface-inset flex flex-none items-center text-[15.5px]"
                >
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
                </div>
              )
            })}
          {!walletTab &&
            history.map((h) => (
              <Fragment key={h.transactionId}>
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
                  <div style={{ width: '220px', color: 'rgba(255,255,255,.7)' }}>
                    {h.executionId ?? '—'}
                  </div>
                  <div style={{ width: '160px', color: 'rgba(255,255,255,.7)' }}>
                    {h.transactionHash ? shortAddress(h.transactionHash) : '—'}
                  </div>
                </div>
              </Fragment>
            ))}

          {list.isPending && (
            <div className="text-[13px]" style={{ color: 'rgba(255,236,246,.55)' }}>
              내역을 불러오는 중입니다.
            </div>
          )}
          {list.isError && (
            <div className="text-[13px]" style={{ color: '#ffb0cd' }}>
              내역을 불러오지 못했습니다.
            </div>
          )}
          {isEmpty && (
            <div className="text-[13px]" style={{ color: 'rgba(255,236,246,.55)' }}>
              {walletTab
                ? '아직 충전 · 출금 내역이 없습니다.'
                : `${period.label}에 사용 내역이 없습니다.`}
            </div>
          )}
        </div>

        <div className="flex" style={{ marginTop: '12px', justifyContent: 'flex-end' }}>
          {list.hasNextPage && (
            <button
              type="button"
              onClick={() => void list.fetchNextPage()}
              disabled={list.isFetchingNextPage}
              style={{ gap: '6px', color: 'rgba(255,240,247,.78)', transition: 'color .16s' }}
              className="flex cursor-pointer items-center text-[13px] font-semibold hover:text-[#fff]"
            >
              {list.isFetchingNextPage ? '불러오는 중' : '더 보기'}
              <span className="text-[13.5px]">→</span>
            </button>
          )}
        </div>
      </div>
    </MyPageFrame>
  )
}
