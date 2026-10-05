import StatusDot from '@/components/StatusDot'
import TrendChart from '@/components/mypage/TrendChart'
import { priceLabel, runLabel } from '@/lib/format'

import type { useUsageTrend } from './useUsageTrend'

const LEGEND = [
  { color: '#e9aec4', label: '사용 금액' },
  { color: '#dba18b', label: '실행 수' },
]

/** 기간 안의 사용 금액(주 선)과 실행 수(옅은 선). */
export default function UsageTrendCard({
  trend,
  periodLabel,
}: {
  trend: ReturnType<typeof useUsageTrend>
  periodLabel: string
}) {
  return (
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
        {trend.isLoaded
          ? `${periodLabel} 총 사용 ${priceLabel(trend.totals.spent)} · 실행 ${runLabel(trend.totals.executionCount)}${trend.cut ? ' · 최근 내역 일부 기준' : ''}`
          : trend.isError
            ? '사용량을 불러오지 못했습니다.'
            : '사용량을 불러오는 중입니다.'}
      </div>
      <div className="flex" style={{ marginTop: '10px', gap: '18px' }}>
        {LEGEND.map((l) => (
          <div key={l.label} className="flex items-center" style={{ gap: '7px' }}>
            <StatusDot color={l.color} />
            <div
              className="text-[12.5px] whitespace-nowrap"
              style={{ color: 'var(--color-muted)' }}
            >
              {l.label}
            </div>
          </div>
        ))}
      </div>

      <TrendChart
        id="spend"
        line={trend.spendLine}
        subLine={trend.runLine}
        ticks={trend.ticks}
        marginTop={16}
      />
    </div>
  )
}
