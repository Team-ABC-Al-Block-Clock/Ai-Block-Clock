import { timeLabel } from '@/lib/format'

import { CHIP } from './config'

/**
 * 워크플로우 한 장. 행을 누르면 캔버스를 복원한다.
 * 「실행하기」도 지금은 캔버스로 보낸다 — 실행 생성 API 가 오면 그 요청으로 바꾼다.
 */
export default function WorkflowCard({
  name,
  description,
  lastExecutedAt,
  href,
  delay,
  onDelete,
}: {
  name: string
  description: string | null
  lastExecutedAt: string | null
  href: string
  delay: number
  onDelete: () => void
}) {
  const handleDelete = (e: React.MouseEvent) => {
    // 카드 전체가 링크다. 삭제를 누를 때 캔버스로 이동하면 모달이 사라진다.
    e.preventDefault()
    e.stopPropagation()
    onDelete()
  }

  return (
    <a
      href={href}
      className="bg-surface-card box-border block flex-none transition-colors duration-200 hover:bg-[rgba(255,255,255,.13)]"
      style={{
        width: '896px',
        padding: '22px 24px',
        borderRadius: '18px',
        animation: `rise .6s ${(0.06 + delay * 0.05).toFixed(2)}s cubic-bezier(.2,.8,.25,1) both`,
      }}
    >
      <div className="flex items-center" style={{ gap: '11px' }}>
        <div className="text-[16px] font-semibold tracking-[-.01em] text-white">{name}</div>
        <div className="flex" style={{ marginLeft: 'auto', gap: '8px' }}>
          <div
            className={`${CHIP} text-[13.5px] font-semibold tracking-[-.01em] hover:brightness-[1.08]`}
            style={{
              height: '34px',
              padding: '0 16px',
              borderRadius: '17px',
              background: 'var(--color-accent)',
              color: 'var(--color-accent-ink)',
            }}
          >
            실행하기
          </div>
          <div
            onClick={handleDelete}
            className={`${CHIP} text-[13.5px] font-semibold tracking-[-.01em] hover:bg-[rgba(233,174,196,.24)] hover:text-[#ffd7e6]`}
            style={{
              height: '34px',
              padding: '0 14px',
              borderRadius: '17px',
              background: 'rgba(255,255,255,.09)',
              color: 'rgba(255,240,247,.72)',
            }}
          >
            삭제
          </div>
        </div>
      </div>

      <div
        className="text-[14px] tracking-[-.01em]"
        style={{ marginTop: '9px', color: 'rgba(255,236,246,.62)' }}
      >
        {description ?? '설명이 없습니다'}
      </div>

      <div
        className="flex text-[13px]"
        style={{
          marginTop: '14px',
          gap: '20px',
          color: 'rgba(255,240,247,.55)',
          fontVariantNumeric: 'tabular-nums',
        }}
      >
        <div>마지막 실행 {timeLabel(lastExecutedAt)}</div>
      </div>
    </a>
  )
}
