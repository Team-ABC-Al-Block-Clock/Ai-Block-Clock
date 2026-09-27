import { describe, expect, it } from 'vitest'

import type { StepStatus } from '@/api/executions'

import {
  branchTaken,
  cardStatuses,
  edgeWasTaken,
  inCanvasOrder,
  reachedNodes,
  runFocus,
  stepGroups,
  type RunCard,
} from './runSteps'

const step = (name: string, status: StepStatus = 'succeeded') => ({ name, status })
const node = (id: string): RunCard => ({ id, kind: 'NODE', name: id })
const names = (groups: ReturnType<typeof stepGroups>) =>
  groups && Object.fromEntries([...groups].map(([id, group]) => [id, group.map((it) => it.name)]))

describe('stepGroups', () => {
  const cards: RunCard[] = [node('m'), { id: 'br', kind: 'BRANCH', name: '조건 분기' }, node('t')]

  it('캔버스 차례에 분기가 제자리에 끼어도 짝짓는다', () => {
    expect(names(stepGroups(cards, [step('m'), step('b1'), step('t')]))).toEqual({
      m: ['m'],
      br: ['b1'],
      t: ['t'],
    })
  })

  it('예전 서버처럼 분기가 끝에 붙어도 짝짓는다', () => {
    expect(names(stepGroups(cards, [step('m'), step('t'), step('b1')]))).toEqual({
      m: ['m'],
      t: ['t'],
      br: ['b1'],
    })
  })

  it('이름이 b1 인 작업 노드와 분기를 헷갈리지 않는다', () => {
    const tricky: RunCard[] = [node('b1'), { id: 'br', kind: 'BRANCH', name: '분기' }]
    expect(names(stepGroups(tricky, [step('b1'), step('b1')]))).toEqual({
      b1: ['b1'],
      br: ['b1'],
    })
  })

  it('수나 이름이 어긋나면 짝짓지 않는다', () => {
    expect(stepGroups(cards, [step('m'), step('t')])).toBeNull()
    expect(stepGroups(cards, [step('m'), step('b1'), step('x')])).toBeNull()
    expect(stepGroups(cards, [])).toBeNull()
  })

  it('에이전트 속 분기는 에이전트 단계로 묶는다', () => {
    const agent: RunCard[] = [
      node('입력'),
      { id: 'ag', kind: 'AGENT', name: '마켓 에이전트' },
      { id: 'br', kind: 'BRANCH', name: '조건 분기' },
      node('결과'),
    ]
    const steps = [
      step('입력'),
      step('조사'),
      step('a1.b1'),
      step('요약'),
      step('b1'),
      step('결과'),
    ]
    expect(names(stepGroups(agent, steps))).toEqual({
      입력: ['입력'],
      ag: ['조사', 'a1.b1', '요약'],
      br: ['b1'],
      결과: ['결과'],
    })
  })

  it('붙어 있는 에이전트처럼 배정이 여러 가지면 null', () => {
    const agents: RunCard[] = [
      { id: 'a', kind: 'AGENT', name: '첫째' },
      { id: 'b', kind: 'AGENT', name: '둘째' },
    ]
    expect(stepGroups(agents, [step('x'), step('y'), step('z')])).toBeNull()
  })

  it.each(['canvas', 'legacy'])(
    '에이전트 경계가 모호해도 분기와 뒤 노드는 보존한다 (%s)',
    (order) => {
      const cards: RunCard[] = [
        { id: 'a', kind: 'AGENT', name: '첫째' },
        { id: 'b', kind: 'AGENT', name: '둘째' },
        { id: 'br', kind: 'BRANCH', name: '분기' },
        node('t'),
        node('f'),
      ]
      const work = [step('x'), step('y'), step('z')]
      const tail = [step('t'), step('f', 'skipped')]
      const steps =
        order === 'canvas' ? [...work, step('b1'), ...tail] : [...work, ...tail, step('b1')]
      const groups = stepGroups(cards, steps)!
      expect(groups.has('a')).toBe(false)
      expect(groups.has('b')).toBe(false)
      const statuses = cardStatuses(groups)
      expect(statuses.get('br')).toBe('succeeded')
      expect(statuses.get('t')).toBe('succeeded')
      expect(statuses.get('f')).toBe('skipped')
      const edges = [
        { from: 'br', to: 't', when: true },
        { from: 'br', to: 'f', when: false },
      ]
      expect(branchTaken('br', edges, reachedNodes(cards, statuses, true), statuses)).toBe(true)
    },
  )

  it('여러 경계 후보 중 처음 두 개가 같아도 확정하지 않는다', () => {
    const cards: RunCard[] = [
      { id: 'a', kind: 'AGENT', name: '첫째' },
      { id: 'b', kind: 'AGENT', name: '둘째' },
      { id: 'c', kind: 'AGENT', name: '셋째' },
      node('end'),
    ]
    expect(
      names(
        stepGroups(
          cards,
          ['v', 'w', 'x', 'y', 'z', 'end'].map((name) => step(name)),
        ),
      ),
    ).toEqual({ end: ['end'] })
  })
})

describe('cardStatuses · reachedNodes', () => {
  const cards: RunCard[] = [
    node('m'),
    { id: 'br', kind: 'BRANCH', name: '분기' },
    node('t'),
    node('f'),
    node('j'),
  ]
  const steps = [
    step('m'),
    step('b1'),
    step('t', 'skipped'),
    step('f', 'running'),
    step('j', 'pending'),
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

  it('카드마다 제 단계 상태를 준다', () => {
    expect([...cardStatuses(stepGroups(cards, steps)!)]).toEqual([
      ['m', 'succeeded'],
      ['br', 'succeeded'],
      ['t', 'skipped'],
      ['f', 'running'],
      ['j', 'pending'],
    ])
  })

  it('한 카드의 여러 내부 단계를 하나로 합친다', () => {
    const groups = new Map([
      ['a', [step('x'), step('y', 'running')]],
      ['b', [step('x', 'skipped'), step('y', 'succeeded')]],
      ['c', [step('x', 'skipped')]],
      ['d', [step('x'), step('y', 'failed')]],
    ])
    expect([...cardStatuses(groups)]).toEqual([
      ['a', 'running'],
      ['b', 'succeeded'],
      ['c', 'skipped'],
      ['d', 'failed'],
    ])
  })

  it('가지 않은 갈래와 아직 안 간 자리는 빼고 지나간 자리만 센다', () => {
    const statuses = cardStatuses(stepGroups(cards, steps)!)
    expect([...reachedNodes(nodes, statuses, false)]).toEqual(['s', 'm', 'br', 'f'])
  })

  it('끝나면 종료도 지나간 자리다', () => {
    const done = steps.map((it) =>
      it.status === 'skipped' ? it : { ...it, status: 'succeeded' as const },
    )
    const statuses = cardStatuses(stepGroups(cards, done)!)
    expect(reachedNodes(nodes, statuses, true).has('e')).toBe(true)
  })
})

describe('inCanvasOrder', () => {
  it('끝에 붙은 분기를 제 자리로 옮기고 내부 단계를 잃지 않는다', () => {
    const cards: RunCard[] = [
      node('m'),
      { id: 'ag', kind: 'AGENT', name: '에이전트' },
      { id: 'br', kind: 'BRANCH', name: '분기' },
      node('t'),
    ]
    const steps = [step('m'), step('조사'), step('요약'), step('t'), step('b1')]
    expect(inCanvasOrder(cards, stepGroups(cards, steps)!).map((it) => it.name)).toEqual([
      'm',
      '조사',
      '요약',
      'b1',
      't',
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
