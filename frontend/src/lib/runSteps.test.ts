import { describe, expect, it } from 'vitest'

import type { StepStatus } from '@/api/executions'

import {
  branchTaken,
  cardStatuses,
  edgeWasTaken,
  inCanvasOrder,
  nameBranchSteps,
  reachedNodes,
  runFocus,
} from './runSteps'

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

  it('한 카드의 여러 내부 단계를 카드 하나에 묶는다', () => {
    const expanded = [
      step('m', 'succeeded'),
      step('t', 'succeeded'),
      step('t', 'running'),
      step('f', 'pending'),
      step('j', 'pending'),
      step('b1', 'pending'),
    ]
    expect(cardStatuses(cards, 1, expanded, cards)).toEqual(
      new Map([
        ['m', 'succeeded'],
        ['t', 'running'],
        ['f', 'pending'],
        ['j', 'pending'],
      ]),
    )
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

describe('branchTaken', () => {
  const edges = [
    { from: 'br', to: 't', when: true },
    { from: 'br', to: 'f', when: false },
  ]

  it('반대쪽이 건너뛰었으면 이쪽으로 갔다', () => {
    expect(branchTaken('br', edges, new Set(), new Map([['t', 'skipped' as const]]))).toBe(false)
  })

  it('한쪽이 지나갔으면 그쪽이다', () => {
    expect(branchTaken('br', edges, new Set(['t']), new Map())).toBe(true)
  })

  it('아직 모르면 null', () => {
    expect(branchTaken('br', edges, new Set(), new Map([['t', 'pending' as const]]))).toBeNull()
  })

  it.each([true, false])('합류점에 닿아도 %s 갈래의 판정과 선을 유지한다', (taken) => {
    const joined = [
      { from: 'br', to: 'work', when: taken },
      { from: 'br', to: 'join', when: !taken },
      { from: 'work', to: 'join' },
    ]
    const statuses = new Map<string, StepStatus>([
      ['work', 'succeeded'],
      ['join', 'running'],
    ])
    expect(branchTaken('br', joined, new Set(['br', 'work']), statuses)).toBe(taken)
    const reached = new Set(['br', 'work', 'join'])
    expect(branchTaken('br', joined, reached, statuses)).toBe(taken)
    expect(joined.map((edge) => edgeWasTaken(edge, joined, reached, statuses))).toEqual([
      true,
      false,
      true,
    ])
  })

  it('우회 작업이 skipped 면 합류점으로 바로 간 선만 켠다', () => {
    const joined = [
      { from: 'br', to: 'join', when: true },
      { from: 'br', to: 'work', when: false },
      { from: 'work', to: 'join' },
    ]
    const reached = new Set(['br', 'join'])
    const statuses = new Map<string, StepStatus>([
      ['work', 'skipped'],
      ['join', 'succeeded'],
    ])
    expect(branchTaken('br', joined, reached, statuses)).toBe(true)
    expect(joined.map((edge) => edgeWasTaken(edge, joined, reached, statuses))).toEqual([
      true,
      false,
      false,
    ])
  })

  it('합류점만 실행된 정보로는 갈래를 추측하지 않는다', () => {
    const joined = [
      { from: 'br', to: 'join', when: true },
      { from: 'br', to: 'work', when: false },
      { from: 'work', to: 'join' },
    ]
    const reached = new Set(['br', 'join'])
    expect(branchTaken('br', joined, reached, new Map())).toBeNull()
    expect(edgeWasTaken(joined[0], joined, reached, new Map())).toBe(false)
  })
})

describe('내부 단계의 카드 경계', () => {
  const expanded = [
    { name: '입력', status: 'succeeded' as const },
    { name: '내부 조사', status: 'succeeded' as const },
    { name: '내부 요약', status: 'pending' as const },
    { name: '결과', status: 'pending' as const },
    { name: 'b1', status: 'pending' as const },
  ]
  const ids = ['input', 'agent', 'result']
  const names = ['입력', '마켓 에이전트', '결과']
  const types = ['NODE', 'AGENT', 'NODE'] as const

  it('내부 이름이 달라도 기본 노드 사이의 에이전트 경계가 확실하면 묶는다', () => {
    expect(cardStatuses(ids, 1, expanded, names, [...types])).toEqual(
      new Map([
        ['input', 'succeeded'],
        ['agent', 'running'],
        ['result', 'pending'],
      ]),
    )
  })

  it('단계 배정이 여러 가지면 상태를 지어내지 않는다', () => {
    expect(cardStatuses(['a', 'b'], 1, expanded, ['첫째', '둘째'], ['AGENT', 'AGENT'])).toBeNull()
  })

  it('로그에서 내부 단계를 잃지 않고 분기를 제자리에 넣는다', () => {
    const nodes = [
      { id: 'input' },
      { id: 'agent' },
      { id: 'br', controlType: 'BRANCH' },
      { id: 'result' },
    ]
    expect(inCanvasOrder(nodes, ids, expanded, names, [...types]).map((step) => step.name)).toEqual(
      ['입력', '내부 조사', '내부 요약', 'b1', '결과'],
    )
  })
})

describe('runFocus', () => {
  const nodes = ['a', 'b', 'c', 'd', 'e', 'f'].map((id) => ({ id }))

  it('실행이 끝나면 건너뛴 카드를 제외한 마지막 완료 카드에 머문다', () => {
    expect(
      runFocus(
        nodes,
        new Map([
          ['a', 'succeeded'],
          ['e', 'succeeded'],
          ['f', 'skipped'],
        ]),
      ),
    ).toBe('e')
  })

  it('실패한 카드가 있으면 거기에 머문다', () => {
    expect(
      runFocus(
        nodes,
        new Map([
          ['a', 'succeeded'],
          ['d', 'failed'],
          ['e', 'pending'],
        ]),
      ),
    ).toBe('d')
  })

  it('다음 단계가 시작되기 전에도 직전 완료 카드에 머문다', () => {
    expect(
      runFocus(
        nodes,
        new Map([
          ['d', 'succeeded'],
          ['e', 'pending'],
        ]),
      ),
    ).toBe('d')
  })
})
