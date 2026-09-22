/** 수수료율. 임시값이다 — 서버 정책값이므로 응답이 판매가를 주면 그것이 이긴다. */
const FEE_PERCENT = 20n

/** 실행가에 수수료를 붙인 판매 단가. 읽을 수 없는 값이면 null. */
export function sellingPrice(estimatedCost: string): string | null {
  const cost = estimatedCost.trim()
  if (!/^\d+(\.\d+)?$/.test(cost)) return null

  // 둘째 자리까지 정수로 올려 계산한다 — 실수로 곱하면 180 이 216.00000000000003 이 된다.
  const scaled = BigInt(Math.round(Number(cost) * 100))
  const total = (scaled * (100n + FEE_PERCENT)) / 100n
  const whole = total / 100n
  const rest = total % 100n
  if (rest === 0n) return `${whole}`
  return `${whole}.${`${rest}`.padStart(2, '0').replace(/0$/, '')}`
}
