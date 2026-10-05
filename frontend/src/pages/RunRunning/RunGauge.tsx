import { PANEL_WIDTH } from '@/components/SidePanel'

interface RunGaugeProps {
  pctLabel: string
  gaugeBg: string
  stepTitle: string
  stepMeta: string
  /** 앞서 받은 진행은 있는데 지금 소식이 끊겼다. */
  stale: boolean
}

/** 오른쪽 패널 위의 진행률 원과 지금 도는 단계. */
export default function RunGauge({ pctLabel, gaugeBg, stepTitle, stepMeta, stale }: RunGaugeProps) {
  return (
    <>
      <div
        className="absolute"
        style={{ left: '148px', top: '138px', width: '164px', height: '164px' }}
      >
        <div
          className="absolute rounded-full"
          style={{
            inset: '-18px',
            background:
              'radial-gradient(circle, rgba(255,160,200,.26) 0 46%, rgba(255,160,200,0) 74%)',
            animation: 'runGlow 2.6s ease-in-out infinite',
          }}
        ></div>
        <div className="absolute inset-0 rounded-full" style={{ background: gaugeBg }}></div>
        <div
          className="absolute rounded-full"
          style={{ inset: '13px', background: 'rgba(14,8,26,.92)' }}
        ></div>
        <div className="absolute inset-0 flex items-center justify-center text-[36px] font-bold tracking-[-.02em] text-white">
          {pctLabel}
        </div>
      </div>

      <div
        className="absolute left-0 text-center text-[19.5px] font-semibold tracking-[-.01em]"
        style={{ top: '330px', width: `${PANEL_WIDTH}px`, color: 'rgba(255,255,255,.9)' }}
      >
        {stepTitle}
      </div>
      <div
        className="absolute left-0 text-center text-[17px]"
        style={{ top: '356px', width: `${PANEL_WIDTH}px`, color: 'rgba(255,236,246,.72)' }}
      >
        {stepMeta}
      </div>

      {stale && (
        <div
          className="absolute left-0 text-center text-[13px]"
          style={{ top: '380px', width: `${PANEL_WIDTH}px`, color: 'rgba(255,214,232,.6)' }}
        >
          진행 상황을 잠시 받지 못하고 있습니다
        </div>
      )}
    </>
  )
}
