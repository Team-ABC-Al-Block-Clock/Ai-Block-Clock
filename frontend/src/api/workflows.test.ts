import { afterEach, describe, expect, it } from 'vitest'

import { ApiError } from '@/api/errors'
import {
  deleteWorkflow,
  getMyWorkflows,
  getNodePalette,
  getWorkflow,
  replaceWorkflow,
  saveWorkflow,
  type WorkflowGraph,
} from '@/api/workflows'
import { resetWorkflows } from '@/mocks/fixtures/workflows'

/**
 * 내 워크플로우 목록과 삭제. api 레이어 + mock 핸들러 + 커서 모듈을 한 번에 통과시킨다.
 *
 * 삭제는 픽스처에서 항목을 실제로 없애므로 매 테스트 뒤에 되돌린다 — 길이만 잘라내는
 * 방식으로는 중간에서 지운 건이 복구되지 않는다.
 */
afterEach(() => {
  resetWorkflows()
  // 저장 mock 이 localStorage 에 쌓인다. 비우지 않으면 테스트끼리 샌다.
  localStorage.removeItem('mockWorkflows')
})

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

describe('getNodePalette', () => {
  it('명세 기본값 10건과 적용된 scope 를 함께 준다', async () => {
    const page = await getNodePalette({ scope: 'ALL' })

    expect(page.content).toHaveLength(10)
    expect(page.scope).toBe('ALL')
    expect(page.hasNext).toBe(true)
    expect(page.nextCursor).toBeTruthy()
    expect(page).not.toHaveProperty('status')
  })

  it('MINE 은 내 것만 주고, ALL 은 다른 사람의 에이전트까지 준다', async () => {
    const mine = await getNodePalette({ scope: 'MINE', size: 30 })
    const all = await getNodePalette({ scope: 'ALL', size: 30 })

    expect(mine.content.every((item) => item.mine)).toBe(true)
    expect(all.content.some((item) => !item.mine)).toBe(true)
    expect(all.content.length).toBeGreaterThan(mine.content.length)
  })

  it('MINE 도 두 번째 장이 있다', async () => {
    // 내 것이 10건 이하로 줄면 판매자 모드의 무한 스크롤을 확인할 방법이 없다.
    const first = await getNodePalette({ scope: 'MINE' })
    expect(first.hasNext).toBe(true)

    const second = await getNodePalette({ scope: 'MINE', cursor: first.nextCursor ?? undefined })
    const ids = [...first.content, ...second.content].map((item) => item.itemId)
    expect(new Set(ids).size).toBe(ids.length)
  })

  it('내 것이 먼저, 그다음 많이 쓰인 순이다', async () => {
    const { content } = await getNodePalette({ scope: 'ALL', size: 30 })
    const flags = content.map((item) => item.mine)

    expect(flags.slice(0, flags.lastIndexOf(true) + 1).every(Boolean)).toBe(true)
    const used = content.filter((item) => item.mine).map((item) => item.executionCount)
    expect(used).toEqual([...used].sort((a, b) => b - a))
  })

  it('항목은 정보 카드에 필요한 것을 모두 담는다', async () => {
    const [first] = (await getNodePalette({ scope: 'ALL' })).content

    // i 버튼이 뒤집어 보여주는 넷(사용 횟수·단가·유형·설명)이 여기 다 있어야
    // 카드마다 상세를 다시 부르지 않는다.
    expect(Object.keys(first).sort()).toEqual([
      'executionCount',
      'iconUrl',
      'itemId',
      'itemType',
      'mine',
      'name',
      'nodeType',
      'owner',
      'price',
      'summary',
    ])
  })

  it('다른 사람의 에이전트는 내부 구성을 공개하지 않는다', async () => {
    const { content } = await getNodePalette({ scope: 'ALL', size: 30 })
    const theirs = content.filter((item) => !item.mine)

    expect(theirs.length).toBeGreaterThan(0)
    expect(theirs.every((item) => item.nodeType === 'AGENT')).toBe(true)
    expect(theirs.every((item) => !('prompt' in item) && !('nodes' in item))).toBe(true)
  })

  it('keyword 는 이름만 본다', async () => {
    const byName = await getNodePalette({ scope: 'ALL', keyword: '통계', size: 30 })
    expect(byName.content.map((item) => item.name)).toEqual(['통계 분석'])

    // '평균과 분포를 계산합니다' 는 설명이라 걸리지 않는다.
    const bySummary = await getNodePalette({ scope: 'ALL', keyword: '평균과 분포', size: 30 })
    expect(bySummary.content).toHaveLength(0)
  })

  it('설명이나 아이콘이 없는 건이 섞여 있다', async () => {
    const { content } = await getNodePalette({ scope: 'ALL', size: 30 })

    // 화면이 빈칸이나 깨진 이미지를 내는지는 이 건들에서만 드러난다.
    expect(content.some((item) => item.summary === null)).toBe(true)
    expect(content.some((item) => item.iconUrl === null)).toBe(true)
    expect(content.some((item) => item.executionCount === 0)).toBe(true)
  })

  it('scope 가 없거나 명세에 없는 값이면 COMMON_001', async () => {
    const missing = await getNodePalette({} as never).catch((e: unknown) => e)
    expect(missing).toBeInstanceOf(ApiError)
    expect(missing).toMatchObject({ code: 'COMMON_001', status: 400 })

    await expect(getNodePalette({ scope: 'EVERYTHING' as never })).rejects.toMatchObject({
      code: 'COMMON_001',
    })
  })

  it('size 가 범위를 벗어나면 COMMON_001, 커서가 어긋나면 MARKET_003', async () => {
    await expect(getNodePalette({ scope: 'ALL', size: 99 })).rejects.toMatchObject({
      code: 'COMMON_001',
    })
    await expect(getNodePalette({ scope: 'ALL', cursor: '망가진커서' })).rejects.toMatchObject({
      code: 'MARKET_003',
    })
  })

  it('시나리오 스위치로 강제한 실패가 ApiError 로 온다', async () => {
    // 핸들러가 scene() 을 부르는지 확인한다 — 빠뜨리면 ?mockFail 이 이 화면에만 안 먹는다.
    localStorage.setItem(
      'abc:mock-scenario',
      JSON.stringify({ delayMs: 0, failCode: 'COMMON_001', failStatus: 400 }),
    )
    try {
      await expect(getNodePalette({ scope: 'ALL' })).rejects.toBeInstanceOf(ApiError)
    } finally {
      localStorage.removeItem('abc:mock-scenario')
    }
  })
})

