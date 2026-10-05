import { describe, expect, it } from 'vitest'

import { ApiError } from '@/api/errors'

import { describeRegisterError } from './registerError'

const apiError = (code: string, data?: unknown) => new ApiError('x', { code, status: 400, data })

describe('describeRegisterError', () => {
  it('오류가 없으면 아무 말도 하지 않는다', () => {
    expect(describeRegisterError(null)).toBeNull()
  })

  it('서버가 짚은 칸을 화면 이름으로 바꿔 알린다', () => {
    expect(describeRegisterError(apiError('COMMON_001', { name: 'x', revision: 'y' }))).toBe(
      '이름 · 워크플로우 회차 을(를) 확인해 주세요',
    )
  })

  it('화면에 없는 칸은 서버가 준 이름 그대로 알린다', () => {
    expect(describeRegisterError(apiError('COMMON_001', { price: 'x' }))).toBe(
      'price 을(를) 확인해 주세요',
    )
  })

  it('짚은 칸이 없으면 워크플로우를 다시 열게 한다', () => {
    expect(describeRegisterError(apiError('COMMON_001'))).toBe(
      '보낸 값이 올바르지 않습니다. 워크플로우를 다시 열어 주세요',
    )
  })

  it('다시 눌러도 통과하지 않는 코드는 문구를 가른다', () => {
    expect(describeRegisterError(apiError('WORKFLOW_016'))).toContain('다시 열어')
    expect(describeRegisterError(apiError('AGENT_002'))).toContain('다른 사람의 노드')
    expect(describeRegisterError(apiError('AGENT_001'))).toContain('노드가 없는')
  })

  it('모르는 실패는 잠시 후 다시 시도하게 한다', () => {
    expect(describeRegisterError(new Error('boom'))).toBe(
      '등록에 실패했습니다. 잠시 후 다시 시도해 주세요',
    )
  })
})
