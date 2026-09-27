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
      {
        type: 'list',
        ordered: false,
        start: 1,
        items: [
          { text: '하나', children: [] },
          { text: '둘', children: [] },
        ],
      },
      { type: 'table', header: ['항목', '값'], rows: [['모드', 'mock']] },
      { type: 'code', text: '{ "a": 1 }' },
      { type: 'rule' },
    ])
  })

  it('번호 목록', () => {
    expect(parseMarkdown('1. 가\n2. 나')).toEqual([
      {
        type: 'list',
        ordered: true,
        start: 1,
        items: [
          { text: '가', children: [] },
          { text: '나', children: [] },
        ],
      },
    ])
  })

  // Gemini 가 흔히 내는 모양. 끊어 읽으면 번호가 1. 1. 1. 로 보인다.
  it('하위 항목과 빈 줄을 건너 한 목록으로 잇는다', () => {
    const [list] = parseMarkdown('1. **시장**\n   - 규모\n2. **경쟁**\n\n3. 결론')
    expect(list).toEqual({
      type: 'list',
      ordered: true,
      start: 1,
      items: [
        { text: '**시장**', children: ['규모'] },
        { text: '**경쟁**', children: [] },
        { text: '결론', children: [] },
      ],
    })
  })

  it('3 에서 시작하는 목록은 시작 번호를 지킨다', () => {
    expect(parseMarkdown('3. 셋\n4. 넷')[0]).toMatchObject({ start: 3 })
  })

  it('날짜 줄은 목록이 아니다', () => {
    expect(parseMarkdown('2026. 9. 27. 기준으로 작성')).toEqual([
      { type: 'paragraph', text: '2026. 9. 27. 기준으로 작성' },
    ])
  })

  it('한 줄짜리 코드 묶음은 뒤를 삼키지 않는다', () => {
    expect(parseMarkdown('```json {"level":"high"} ```\n## 제목\n본문')).toEqual([
      { type: 'code', text: '{"level":"high"}' },
      { type: 'heading', level: 2, text: '제목' },
      { type: 'paragraph', text: '본문' },
    ])
  })

  it('닫히지 않은 코드 줄은 그 줄만 문단으로 둔다', () => {
    expect(parseMarkdown('``` 입니다\n\n## 다음')).toEqual([
      { type: 'paragraph', text: '``` 입니다' },
      { type: 'heading', level: 2, text: '다음' },
    ])
  })

  it('구분선은 문단을 끊는다', () => {
    expect(parseMarkdown('위\n\n***\n아래')).toEqual([
      { type: 'paragraph', text: '위' },
      { type: 'rule' },
      { type: 'paragraph', text: '아래' },
    ])
  })

  // 문장 바로 아래 --- 는 구분선이 아니라 제목이다. PDF(Python-Markdown)도 그렇게 그린다.
  it('밑줄 제목', () => {
    expect(parseMarkdown('앞 문단\n요약\n---\n본문')).toEqual([
      { type: 'paragraph', text: '앞 문단' },
      { type: 'heading', level: 2, text: '요약' },
      { type: 'paragraph', text: '본문' },
    ])
    expect(parseMarkdown('큰 제목\n===')[0]).toEqual({ type: 'heading', level: 1, text: '큰 제목' })
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
    expect(jsonToMarkdown({ ok: true, done: false })).toBe('## ok\n\nTrue\n\n## done\n\nFalse')
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
