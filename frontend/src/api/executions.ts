import { api, API_BASE_URL } from '@/api/client'

export type ExecutionStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'

/** 단계 상태는 실행 엔진이 매긴 값을 그대로 받는다. 늘 수 있어 열어 둔다. */
export interface ExecutionStep {
  name: string
  status: string
}

/**
 * 왜 멎었는지. `status` 가 `FAILED` 일 때만 온다.
 *
 * `fault` 가 비어 있으면 **모른다는 뜻**이다 — 플랫폼 과실이 아니다.
 * 실행 엔진이 이력을 잊어 사유를 되찾지 못한 경우다.
 */
export interface ExecutionFailure {
  stepName: string | null
  errorCode: string
  fault: 'supplier' | 'consumer' | 'platform' | null
}

export interface ExecutionProgress {
  executionId: string
  status: ExecutionStatus
  done: number
  total: number
  percent: number
  /** 끝났으면 null */
  currentStepName: string | null
  steps: ExecutionStep[]
  failure: ExecutionFailure | null
}

export interface ExecutionStarted {
  executionId: string
  status: ExecutionStatus
  stepCount: number
}

export interface StartRunBody {
  workflowId: string
  /** 사용자가 덧붙인 한 줄. 모든 단계에 따라간다. 500자까지 */
  comment?: string
  /** 첫 단계가 읽을 파일. 엑셀 또는 CSV, 10MB 까지 */
  inputFile?: File
  /** true 면 모델을 부르지 않고 파이프라인만 돈다 */
  mock?: boolean
}

/**
 * 접수만 하고 즉시 돌아온다. 진행 상황은 `openRunStream` 으로 따라간다.
 *
 * 파일이 함께 갈 수 있어 JSON 이 아니라 폼으로 보낸다 — 서버가 `multipart/form-data`
 * 만 받는다. 비어 있는 선택 항목은 파트를 아예 넣지 않는다.
 */
export const startRun = (params: StartRunBody) => {
  const body = new FormData()

  body.append('workflowId', params.workflowId)
  if (params.comment) body.append('comment', params.comment)
  if (params.inputFile) body.append('file', params.inputFile)
  if (params.mock !== undefined) body.append('mock', String(params.mock))

  return api.post<ExecutionStarted>('/runs', body)
}

/** 스트림을 열 수 없을 때 쓰는 단발 조회. 뒤에서 도는 폴링이 알아낸 값을 준다. */
export const getRunProgress = (executionId: string) =>
  api.get<ExecutionProgress>(`/runs/${executionId}`)

export const isFinished = (status: ExecutionStatus) => status === 'SUCCEEDED' || status === 'FAILED'

/**
 * 진행 상황 스트림을 연다.
 *
 * axios 인터셉터를 타지 않으므로 봉투가 벗겨지지 않는다 — 서버가 `data` 알맹이만
 * 실어 보내므로 그대로 쓴다. 세션은 쿠키라 `withCredentials` 만 있으면 붙는다.
 */
export const openRunStream = (executionId: string) =>
  new EventSource(`${API_BASE_URL}/runs/${executionId}/stream`, { withCredentials: true })
