import type { NodeDetail } from '@/api/nodes'
import { PLACEABLE_ITEMS } from '@/mocks/fixtures/nodePalette'

/**
 * 작업 노드 상세. 팔레트 픽스처에서 파생한다 — 두 곳에 따로 적으면 ID 체계가 갈려
 * 팔레트에서 놓은 노드를 상세로 열 수 없게 된다.
 */

/** 프롬프트는 이름에서 지어 낸다. 건마다 손으로 적을 값이 아니다. */
const promptOf = (name: string, summary: string | null) =>
  `당신은 ${name} 담당입니다.\n${summary ?? '주어진 입력을 처리해 결과를 돌려주세요.'}`

export const NODE_DETAILS: NodeDetail[] = PLACEABLE_ITEMS.filter(
  (item) => item.itemType === 'NODE' && item.mine,
).map((item) => ({
  nodeId: item.itemId,
  name: item.name,
  prompt: promptOf(item.name, item.summary),
  model: 'GEMINI_2_5_FLASH',
  baseCost: item.price,
  currency: 'ABC',
  refFiles: [],
}))

/** 팔레트에 없는 남의 노드. 권한 실패(`NODE_002`) 경로를 확인할 때 쓴다. */
export const OTHERS_NODE_ID = 'node_900'

/**
 * 워크플로우에 배치돼 지울 수 없는 노드(`NODE_004`).
 *
 * 픽스처 워크플로우가 내 기본 노드 앞 세 건으로 한 줄을 만든다 — 그중 첫 건을 고른다.
 */
export const PLACED_NODE_ID = NODE_DETAILS[0]?.nodeId ?? 'node_001'
