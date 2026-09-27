import { describe, expect, it } from 'vitest'

import type { OutputKind } from '@/api/nodes'

import {
  blankField,
  EXAMPLE_INVALID,
  fieldIssues,
  fieldKind,
  fieldsExample,
  fieldsFrom,
  outputDraftProblem,
  outputExampleProblem,
  readOutputDraft,
  sameOutput,
  scalarFields,
  switchOutputMode,
  toOutputDraft,
  type FieldDraft,
} from './nodeOutput'

const raw = (kind: OutputKind, text: string) => ({ kind, form: false, fields: [], text })

const field = (name: string, kind: FieldDraft['kind'], value = ''): FieldDraft => ({
  ...blankField(),
  name,
  kind,
  value,
})

const form = (...fields: FieldDraft[]) => ({ kind: 'JSON' as const, form: true, fields, text: '' })

describe('readOutputDraft', () => {
  it('글이면 예시를 버린다', () => {
    expect(readOutputDraft(raw('TEXT', '{"a":1}'))).toEqual({
      kind: 'TEXT',
      example: null,
    })
  })

  it('JSON 객체면 그대로 싣는다', () => {
    expect(readOutputDraft(raw('JSON', '{"level":"high"}'))).toEqual({
      kind: 'JSON',
      example: { level: 'high' },
    })
  })

  // 서버가 `isExampleMatchingKind` 로 물리는 것들이다.
  it.each(['', '{', '[]', '{}', '"high"', '3'])('객체가 아니거나 비었으면 null — %s', (text) => {
    expect(readOutputDraft(raw('JSON', text))).toBeNull()
  })

  it.each(['{"meta":{}}', '{"meta":{"detail":{}}}', '{"items":[{"id":1},{}]}'])(
    '하위 객체와 배열 안의 빈 객체도 거른다 — %s',
    (text) => {
      expect(readOutputDraft(raw('JSON', text))).toBeNull()
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
    expect(outputDraftProblem(raw('JSON', '{'))).toBe(EXAMPLE_INVALID)
    expect(outputDraftProblem(raw('JSON', '{"meta":{}}'))).toMatch(/빈 객체/)
    expect(outputDraftProblem(raw('TEXT', '{'))).toBeNull()
  })

  it('값이 있는 하위 객체와 null 은 보존한다', () => {
    const example = { meta: { name: null }, items: [{ id: 1 }], tags: [] }
    expect(readOutputDraft(raw('JSON', JSON.stringify(example)))).toEqual({
      kind: 'JSON',
      example,
    })
  })
})

describe('toOutputDraft', () => {
  it('비어 있으면 글, JSON 을 고르면 빈 줄 하나짜리 폼', () => {
    const draft = toOutputDraft(null)
    expect(draft).toMatchObject({ kind: 'TEXT', form: true, text: '' })
    expect(draft.fields).toHaveLength(1)
    expect(draft.fields[0]).toMatchObject({ name: '', kind: 'string', value: '' })
  })

  it('한 단계짜리 예시는 폼으로 연다', () => {
    const draft = toOutputDraft({ kind: 'JSON', example: { level: 'high', score: 72, ok: false } })
    expect(draft.form).toBe(true)
    expect(draft.fields.map(({ name, kind, value }) => [name, kind, value])).toEqual([
      ['level', 'string', 'high'],
      ['score', 'number', '72'],
      ['ok', 'boolean', 'false'],
    ])
  })

  it.each([{ tags: ['a'] }, { meta: { level: 'x' } }, { level: null }])(
    '목록 · 하위 객체 · null 이 있으면 직접 입력으로 연다 — %j',
    (example) => {
      expect(toOutputDraft({ kind: 'JSON', example }).form).toBe(false)
    },
  )

  it.each([
    { a: 1, b: { c: null } },
    { level: 'high', score: 72, ok: false },
  ])('예시를 되읽어도 같은 값이다 — %j', (example) => {
    const output = { kind: 'JSON' as const, example }
    expect(sameOutput(readOutputDraft(toOutputDraft(output)), output)).toBe(true)
  })
})

describe('출력 폼', () => {
  it('타입대로 JSON 을 만들고 빈 값은 기본값으로 채운다', () => {
    expect(
      fieldsExample([
        field('level', 'string', 'high'),
        field(' score ', 'number', '72'),
        field('ok', 'boolean', 'false'),
        field('memo', 'string'),
        field('count', 'number'),
        field('done', 'boolean'),
      ]),
    ).toEqual({ level: 'high', score: 72, ok: false, memo: '', count: 0, done: true })
  })

  it('폼이면 필드로 읽는다', () => {
    expect(readOutputDraft(form(field('level', 'string', 'high')))).toEqual({
      kind: 'JSON',
      example: { level: 'high' },
    })
  })

  it('이름과 값이 다 빈 줄은 없는 것으로 본다', () => {
    expect(readOutputDraft(form(field('a', 'number', '1'), field('', 'string')))).toEqual({
      kind: 'JSON',
      example: { a: 1 },
    })
  })

  it('필드가 없으면 막는다', () => {
    const issues = fieldIssues([field('', 'string')])
    expect(issues.message).toMatch(/하나 이상/)
    expect(issues.names.size).toBe(0)
    expect(readOutputDraft(form(field('', 'string')))).toBeNull()
  })

  it('값만 있고 이름이 없는 줄을 짚는다', () => {
    const blank = field('', 'string', 'high')
    const issues = fieldIssues([field('a', 'string'), blank])
    expect([...issues.names]).toEqual([blank.id])
    expect(issues.message).toMatch(/이름/)
  })

  it('겹친 이름은 두 줄 모두 짚는다', () => {
    const a = field('score', 'number')
    const b = field(' score', 'string')
    const issues = fieldIssues([a, b])
    expect([...issues.names].sort()).toEqual([a.id, b.id].sort())
    expect(outputDraftProblem(form(a, b))).toMatch(/두 번/)
  })

  it('숫자 필드에 숫자가 아닌 값을 짚는다', () => {
    const bad = field('score', 'number', 'high')
    expect([...fieldIssues([bad]).values]).toEqual([bad.id])
    expect(readOutputDraft(form(bad))).toBeNull()
  })

  it('61번째 키는 엔진 한도로 막는다', () => {
    const fields = Array.from({ length: 61 }, (_, i) => field(`k${i}`, 'number'))
    expect(outputDraftProblem(form(...fields))).toMatch(/60개/)
  })
})

describe('switchOutputMode', () => {
  it('폼에서 직접 입력으로 가면 적은 필드가 JSON 으로 옮겨진다', () => {
    const next = switchOutputMode(form(field('level', 'string', 'high')))
    expect(next?.form).toBe(false)
    expect(JSON.parse(next!.text)).toEqual({ level: 'high' })
  })

  it('한 단계짜리 JSON 만 폼으로 돌아간다', () => {
    const flat = switchOutputMode(raw('JSON', '{"score":3}'))
    expect(flat?.fields.map((it) => [it.name, it.kind, it.value])).toEqual([
      ['score', 'number', '3'],
    ])
    expect(switchOutputMode(raw('JSON', '{"meta":{"a":1}}'))).toBeNull()
    expect(switchOutputMode(raw('JSON', '{'))).toBeNull()
    expect(switchOutputMode(raw('JSON', ''))?.fields).toHaveLength(1)
  })

  it('fieldsFrom 은 앞뒤 공백이 있는 키를 폼으로 읽지 않는다', () => {
    expect(fieldsFrom({ ' a': 1 })).toBeNull()
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
