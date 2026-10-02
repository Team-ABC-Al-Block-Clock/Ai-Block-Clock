import { describe, expect, it } from 'vitest'

import type { CanvasNodeData } from './canvasWorkflow'

import {
  conditionProblem,
  operatorsFor,
  describeCondition,
  formatConditionRight,
  parseConditionRight,
  parseLiteral,
} from '@/lib/canvas/branchCondition'

describe('parseLiteral', () => {
  it('숫자로 읽히는 글자는 숫자다', () => {
    expect(parseLiteral('12')).toBe(12)
    expect(parseLiteral(' -3.5 ')).toBe(-3.5)
  })

  it('true · false 는 불리언이다', () => {
    expect(parseLiteral('true')).toBe(true)
    expect(parseLiteral('false')).toBe(false)
  })

  // 등급 이름이 "true" 일 수 있다. 따옴표로 감싸면 글자 그대로 간다.
  it('따옴표로 감싸면 글자 그대로다', () => {
    expect(parseLiteral('"true"')).toBe('true')
    expect(parseLiteral('"12"')).toBe('12')
  })

  it('그 밖에는 글자다', () => {
    expect(parseLiteral('high')).toBe('high')
  })

  // `Number('')` 이 0 이라 빈 칸이 0 으로 샐 수 있다.
  it('빈 칸은 0 이 아니다', () => {
    expect(parseLiteral('   ')).toBe('')
  })
})

describe('parseConditionRight', () => {
  it('IN 은 쉼표로 나눠 배열이다', () => {
    expect(parseConditionRight('high, mid , 3', 'IN')).toEqual(['high', 'mid', 3])
  })

  it('IN 이 아니면 값 하나다', () => {
    expect(parseConditionRight('high', 'EQ')).toBe('high')
  })

  it('둘째 이후 값의 따옴표 안 쉼표를 보존한다', () => {
    expect(parseConditionRight('high, "a,b", "c,d"', 'IN')).toEqual(['high', 'a,b', 'c,d'])
    expect(parseConditionRight('high, "a,b"', 'IN', 'string')).toEqual(['high', 'a,b'])
  })

  it('따옴표·역슬래시·빈 문자열도 재적용할 때 보존한다', () => {
    const values = ['high', 'a"b,c', 'a\\b', '', ' true ', '줄\n바꿈']
    expect(parseConditionRight(formatConditionRight(values), 'IN')).toEqual(values)
    expect(parseConditionRight(formatConditionRight(values), 'IN', 'string')).toEqual(values)
  })

  it.each(['high, "a,b', 'high,,low', 'high,'])('미완성 목록을 저장하지 않는다 — %s', (text) => {
    expect(parseConditionRight(text, 'IN')).toBeNull()
  })

  it('채운 것이 없으면 null 이다', () => {
    expect(parseConditionRight('  ', 'EQ')).toBeNull()
    expect(parseConditionRight(' , , ', 'IN')).toBeNull()
  })
})

describe('formatConditionRight', () => {
  it('배열은 쉼표로 이어 되돌린다', () => {
    expect(formatConditionRight(['high', 3])).toBe('high, 3')
  })

  it('값 하나는 그대로다', () => {
    expect(formatConditionRight(true)).toBe('true')
  })

  /**
   * 되읽어 같은 값이 나와야 한다. 따옴표를 잃으면 등급 이름 `"true"` 가 다음 「적용」에서
   * 불리언으로 씻겨 나간다.
   */
  it('글자로 남아야 하는 값은 따옴표째 되돌린다', () => {
    expect(formatConditionRight('true')).toBe('"true"')
    expect(formatConditionRight('12')).toBe('"12"')
    expect(formatConditionRight('high')).toBe('high')
  })

  // 쉼표가 든 값은 `IN` 이 되읽을 때 두 값으로 갈린다.
  it('쉼표가 든 값도 한 값으로 지킨다', () => {
    expect(formatConditionRight(['a,b', 'c'])).toBe('"a,b", c')
    expect(parseConditionRight(formatConditionRight(['a,b', 'c']), 'IN')).toEqual(['a,b', 'c'])
  })

  it('되돌린 글자를 다시 읽으면 처음 값이다', () => {
    for (const value of ['true', '12', 'high', '', '-3.5']) {
      expect(parseLiteral(formatConditionRight(value))).toBe(value)
    }
  })
})

