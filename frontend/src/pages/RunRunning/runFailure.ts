import type { ExecutionFailure } from '@/api/executions'

/** 실행 엔진이 매기는 오류 코드. 없는 코드가 와도 화면이 비지 않게 기본 문구를 둔다. */
const FAILURE_TEXT: Record<string, string> = {
  SCHEMA_VALIDATION_FAILED: '단계가 약속한 형식으로 결과를 내지 못했습니다.',
  BRANCH_CONDITION_INVALID: '분기 조건이 없는 값을 가리킵니다.',
  AGENT_BOUNDARY_VIOLATION: '에이전트가 공개한 형식과 다른 결과를 냈습니다.',
  REF_FILE_REJECTED: '단계에 붙은 참고 자료를 읽을 수 없습니다.',
  PROMPT_TEMPLATE_INVALID: '프롬프트가 이 자리에서 받을 수 없는 값을 찾습니다.',
  MODEL_TIMEOUT: '모델이 제때 답하지 않았습니다.',
  MODEL_API_ERROR: '모델 호출이 실패했습니다.',
  FILE_PARSE_FAILED: '올린 파일을 읽지 못했습니다.',
  RATE_LIMITED: '요청이 몰려 잠시 처리하지 못했습니다.',
  LLM_INIT_FAILED: '실행 환경 설정에 문제가 있습니다.',
  RUNTIME_INTERNAL: '실행 엔진에서 오류가 났습니다.',
  ABC_RUNTIME_STALLED: '실행 엔진이 응답을 멈춰 기다리기를 그만두었습니다.',
  ABC_RUNTIME_UNREPORTED: '실행이 실패했지만 까닭은 남아 있지 않습니다.',
  ABC_RUNTIME_UNREACHABLE: '실행 엔진에 닿지 못했습니다.',
}

/**
 * `fault` 는 원래 정산용이다 — 누구에게 환원할지 가르는 값이라 그대로 보여 주면 탓만 하고
 * 할 일을 알려 주지 않는다. 특히 `consumer` 는 사용자 잘못이라는 말이 된다. 행동으로 바꾼다.
 * 서버가 까닭을 못 찾으면 비어 오고, 그때는 아무 말도 덧붙이지 않는다.
 */
const FAULT_ADVICE: Record<string, string> = {
  supplier: '재실행 후에도 실패한다면 노드를 교체해주세요.',
  consumer: '올린 파일과 입력한 내용을 확인해 주세요.',
  platform: '잠시 후 다시 실행해 주세요.',
}

/** 한 줄에 한 마디씩 — 어디서 멈췄나, 무슨 일이 났나, 무엇을 하면 되나. */
export function describeFailure(failure: ExecutionFailure): string {
  const lines = [
    failure.stepName && `${failure.stepName} 단계에서 중단되었습니다.`,
    FAILURE_TEXT[failure.errorCode] ?? '실행이 실패했습니다.',
    failure.fault && FAULT_ADVICE[failure.fault],
  ]

  return lines.filter(Boolean).join('\n')
}

/** 실패 카드에 띄울 문구. 실행을 못 찾았거나 소식이 끊긴 경우를 먼저 가른다. */
export function failureNotice({
  missing,
  lost,
  failure,
  failedLabel,
}: {
  missing: boolean
  lost: boolean
  failure: ExecutionFailure | null | undefined
  /** 실패한 단계의 화면 이름. 분기는 엔진 id 대신 조건으로 바꿔 둔다. */
  failedLabel: string | undefined
}) {
  if (missing) return '워크플로우를 다시 실행해 주세요.'
  if (lost) return '실행은 계속되고 있습니다. 새로고침해 주세요.'
  if (failure) return describeFailure({ ...failure, stepName: failedLabel ?? failure.stepName })
  return '실행 엔진이 사유를 알려 주지 않았습니다.\n재실행 후에도 실패한다면 워크플로우를 확인해주세요.'
}
