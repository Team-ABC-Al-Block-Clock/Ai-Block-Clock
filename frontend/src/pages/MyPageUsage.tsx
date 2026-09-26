import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { Fragment, useEffect, useState } from 'react'
import Sidebar from '../components/Sidebar'
import MyPageFrame from '@/components/MyPageFrame'
import type { CreditTransactionType } from '@/api/credits'
import { getCreditBalance, getCreditHistory, getCreditTransactions } from '@/api/credits'
import StatusDot from '@/components/StatusDot'
import { amountLabel, priceLabel, shortAddress, timeLabel } from '@/lib/format'
import { PERIODS, rangeOf } from '@/lib/period'
import { queryKeys } from '@/lib/queryKeys'
import { historyStatus, type HistoryTone } from '@/lib/withdrawalMessage'

/** 마켓(9)·마이페이지 목록(10)과 다르다. */
const PAGE_SIZE = 20

const TYPE_LABELS: Record<CreditTransactionType, string> = {
  CHARGE: '충전',
  RESERVE: '사용',
  REFUND: '환불',
  PAYOUT_CONVERSION: '정산금 옮김',
}

/** 들어오는 것은 초록, 나가는 것은 분홍. */
const WALLET_POLL_MS = 15_000
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

export default function MyPageUsage() {
  const [periodAt, setPeriodAt] = useState(1)
  const [periodOpen, setPeriodOpen] = useState(false)

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
        에이전트 사용량, 토큰 소비량과 블록체인 결제 내역을 확인합니다.
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
          <div className="text-[13.5px]" style={{ color: 'rgba(255,240,247,.84)' }}>
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
                className="flex cursor-pointer items-center text-[13.5px] hover:bg-[rgba(255,255,255,.12)]"
              >
                {p.label}
              </button>
            ))}
          </div>
        )}
      </div>

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
        {/* 서버에 통계 API 가 없어 아래 꺾은선과 요약 문구는 임의 값이다. */}
        <div
          className="text-[13px] tracking-[-.01em]"
          style={{ marginTop: '5px', width: '534px', color: 'rgba(255,236,246,.6)' }}
        >
          총 호출 수 1,248회 (+18.3%) · 가장 많이 사용 GPT-4o · 피크 14:00 ~ 16:00
        </div>
        <div className="flex" style={{ marginTop: '10px', gap: '18px' }}>
          <div className="flex items-center" style={{ gap: '7px' }}>
            <StatusDot color="#e9aec4" />
            <div
              className="text-[12.5px] whitespace-nowrap"
              style={{ color: 'var(--color-muted)' }}
            >
              사용 코인
            </div>
          </div>
          <div className="flex items-center" style={{ gap: '7px' }}>
            <StatusDot color="#dba18b" />
            <div
              className="text-[12.5px] whitespace-nowrap"
              style={{ color: 'var(--color-muted)' }}
            >
              호출 수
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
            <path
              d="M0 118 L44 96 L89 108 L133 74 L178 92 L222 62 L267 78 L311 50 L356 66 L400 34 L445 52 L489 26 L534 40 L534 146 L0 146 Z"
              fill="url(#spendFill)"
            />
            <path
              d="M0 118 L44 96 L89 108 L133 74 L178 92 L222 62 L267 78 L311 50 L356 66 L400 34 L445 52 L489 26 L534 40"
              fill="none"
              stroke="url(#spendLine)"
              strokeWidth="2.4"
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeDasharray="1400"
              style={{ animation: 'drawUp 1.5s .35s cubic-bezier(.3,.7,.2,1) both' }}
            />
            <path
              d="M0 132 L44 124 L89 128 L133 110 L178 118 L222 100 L267 112 L311 92 L356 102 L400 80 L445 92 L489 70 L534 82"
              fill="none"
              stroke="#dba18b"
              strokeOpacity=".62"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeDasharray="1400"
              style={{ animation: 'drawUp 1.5s .5s cubic-bezier(.3,.7,.2,1) both' }}
            />
          </svg>
          <div
            className="absolute left-0 flex justify-between text-[12px]"
            style={{ top: '142px', width: '534px', color: 'rgba(255,236,246,.5)' }}
          >
            <span>08/01</span>
            <span>08/07</span>
            <span>08/13</span>
            <span>08/19</span>
            <span>08/25</span>
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
        <div className="text-[17px] font-semibold tracking-[-.01em] text-white">코인 잔액</div>
        <div
          className="flex flex-col items-center justify-center text-center"
          style={{ height: '200px' }}
        >
          <div className="text-[14px]" style={{ color: 'rgba(255,236,246,.62)' }}>
            지금 쓸 수 있는 ABC
          </div>
          <div
            className="text-[40px] font-semibold tracking-[-.02em] text-white"
            style={{ marginTop: '8px', lineHeight: '1.1' }}
          >
            {available ? amountLabel(available) : '—'}
          </div>
          <div className="text-[15px] font-semibold" style={{ marginTop: '4px', color: '#c9a9dd' }}>
            ABC
          </div>
        </div>
        {/* PAY-004 — 남에게 ABC 가 가는 길은 정산금 적립 하나뿐이다. */}
        <div className="text-center text-[13px]" style={{ color: 'rgba(255,236,246,.55)' }}>
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
