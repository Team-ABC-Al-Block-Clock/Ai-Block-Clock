import { http, HttpResponse } from 'msw'

import { API_BASE_URL } from '@/api/client'
import type { CostItem, ExecutionStatus, ExecutionStep, StepStatus } from '@/api/executions'
import { CREDIT_BALANCE } from '@/mocks/fixtures/credits'
import { findNode } from '@/mocks/handlers/nodes'
import { PLACEABLE_ITEMS } from '@/mocks/fixtures/nodePalette'
import { fail, ok } from '@/mocks/response'
import { scene } from '@/mocks/scenario'
import { findWorkflowDetail } from '@/mocks/handlers/workflows'
import type { WorkflowDetail } from '@/api/workflows'

/**
 * 실행 도메인 mock. 견적은 저장된 워크플로우를 읽어 자리마다 팔레트 단가를 매긴다 —
 * 픽스처를 손으로 두면 캔버스를 고쳐도 금액이 그대로다.
 */

/** 금액은 서버와 같이 소수 여섯 자리 문자열로 맞춘다. */
const money = (value: number) => value.toFixed(6)

/**
 * 접수된 실행. 시작 시각만 적고 진행률은 조회할 때 경과 시간으로 짓는다 — 타이머를 돌리면
 * 탭을 옮긴 사이 멈춘다. localStorage 에 둔다(새로고침하면 없는 실행이 되지 않게).
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

/** 값을 매길 수 있는 자리만. 제어·입출력 노드는 원본이 없어 원가도 단계도 없다. */
const priced = (detail: WorkflowDetail) =>
  detail.nodes.flatMap((node) =>
    node.sourceType !== 'CONTROL' && node.sourceType !== 'IO' && node.sourceId
      ? [{ nodeKey: node.nodeKey, sourceType: node.sourceType, sourceId: node.sourceId }]
      : [],
  )

