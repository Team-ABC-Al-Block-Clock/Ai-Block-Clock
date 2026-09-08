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

/** 540 → '540 Credit' */
export function creditLabel(n: number) {
  return n.toLocaleString() + ' Credit'
}
