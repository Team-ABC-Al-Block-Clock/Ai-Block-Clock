/** 잔액 고리의 둘레. 반지름 72 의 2πr 이다. */
export const GAUGE_LENGTH = 452.39

/** 실행에 묶인 것까지가 전체다. 잔액이 0 이면 나눗셈이 NaN 이라 빈 고리로 둔다. */
export function gaugeArc(available: string | undefined, reserved: string | undefined) {
  const total = Number(available ?? 0) + Number(reserved ?? 0)
  return total > 0 ? (GAUGE_LENGTH * Number(available ?? 0)) / total : 0
}