describe('describeCondition', () => {
  const nameOf = (id: string) => (id === 'n1' ? '내용 분석' : undefined)

  it('읽는 노드와 비교를 한 줄로 적는다', () => {
    const at = describeCondition(
      { fromId: 'n1', path: 'competition_level', operator: 'EQ', right: 'high' },
      nameOf,
    )
    expect(at).toBe('내용 분석.competition_level = high')
  })

  it('조건이 없으면 없다고 적는다', () => {
    expect(describeCondition(null, nameOf)).toBe('조건 없음')
  })

  // 조건이 가리키던 노드를 지워도 카드가 빈칸으로 남지 않게 한다.
  it('가리키는 노드가 사라져도 자리를 지킨다', () => {
    const at = describeCondition({ fromId: 'zzz', path: 'x', operator: 'GT', right: 3 }, nameOf)
    expect(at).toBe('알 수 없는 노드.x > 3')
  })
})

describe('칸의 종류를 알 때', () => {
  it('비교는 종류마다 엔진이 받는 것만', () => {
    expect(operatorsFor('string').map((it) => it.value)).toEqual(['EQ', 'NE', 'IN'])
    expect(operatorsFor('boolean').map((it) => it.value)).toEqual(['EQ', 'NE'])
    expect(operatorsFor(null)).toHaveLength(7)
  })

  it('글자 칸에 적은 숫자는 글자로 읽는다', () => {
    expect(parseConditionRight('12', 'EQ', 'string')).toBe('12')
    expect(parseConditionRight('12', 'EQ', 'number')).toBe(12)
    expect(parseConditionRight('a, 3', 'IN', 'string')).toEqual(['a', '3'])
  })

  it('종류에 맞지 않는 값은 받지 않는다', () => {
    expect(parseConditionRight('high', 'EQ', 'number')).toBeNull()
    expect(parseConditionRight('yes', 'EQ', 'boolean')).toBeNull()
    expect(parseConditionRight('1, x', 'IN', 'number')).toBeNull()
  })
})

describe('conditionProblem', () => {
  const source = (output: CanvasNodeData['output'], sourceType: 'NODE' | 'AGENT' = 'NODE') =>
    ({ id: 'a', sourceType, output }) as CanvasNodeData
  const condition = (path: string, operator: 'EQ' | 'GT' = 'EQ') => ({
    fromId: 'a',
    path,
    operator,
    right: 'high',
  })
  const json = { kind: 'JSON' as const, example: { level: 'high' } }

  it('글 출력 노드는 읽을 수 없다', () => {
    expect(conditionProblem(source({ kind: 'TEXT', example: null }), condition('level'))).toMatch(
      /JSON/,
    )
  })

  it('예시에 없는 칸', () => {
    expect(conditionProblem(source(json), condition('score'))).toMatch(/출력 예시에 없습니다/)
  })

  it('종류에 맞지 않는 비교', () => {
    expect(conditionProblem(source(json), condition('level', 'GT'))).toMatch(/비교 방법/)
  })

  it('맞으면 없다. 에이전트는 몰라서 보지 않는다', () => {
    expect(conditionProblem(source(json), condition('level'))).toBeNull()
    expect(conditionProblem(source(null, 'AGENT'), condition('whatever'))).toBeNull()
  })

  it('출력 예시가 숫자로 바뀌면 남아 있는 문자열 비교를 막는다', () => {
    const changed = source({ kind: 'JSON', example: { level: 3 } })
    expect(conditionProblem(changed, condition('level'))).toMatch(/비교 값을 다시 입력/)
    expect(conditionProblem(changed, { ...condition('level'), right: 3 })).toBeNull()
  })

  it('IN 의 모든 값이 출력 예시의 종류와 맞아야 한다', () => {
    const changed = source({ kind: 'JSON', example: { level: 3 } })
    expect(
      conditionProblem(changed, { ...condition('level'), operator: 'IN', right: [3, '4'] }),
    ).toMatch(/비교 값을 다시 입력/)
    expect(
      conditionProblem(changed, { ...condition('level'), operator: 'IN', right: [3, 4] }),
    ).toBeNull()
  })

  it('불리언 예시에 문자열 true 를 비교하지 않는다', () => {
    const changed = source({ kind: 'JSON', example: { level: true } })
    expect(conditionProblem(changed, { ...condition('level'), right: 'true' })).toMatch(
      /비교 값을 다시 입력/,
    )
    expect(conditionProblem(changed, { ...condition('level'), right: true })).toBeNull()
  })
})
