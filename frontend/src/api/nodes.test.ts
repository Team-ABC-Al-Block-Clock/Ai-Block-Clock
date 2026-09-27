import { beforeEach, describe, expect, it, vi } from 'vitest'

import { client } from '@/api/client'
import { ApiError } from '@/api/errors'
import {
  attachRefFile,
  describeRefFileProblem,
  detachRefFile,
  getNode,
  REF_FILE_LIMITS,
  updateNode,
} from '@/api/nodes'
import { estimateCost } from '@/api/executions'
import { getNodePalette, getWorkflow, replaceWorkflow, saveWorkflow } from '@/api/workflows'
import { resetCreatedNodes } from '@/mocks/handlers/nodes'
import { NODE_DETAILS, OTHERS_NODE_ID } from '@/mocks/fixtures/nodes'

/** api 레이어 + mock 핸들러를 한 번에 통과시킨다. */

// 자료를 붙이고 떼면 mock 이 픽스처 노드를 덮어써 남는다. 비우지 않으면 실행 순서에 따라 갈린다.
beforeEach(resetCreatedNodes)

describe('getNode', () => {
  it('저장하며 만들어진 원본을 상세로 연다', async () => {
    const node = await getNode(NODE_DETAILS[0].nodeId)

    expect(node.nodeId).toBe(NODE_DETAILS[0].nodeId)
    expect(node.prompt).toBeTruthy()
  })

  it('확정 명세의 여덟 필드만 담는다', async () => {
    const node = await getNode(NODE_DETAILS[0].nodeId)

    expect(Object.keys(node).sort()).toEqual([
      'baseCost',
      'currency',
      'model',
      'name',
      'nodeId',
      'output',
      'prompt',
      'refFiles',
    ])
  })

  it('없는 노드는 NODE_001', async () => {
    await expect(getNode('node_없음')).rejects.toMatchObject({ code: 'NODE_001' })
  })

  it('남의 노드는 NODE_002', async () => {
    await expect(getNode(OTHERS_NODE_ID)).rejects.toMatchObject({ code: 'NODE_002' })
  })
})

/**
 * 기본 노드는 캔버스에서 적고 워크플로우를 저장할 때 만들어진다. 팔레트에는 오르지 않아,
 * 다시 열 때 이름·프롬프트는 개별 조회로만 되찾을 수 있다.
 */
describe('기본 노드의 저장 왕복', () => {
  const place = (name: string, prompt: string) => ({
    name: '왕복 검증',
    nodes: [{ nodeKey: 'n1', sourceType: 'NODE' as const, nodeType: 'LLM' as const, name, prompt }],
    edges: [],
  })

  it('원본 없이 이름·프롬프트만 보내면 서버가 만들어 id 를 돌려준다', async () => {
    const saved = await saveWorkflow(place('왕복할 노드', '할 일을 적는다'))
    const detail = await getWorkflow(saved.workflowId)

    const [placed] = detail.nodes
    expect(placed.sourceId).toBeTruthy()

    const node = await getNode(placed.sourceId!)
    expect(node.name).toBe('왕복할 노드')
    expect(node.prompt).toBe('할 일을 적는다')
  })

  it('만든 노드는 팔레트에 오르지 않는다 — 재사용하지 않기 때문이다', async () => {
    const saved = await saveWorkflow(place('팔레트에 없을 노드', '할 일'))
    const detail = await getWorkflow(saved.workflowId)
    const page = await getNodePalette({ scope: 'ALL', size: 30 })

    expect(page.content.some((item) => item.itemId === detail.nodes[0].sourceId)).toBe(false)
  })

  it('프롬프트가 비면 COMMON_001 로 거절한다', async () => {
    await expect(saveWorkflow(place('이름만 있는 노드', '   '))).rejects.toBeInstanceOf(ApiError)
  })
})

