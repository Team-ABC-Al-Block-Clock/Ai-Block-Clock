import type { ConditionOperator } from '@/api/workflows'
import { operatorsFor, parseConditionRight } from '@/lib/canvas/branchCondition'
import type { CanvasCondition } from '@/lib/canvas/canvasWorkflow'
import type { OutputField } from '@/lib/canvas/nodeOutput'

/** 조건이 읽을 수 있는 앞 노드. 이름만 있으면 고르게 할 수 있다. */
export interface ConditionSource {
  id: string
  name: string
  /** 견줄 수 있는 칸. 에이전트처럼 출력 형식을 모르면 null — 경로를 손으로 적는다 */
  fields: OutputField[] | null
}

/** 폼에 적힌 그대로의 값. 후보가 바뀌면 이 값이 후보 밖에 놓일 수 있다. */
export interface BranchInput {
  fromId: string
  path: string
  operator: ConditionOperator
  right: string
}

/**
 * 분기 폼이 지금 보여 줄 값과 적용할 수 있는지.
 *
 * 고른 값이 후보에 없으면 첫 후보로 본다. 폼은 분기 id 로만 다시 서서, 분기를 고른 채 앞에
 * 노드를 이으면 후보만 늘고 `fromId` 는 빈 채로 남는다. 그대로 두면 `select` 는 첫 후보를
 * 보여주는데 값은 비어 있어 「적용」이 영영 죽는다 — 후보가 하나뿐이면 골라서 빠져나올 수도 없다.
 */
export function branchFormState(
  input: BranchInput,
  sources: ConditionSource[],
  saved: CanvasCondition | null,
) {
  const picked = sources.some((source) => source.id === input.fromId)
    ? input.fromId
    : (sources[0]?.id ?? '')
  const fields = sources.find((source) => source.id === picked)?.fields ?? null
  // 칸을 고르는 동안에도 같은 까닭으로 후보 밖의 값은 첫 칸으로 본다.
  const field = fields ? (fields.find((it) => it.path === input.path) ?? fields[0]) : undefined
  const pickedPath = fields ? (field?.path ?? '') : input.path.trim()
  const operators = operatorsFor(field?.kind)
  const pickedOperator = operators.some((it) => it.value === input.operator)
    ? input.operator
    : operators[0].value
  const parsed = parseConditionRight(input.right, pickedOperator, field?.kind)

  return {
    picked,
    fields,
    field,
    pickedPath,
    operators,
    pickedOperator,
    parsed,
    canApply: !!picked && !!pickedPath && parsed !== null,
    /** 저장된 조건이 지금 후보에 없으면 폼은 첫 후보를 보여 준다. 카드의 조건과 달라 보이니 알린다. */
    drifted:
      !!saved &&
      (picked !== input.fromId ||
        (!!fields && pickedPath !== input.path) ||
        pickedOperator !== input.operator),
  }
}
