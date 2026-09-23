import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { Fragment, useEffect, useState } from 'react'
import Sidebar from '../components/Sidebar'
import type { CreditTransactionType } from '@/api/credits'
import { getCreditBalance, getCreditTransactions } from '@/api/credits'
import { paths } from '@/routes/paths'
import StatusDot from '@/components/StatusDot'
import { amountLabel, priceLabel, shortAddress, timeLabel } from '@/lib/format'
import { queryKeys } from '@/lib/queryKeys'

/** 마켓(9)·마이페이지 목록(10)과 다르다. */
const PAGE_SIZE = 20

/** 기간 필터. `days` 가 0 이면 조건을 안 붙인다 — from·to 는 한쪽만 보낼 수 없다. */
const PERIODS = [
  { label: '최근 7일', days: 7 },
  { label: '최근 30일', days: 30 },
  { label: '최근 90일', days: 90 },
  { label: '전체 기간', days: 0 },
]

/** 서버가 UTC 날짜 경계로 자르므로 UTC 로 만든다. */
const utcDay = (ms: number) => new Date(ms).toISOString().slice(0, 10)

function rangeOf(days: number) {
  if (days === 0) return {}
  const now = Date.now()
  return { from: utcDay(now - (days - 1) * 86_400_000), to: utcDay(now) }
}

const TYPE_LABELS: Record<CreditTransactionType, string> = {
  CHARGE: '충전',
  RESERVE: '사용',
  REFUND: '환불',
}

