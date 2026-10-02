/** 목록이 주는 `trend`(최근 7일 실행 횟수)를 카드의 꺾은선으로 바꾼다. */

/** 카드의 svg 와 같은 viewBox. 선 굵기 2 라 위아래로 1 씩 비워 둔다. */
const WIDTH = 90
const TOP = 1
const BOTTOM = 27
const LEFT = 1
const RIGHT = WIDTH - 1

/** 좌표 문자열이 길어지면 DOM 만 무거워진다. 90×28 에서 소수 한 자리면 충분하다. */
const round = (n: number) => Number(n.toFixed(1))

/**
 * `[12, 18, 15, 22, 19, 26, 31]` → `'M1 27 L15.7 21.7 … L89 1'`
 *
 * 최솟값이 아래, 최댓값이 위에 붙는다 — 절대 크기가 아니라 추이를 보여주는 그림이다.
 * 값이 전부 같으면 가운데 수평선을 그린다. 나누면 NaN 이 좌표에 들어가 path 가
 * 통째로 사라지는데 콘솔에는 아무것도 안 남는다.
 */
export function sparkPath(trend: number[]): string {
  if (trend.length === 0) return ''

  const min = Math.min(...trend)
  const max = Math.max(...trend)

  // 점이 하나뿐이거나 변화가 없으면 기울기를 만들 수 없다.
  if (trend.length === 1 || min === max) {
    const mid = round((TOP + BOTTOM) / 2)
    return `M${LEFT} ${mid} L${RIGHT} ${mid}`
  }

  const stepX = (RIGHT - LEFT) / (trend.length - 1)
  const span = BOTTOM - TOP
  return trend
    .map((value, i) => {
      const x = round(LEFT + i * stepX)
      const y = round(BOTTOM - ((value - min) / (max - min)) * span)
      return `${i === 0 ? 'M' : 'L'}${x} ${y}`
    })
    .join(' ')
}
