import { ACCENT_BUTTON } from './config'

interface ChargeProgressProps {
  card: { title: string; note: string; action: { label: string; run: () => void } | null }
  transactionHash: string | null
}

/** 충전이 시작된 뒤 요약 자리를 차지하는 진행 상태. */
export default function ChargeProgress({ card, transactionHash }: ChargeProgressProps) {
  return (
    <>
      <div style={{ width: '530px' }}>
        <div className="text-[15.5px] font-semibold tracking-[-.01em] text-white">{card.title}</div>
        <div
          className="text-[15.5px]"
          style={{ marginTop: '10px', color: 'rgba(255,236,246,.72)' }}
        >
          {card.note}
        </div>
        {/* 해시는 문의할 때 유일한 단서다. 끝날 때까지 지우지 않는다. */}
        {transactionHash && (
          <div
            className="truncate text-[15.5px]"
            style={{ marginTop: '8px', color: 'rgba(255,236,246,.5)' }}
          >
            거래 해시 {transactionHash}
          </div>
        )}
      </div>
      {card.action && (
        <div
          onClick={card.action.run}
          style={{ width: '300px', height: '60px', ...ACCENT_BUTTON }}
          className="flex cursor-pointer items-center justify-center text-[17px] font-semibold tracking-[-.01em] hover:brightness-[1.06]"
        >
          {card.action.label}
        </div>
      )}
    </>
  )
}
