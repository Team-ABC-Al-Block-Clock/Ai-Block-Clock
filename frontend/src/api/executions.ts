import { api } from '@/api/client'
import type { SourceType } from '@/api/workflows'

/**
 * 실행 도메인 API. 금액은 소수점 오차를 피하려고 전부 문자열이다(`api/credits.ts` 와 같은 규칙).
 *
 * 견적은 `/executions` 아래, 실행 시작·조회는 `/runs` 아래다 — 같은 도메인인데 앞 조각이
 * 갈린다. 서버가 이유를 밝힌 적은 없고 두 갈래를 각각 다른 MR 이 열었다. 물어보기 전까지는
 * 그대로 따른다.
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
 * 실행 전 예상 차감액. 조회지만 명세가 POST 라 CSRF 토큰이 붙는다(`api/client.ts` 가 알아서 한다).
 *
 * 계산만 하고 차감하지 않는다 — 실제 차단은 실행을 접수할 때 서버가 한 번 더 본다.
 * 그래서 화면이 막는 것은 안내이지 최종 판정이 아니다.
 */
export const estimateCost = (workflowId: string) =>
  api.post<CostEstimate>('/executions/cost-estimates', { workflowId })

/** 실행 전체의 상태. 서버 enum 그대로다. */
export type ExecutionStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'

/**
 * 단계 하나의 상태. 실행 엔진이 보낸 소문자를 서버가 그대로 흘려 준다 —
 * 실행 전체 상태와 집합이 다르다(`pending` 과 `skipped` 가 더 있고 `queued` 가 없다).
 */
export type StepStatus = 'pending' | 'running' | 'succeeded' | 'failed' | 'skipped'

/** 단계 이름과 상태뿐이다. `nodeKey` 가 없어 캔버스와는 순서로 짝짓는다. */
export interface ExecutionStep {
  name: string
  status: StepStatus
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
  /** 첫 단계에 함께 전달된다. 500자까지 */
  comment?: string
  file?: File | null
  /** 켜면 모델을 부르지 않고 파이프라인만 돈다 */
  mock?: boolean
}

/**
 * 실행 접수. 단계 하나가 25초쯤 걸려 서버는 기다리지 않고 접수 번호만 준다(202).
 *
 * 명세가 `multipart/form-data` 다 — 첫 단계가 읽을 파일을 같이 싣기 때문이다. `Content-Type` 은
 * 손대지 않는다. FormData 를 주면 브라우저가 boundary 까지 붙여 준다(`api/client.ts`).
 *
 * 비어 있는 칸은 아예 넣지 않는다. 빈 문자열을 넣으면 서버가 「코멘트가 왔는데 비었다」로 받는다.
 */
export const startExecution = ({ workflowId, comment, file, mock }: StartExecutionInput) => {
  const form = new FormData()
  form.append('workflowId', workflowId)
  if (comment?.trim()) form.append('comment', comment.trim())
  if (file) form.append('file', file)
  if (mock) form.append('mock', 'true')

  return api.post<StartedExecution>('/runs', form)
}

/**
 * 진행 상황. 2초 간격으로 부르고 `status` 가 끝나면 멈춘다.
 *
 * 그래프는 오지 않는다 — 캔버스는 `GET /workflows/{workflowId}` 로 따로 그리고 여기서는
 * 단계의 이름과 상태만 얹는다.
 */
export const getExecutionProgress = (executionId: string) =>
  api.get<ExecutionProgress>(`/runs/${executionId}`)
