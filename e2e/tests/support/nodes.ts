import { expect } from '@playwright/test'

import { expectOk, type Api } from './auth'

export interface NodeTestRun {
  testRunId: string
  nodeId: string
  status: 'REQUESTED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'
  mock: boolean
  failureCode: string | null
  result: { payload?: { text?: string } } | null
}

/** 판매자 노드 하나를 만든다. 노드 테스트(POST /api/v1/nodes/{id}/test-runs)의 대상이다. */
export async function saveNode(api: Api, name = '성적 요약'): Promise<string> {
  const response = await expectOk(await api.post('/api/v1/nodes', { name, prompt: `${name}을(를) 한다` }))

  return (await response.json()).data.nodeId
}

/** 노드 테스트를 맡긴다. 모델을 부르지 않게 늘 mock 이다. 응답을 그대로 돌려준다 — 거절도 보려고. */
export function startMockTestRun(api: Api, nodeId: string) {
  return api.post(`/api/v1/nodes/${nodeId}/test-runs`, { comment: '중간고사 점수를 지난 학기와 비교해 주세요', mock: true })
}

export async function testRunOf(api: Api, nodeId: string, testRunId: string): Promise<NodeTestRun> {
  const response = await expectOk(await api.get(`/api/v1/nodes/${nodeId}/test-runs/${testRunId}`))

  return (await response.json()).data
}

/** 노드 테스트가 끝날 때까지 기다려 마지막 모습을 돌려준다. */
export async function finishedTestRun(api: Api, nodeId: string, testRunId: string): Promise<NodeTestRun> {
  await expect
    .poll(async () => (await testRunOf(api, nodeId, testRunId)).status, { timeout: 60_000 })
    .toMatch(/^(SUCCEEDED|FAILED)$/)

  return testRunOf(api, nodeId, testRunId)
}

/** 노드 테스트를 맡기고 끝날 때까지 기다린다. 접수가 거절되면 실패다. */
export async function completeMockTestRun(api: Api, nodeId: string): Promise<NodeTestRun> {
  const started = await startMockTestRun(api, nodeId)

  expect(started.status(), await started.text()).toBe(202)

  return finishedTestRun(api, nodeId, (await started.json()).data.testRunId)
}
