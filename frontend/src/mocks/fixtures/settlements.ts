import type { Settlement } from '@/api/settlements'
import { myAgents } from '@/mocks/fixtures/agents'

const STEP_NAMES = ['요약', '분류', '초안 작성', '검토']
const DAY_MS = 86_400_000
const LAST_DAY = Date.parse('2026-09-25T15:00:00Z')

let cache: Settlement[] | null = null

/** 최신순. 넷째 칸마다 캔버스 노드(agentId null), 일곱째 칸마다 내가 쓴 것이다. */
export function settlements(): Settlement[] {
  if (cache) return cache
  const agents = myAgents().slice(0, 4)
  cache = Array.from({ length: 42 }, (_, i) => {
    const agent = i % 4 === 3 ? null : agents[i % agents.length]
    return {
      executionId: `exe_${String(500 - Math.floor(i / 2)).padStart(4, '0')}`,
      stepId: `n${(i % 3) + 1}`,
      nodeName: agent ? agent.name : STEP_NAMES[i % STEP_NAMES.length],
      agentId: agent?.agentId ?? null,
      amount: (0.25 + ((i * 37) % 11) * 0.35).toFixed(6),
      selfUse: i % 7 === 0,
      settledAt: new Date(LAST_DAY - i * 0.9 * DAY_MS).toISOString(),
    }
  })
  return cache
}