/** 들어오는 것은 초록, 나가는 것은 분홍. */
const TYPE_COLORS: Record<CreditTransactionType, string> = {
  CHARGE: '#9ad0b4',
  RESERVE: '#ffb0cd',
  REFUND: '#9ad0b4',
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

  const history = transactions.data?.pages.flatMap((page) => page.content) ?? []
  // 불러오는 중을 빈 목록으로 보면 요청마다 '없음' 이 깜빡인다.
  const isEmpty = !transactions.isPending && !transactions.isError && history.length === 0

  const available = balance.data?.availableAmount
  const reserved = balance.data?.reservedAmount
  // 잔액이 0 이면 나눗셈이 NaN 이라 빈 고리로 둔다.
  const total = Number(available ?? 0) + Number(reserved ?? 0)
  const gaugeArc = total > 0 ? (452.39 * Number(available ?? 0)) / total : 0

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
    <>
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
          className="text-[11.5px] tracking-[-.01em]"
          style={{ marginTop: '5px', width: '534px', color: 'rgba(255,236,246,.6)' }}
        >
          총 호출 수 1,248회 (+18.3%) · 가장 많이 사용 GPT-4o · 피크 14:00 ~ 16:00
        </div>
        <div className="flex" style={{ marginTop: '10px', gap: '18px' }}>
          <div className="flex items-center" style={{ gap: '7px' }}>
            <StatusDot color="#e9aec4" />
            <div className="text-[11px] whitespace-nowrap" style={{ color: 'var(--color-muted)' }}>
              사용 코인
            </div>
          </div>
          <div className="flex items-center" style={{ gap: '7px' }}>
            <StatusDot color="#dba18b" />
            <div className="text-[11px] whitespace-nowrap" style={{ color: 'var(--color-muted)' }}>
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
            className="absolute left-0 flex justify-between text-[10px]"
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
        <div className="flex items-center justify-between">
          <div className="text-[17px] font-semibold tracking-[-.01em] text-white">코인 잔액</div>
          <div className="flex" style={{ gap: '8px' }}>
            <a
              href={paths.my.credit}
              style={{
                height: '30px',
                padding: '0 12px',
                borderRadius: '9px',
                background: 'rgba(185,141,169,.28)',
                color: '#f0dcea',
              }}
              className="flex items-center text-[11.5px] font-semibold tracking-[-.01em] transition-colors duration-[160ms] hover:bg-[rgba(185,141,169,.44)] hover:text-[#fff]"
            >
              예치
            </a>
            <a
              href={paths.my.credit}
              style={{
                height: '30px',
                padding: '0 12px',
                borderRadius: '9px',
                color: 'rgba(255,240,247,.86)',
              }}
              className="flex items-center bg-[rgba(255,255,255,.10)] text-[11.5px] font-semibold tracking-[-.01em] transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.2)] hover:text-[#fff]"
            >
              출금
            </a>
          </div>
        </div>
        <div className="relative" style={{ marginTop: '14px', width: '266px', height: '150px' }}>
          <svg
            className="absolute top-0 left-0"
            width="266"
            height="150"
            viewBox="0 0 266 150"
            fill="none"
          >
            <g transform="translate(133,75) rotate(-90) scale(0.76)">
              <circle r="72" fill="none" stroke="rgba(255,255,255,.12)" strokeWidth="12" />
              <circle
                r="72"
                fill="none"
                stroke="#c9a9dd"
                strokeWidth="12"
                strokeLinecap="round"
                strokeDasharray={`${gaugeArc} 452.39`}
                style={{ animation: 'gaugeIn 1.1s .5s cubic-bezier(.3,.7,.2,1) both' }}
              />
            </g>
          </svg>
          <div className="absolute left-0 text-center" style={{ top: '56px', width: '266px' }}>
            <div
              className="text-[22px] font-semibold tracking-[-.02em] text-white"
              style={{ lineHeight: '1.1' }}
            >
              {available ? amountLabel(available) : '—'}
            </div>
            <div
              className="text-[10px]"
              style={{ marginTop: '1px', color: 'rgba(255,236,246,.6)', lineHeight: '1.2' }}
            >
              ABC 사용 가능
            </div>
          </div>
        </div>
        <div className="flex flex-col" style={{ marginTop: '14px', gap: '7px' }}>
          <div className="flex justify-between" style={{ alignItems: 'baseline' }}>
            <span className="text-[11.5px]" style={{ color: 'rgba(255,236,246,.62)' }}>
              실행에 잠긴 코인
            </span>
            <span className="text-[11.5px] font-semibold" style={{ color: '#c9a9dd' }}>
              {reserved ? priceLabel(reserved) : '—'}
            </span>
          </div>
          {/* 예치금·이번 달 사용은 잔액 응답에 없다. */}
          <div className="flex justify-between" style={{ alignItems: 'baseline' }}>
            <span className="text-[11.5px]" style={{ color: 'rgba(255,236,246,.62)' }}>
              잠긴 코인은 실행이 끝나면 정산되거나 되돌아옵니다
            </span>
          </div>
        </div>
      </div>

      <div
        className="bg-surface-card absolute box-border overflow-hidden"
        style={{
          left: '442px',
          top: '570px',
          width: '900px',
          height: '363px',
          padding: '16px 20px',
          borderRadius: '16px',
          animation: 'rise .7s .34s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div className="text-[15px] font-semibold tracking-[-.01em] text-white">최근 사용 내역</div>
        <div
          className="flex text-[10.5px] font-semibold"
          style={{ marginTop: '14px', color: 'rgba(255,236,246,.55)' }}
        >
          <div style={{ width: '150px' }}>날짜/시간</div>
          <div style={{ width: '110px' }}>유형</div>
          <div style={{ width: '160px' }}>금액</div>
          <div style={{ width: '200px' }}>실행</div>
          <div style={{ width: '160px' }}>트랜잭션</div>
        </div>
        <div
          className="flex flex-col overflow-y-auto"
          style={{ marginTop: '6px', height: '214px' }}
        >
          {history.map((h) => (
            <Fragment key={h.transactionId}>
              <div
                style={{
                  height: '27px',
                  color: 'var(--color-body)',
                  fontVariantNumeric: 'tabular-nums',
                  borderRadius: '6px',
                  transition: 'background .14s',
                }}
                className="hover:bg-surface-inset flex flex-none items-center text-[11.5px]"
              >
                <div style={{ width: '150px' }}>{timeLabel(h.occurredAt)}</div>
                <div
                  className="font-semibold"
                  style={{ width: '110px', color: TYPE_COLORS[h.transactionType] }}
                >
                  {TYPE_LABELS[h.transactionType] ?? h.transactionType}
                </div>
                <div style={{ width: '160px' }}>{priceLabel(h.amount)}</div>
                {/* 실행 상세 화면이 생기면 링크를 붙인다. */}
                <div style={{ width: '200px', color: 'rgba(255,255,255,.45)' }}>
                  {h.executionId ?? '—'}
                </div>
                <div style={{ width: '160px', color: 'rgba(255,255,255,.7)' }}>
                  {h.transactionHash ? shortAddress(h.transactionHash) : '—'}
                </div>
              </div>
            </Fragment>
          ))}

          {transactions.isPending && (
            <div className="text-[11.5px]" style={{ color: 'rgba(255,236,246,.55)' }}>
              사용 내역을 불러오는 중입니다.
            </div>
          )}
          {transactions.isError && (
            <div className="text-[11.5px]" style={{ color: '#ffb0cd' }}>
              사용 내역을 불러오지 못했습니다.
            </div>
          )}
          {isEmpty && (
            <div className="text-[11.5px]" style={{ color: 'rgba(255,236,246,.55)' }}>
              {period.label}에 사용 내역이 없습니다.
            </div>
          )}
        </div>

        <div className="flex" style={{ marginTop: '12px', justifyContent: 'flex-end' }}>
          {transactions.hasNextPage && (
            <button
              type="button"
              onClick={() => void transactions.fetchNextPage()}
              disabled={transactions.isFetchingNextPage}
              style={{ gap: '6px', color: 'rgba(255,240,247,.78)', transition: 'color .16s' }}
              className="flex cursor-pointer items-center text-[11.5px] font-semibold hover:text-[#fff]"
            >
              {transactions.isFetchingNextPage ? '불러오는 중' : '더 보기'}
              <span className="text-[12px]">→</span>
            </button>
          )}
        </div>
      </div>
    </>
  )
}
