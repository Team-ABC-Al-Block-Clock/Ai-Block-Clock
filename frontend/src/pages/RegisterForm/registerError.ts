import { ApiError } from '@/api/errors'

const FAILED = '등록에 실패했습니다. 잠시 후 다시 시도해 주세요'

/** 서버가 짚어 준 칸의 화면 이름. 여기 없는 칸은 서버가 준 이름을 그대로 보여 준다. */
export const FIELD_LABEL: Record<string, string> = {
  name: '이름',
  summary: '한 줄 설명',
  description: '상세 설명',
  usageExample: '입력 예시',
  onboardingMessage: '웰컴 메시지',
  workflowId: '굳힐 워크플로우',
  revision: '워크플로우 회차',
}

/**
 * `COMMON_001` 이 어느 칸 때문인지. 서버가 `error.data` 에 `{ 칸: 사유 }` 를 실어 보낸다
 * (`ApiControllerAdvice.handleValidationException`). 이것을 안 읽고 이름·설명을 지목하면
 * `workflowId`·`revision` 이 빠져 막힌 사람이 엉뚱한 칸만 고치다 만다.
 */
function invalidFieldNames(data: unknown): string[] {
  if (!data || typeof data !== 'object') return []
  return Object.keys(data as Record<string, unknown>).map((key) => FIELD_LABEL[key] ?? key)
}

/** 다시 눌러도 통과하지 않는 코드는 문구를 따로 가른다 — "잠시 후 다시" 로 뭉개면 계속 누른다. */
export function describeRegisterError(error: unknown): string | null {
  if (!error) return null
  if (!(error instanceof ApiError)) return FAILED
  if (error.code === 'COMMON_001') {
    const fields = invalidFieldNames(error.data)
    // 본문을 아예 읽지 못하면(빈 UUID 등) 짚어 줄 칸도 없이 온다.
    return fields.length
      ? `${fields.join(' · ')} 을(를) 확인해 주세요`
      : '보낸 값이 올바르지 않습니다. 워크플로우를 다시 열어 주세요'
  }
  // 사용자가 보던 회차와 굳히는 회차가 다르다. 편집 화면을 다시 열어야 한다.
  if (error.code === 'WORKFLOW_016') return '워크플로우가 그 사이 수정되었습니다. 다시 열어 주세요'
  if (error.code === 'AGENT_002' || error.code === 'WORKFLOW_003')
    return '다른 사람의 노드가 섞여 있어 등록할 수 없습니다'
  if (error.code === 'AGENT_001') return '노드가 없는 워크플로우는 등록할 수 없습니다'
  if (error.code === 'WORKFLOW_002' || error.code === 'WORKFLOW_015')
    return '워크플로우를 찾을 수 없습니다'
  return FAILED
}
