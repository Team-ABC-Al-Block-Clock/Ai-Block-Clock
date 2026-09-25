import { API_BASE_URL, api } from '@/api/client'
import { ApiError, isApiErrorBody } from '@/api/errors'
import type { SourceType } from '@/api/workflows'

/**
 * 실행 도메인 API. 금액은 소수점 오차를 피하려고 전부 문자열이다.
 * 견적은 `/executions`, 시작·조회는 `/runs` 다 — 서버가 이유를 밝힌 적 없어 그대로 따른다.
 */

/** 견적의 한 줄. 에이전트는 속을 펼치지 않고 판매가 하나로 온다. */
export interface CostItem {
  /** 캔버스에서 이 자리를 가리키는 키(`n1`…) */
  nodeKey: string
  sourceType: SourceType
  name: string
  /** 노드는 원가, 에이전트는 판매가 */
  price: string
}

export interface CostEstimate {
  /** 놓인 노드의 원가와 에이전트 판매가를 더한 값 */
  estimatedAmount: string
  /** 다른 실행에 잠긴 금액은 빠져 있다 */
  availableAmount: string
  /** 넉넉하면 '0.000000' */
  shortageAmount: string
  executable: boolean
  /** 연결 순서대로 온다 */
  items: CostItem[]
}

/**
 * 실행 전 예상 차감액. 조회지만 명세가 POST 라 CSRF 토큰이 붙는다.
 * 계산만 하고 차감하지 않는다 — 화면이 막는 것은 안내이지 최종 판정이 아니다.
 */
export const estimateCost = (workflowId: string) =>
  api.post<CostEstimate>('/executions/cost-estimates', { workflowId })

/** 실행 전체의 상태. 서버 enum 그대로다. */
export type ExecutionStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'

/** 단계 하나의 상태. 실행 전체 상태와 집합이 다르다 — 소문자이고 `skipped` 가 있다. */
export type StepStatus = 'pending' | 'running' | 'succeeded' | 'failed' | 'skipped'

/** 단계 이름과 상태뿐이다. `nodeKey` 가 없어 캔버스와는 순서로 짝짓는다. */
export interface ExecutionStep {
  name: string
  status: StepStatus
}

/**
 * 왜 중단되었는지. `status` 가 `FAILED` 일 때만 온다.
 *
 * `fault` 가 비어 있으면 모른다는 뜻이다 — 플랫폼 과실이 아니다. 실행 엔진이 이력을 잊어
 * 사유를 되찾지 못한 경우다.
 */
export interface ExecutionFailure {
  stepName: string | null
  errorCode: string
  fault: 'supplier' | 'consumer' | 'platform' | null
}

export interface ExecutionProgress {
  executionId: string
  status: ExecutionStatus
  /** 끝난 단계 수 */
  done: number
  /** 돌아야 하는 단계 수. 에이전트는 안의 단계 수만큼 센다 */
  total: number
  percent: number
  /** 끝났으면 null */
  currentStepName: string | null
  steps: ExecutionStep[]
  /** `FAILED` 가 아니면 null. 서버가 칸 자체를 안 보내기도 한다 */
  failure?: ExecutionFailure | null
}

export interface StartedExecution {
  executionId: string
  status: ExecutionStatus
  /** 펼친 프롬프트 수. 캔버스의 카드 수와 다를 수 있다 */
  stepCount: number
}

/** 첫 단계가 읽을 파일. 서버는 확장자만 보고 가른다(`ExecutionFileValidator`). */
export const RUN_FILE_LIMITS = {
  maxBytes: 10 * 1024 * 1024,
  extensions: ['.xlsx', '.xls', '.csv'],
} as const

/** 고른 파일이 제약에 맞는지 — 어긋나면 띄울 문구, 맞으면 null. */
export function describeRunFileProblem(file: File): string | null {
  const { extensions, maxBytes } = RUN_FILE_LIMITS
  const name = file.name.toLowerCase()

  if (!extensions.some((ext) => name.endsWith(ext))) return '엑셀 또는 CSV 파일만 올릴 수 있습니다'
  if (file.size === 0) return '빈 파일은 올릴 수 없습니다'
  if (file.size > maxBytes) return '파일은 10MB 이하여야 합니다'
  return null
}

