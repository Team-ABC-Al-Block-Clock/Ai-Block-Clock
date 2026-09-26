import { describe, expect, it } from 'vitest'

import {
  describeCondition,
  formatConditionRight,
  parseConditionRight,
  parseLiteral,
} from '@/lib/branchCondition'

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
