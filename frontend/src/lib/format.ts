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

/** 2_370_000 → '2.4M', 42_066_000 → '42.1M' */
export function tokenLabel(n: number) {
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1).replace(/\.0$/, '') + 'M'
  return compactCount(n)
}

/**
 * '540' → '540 Credit', '540.00' → '540 Credit', '1248.50' → '1,248.5 Credit'
 *
 * `price` 가 문자열로 오는 이유가 소수점 오차 방지이므로 Number 로 되돌리지 않는다.
 * 천 단위 구분만 문자열 위에서 넣는다.
 */
export function creditLabel(price: string | number) {
  const [int, frac = ''] = String(price).split('.')
  const grouped = int.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  // 소수점 뒤 0 은 떼어낸다. Jackson 이 BigDecimal('540.00') 을 '540.00' 으로 주는데
  // 그대로 쓰면 카드마다 '540.00 Credit' 이 된다 — DB 컬럼 자릿수가 화면에 새는 것이다.
  const fraction = frac.replace(/0+$/, '')
  return (fraction ? `${grouped}.${fraction}` : grouped) + ' Credit'
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
