import type { ReactNode } from 'react'

/** 섹션 제목. 카드와의 간격도 여기서 준다. */
export default function SectionTitle({ delay, children }: { delay: string; children: ReactNode }) {
  return (
    <div
      className="text-[21px] font-bold tracking-[-.01em] text-white"
      style={{
        marginTop: '48px',
        marginBottom: '14px',
        animation: `rise .7s ${delay} cubic-bezier(.2,.8,.25,1) both`,
      }}
    >
      {children}
    </div>
  )
}
