import type { CSSProperties } from 'react'

import type { OutputKind } from '@/api/nodes'

const KINDS: { value: OutputKind; label: string; hint: string }[] = [
  { value: 'TEXT', label: '글', hint: '자유 텍스트로 냅니다' },
  {
    value: 'JSON',
    label: 'JSON',
    hint: '예시와 같은 모양으로 냅니다. 분기 조건이 이 칸을 읽습니다',
  },
]

/** 출력 형식 고르기. 둘뿐이라 목록 대신 한 줄에 나란히 둔다. */
export function OutputKindToggle({
  value,
  onChange,
  style,
}: {
  value: OutputKind
  onChange: (next: OutputKind) => void
  style?: CSSProperties
}) {
  return (
    <div
      role="radiogroup"
      aria-label="출력 형식"
      className="bg-surface-card flex"
      style={{ borderRadius: '11px', padding: '4px', gap: '4px', ...style }}
    >
      {KINDS.map((kind) => (
        <button
          key={kind.value}
          type="button"
          role="radio"
          aria-checked={value === kind.value}
          title={kind.hint}
          onClick={() => onChange(kind.value)}
          className="flex-1 cursor-pointer border-0 font-[inherit] font-semibold"
          style={{
            borderRadius: '8px',
            background: value === kind.value ? 'rgba(255,255,255,.2)' : 'transparent',
            color: value === kind.value ? '#fff' : 'rgba(255,255,255,.6)',
          }}
        >
          {kind.label}
        </button>
      ))}
    </div>
  )
}

/** JSON 출력의 예시. 틀린 동안은 테두리로만 알린다 — 문구는 부르는 쪽이 둔다. */
export function OutputExampleBox({
  value,
  onChange,
  invalid,
  height,
  fontSize,
  style,
}: {
  value: string
  onChange: (next: string) => void
  invalid: boolean
  height: number
  fontSize: string
  style?: CSSProperties
}) {
  return (
    <div
      className="bg-surface-card"
      style={{
        height: `${height}px`,
        borderRadius: '11px',
        padding: '8px 12px',
        boxShadow: invalid ? 'inset 0 0 0 1px rgba(255,150,170,.8)' : undefined,
        ...style,
      }}
    >
      <textarea
        className="scroll-thin w-full resize-none border-0 bg-transparent font-mono text-white outline-0"
        value={value}
        onChange={(ev) => onChange(ev.target.value)}
        placeholder={'{ "competition_level": "high", "score": 0 }'}
        aria-label="출력 예시"
        spellCheck={false}
        style={{ height: `${height - 16}px`, fontSize, lineHeight: 1.45 }}
      />
    </div>
  )
}
