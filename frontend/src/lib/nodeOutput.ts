import type { NodeOutput, OutputKind } from '@/api/nodes'

/** 출력 폼의 한 줄. 값은 적는 동안 숫자가 아닐 수 있어 글자로 둔다. */
export interface FieldDraft {
  id: number
  name: string
  kind: FieldKind
  value: string
}

/** 폼이 들고 있는 출력 형식. `form` 이면 `fields` 를, 아니면 `text` 를 읽는다. */
export interface OutputDraft {
  kind: OutputKind
  form: boolean
  fields: FieldDraft[]
  text: string
}

/** 엔진 `MAX_KEYS` 와 같다. */
export const FIELDS_MAX = 60

let fieldSeq = 0
export const blankField = (): FieldDraft => ({
  id: ++fieldSeq,
  name: '',
  kind: 'string',
  value: '',
})

/** 한 단계짜리 글자·숫자·참거짓 객체만 폼으로 읽는다. null 은 되읽으면 "" 가 돼 뺀다. */
export function fieldsFrom(example: unknown): FieldDraft[] | null {
  if (typeof example !== 'object' || example === null || Array.isArray(example)) return null
  const fields: FieldDraft[] = []
  for (const [name, value] of Object.entries(example)) {
    if (!name || name !== name.trim()) return null
    if (typeof value === 'string') fields.push({ ...blankField(), name, value })
    else if (typeof value === 'number')
      fields.push({ ...blankField(), name, kind: 'number', value: String(value) })
    else if (typeof value === 'boolean')
      fields.push({ ...blankField(), name, kind: 'boolean', value: String(value) })
    else return null
  }
  return fields.length ? fields : [blankField()]
}

export function toOutputDraft(output?: NodeOutput | null): OutputDraft {
  const example = output?.example
  const fields = example ? fieldsFrom(example) : null
  return {
    kind: output?.kind ?? 'TEXT',
    form: !example || !!fields,
    fields: fields ?? [blankField()],
    text: example ? JSON.stringify(example, null, 2) : '',
  }
}

const numberOf = (value: string) => (value.trim() ? Number(value) : 0)
const isBlank = (field: FieldDraft) => !field.name.trim() && !field.value.trim()

export interface FieldIssues {
  names: Set<number>
  values: Set<number>
  message: string | null
}

/** 폼에서 막을 줄. 이름과 값이 다 빈 줄은 없는 것으로 본다. */
export function fieldIssues(fields: FieldDraft[]): FieldIssues {
  const names = new Set<number>()
  const values = new Set<number>()
  let message: string | null = null
  const note = (text: string) => (message ??= text)
  const byName = new Map<string, FieldDraft[]>()
  for (const field of fields) {
    if (isBlank(field)) continue
    const name = field.name.trim()
    if (!name) {
      names.add(field.id)
      note('필드 이름을 적어 주세요')
    } else byName.set(name, [...(byName.get(name) ?? []), field])
    if (field.kind === 'number' && !Number.isFinite(numberOf(field.value))) {
      values.add(field.id)
      note(`${name || '숫자 필드'} 의 예시 값은 숫자로 적어 주세요`)
    }
  }
  for (const [name, same] of byName) {
    if (same.length < 2) continue
    same.forEach((field) => names.add(field.id))
    note(`필드 이름 ${name} 이 두 번 있습니다`)
  }
  if (!byName.size && !names.size) note('출력 필드를 하나 이상 적어 주세요')
  return { names, values, message }
}

/** 폼을 JSON 예시로. 값을 비우면 "" · 0 · true 로 채운다. `__proto__` 도 키로 남게 fromEntries 로 만든다. */
export function fieldsExample(fields: FieldDraft[]): Record<string, unknown> {
  return Object.fromEntries(
    fields
      .filter((field) => field.name.trim())
      .map((field) => {
        const name = field.name.trim()
        if (field.kind === 'number') {
          const n = numberOf(field.value)
          return [name, Number.isFinite(n) ? n : 0]
        }
        if (field.kind === 'boolean') return [name, field.value !== 'false']
        return [name, field.value]
      }),
  )
}

export type ModeSwitch = { next: OutputDraft; reason?: never } | { next?: never; reason: string }

/** 폼과 직접 입력을 오간다. 옮기다 값을 잃거나 폼으로 읽히지 않으면 그 이유를 돌려준다. */
export function switchOutputMode(draft: OutputDraft): ModeSwitch {
  if (draft.form) {
    const issues = fieldIssues(draft.fields)
    if (issues.names.size || issues.values.size) return { reason: issues.message ?? '' }
    return {
      next: { ...draft, form: false, text: JSON.stringify(fieldsExample(draft.fields), null, 2) },
    }
  }
  let parsed: unknown
  try {
    parsed = draft.text.trim() ? JSON.parse(draft.text) : {}
  } catch {
    return { reason: 'JSON 을 고친 뒤 폼으로 돌아갈 수 있습니다' }
  }
  const fields = fieldsFrom(parsed)
  if (!fields) return { reason: '목록이나 하위 객체가 있어 폼으로 열 수 없습니다' }
  return { next: { ...draft, form: true, fields } }
}

export const EXAMPLE_INVALID = '출력 예시를 빈 객체({})가 없는 JSON 객체로 적어 주세요'

