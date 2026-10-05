import StatusDot from '@/components/StatusDot'
import { amountLabel } from '@/lib/format'

import { GAUGE_LENGTH, gaugeArc } from './balanceGauge'

/** 사용 가능 · 실행 중 ABC 를 고리 하나로 보여 준다. */
export default function BalanceCard({
  available,
  reserved,
}: {
  available: string | undefined
  reserved: string | undefined
}) {
  return (
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
              strokeDasharray={`${gaugeArc(available, reserved)} ${GAUGE_LENGTH}`}
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
          <div className="text-[13px]" style={{ marginTop: '3px', color: 'rgba(255,236,246,.62)' }}>
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
  )
}
