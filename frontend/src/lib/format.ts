import type { ControlKind, ControlType, IoType, ItemNodeType } from '@/api/workflows'

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
 * '540.00' → '540', '1248.50' → '1,248.5'. 단위를 따로 적는 자리에 쓴다.
 *
 * TS 가 문자열 오버로드를 `StringNumericLiteral` 로 타이핑해 API 의 `string` 은 단언이
 * 필요하다. 명세가 10진수 문자열로 확정했으니 여기 한 곳에서만 한다 — 값이 숫자가
 * 아니면 RangeError 로 터지는 편이 'abc' 를 조용히 그리는 것보다 낫다.
 */
export function amountLabel(amount: string | number) {
  return AMOUNT.format(amount as Intl.StringNumericLiteral)
}

/** '540.00' → '540 ABC', '1248.50' → '1,248.5 ABC' */
export function priceLabel(price: string | number) {
  return `${amountLabel(price)} ABC`
}

/** 충전이 보내는 소수 6자리를 그대로 적는다. `priceLabel` 은 2자리에서 끊어 보낸 값과 달라진다. */
const CHARGE_AMOUNT = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 6 })

/** '1.123456' → '1.123456 ABC', '2500' → '2,500 ABC' */
export function chargeAmountLabel(amount: string | number) {
  return `${CHARGE_AMOUNT.format(amount as Intl.StringNumericLiteral)} ABC`
}

/** 204800 → '200KB', 1363148 → '1.3MB' — 1KB 미만은 올려서 '1KB' 로 적는다. */
export function fileSizeLabel(bytes: number) {
  if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1).replace(/\.0$/, '')}MB`
  return `${Math.max(1, Math.round(bytes / 1024))}KB`
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

/** 15230491 → '#15,230,491', 아직 블록에 안 들어간 트랜잭션(null) → '—' */
export function blockLabel(blockNumber: number | null) {
  if (blockNumber === null) return '—'
  // 로케일을 기계에 맡기면 구분자가 갈려 CI 와 화면이 서로 달라진다.
  return `#${blockNumber.toLocaleString('ko-KR')}`
}

/** '0x1f9c4b2e…5b8072a' → '0x1f9c…072a' — 지갑 주소는 일부만 화면에 넣는다. */
export function shortAddress(address: string) {
  if (address.length <= 13) return address
  return `${address.slice(0, 6)}…${address.slice(-4)}`
}

/**
 * '이싸피' → '이*피', '김민' → '김*', '박' → '박'
 *
 * 리뷰는 누가 썼는지 알아볼 수 있으면 안 되지만 완전히 익명이면 같은 사람이 여러 건을
 * 쓴 것인지도 구별되지 않는다. 가운데만 가려 앞뒤 글자는 남긴다 — 국내 서비스의 통례다.
 *
 * 서버가 아니라 화면에서 가린다. 마스킹은 표시 규칙이라 값 자체는 원본으로 두어야
 * 정렬·비교가 어긋나지 않는다. 대신 이 함수를 거치지 않고 닉네임을 그리면 그대로
 * 노출되므로, 리뷰를 새로 그리는 곳에서는 반드시 여기를 통과시킨다.
 */
export function maskName(name: string) {
  const trimmed = name.trim()
  if (trimmed.length <= 1) return trimmed
  if (trimmed.length === 2) return `${trimmed[0]}*`
  return `${trimmed[0]}${'*'.repeat(trimmed.length - 2)}${trimmed.at(-1)}`
}

/**
 * '2026-09-07T14:22:00Z' → '09.07 14:22', null → '—'
 *
 * 표준시를 서울로 고정한다. 상대 시각('3일 전')은 만들지 않는다. 목록을 열어 둔 채 자정을 넘기면 값이 어긋나기 때문이다.
 */
const DATE_TIME = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  // h23 이 아니면 자정이 '24:00' 으로 나오는 엔진이 있다.
  hourCycle: 'h23',
})

// 구분자를 로케일에 맡기지 않는다 — ko-KR 이 '9. 7. 14:22' 같은 모양을 줄 수 있다.
function timeParts(iso: string | null) {
  if (!iso) return null
  const at = Date.parse(iso)
  if (Number.isNaN(at)) return null
  return Object.fromEntries(DATE_TIME.formatToParts(at).map((p) => [p.type, p.value]))
}

export function timeLabel(iso: string | null) {
  const part = timeParts(iso)
  if (!part) return '—'
  return `${part.month}.${part.day} ${part.hour}:${part.minute}`
}

/**
 * '2026-09-01T09:00:00+09:00' → '2026.09.01 09:00'
 *
 * 해를 떼지 않는다. 온체인 등록 시각처럼 영구히 남는 기록은 재작년 것과 구별돼야 하고,
 * 사용자가 익스플로러와 맞춰 보는 값이다.
 */
export function dateTimeLabel(iso: string | null) {
  const part = timeParts(iso)
  if (!part) return '—'
  return `${part.year}.${part.month}.${part.day} ${part.hour}:${part.minute}`
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

/** `Record<ItemNodeType, string>` 이라 유형이 늘어나면 채울 때까지 컴파일이 실패한다. */
const NODE_TYPE_LABELS: Record<ItemNodeType, string> = {
  LLM: '기본',
  AGENT: '에이전트',
}

export const nodeTypeLabel = (type: ItemNodeType) => NODE_TYPE_LABELS[type]

/** 제어 노드는 `nodeType` 이 없어 종류 이름을 대신 쓴다. */
const CONTROL_TYPE_LABELS: Record<ControlType, string> = {
  BRANCH: '분기',
}

export const controlTypeLabel = (type: ControlType) => CONTROL_TYPE_LABELS[type]

const IO_TYPE_LABELS: Record<IoType, string> = {
  START: '시작',
  END: '종료',
}

export const ioTypeLabel = (type: IoType) => IO_TYPE_LABELS[type]

/** 카드에 적히는 이름. 제어·입출력은 팔레트에 없어 이름을 여기서 받는다. */
const KIND_NAMES: Record<ControlKind, string> = {
  BRANCH: '조건 분기',
  START: '시작',
  END: '종료',
}

export const controlKindName = (kind: ControlKind) => KIND_NAMES[kind]

/** 노드 정보 패널이 설명 자리에 쓰는 한 줄. 제어·입출력은 팔레트에 없어 설명도 없다. */
const KIND_HINTS: Record<ControlKind, string> = {
  BRANCH: '조건이 참이면 true, 거짓이면 false 로 갈라집니다.',
  START: '워크플로우를 시작합니다. 실행할 때 올린 입력이 다음 노드로 넘어갑니다.',
  END: '워크플로우가 끝납니다. 마지막 노드의 결과를 결과 화면으로 내보냅니다.',
}

export const controlKindHint = (kind: ControlKind) => KIND_HINTS[kind]

/** 카드 아래에 붙는 종류 이름. 셋 중 채워진 것을 쓴다 — 한 노드는 하나만 갖는다. */
export function placedKindLabel(node: {
  nodeType?: ItemNodeType | null
  controlType?: ControlType | null
  ioType?: IoType | null
}): string {
  if (node.ioType) return ioTypeLabel(node.ioType)
  if (node.controlType) return controlTypeLabel(node.controlType)
  return node.nodeType ? nodeTypeLabel(node.nodeType) : ''
}
