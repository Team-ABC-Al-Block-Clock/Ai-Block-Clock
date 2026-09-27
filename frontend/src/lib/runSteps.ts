import type { ExecutionStep, StepStatus } from '@/api/executions'

/** 실행 화면의 카드 하나. 시작·종료는 단계가 없어 싣지 않는다. */
export interface RunCard {
  id: string
  kind: 'NODE' | 'AGENT' | 'BRANCH'
  name: string
}

/** 캔버스 분기의 단계 이름. 서버가 분기에 이름을 싣지 않아 엔진이 id 를 이름으로 돌려준다. */
const TOP_BRANCH = /^b\d+$/

/**
 * 카드마다 제 단계를 나눠 준다. 배정이 하나로 떨어지지 않으면 `null` — 추측해 켜지 않는다.
 *
 * 서버는 캔버스 차례대로 단계를 싣고 분기를 제자리에 끼운다(`PlanLevel`). 기본 노드는 제 이름 한 단계,
 * 분기는 `b1` 같은 한 단계, 에이전트는 속 단계 여럿이다. 예전 서버는 분기를 끝에 붙였다 — 그 차례도 받는다.
 */
export function stepGroups(
  cards: RunCard[],
  steps: ExecutionStep[],
): Map<string, ExecutionStep[]> | null {
  if (cards.length === 0 || steps.length === 0) return null
  const branchOf = new Map(
    cards.filter((card) => card.kind === 'BRANCH').map((card, i) => [card.id, `b${i + 1}`]),
  )
  const inCanvas = partition(cards, steps, branchOf)
  if (inCanvas) return inCanvas
  const tailBranches = [
    ...cards.filter((card) => card.kind !== 'BRANCH'),
    ...cards.filter((card) => card.kind === 'BRANCH'),
  ]
  return partition(tailBranches, steps, branchOf)
}

function partition(
  cards: RunCard[],
  steps: ExecutionStep[],
  branchOf: Map<string, string>,
): Map<string, ExecutionStep[]> | null {
  const fits = (card: RunCard, step: ExecutionStep) =>
    card.kind === 'NODE'
      ? step.name === card.name
      : card.kind === 'BRANCH'
        ? step.name === branchOf.get(card.id)
        : !TOP_BRANCH.test(step.name)

  // 두 가지 배정이 가능하면 멈춘다. 같은 이름이나 붙어 있는 에이전트 때문에 경계가 모호할 수 있다.
  const memo = new Map<string, number[][]>()
  const ends = (at: number, from: number): number[][] => {
    if (at === cards.length) return from === steps.length ? [[]] : []
    const key = `${at}:${from}`
    const cached = memo.get(key)
    if (cached) return cached
    const card = cards[at]
    const last = Math.min(
      steps.length,
      card.kind === 'AGENT' ? steps.length - (cards.length - at - 1) : from + 1,
    )
    const found: number[][] = []
    for (let end = from + 1; end <= last && found.length < 2; end++) {
      if (!fits(card, steps[end - 1])) break
      for (const rest of ends(at + 1, end)) {
        found.push([end, ...rest])
        if (found.length === 2) break
      }
    }
    memo.set(key, found)
    return found
  }
  const choices = ends(0, 0)
  if (choices.length !== 1) return null
  const groups = new Map<string, ExecutionStep[]>()
  let from = 0
  choices[0].forEach((end, i) => {
    groups.set(cards[i].id, steps.slice(from, end))
    from = end
  })
  return groups
}

/** 카드마다 속 단계의 상태를 하나로 합친다. */
export function cardStatuses(groups: Map<string, ExecutionStep[]>): Map<string, StepStatus> {
  return new Map(
    [...groups].map(([id, group]) => {
      const statuses = group.map((step) => step.status)
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
 * 분기와 작업 카드는 제 단계로, 시작은 첫 단계가 움직였는지로, 종료는 실행이 끝났는지로 본다.
 */
export function reachedNodes(
  nodes: { id: string; ioType?: string | null }[],
  statuses: Map<string, StepStatus>,
  finished: boolean,
): Set<string> {
  const reached = new Set<string>()
  const started = [...statuses.values()].some((status) => status !== 'pending')
  for (const node of nodes) {
    const hit =
      node.ioType === 'START'
        ? started || finished
        : node.ioType === 'END'
          ? finished
          : REACHED.includes(statuses.get(node.id) ?? 'pending')
    if (hit) reached.add(node.id)
  }
  return reached
}

/** 단계를 캔버스 차례로. 예전 서버는 분기를 끝에 붙여 보내 로그에서 분기가 맨 뒤에 찍혔다. */
export const inCanvasOrder = (cards: RunCard[], groups: Map<string, ExecutionStep[]>) =>
  cards.flatMap((card) => groups.get(card.id) ?? [])

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