export interface StartExecutionInput {
  workflowId: string
  /** 이 누름을 가리키는 UUID. 같은 값이 다시 오면 서버가 앞서 접수한 실행을 돌려준다. */
  requestId: string
  /** 첫 단계에 함께 전달된다. 500자까지 */
  comment?: string
  file?: File | null
  /** 켜면 모델을 부르지 않고 파이프라인만 돈다 */
  mock?: boolean
}

/**
 * 실행 접수. 서버는 기다리지 않고 접수 번호만 준다(202). 그 순간 견적만큼 ABC 가 빠지고,
 * 모자라면 `PAYMENT_008`(402) 이다.
 *
 * 명세가 multipart 다 — 첫 단계가 읽을 파일을 같이 싣는다. `Content-Type` 은 손대지 않는다.
 * 빈 칸은 아예 넣지 않는다. 빈 문자열을 넣으면 서버가 「값이 왔는데 비었다」로 받는다.
 */
export const startExecution = ({
  workflowId,
  requestId,
  comment,
  file,
  mock,
}: StartExecutionInput) => {
  const form = new FormData()
  form.append('workflowId', workflowId)
  form.append('requestId', requestId)
  if (comment?.trim()) form.append('comment', comment.trim())
  if (file) form.append('file', file)
  if (mock) form.append('mock', 'true')

  return api.post<StartedExecution>('/runs', form)
}

/**
 * 진행 상황. 2초 간격으로 부르고 `status` 가 끝나면 멈춘다.
 * 그래프는 오지 않는다 — 캔버스는 따로 그리고 여기서는 단계 이름과 상태만 얹는다.
 */
export const getExecutionProgress = (executionId: string) =>
  api.get<ExecutionProgress>(`/runs/${executionId}`)

export const isFinished = (status: ExecutionStatus) => status === 'SUCCEEDED' || status === 'FAILED'

/**
 * 진행 상황 스트림을 연다. 붙는 즉시 지금 상태가 한 번 오고, 그 뒤로는 달라졌을 때만 온다.
 *
 * axios 인터셉터를 타지 않아 봉투가 벗겨지지 않는다 — 서버가 `data` 알맹이만 실어 보내므로
 * 그대로 쓴다. 세션은 쿠키라 `withCredentials` 만 있으면 붙는다.
 */
export const openRunStream = (executionId: string) =>
  new EventSource(`${API_BASE_URL}/runs/${executionId}/stream`, { withCredentials: true })

export type ResultFormat = 'JSON' | 'PDF'

export const RESULT_FORMAT_EXTENSIONS: Record<ResultFormat, string> = {
  JSON: 'json',
  PDF: 'pdf',
}

function filenameOf(disposition: string | null, executionId: string, format: ResultFormat) {
  const quoted = disposition?.match(/filename="([^"]+)"/)
  return quoted?.[1] ?? `${executionId}.${RESULT_FORMAT_EXTENSIONS[format]}`
}

export async function fetchResult(executionId: string, format: ResultFormat = 'JSON') {
  const query = format === 'JSON' ? '' : `?format=${format}`
  const response = await fetch(`${API_BASE_URL}/runs/${executionId}/result${query}`, {
    credentials: 'include',
  })

  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new ApiError(isApiErrorBody(body) ? body.error.message : '결과를 받지 못했습니다.', {
      code: isApiErrorBody(body) ? body.error.code : undefined,
      status: response.status,
    })
  }

  return {
    blob: await response.blob(),
    filename: filenameOf(response.headers.get('Content-Disposition'), executionId, format),
  }
}

export async function downloadResult(executionId: string, format: ResultFormat = 'JSON') {
  const { blob, filename } = await fetchResult(executionId, format)
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')

  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}
