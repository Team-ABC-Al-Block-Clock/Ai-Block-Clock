import { http } from 'msw'

import { API_BASE_URL } from '@/api/client'
import type { CostItem, ExecutionStatus, ExecutionStep, StepStatus } from '@/api/executions'
import { CREDIT_BALANCE } from '@/mocks/fixtures/credits'
import { PLACEABLE_ITEMS } from '@/mocks/fixtures/nodePalette'
import { fail, ok } from '@/mocks/response'
import { scene } from '@/mocks/scenario'
import { findWorkflowDetail } from '@/mocks/handlers/workflows'
import type { WorkflowDetail } from '@/api/workflows'

/**
 * 실행 도메인 mock.
 *
 * 견적은 저장된 워크플로우를 읽어 자리마다 팔레트 단가를 매긴다 — 손으로 픽스처를 두면
 * 캔버스를 고쳐도 금액이 그대로라 「바뀐 게 반영 안 된다」로 보인다.
 */

/** 금액은 서버와 같이 소수 여섯 자리 문자열로 맞춘다. */
const money = (value: number) => value.toFixed(6)

/**
 * 접수된 실행. 시작 시각만 적어 두고 진행률은 조회할 때 경과 시간으로 계산한다 —
 * 타이머를 돌리면 탭을 옮긴 사이 멈춰 버려 돌아왔을 때 진행이 얼어 있다.
 *
 * localStorage 에 둔다. 메모리면 진행 화면에서 새로고침하는 순간 없는 실행이 된다.
 * 되돌리려면 `localStorage.removeItem('mockExecutions')`.
 */
const RUNS_KEY = 'mockExecutions'

interface MockRun {
  executionId: string
  /** 이 실행을 접수시킨 누름. 같은 값이 다시 오면 새로 접수하지 않는다 */
  requestId: string
  startedAt: number
  /** 접수 때 굳힌다. 도는 도중에 워크플로우를 고쳐도 단계 목록이 바뀌지 않아야 한다 */
  stepNames: string[]
}

/** 한 단계에 쓰는 시간. 실서버는 25초쯤이지만 화면을 확인하려면 그만큼 기다릴 수 없다. */
const STEP_MS = 2_500
/** 접수 직후 잠깐은 QUEUED 다 — 화면이 그 상태를 그리는지 볼 자리가 필요하다. */
const QUEUED_MS = 900

function readRuns(): Record<string, MockRun> {
  try {
    return JSON.parse(localStorage.getItem(RUNS_KEY) ?? '{}') as Record<string, MockRun>
  } catch {
    return {}
  }
}

function putRun(run: MockRun) {
  try {
    localStorage.setItem(RUNS_KEY, JSON.stringify({ ...readRuns(), [run.executionId]: run }))
  } catch {
    /* 사용량 초과·프라이빗 모드 — 이번 실행만 못 따라간다 */
  }
}

/** 서버가 파일을 가르는 규칙(`ExecutionFileValidator`). 확장자만 보고 MIME 은 보지 않는다. */
const FILE_EXTENSIONS = ['.xlsx', '.xls', '.csv']
const FILE_MAX_BYTES = 10 * 1024 * 1024

/**
 * 견적. 접수와 견적 조회가 같은 값을 써야 한다 — 따로 계산하면 카드에 6 이 떠 있는데
 * 접수는 7 로 막는 일이 생긴다.
 */
function quoteOf(detail: WorkflowDetail) {
  const items: CostItem[] = detail.nodes.map((node) => {
    const item = PLACEABLE_ITEMS.find((it) => it.itemId === node.sourceId)
    return {
      nodeKey: node.nodeKey,
      sourceType: node.sourceType,
      name: item?.name ?? '알 수 없는 노드',
      price: money(Number(item?.price ?? 0)),
    }
  })

  const estimated = items.reduce((sum, item) => sum + Number(item.price), 0)
  const available = Number(CREDIT_BALANCE.availableAmount)
  const shortage = Math.max(0, estimated - available)

  return {
    estimatedAmount: money(estimated),
    availableAmount: money(available),
    shortageAmount: money(shortage),
    executable: shortage === 0,
    items,
  }
}

/** 접수 때 굳히는 단계 이름. 견적과 같은 자리를 읽어 금액과 단계가 어긋나지 않게 한다. */
const stepNamesOf = (detail: WorkflowDetail) =>
  detail.nodes.map(
    (node) =>
      PLACEABLE_ITEMS.find((item) => item.itemId === node.sourceId)?.name ?? '이름 없는 단계',
  )

