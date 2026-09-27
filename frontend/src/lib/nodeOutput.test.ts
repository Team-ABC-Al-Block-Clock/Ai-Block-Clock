import { describe, expect, it } from 'vitest'

import {
  EXAMPLE_INVALID,
  fieldKind,
  outputDraftProblem,
  outputExampleProblem,
  readOutputDraft,
  sameOutput,
  scalarFields,
  toOutputDraft,
} from './nodeOutput'

describe('readOutputDraft', () => {
  it('글이면 예시를 버린다', () => {
    expect(readOutputDraft({ kind: 'TEXT', text: '{"a":1}' })).toEqual({
      kind: 'TEXT',
      example: null,
    })
  })

  it('JSON 객체면 그대로 싣는다', () => {
    expect(readOutputDraft({ kind: 'JSON', text: '{"level":"high"}' })).toEqual({
      kind: 'JSON',
      example: { level: 'high' },
    })
  })

  // 서버가 `isExampleMatchingKind` 로 물리는 것들이다.
  it.each(['', '{', '[]', '{}', '"high"', '3'])('객체가 아니거나 비었으면 null — %s', (text) => {
    expect(readOutputDraft({ kind: 'JSON', text })).toBeNull()
  })

  it.each(['{"meta":{}}', '{"meta":{"detail":{}}}', '{"items":[{"id":1},{}]}'])(
    '하위 객체와 배열 안의 빈 객체도 거른다 — %s',
    (text) => {
      expect(readOutputDraft({ kind: 'JSON', text })).toBeNull()
    },
  )

  // 엔진 `check_example` 의 경계. 루트가 1단계이고, 6단계에 닿으면 물린다.
  it('중첩은 5단계까지', () => {
    const five = { a: { b: { c: { d: 'x' } } } }
    const six = { a: { b: { c: { d: { e: 'x' } } } } }
    expect(outputExampleProblem(five)).toBeNull()
    expect(outputExampleProblem(six)).toMatch(/5단계/)
  })

  it('키는 전체에서 60개까지', () => {
    const keys = (n: number) =>
      Object.fromEntries(Array.from({ length: n }, (_, i) => [`k${i}`, i]))
    expect(outputExampleProblem(keys(60))).toBeNull()
    expect(outputExampleProblem(keys(61))).toMatch(/60개/)
  })

  // 엔진은 배열의 첫 원소로만 모양을 잰다. 긴 목록 예시를 막으면 엔진이 받는 것까지 막는다.
  it('깊이와 키 수는 배열의 첫 원소만 센다', () => {
    const rows = Array.from({ length: 30 }, () => ({ a: 1, b: 2, c: 3 }))
    expect(outputExampleProblem({ rows })).toBeNull()
  })

  it('폼 문구는 무엇이 틀렸는지 말한다', () => {
    expect(outputDraftProblem({ kind: 'JSON', text: '{' })).toBe(EXAMPLE_INVALID)
    expect(outputDraftProblem({ kind: 'JSON', text: '{"meta":{}}' })).toMatch(/빈 객체/)
    expect(outputDraftProblem({ kind: 'TEXT', text: '{' })).toBeNull()
  })

  it('값이 있는 하위 객체와 null 은 보존한다', () => {
    const example = { meta: { name: null }, items: [{ id: 1 }], tags: [] }
    expect(readOutputDraft({ kind: 'JSON', text: JSON.stringify(example) })).toEqual({
      kind: 'JSON',
      example,
    })
  })
})

describe('toOutputDraft', () => {
  it('비어 있으면 글', () => {
    expect(toOutputDraft(null)).toEqual({ kind: 'TEXT', text: '' })
  })

  it('예시를 되읽어도 같은 값이다', () => {
    const output = { kind: 'JSON' as const, example: { a: 1, b: { c: null } } }
    expect(sameOutput(readOutputDraft(toOutputDraft(output)), output)).toBe(true)
  })
})

describe('scalarFields', () => {
  it('객체 안까지 내려가고 배열은 건너뛴다', () => {
    expect(
      scalarFields({ topic: 'a', score: 0, urgent: false, tags: ['x'], detail: { level: null } }),
    ).toEqual([
      { path: 'topic', kind: 'string' },
      { path: 'score', kind: 'number' },
      { path: 'urgent', kind: 'boolean' },
      { path: 'detail.level', kind: 'string' },
    ])
  })
})

describe('fieldKind', () => {
  const example = { level: 'high', items: [{ name: 'a' }], detail: { score: 1 } }

  it('점 경로와 배열 번호를 따라간다', () => {
    expect(fieldKind(example, 'detail.score')).toBe('number')
    expect(fieldKind(example, 'items.0.name')).toBe('string')
  })

  it('없거나 견줄 수 없는 칸은 null', () => {
    expect(fieldKind(example, 'nope')).toBeNull()
    expect(fieldKind(example, 'items')).toBeNull()
    expect(fieldKind(example, 'detail')).toBeNull()
  })
})
