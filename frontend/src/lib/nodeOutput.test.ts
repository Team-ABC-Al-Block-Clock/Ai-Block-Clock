import { describe, expect, it } from 'vitest'

import { readOutputDraft, sameOutput, toOutputDraft } from './nodeOutput'

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
