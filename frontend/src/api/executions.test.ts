import type { AxiosAdapter } from 'axios'
import { http } from 'msw'
import { describe, expect, it } from 'vitest'

import { API_BASE_URL, client } from '@/api/client'
import { clearCsrfToken } from '@/api/csrf'
import { ApiError } from '@/api/errors'
import { getRunProgress, startRun, type ExecutionProgress } from '@/api/executions'
import { fail, ok } from '@/mocks/response'
import { server } from '@/test/server'

const PROGRESS: ExecutionProgress = {
  executionId: '8a1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13',
  status: 'RUNNING',
  done: 1,
  total: 2,
  percent: 50,
  currentStepName: '가정통신문 작성',
  steps: [
    { name: '성적 분석', status: 'succeeded' },
    { name: '가정통신문 작성', status: 'running' },
  ],
  failure: null,
}

describe('startRun', () => {
  it('접수 번호와 단계 수를 준다', async () => {
    server.use(
      http.post(`${API_BASE_URL}/runs`, () =>
        ok({ executionId: PROGRESS.executionId, status: 'QUEUED', stepCount: 2 }),
      ),
    )

    const started = await startRun({ workflowId: 'w-1', comment: '이번 달은 난이도가 높았습니다' })

    expect(started.executionId).toBe(PROGRESS.executionId)
    expect(started.stepCount).toBe(2)
  })

  it('파일이 함께 갈 수 있어 폼으로 보낸다 — 서버가 multipart 만 받는다', async () => {
    const sheet = new File(['a,b\n1,2'], 'scores.csv', { type: 'text/csv' })

    const parts = await capturePartsOf(() =>
      startRun({ workflowId: 'w-1', comment: '한 줄', inputFile: sheet, mock: true }),
    )

    expect(parts.get('workflowId')).toBe('w-1')
    expect(parts.get('comment')).toBe('한 줄')
    expect(parts.get('file')).toBe(sheet)
    // 폼은 값을 문자열로 만든다. 서버도 'true' 로 읽는다.
    expect(parts.get('mock')).toBe('true')
  })

  it('비어 있는 선택 항목은 파트를 아예 넣지 않는다', async () => {
    // 빈 값을 넣으면 서버가 「보냈다」로 읽는다.
    const parts = await capturePartsOf(() => startRun({ workflowId: 'w-1' }))

    expect([...parts.keys()]).toEqual(['workflowId'])
  })

  it('실행 엔진에 맡기지 못하면 코드로 가를 수 있게 던진다', async () => {
    server.use(
      http.post(`${API_BASE_URL}/runs`, () =>
        fail(502, 'EXECUTION_002', '실행을 맡기지 못했습니다. 잠시 후 다시 시도해 주세요.'),
      ),
    )

    await expect(startRun({ workflowId: 'w-1' })).rejects.toMatchObject({
      code: 'EXECUTION_002',
      status: 502,
    })
    await expect(startRun({ workflowId: 'w-1' })).rejects.toBeInstanceOf(ApiError)
  })
})

/**
 * 요청을 보내지 않고 axios 가 들고 있는 본문만 가로챈다 — `auth.test.ts` 와 같은 방법이다.
 *
 * jsdom 은 파일 파트를 실어 보내지 못해 msw 까지 가면 본문이 깨진다. 확인할 것은
 * 「파트가 실렸는가」 하나라 직렬화까지 갈 필요가 없다.
 */
async function capturePartsOf(send: () => Promise<unknown>): Promise<FormData> {
  const previous = client.defaults.adapter
  let captured: FormData | undefined

  const capture: AxiosAdapter = (config) => {
    const csrf = config.url?.endsWith('/auth/csrf')
    if (!csrf) captured = config.data as FormData

    return Promise.resolve({
      data: {
        status: 'SUCCESS',
        data: csrf ? { headerName: 'X-CSRF-TOKEN', token: 'capture-token' } : {},
      },
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    })
  }

  clearCsrfToken()
  client.defaults.adapter = capture

  try {
    await send()
  } finally {
    client.defaults.adapter = previous
    clearCsrfToken()
  }

  if (!captured) throw new Error('요청 본문을 가로채지 못했다')

  return captured
}

describe('getRunProgress', () => {
  it('진행 상황을 봉투를 벗겨 준다', async () => {
    server.use(http.get(`${API_BASE_URL}/runs/:id`, () => ok(PROGRESS)))

    const progress = await getRunProgress(PROGRESS.executionId)

    expect(progress).toEqual(PROGRESS)
    expect(progress).not.toHaveProperty('status', 'SUCCESS')
  })

  it('멎은 실행은 왜 멎었는지 함께 준다', async () => {
    server.use(
      http.get(`${API_BASE_URL}/runs/:id`, () =>
        ok({
          ...PROGRESS,
          status: 'FAILED',
          failure: {
            stepName: '가정통신문 작성',
            errorCode: 'MODEL_API_ERROR',
            fault: 'supplier',
          },
        }),
      ),
    )

    const progress = await getRunProgress(PROGRESS.executionId)

    expect(progress.failure).toEqual({
      stepName: '가정통신문 작성',
      errorCode: 'MODEL_API_ERROR',
      fault: 'supplier',
    })
  })

  it('까닭을 되찾지 못하면 fault 가 비어 온다 — 플랫폼 과실이 아니다', async () => {
    server.use(
      http.get(`${API_BASE_URL}/runs/:id`, () =>
        ok({
          ...PROGRESS,
          status: 'FAILED',
          failure: { stepName: null, errorCode: 'ABC_RUNTIME_UNREPORTED', fault: null },
        }),
      ),
    )

    const progress = await getRunProgress(PROGRESS.executionId)

    expect(progress.failure?.fault).toBeNull()
  })

  it('남의 실행은 찾을 수 없다고 한다', async () => {
    server.use(
      http.get(`${API_BASE_URL}/runs/:id`, () =>
        fail(404, 'EXECUTION_001', '실행을 찾을 수 없습니다.'),
      ),
    )

    await expect(getRunProgress('없는-실행')).rejects.toMatchObject({ code: 'EXECUTION_001' })
  })
})
