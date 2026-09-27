import { useEffect, useRef, useState } from 'react'

import { AI_MODELS, type AiModel } from '@/api/nodes'

/**
 * 모델 고르기.
 *
 * 네이티브 `select` 는 운영체제가 그리는 목록이 떠서 화면과 결이 맞지 않는다. 눌린 칸 바로
 * 아래로 떨어뜨린다 — 목록이 하나뿐이어도 고를 수 있다는 것이 보여야 한다.
 */
const OPTION_HEIGHT = 40

export default function ModelSelect({
  value,
  onChange,
  className,
  style,
}: {
  value: AiModel
  onChange: (next: AiModel) => void
  className?: string
  style?: React.CSSProperties
}) {
  const [open, setOpen] = useState(false)
  const boxRef = useRef<HTMLDivElement>(null)
  const listRef = useRef<HTMLDivElement>(null)

  // 목록이 길어 고른 모델이 아래에 있으면 열었을 때 보이지 않는다.
  useEffect(() => {
    if (open)
      listRef.current?.querySelector('[data-selected]')?.scrollIntoView?.({ block: 'nearest' })
  }, [open])

  // 바깥을 누르거나 Esc 로 닫는다. 열어 둔 채 다른 곳을 눌렀을 때 남아 있으면 길을 잃는다.
  useEffect(() => {
    if (!open) return
    const onDown = (ev: PointerEvent) => {
      if (!boxRef.current?.contains(ev.target as Node)) setOpen(false)
    }
    const onKey = (ev: KeyboardEvent) => {
      if (ev.key === 'Escape') setOpen(false)
    }
    document.addEventListener('pointerdown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const current = AI_MODELS.find((item) => item.value === value) ?? AI_MODELS[0]

  return (
    <div ref={boxRef} className={className} style={{ ...style, position: 'absolute' }}>
      <button
        type="button"
        onClick={() => setOpen((it) => !it)}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-label="AI 모델"
        className="bg-surface-card flex w-full cursor-pointer items-center justify-between border-0 font-[inherit] text-white hover:bg-[rgba(255,255,255,.14)]"
        style={{ height: '100%', borderRadius: '11px', padding: '0 12px' }}
      >
        <span className="truncate">{current.label}</span>
        <svg width="12" height="8" viewBox="0 0 12 8" fill="none" aria-hidden>
          <path
            d="M1 1.5L6 6.5L11 1.5"
            stroke="rgba(255,255,255,.66)"
            strokeWidth="1.6"
            strokeLinecap="round"
            strokeLinejoin="round"
            style={{ transformOrigin: '50% 50%', transform: open ? 'rotate(180deg)' : 'none' }}
          />
        </svg>
      </button>

      {open && (
        <div
          ref={listRef}
          role="listbox"
          className="absolute overflow-x-hidden overflow-y-auto"
          style={{
            left: 0,
            right: 0,
            // 눌린 칸 바로 아래. 6px 은 그림자가 칸에 붙어 보이지 않을 만큼만 띄운 값이다.
            top: 'calc(100% + 6px)',
            // 다섯 칸 반만 보여 아래에 더 있다는 것이 드러나게 한다.
            maxHeight: `${OPTION_HEIGHT * 5.5}px`,
            borderRadius: '11px',
            background: 'var(--color-surface-menu)',
            boxShadow: 'inset 0 0 0 1px rgba(255,255,255,.14),0 14px 34px rgba(4,2,12,.5)',
            backdropFilter: 'blur(18px)',
            WebkitBackdropFilter: 'blur(18px)',
            animation: 'uiIn .16s cubic-bezier(.2,.8,.25,1) both',
            zIndex: 3,
          }}
        >
          {AI_MODELS.map((item) => (
            <button
              key={item.value}
              type="button"
              role="option"
              aria-selected={item.value === value}
              data-selected={item.value === value || undefined}
              onClick={() => {
                onChange(item.value)
                setOpen(false)
              }}
              className="flex w-full cursor-pointer items-center gap-[10px] border-0 bg-transparent text-left font-[inherit] text-white hover:bg-[rgba(255,255,255,.12)]"
              style={{
                height: `${OPTION_HEIGHT}px`,
                padding: '0 12px',
                background: item.value === value ? 'rgba(255,255,255,.10)' : undefined,
              }}
            >
              <span className="flex-1 truncate">{item.label}</span>
              {item.hint && (
                <span className="flex-none text-[13px]" style={{ color: 'rgba(255,224,238,.6)' }}>
                  {item.hint}
                </span>
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
