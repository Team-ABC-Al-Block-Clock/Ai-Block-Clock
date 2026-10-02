import { useEffect, useState } from 'react'

import { PERIODS } from '@/lib/stats/period'

interface PeriodMenuProps {
  /** 고른 PERIODS 칸 */
  at: number
  onPick: (at: number) => void
}

/** 오른쪽 위 기간 선택. 사용 내역 · 판매 내역이 같이 쓴다. */
export default function PeriodMenu({ at, onPick }: PeriodMenuProps) {
  const [open, setOpen] = useState(false)

  const pick = (next: number) => {
    onPick(next)
    setOpen(false)
  }

  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open])

  return (
    <div className="absolute" style={{ left: '1210px', top: '160px', width: '128px', zIndex: 5 }}>
      {/* 등장 모션은 버튼에만 건다 — transform 이 걸린 조상 안에서는 아래 메뉴의 유리가 죽는다. */}
      <button
        type="button"
        onClick={() => setOpen(!open)}
        style={{
          width: '128px',
          height: '38px',
          borderRadius: '19px',
          padding: '0 15px',
          animation: 'rise .7s .12s cubic-bezier(.2,.8,.25,1) both',
        }}
        className="box-border flex cursor-pointer items-center justify-between bg-[rgba(255,255,255,.09)] hover:bg-[rgba(255,255,255,.18)]"
      >
        <div className="text-[13.5px]" style={{ color: 'rgba(255,240,247,.84)' }}>
          {PERIODS[at].label}
        </div>
        <div
          style={{
            width: '0',
            height: '0',
            borderLeft: '4px solid transparent',
            borderRight: '4px solid transparent',
            borderTop: '5px solid rgba(255,255,255,.6)',
          }}
        ></div>
      </button>

      {/* 바깥을 덮어 어디를 눌러도 닫는다 — `profile-menu` 와 같은 방식이다. */}
      {open && (
        <div className="fixed inset-0" style={{ zIndex: 4 }} onClick={() => setOpen(false)}></div>
      )}

      {open && (
        <div
          className="bg-surface-menu relative box-border flex flex-col overflow-hidden"
          style={{
            marginTop: '6px',
            width: '128px',
            borderRadius: '12px',
            padding: '4px',
            // veil 보다 위에 와야 항목이 눌린다.
            zIndex: 5,
            backdropFilter: 'blur(18px) saturate(115%)',
            WebkitBackdropFilter: 'blur(18px) saturate(115%)',
          }}
        >
          {PERIODS.map((p, i) => (
            <button
              key={p.label}
              type="button"
              onClick={() => pick(i)}
              style={{
                height: '32px',
                padding: '0 11px',
                borderRadius: '8px',
                color: i === at ? '#f0dcea' : 'rgba(255,240,247,.78)',
                background: i === at ? 'rgba(185,141,169,.28)' : 'transparent',
              }}
              className="flex cursor-pointer items-center text-[13.5px] hover:bg-[rgba(255,255,255,.12)]"
            >
              {p.label}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
