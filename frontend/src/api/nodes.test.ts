import { describe, expect, it } from 'vitest'

import { ApiError } from '@/api/errors'
import { getNode } from '@/api/nodes'
import { getNodePalette, getWorkflow, saveWorkflow } from '@/api/workflows'
import { NODE_DETAILS, OTHERS_NODE_ID } from '@/mocks/fixtures/nodes'

/** api 레이어 + mock 핸들러를 한 번에 통과시킨다. */

describe('getNode', () => {
  it('저장하며 만들어진 원본을 상세로 연다', async () => {
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

    const node = await getNode(placed.sourceId)
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
