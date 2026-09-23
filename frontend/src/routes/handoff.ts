import { RECOMMEND_LIMITS, type RecommendedAgent } from '@/api/agents'
import type { PlacedSeed } from '@/lib/canvasReducer'

/**
 * 화면 사이로 건너가는 값 — 주소의 검색어와 `navigate` state.
 *
 * 둘 다 사용자가 손댈 수 있고 state 는 새로고침하면 사라진다. 받는 쪽은 없는 경우와
 * 모양이 다른 경우를 늘 처리한다 — `access.ts` 의 로그인 복귀 경로와 같은 규칙이다.
 */

/**
 * 랜딩 → 추천 찾기. 사용자가 적은 문장은 주소에 실어 보낸다.
 *
 * state 로 넘기면 두 곳에서 사라진다 — 찾기 화면은 로그인이 필요해서 미로그인 사용자는
 * 로그인을 거치며 잃고, 새로고침해도 잃는다. 주소에 있으면 `ProtectedRoute` 가
 * 돌아올 경로에 함께 실어 주고 새로고침도 견딘다.
 */
export const QUERY_PARAM = 'q'

/** 서버가 거절하는 길이는 여기서 미리 자른다. 화면은 이 값이 비면 랜딩으로 돌려보낸다. */
export function queryFrom(params: URLSearchParams): string {
  return (params.get(QUERY_PARAM) ?? '').trim().slice(0, RECOMMEND_LIMITS.query.max)
}

/** 랜딩과 재검색이 같은 자리를 만들도록 주소도 한 곳에서 만든다. */
export function searchPath(basePath: string, query: string): string {
  return `${basePath}?${new URLSearchParams({ [QUERY_PARAM]: query })}`
}

/** 추천 찾기 → 편집. 캔버스에 올릴 에이전트. */
export interface PickedAgentsHandoff {
  agents: RecommendedAgent[]
}

/**
 * 선택한 에이전트를 캔버스 노드 씨앗으로 바꾼다.
 *
 * 전부 남의 것으로 둔다 — 추천 응답에 소유자가 없어 내 것인지 가를 수 없다. 내 에이전트가
 * 섞여도 판매 등록만 막히고, 반대로 두면 팔 수 없는 구성을 팔 수 있다고 말하게 된다.
 */
export function seedsFrom(state: unknown): PlacedSeed[] {
  const agents = (state as PickedAgentsHandoff | null)?.agents
  if (!Array.isArray(agents)) return []
  return agents
    .filter(
      (agent): agent is RecommendedAgent =>
        typeof agent?.agentId === 'string' && typeof agent?.name === 'string',
    )
    .map((agent) => ({ id: agent.agentId, name: agent.name, nodeType: 'AGENT', mine: false }))
}
