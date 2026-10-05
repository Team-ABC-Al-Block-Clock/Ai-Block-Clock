import TrendChart from '@/components/mypage/TrendChart'

interface SalesTrendCardProps {
  line: string
  ticks: string[]
  periodLabel: string
  noSales: boolean
  isError: boolean
}

const OVERLAY = 'absolute left-0 flex items-center justify-center text-[13.5px]'
const OVERLAY_BOX = { top: '0', width: '534px', height: '134px' } as const

/** 기간 동안 받은 정산금의 날짜별 추이. */
export default function SalesTrendCard({
  line,
  ticks,
  periodLabel,
  noSales,
  isError,
}: SalesTrendCardProps) {
  return (
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
        {noSales && (
          <div className={OVERLAY} style={{ ...OVERLAY_BOX, color: 'rgba(255,236,246,.55)' }}>
            {periodLabel}에 받은 정산금이 없습니다.
          </div>
        )}
        {isError && (
          <div className={OVERLAY} style={{ ...OVERLAY_BOX, color: '#ffb0cd' }}>
            정산 통계를 불러오지 못했습니다.
          </div>
        )}
      </TrendChart>
    </div>
  )
}
