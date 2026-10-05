import type { ReactNode } from 'react'

/** 목록 카드의 열 이름 줄. */
export default function ListHeader({ children }: { children: ReactNode }) {
  return (
    <div
      className="flex text-[13.5px] font-semibold"
      style={{ marginTop: '14px', color: 'rgba(255,236,246,.72)' }}
    >
      {children}
    </div>
  )
}