/** 경과 시간으로 진행을 짓는다. 끝난 뒤에는 더 움직이지 않는다. */
function progressOf(run: MockRun) {
  const total = run.stepNames.length
  const elapsed = Date.now() - run.startedAt
  const done = Math.max(0, Math.min(total, Math.floor((elapsed - QUEUED_MS) / STEP_MS)))
  const finished = done >= total
  const status: ExecutionStatus = finished
    ? 'SUCCEEDED'
    : elapsed < QUEUED_MS
      ? 'QUEUED'
      : 'RUNNING'

  const steps: ExecutionStep[] = run.stepNames.map((name, i) => {
    const stepStatus: StepStatus = i < done ? 'succeeded' : i === done ? 'running' : 'pending'
    return { name, status: finished ? 'succeeded' : status === 'QUEUED' ? 'pending' : stepStatus }
  })

  return {
    executionId: run.executionId,
    status,
    done,
    total,
    percent: total ? Math.round((done / total) * 100) : 0,
    currentStepName: finished || status === 'QUEUED' ? null : (run.stepNames[done] ?? null),
    steps,
  }
}

export const executionHandlers = [
  http.post(`${API_BASE_URL}/runs`, async ({ request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const form = await request.formData()

    // 순서를 서버와 맞춘다. 검증(요청 본문) → 워크플로우 → 멱등 → 파일 → 분기 → 선차감.
    const workflowId = form.get('workflowId')
    if (typeof workflowId !== 'string' || !workflowId)
      return fail(400, 'COMMON_001', '워크플로우를 지정해 주세요.')

    const requestId = form.get('requestId')
    if (typeof requestId !== 'string' || !requestId)
      return fail(400, 'COMMON_001', '실행 요청 식별자를 보내 주세요.')

    const comment = form.get('comment')
    if (typeof comment === 'string' && comment.length > 500)
      return fail(400, 'COMMON_001', '코멘트는 500자까지 보낼 수 있습니다.')

    const detail = findWorkflowDetail(workflowId)
    if (!detail) return fail(404, 'WORKFLOW_002', '워크플로우를 찾을 수 없습니다.')

    // 같은 누름이 다시 왔다. 새로 차감하지 않고 앞서 접수한 실행을 그대로 돌려준다.
    const already = Object.values(readRuns()).find((run) => run.requestId === requestId)
    if (already)
      return ok(
        {
          executionId: already.executionId,
          status: progressOf(already).status,
          stepCount: already.stepNames.length,
        },
        { status: 202 },
      )

    const file = form.get('file')
    if (file instanceof File) {
      const name = file.name.toLowerCase()
      if (!FILE_EXTENSIONS.some((ext) => name.endsWith(ext)) || file.size === 0)
        return fail(400, 'EXECUTION_003', '엑셀 또는 CSV 파일만 올릴 수 있습니다.')
      if (file.size > FILE_MAX_BYTES) return fail(413, 'FILE_002', '파일이 10MB 를 넘습니다.')
    }

    // 분기(`CONTROL`)는 여기서 EXECUTION_005 로 끊긴다. 화면이 아직 분기를 만들지 못해 생략한다.

    // 빈 그래프는 저장에서 이미 막혀 서버는 이 자리에 응답이 없다. 0단계 실행을 만들지 않으려는 방어다.
    if (!detail.nodes.length)
      return fail(400, 'WORKFLOW_014', '노드를 하나 이상 놓아야 실행할 수 있습니다.')

    /**
     * 선차감. 실제로 잔액을 깎지는 않는다 — 픽스처가 상수라 깎으면 새로고침에 되살아나
     * 「깎였다 안 깎였다」 하는 쪽이 더 헷갈린다. 막는 자리와 실어 보내는 금액만 서버와 맞춘다.
     */
    const quote = quoteOf(detail)
    if (!quote.executable)
      return fail(402, 'PAYMENT_008', 'ABC 잔액이 부족합니다. 충전한 뒤 다시 실행해 주세요.', {
        estimatedAmount: quote.estimatedAmount,
        availableAmount: quote.availableAmount,
        shortageAmount: quote.shortageAmount,
      })

    const run: MockRun = {
      executionId: `exe_${Date.now().toString(36)}`,
      requestId,
      startedAt: Date.now(),
      stepNames: stepNamesOf(detail),
    }
    putRun(run)

    return ok(
      { executionId: run.executionId, status: 'QUEUED', stepCount: run.stepNames.length },
      { status: 202 },
    )
  }),

  http.get(`${API_BASE_URL}/runs/:executionId`, async ({ request, params }) => {
    const forced = await scene(request)
    if (forced) return forced

    const run = readRuns()[String(params.executionId)]
    if (!run) return fail(404, 'EXECUTION_001', '실행을 찾을 수 없습니다.')

    return ok(progressOf(run))
  }),

  http.post(`${API_BASE_URL}/executions/cost-estimates`, async ({ request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const { workflowId } = (await request.json()) as { workflowId?: string }
    if (!workflowId) return fail(400, 'COMMON_001', '워크플로우를 지정해 주세요.')

    const detail = findWorkflowDetail(workflowId)
    if (!detail) return fail(404, 'WORKFLOW_002', '워크플로우를 찾을 수 없습니다.')

    return ok(quoteOf(detail))
  }),
]
