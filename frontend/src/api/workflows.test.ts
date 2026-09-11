import { afterEach, describe, expect, it } from 'vitest'

import { ApiError } from '@/api/errors'
import { deleteWorkflow, getMyWorkflows } from '@/api/workflows'
import { resetWorkflows } from '@/mocks/fixtures/workflows'

/**
 * 내 워크플로우 목록과 삭제. api 레이어 + mock 핸들러 + 커서 모듈을 한 번에 통과시킨다.
 *
 * 삭제는 픽스처에서 항목을 실제로 없애므로 매 테스트 뒤에 되돌린다 — 길이만 잘라내는
 * 방식으로는 중간에서 지운 건이 복구되지 않는다.
 */
afterEach(() => resetWorkflows())

describe('getMyWorkflows', () => {
  it('명세 기본값 10건과 다음 커서를 준다', async () => {
    const page = await getMyWorkflows()

    expect(page.content).toHaveLength(10)
    // 픽스처가 12건이라 다음 장이 있다. 10건 이하로 줄면 무한 스크롤 검증이 사라진다.
    expect(page.hasNext).toBe(true)
    expect(page.nextCursor).toBeTruthy()
    expect(page).not.toHaveProperty('status')
  })

  it('목록 응답은 확정 명세의 필드만 담는다', async () => {
    const [first] = (await getMyWorkflows()).content

    // 노드 개수와 검증 상태는 반환하지 않는다(명세 「필드 최소화」).
    // 삭제 가능 여부(registeredAsAgent·running)도 응답에 없다 — 핸들러만 아는 값이다.
    expect(Object.keys(first).sort()).toEqual([
      'executionCount',
      'lastExecutedAt',
      'name',
      'summary',
      'workflowId',
    ])
  })

  it('기본 정렬은 마지막 실행 역순이고, 실행하지 않은 것은 뒤로 밀린다', async () => {
    const page = await getMyWorkflows({ size: 30 })
    const times = page.content.map((w) => w.lastExecutedAt)

    const never = times.filter((t) => t === null)
    expect(never.length).toBeGreaterThan(0)
    // null 이 한 번 나오면 그 뒤는 전부 null 이어야 한다.
    expect(times.slice(times.indexOf(null)).every((t) => t === null)).toBe(true)

    const ran = times.filter((t): t is string => t !== null).map(Date.parse)
    expect(ran).toEqual([...ran].sort((a, b) => b - a))
  })

  it('LATEST 는 등록 시각 역순이라 순서가 달라진다', async () => {
    const recent = await getMyWorkflows({ size: 30 })
    const latest = await getMyWorkflows({ sort: 'LATEST', size: 30 })

    // 등록 시각은 응답에 없다(명세). 순서가 실제로 갈리는지로만 확인한다.
    expect(latest.content[0].workflowId).not.toBe(recent.content[0].workflowId)
    expect(latest.content.map((w) => w.workflowId)).not.toEqual(
      recent.content.map((w) => w.workflowId),
    )
  })

  it('keyword 는 이름만 검색한다', async () => {
    const byName = await getMyWorkflows({ keyword: '리포트', size: 30 })
    expect(byName.content.length).toBeGreaterThan(0)
    for (const workflow of byName.content) expect(workflow.name).toContain('리포트')

    // 명세상 검색 대상은 이름뿐이다 — 한 줄 설명에만 있는 말은 걸리지 않는다.
    const bySummary = await getMyWorkflows({ keyword: '폴더', size: 30 })
    expect(bySummary.content).toHaveLength(0)
  })

  it('sort·size 가 올바르지 않으면 COMMON_001', async () => {
    await expect(getMyWorkflows({ sort: 'REVENUE' as unknown as 'LATEST' })).rejects.toMatchObject({
      code: 'COMMON_001',
      status: 400,
    })
    await expect(getMyWorkflows({ size: 31 })).rejects.toMatchObject({ code: 'COMMON_001' })
  })

  it('커서로 이어 받으면 중복도 누락도 없다', async () => {
    const whole = await getMyWorkflows({ size: 30 })
    const expected = whole.content.map((w) => w.workflowId)

    const collected: string[] = []
    let cursor: string | undefined
    let last
    do {
      last = await getMyWorkflows({ cursor, size: 5 })
      collected.push(...last.content.map((w) => w.workflowId))
      cursor = last.nextCursor ?? undefined
    } while (cursor)

    expect(collected).toEqual(expected)
    expect(new Set(collected).size).toBe(collected.length)
    expect(last.hasNext).toBe(false)
  })
})

describe('deleteWorkflow', () => {
  it('삭제하면 ID 와 시각을 주고, 목록에서 사라진다', async () => {
    const res = await deleteWorkflow('wf_002')

    expect(res.workflowId).toBe('wf_002')
    // 되돌릴 수 없는 동작이라 화면이 '언제' 를 말할 수 있어야 한다.
    expect(Number.isNaN(Date.parse(res.deletedAt))).toBe(false)
    expect(Object.keys(res).sort()).toEqual(['deletedAt', 'workflowId'])

    const page = await getMyWorkflows({ size: 30 })
    expect(page.content.map((w) => w.workflowId)).not.toContain('wf_002')
    expect(page.content).toHaveLength(11)
  })

  it('없는 ID 는 WORKFLOW_002 / 404', async () => {
    const error = await deleteWorkflow('wf_없음').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ code: 'WORKFLOW_002', status: 404 })
  })

  it('에이전트로 등록된 것은 WORKFLOW_009 / 409 이고 지워지지 않는다', async () => {
    // 소유권이 블록체인에 기록된 상태라 삭제할 수 없다(명세 참고사항).
    await expect(deleteWorkflow('wf_011')).rejects.toMatchObject({
      code: 'WORKFLOW_009',
      status: 409,
    })

    const page = await getMyWorkflows({ size: 30 })
    expect(page.content.map((w) => w.workflowId)).toContain('wf_011')
  })

  it('실행이 진행 중이면 WORKFLOW_010 / 409 이고 지워지지 않는다', async () => {
    await expect(deleteWorkflow('wf_012')).rejects.toMatchObject({
      code: 'WORKFLOW_010',
      status: 409,
    })

    const page = await getMyWorkflows({ size: 30 })
    expect(page.content.map((w) => w.workflowId)).toContain('wf_012')
  })

  it('같은 것을 두 번 지우면 두 번째는 WORKFLOW_002', async () => {
    // 멱등이 아니다. 화면은 '이미 삭제된 워크플로우' 문구를 내고 목록을 맞춰야 한다.
    await deleteWorkflow('wf_004')
    await expect(deleteWorkflow('wf_004')).rejects.toMatchObject({ code: 'WORKFLOW_002' })
  })
})
