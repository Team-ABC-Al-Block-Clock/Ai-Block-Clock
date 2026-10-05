import { describe, expect, it } from 'vitest'

import type { CanvasCondition } from '@/lib/canvas/canvasWorkflow'

import { branchFormState, type BranchInput, type ConditionSource } from './branchFormState'

const scored: ConditionSource = {
  id: 'n1',
  name: '점수 매기기',
  fields: [
    { path: 'level', kind: 'string' },
    { path: 'score', kind: 'number' },
  ],
}
const agent: ConditionSource = { id: 'a1', name: '분류 에이전트', fields: null }

const input = (over: Partial<BranchInput> = {}): BranchInput => ({
  fromId: 'n1',
  path: 'level',
  operator: 'EQ',
  right: 'high',
  ...over,
})

const saved: CanvasCondition = { fromId: 'n1', path: 'level', operator: 'EQ', right: 'high' }

describe('branchFormState', () => {
  it('후보 안의 값이면 그대로 쓰고 적용할 수 있다', () => {
    const state = branchFormState(input(), [scored], saved)
    expect(state.picked).toBe('n1')
    expect(state.pickedPath).toBe('level')
    expect(state.canApply).toBe(true)
    expect(state.drifted).toBe(false)
  })

  it('고른 노드가 비어 있으면 첫 후보로 보고 적용을 살려 둔다', () => {
    const state = branchFormState(input({ fromId: '' }), [scored], null)
    expect(state.picked).toBe('n1')
    expect(state.canApply).toBe(true)
  })

  it('후보에 없는 칸은 첫 칸으로 본다', () => {
    const state = branchFormState(input({ path: 'gone' }), [scored], null)
    expect(state.pickedPath).toBe('level')
  })

  it('칸 종류가 받지 않는 비교는 그 종류의 첫 비교로 바꾼다', () => {
    const state = branchFormState(input({ path: 'level', operator: 'GT' }), [scored], null)
    expect(state.operators.map((it) => it.value)).not.toContain('GT')
    expect(state.pickedOperator).toBe(state.operators[0].value)
  })

  it('출력 형식을 모르는 노드는 경로를 손으로 적고, 비면 적용할 수 없다', () => {
    expect(branchFormState(input({ fromId: 'a1', path: '  ' }), [agent], null).canApply).toBe(false)
    expect(branchFormState(input({ fromId: 'a1', path: ' x.y ' }), [agent], null).pickedPath).toBe(
      'x.y',
    )
  })

  it('값의 종류가 칸과 맞지 않으면 적용할 수 없다', () => {
    const state = branchFormState(input({ path: 'score', right: '높음' }), [scored], null)
    expect(state.parsed).toBeNull()
    expect(state.canApply).toBe(false)
  })

  it('저장된 조건이 지금 후보 밖이면 달라졌다고 알린다', () => {
    const state = branchFormState(input({ fromId: 'gone' }), [scored], { ...saved, fromId: 'gone' })
    expect(state.drifted).toBe(true)
  })

  it('저장된 조건이 없으면 달라졌다고 하지 않는다', () => {
    expect(branchFormState(input({ fromId: 'gone' }), [scored], null).drifted).toBe(false)
  })
})