/** 견적. 접수와 조회가 같은 값을 써야 한다 — 따로 계산하면 카드와 막는 금액이 어긋난다. */
function quoteOf(detail: WorkflowDetail) {
  const items: CostItem[] = priced(detail).map(({ nodeKey, sourceType, sourceId }) => {
    // 기본 노드는 팔레트에 없다. 저장하며 만들어진 원본에서 찾는다.
    const item = PLACEABLE_ITEMS.find((it) => it.itemId === sourceId)
    const basic = item ? undefined : findNode(sourceId)
    return {
      nodeKey,
      sourceType,
      name: item?.name ?? basic?.name ?? '알 수 없는 노드',
      price: money(Number(item?.price ?? basic?.baseCost ?? 0)),
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
  [
    ...priced(detail).map(
      ({ sourceId }) =>
        PLACEABLE_ITEMS.find((item) => item.itemId === sourceId)?.name ??
        // 기본 노드는 팔레트에 없다. 저장하며 만들어진 원본에서 찾는다 — 견적과 같은 자리다.
        findNode(sourceId)?.name ??
        '이름 없는 단계',
    ),
    ...detail.nodes.filter((node) => node.controlType).map((_, i) => `b${i + 1}`),
  ]

/**
 * 실패 화면을 볼 자리. 몇 번째 단계에서 멎게 할지 적어 둔다 — 0부터 센다.
 * `localStorage.setItem('mockFailStep', '2')`, 되돌리려면 지운다.
 */
const FAIL_STEP_KEY = 'mockFailStep'

function failStep(): number | null {
  try {
    const raw = localStorage.getItem(FAIL_STEP_KEY)
    const at = raw === null ? NaN : Number(raw)
    return Number.isInteger(at) && at >= 0 ? at : null
  } catch {
    return null
  }
}

/** 경과 시간으로 진행을 짓는다. 끝난 뒤에는 더 움직이지 않는다. */
function progressOf(run: MockRun) {
  const total = run.stepNames.length
  const elapsed = Date.now() - run.startedAt
  const reached = Math.max(0, Math.min(total, Math.floor((elapsed - QUEUED_MS) / STEP_MS)))

  const at = failStep()
  // 대기 구간을 지나야 실패로 본다 — 0 을 넣으면 접수하자마자 터져 QUEUED 를 볼 수 없다.
  const failed = at !== null && at < total && elapsed >= QUEUED_MS && reached >= at
  const done = failed ? at : reached
  const finished = !failed && done >= total
  const status: ExecutionStatus = failed
    ? 'FAILED'
    : finished
      ? 'SUCCEEDED'
      : elapsed < QUEUED_MS
        ? 'QUEUED'
        : 'RUNNING'

  const steps: ExecutionStep[] = run.stepNames.map((name, i) => {
    if (failed) return { name, status: i < done ? 'succeeded' : i === done ? 'failed' : 'pending' }
    const stepStatus: StepStatus = i < done ? 'succeeded' : i === done ? 'running' : 'pending'
    return { name, status: finished ? 'succeeded' : status === 'QUEUED' ? 'pending' : stepStatus }
  })

  return {
    executionId: run.executionId,
    status,
    done,
    total,
    percent: total ? Math.round((done / total) * 100) : 0,
    currentStepName:
      failed || finished || status === 'QUEUED' ? null : (run.stepNames[done] ?? null),
    steps,
    failure: failed
      ? { stepName: run.stepNames[done] ?? null, errorCode: 'MODEL_API_ERROR', fault: 'supplier' }
      : null,
  }
}

export const executionHandlers = [
  http.post(`${API_BASE_URL}/runs`, async ({ request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const form = await request.formData()

    /**
     * 순서를 서버와 맞춘다. 상한 → 검증(요청 본문) → 워크플로우 → 멱등 → 파일 → 분기 → 선차감.
     *
     * 상한이 맨 앞인 것은 multipart 설정(10MB)이 컨트롤러 앞에서 잡기 때문이다. 그래서 다른
     * 무엇이 잘못됐든 `FILE_002` 가 먼저 나가고, 같은 상한을 쓰는 `EXECUTION_004` 는 닿지 않는다.
     */
    const file = form.get('file')
    if (file instanceof File && file.size > FILE_MAX_BYTES)
      return fail(413, 'FILE_002', '올린 파일이 10MB 를 넘습니다.')

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

    if (file instanceof File) {
      if (file.size === 0) return fail(400, 'EXECUTION_003', '빈 파일은 올릴 수 없습니다.')
      if (!FILE_EXTENSIONS.some((ext) => file.name.toLowerCase().endsWith(ext)))
        return fail(400, 'EXECUTION_003', '엑셀 또는 CSV 파일만 올릴 수 있습니다.')
    }

    // 안에 분기가 든 에이전트는 서버가 EXECUTION_005 로 끊는다. mock 에이전트는 속이 없어 생략한다.

    // 빈 그래프는 저장에서 막혀 서버에는 이 응답이 없다. 0단계 실행을 만들지 않으려는 방어다.
    if (!detail.nodes.length)
      return fail(400, 'WORKFLOW_014', '노드를 하나 이상 놓아야 실행할 수 있습니다.')

    // 선차감. 잔액을 실제로 깎지는 않는다 — 픽스처가 상수라 새로고침에 되살아난다.
    const quote = quoteOf(detail)
    if (!quote.executable)
      return fail(402, 'PAYMENT_008', 'ABC 잔액이 부족합니다. 충전 후 다시 실행해 주세요.', {
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

  // 결과 JSON 은 엔진 봉투 그대로다. PDF 는 만들지 못한 셈 쳐 JSON 으로 받는 길을 확인한다.
  http.get(`${API_BASE_URL}/runs/:executionId/result`, async ({ request, params }) => {
    const forced = await scene(request)
    if (forced) return forced

    const run = readRuns()[String(params.executionId)]
    if (!run) return fail(404, 'EXECUTION_001', '실행을 찾을 수 없습니다.')
    if (progressOf(run).status !== 'SUCCEEDED')
      return fail(409, 'EXECUTION_001', '아직 끝나지 않은 실행입니다.')
    if (new URL(request.url).searchParams.get('format') === 'PDF')
      return fail(404, 'EXECUTION_007', '그 형식으로 받을 수 있는 결과가 없습니다.')

    const envelope = {
      a2a_version: '2.0',
      node_id: `n${run.stepNames.length}`,
      schema_id: 'text',
      payload: {
        text: ['# 실행 결과', '', ...run.stepNames.map((name) => `- ${name} 완료`)].join('\n'),
      },
    }
    return new HttpResponse(JSON.stringify(envelope), {
      headers: {
        'Content-Type': 'application/json',
        'Content-Disposition': `attachment; filename="${run.executionId}.json"`,
      },
    })
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