/**
 * 워크플로우 저장 왕복. api 레이어 + mock 핸들러 + 응답 인터셉터를 한 번에 통과시킨다.
 *
 * 회차와 거절 코드는 화면 분기가 그대로 읽는 값이라, 서버와 같은 코드로 돌아오는지까지 본다.
 */
const line = (...sourceIds: string[]): WorkflowGraph => ({
  nodes: sourceIds.map((sourceId, i) => ({
    nodeKey: `n${i + 1}`,
    sourceType: 'NODE' as const,
    sourceId,
  })),
  edges: sourceIds.slice(1).map((_, i) => ({
    edgeKey: `e${i + 1}`,
    sourceNodeKey: `n${i + 1}`,
    targetNodeKey: `n${i + 2}`,
  })),
})

const TWO = () => line('node_001', 'node_002')

describe('saveWorkflow', () => {
  it('처음 저장하면 회차 1로 돌아온다', async () => {
    const result = await saveWorkflow({ ...TWO(), name: '문서 정리' })

    expect(result.revision).toBe(1)
    expect(result.name).toBe('문서 정리')
    expect(result.workflowId).toBeTruthy()
    // 예상 차감액은 저장 응답에 없다 — 실행 견적 API 가 따로 준다.
    expect(result).not.toHaveProperty('estimatedCost')
  })

  it('노드 하나에 선이 없어도 저장된다', async () => {
    await expect(saveWorkflow({ ...line('node_001'), name: '한 개' })).resolves.toMatchObject({
      revision: 1,
    })
  })

  it('빈 캔버스는 WORKFLOW_014 로 거절한다', async () => {
    await expect(saveWorkflow({ nodes: [], edges: [], name: '빈 것' })).rejects.toMatchObject({
      code: 'WORKFLOW_014',
      status: 400,
    })
  })

  it('한 줄이 아니면 WORKFLOW_013 으로 거절한다', async () => {
    const branched: WorkflowGraph = {
      nodes: line('node_001', 'node_002', 'node_003').nodes,
      // n1 에서 둘로 갈라진다.
      edges: [
        { edgeKey: 'e1', sourceNodeKey: 'n1', targetNodeKey: 'n2' },
        { edgeKey: 'e2', sourceNodeKey: 'n1', targetNodeKey: 'n3' },
      ],
    }

    await expect(saveWorkflow({ ...branched, name: '갈래' })).rejects.toMatchObject({
      code: 'WORKFLOW_013',
      status: 400,
    })
  })

  it('없는 노드를 놓으면 WORKFLOW_015 로 거절한다', async () => {
    await expect(saveWorkflow({ ...line('없는노드'), name: '유령' })).rejects.toMatchObject({
      code: 'WORKFLOW_015',
      status: 404,
    })
  })

  it('이름이 비면 COMMON_001 로 거절한다', async () => {
    await expect(saveWorkflow({ ...TWO(), name: '   ' })).rejects.toMatchObject({
      code: 'COMMON_001',
    })
  })
})

