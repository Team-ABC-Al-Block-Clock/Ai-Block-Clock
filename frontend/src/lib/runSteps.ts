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

/**
 * 작업 카드마다 단계 상태. 서버 단계는 작업 카드 차례에 분기가 뒤에 붙어 온다.
 *
 * 에이전트는 안의 단계 수만큼 펼쳐져 수가 어긋난다. 그때는 `null` — 잘못 짝지으면 엉뚱한
 * 카드가 켜진다.
 */
export function cardStatuses(
  cardIds: string[],
  branchCount: number,
  steps: ExecutionStep[],
): Map<string, StepStatus> | null {
  if (steps.length === 0 || steps.length !== cardIds.length + branchCount) return null
  return new Map(cardIds.map((id, i) => [id, steps[i].status]))
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
): ExecutionStep[] {
  const work = new Map(cardIds.map((id, i) => [id, steps[i]]))
  let branch = cardIds.length
  return nodes.flatMap((node) => {
    const step = node.controlType ? steps[branch++] : work.get(node.id)
    return step ? [step] : []
  })
}
