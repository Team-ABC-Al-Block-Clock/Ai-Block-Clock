import { describe, expect, it } from 'vitest'

import { ApiError } from '@/api/errors'

import { resultOf, resultProblem } from './runResult'

describe('resultOf', () => {
  it('글 출력은 마크다운으로 읽고 원문을 복사한다', () => {
    const result = resultOf({ schema_id: 'text', payload: { text: '## 결과\n본문' } })

    expect(result?.text).toBe('## 결과\n본문')
    expect(result?.blocks).toEqual([
      { type: 'heading', level: 2, text: '결과' },
      { type: 'paragraph', text: '본문' },
    ])
  })

  it('JSON 출력은 PDF 와 같은 문서로 펴고 그 글을 복사한다', () => {
    const result = resultOf({ schema_id: 'json', payload: { level: 'high' } })

    expect(result?.text).toBe('## level\n\nhigh')
    expect(result?.blocks[0]).toEqual({ type: 'heading', level: 2, text: 'level' })
  })

  // 판매자가 예시에 `text` 칸 하나만 둔 JSON 과 글을 가를 길은 모양뿐이다. 칸이 더 있으면 JSON 이다.
  it('text 말고 칸이 더 있으면 JSON 으로 본다', () => {
    expect(resultOf({ payload: { text: 'a', score: 1 } })?.text).toContain('## score')
  })

  it('봉투가 아니면 null', () => {
    expect(resultOf(null)).toBeNull()
    expect(resultOf({ payload: 'x' })).toBeNull()
  })
})

describe('resultProblem', () => {
  const state = (over: Partial<Parameters<typeof resultProblem>[0]>) => ({
    isPending: false,
    error: null,
    data: undefined,
    ...over,
  })
  const apiError = (code: string, status: number) => new ApiError('x', { code, status })

  it('받는 중', () => {
    expect(resultProblem(state({ isPending: true }))).toMatch(/받는 중/)
  })

  it('실패한 실행과 준비 안 된 실행을 가른다', () => {
    expect(resultProblem(state({ error: apiError('EXECUTION_008', 404) }))).toMatch(/실패로 끝난/)
    expect(resultProblem(state({ error: apiError('EXECUTION_001', 409) }))).toMatch(/준비되지/)
    expect(resultProblem(state({ error: apiError('EXECUTION_001', 404) }))).toMatch(/받지 못했/)
  })

  it('봉투에 알맹이가 없으면 비었다고 한다', () => {
    expect(resultProblem(state({ data: null }))).toMatch(/비어/)
  })

  it('그릴 결과가 있으면 없다', () => {
    expect(resultProblem(state({ data: { blocks: [], text: '' } }))).toBeNull()
  })
})
