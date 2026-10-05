import { describe, expect, it } from 'vitest'

import { PROFILE_LIMITS } from '@/api/auth'

import { introProblem, nicknameProblem } from './profileRules'

const { nickname: nickLimit, introduction: introLimit } = PROFILE_LIMITS

describe('nicknameProblem', () => {
  it('범위 안이면 통과한다', () => {
    expect(nicknameProblem('a'.repeat(nickLimit.min))).toBeNull()
    expect(nicknameProblem('a'.repeat(nickLimit.max))).toBeNull()
  })

  it('짧거나 길면 범위를 알려 준다', () => {
    const message = `이름은 ${nickLimit.min}~${nickLimit.max}자여야 합니다`
    expect(nicknameProblem('a'.repeat(nickLimit.min - 1))).toBe(message)
    expect(nicknameProblem('a'.repeat(nickLimit.max + 1))).toBe(message)
  })
})

describe('introProblem', () => {
  it('비어 있어도 통과한다', () => {
    expect(introProblem('')).toBeNull()
    expect(introProblem('a'.repeat(introLimit.max))).toBeNull()
  })

  it('넘치면 한도를 알려 준다', () => {
    expect(introProblem('a'.repeat(introLimit.max + 1))).toBe(
      `한 줄 소개는 ${introLimit.max}자까지입니다`,
    )
  })
})
