import type { ConditionLiteral, ConditionOperator } from '@/api/workflows'
import type { CanvasCondition } from '@/lib/canvasWorkflow'

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
  const quoted = value.length >= 2 && value.startsWith('"') && value.endsWith('"')
  if (quoted) return value.slice(1, -1)
  if (value === 'true') return true
  if (value === 'false') return false
  // `Number('')` 은 0 이다. 빈 칸이 0 으로 새지 않게 먼저 거른다.
  if (value !== '' && !Number.isNaN(Number(value))) return Number(value)
  return value
}

/** 따옴표 안의 쉼표는 자르지 않는다 — `"a,b"` 는 한 값이다. */
const IN_ITEM = /"[^"]*"|[^,]+/g

/** `IN` 이면 쉼표로 나눠 배열, 아니면 값 하나. 빈 칸만 남으면 `null`. */
export function parseConditionRight(
  text: string,
  operator: ConditionOperator,
): ConditionLiteral | ConditionLiteral[] | null {
  if (operator !== 'IN') return text.trim() === '' ? null : parseLiteral(text)

  const items = (text.match(IN_ITEM) ?? []).map((part) => part.trim()).filter((part) => part !== '')
  return items.length === 0 ? null : items.map(parseLiteral)
}

/**
 * 입력란에 되돌려 놓을 글자. 저장된 값을 다시 열었을 때 쓴다.
 *
 * `parseLiteral` 이 다시 읽어 같은 값이 나와야 한다 — 그대로 적으면 등급 이름 `"true"` 가
 * 다음 「적용」에서 불리언 `true` 로 씻겨 나간다. 되읽어 달라지는 글자만 따옴표로 감싼다.
 */
const reread = (value: ConditionLiteral): string =>
  // 쉼표가 들었으면 `IN` 이 되읽을 때 두 값으로 갈린다. 그것도 감싼다.
  typeof value === 'string' && (parseLiteral(value) !== value || value.includes(','))
    ? `"${value}"`
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
