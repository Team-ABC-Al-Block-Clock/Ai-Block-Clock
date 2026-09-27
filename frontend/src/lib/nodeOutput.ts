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
  try {
    return outputExampleProblem(JSON.parse(draft.text))
  } catch {
    return EXAMPLE_INVALID
  }
}

/** 서버가 받는 값으로. 예시가 틀렸으면 `null`. */
export function readOutputDraft(draft: OutputDraft): NodeOutput | null {
  if (draft.kind === 'TEXT') return { kind: 'TEXT', example: null }
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