/** 저장하자마자 견적이 나와야 한다 — 금액이 0 이면 실행 화면이 막힌다. */
describe('기본 노드의 견적', () => {
  it('저장 직후 바로 단가가 매겨진다', async () => {
    const saved = await saveWorkflow({
      name: '견적 검증',
      nodes: [
        {
          nodeKey: 'n1',
          sourceType: 'NODE' as const,
          nodeType: 'LLM' as const,
          name: '분석',
          prompt: '아주 긴 프롬프트를 적어 원가가 1 보다 크게 나오도록 한다'.repeat(3),
        },
      ],
      edges: [],
    })

    const estimate = await estimateCost(saved.workflowId)

    expect(estimate.items[0].name).toBe('분석')
    expect(Number(estimate.estimatedAmount)).toBeGreaterThan(0)
  })
})

/** 저장 응답의 원본 id 를 받지 않으면 다음 저장에서 같은 노드가 또 만들어진다. */
describe('저장 응답의 원본 id', () => {
  const draft = (name: string, prompt: string) => ({
    name: '중복 검증',
    nodes: [{ nodeKey: 'n1', sourceType: 'NODE' as const, nodeType: 'LLM' as const, name, prompt }],
    edges: [],
  })

  it('저장하면 놓인 노드의 원본 id 가 함께 온다', async () => {
    const saved = await saveWorkflow(draft('원본 id 검증', '할 일'))

    expect(saved.nodes).toHaveLength(1)
    expect(saved.nodes[0].nodeKey).toBe('n1')
    expect(saved.nodes[0].sourceId).toBeTruthy()
  })

  it('받은 id 로 다시 저장하면 노드가 새로 만들어지지 않는다', async () => {
    const first = await saveWorkflow(draft('두 번 저장', '할 일'))
    const sourceId = first.nodes[0].sourceId!

    const again = await replaceWorkflow(first.workflowId, {
      name: '두 번 저장',
      nodes: [{ nodeKey: 'n1', sourceType: 'NODE', sourceId }],
      edges: [],
      revision: first.revision,
    })

    expect(again.nodes[0].sourceId).toBe(sourceId)
  })
})

/** 원본이 있는 노드는 제자리에서 고친다 — 새로 만들면 옛 원본이 고아가 된다. */
describe('updateNode', () => {
  it('이름·프롬프트를 고치고 원가를 다시 매긴다', async () => {
    const saved = await saveWorkflow({
      name: '수정 검증',
      nodes: [
        {
          nodeKey: 'n1',
          sourceType: 'NODE' as const,
          nodeType: 'LLM' as const,
          name: '고치기 전',
          prompt: '짧은 프롬프트',
        },
      ],
      edges: [],
    })
    const sourceId = saved.nodes[0].sourceId!

    const updated = await updateNode(sourceId, {
      name: '고친 뒤',
      prompt: '아주 긴 프롬프트를 적어 원가가 달라지게 만든다'.repeat(3),
      output: { kind: 'JSON', example: { level: 'high' } },
    })

    expect(updated.nodeId).toBe(sourceId)
    expect(updated.name).toBe('고친 뒤')
    expect(updated.output).toEqual({ kind: 'JSON', example: { level: 'high' } })
    expect(Number(updated.baseCost)).toBeGreaterThan(1)
    expect((await getNode(sourceId)).name).toBe('고친 뒤')
  })
})

/** 노드에 붙는 참고 자료. 서버 `RefFileValidator` 와 같은 값으로 막는지 본다. */
const fileOf = (name: string, bytes: number) =>
  new File([new Uint8Array(bytes)], name, { type: 'application/octet-stream' })

