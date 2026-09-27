import type { ExecutionStep, StepStatus } from '@/api/executions'

/**
 * 분기 단계에 캔버스 이름을 붙인다.
 *
 * 서버는 분기에 이름을 싣지 않아 엔진이 id(`b1`)를 이름으로 돌려준다. 분기는 작업 단계 뒤에
 * 캔버스 차례대로 붙는다(`RuntimeWorkflow.of`). 끝자리이면서 id 모양일 때만 바꿔, 이름이
 * `b1` 인 작업 노드와 섞이지 않게 한다.
 */
export function nameBranchSteps(steps: ExecutionStep[], branchNames: string[]): ExecutionStep[] {
  const offset = steps.length - branchNames.length
  if (branchNames.length === 0 || offset < 0) return steps
  const tail = steps.slice(offset)
  if (!tail.every((step, i) => step.name === `b${i + 1}`)) return steps
  return [...steps.slice(0, offset), ...tail.map((step, i) => ({ ...step, name: branchNames[i] }))]
}

type CardType = 'NODE' | 'AGENT'

/** 작업 단계가 각 카드에 속하는 유일한 구간을 찾는다. 이름이나 경계가 모호하면 추측하지 않는다. */
function workGroups(
  cardIds: string[],
  branchCount: number,
  steps: ExecutionStep[],
  cardNames: string[],
  cardTypes: CardType[],
): ExecutionStep[][] | null {
  const count = steps.length - branchCount
  if (cardIds.length === 0 || count < cardIds.length) return null
  const work = steps.slice(0, count)
  if (count === cardIds.length) return work.map((step) => [step])
  if (cardNames.length !== cardIds.length) return null

  // 기본 노드는 한 단계이고, 에이전트는 여러 단계를 차지한다. 기본 노드의 이름으로
  // 경계를 찾되, 같은 이름이나 인접한 에이전트 때문에 두 가지 배정이 가능하면 중단한다.
  const memo = new Map<string, number[][]>()
  const partitions = (card: number, start: number): number[][] => {
    if (card === cardIds.length) return start === count ? [[]] : []
    const key = `${card}:${start}`
    const cached = memo.get(key)
    if (cached) return cached
    const results: number[][] = []
    const last = cardTypes[card] === 'NODE' ? start + 1 : count - (cardIds.length - card - 1)
    for (let end = start + 1; end <= last; end++) {
      if (cardTypes[card] !== 'AGENT' && work[end - 1]?.name !== cardNames[card]) break
      for (const tail of partitions(card + 1, end)) {
        results.push([end, ...tail])
        if (results.length === 2) break
      }
      if (results.length === 2) break
    }
    memo.set(key, results)
    return results
  }
  const choices = partitions(0, 0)
  if (choices.length !== 1) return null
  let start = 0
  return choices[0].map((end) => {
    const group = work.slice(start, end)
    start = end
    return group
  })
}

/** 작업 카드마다 내부 단계의 상태를 합친다. 분기 단계는 응답의 끝에 붙는다. */
export function cardStatuses(
  cardIds: string[],
  branchCount: number,
  steps: ExecutionStep[],
  cardNames: string[] = [],
  cardTypes: CardType[] = [],
): Map<string, StepStatus> | null {
  const groups = workGroups(cardIds, branchCount, steps, cardNames, cardTypes)
  if (!groups) return null
  return new Map(
    cardIds.map((id, i) => {
      const statuses = groups[i].map((step) => step.status)
      const status: StepStatus = statuses.includes('failed')
        ? 'failed'
        : statuses.every((status) => status === 'skipped')
          ? 'skipped'
          : statuses.every((status) => status === 'succeeded' || status === 'skipped')
            ? 'succeeded'
            : statuses.some((status) => status === 'running' || status === 'succeeded')
              ? 'running'
              : 'pending'
      return [id, status]
    }),
  )
}

const REACHED: StepStatus[] = ['running', 'succeeded', 'failed']

/**
 * 실행이 지나간 자리. 선은 양 끝이 다 지나간 것만 칠한다 — 가지 않은 갈래와 그리로 가는 선은 꺼진 채로 둔다.
 *
 * 분기는 뒤에 붙은 제 단계로, 시작은 첫 단계가 움직였는지로, 종료는 실행이 끝났는지로 본다.
 */
