import type { ReactNode } from 'react'

/** 목록 자리에 대신 띄우는 한 줄 — 불러오는 중 · 실패 · 비어 있음. */
export default function ListMessage({ error, children }: { error?: boolean; children: ReactNode }) {
  return (
    <div className="text-[13px]" style={{ color: error ? '#ffb0cd' : 'rgba(255,236,246,.55)' }}>
      {children}
    </div>
  )
}
