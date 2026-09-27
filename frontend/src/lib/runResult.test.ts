import { describe, expect, it } from 'vitest'

import { resultOf } from './runResult'

describe('resultOf', () => {
  it('글 출력은 마크다운으로 읽고 원문을 복사한다', () => {
    const result = resultOf({ schema_id: 'text', payload: { text: '## 결과\n본문' } })

    expect(result?.text).toBe('## 결과\n본문')
    expect(result?.blocks).toEqual([
      { type: 'heading', level: 2, text: '결과' },
      { type: 'paragraph', text: '본문' },
    ])
  })

  it('JSON 출력은 키를 제목으로 펴고 들여쓴 JSON 을 복사한다', () => {
    const result = resultOf({ schema_id: 'json', payload: { level: 'high' } })

    expect(result?.text).toBe('{\n  "level": "high"\n}')
    expect(result?.blocks[0]).toEqual({ type: 'heading', level: 2, text: 'level' })
  })

  // 판매자가 예시에 `text` 칸 하나만 둔 JSON 과 글을 가를 길은 모양뿐이다. 칸이 더 있으면 JSON 이다.
  it('text 말고 칸이 더 있으면 JSON 으로 본다', () => {
    expect(resultOf({ payload: { text: 'a', score: 1 } })?.text).toContain('"score": 1')
  })

  it('봉투가 아니면 null', () => {
    expect(resultOf(null)).toBeNull()
    expect(resultOf({ payload: 'x' })).toBeNull()
  })
})
