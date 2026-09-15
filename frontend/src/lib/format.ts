import type { ItemNodeType } from '@/api/workflows'

/** 2400 → '2.4k', 640 → '640' */
export function compactCount(n: number) {
  if (n < 1000) return String(n)
  return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k'
}

/** 1248 → '1,248회' */
export function runLabel(n: number) {
  return n.toLocaleString() + '회'
}

/** 문자열 인자를 10진수 그대로 파싱한다 — Number 로 되돌리지 않아 소수점 오차가 없다. */
const AMOUNT = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 2 })

/**
 * '540.00' → '540 ABC', '1248.50' → '1,248.5 ABC'
 *
 * 단언은 여기 한 곳에서만 한다 — 값이 숫자가 아니면 RangeError 로 터지는 편이
 * 'abc ABC' 를 조용히 그리는 것보다 낫다.
 */
export function priceLabel(price: string | number) {
  return `${AMOUNT.format(price as Intl.StringNumericLiteral)} ABC`
}

/** 4.7 · 35 → '4.7 (35)', 리뷰 0건 → '—' */
export function ratingLabel(averageRating: number | null, reviewCount: number) {
  if (averageRating === null || reviewCount === 0) return '—'
  return `${averageRating.toFixed(1)} (${compactCount(reviewCount)})`
}

/**
 * 0.982 → '98.2%', 실행 0회 → '—'
 *
 * 실행이 없으면 `successRate` 가 0 으로 와서 '0%' 는 거짓말이 된다.
 * 반올림이 아니라 내림이다 — 9,999/10,000 을 '100%' 로 부풀리지 않는다.
 */
export function successLabel(successRate: number, executionCount: number) {
  if (executionCount === 0) return '—'
  return (Math.floor(successRate * 1000) / 10).toFixed(1).replace(/\.0$/, '') + '%'
}

/** '0x1f9c4b2e…5b8072a' → '0x1f9c…072a' — 지갑 주소는 전체가 화면에 들어가지 않는다 */
export function shortAddress(address: string) {
  if (address.length <= 13) return address
  return `${address.slice(0, 6)}…${address.slice(-4)}`
}

/**
 * '2026-09-07T14:22:00Z' → '09.07 14:22', null → '—'
 *
 * 표준시를 서울로 고정한다 — 안 그러면 테스트가 CI 의 TZ 설정에 따라 갈린다.
 * 상대 시각('3일 전')은 만들지 않는다. 목록을 열어 둔 채 자정을 넘기면 값이 어긋난다.
 */
const DATE_TIME = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  // h23 이 아니면 자정이 '24:00' 으로 나오는 엔진이 있다.
  hourCycle: 'h23',
})

export function timeLabel(iso: string | null) {
  if (!iso) return '—'
  const at = Date.parse(iso)
  if (Number.isNaN(at)) return '—'
  // 구분자를 로케일에 맡기지 않는다 — ko-KR 이 '9. 7. 14:22' 같은 모양을 줄 수 있다.
  const part = Object.fromEntries(DATE_TIME.formatToParts(at).map((p) => [p.type, p.value]))
  return `${part.month}.${part.day} ${part.hour}:${part.minute}`
}

/**
 * 받침이 있으면 '을', 없으면 '를'. 한글이 아닌 끝 글자는 '를' 로 둔다 —
 * 발음까지 따지려면 표를 들여야 한다.
 */
export function josa(name: string) {
  const code = name.trim().slice(-1).charCodeAt(0)
  const isHangul = code >= 0xac00 && code <= 0xd7a3
  return isHangul && (code - 0xac00) % 28 !== 0 ? '을' : '를'
}

/** `Record<ItemNodeType, string>` 이라 유형이 늘면 채울 때까지 컴파일이 실패한다. */
const NODE_TYPE_LABELS: Record<ItemNodeType, string> = {
  TRIGGER: '트리거',
  LLM: 'LLM',
  TOOL: '도구',
  AGENT: '에이전트',
}

export const nodeTypeLabel = (type: ItemNodeType) => NODE_TYPE_LABELS[type]
