import { randomUUID } from 'node:crypto'

import { expect, type APIResponse, type Page, type Response } from '@playwright/test'

import { expectOk, type Api } from './auth'

export interface ExecutionProgress {
  executionId: string
  status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'
  done: number
  total: number
  percent: number
  currentStepName: string | null
  steps: { name: string; status: string }[]
  failure: { stepName: string | null; errorCode: string; fault: string | null } | null
}

/**
 * 실행 화면에는 mock 을 고르는 칸이 없다. "실행하기" 가 보내는 접수 요청(multipart)에 `mock=true` 한 칸만 더한다 —
 * 실제 모델을 부르지 않게. 나머지는 화면이 보낸 그대로다.
 */
export async function runAsMock(page: Page) {
  await page.route('**/api/v1/runs', async (route) => {
    const request = route.request()

    if (request.method() !== 'POST') return route.fallback()

    const boundary = /boundary=([^;]+)/.exec(request.headers()['content-type'] ?? '')?.[1]
    const body = request.postDataBuffer()
    const closing = Buffer.from(`--${boundary}--`)
    const at = body && boundary ? body.lastIndexOf(closing) : -1

    if (!body || at < 0) throw new Error(`접수 요청이 multipart 가 아니다: ${request.headers()['content-type']}`)

    const mock = Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="mock"\r\n\r\ntrue\r\n`)

    await route.continue({ postData: Buffer.concat([body.subarray(0, at), mock, body.subarray(at)]) })
  })
}

/**
 * 한 번 도는 애니메이션(화면 전환 · 카드 등장)이 끝날 때까지 기다린다. 스크린샷 직전에 부른다 —
 * `toBeVisible` 은 투명도 0 인 요소도 보인다고 보아, 그대로 찍으면 흐릿한 전환 중 화면이 남는다.
 * 계속 도는 애니메이션(빛 · 커서 · 선)은 기다리지 않는다.
 */
export async function animationsSettled(page: Page) {
  await page.waitForFunction(() =>
    document
      .getAnimations()
      .every(
        (animation) =>
          animation.playState !== 'running' || animation.effect?.getComputedTiming().iterations === Infinity,
      ),
  )
}

/** 화면 밖에서 mock 실행을 맡긴다. 화면을 보지 않는 시나리오에서 쓴다. */
export async function startMockRun(api: Api, workflowId: string, comment = 'E2E mock 실행') {
  return api.postForm('/api/v1/runs', { workflowId, requestId: randomUUID(), comment, mock: 'true' })
}

export async function acceptedRun(response: APIResponse | Response) {
  expect(response.status(), await response.text()).toBe(202)

  return (await response.json()).data as { executionId: string; status: string; stepCount: number }
}

export async function progressOf(api: Api, executionId: string): Promise<ExecutionProgress> {
  const response = await expectOk(await api.get(`/api/v1/runs/${executionId}`))

  return (await response.json()).data
}

export interface ExecutionEvidence {
  execution: { status: string; runId: string | null; mock: boolean }
  failure: { failureCode: string | null; fault: string; affectsSuccessRate: boolean } | null
  ledger: { eventType: string; [key: string]: unknown }[]
  runRecord: {
    closedBy: string
    runId: string | null
    engineStatus: string | null
    response: { run_id?: string; status?: string } | string | null
    note: string | null
  } | null
}

/**
 * 관리자 정산 근거(GET /api/v1/admin/executions/{id}/evidence). DB 에 닫힌 실패 판정과 근거를 그대로 보여 준다.
 * 근거는 닫는 트랜잭션이 커밋된 뒤 따로 적히므로(ExecutionEvidenceRecorder) 적힐 때까지 기다린다.
 */
export async function evidenceOf(admin: Api, executionId: string): Promise<ExecutionEvidence> {
  let evidence: ExecutionEvidence | undefined

  await expect
    .poll(
      async () => {
        const response = await admin.get(`/api/v1/admin/executions/${executionId}/evidence`)

        evidence = response.ok() ? (await response.json()).data : undefined

        return evidence?.runRecord?.closedBy ?? null
      },
      { message: `실행 ${executionId} 의 정산 근거가 적히지 않았다`, timeout: 20_000 },
    )
    .not.toBeNull()

  return evidence!
}

/**
 * 화면이 진행을 2초 조회(GET /api/v1/runs/{id})로 받았는지 센다. SSE(`/stream`)로 받았으면 비어 있어야 한다 —
 * 스트림이 끊기면 화면이 조회로 내려가므로, 이 목록이 차면 SSE 가 화면까지 닿지 않은 것이다.
 */
export function recordProgressPolls(page: Page): string[] {
  const polls: string[] = []

  page.on('request', (request) => {
    const path = new URL(request.url()).pathname

    if (request.method() === 'GET' && /^\/api\/v1\/runs\/[^/]+$/.test(path)) polls.push(path)
  })

  return polls
}

/** 실행 화면이 스트림을 연 요청. */
export function recordStreams(page: Page): string[] {
  const streams: string[] = []

  page.on('request', (request) => {
    const path = new URL(request.url()).pathname

    if (/^\/api\/v1\/runs\/[^/]+\/stream$/.test(path)) streams.push(path)
  })

  return streams
}
