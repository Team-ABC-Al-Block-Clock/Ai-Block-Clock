import type { CostEstimate } from '@/api/executions'
import { PANEL_CONTENT_WIDTH, PANEL_INSET } from '@/components/SidePanel'
import { amountLabel, priceLabel } from '@/lib/format'
import { paths } from '@/routes/paths'

import { CHARGE_LINK, COST_Y } from './config'

interface CostCardProps {
  cost: CostEstimate | undefined
  /** 잔액 대비 견적. 0 ~ 1 */
  ratio: number
  shortage: number
  blocked: boolean
}

/** 실행 예상 비용과 사용 가능 금액. 모자라면 충전으로 가는 길을 함께 둔다. */
export default function CostCard({ cost, ratio, shortage, blocked }: CostCardProps) {
  return (
    <div
      className="absolute box-border flex flex-col"
      style={{
        left: `${PANEL_INSET}px`,
        top: `${COST_Y}px`,
        width: `${PANEL_CONTENT_WIDTH}px`,
        height: '194px',
        padding: '18px 20px',
        borderRadius: '14px',
        background: 'linear-gradient(180deg, rgba(201,169,221,.13), rgba(255,255,255,.05))',
      }}
    >
      <div
        className="flex-none text-[16.5px] font-semibold"
        style={{ letterSpacing: '.06em', color: 'rgba(255,214,232,.85)' }}
      >
        실행 예상 비용
      </div>
      <div
        className="flex flex-none"
        style={{ marginTop: '9px', alignItems: 'baseline', gap: '8px' }}
      >
        <div
          className="text-[34px] font-semibold tracking-[-.02em] text-white"
          style={{ lineHeight: '1' }}
        >
          {cost ? amountLabel(cost.estimatedAmount) : '—'}
        </div>
        <div className="text-[16.5px] font-semibold" style={{ color: 'rgba(255,236,246,.9)' }}>
          ABC
        </div>
      </div>
      <div
        className="flex flex-none justify-between"
        style={{ marginTop: '16px', alignItems: 'baseline' }}
      >
        <span className="text-[16px]" style={{ color: 'rgba(255,236,246,.82)' }}>
          사용 가능 금액
        </span>
        <span className="text-[17px] font-semibold" style={{ color: '#d7bce8' }}>
          {cost ? priceLabel(cost.availableAmount) : '—'}
        </span>
      </div>
      <div
        className="flex-none overflow-hidden"
        style={{
          marginTop: '7px',
          height: '4px',
          borderRadius: '2px',
          background: 'rgba(255,255,255,.1)',
        }}
      >
        <div
          style={{
            width: `${(ratio * 100).toFixed(1)}%`,
            height: '4px',
            borderRadius: '2px',
            background: blocked ? '#e2849f' : '#c9a9dd',
            transition: 'width .3s ease, background .3s ease',
          }}
        ></div>
      </div>
      <div
        className="flex-none text-[14.5px] tracking-[-.005em]"
        style={{
          marginTop: 'auto',
          color: blocked ? 'rgba(255,190,209,.86)' : 'rgba(255,236,246,.44)',
        }}
      >
        {blocked ? (
          <>
            {`${priceLabel(shortage)} 부족합니다. `}
            <a href={paths.my.credit} style={CHARGE_LINK}>
              충전하러 가기
            </a>
          </>
        ) : (
          '실제 사용량에 따라 달라질 수 있습니다'
        )}
      </div>
    </div>
  )
}
