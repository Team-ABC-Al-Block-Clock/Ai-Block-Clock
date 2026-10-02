interface CreditSummaryProps {
  title: string
  sumLabel: string
  sumCredit: string
  details: { label: string; value: string }[]
  /** 요약 아래 한 줄. 없으면 그리지 않는다. */
  message: { text: string; good: boolean } | null
  ctaLabel: string
  /** 막혔으면 버튼을 흐리게 한다 */
  blocked: boolean
  onCta: () => void
}

/** 보낼 금액과 그 뒤의 잔액, 그리고 보내는 버튼. */
export default function CreditSummary({
  title,
  sumLabel,
  sumCredit,
  details,
  message,
  ctaLabel,
  blocked,
  onCta,
}: CreditSummaryProps) {
  return (
    <>
      <div style={{ width: '500px' }}>
        <div className="text-[14px] font-semibold" style={{ color: 'rgba(255,236,246,.62)' }}>
          {title}
        </div>
        <div
          className="flex items-baseline justify-between"
          style={{ marginTop: '10px', paddingBottom: '14px' }}
        >
          <span className="text-[15px]" style={{ color: 'rgba(255,236,246,.72)' }}>
            {sumLabel}
          </span>
          <span className="text-[26px] font-semibold tracking-[-.02em] text-white">
            {sumCredit}
          </span>
        </div>
        <div style={{ paddingTop: '12px', borderTop: '1px solid rgba(255,255,255,.1)' }}>
          {details.map((d, i) => (
            <div
              key={d.label}
              className="flex items-baseline justify-between"
              style={{ marginTop: i === 0 ? 0 : '8px' }}
            >
              <span className="text-[15px]" style={{ color: 'rgba(255,236,246,.72)' }}>
                {d.label}
              </span>
              <span
                className="text-[15.5px] font-semibold"
                style={{ color: 'rgba(255,255,255,.86)' }}
              >
                {d.value}
              </span>
            </div>
          ))}
        </div>
        {message && (
          <div
            className="text-[15px]"
            style={{ marginTop: '10px', color: message.good ? '#9ad0b4' : 'rgba(255,196,214,.9)' }}
          >
            {message.text}
          </div>
        )}
      </div>
      <div
        className="flex flex-col justify-center self-stretch"
        style={{
          gap: '12px',
          paddingLeft: '30px',
          borderLeft: '1px solid rgba(255,255,255,.1)',
        }}
      >
        <div
          onClick={onCta}
          style={{
            width: '280px',
            height: '58px',
            borderRadius: '14px',
            background: 'var(--color-accent)',
            color: 'var(--color-accent-ink)',
            opacity: blocked ? 0.5 : 1,
            transition: 'filter .16s',
          }}
          className={`flex items-center justify-center text-[17px] font-semibold tracking-[-.01em] ${blocked ? 'cursor-not-allowed' : 'cursor-pointer hover:brightness-[1.06]'}`}
        >
          {ctaLabel}
        </div>
      </div>
    </>
  )
}
