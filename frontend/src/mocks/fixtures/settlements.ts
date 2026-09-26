import type { Settlement } from '@/api/settlements'
import { myAgents } from '@/mocks/fixtures/agents'

const STEP_NAMES = ['요약', '분류', '초안 작성', '검토']
const DAY_MS = 86_400_000
const LAST_DAY = Date.parse('2026-09-26T15:00:00Z')
const ROWS = 150

/** 0 ~ 1 사이의 고정된 난수. 새로고침해도 같은 픽스처가 나온다. */
const noise = (i: number) => ((i * 2654435761) % 4294967296) / 4294967296

let cache: Settlement[] | null = null

/** 최신순, 90일쯤에 걸친다. 앞쪽 에이전트일수록 많이 팔리고, 열한째 칸마다 에이전트 없는 노드다. */
export function settlements(): Settlement[] {
  if (cache) return cache
  const agents = myAgents()
  cache = Array.from({ length: ROWS }, (_, i) => {
    const r = noise(i + 1)
    const agent = i % 11 === 10 ? null : agents[Math.floor(r * r * agents.length)]
    return {
      executionId: `exe_${String(900 - Math.floor(i / 2)).padStart(4, '0')}`,
      stepId: `n${(i % 3) + 1}`,
      nodeName: agent ? agent.name : STEP_NAMES[i % STEP_NAMES.length],
      agentId: agent?.agentId ?? null,
      amount: (8 + Math.floor(noise(i + 97) * 10) * 4.5).toFixed(6),
      // 에이전트 없는 노드는 남이 쓸 수 없어 내 실행에서만 나온다.
      selfUse: agent === null || i % 7 === 0,
      settledAt: new Date(LAST_DAY - i * 0.6 * DAY_MS).toISOString(),
    }
  })
  return cache
}
