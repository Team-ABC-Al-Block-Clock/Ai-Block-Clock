import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { API_BASE_URL } from '@/api/client'
import { startExecution } from '@/api/executions'
import { server } from '@/test/server'

const REQUEST_ID = '0b3f1c5e-1f4a-4c6a-9d2b-8f6a1c2d3e4f'

const excel = (name = '성적.xlsx') =>
  new File(['판매,수량\n1,2\n'], name, {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  })

/** 핸들러가 실제로 받은 폼을 꺼내 본다. mock 핸들러는 그대로 두고 한 번만 가로챈다. */
const receivedForm = async (input: Parameters<typeof startExecution>[0]) => {
  let received: FormData | null = null
  server.use(
    http.post(`${API_BASE_URL}/runs`, async ({ request }) => {
      received = await request.formData()
      return HttpResponse.json({ status: 'SUCCESS', data: { executionId: 'exe_001' } })
    }),
  )
  await startExecution(input)
  return received as unknown as FormData
}

describe('startExecution 의 multipart 요청', () => {
  it('폼이 요청 본문에 실려 서버까지 간다', async () => {
    const form = await receivedForm({
      workflowId: 'wf_001',
      requestId: REQUEST_ID,
      comment: '난이도가 높았습니다',
      file: excel(),
    })

    expect(form.get('workflowId')).toBe('wf_001')
    expect(form.get('requestId')).toBe(REQUEST_ID)
    expect(form.get('comment')).toBe('난이도가 높았습니다')
  })

  it('파일이 이름과 내용을 지킨 채 건너간다', async () => {
    const form = await receivedForm({
      workflowId: 'wf_001',
      requestId: REQUEST_ID,
      file: excel('매출.csv'),
    })

    const file = form.get('file')
    expect(file).toBeInstanceOf(File)
    expect((file as File).name).toBe('매출.csv')
    await expect((file as File).text()).resolves.toBe('판매,수량\n1,2\n')
  })

  it('mock 핸들러가 접수 번호를 돌려준다', async () => {
    const started = await startExecution({
      workflowId: 'wf_001',
      requestId: REQUEST_ID,
      file: excel(),
    })

    expect(started.executionId).toEqual(expect.any(String))
  })
})
