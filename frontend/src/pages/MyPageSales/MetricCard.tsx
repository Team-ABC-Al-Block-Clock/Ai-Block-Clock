interface MetricCardProps {
  label: string
  value: string
  delta: string
  /** 있으면 아래 한 줄이 그 화면으로 가는 링크가 된다. */
  href?: string
  /** 몇 번째 칸인지. 칸마다 조금씩 늦게 떠오른다. */
  index: number
}

/** 위쪽 네 칸짜리 지표 카드 하나. */
export default function MetricCard({ label, value, delta, href, index }: MetricCardProps) {
  return (
    <div
      className="bg-surface-card box-border flex flex-none flex-col justify-center"
      style={{
        width: '212px',
        height: '140px',
        padding: '18px',
        borderRadius: '16px',
        animation: `rise .7s ${(0.14 + index * 0.04).toFixed(2)}s cubic-bezier(.2,.8,.25,1) both`,
      }}
    >
      <div>
        <div
          className="text-[14px] tracking-[-.01em]"
          style={{ color: 'rgba(255,236,246,.62)', lineHeight: '1.3' }}
        >
          {label}
        </div>
        <div
          className="text-[26px] font-semibold tracking-[-.02em] text-white"
          style={{ marginTop: '10px', lineHeight: '1.1' }}
        >
          {value}
        </div>
        {href ? (
          <a
            href={href}
            className="block text-[13px] hover:text-white"
            style={{ marginTop: '8px', color: '#dcaebf', lineHeight: '1.2' }}
          >
            {delta}
          </a>
        ) : (
          <div
            className="text-[13px]"
            style={{ marginTop: '8px', color: '#dcaebf', lineHeight: '1.2' }}
          >
            {delta}
          </div>
        )}
      </div>
    </div>
  )
}
