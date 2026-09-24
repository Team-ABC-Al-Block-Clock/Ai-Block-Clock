import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from 'msw'

import { API_BASE_URL, client } from '@/api/client'

import { ApiError } from '@/api/errors'
import {
  describeRunFileProblem,
  estimateCost,
  getExecutionProgress,
  startExecution,
} from '@/api/executions'
import { saveWorkflow, type WorkflowGraph } from '@/api/workflows'
import { CREDIT_BALANCE } from '@/mocks/fixtures/credits'
import { fail, ok } from '@/mocks/response'
import { server } from '@/test/server'

/** api 레이어 + 실행 mock 을 한 번에 통과시킨다. 견적은 저장된 워크플로우를 읽는다. */

afterEach(() => {
  localStorage.removeItem('mockWorkflows')
  localStorage.removeItem('mockExecutions')
})

const excel = (name = '성적.xlsx', size = 1024) =>
  new File([new Uint8Array(size)], name, { type: '' })

/** 접수에는 누름마다 새로 만드는 UUID 가 필요하다. 값 자체는 무엇이든 된다. */
const REQUEST_ID = '5d3f9c1e-7b2a-4e8f-9a61-2c4b8d0e1f37'

/** `agt_001` 이 150 이라 다섯 개면 750 — 잔액 640 을 넘긴다. */
const chainOf = (sourceId: string, count: number): WorkflowGraph => ({
  nodes: Array.from({ length: count }, (_, i) => ({
    nodeKey: `n${i + 1}`,
    sourceType: sourceId.startsWith('agt') ? ('AGENT' as const) : ('NODE' as const),
    sourceId,
  })),
  edges: Array.from({ length: count - 1 }, (_, i) => ({
    edgeKey: `e${i + 1}`,
    sourceNodeKey: `n${i + 1}`,
    targetNodeKey: `n${i + 2}`,
  })),
})

describe('estimateCost', () => {
  it('픽스처 워크플로우의 노드 단가를 더해 준다', async () => {
    // wf_001 은 node_001·002·003 — 4 + 12 + 6.
    const estimate = await estimateCost('wf_001')

    expect(estimate.items).toHaveLength(3)
    expect(estimate.estimatedAmount).toBe('22.000000')
    expect(estimate.availableAmount).toBe(CREDIT_BALANCE.availableAmount)
  })

  it('금액은 전부 소수점 여섯 자리 문자열이다', async () => {
    const estimate = await estimateCost('wf_001')

    for (const value of [
      estimate.estimatedAmount,
      estimate.availableAmount,
      estimate.shortageAmount,
    ])
      expect(value).toMatch(/^\d+\.\d{6}$/)
    expect(estimate.items.every((item) => /^\d+\.\d{6}$/.test(item.price))).toBe(true)
  })

  it('한 줄은 명세의 네 필드만 담는다', async () => {
    const [first] = (await estimateCost('wf_001')).items

    expect(Object.keys(first).sort()).toEqual(['name', 'nodeKey', 'sourceType', 'price'].sort())
  })

  it('잔액이 넉넉하면 부족액 0 에 실행 가능이다', async () => {
    const estimate = await estimateCost('wf_001')

    expect(estimate.shortageAmount).toBe('0.000000')
    expect(estimate.executable).toBe(true)
  })

  it('잔액을 넘으면 부족액과 함께 실행 불가를 준다', async () => {
    const saved = await saveWorkflow({ ...chainOf('agt_001', 5), name: '비싼 흐름' })

    const estimate = await estimateCost(saved.workflowId)

    expect(estimate.estimatedAmount).toBe('750.000000')
    expect(estimate.shortageAmount).toBe('110.000000')
    expect(estimate.executable).toBe(false)
  })

  it('저장한 구성이 그대로 견적에 반영된다', async () => {
    const saved = await saveWorkflow({ ...chainOf('node_004', 2), name: '요약 두 번' })

    const estimate = await estimateCost(saved.workflowId)

    // node_004 가 15 — 캔버스를 고치면 금액도 따라 움직여야 한다.
    expect(estimate.estimatedAmount).toBe('30.000000')
    expect(estimate.items.map((item) => item.name)).toEqual(['요약 생성', '요약 생성'])
  })

  it('없는 워크플로우는 WORKFLOW_002 다', async () => {
    await expect(estimateCost('wf_999')).rejects.toMatchObject({ code: 'WORKFLOW_002' })
    await expect(estimateCost('wf_999')).rejects.toBeInstanceOf(ApiError)
  })
})

