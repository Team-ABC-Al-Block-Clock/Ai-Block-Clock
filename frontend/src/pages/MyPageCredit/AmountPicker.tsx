import { priceLabel } from '@/lib/format'

interface AmountPickerProps {
  title: string
  note: string
  presets: { amount: string; disabled: boolean }[]
  /** 고른 칸의 순서 */
  at: number
  onPick: (index: number) => void
  typed: string
  placeholder: string
  onType: (value: string) => void
}

/** 금액 칸 네 개와 직접 입력. */
export default function AmountPicker({
  title,
  note,
  presets,
  at,
  onPick,
  typed,
  placeholder,
  onType,
}: AmountPickerProps) {
  return (
    <>
      <div
        className="absolute flex"
        style={{
          left: '442px',
          top: '424px',
          alignItems: 'baseline',
          gap: '14px',
          animation: 'rise .7s .18s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div className="text-[18px] font-semibold tracking-[-.01em] text-white">{title}</div>
        <div className="text-[14.5px]" style={{ color: 'rgba(255,236,246,.58)' }}>
          {note}
        </div>
      </div>

      <div
        className="absolute flex"
        style={{ left: '442px', top: '460px', width: '896px', gap: '16px' }}
      >
        {presets.map((p, i) => (
          <div
            key={i}
            onClick={() => !p.disabled && onPick(i)}
            style={{
              width: '212px',
              height: '96px',
              padding: '0 22px',
              borderRadius: '12px',
              background: i === at ? 'rgba(185,141,169,.32)' : 'var(--color-surface-card)',
              boxShadow: 'none',
              transition: 'background .16s,box-shadow .16s',
              animation: `rise .7s ${(0.2 + i * 0.04).toFixed(2)}s cubic-bezier(.2,.8,.25,1) both`,
            }}
            className={`box-border flex flex-none items-center ${p.disabled ? 'cursor-not-allowed' : 'cursor-pointer hover:bg-[rgba(255,255,255,.14)]'}`}
          >
            <div
              className="text-[24px] font-semibold tracking-[-.01em]"
              style={{
                color: p.disabled ? 'rgba(255,240,247,.38)' : i === at ? '#e6cbe0' : '#fff',
              }}
            >
              {priceLabel(p.amount)}
            </div>
          </div>
        ))}
      </div>

      <div
        className="absolute text-[15px] font-semibold tracking-[-.01em] text-white"
        style={{ left: '442px', top: '590px' }}
      >
        직접 입력
      </div>
      <div
        className="absolute box-border flex items-center justify-between"
        style={{
          left: '442px',
          top: '620px',
          width: '896px',
          height: '64px',
          padding: '0 22px',
          borderRadius: '10px',
          background: 'rgba(255,255,255,.06)',
        }}
      >
        <input
          className="flex-1 border-0 bg-transparent font-[inherit] text-[16px] tracking-[-.01em] text-white outline-0"
          id="creditInput"
          type="text"
          onChange={(e) => onType(e.target.value)}
          placeholder={placeholder}
          style={{ height: '32px' }}
          value={typed}
        />
        <div className="text-[16px] font-semibold" style={{ color: 'rgba(255,236,246,.6)' }}>
          ABC
        </div>
      </div>
    </>
  )
}
