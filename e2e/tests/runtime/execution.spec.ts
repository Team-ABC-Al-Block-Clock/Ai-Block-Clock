import { readFile } from 'node:fs/promises'
import { join } from 'node:path'

import type { APIResponse, Page } from '@playwright/test'

import { deadLetter, pauseBroker, readyCount, resumeBroker, takeSubmit, tapEvents } from '../support/broker'
import { balanceOf, charge, micros } from '../support/credits'
import {
  acceptedRun,
  animationsSettled,
  evidenceOf,
  progressOf,
  recordProgressPolls,
  recordStreams,
  runAsMock,
  startMockRun,
} from '../support/executions'
import { signup } from '../support/member'
import { completeMockTestRun, finishedTestRun, saveNode, startMockTestRun } from '../support/nodes'
import { runtimeLog, startRuntime, stopRuntime, waitFor } from '../support/runtime'
import { BACKEND_LOG, FILE_STORE_ROOT } from '../support/runtimeEnv'
import { expect, test } from '../support/runtimeFixtures'
import { estimateOf, saveLinearWorkflow } from '../support/workflows'

/**
 * 실행 접수 · 결과 전달을 RabbitMQ 로 바꾼 뒤의 흐름(ADR-0001 Action Item 19).
 * 백엔드(bootRun) → 테스트용 RabbitMQ → 런타임(ai/spike/linear-mvp, mock) → 이벤트 → SSE → 화면.
 *
 * 런타임은 테스트가 띄우고 내린다. 테스트마다 필요한 상태(떠 있음 · 내려감)를 스스로 맞추므로 순서에 기대지 않는다.
 * 모델은 부르지 않는다 — 접수 요청에 mock=true 를 싣고, 런타임은 키 없이 띄운다.
 */

const CHARGED = '1'
const PROMPT = '이번 달 성적으로 가정통신문을 써 줘'
/** 첫 단계가 읽을 성적표. 백엔드가 공유 폴더에 두고 런타임이 같은 자리에서 읽는다(contract 3장). */
const SCORES_CSV = ['이름,반,국어,수학', '김싸피,1,88,92', '이싸피,2,75,81', ''].join('\n')
const QUEUE_EXPIRED_TEXT =
  '실행 대기열에서 10분 넘게 차례가 오지 않아 실행하지 못했습니다. 차감한 금액은 모두 돌려드렸습니다.'

test.afterEach(async ({}, testInfo) => {
  if (testInfo.status === testInfo.expectedStatus) return

  const log = runtimeLog()

  if (log) await testInfo.attach('runtime.log', { path: log, contentType: 'text/plain' })

  const backend = await readFile(BACKEND_LOG, 'utf8').catch(() => '')

  await testInfo.attach('backend.log (끝 400줄)', {
    body: backend.split(/\r?\n/).slice(-400).join('\n'),
    contentType: 'text/plain',
  })
})

test.afterAll(async () => {
  resumeBroker()
  await stopRuntime()
})

type Shot = (name: string) => Promise<string>

/** 전환 애니메이션이 끝난 화면을 찍는다. */
function stillShot(page: Page, shot: Shot): Shot {
  return async (name) => {
    await animationsSettled(page)

    return shot(name)
  }
}

/** 대기 화면에서 프롬프트를 쓰고 "실행하기" 를 누른다. 접수 응답(202)과 실행 화면으로 옮겨 간 것까지 본다. */
async function runFromWaitingScreen(page: Page, workflowId: string, snap?: Shot) {
  await page.locator('#runPrompt').fill(PROMPT)
  await expect(page.locator('#runBtn')).toHaveCSS('cursor', 'pointer')

  if (snap) await snap('프롬프트-입력-실행하기-켜짐')

  const accepted = page.waitForResponse(
    (response) => response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/v1/runs',
  )

  await page.getByText('실행하기', { exact: true }).click()

  const run = await acceptedRun(await accepted)

  expect(run.status).toBe('QUEUED')
  await expect(page).toHaveURL(`/run/${run.executionId}/running?workflowId=${workflowId}`)

  return run
}

/** 백엔드 로그에 이 줄이 찍힐 때까지 기다린다. */
async function waitForBackendLog(text: string, timeoutMs: number) {
  await waitFor(
    async () => (await readFile(BACKEND_LOG, 'utf8').catch(() => '')).includes(text),
    timeoutMs,
    `백엔드 로그에 "${text}" 가 없다: ${BACKEND_LOG}`,
  )
}

