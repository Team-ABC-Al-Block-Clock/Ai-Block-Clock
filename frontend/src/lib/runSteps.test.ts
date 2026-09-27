import { describe, expect, it } from 'vitest'

import { nameBranchSteps } from './runSteps'

describe('nameBranchSteps', () => {
  const step = (name: string) => ({ name, status: 'succeeded' as const })

  it('끝에 붙은 분기에 캔버스 이름을 준다', () => {
    const named = nameBranchSteps([step('시장 조사'), step('차별화'), step('b1')], ['조건 분기'])
    expect(named.map((it) => it.name)).toEqual(['시장 조사', '차별화', '조건 분기'])
  })

  it('이름이 b1 인 작업 노드는 끝자리가 아니면 그대로 둔다', () => {
    const named = nameBranchSteps([step('b1'), step('요약'), step('b1')], ['조건 분기'])
    expect(named.map((it) => it.name)).toEqual(['b1', '요약', '조건 분기'])
  })

  it('끝자리가 분기 id 모양이 아니면 건드리지 않는다', () => {
    const steps = [step('시장 조사'), step('요약')]
    expect(nameBranchSteps(steps, ['조건 분기'])).toBe(steps)
  })

  it('분기가 둘이면 차례대로', () => {
    const named = nameBranchSteps([step('a'), step('b1'), step('b2')], ['첫 분기', '둘째 분기'])
    expect(named.map((it) => it.name)).toEqual(['a', '첫 분기', '둘째 분기'])
  })
})
