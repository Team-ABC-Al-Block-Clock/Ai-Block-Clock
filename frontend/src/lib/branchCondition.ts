import type { ConditionLiteral, ConditionOperator } from '@/api/workflows'
import type { CanvasCondition, CanvasNodeData } from '@/lib/canvasWorkflow'
import { fieldKind, type FieldKind } from '@/lib/nodeOutput'

/** 고를 수 있는 비교. 서버 `ConditionOperator` 를 그대로 따른다. */
export const CONDITION_OPERATORS: { value: ConditionOperator; label: string }[] = [
  { value: 'EQ', label: '같다' },
  { value: 'NE', label: '다르다' },
  { value: 'GT', label: '크다' },
  { value: 'GE', label: '크거나 같다' },
  { value: 'LT', label: '작다' },
  { value: 'LE', label: '작거나 같다' },
  { value: 'IN', label: '목록에 있다' },
]

/** 칸의 종류마다 쓸 수 있는 비교. 엔진 `BRANCH_OPS_BY_KIND` 와 같다. */
const OPERATORS_BY_KIND: Record<FieldKind, ConditionOperator[]> = {
  string: ['EQ', 'NE', 'IN'],
  number: ['EQ', 'NE', 'GT', 'GE', 'LT', 'LE', 'IN'],
  boolean: ['EQ', 'NE'],
}

/** 고를 수 있는 비교. 칸의 종류를 모르면 전부. */
export const operatorsFor = (kind?: FieldKind | null) =>
  kind
    ? CONDITION_OPERATORS.filter((option) => OPERATORS_BY_KIND[kind].includes(option.value))
    : CONDITION_OPERATORS

const OPERATOR_SIGNS: Record<ConditionOperator, string> = {
  EQ: '=',
  NE: '≠',
  GT: '>',
  GE: '≥',
  LT: '<',
  LE: '≤',
  IN: '∈',
}

/**
 * 적은 글자를 서버가 받는 값으로.
 *
 * 서버는 문자열·숫자·불리언만 받는다(`BranchConditionRequest.isLiteral`). 따옴표를 두르지
 * 않으므로 `12` 는 숫자, `true` 는 불리언으로 읽는다 — 글자 그대로 두고 싶은 값은 따옴표로
 * 감싼다. 그 둘을 가를 길이 없으면 `"true"` 인 등급 이름을 불리언으로 보내게 된다.
 */
export function parseLiteral(text: string): ConditionLiteral {
  const value = text.trim()
  if (value.startsWith('"')) return quotedString(value) ?? value
  if (value === 'true') return true
  if (value === 'false') return false
  // `Number('')` 은 0 이다. 빈 칸이 0 으로 새지 않게 먼저 거른다.
  if (value !== '' && Number.isFinite(Number(value))) return Number(value)
  return value
}

function quotedString(value: string): string | null {
  try {
    const parsed: unknown = JSON.parse(value)
    return typeof parsed === 'string' ? parsed : null
  } catch {
    return null
  }
}

/** 앞의 공백과 이스케이프를 보존하고, 따옴표 밖의 쉼표만 구분자로 쓴다. */
function splitList(text: string): string[] | null {
  const items: string[] = []
  let start = 0
  let quoted = false
  let escaped = false
  for (let i = 0; i < text.length; i++) {
    const char = text[i]
    if (escaped) escaped = false
    else if (quoted && char === '\\') escaped = true
    else if (char === '"') quoted = !quoted
    else if (char === ',' && !quoted) {
      items.push(text.slice(start, i).trim())
      start = i + 1
    }
  }
  if (quoted || escaped) return null
  items.push(text.slice(start).trim())
  return items.some((item) => item === '') ? null : items
}

/** 칸의 종류를 알면 그 종류로만 읽는다. 글자 칸에 적은 `12` 가 숫자로 새면 엔진이 같다고 보지 않는다. */
function parseAs(text: string, kind?: FieldKind | null): ConditionLiteral | null {
  const value = text.trim()
  if (value.startsWith('"')) return !kind || kind === 'string' ? quotedString(value) : null
  if (kind === 'string') return value
  if (kind === 'number')
    return value !== '' && Number.isFinite(Number(value)) ? Number(value) : null
  if (kind === 'boolean') return value === 'true' ? true : value === 'false' ? false : null
  return parseLiteral(value)
}

/** `IN` 이면 쉼표로 나눠 배열, 아니면 값 하나. 빈 칸이나 종류에 맞지 않는 값이 있으면 `null`. */
export function parseConditionRight(
  text: string,
  operator: ConditionOperator,
  kind?: FieldKind | null,
): ConditionLiteral | ConditionLiteral[] | null {
  if (operator !== 'IN') return text.trim() === '' ? null : parseAs(text, kind)

  const items = splitList(text)
  if (!items) return null
  const values = items.map((item) => parseAs(item, kind))
  return values.length === 0 || values.includes(null) ? null : (values as ConditionLiteral[])
}

/**
 * 입력란에 되돌려 놓을 글자. 저장된 값을 다시 열었을 때 쓴다.
 *
 * `parseLiteral` 이 다시 읽어 같은 값이 나와야 한다 — 그대로 적으면 등급 이름 `"true"` 가
 * 다음 「적용」에서 불리언 `true` 로 씻겨 나간다. 되읽어 달라지는 글자만 따옴표로 감싼다.
 */
const reread = (value: ConditionLiteral): string =>
  // 쉼표가 들었으면 `IN` 이 되읽을 때 두 값으로 갈린다. 그것도 감싼다.
  typeof value === 'string' &&
  (value === '' || parseLiteral(value) !== value || /[,"\\\r\n]/.test(value))
    ? JSON.stringify(value)
    : String(value)

export function formatConditionRight(right: CanvasCondition['right']): string {
  return Array.isArray(right) ? right.map(reread).join(', ') : reread(right)
}

/** 카드 밑에 한 줄로 요약한다. 읽는 노드의 이름은 밖에서 찾아 넘긴다. */
export function describeCondition(
  condition: CanvasCondition | null | undefined,
  nameOf: (id: string) => string | undefined,
): string {
  if (!condition) return '조건 없음'
  const from = nameOf(condition.fromId) ?? '알 수 없는 노드'
  const sign = OPERATOR_SIGNS[condition.operator]
  return `${from}.${condition.path} ${sign} ${formatConditionRight(condition.right)}`
}

/**
 * 엔진이 실행 전에 물릴 조건인지. 문제가 없거나 알 수 없으면 `null`.
 *
 * 에이전트는 출력 형식을 알려 주지 않아 보지 않는다 — 엔진이 대신 본다.
 */
export function conditionProblem(
  source: CanvasNodeData | undefined,
  condition: CanvasCondition,
): string | null {
  const output = source?.sourceType === 'NODE' ? source.output : null
  if (!output) return null
  if (output.kind === 'TEXT' || !output.example)
    return '분기 조건이 읽는 노드의 출력 형식을 JSON 으로 바꿔 주세요'
  const kind = fieldKind(output.example, condition.path)
  if (!kind) return '분기 조건의 값 경로가 출력 예시에 없습니다'
  if (!OPERATORS_BY_KIND[kind].includes(condition.operator))
    return '분기 조건의 비교 방법이 값의 종류와 맞지 않습니다'
  return null
}
