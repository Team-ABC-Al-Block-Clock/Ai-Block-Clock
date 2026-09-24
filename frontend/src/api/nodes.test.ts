import { describe, expect, it } from 'vitest'

import { ApiError } from '@/api/errors'
import { createNode, deleteNode, getNode, updateNode } from '@/api/nodes'
import { getNodePalette } from '@/api/workflows'
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

  it('빈 프롬프트는 NODE_003', async () => {
    await expect(
      updateNode(anyNode().nodeId, { name: '이름', prompt: '   ' }),
    ).rejects.toMatchObject({ code: 'NODE_003' })
  })

  it('이름이 상한을 넘으면 COMMON_001 — 프롬프트보다 먼저 걸린다', async () => {
    await expect(
      updateNode(anyNode().nodeId, { name: 'ㄱ'.repeat(41), prompt: '' }),
    ).rejects.toMatchObject({ code: 'COMMON_001' })
  })
})

describe('createNode', () => {
  it('새 id 를 받아 온다', async () => {
    const made = await createNode({ name: '새 노드', prompt: '할 일' })

    expect(made.nodeId).toBeTruthy()
    expect(NODE_DETAILS.some((node) => node.nodeId === made.nodeId)).toBe(false)
  })

  it('만든 노드는 곧바로 팔레트에 들어간다 — 안 그러면 만들고도 놓을 수 없다', async () => {
    const made = await createNode({ name: '팔레트에 뜰 노드', prompt: '할 일' })
    const page = await getNodePalette({ scope: 'MINE', size: 30 })

    expect(page.content.some((item) => item.itemId === made.nodeId)).toBe(true)
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
    const made = await createNode({ name: '사라질 노드', prompt: '할 일' })
    await deleteNode(made.nodeId)
    const page = await getNodePalette({ scope: 'MINE', size: 30 })

    expect(page.content.some((item) => item.itemId === made.nodeId)).toBe(false)
  })
})