export function reachedNodes(
  nodes: { id: string; controlType?: unknown; ioType?: string | null }[],
  statuses: Map<string, StepStatus>,
  branchSteps: ExecutionStep[],
  finished: boolean,
): Set<string> {
  const reached = new Set<string>()
  const started = [...statuses.values()].some((status) => status !== 'pending')
  let branch = 0
  for (const node of nodes) {
    const hit = node.controlType
      ? REACHED.includes(branchSteps[branch++]?.status)
      : node.ioType === 'START'
        ? started || finished
        : node.ioType === 'END'
          ? finished
          : REACHED.includes(statuses.get(node.id) ?? 'pending')
    if (hit) reached.add(node.id)
  }
  return reached
}

/** 단계를 캔버스 차례로. 서버는 분기를 끝에 붙여 보내 로그에서 분기가 맨 뒤에 찍힌다. */
export function inCanvasOrder(
  nodes: { id: string; controlType?: unknown }[],
  cardIds: string[],
  steps: ExecutionStep[],
  cardNames: string[] = [],
  cardTypes: CardType[] = [],
): ExecutionStep[] {
  const branchCount = nodes.filter((node) => node.controlType).length
  const groups = workGroups(cardIds, branchCount, steps, cardNames, cardTypes)
  if (!groups) return steps
  const work = new Map(cardIds.map((id, i) => [id, groups[i]]))
  let branch = steps.length - branchCount
  return nodes.flatMap((node) => {
    if (!node.controlType) return work.get(node.id) ?? []
    const step = steps[branch++]
    return step ? [step] : []
  })
}

/**
 * 분기가 어느 쪽으로 갔는지. 아직 모르면 `null`.
 *
 * 서버는 판정 값을 넘기지 않아 갈래 끝의 상태로 읽는다 — 한쪽이 지나갔거나 반대쪽이 건너뛰었으면 그쪽이다.
 */
export function branchTaken(
  branchId: string,
  edges: { from: string; to: string; when?: boolean | null }[],
  reached: Set<string>,
  statuses: Map<string, StepStatus>,
): boolean | null {
  const target = (when: boolean) => edges.find((it) => it.from === branchId && it.when === when)?.to
  const [yes, no] = [target(true), target(false)]
  if (!yes || !no) return null
  const downstream = (from: string) => {
    const seen = new Set<string>()
    const pending = [from]
    while (pending.length) {
      const id = pending.pop()!
      if (seen.has(id)) continue
      seen.add(id)
      pending.push(...edges.filter((edge) => edge.from === id).map((edge) => edge.to))
    }
    return seen
  }
  // 상대 갈래에서도 닿는 대상은 합류점이다. 그곳의 성공은 어느 갈래의 증거도 아니다.
  const yesOnly = !downstream(no).has(yes)
  const noOnly = !downstream(yes).has(no)
  const tookYes = (yesOnly && reached.has(yes)) || (noOnly && statuses.get(no) === 'skipped')
  const tookNo = (noOnly && reached.has(no)) || (yesOnly && statuses.get(yes) === 'skipped')
  return tookYes === tookNo ? null : tookYes
}

/** 합류점이 켜져 있어도 선택되지 않은 분기 선은 켜지지 않는다. */
export function edgeWasTaken(
  edge: { from: string; to: string; when?: boolean | null },
  edges: { from: string; to: string; when?: boolean | null }[],
  reached: Set<string>,
  statuses: Map<string, StepStatus>,
): boolean {
  if (!reached.has(edge.from) || !reached.has(edge.to)) return false
  return edge.when == null || branchTaken(edge.from, edges, reached, statuses) === edge.when
}

/** 실행이 멈춰도 실패한 카드 또는 마지막으로 완료된 카드에 카메라를 둔다. */
export function runFocus(
  nodes: { id: string }[],
  statuses: Map<string, StepStatus>,
): string | undefined {
  return (
    nodes.find((node) => statuses.get(node.id) === 'failed') ??
    nodes.find((node) => statuses.get(node.id) === 'running') ??
    nodes.findLast((node) => statuses.get(node.id) === 'succeeded')
  )?.id
}
