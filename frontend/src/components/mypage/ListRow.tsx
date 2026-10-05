import type { ReactNode } from 'react'

/** 목록 카드의 한 줄. 열 폭은 칸마다 밖에서 준다. */
export default function ListRow({ children }: { children: ReactNode }) {
  return (
    <div
      style={{
        height: '44px',
        color: 'var(--color-body)',
        fontVariantNumeric: 'tabular-nums',
        borderRadius: '6px',
        transition: 'background .14s',
      }}
      className="hover:bg-surface-inset flex flex-none items-center text-[15.5px]"
    >
      {children}
    </div>
  )
}
