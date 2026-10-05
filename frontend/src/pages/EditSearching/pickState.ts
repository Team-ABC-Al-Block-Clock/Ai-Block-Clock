import type { RecommendedAgent } from '@/api/agents'

import { MAX_PICKED } from './config'

/** 고른 것이면 빼고, 아니면 뒤에 붙인다. */
export function togglePicked(chosen: RecommendedAgent[], agent: RecommendedAgent) {
  const without = chosen.filter((it) => it.agentId !== agent.agentId)
  if (without.length !== chosen.length) return without
  // 넘겨 받으면 편집 화면의 한 줄이 `ZOOM_MIN` 에 걸려 패널 뒤로 넘어간다.
  return chosen.length >= MAX_PICKED ? chosen : [...chosen, agent]
}

export const withoutPicked = (chosen: RecommendedAgent[], agentId: string) =>
  chosen.filter((it) => it.agentId !== agentId)
