import type { ReactNode } from 'react'

import { PANEL_INSET } from '@/components/SidePanel'

/** 「노드 추가」 패널과 같은 차림 — 라벨 아래 칸 하나. 두 자리가 달라 보이면 같은 값이 아닌 줄 안다. */
export default function FieldLabel({ top, children }: { top: string; children: ReactNode }) {
  return (
    <div
      className="absolute text-[15px]"
      style={{ left: `${PANEL_INSET}px`, top, color: 'rgba(255,214,232,.85)' }}
    >
      {children}
    </div>
  )
}
