import { describe, expect, it } from 'vitest'

import { FILL_MS, creepTo, rollRate, rollTo } from './ease'

/** 60fps 한 프레임. */
const FRAME = 1000 / 60

/** `from` 에서 `to` 까지 굴리는 데 걸린 시간(ms). */
const timeToRoll = (from: number, to: number) => {
  const perMs = rollRate(from, to)
  let value = from
  let elapsed = 0
  while (value !== to && elapsed < 10_000) {
    value = rollTo(value, to, FRAME, perMs)
    elapsed += FRAME
  }
  return elapsed
}

describe('rollTo', () => {
  it('목표를 넘어서지 않는다', () => {
    const perMs = rollRate(0, 50)
    let value = 0
    for (let i = 0; i < 500; i++) {
      const next = rollTo(value, 50, FRAME, perMs)
      expect(next).toBeGreaterThanOrEqual(value)
      expect(next).toBeLessThanOrEqual(50)
      value = next
    }
    expect(value).toBe(50)
  })

  it('거리가 달라도 걸리는 시간은 같다 — 일정한 속도로 굴러 보인다', () => {
    const short = timeToRoll(0, 10)
    const long = timeToRoll(0, 90)

    expect(Math.abs(short - long)).toBeLessThan(FRAME * 2)
    expect(short).toBeGreaterThan(FILL_MS * 0.9)
    expect(short).toBeLessThan(FILL_MS * 1.1)
  })

  it('한 프레임에 같은 거리를 나아간다 — 앞만 빠르지 않다', () => {
    const perMs = rollRate(0, 100)
    const first = rollTo(0, 100, FRAME, perMs)
    const later = rollTo(80, 100, FRAME, perMs) - 80

    expect(first).toBeCloseTo(later, 6)
  })

  it('뒤로 가는 값도 따라간다', () => {
    expect(timeToRoll(80, 20)).toBeGreaterThan(FILL_MS * 0.9)
    expect(rollTo(80, 20, FRAME, rollRate(80, 20))).toBeLessThan(80)
  })

  it('이미 닿았으면 그대로 둔다', () => {
    expect(rollTo(50, 50, FRAME, rollRate(50, 50))).toBe(50)
  })
})

describe('creepTo', () => {
  it('눈금에 닿지 않는다 — 닿으면 서버보다 앞선다', () => {
    let value = 33
    for (let i = 0; i < 2000; i++) value = creepTo(value, 67, FRAME)

    expect(value).toBeLessThan(67)
    expect(value).toBeCloseTo(66, 0)
  })

  it('단계가 길어도 계속 움직인다', () => {
    const from = 33
    let value = from
    for (let i = 0; i < 60; i++) value = creepTo(value, 67, FRAME)

    expect(value).toBeGreaterThan(from + 5)
  })

  it('이미 눈금 앞에 서 있으면 움직이지 않는다', () => {
    expect(creepTo(66.5, 67, FRAME)).toBe(66.5)
    expect(creepTo(70, 67, FRAME)).toBe(70)
  })

  it('흐른 시간이 없으면 제자리다', () => {
    expect(creepTo(33, 67, 0)).toBe(33)
  })
})