/** 엔진은 하위 객체와 배열 안의 객체에도 적어도 한 개의 필드를 요구한다. */
function hasShape(value: unknown): boolean {
  if (Array.isArray(value)) return value.every(hasShape)
  if (value === null || typeof value !== 'object') return true
  const values = Object.values(value)
  return values.length > 0 && values.every(hasShape)
}

/** 엔진 `shape.MAX_DEPTH` · `MAX_KEYS`. 넘으면 실행 때 `OUTPUT_EXAMPLE_INVALID` 로 물린다. */
const MAX_DEPTH = 5
const MAX_KEYS = 60

/**
 * 엔진이 물릴 예시인지. 문제가 없으면 `null`.
 *
 * 깊이와 키 수는 엔진 `check_example` 처럼 배열의 첫 원소만 따라 센다 — 모든 원소를 세면
 * 엔진은 받는 긴 목록 예시를 여기서 막게 된다.
 */
export function outputExampleProblem(example: unknown): string | null {
  if (typeof example !== 'object' || example === null || Array.isArray(example))
    return EXAMPLE_INVALID
  if (!hasShape(example)) return '출력 예시에 빈 객체({})가 있습니다'
  let keys = 0
  const walk = (value: unknown, depth: number): string | null => {
    if (depth > MAX_DEPTH) return `출력 예시의 중첩은 ${MAX_DEPTH}단계까지입니다`
    if (Array.isArray(value)) return value.length ? walk(value[0], depth + 1) : null
    if (value === null || typeof value !== 'object') return null
    for (const sub of Object.values(value)) {
      keys += 1
      if (keys > MAX_KEYS) return `출력 예시의 키는 ${MAX_KEYS}개까지입니다`
      const found = walk(sub, depth + 1)
      if (found) return found
    }
    return null
  }
  return walk(example, 1)
}

export const validOutputExample = (example: unknown): example is Record<string, unknown> =>
  outputExampleProblem(example) === null

/** 폼에 적힌 예시가 왜 안 되는지. 글 출력이거나 문제가 없으면 `null`. */
export function outputDraftProblem(draft: OutputDraft): string | null {
  if (draft.kind === 'TEXT') return null
  if (draft.form)
    return fieldIssues(draft.fields).message ?? outputExampleProblem(fieldsExample(draft.fields))
  try {
    return outputExampleProblem(JSON.parse(draft.text))
  } catch {
    return EXAMPLE_INVALID
  }
}

/** 서버가 받는 값으로. 예시가 틀렸으면 `null`. */
export function readOutputDraft(draft: OutputDraft): NodeOutput | null {
  if (draft.kind === 'TEXT') return { kind: 'TEXT', example: null }
  if (draft.form) {
    if (fieldIssues(draft.fields).message) return null
    const example = fieldsExample(draft.fields)
    return validOutputExample(example) ? { kind: 'JSON', example } : null
  }
  try {
    const example: unknown = JSON.parse(draft.text)
    return validOutputExample(example) ? { kind: 'JSON', example } : null
  } catch {
    return null
  }
}

/** 같은 값인지. 예시의 들여쓰기만 바뀐 것은 고친 것으로 보지 않는다. */
export const sameOutput = (a: NodeOutput | null, b: NodeOutput | null) =>
  JSON.stringify(a) === JSON.stringify(b)

/** 분기 조건이 견줄 수 있는 칸의 종류. 배열·객체는 견줄 수 없다. */
export type FieldKind = 'string' | 'number' | 'boolean'

export interface OutputField {
  /** 점으로 이은 경로. 예: `detail.level` */
  path: string
  kind: FieldKind
}

type ValueKind = FieldKind | 'array' | 'object'

/** 엔진 `shape.kind_of` 와 같다. null 은 선택 항목인 글자로 본다. */
function kindOf(value: unknown): ValueKind {
  if (value === null || typeof value === 'string') return 'string'
  if (typeof value === 'boolean') return 'boolean'
  if (typeof value === 'number') return 'number'
  if (Array.isArray(value)) return 'array'
  return 'object'
}

const isScalar = (kind: ValueKind | null): kind is FieldKind =>
  kind === 'string' || kind === 'number' || kind === 'boolean'

/** 예시에서 견줄 수 있는 칸 전부. 배열 안은 번호를 골라야 해서 싣지 않는다. */
export function scalarFields(example: Record<string, unknown>): OutputField[] {
  const fields: OutputField[] = []
  const walk = (value: Record<string, unknown>, prefix: string) => {
    for (const [key, sub] of Object.entries(value)) {
      const path = prefix ? `${prefix}.${key}` : key
      const kind = kindOf(sub)
      if (kind === 'object') walk(sub as Record<string, unknown>, path)
      else if (isScalar(kind)) fields.push({ path, kind })
    }
  }
  walk(example, '')
  return fields
}

/** 경로가 가리키는 칸의 종류. 엔진 `shape.field_kind` 처럼 배열 번호도 따라간다. 없으면 null. */
export function fieldKind(example: Record<string, unknown>, path: string): FieldKind | null {
  let at: unknown = example
  for (const key of path.split('.').filter(Boolean)) {
    if (Array.isArray(at) && /^\d+$/.test(key)) {
      if (at.length === 0) return null
      at = at[0]
    } else if (at && typeof at === 'object' && !Array.isArray(at) && key in at) {
      at = (at as Record<string, unknown>)[key]
    } else {
      return null
    }
  }
  const kind = kindOf(at)
  return isScalar(kind) ? kind : null
}
