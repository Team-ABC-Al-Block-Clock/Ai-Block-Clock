import { describe, expect, it } from 'vitest'

import { resultTextOf } from './runResult'

describe('resultTextOf', () => {
  it('글 출력은 본문 그대로', () => {
    expect(resultTextOf({ schema_id: 'text', payload: { text: '## 결과' } })).toBe('## 결과')
  })

  it('JSON 출력은 보기 좋게 편다', () => {
    expect(resultTextOf({ schema_id: 'json', payload: { level: 'high' } })).toBe(
      '{\n  "level": "high"\n}',
    )
  })

  // 판매자가 예시에 `text` 칸 하나만 둔 JSON 과 글을 가를 길은 모양뿐이다. 칸이 더 있으면 JSON 이다.
  it('text 말고 칸이 더 있으면 JSON 으로 본다', () => {
    expect(resultTextOf({ payload: { text: 'a', score: 1 } })).toContain('"score": 1')
  })

  it('봉투가 아니면 null', () => {
    expect(resultTextOf(null)).toBeNull()
    expect(resultTextOf({ payload: 'x' })).toBeNull()
  })
})