describe('describeRunFileProblem', () => {
  it('엑셀과 CSV 만 통과시킨다', () => {
    expect(describeRunFileProblem(excel('성적.xlsx'))).toBeNull()
    expect(describeRunFileProblem(excel('성적.CSV'))).toBeNull()
    expect(describeRunFileProblem(excel('성적.pdf'))).not.toBeNull()
    // 서버도 이름 끝만 본다 — 앞에 허용 확장자가 섞여도 통과시키지 않는다.
    expect(describeRunFileProblem(excel('성적.xlsx.exe'))).not.toBeNull()
  })

  it('빈 파일과 10MB 초과를 막는다', () => {
    expect(describeRunFileProblem(excel('성적.csv', 0))).not.toBeNull()
    expect(describeRunFileProblem(excel('성적.xlsx', 10 * 1024 * 1024 + 1))).not.toBeNull()
    // 서버가 10MB 까지는 받는다. 경계에서 화면이 먼저 막으면 안 된다.
    expect(describeRunFileProblem(excel('성적.xlsx', 10 * 1024 * 1024))).toBeNull()
  })
})

/**
 * 접수는 폼 조립까지만 확인한다. jsdom 의 `FormData` 는 undici 의 것과 달라 요청에 실리지
 * 못하고 `[object FormData]` 라는 문자열로 나간다 — 핸들러까지 가지 못하는 것은 환경 탓이라
 * 여기서 흉내 내 봐야 서로 다른 것을 검사하게 된다. 접수 경로 자체는 브라우저에서 확인한다.
 */
describe('startExecution', () => {
  const sentForm = async (input: Parameters<typeof startExecution>[0]) => {
    const post = vi.spyOn(client, 'post').mockResolvedValue({} as never)
    await startExecution(input)
    const form = post.mock.calls[0][1] as FormData
    post.mockRestore()
    return form
  }

  it('워크플로우·누름 식별자·코멘트·파일을 폼에 담는다', async () => {
    const file = excel()
    const form = await sentForm({
      workflowId: 'wf_001',
      requestId: REQUEST_ID,
      comment: '난이도가 높았습니다',
      file,
    })

    expect(form.get('workflowId')).toBe('wf_001')
    expect(form.get('requestId')).toBe(REQUEST_ID)
    expect(form.get('comment')).toBe('난이도가 높았습니다')
    expect(form.get('file')).toBe(file)
  })

  it('빈 코멘트와 고르지 않은 파일은 아예 넣지 않는다', async () => {
    const form = await sentForm({
      workflowId: 'wf_001',
      requestId: REQUEST_ID,
      comment: '   ',
      file: null,
    })

    expect(form.has('comment')).toBe(false)
    expect(form.has('file')).toBe(false)
    // 서버는 값이 온 칸만 본다. 빈 문자열을 넣으면 「코멘트가 왔는데 비었다」가 된다.
    expect([...form.keys()].sort()).toEqual(['requestId', 'workflowId'])
  })

  it('mock 은 켰을 때만 싣는다', async () => {
    const base = { workflowId: 'wf_001', requestId: REQUEST_ID }

    expect((await sentForm(base)).has('mock')).toBe(false)
    expect((await sentForm({ ...base, mock: true })).get('mock')).toBe('true')
  })

  it('경로는 /executions 가 아니라 /runs 다', async () => {
    const post = vi.spyOn(client, 'post').mockResolvedValue({} as never)
    await startExecution({ workflowId: 'wf_001', requestId: REQUEST_ID })

    expect(post.mock.calls[0][0]).toBe('/runs')
    post.mockRestore()
  })
})

/** 접수를 거치지 않고 실행을 심는다. mock 핸들러가 읽는 저장소와 같은 모양이다. */
const seedRun = (executionId: string, stepNames: string[], agoMs = 0) => {
  localStorage.setItem(
    'mockExecutions',
    JSON.stringify({ [executionId]: { executionId, startedAt: Date.now() - agoMs, stepNames } }),
  )
}

