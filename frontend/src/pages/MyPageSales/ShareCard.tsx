import { SHARE_COLORS } from './config'

interface ShareCardProps {
  topAmount: string
  topName: string
  /** 2 ~ 4위. 늘 세 칸이다. */
  shares: { label: string; value: string; width: string }[]
}

/** 기간 1위 에이전트의 판매 금액과 그 아래 순위의 비중 막대. */
export default function ShareCard({ topAmount, topName, shares }: ShareCardProps) {
  return (
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
        {topAmount}
      </div>
      <div className="text-[12.5px]" style={{ marginTop: '4px', color: 'rgba(255,236,246,.6)' }}>
        {topName}
      </div>
      <div className="flex flex-col" style={{ marginTop: '14px', gap: '10px' }}>
        {shares.map((t, i) => (
          <div key={i}>
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
                  width: t.width,
                  height: '7px',
                  borderRadius: '4px',
                  background: SHARE_COLORS[i],
                  transformOrigin: 'left center',
                  animation: `growBar .8s ${(0.4 + i * 0.12).toFixed(2)}s cubic-bezier(.2,.8,.25,1) both`,
                }}
              ></div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
