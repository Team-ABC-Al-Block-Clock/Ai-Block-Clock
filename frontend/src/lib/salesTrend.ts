import type { SettlementStatistics } from '@/api/settlements'

const DAY_MS = 86_400_000

/** 서버는 받은 것이 없는 날을 뺀다. 기간이 있으면 빈 날을 0 으로 채워 선이 날짜를 건너뛰지 않게 한다. */
export function dailySeries(
  periods: SettlementStatistics['periods'],
  range: { from?: string; to?: string },
) {
  if (!range.from || !range.to) {
    return periods.map((p) => ({
      day: p.periodStart,
      amount: Number(p.amount),
      executionCount: p.executionCount,
    }))
  }
  const byDay = new Map(periods.map((p) => [p.periodStart, p]))
  const series: { day: string; amount: number; executionCount: number }[] = []
  for (let at = Date.parse(range.from); at <= Date.parse(range.to); at += DAY_MS) {
    const day = new Date(at).toISOString().slice(0, 10)
    const hit = byDay.get(day)
    series.push({ day, amount: Number(hit?.amount ?? 0), executionCount: hit?.executionCount ?? 0 })
  }
  return series
}

/** 0 이 바닥, 최댓값이 `top` 에 붙는다. 점이 하나면 수평선이다. */
export function trendPath(values: number[], width: number, height: number, top = 12) {
  if (values.length === 0) return ''
  const max = Math.max(...values)
  const y = (v: number) =>
    Number((max > 0 ? height - ((height - top) * v) / max : height).toFixed(1))
  if (values.length === 1) return `M0 ${y(values[0])} L${width} ${y(values[0])}`
  const step = width / (values.length - 1)
  return values
    .map((v, i) => `${i === 0 ? 'M' : 'L'}${Number((i * step).toFixed(1))} ${y(v)}`)
    .join(' ')
}

/** 눈금 다섯 개. 날이 적으면 있는 날만 쓴다. `09-26` 꼴이다. */
export function trendTicks(series: { day: string }[]) {
  if (series.length <= 1) return series.map((p) => p.day.slice(5))
  return [0, 1, 2, 3, 4].map((k) => series[Math.round(((series.length - 1) * k) / 4)].day.slice(5))
}
