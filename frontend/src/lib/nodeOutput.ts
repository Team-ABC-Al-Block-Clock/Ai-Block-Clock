import type { NodeOutput, OutputKind } from '@/api/nodes'

/** 폼이 들고 있는 출력 형식. 예시는 고치는 동안 JSON 이 아닐 수 있어 글자로 둔다. */
export interface OutputDraft {
  kind: OutputKind
  text: string
}

export const toOutputDraft = (output?: NodeOutput | null): OutputDraft => ({
  kind: output?.kind ?? 'TEXT',
  text: output?.example ? JSON.stringify(output.example, null, 2) : '',
})

export const EXAMPLE_INVALID = '출력 예시를 키가 하나 이상인 JSON 객체로 적어 주세요'

/** 서버가 받는 값으로. 예시가 틀렸으면 `null`. */
export function readOutputDraft(draft: OutputDraft): NodeOutput | null {
  if (draft.kind === 'TEXT') return { kind: 'TEXT', example: null }
  try {
    const example: unknown = JSON.parse(draft.text)
    const isObject = typeof example === 'object' && example !== null && !Array.isArray(example)
    return isObject && Object.keys(example).length > 0
      ? { kind: 'JSON', example: example as Record<string, unknown> }
      : null
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
