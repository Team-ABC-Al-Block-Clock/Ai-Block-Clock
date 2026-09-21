import { describe, expect, it } from 'vitest'

import { NODE_SIZE, portPoint } from './canvasWorkflow'
import { roundedPath, routeEdge } from './edgeRoute'

const HALF = NODE_SIZE / 2
/** 한가운데 좌표로 카드를 만든다. 포트는 좌우 변의 가운데다. */
const card = (x: number, y: number) => ({ x: x - HALF, y: y - HALF })

/** 꺾인 점들을 촘촘히 훑어 카드 한가운데에 가장 가까웠던 거리. */
const 최소거리 = (points: { x: number; y: number }[], centers: { x: number; y: number }[]) => {
  let min = Infinity
  for (let i = 1; i < points.length; i++) {
    for (let t = 0; t <= 30; t++) {
      const x = points[i - 1].x + ((points[i].x - points[i - 1].x) * t) / 30
      const y = points[i - 1].y + ((points[i].y - points[i - 1].y) * t) / 30
      for (const c of centers) min = Math.min(min, Math.hypot(x - c.x, y - c.y))
    }
  }
  return min
}

describe('routeEdge', () => {
  it('막는 것이 없으면 곧게 간다 — 꺾이지 않는다', () => {
    const route = routeEdge(portPoint(card(0, 0), 'out'), portPoint(card(600, 0), 'in'), [
      { x: 0, y: 0 },
      { x: 600, y: 0 },
    ])!
    expect(route).not.toBeNull()
    expect(new Set(route.map((p) => p.y)).size).toBe(1)
  })

  it('길을 막고 선 카드를 피해 돌아간다', () => {
    const centers = [
      { x: 0, y: 0 },
      { x: 300, y: 0 },
      { x: 600, y: 0 },
    ]
    const route = routeEdge(portPoint(card(0, 0), 'out'), portPoint(card(600, 0), 'in'), centers)!
    expect(최소거리(route, centers)).toBeGreaterThanOrEqual(HALF - 0.5)
  })

  it('되돌아가는 선도 카드를 밟지 않는다', () => {
    const centers = [
      { x: 600, y: 100 },
      { x: 100, y: 400 },
    ]
    const route = routeEdge(
      portPoint(card(600, 100), 'out'),
      portPoint(card(100, 400), 'in'),
      centers,
    )!
    expect(최소거리(route, centers)).toBeGreaterThanOrEqual(HALF - 0.5)
  })

  it('양 끝은 포트에 정확히 닿는다', () => {
    const from = portPoint(card(0, 0), 'out')
    const to = portPoint(card(600, 200), 'in')
    const route = routeEdge(from, to, [
      { x: 0, y: 0 },
      { x: 600, y: 200 },
    ])!
    expect(route[0]).toEqual(from)
    expect(route[route.length - 1]).toEqual(to)
  })
})

describe('roundedPath', () => {
  it('꺾이는 자리를 둥글린다', () => {
    const d = roundedPath([
      { x: 0, y: 0 },
      { x: 100, y: 0 },
      { x: 100, y: 100 },
    ])
    expect(d.startsWith('M0 0')).toBe(true)
    expect(d).toContain('Q100 0')
    expect(d.endsWith('L100 100')).toBe(true)
  })

  it('점이 둘뿐이면 곧은 선이다', () => {
    expect(
      roundedPath([
        { x: 0, y: 0 },
        { x: 50, y: 0 },
      ]),
    ).toBe('M0 0 L50 0')
  })
})
