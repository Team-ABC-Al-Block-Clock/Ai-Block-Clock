import type { ReactNode } from 'react'

import { TREND_HEIGHT, TREND_WIDTH } from '@/lib/stats/salesTrend'

const GRID_TOPS = [0, 40, 80, 120]

interface TrendChartProps {
  /** 그라데이션 id 접두사. 한 문서에서 id 가 겹치면 뒤에 그린 차트가 앞 차트의 색을 가져간다. */
  id: string
  /** `trendPath` 로 그린 주 선. 아래를 옅게 채운다. */
  line: string
  /** 같은 축에 옅게 겹쳐 그리는 두 번째 선. */
  subLine?: string
  ticks: string[]
  marginTop: number
  /** 그래프 자리 위에 띄우는 안내(빈 기간 · 실패). */
  children?: ReactNode
}

/** 판매 · 사용 내역이 같이 쓰는 날짜별 추이 그래프. 격자 · 선 · 눈금까지 그린다. */
export default function TrendChart({
  id,
  line,
  subLine,
  ticks,
  marginTop,
  children,
}: TrendChartProps) {
  return (
    <div
      className="relative"
      style={{ marginTop: `${marginTop}px`, width: `${TREND_WIDTH}px`, height: '158px' }}
    >
      {GRID_TOPS.map((top) => (
        <div
          key={top}
          className="absolute left-0 bg-[rgba(255,255,255,.09)]"
          style={{ top: `${top}px`, width: `${TREND_WIDTH}px`, height: '1px' }}
        ></div>
      ))}
      <svg
        className="absolute top-0 left-0"
        width={TREND_WIDTH}
        height="134"
        viewBox={`0 0 ${TREND_WIDTH} ${TREND_HEIGHT}`}
        preserveAspectRatio="none"
        fill="none"
        style={{ overflow: 'visible' }}
      >
        <defs>
          <linearGradient id={`${id}Line`} x1="0" y1="1" x2="1" y2="0">
            <stop offset="0%" stopColor="#dba18b" />
            <stop offset="100%" stopColor="#e3b7c8" />
          </linearGradient>
          <linearGradient id={`${id}Fill`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#ffb0cd" stopOpacity=".34" />
            <stop offset="100%" stopColor="#ffb0cd" stopOpacity="0" />
          </linearGradient>
        </defs>
        {line && (
          <path
            d={`${line} L${TREND_WIDTH} ${TREND_HEIGHT} L0 ${TREND_HEIGHT} Z`}
            fill={`url(#${id}Fill)`}
          />
        )}
        {line && (
          <path
            d={line}
            fill="none"
            stroke={`url(#${id}Line)`}
            strokeWidth="2.4"
            strokeLinecap="round"
            strokeLinejoin="round"
            pathLength={1400}
            strokeDasharray="1400"
            style={{ animation: 'drawUp 1.5s .35s cubic-bezier(.3,.7,.2,1) both' }}
          />
        )}
        {subLine && (
          <path
            d={subLine}
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
      {children}
      <div
        className="absolute left-0 flex justify-between text-[12px]"
        style={{ top: '142px', width: `${TREND_WIDTH}px`, color: 'rgba(255,236,246,.5)' }}
      >
        {ticks.map((t, i) => (
          <span key={i}>{t.replace('-', '/')}</span>
        ))}
      </div>
    </div>
  )
}