test('실행을 맡기면 대기열을 거쳐 런타임이 단계를 진행하고, 끝나면 PDF · JSON 결과를 받으며 예치가 돌아온다', async ({
  page,
  member,
  admin,
  shot,
}) => {
  const snap = stillShot(page, shot)

  await startRuntime()
  await charge(member.api, CHARGED)

  const workflow = await saveLinearWorkflow(member.api)
  const estimate = await estimateOf(member.api, workflow.id)
  const polls = recordProgressPolls(page)
  const streams = recordStreams(page)

  await runAsMock(page)
  await page.goto(`/run/waiting?workflowId=${workflow.id}`)

  await expect(page.getByText('실행 예상 비용')).toBeVisible()
  await expect(page.getByText(`${CHARGED} ABC`, { exact: true })).toBeVisible()
  await expect(page.getByText(workflow.steps[1], { exact: true }).first()).toBeVisible()
  await snap('실행-대기-견적-0.027과-사용-가능-1ABC')

  const chooser = page.waitForEvent('filechooser')

  await page.getByRole('button', { name: '찾기' }).click()
  await (await chooser).setFiles({ name: 'scores.csv', mimeType: 'text/csv', buffer: Buffer.from(SCORES_CSV) })
  await expect(page.getByText('scores.csv')).toBeVisible()

  const run = await runFromWaitingScreen(page, workflow.id, snap)

  expect(run.stepCount).toBe(2)

  // 첫 단계가 도는 동안 — 접수 때 견적만큼 예치로 옮겨져 있다(B1)
  await expect(page.getByText('0 / 2 단계')).toBeVisible()
  await expect(page.getByText('실행을 준비하는 중')).toBeHidden()

  const during = await balanceOf(member.api)

  expect(micros(during.reservedAmount)).toBe(micros(estimate))
  expect(micros(during.availableAmount)).toBe(micros(CHARGED) - micros(estimate))
  await snap('실행-중-첫-단계-성적-분석')

  await expect(page.getByText('1 / 2 단계')).toBeVisible({ timeout: 30_000 })
  // 카드 · 단계 제목 · 실시간 출력 세 곳에 두 번째 단계 이름이 보인다
  await expect(page.getByText(workflow.steps[1], { exact: true })).toHaveCount(3)
  await snap('실행-중-두번째-단계-가정통신문-작성')

  await expect(page.getByText('완료되었습니다.')).toBeVisible({ timeout: 60_000 })
  await expect(page.getByText('실행 완료')).toBeVisible()
  await expect(page.getByText('2 / 2 단계')).toBeVisible()
  await expect(page.getByText('100%', { exact: true })).toBeVisible()
  await expect(page.locator('#resultBox')).toContainText('MOCK')
  await snap('실행-완료-결과-본문')

  const pdfDownload = page.waitForEvent('download')

  await page.getByRole('button', { name: '결과 다운로드 (PDF)' }).click()

  const pdf = await pdfDownload
  const pdfBytes = await readFile((await pdf.path())!)

  expect(pdf.suggestedFilename()).toMatch(/\.pdf$/)
  expect(pdfBytes.subarray(0, 5).toString('latin1')).toBe('%PDF-')

  const jsonDownload = page.waitForEvent('download')

  await page.getByRole('button', { name: 'JSON', exact: true }).click()

  const json = await jsonDownload
  const envelope = JSON.parse(await readFile((await json.path())!, 'utf8'))

  expect(json.suggestedFilename()).toMatch(/\.json$/)
  expect(JSON.stringify(envelope.payload)).toContain('MOCK')
  await expect(page.getByRole('alert')).toHaveCount(0)

  // mock 실행은 공급자에게 줄 것이 없어 예치를 전부 돌려준다(ExecutionUsage)
  await expect.poll(async () => (await balanceOf(member.api)).reservedAmount).toBe('0.000000')
  expect(micros((await balanceOf(member.api)).availableAmount)).toBe(micros(CHARGED))

  // 올린 파일은 공유 폴더의 그 자리에 그대로 있다. 런타임은 없는 파일이면 FILE_PATH_REJECTED 로 거절하므로,
  // 성공했다는 것이 같은 자리에서 읽었다는 뜻이다(contract 3장 · R19)
  expect(await readFile(join(FILE_STORE_ROOT, 'executions', run.executionId, 'input.csv'), 'utf8')).toBe(SCORES_CSV)

  // 진행은 SSE 로 왔다 — 조회로 내려가지 않았다
  expect(streams).toContain(`/api/v1/runs/${run.executionId}/stream`)
  expect(polls).toEqual([])

  // 정산 근거는 완료 이벤트의 snapshot 원문이다(B17)
  const evidence = await evidenceOf(admin, run.executionId)

  expect(evidence.execution).toMatchObject({ status: 'SUCCEEDED', runId: run.executionId, mock: true })
  expect(evidence.failure).toBeNull()
  expect(evidence.runRecord).toMatchObject({ closedBy: 'ENGINE', runId: run.executionId })
  expect(evidence.runRecord?.response).toMatchObject({ run_id: run.executionId, status: 'succeeded' })

  await page.goto('/my/usage')
  await expect(page.getByText('환불', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('사용', { exact: true }).first()).toBeVisible()
  await snap('사용-내역-사용과-환불')
})

test('실행 도중 런타임이 죽으면 다시 뜬 런타임이 재배달받아 attempt 2 로 끝내고, 백엔드가 그 결과로 닫는다', async ({
  api,
  admin,
}) => {
  await startRuntime()
  await signup(api)
  await charge(api, CHARGED)

  const workflow = await saveLinearWorkflow(api, ['자료 정리', '요약 작성', '검토'])
  const tap = await tapEvents()

  try {
    const run = await acceptedRun(await startMockRun(api, workflow.id))

    // 첫 단계가 도는 중에 죽인다
    await expect.poll(async () => (await progressOf(api, run.executionId)).status, { timeout: 30_000 }).toBe('RUNNING')
    await stopRuntime()

    const orphaned = await progressOf(api, run.executionId)

    expect(orphaned.status).toBe('RUNNING')
    expect(orphaned.done).toBeLessThan(3)

    await startRuntime()
    await expect
      .poll(async () => (await progressOf(api, run.executionId)).status, { timeout: 90_000 })
      .toBe('SUCCEEDED')

    const events = await tap.events(run.executionId)
    const summary = events.map((event) => `${event.attempt}:${event.seq}:${event.type}`)

    expect(summary, summary.join(' ')).toContain('1:1:accepted')
    expect(summary, summary.join(' ')).toContain('2:1:accepted')
    expect(events.find((event) => event.type === 'succeeded')?.attempt, summary.join(' ')).toBe(2)
    expect(events.filter((event) => event.attempt === 1 && event.type !== 'progress' && event.type !== 'accepted'))
      .toEqual([])

    const result = await api.get(`/api/v1/runs/${run.executionId}/result`)

    expect(result.status()).toBe(200)
    expect(await result.text()).toContain('MOCK')
    await expect.poll(async () => (await balanceOf(api)).reservedAmount).toBe('0.000000')
    expect(micros((await balanceOf(api)).availableAmount)).toBe(micros(CHARGED))

    const evidence = await evidenceOf(admin, run.executionId)

    expect(evidence.runRecord).toMatchObject({ closedBy: 'ENGINE' })
    expect(evidence.ledger.filter((tx) => tx.eventType.includes('REFUND'))).toHaveLength(1)
  } finally {
    await tap.close()
  }
})

test('런타임이 내려가 있어도 접수는 202 · QUEUED 이고, 화면은 준비 중으로 기다리다 런타임이 뜨면 이어서 끝난다', async ({
  page,
  member,
  shot,
}) => {
  const snap = stillShot(page, shot)

  await stopRuntime()
  await charge(member.api, CHARGED)

  const workflow = await saveLinearWorkflow(member.api)
  const estimate = await estimateOf(member.api, workflow.id)

  await runAsMock(page)
  await page.goto(`/run/waiting?workflowId=${workflow.id}`)

  const run = await runFromWaitingScreen(page, workflow.id)

  await expect(page.getByText('실행을 준비하는 중')).toBeVisible()
  await expect(page.getByText('0 / 2 단계')).toBeVisible()

  // 디스패처가 SubmitRun 을 큐에 넣었고, 받을 런타임이 없어 기다린다. 그동안 백엔드는 닫지 않는다(B40)
  await expect.poll(() => readyCount(), { timeout: 20_000 }).toBeGreaterThan(0)
  expect((await progressOf(member.api, run.executionId)).status).toBe('QUEUED')
  expect(micros((await balanceOf(member.api)).reservedAmount)).toBe(micros(estimate))
  await expect(page.getByText('실행을 준비하는 중')).toBeVisible()
  await snap('런타임-없음-대기열에서-준비-중')

  await startRuntime()

  await expect(page.getByText('1 / 2 단계')).toBeVisible({ timeout: 60_000 })
  await snap('런타임이-뜬-뒤-이어서-진행')
  await expect(page.getByText('완료되었습니다.')).toBeVisible({ timeout: 60_000 })
  await expect(page.locator('#resultBox')).toContainText('MOCK')
  await snap('런타임이-뜬-뒤-이어서-완료')

  await expect.poll(async () => (await balanceOf(member.api)).reservedAmount).toBe('0.000000')
  expect(micros((await balanceOf(member.api)).availableAmount)).toBe(micros(CHARGED))
})

test('브로커가 멈춘 동안에도 실행 접수는 곧바로 202 · QUEUED 이고, 브로커가 돌아오면 디스패처가 다시 넘겨 한 번만 환불된다', async ({
  api,
  admin,
}) => {
  await startRuntime()
  await signup(api)
  await charge(api, CHARGED)

  const workflow = await saveLinearWorkflow(api)
  let executionId = ''

  // 멈추기 전 — 브로커가 살아 있을 때 발행 하나가 confirm 까지 끝난 상태로 둔다
  expect((await completeMockTestRun(api, await saveNode(api))).status).toBe('SUCCEEDED')

  pauseBroker()

  try {
    const requestedAt = Date.now()
    const run = await acceptedRun(await startMockRun(api, workflow.id))

    executionId = run.executionId

    // 요청 스레드는 발행을 기다리지 않는다(B5 · B6)
    expect(Date.now() - requestedAt).toBeLessThan(3_000)
    expect(run.status).toBe('QUEUED')

    // confirm 이 기한(5초) 안에 오지 않아 넘기지 못한 채로 남는다(B8)
    await waitForBackendLog(`실행을 넘기지 못했다. 다음 차례에 다시 넘긴다: execution=${executionId}`, 20_000)
    expect((await progressOf(api, executionId)).status).toBe('QUEUED')
  } finally {
    resumeBroker()
  }

  // 다시 넘겨(B9) 런타임이 돌린다. 브로커에 붙잡혀 있던 발행까지 겹쳐 나가도 한 번만 닫힌다(B11 · B19)
  await expect
    .poll(async () => (await progressOf(api, executionId)).status, { timeout: 90_000 })
    .toBe('SUCCEEDED')
  await expect.poll(async () => (await balanceOf(api)).reservedAmount).toBe('0.000000')
  expect(micros((await balanceOf(api)).availableAmount)).toBe(micros(CHARGED))

  const evidence = await evidenceOf(admin, executionId)

  expect(evidence.ledger.filter((tx) => tx.eventType.includes('REFUND'))).toHaveLength(1)
})

test('브로커가 응답하지 않는 동안 노드 테스트 시작은 매번 confirm 기한(5초) 안에 EXECUTION_ENGINE_UNAVAILABLE 로 거절된다', async ({
  api,
}) => {
  await startRuntime()
  await signup(api)

  const nodeId = await saveNode(api)
  let pending: Promise<APIResponse> | undefined

  expect((await completeMockTestRun(api, nodeId)).status).toBe('SUCCEEDED')

  pauseBroker()

  try {
    // 여러 사람이 잇따라 누르는 경우다. 몇 번째든 기한 안에 거절되어야 한다
    for (const attempt of [1, 2, 3, 4, 5, 6, 7, 8]) {
      pending = startMockTestRun(api, nodeId)

      const outcome = await Promise.race([
        pending,
        new Promise<'응답 없음'>((resolve) => setTimeout(() => resolve('응답 없음'), 15_000)),
      ])

      expect(outcome, `${attempt}번째 노드 테스트 시작이 15초 안에 답하지 않는다(B25)`).not.toBe('응답 없음')

      const refused = outcome as APIResponse

      expect(refused.status(), await refused.text()).toBe(502)
      expect((await refused.json()).error.code).toBe('EXECUTION_002')
      pending = undefined
    }
  } finally {
    resumeBroker()
    // 붙잡혀 있던 요청은 브로커가 돌아오면 끝난다. 다음 테스트로 넘기지 않는다
    await pending?.catch(() => undefined)
  }
})

test('노드 테스트도 브로커로 넘어가 런타임이 mode=test 로 돌리고, 완료 이벤트로 결과물과 함께 닫힌다', async ({ api }) => {
  await startRuntime()
  await signup(api)

  const nodeId = await saveNode(api)
  const tap = await tapEvents()

  try {
    const started = await startMockTestRun(api, nodeId)

    expect(started.status(), await started.text()).toBe(202)

    const accepted = (await started.json()).data

    // confirm 을 받은 뒤에 답한다 — 이미 런타임에 넘어간 상태다(B24)
    expect(accepted.status).toBe('RUNNING')
    expect(accepted.mock).toBe(true)

    const finished = await finishedTestRun(api, nodeId, accepted.testRunId)

    expect(finished.status).toBe('SUCCEEDED')
    expect(finished.failureCode).toBeNull()
    expect(finished.result?.payload?.text).toContain('MOCK')

    const events = await tap.events(accepted.testRunId)

    expect([...new Set(events.map((event) => event.mode))]).toEqual(['test'])
    expect(events.map((event) => event.type)).toEqual(expect.arrayContaining(['accepted', 'succeeded']))
  } finally {
    await tap.close()
  }
})

test('대기열에서 만료된 실행은 ABC_QUEUE_EXPIRED 로 닫히고, 실행 화면이 새 실패 문구를 보이며 예치가 전액 돌아온다', async ({
  page,
  member,
  admin,
  shot,
}) => {
  const snap = stillShot(page, shot)

  await stopRuntime()
  await charge(member.api, CHARGED)

  const workflow = await saveLinearWorkflow(member.api)
  const estimate = await estimateOf(member.api, workflow.id)

  await runAsMock(page)
  await page.goto(`/run/waiting?workflowId=${workflow.id}`)

  const run = await runFromWaitingScreen(page, workflow.id)

  await expect(page.getByText('실행을 준비하는 중')).toBeVisible()

  // 런타임이 없어 큐에서 기다리는 SubmitRun 을 꺼낸다. 화면이 보낸 그대로 실렸는지(B2)도 본다
  const submit = await takeSubmit(run.executionId)

  expect(submit.message.properties).toMatchObject({
    type: 'SubmitRun',
    message_id: run.executionId,
    content_type: 'application/json',
    delivery_mode: 2,
  })
  expect(submit.body).toMatchObject({
    execution_id: run.executionId,
    mode: 'run',
    comment: PROMPT,
    mock: true,
    input: null,
    files: [],
    ref_files: {},
  })
  expect(typeof submit.body.workflow).toBe('object')
  expect(micros((await balanceOf(member.api)).reservedAmount)).toBe(micros(estimate))
  await snap('대기열에서-기다리는-실행')

  // TTL 10분을 기다리지 않는다 — 브로커가 만료로 dead letter 할 때와 같은 헤더를 붙여 abc.runs.dlx 로 보낸다
  await deadLetter(submit.message, 'expired')

  await expect(page.getByText('실행이 실패했습니다')).toBeVisible({ timeout: 30_000 })
  await expect(page.getByText('실행이 중단되었습니다')).toBeVisible()
  await expect(page.getByText(QUEUE_EXPIRED_TEXT)).toBeVisible()
  await expect(page.getByText('잠시 후 다시 실행해 주세요.')).toBeVisible()
  await expect(page.getByRole('link', { name: '처음부터 다시 실행하기' })).toBeVisible()
  await snap('대기열-만료-실패-문구')

  // 예치는 전액 돌아오고, DB 에는 ABC_QUEUE_EXPIRED · platform 으로 닫혔다(B30 · B35)
  await expect.poll(async () => (await balanceOf(member.api)).reservedAmount).toBe('0.000000')
  expect(micros((await balanceOf(member.api)).availableAmount)).toBe(micros(CHARGED))

  const evidence = await evidenceOf(admin, run.executionId)

  expect(evidence.execution.status).toBe('FAILED')
  expect(evidence.failure?.failureCode).toBe('ABC_QUEUE_EXPIRED')
  expect(evidence.failure?.fault.toLowerCase()).toBe('platform')
  expect(evidence.runRecord).toMatchObject({ closedBy: 'DEAD_LETTERED', response: null })

  const result = await member.api.get(`/api/v1/runs/${run.executionId}/result`)

  expect(result.status()).toBe(404)
  expect((await result.json()).error.code).toBe('EXECUTION_008')

  // 닫힌 뒤 다시 물어도(새로고침 · 나중에 다시 열기) 같은 사유가 보여야 한다
  const progress = await progressOf(member.api, run.executionId)

  expect(progress.status).toBe('FAILED')
  expect.soft(progress.failure, 'GET /api/v1/runs/{id} 가 DB 의 실패 사유를 돌려주지 않는다').toMatchObject({
    errorCode: 'ABC_QUEUE_EXPIRED',
    fault: 'platform',
  })

  await page.reload()
  await expect(page.getByText('실행이 실패했습니다')).toBeVisible()
  await snap('대기열-만료-새로고침-뒤')
  await expect.soft(page.getByText(QUEUE_EXPIRED_TEXT), '새로고침하면 만료 문구가 사라진다').toBeVisible()

  await page.goto('/my/usage')
  await expect(page.getByText('환불', { exact: true }).first()).toBeVisible()
  await snap('사용-내역-만료-환불')
})
