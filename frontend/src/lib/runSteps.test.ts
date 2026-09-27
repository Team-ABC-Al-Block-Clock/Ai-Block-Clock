import { describe, expect, it } from 'vitest'

import type { StepStatus } from '@/api/executions'

import { cardStatuses, inCanvasOrder, nameBranchSteps, reachedNodes } from './runSteps'

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

describe('cardStatuses · reachedNodes', () => {
  const step = (name: string, status: StepStatus) => ({ name, status })
  const cards = ['m', 't', 'f', 'j']
  const steps = [
    step('m', 'succeeded'),
    step('t', 'skipped'),
    step('f', 'running'),
    step('j', 'pending'),
    step('b1', 'succeeded'),
  ]
  const nodes = [
    { id: 's', ioType: 'START' },
    { id: 'm' },
    { id: 'br', controlType: 'BRANCH' },
    { id: 't' },
    { id: 'f' },
    { id: 'j' },
    { id: 'e', ioType: 'END' },
  ]

  it('분기가 뒤에 붙어도 작업 카드와 짝짓는다', () => {
    expect([...cardStatuses(cards, 1, steps)!]).toEqual([
      ['m', 'succeeded'],
      ['t', 'skipped'],
      ['f', 'running'],
      ['j', 'pending'],
    ])
  })

  it('수가 어긋나면 짝짓지 않는다', () => {
    expect(cardStatuses(cards, 0, steps)).toBeNull()
    expect(cardStatuses(cards, 1, [])).toBeNull()
  })

  it('가지 않은 갈래와 아직 안 간 자리는 빼고 지나간 자리만 센다', () => {
    const statuses = cardStatuses(cards, 1, steps)!
    expect([...reachedNodes(nodes, statuses, steps.slice(4), false)]).toEqual(['s', 'm', 'br', 'f'])
  })

  it('끝나면 종료도 지나간 자리다', () => {
    const done = steps.map((it) =>
      it.status === 'skipped' ? it : { ...it, status: 'succeeded' as const },
    )
    const statuses = cardStatuses(cards, 1, done)!
    expect(reachedNodes(nodes, statuses, done.slice(4), true).has('e')).toBe(true)
  })
})

describe('inCanvasOrder', () => {
  it('끝에 붙은 분기를 제 자리로 옮긴다', () => {
    const steps = ['m', 't', 'f', 'b1'].map((name) => ({ name, status: 'succeeded' as const }))
    const nodes = [
      { id: 's' },
      { id: 'm' },
      { id: 'br', controlType: 'BRANCH' },
      { id: 't' },
      { id: 'f' },
    ]
    expect(inCanvasOrder(nodes, ['m', 't', 'f'], steps).map((it) => it.name)).toEqual([
      'm',
      'b1',
      't',
      'f',
    ])
  })
})
