import { describe, expect, it } from 'vitest'

import { ApiError } from '@/api/errors'
import { createNode, deleteNode, getNode, updateNode } from '@/api/nodes'
import { getWorkflow, getNodePalette, saveWorkflow } from '@/api/workflows'
import { NODE_DETAILS, OTHERS_NODE_ID, PLACED_NODE_IDS } from '@/mocks/fixtures/nodes'

/** api 레이어 + mock 핸들러를 한 번에 통과시킨다. */

const anyNode = () => NODE_DETAILS.find((node) => !PLACED_NODE_IDS.has(node.nodeId))!

describe('getNode', () => {
  it('팔레트에 있는 내 노드를 상세로 연다', async () => {
    const node = await getNode(NODE_DETAILS[0].nodeId)

    expect(node.nodeId).toBe(NODE_DETAILS[0].nodeId)
    expect(node.prompt).toBeTruthy()
  })

  it('확정 명세의 일곱 필드만 담는다', async () => {
    const node = await getNode(NODE_DETAILS[0].nodeId)

    expect(Object.keys(node).sort()).toEqual([
      'baseCost',
      'currency',
      'model',
      'name',
      'nodeId',
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

describe('updateNode', () => {
  it('보낸 이름·프롬프트·모델이 그대로 돌아온다', async () => {
    const target = anyNode()
    const saved = await updateNode(target.nodeId, {
      name: '고친 이름',
      prompt: '새 프롬프트',
      model: 'GEMINI_2_5_FLASH',
    })

    expect(saved).toMatchObject({
      nodeId: target.nodeId,
      name: '고친 이름',
      prompt: '새 프롬프트',
      model: 'GEMINI_2_5_FLASH',
    })
  })

  it('모델을 비우면 서버 기본 모델이 붙는다', async () => {
    const saved = await updateNode(anyNode().nodeId, { name: '이름', prompt: '프롬프트' })

    expect(saved.model).toBe('GEMINI_2_5_FLASH')
  })

  // 빈 값·길이는 서버에서 빈 검증이 먼저 잡는다. NODE_003 은 그 뒤 도메인에서만 난다.
  it('빈 프롬프트는 COMMON_001 — 도메인의 NODE_003 앞에서 빈 검증이 먼저 잡는다', async () => {
    await expect(
      updateNode(anyNode().nodeId, { name: '이름', prompt: '   ' }),
    ).rejects.toMatchObject({ code: 'COMMON_001' })
  })

  it('이름이 상한을 넘어도 COMMON_001', async () => {
    await expect(
      updateNode(anyNode().nodeId, { name: 'ㄱ'.repeat(41), prompt: '할 일' }),
    ).rejects.toMatchObject({ code: 'COMMON_001' })
  })
})

describe('createNode', () => {
  it('새 id 를 받아 온다', async () => {
    const made = await createNode({ name: '새 노드', prompt: '할 일' })

    expect(made.nodeId).toBeTruthy()
    expect(NODE_DETAILS.some((node) => node.nodeId === made.nodeId)).toBe(false)
  })

  it('만든 노드는 팔레트에 들어가지 않는다 — 거기 실리는 것은 마켓 항목이다', async () => {
    const made = await createNode({ name: '팔레트에 없을 노드', prompt: '할 일' })
    const page = await getNodePalette({ scope: 'ALL', size: 30 })

    expect(page.content.some((item) => item.itemId === made.nodeId)).toBe(false)
  })
})

describe('deleteNode', () => {
  it('픽스처 워크플로우가 무는 세 건은 모두 NODE_004 로 막힌다', async () => {
    for (const nodeId of PLACED_NODE_IDS) {
      await expect(deleteNode(nodeId)).rejects.toMatchObject({ code: 'NODE_004' })
    }
  })

  it('지우고 나면 다시 열 수 없다', async () => {
    const made = await createNode({ name: '지울 노드', prompt: '할 일' })
    const gone = await deleteNode(made.nodeId)

    expect(gone.nodeId).toBe(made.nodeId)
    expect(gone.deletedAt).toBeTruthy()
    await expect(getNode(made.nodeId)).rejects.toBeInstanceOf(ApiError)
  })

  it('지운 노드는 팔레트에서도 빠진다 — 남으면 다시 놓을 수 있고 그 카드의 상세는 404 다', async () => {
    const target = NODE_DETAILS[NODE_DETAILS.length - 1]
    await deleteNode(target.nodeId)
    const page = await getNodePalette({ scope: 'ALL', size: 30 })

    expect(page.content.some((item) => item.itemId === target.nodeId)).toBe(false)
  })
})

/** 만든 노드를 캔버스에 놓고 저장했다가 다시 여는 길. 팔레트에 없어 개별 조회로 메운다. */
describe('만든 노드의 저장 왕복', () => {
  it('저장에 통과하고 다시 열면 개별 조회로 이름을 되찾는다', async () => {
    const made = await createNode({ name: '왕복할 노드', prompt: '할 일' })

    const saved = await saveWorkflow({
      name: '왕복 검증',
      nodes: [{ nodeKey: 'n1', sourceType: 'NODE', sourceId: made.nodeId }],
      edges: [],
    })
    const detail = await getWorkflow(saved.workflowId)
    expect(detail.nodes.map((node) => node.sourceId)).toEqual([made.nodeId])

    // 팔레트에는 없다 — 이름표는 여기서 못 찾는다.
    const page = await getNodePalette({ scope: 'ALL', size: 30 })
    expect(page.content.some((item) => item.itemId === made.nodeId)).toBe(false)

    // 개별 조회가 이름을 들고 있다.
    expect((await getNode(made.nodeId)).name).toBe('왕복할 노드')
  })
})