describe('describeRefFileProblem', () => {
  it('받는 형식은 통과시킨다', () => {
    for (const ext of REF_FILE_LIMITS.extensions) {
      expect(describeRefFileProblem(fileOf(`자료${ext}`, 1024), 0)).toBeNull()
    }
  })

  it('대문자 확장자도 같은 형식으로 본다', () => {
    expect(describeRefFileProblem(fileOf('기준표.PDF', 1024), 0)).toBeNull()
  })

  it('이미지는 받지 않는다 — 엔진이 pdf · txt · md · csv 만 읽는다', () => {
    for (const name of ['표지.png', '사진.jpg', '사진.jpeg']) {
      expect(describeRefFileProblem(fileOf(name, 1024), 0)).toContain('업로드 가능합니다')
    }
  })

  it('빈 파일과 형식이 함께 어긋나면 서버와 같은 순서로 빈 파일을 먼저 짚는다', () => {
    expect(describeRefFileProblem(fileOf('빈.zip', 0), 0)).toContain('빈 파일')
  })

  it('받지 않는 형식은 사유를 돌려준다', () => {
    expect(describeRefFileProblem(fileOf('자료.zip', 1024), 0)).toContain('PDF')
  })

  it('빈 파일은 막는다', () => {
    expect(describeRefFileProblem(fileOf('빈.csv', 0), 0)).toContain('빈 파일')
  })

  it('한 건이 아니라 붙어 있는 것과의 합계로 막는다', () => {
    const half = REF_FILE_LIMITS.maxTotalBytes / 2

    expect(describeRefFileProblem(fileOf('절반.pdf', half), 0)).toBeNull()
    expect(describeRefFileProblem(fileOf('절반.pdf', half), half + 1)).toContain('10MB')
  })

  it('이미 목록에 있는 이름은 막는다 — 대소문자는 같은 것으로 본다', () => {
    expect(describeRefFileProblem(fileOf('기준표.pdf', 1024), 0, ['기준표.pdf'])).toContain('이미')
    expect(describeRefFileProblem(fileOf('기준표.PDF', 1024), 0, ['기준표.pdf'])).toContain('이미')
    expect(describeRefFileProblem(fileOf('기준표2.pdf', 1024), 0, ['기준표.pdf'])).toBeNull()
  })

  it('딱 상한까지는 붙일 수 있다', () => {
    expect(
      describeRefFileProblem(fileOf('꽉.pdf', 1024), REF_FILE_LIMITS.maxTotalBytes - 1024),
    ).toBeNull()
  })
})

/**
 * 붙이기는 폼 조립까지만 확인한다. jsdom 의 `FormData` 는 undici 의 것과 달라 요청에 실리지
 * 못해 핸들러의 검사 갈래까지 가지 못한다(`api/executions.test.ts` 와 같은 사정).
 * 형식·용량 거절은 브라우저에서 확인한다.
 */
describe('attachRefFile', () => {
  it('명세가 정한 이름(file)으로 한 건만 담아 보낸다', async () => {
    const post = vi.spyOn(client, 'post').mockResolvedValue({} as never)
    const file = fileOf('채점 기준표.pdf', 2048)
    await attachRefFile('node_001', file)

    expect(post.mock.calls[0][0]).toBe('/nodes/node_001/ref-files')
    const form = post.mock.calls[0][1] as FormData
    expect(form.get('file')).toBe(file)
    expect([...form.keys()]).toEqual(['file'])
    post.mockRestore()
  })

  it('남의 노드에는 붙이지 못한다', async () => {
    await expect(attachRefFile(OTHERS_NODE_ID, fileOf('자료.pdf', 16))).rejects.toMatchObject({
      code: 'NODE_002',
    })
  })
})

describe('detachRefFile', () => {
  it('뗀 자료는 상세 조회의 refFiles 에서 빠진다', async () => {
    const nodeId = NODE_DETAILS[1].nodeId
    const [first] = (await getNode(nodeId)).refFiles

    await detachRefFile(nodeId, first.refFileId)

    const after = await getNode(nodeId)
    expect(after.refFiles.map((file) => file.refFileId)).not.toContain(first.refFileId)
  })

  it('이미 삭제된 자료는 NODE_005', async () => {
    await expect(detachRefFile(NODE_DETAILS[0].nodeId, 'ref_없음')).rejects.toMatchObject({
      code: 'NODE_005',
    })
  })
})
