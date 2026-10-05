import { describe, expect, it } from 'vitest'

import type { ExecutionProgress } from '@/api/executions'

import { runHeadline } from './runHeadline'

const progress = (over: Partial<ExecutionProgress> = {}): ExecutionProgress => ({
  executionId: 'exe_1',
  status: 'RUNNING',
  done: 1,
  total: 3,
  percent: 33,
  currentStepName: 'n2',
  steps: [],
  ...over,
})

const base = {
  missing: false,
  lost: false,
  failed: false,
  finished: false,
  progress: undefined,
  currentLabel: undefined,
  activeName: undefined,
  runnableCount: 0,
}

describe('runHeadline', () => {
  it('진행을 받기 전에는 짐작한 카드와 작업 단계 수를 보인다', () => {
    expect(runHeadline({ ...base, activeName: '요약', runnableCount: 3 })).toEqual({
      stepTitle: '요약 중',
      stepMeta: '0 / 3 단계',
    })
    expect(runHeadline(base)).toEqual({ stepTitle: '워크플로우를 불러오는 중', stepMeta: '' })
  })

  it('받았으면 도는 단계의 화면 이름과 서버가 센 단계 수를 쓴다', () => {
    expect(runHeadline({ ...base, progress: progress(), currentLabel: '요약' })).toEqual({
      stepTitle: '요약',
      stepMeta: '1 / 3 단계',
    })
  })

  it('도는 단계가 없으면 준비 중인지 정리 중인지 가른다', () => {
    const idle = { ...base, currentLabel: undefined }
    expect(
      runHeadline({ ...idle, progress: progress({ currentStepName: null, done: 0 }) }).stepTitle,
    ).toBe('실행을 준비하는 중')
    expect(
      runHeadline({ ...idle, progress: progress({ currentStepName: null, done: 3 }) }).stepTitle,
    ).toBe('결과를 정리하는 중')
  })

  it('찾지 못함 · 끊김 · 실패 · 완료가 진행보다 앞선다', () => {
    const p = progress()
    expect(runHeadline({ ...base, progress: p, missing: true }).stepTitle).toBe(
      '실행을 찾을 수 없습니다',
    )
    expect(runHeadline({ ...base, progress: p, lost: true })).toEqual({
      stepTitle: '진행 상황을 알 수 없습니다',
      stepMeta: '',
    })
    expect(runHeadline({ ...base, progress: p, failed: true }).stepTitle).toBe(
      '실행이 중단되었습니다',
    )
    expect(runHeadline({ ...base, progress: p, finished: true }).stepTitle).toBe('실행 완료')
  })
})
