/** 기간 필터. `days` 가 0 이면 조건을 안 붙인다 — from·to 는 한쪽만 보낼 수 없다. */
export const PERIODS = [
  { label: '최근 7일', days: 7 },
  { label: '최근 30일', days: 30 },
  { label: '최근 90일', days: 90 },
  { label: '전체 기간', days: 0 },
]

/** 서버가 UTC 날짜 경계로 자르므로 UTC 로 만든다. */
export const utcDay = (ms: number) => new Date(ms).toISOString().slice(0, 10)

export function rangeOf(days: number): { from?: string; to?: string } {
  if (days === 0) return {}
  const now = Date.now()
  return { from: utcDay(now - (days - 1) * 86_400_000), to: utcDay(now) }
}

/** 이번 달은 1일부터 오늘까지, 지난달은 한 달 전부. UTC 기준이다. */
export function monthRanges(now = Date.now()) {
  const d = new Date(now)
  const y = d.getUTCFullYear()
  const m = d.getUTCMonth()
  return {
    current: { from: utcDay(Date.UTC(y, m, 1)), to: utcDay(now) },
    previous: { from: utcDay(Date.UTC(y, m - 1, 1)), to: utcDay(Date.UTC(y, m, 0)) },
  }
}

/** 지난달보다 몇 % 올랐는지. 지난달이 0 이면 비교할 수 없어 null 이다. */
export function growthPercent(current: number, previous: number): number | null {
  if (previous <= 0) return null
  return Math.round(((current - previous) / previous) * 1000) / 10
}
