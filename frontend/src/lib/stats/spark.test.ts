import { describe, expect, it } from 'vitest'

import { sparkPath } from '@/lib/stats/spark'

/** 0 으로 나누는 경로와 좌표 뒤집힘. 둘 다 화면에는 '빈칸' 으로만 드러나 눈으로 못 잡는다. */

describe('sparkPath', () => {
  it('최솟값은 아래, 최댓값은 위에 붙는다', () => {
    const d = sparkPath([1, 2, 3, 4, 5, 6, 7])

    // y 는 아래로 갈수록 커진다 — 증가하는 추이는 27 에서 시작해 1 에서 끝나야 한다.
    expect(d.startsWith('M1 27 ')).toBe(true)
    expect(d.endsWith('L89 1')).toBe(true)
  })

  it('7일치가 폭 전체에 균등하게 놓인다', () => {
    const xs = sparkPath([1, 2, 3, 4, 5, 6, 7])
      .split(' ')
      .filter((_, i) => i % 2 === 0)
      .map((token) => Number(token.slice(1)))

    expect(xs).toHaveLength(7)
    expect(xs[0]).toBe(1)
    expect(xs.at(-1)).toBe(89)
    // 간격이 일정한지 — 하나만 어긋나면 꺾은선이 한쪽으로 쏠린다.
    // 88 / 6 = 14.666… 이라 소수 한 자리로 자르면 14.7 과 14.6 이 섞인다. 폭 오차만 본다.
    const gaps = xs.slice(1).map((x, i) => x - xs[i])
    // 좌표를 소수 한 자리로 자르므로 간격은 최대 0.1 흔들린다. 쏠림만 걸러 낸다.
    for (const gap of gaps) expect(Math.abs(gap - 88 / 6)).toBeLessThan(0.15)
  })

  it('값이 전부 같으면 NaN 이 아니라 가운데 수평선', () => {
    // 콜드스타트(실행 0회). (v - min) / (max - min) 이 0/0 이 되는 자리다.
    expect(sparkPath([0, 0, 0, 0, 0, 0, 0])).toBe('M1 14 L89 14')
    expect(sparkPath([5, 5, 5])).toBe('M1 14 L89 14')
  })

  it('점이 하나면 수평선, 없으면 빈 문자열', () => {
    expect(sparkPath([9])).toBe('M1 14 L89 14')
    // svg 가 d="" 를 받으면 아무것도 안 그린다. 좌표를 지어내지 않는다.
    expect(sparkPath([])).toBe('')
  })

  it('좌표에 NaN 이 섞이지 않는다', () => {
    for (const trend of [[0, 0, 0], [1, 9], [3, 1, 4, 1, 5, 9, 2], [0], []]) {
      expect(sparkPath(trend)).not.toContain('NaN')
    }
  })
})
