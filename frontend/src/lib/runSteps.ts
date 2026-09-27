import type { ExecutionStep } from '@/api/executions'

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
