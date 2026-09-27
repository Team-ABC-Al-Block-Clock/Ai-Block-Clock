import { describe, expect, it } from 'vitest'

import { jsonToMarkdown, parseInline, parseMarkdown } from './markdown'

describe('parseMarkdown', () => {
  it('제목·문단·목록·표·코드를 가른다', () => {
    const blocks = parseMarkdown(
      [
        '## 요약',
        '첫 줄',
        '이어지는 줄',
        '',
        '- 하나',
        '- 둘',
        '',
        '| 항목 | 값 |',
        '| --- | --- |',
        '| 모드 | mock |',
        '',
        '```json',
        '{ "a": 1 }',
        '```',
        '---',
      ].join('\n'),
    )

    expect(blocks).toEqual([
      { type: 'heading', level: 2, text: '요약' },
      { type: 'paragraph', text: '첫 줄 이어지는 줄' },
      { type: 'list', ordered: false, items: ['하나', '둘'] },
      { type: 'table', header: ['항목', '값'], rows: [['모드', 'mock']] },
      { type: 'code', text: '{ "a": 1 }' },
      { type: 'rule' },
    ])
  })

  it('번호 목록', () => {
    expect(parseMarkdown('1. 가\n2. 나')).toEqual([
      { type: 'list', ordered: true, items: ['가', '나'] },
    ])
  })

  it('구분선이 없는 | 줄은 표가 아니다', () => {
    expect(parseMarkdown('a | b')).toEqual([{ type: 'paragraph', text: 'a | b' }])
  })
})

describe('parseInline', () => {
  it('굵게와 코드만 가른다', () => {
    expect(parseInline('값은 **높음** 이고 `level` 칸')).toEqual([
      { text: '값은 ' },
      { text: '높음', bold: true },
      { text: ' 이고 ' },
      { text: 'level', code: true },
      { text: ' 칸' },
    ])
  })
})

describe('parseInline — 한 줄 코드 묶음', () => {
  it('문장 안의 ``` 묶음을 백틱 없이 코드로', () => {
    expect(parseInline('앞 ```json { "a": 1 } ``` 뒤')).toEqual([
      { text: '앞 ' },
      { text: '{ "a": 1 }', code: true },
      { text: ' 뒤' },
    ])
  })
})

describe('jsonToMarkdown', () => {
  it('엔진 to_markdown 과 같은 글 — 키는 제목, 한 겹만 펴고 아래는 눕힌다', () => {
    expect(
      jsonToMarkdown({
        level: 'high',
        memo: null,
        tags: ['a', { name: 'b', note: null }],
        detail: { score: 3, meta: { x: 1, y: [2, 3] } },
      }),
    ).toBe(
      [
        '## level',
        '',
        'high',
        '',
        '## tags',
        '',
        '- a',
        '- name: b',
        '',
        '## detail',
        '',
        '- **score**: 3',
        '- **meta**: x: 1 · y: 2, 3',
      ].join('\n'),
    )
  })
})