describe('replaceWorkflow', () => {
  it('같은 회차로 보내면 회차가 오른다', async () => {
    const first = await saveWorkflow({ ...TWO(), name: '문서 정리' })

    const second = await replaceWorkflow(first.workflowId, {
      ...line('node_001', 'node_002', 'node_003'),
      name: '문서 정리 v2',
      revision: first.revision,
    })

    expect(second.revision).toBe(2)
    expect(second.name).toBe('문서 정리 v2')
  })

  // 화면이 들고 있던 회차가 낡으면 덮어쓰지 않고 거절한다. 다시 열라고 안내할 근거다.
  it('회차가 어긋나면 WORKFLOW_016 으로 거절한다', async () => {
    const first = await saveWorkflow({ ...TWO(), name: '문서 정리' })
    await replaceWorkflow(first.workflowId, { ...TWO(), name: '먼저', revision: 1 })

    const error = await replaceWorkflow(first.workflowId, {
      ...TWO(),
      name: '나중',
      revision: 1,
    }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ code: 'WORKFLOW_016', status: 409 })
  })

  it('없는 워크플로우는 WORKFLOW_002 다', async () => {
    await expect(
      replaceWorkflow('없는아이디', { ...TWO(), name: '유령', revision: 1 }),
    ).rejects.toMatchObject({ code: 'WORKFLOW_002', status: 404 })
  })
})

describe('getWorkflow', () => {
  it('저장한 구성을 연결 순서 그대로 돌려준다', async () => {
    const graph = line('node_001', 'node_002', 'node_003')
    const { workflowId } = await saveWorkflow({ ...graph, name: '문서 정리' })

    const detail = await getWorkflow(workflowId)

    expect(detail.nodes.map((node) => node.sourceId)).toEqual(['node_001', 'node_002', 'node_003'])
    expect(detail.edges).toEqual(graph.edges)
    // 고르는 화면이 없어 보내지 않았다 — 서버 기본값으로 돌아온다.
    expect(detail.nodes.every((node) => node.inputSource === 'PREV')).toBe(true)
  })

  it('없는 워크플로우는 WORKFLOW_002 다', async () => {
    await expect(getWorkflow('없는아이디')).rejects.toMatchObject({
      code: 'WORKFLOW_002',
      status: 404,
    })
  })
})

