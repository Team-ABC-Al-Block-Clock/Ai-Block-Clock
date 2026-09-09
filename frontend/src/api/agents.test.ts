import { describe, expect, it } from 'vitest'

import { getMarketplaceAgents } from '@/api/agents'
import type { AgentSummary } from '@/api/agents'
import { ApiError } from '@/api/errors'
import type { CursorPage } from '@/api/page'

/**
 * 마켓 목록 조회. api 레이어 + mock 핸들러 + 커서 모듈을 한 번에 통과시킨다.
 *
 * 순수 함수만 따로 검증하면 봉투 해제와 쿼리 파라미터 직렬화가 빠지는데,
 * 정작 잘 틀리는 곳은 거기다.
 */
describe('getMarketplaceAgents', () => {
  it('기본값으로 9건과 다음 커서를 준다', async () => {
    const page = await getMarketplaceAgents()

    expect(page.content).toHaveLength(9)
    expect(page.hasNext).toBe(true)
    expect(page.nextCursor).toBeTruthy()
    // 봉투는 응답 인터셉터가 벗긴다. 화면이 res.data.data 를 몰라야 한다.
    expect(page).not.toHaveProperty('status')
  })

  it('내부 자료(등급·노드 구성)는 목록에 없다', async () => {
    const [first] = (await getMarketplaceAgents()).content

    expect(first).not.toHaveProperty('tier')
    expect(first).not.toHaveProperty('baseCost')
    expect(first).not.toHaveProperty('inputSchema')
    expect(first.currency).toBe('ABC')
    // 소수점 오차를 막기 위해 문자열로 온다. 숫자로 바뀌면 화면 포맷이 깨진다.
    expect(typeof first.price).toBe('string')
  })

  it('커서로 이어 받으면 중복도 누락도 없다', async () => {
    // 한 번에 받은 순서가 정답이다. 커서로 나눠 받은 것이 이것과 같아야 한다.
    const whole = await getMarketplaceAgents({ size: 30 })
    const expected = whole.content.map((a) => a.agentId)

    const collected: string[] = []
    let cursor: string | undefined
    let last: CursorPage<AgentSummary> | undefined
    do {
      last = await getMarketplaceAgents({ cursor, size: 9 })
      collected.push(...last.content.map((a) => a.agentId))
      cursor = last.nextCursor ?? undefined
    } while (cursor)

    expect(collected).toEqual(expected)
    expect(new Set(collected).size).toBe(collected.length)
    // 마지막 페이지
    expect(last.hasNext).toBe(false)
    expect(last.nextCursor).toBeNull()
  })

  it('keyword 는 이름과 한 줄 설명만 검색한다', async () => {
    const page = await getMarketplaceAgents({ keyword: '계약', size: 30 })

    expect(page.content.length).toBeGreaterThan(0)
    for (const agent of page.content) {
      expect(`${agent.name} ${agent.summary}`).toContain('계약')
    }
    // 카테고리는 응답에도 검색 대상에도 없다
    const byCategory = await getMarketplaceAgents({ keyword: '금융', size: 30 })
    expect(byCategory.content).toHaveLength(0)
  })

  it('결과가 0건이면 빈 목록을 준다', async () => {
    const page = await getMarketplaceAgents({ keyword: '없는검색어zzz' })

    expect(page.content).toEqual([])
    expect(page.hasNext).toBe(false)
    expect(page.nextCursor).toBeNull()
  })

  it('평가가 없는 콜드스타트 건이 섞여 있다', async () => {
    const page = await getMarketplaceAgents({ size: 30 })

    const cold = page.content.filter((a) => a.averageRating === null)
    expect(cold.length).toBeGreaterThan(0)
    for (const agent of cold) expect(agent.reviewCount).toBe(0)
  })

  it('size 가 범위를 벗어나면 COMMON_001', async () => {
    await expect(getMarketplaceAgents({ size: 31 })).rejects.toMatchObject({
      code: 'COMMON_001',
      status: 400,
    })
  })

  it('커서가 유효하지 않으면 MARKET_003', async () => {
    const error = await getMarketplaceAgents({ cursor: 'not-a-cursor' }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).code).toBe('MARKET_003')
  })
})
