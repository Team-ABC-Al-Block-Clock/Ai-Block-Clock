import { describe, expect, it } from 'vitest'

import type { RecommendedAgent } from '@/api/agents'

import { MAX_PICKED } from './config'
import { togglePicked, withoutPicked } from './pickState'

const agent = (agentId: string): RecommendedAgent => ({
  agentId,
  name: agentId,
  summary: '',
  price: '0',
})

describe('togglePicked', () => {
  it('고르지 않은 것은 뒤에 붙인다', () => {
    expect(togglePicked([agent('a')], agent('b')).map((it) => it.agentId)).toEqual(['a', 'b'])
  })

  it('고른 것은 빼고 남은 순서를 지킨다', () => {
    const chosen = [agent('a'), agent('b'), agent('c')]
    expect(togglePicked(chosen, agent('b')).map((it) => it.agentId)).toEqual(['a', 'c'])
  })

  it('상한에 닿으면 더 붙이지 않는다', () => {
    const full = Array.from({ length: MAX_PICKED }, (_, i) => agent(`a${i}`))
    expect(togglePicked(full, agent('x'))).toBe(full)
    expect(togglePicked(full, agent('a0'))).toHaveLength(MAX_PICKED - 1)
  })
})

describe('withoutPicked', () => {
  it('id 가 같은 것만 뺀다', () => {
    expect(withoutPicked([agent('a'), agent('b')], 'a').map((it) => it.agentId)).toEqual(['b'])
  })
})
