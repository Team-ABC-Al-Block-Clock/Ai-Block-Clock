/** 화면 표시용 포맷. 화면마다 다시 쓰면 같은 값이 화면마다 다르게 보인다. */

/** 2400 → '2.4k', 640 → '640' */
export function compactCount(n: number) {
  if (n < 1000) return String(n)
  return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k'
}

/** 1248 → '1,248회' */
export function runLabel(n: number) {
  return n.toLocaleString() + '회'
}

/**
 * 단가를 문자열로 받는다. `Intl.NumberFormat` 은 문자열 인자를 **10진수 그대로** 파싱하므로
 * (Intl.NumberFormat v3) Number 로 되돌리지 않아도 되고, 소수점 오차도 생기지 않는다.
 * 천 단위 구분과 소수점 뒤 0 정리를 브라우저가 해 준다 —
 * Jackson 이 BigDecimal('540.00') 을 '540.00' 으로 줘도 화면에는 '540' 이 나간다.
 */
const AMOUNT = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 2 })

/** '540' → '540 ABC', '540.00' → '540 ABC', '1248.50' → '1,248.5 ABC'
 *
 *  단위는 ABC 하나뿐이라 응답의 통화 필드를 받지 않고 여기서 붙인다.
 *
 *  TS 는 문자열 오버로드를 `StringNumericLiteral`(숫자 모양 리터럴)로 타이핑하기 때문에
 *  API 에서 온 `string` 은 단언이 필요하다. 명세가 `price` 를 10진수 문자열로 확정했으니
 *  여기 한 곳에서만 단언한다 — 값이 숫자가 아니면 RangeError 로 시끄럽게 터지는 편이
 *  'abc ABC' 를 조용히 그리는 것보다 낫다. */
export function priceLabel(price: string | number) {
  return `${AMOUNT.format(price as Intl.StringNumericLiteral)} ABC`
}

/**
 * 4.7 · 35 → '4.7 (35)', 리뷰 0건 → '—'
 *
 * 콜드스타트(`averageRating: null`)를 한 곳에서 처리한다. 화면마다 따로 다루면
 * 어딘가는 `NaN` 이나 빈칸이 된다.
 */
export function ratingLabel(averageRating: number | null, reviewCount: number) {
  if (averageRating === null || reviewCount === 0) return '—'
  return `${averageRating.toFixed(1)} (${compactCount(reviewCount)})`
}

/**
 * 0.982 → '98.2%', 1 → '100%', 0.9995 → '99.9%', 실행 0회 → '—'
 *
 * 실행이 없으면 서버도 비율을 만들 수 없어 `successRate` 가 0 으로 온다.
 * 그대로 그리면 '0%' — 전부 실패했다는 거짓말이 된다.
 *
 * 반올림이 아니라 **내림**이다. 반올림하면 9,999/10,000 이 '100%' 가 되어
 * '한 번도 실패하지 않았다' 는 과장이 된다 — 0회를 '—' 로 막는 것과 같은 이유다.
 * 실제로 100% 일 때만 '100%' 가 나온다.
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
 * 아직 실행하지 않은 워크플로우는 `lastExecutedAt` 이 null 로 온다. 그대로 그리면 'Invalid Date' 다.
 *
 * 상대 시각('3일 전')은 만들지 않는다 — 시계에 의존해 경계마다 답이 달라지고, 목록을 열어 둔
 * 채로 자정을 넘기면 화면과 값이 어긋난다.
 *
 * 표준시를 서울로 고정한다. 안 그러면 같은 응답이 기계의 TZ 에 따라 다르게 보이고,
 * 테스트가 CI 의 TZ 설정에 따라 통과하거나 실패한다.
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
 * 받침이 있으면 '을', 없으면 '를'. '성적 분석 리포트를' · '메일 초안을'
 *
 * 삭제 확인 모달이 사용자가 만든 이름을 문장에 넣는다. 조사를 고정하면 이름에 따라
 * 어색한 문장이 나온다. 한글이 아닌 끝 글자(영문·숫자)는 '를' 로 둔다 —
 * 발음까지 따지려면 표를 들여야 하고, 이 화면에는 그만한 값어치가 없다.
 */
export function josa(name: string) {
  const code = name.trim().slice(-1).charCodeAt(0)
  const isHangul = code >= 0xac00 && code <= 0xd7a3
  return isHangul && (code - 0xac00) % 28 !== 0 ? '을' : '를'
}
