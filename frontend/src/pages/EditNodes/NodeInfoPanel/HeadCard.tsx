import { rowStyle } from './layout'

/** 패널 맨 위의 머리 카드. 고른 것이 무엇인지 한 줄로 세운다 — 종류를 가리지 않는다. */
export default function HeadCard({
  initial,
  name,
  typeLabel,
}: {
  initial: string
  name: string
  typeLabel: string
}) {
  return (
    <div
      className="absolute box-border flex items-center"
      style={{
        ...rowStyle('22px'),
        height: '104px',
        borderRadius: '12px',
        background: 'rgba(255,255,255,.11)',
        padding: '0 16px',
        gap: '16px',
      }}
    >
      <div
        className="relative flex-none overflow-hidden bg-[rgba(255,255,255,.10)]"
        style={{ width: '48px', height: '48px', borderRadius: '10px' }}
      >
        <div
          className="absolute inset-0 flex items-center justify-center text-[17px] font-semibold"
          style={{ color: 'rgba(255,226,238,.65)' }}
        >
          {initial}
        </div>
      </div>
      <div className="flex min-w-0 flex-col" style={{ gap: '4px' }}>
        <div className="truncate text-[18px] font-semibold tracking-[-.005em] text-white">
          {name}
        </div>
        <div className="text-[14.5px]" style={{ color: 'rgba(255,224,238,.88)' }}>
          {typeLabel} 노드
        </div>
      </div>
    </div>
  )
}
