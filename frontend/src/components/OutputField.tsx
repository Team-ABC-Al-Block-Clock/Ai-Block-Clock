import { useEffect, useRef, type CSSProperties } from 'react'

import type { OutputKind } from '@/api/nodes'
import {
  blankField,
  fieldIssues,
  FIELDS_MAX,
  switchOutputMode,
  type FieldDraft,
  type FieldKind,
  type OutputDraft,
} from '@/lib/nodeOutput'

const KINDS: { value: OutputKind; label: string; hint: string }[] = [
  { value: 'TEXT', label: '글', hint: '자유 텍스트로 냅니다' },
  {
    value: 'JSON',
    label: 'JSON',
    hint: '적은 필드 모양으로 냅니다. 분기 조건이 이 필드를 읽습니다',
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

const FIELD_KINDS: { value: FieldKind; label: string }[] = [
  { value: 'string', label: '글자' },
  { value: 'number', label: '숫자' },
  { value: 'boolean', label: '참/거짓' },
]

const COLUMNS = 'minmax(0,1.2fr) 88px minmax(0,1fr) 28px'

const BAD_RING = 'inset 0 0 0 1px rgba(255,150,170,.8)'

/** 폼과 JSON 직접 입력을 오가는 링크. 옮길 수 없으면 꺼 두고 이유를 띄운다. */
export function OutputModeLink({
  draft,
  onChange,
  style,
}: {
  draft: OutputDraft
  onChange: (next: OutputDraft) => void
  style?: CSSProperties
}) {
  const { next, reason } = switchOutputMode(draft)
  return (
    <button
      type="button"
      disabled={!next}
      onClick={() => next && onChange(next)}
      title={reason}
      className="cursor-pointer border-0 bg-transparent p-0 font-[inherit] underline underline-offset-[3px] disabled:cursor-default disabled:opacity-45"
      style={{ color: 'var(--color-accent-bright)', ...style }}
    >
      {draft.form ? 'JSON 으로 직접 적기' : '폼으로 적기'}
    </button>
  )
}

/** JSON 출력의 필드를 한 줄씩. 줄 수만큼 늘어난다. */
export function OutputFieldsBox({
  fields,
  onChange,
  style,
}: {
  fields: FieldDraft[]
  onChange: (next: FieldDraft[]) => void
  style?: CSSProperties
}) {
  const focusId = useRef<number | null>(null)
  const rowsRef = useRef<HTMLDivElement>(null)
  const issues = fieldIssues(fields)

  useEffect(() => {
    if (focusId.current === null) return
    const input = rowsRef.current?.querySelector<HTMLInputElement>(
      `[data-field="${focusId.current}"]`,
    )
    input?.focus()
    input?.scrollIntoView({ block: 'nearest' })
    focusId.current = null
  }, [fields])

  const patch = (id: number, next: Partial<FieldDraft>) =>
    onChange(fields.map((field) => (field.id === id ? { ...field, ...next } : field)))

  const changeKind = (field: FieldDraft, kind: FieldKind) =>
    patch(field.id, {
      kind,
      value: kind === 'boolean' ? 'true' : field.kind === 'boolean' ? '' : field.value,
    })

  const add = () => {
    const field = blankField()
    onChange([...fields, field])
    focusId.current = field.id
  }

  const cell: CSSProperties = {
    height: '36px',
    minWidth: 0,
    borderRadius: '8px',
    fontSize: '15px',
  }

  return (
    <div
      ref={rowsRef}
      className="flex flex-col"
      style={{
        background: 'rgba(255,255,255,.05)',
        borderRadius: '11px',
        padding: '10px',
        gap: '6px',
        ...style,
      }}
    >
      <div
        className="grid text-[13px]"
        style={{ gridTemplateColumns: COLUMNS, gap: '6px', color: 'rgba(255,236,246,.48)' }}
      >
        <span>이름</span>
        <span>타입</span>
        <span>예시 값 (선택)</span>
      </div>
      {fields.map((field) => (
        <div
          key={field.id}
          className="grid flex-none items-center"
          style={{ gridTemplateColumns: COLUMNS, gap: '6px' }}
        >
          <input
            data-field={field.id}
            className="bg-surface-card border-0 font-mono text-white outline-0"
            value={field.name}
            onChange={(ev) => patch(field.id, { name: ev.target.value })}
            placeholder="예: score"
            aria-label="필드 이름"
            spellCheck={false}
            style={{
              ...cell,
              padding: '0 9px',
              boxShadow: issues.names.has(field.id) ? BAD_RING : undefined,
            }}
          />
          <select
            className="bg-surface-card cursor-pointer border-0 font-[inherit] text-white outline-0"
            value={field.kind}
            onChange={(ev) => changeKind(field, ev.target.value as FieldKind)}
            aria-label="필드 타입"
            style={{ ...cell, padding: '0 4px 0 7px' }}
          >
            {FIELD_KINDS.map((kind) => (
              <option key={kind.value} value={kind.value} style={{ color: '#1d1430' }}>
                {kind.label}
              </option>
            ))}
          </select>
          {field.kind === 'boolean' ? (
            <select
              className="bg-surface-card cursor-pointer border-0 font-[inherit] text-white outline-0"
              value={field.value}
              onChange={(ev) => patch(field.id, { value: ev.target.value })}
              aria-label="예시 값"
              style={{ ...cell, padding: '0 4px 0 7px' }}
            >
              <option value="true" style={{ color: '#1d1430' }}>
                true
              </option>
              <option value="false" style={{ color: '#1d1430' }}>
                false
              </option>
            </select>
          ) : (
            <input
              className="bg-surface-card border-0 font-[inherit] text-white outline-0"
              value={field.value}
              onChange={(ev) => patch(field.id, { value: ev.target.value })}
              placeholder={field.kind === 'number' ? '예: 72' : '예: high'}
              aria-label="예시 값"
              inputMode={field.kind === 'number' ? 'decimal' : undefined}
              style={{
                ...cell,
                padding: '0 9px',
                boxShadow: issues.values.has(field.id) ? BAD_RING : undefined,
              }}
            />
          )}
          <button
            type="button"
            disabled={fields.length === 1}
            onClick={() => onChange(fields.filter((it) => it.id !== field.id))}
            aria-label="필드 삭제"
            className="flex cursor-pointer items-center justify-center border-0 bg-transparent font-[inherit] hover:bg-[rgba(255,255,255,.12)] hover:text-white disabled:cursor-default disabled:opacity-25 disabled:hover:bg-transparent"
            style={{
              height: '28px',
              borderRadius: '7px',
              fontSize: '17px',
              color: 'rgba(255,236,246,.55)',
            }}
          >
            ×
          </button>
        </div>
      ))}
      <button
        type="button"
        onClick={add}
        disabled={fields.length >= FIELDS_MAX}
        title={fields.length >= FIELDS_MAX ? `필드는 ${FIELDS_MAX}개까지입니다` : undefined}
        className="flex flex-none cursor-pointer items-center justify-center border-0 bg-transparent font-[inherit] font-semibold hover:bg-[rgba(233,183,210,.08)] disabled:cursor-default disabled:opacity-45"
        style={{
          height: '34px',
          borderRadius: '8px',
          border: '1px dashed rgba(233,183,210,.45)',
          color: 'var(--color-accent-bright)',
          fontSize: '14.5px',
        }}
      >
        ＋ 필드 추가
      </button>
      {(issues.names.size > 0 || issues.values.size > 0) && (
        <div className="text-[13px]" style={{ color: 'rgba(255,150,170,.9)' }}>
          {issues.message}
        </div>
      )}
    </div>
  )
}
