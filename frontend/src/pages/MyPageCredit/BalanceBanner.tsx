/** 지금 쓸 수 있는 금액. 탭에 따라 사용 가능 금액이나 정산금을 보여 준다. */
export default function BalanceBanner({
  label,
  value,
  note,
}: {
  label: string
  value: string
  note: string
}) {
  return (
    <div
      className="bg-surface-card absolute box-border flex items-center justify-between"
      style={{
        left: '442px',
        top: '300px',
        width: '896px',
        height: '90px',
        padding: '0 24px',
        borderRadius: '12px',
        backdropFilter: 'blur(18px) saturate(115%)',
        WebkitBackdropFilter: 'blur(18px) saturate(115%)',
        animation: 'rise .7s .14s cubic-bezier(.2,.8,.25,1) both',
      }}
    >
      <div>
        <div className="text-[14.5px]" style={{ color: 'rgba(255,236,246,.62)' }}>
          {label}
        </div>
        <div
          className="text-[26px] font-semibold tracking-[-.01em] text-white"
          style={{ marginTop: '4px' }}
        >
          {value}
        </div>
      </div>
      <div className="text-[14.5px]" style={{ color: 'rgba(255,236,246,.58)' }}>
        {note}
      </div>
    </div>
  )
}