describe('mock 이 실서버와 같은 선에서 거절한다', () => {
  // 서버 `NodePlacementRequest.matchesIoType` 과 같은 선. 여기서 뭉개면 화면이 mock 에서만 통과한다.
  it('끝 노드는 결과 구조·형식이 없으면 거절한다', async () => {
    const graph = {
      nodes: [
        { nodeKey: 'n1', sourceType: 'NODE' as const, sourceId: 'node_001' },
        { nodeKey: 'n2', sourceType: 'IO' as const, ioType: 'END' as const },
      ],
      edges: [{ edgeKey: 'e1', sourceNodeKey: 'n1', targetNodeKey: 'n2' }],
    }

    await expect(saveWorkflow({ ...graph, name: '끝' })).rejects.toMatchObject({
      code: 'COMMON_001',
      status: 400,
    })
  })

  it('머리가 둘이면 고리가 아니라 WORKFLOW_013 으로 걸린다', async () => {
    const graph = {
      nodes: ['node_001', 'node_002', 'node_003'].map((sourceId, i) => ({
        nodeKey: `n${i + 1}`,
        sourceType: 'NODE' as const,
        sourceId,
      })),
      edges: [
        { edgeKey: 'e1', sourceNodeKey: 'n1', targetNodeKey: 'n3' },
        { edgeKey: 'e2', sourceNodeKey: 'n2', targetNodeKey: 'n3' },
      ],
    }

    await expect(saveWorkflow({ ...graph, name: '머리 둘' })).rejects.toMatchObject({
      code: 'WORKFLOW_013',
      status: 400,
    })
  })

  it('머리에서 닿지 못하는 고리는 개수만으로는 걸러지지 않는다', async () => {
    const cyclic = {
      nodes: line('node_001', 'node_002', 'node_003').nodes,
      // 개수는 맞는다: 선 2개, 드나드는 키도 2개씩. 그래도 n1 은 아무도 닿지 못한다.
      edges: [
        { edgeKey: 'e1', sourceNodeKey: 'n2', targetNodeKey: 'n3' },
        { edgeKey: 'e2', sourceNodeKey: 'n3', targetNodeKey: 'n2' },
      ],
    }

    await expect(saveWorkflow({ ...cyclic, name: '고리' })).rejects.toMatchObject({
      code: 'WORKFLOW_005',
      status: 400,
    })
  })

  // 합류를 받게 되며 들어오는 선이 둘인 자리가 생겼다. 머리에서 다 닿아도 고리일 수 있다.
  it('머리에서 다 닿는 고리도 WORKFLOW_005 로 걸린다', async () => {
    const cyclic = {
      nodes: line('node_001', 'node_002', 'node_003').nodes,
      edges: [
        { edgeKey: 'e1', sourceNodeKey: 'n1', targetNodeKey: 'n2' },
        { edgeKey: 'e2', sourceNodeKey: 'n2', targetNodeKey: 'n3' },
        { edgeKey: 'e3', sourceNodeKey: 'n3', targetNodeKey: 'n2' },
      ],
    }

    await expect(saveWorkflow({ ...cyclic, name: '고리' })).rejects.toMatchObject({
      code: 'WORKFLOW_005',
      status: 400,
    })
  })

  // 목록이 거는 편집 링크가 전부 404 면 mock 으로 화면을 확인할 수 없다.
  it('픽스처 워크플로우도 단건 조회가 된다', async () => {
    const detail = await getWorkflow('wf_001')

    expect(detail.workflowId).toBe('wf_001')
    expect(detail.nodes.length).toBeGreaterThan(0)
    expect(detail.edges).toHaveLength(detail.nodes.length - 1)
  })
})
