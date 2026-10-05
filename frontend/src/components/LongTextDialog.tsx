import { useEffect, useRef, useState, type CSSProperties } from 'react'
import { createPortal } from 'react-dom'

interface LongTextDialogProps {
  title: string
  value: string
  onChange: (next: string) => void
  onClose: () => void
  maxLength?: number
  placeholder?: string
}

/** 좁은 칸에 적던 글을 화면 가운데 큰 창에서 이어 쓴다. 원래 칸과 같은 값을 고친다. */
export default function LongTextDialog({
  title,
  value,
  onChange,
  onClose,
  maxLength,
  placeholder,
}: LongTextDialogProps) {
  const areaRef = useRef<HTMLTextAreaElement>(null)
  const doneRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    const opener = document.activeElement
    const area = areaRef.current
    if (area) {
      area.focus()
      area.setSelectionRange(area.value.length, area.value.length)
    }
    return () => {
      if (opener instanceof HTMLElement) opener.focus()
    }
  }, [])

  const scale = useFrameScale()

  return createPortal(
    <div
      role="dialog"
      aria-modal="true"
      aria-label={title}
      className="fixed inset-0 flex items-center justify-center"
      // 화면 틀 안에 두면 껍데기(헤더 · Studio · 메뉴, zIndex 40)가 위에 남아 눌린다. 창 위에 직접 띄운다.
      style={{ zIndex: 50 }}
      onKeyDown={(e) => {
        if (e.key === 'Escape') return onClose()
        if (e.key !== 'Tab') return
        // 포커스를 받는 것은 입력 칸과 완료뿐이라 둘 사이에서만 돌린다. 뒤의 화면으로 새지 않게 한다.
        e.preventDefault()
        const next = document.activeElement === areaRef.current ? doneRef.current : areaRef.current
        next?.focus()
      }}
    >
      <div
        onClick={onClose}
        className="absolute inset-0"
        style={{
          background: 'rgba(12,5,22,.55)',
          backdropFilter: 'blur(6px)',
          WebkitBackdropFilter: 'blur(6px)',
        }}
      ></div>
      {/* 창 위에 띄우면 틀의 배율을 받지 못해 같은 배율로 직접 줄인다 */}
      <div className="relative flex-none" style={{ transform: `scale(${scale})` }}>
        <div
          className="relative box-border flex flex-col"
          style={{
            width: '880px',
            height: '640px',
            padding: '28px',
            borderRadius: '20px',
            background: 'rgba(22,12,38,.96)',
            boxShadow: '0 20px 52px rgba(4,2,12,.55)',
            animation: 'rise .3s cubic-bezier(.2,.9,.3,1) both',
          }}
        >
          <div
            className="flex flex-none items-baseline justify-between"
            style={{ marginBottom: '16px' }}
          >
            <div className="text-[19px] font-semibold tracking-[-.01em] text-white">{title}</div>
            {maxLength !== undefined && (
              <div className="text-[14px]" style={{ color: 'rgba(255,236,246,.55)' }}>
                {value.length} / {maxLength}
              </div>
            )}
          </div>
          <div
            className="bg-surface-card box-border flex-1"
            style={{ minHeight: 0, borderRadius: '12px', padding: '16px 18px' }}
          >
            <textarea
              ref={areaRef}
              className="scroll-thin h-full w-full border-0 bg-transparent font-[inherit] text-[16px] tracking-[-.005em] text-white outline-0"
              value={value}
              maxLength={maxLength}
              placeholder={placeholder}
              onChange={(e) => onChange(e.target.value)}
              aria-label={title}
              style={{ resize: 'none', lineHeight: '1.65' }}
            ></textarea>
          </div>
          <div className="flex flex-none justify-end" style={{ marginTop: '18px' }}>
            <button
              ref={doneRef}
              type="button"
              onClick={onClose}
              className="flex cursor-pointer items-center border-0 font-[inherit] text-[15px] font-bold hover:brightness-[1.05]"
              style={{
                height: '44px',
                padding: '0 26px',
                borderRadius: '12px',
                background: 'var(--color-accent)',
                color: 'var(--color-accent-ink)',
              }}
            >
              완료
            </button>
          </div>
        </div>
      </div>
    </div>,
    document.body,
  )
}

/** `screen-frame` 이 화면을 줄인 배율. 창 크기가 바뀌면 `framefit` 으로 다시 받는다. */
function useFrameScale() {
  const [scale, setScale] = useState(readFrameScale)
  useEffect(() => {
    const frame = document.querySelector('screen-frame')
    if (!frame) return
    const sync = () => setScale(readFrameScale())
    frame.addEventListener('framefit', sync)
    return () => frame.removeEventListener('framefit', sync)
  }, [])
  return scale
}

function readFrameScale() {
  const frame = document.querySelector('screen-frame') as (Element & { scale?: number }) | null
  return frame?.scale || 1
}

/** 입력 칸 모서리에 붙여 `LongTextDialog` 를 여는 버튼. */
export function ExpandButton({ onClick, style }: { onClick: () => void; style?: CSSProperties }) {
  return (
    <button
      type="button"
      onClick={onClick}
      title="크게 쓰기"
      aria-label="크게 쓰기"
      className="absolute flex cursor-pointer items-center justify-center border-0 bg-transparent hover:bg-[rgba(255,255,255,.14)]"
      style={{
        width: '26px',
        height: '26px',
        borderRadius: '7px',
        color: 'rgba(255,255,255,.62)',
        ...style,
      }}
    >
      <svg
        width="14"
        height="14"
        viewBox="0 0 16 16"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <path d="M9.5 2.5h4v4M13.5 2.5 9 7M6.5 13.5h-4v-4M2.5 13.5 7 9" />
      </svg>
    </button>
  )
}