/** 접수 직후 QUEUED 로 두는 시간과 한 단계에 쓰는 시간. 핸들러와 맞춘다. */
const QUEUED_MS = 900
const STEP_MS = 2_500

describe('getExecutionProgress', () => {
  it('접수 직후에는 QUEUED 에 진행률 0 이다', async () => {
    seedRun('exe_1', ['파일 수집', '내용 분석', '분류 및 태깅'])

    const progress = await getExecutionProgress('exe_1')

    expect(progress.status).toBe('QUEUED')
    expect(progress.done).toBe(0)
    expect(progress.percent).toBe(0)
    expect(progress.currentStepName).toBeNull()
    expect(progress.steps.every((step) => step.status === 'pending')).toBe(true)
  })

  it('도는 동안에는 끝난 단계·도는 단계·남은 단계가 갈린다', async () => {
    seedRun('exe_2', ['파일 수집', '내용 분석', '분류 및 태깅'], QUEUED_MS + STEP_MS + 100)

    const progress = await getExecutionProgress('exe_2')

    expect(progress.status).toBe('RUNNING')
    expect(progress.done).toBe(1)
    expect(progress.total).toBe(3)
    expect(progress.currentStepName).toBe('내용 분석')
    expect(progress.steps.map((step) => step.status)).toEqual(['succeeded', 'running', 'pending'])
  })

  it('끝나면 SUCCEEDED 에 100% 이고 모든 단계가 완료다', async () => {
    seedRun('exe_3', ['파일 수집', '내용 분석'], QUEUED_MS + STEP_MS * 2 + 100)

    const progress = await getExecutionProgress('exe_3')

    expect(progress.status).toBe('SUCCEEDED')
    expect(progress.percent).toBe(100)
    expect(progress.done).toBe(progress.total)
    expect(progress.currentStepName).toBeNull()
    expect(progress.steps.every((step) => step.status === 'succeeded')).toBe(true)
  })

  it('한참 지나도 끝난 지점을 넘지 않는다', async () => {
    seedRun('exe_4', ['파일 수집', '내용 분석'], QUEUED_MS + STEP_MS * 50)

    const progress = await getExecutionProgress('exe_4')

    expect(progress.done).toBe(2)
    expect(progress.percent).toBe(100)
  })

  it('없는 실행은 EXECUTION_001 이다', async () => {
    await expect(getExecutionProgress('exe_999')).rejects.toBeInstanceOf(ApiError)
    await expect(getExecutionProgress('exe_999')).rejects.toMatchObject({ code: 'EXECUTION_001' })
  })
})

/** 스트림·폴링이 함께 읽는 모양. 실패 사유는 !84 가 실어 준다. */
describe('진행 상황의 실패 정보', () => {
  const PROGRESS = {
    executionId: 'exe_1',
    status: 'FAILED',
    done: 1,
    total: 2,
    percent: 50,
    currentStepName: null,
    steps: [
      { name: '성적 분석', status: 'succeeded' },
      { name: '가정통신문 작성', status: 'failed' },
    ],
  }

  it('멎은 실행은 왜 멎었는지 함께 준다', async () => {
    server.use(
      http.get(`${API_BASE_URL}/runs/:id`, () =>
        ok({
          ...PROGRESS,
          failure: { stepName: '가정통신문 작성', errorCode: 'MODEL_API_ERROR', fault: 'supplier' },
        }),
      ),
    )

    const progress = await getExecutionProgress('exe_1')

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
          failure: { stepName: null, errorCode: 'ABC_RUNTIME_UNREPORTED', fault: null },
        }),
      ),
    )

    const progress = await getExecutionProgress('exe_1')

    expect(progress.failure?.fault).toBeNull()
  })

  it('실행 엔진에 맡기지 못하면 코드로 가를 수 있게 던진다', async () => {
    server.use(
      http.post(`${API_BASE_URL}/runs`, () =>
        fail(502, 'EXECUTION_002', '실행을 맡기지 못했습니다. 잠시 후 다시 시도해 주세요.'),
      ),
    )

    await expect(
      startExecution({ workflowId: 'wf_001', requestId: REQUEST_ID }),
    ).rejects.toMatchObject({ code: 'EXECUTION_002', status: 502 })
  })
})
