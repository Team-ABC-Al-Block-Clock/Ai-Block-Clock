import { afterEach, describe, expect, it } from 'vitest'

import {
  addAgentFavorite,
  createAgent,
  getMarketplaceAgent,
  getMarketplaceAgents,
  getMyAgents,
  removeAgentFavorite,
} from '@/api/agents'
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

  it('목록 응답은 확정 명세의 필드만 담는다', async () => {
    const [first] = (await getMarketplaceAgents()).content

    // 없는 필드를 나열하면(tier·baseCost 처럼) 그 필드가 사라진 뒤 공허한 단언이 된다.
    // 명세가 정한 키 집합과 정확히 맞는지를 본다 — 상세 필드(tokenUsage) 누출도 여기서 걸린다.
    expect(Object.keys(first).sort()).toEqual([
      'agentId',
      'averageRating',
      'createdAt',
      'executionCount',
      'favorited',
      'iconUrl',
      'name',
      'owner',
      'price',
      'reviewCount',
      'summary',
    ])
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

/**
 * 마켓 상세 조회. 목록과 달리 응답이 하나라 커서가 없는 대신, 목록에 없는 필드
 * (설명 3종·스크린샷·노드 개수·성공률·즐겨찾기 수)가 전부 여기서만 검증된다.
 */
describe('getMarketplaceAgent', () => {
  it('상세 응답은 확정 명세의 필드만 담는다', async () => {
    const agent = await getMarketplaceAgent('agt_06')

    // 명세가 정한 키 집합과 정확히 맞는지를 본다 — 필드 누락과, 추정 시절의 잔재
    // (tokenUsage 처럼 명세에 없는 필드) 누출이 같이 걸린다.
    expect(Object.keys(agent).sort()).toEqual([
      'agentId',
      'averageRating',
      'createdAt',
      'description',
      'executionCount',
      'favoriteCount',
      'favorited',
      'iconUrl',
      'images',
      'name',
      'owner',
      'price',
      'reviewCount',
      'successRate',
      'summary',
      'usageExample',
    ])
    // 목록의 owner 는 nickname 하나뿐이다. 상세는 셋을 준다.
    expect(Object.keys(agent.owner).sort()).toEqual(['nickname', 'profileImageUrl', 'userId'])
    // 소수점 오차를 막기 위해 문자열로 온다. 숫자로 바뀌면 화면 포맷이 깨진다.
    expect(typeof agent.price).toBe('string')
    // 봉투는 응답 인터셉터가 벗긴다.
    expect(agent).not.toHaveProperty('status')
  })

  it('스크린샷은 INPUT·OUTPUT 뿐이고 유형별 5장 이하, order 는 1부터 이어진다', async () => {
    const { images } = await getMarketplaceAgent('agt_20')

    for (const type of ['INPUT', 'OUTPUT'] as const) {
      const shots = images.filter((i) => i.type === type)
      expect(shots.length).toBeLessThanOrEqual(5)
      // 화면이 order 로 정렬하므로 1..n 이 빠짐없이 있어야 한다.
      expect(shots.map((i) => i.order).sort((a, b) => a - b)).toEqual(
        Array.from({ length: shots.length }, (_, i) => i + 1),
      )
    }
    expect(new Set(images.map((i) => i.type))).toEqual(new Set(['INPUT', 'OUTPUT']))
    // 유형별 장수가 다른 건이어야 탭마다 인덱스를 자르는 화면 로직이 검증된다.
    expect(images.filter((i) => i.type === 'INPUT')).not.toHaveLength(
      images.filter((i) => i.type === 'OUTPUT').length,
    )
    expect(new Set(images.map((i) => i.imageId)).size).toBe(images.length)
  })

  it('스크린샷이 없는 유형이 픽스처에 섞여 있다', async () => {
    // 0장이면 화면은 '스크린샷이 없습니다' 자리를 그려야 한다. 그 상태가 없으면 확인이 안 된다.
    const agent = await getMarketplaceAgent('agt_01')

    expect(agent.images.filter((i) => i.type === 'INPUT')).toHaveLength(0)
    expect(agent.images.filter((i) => i.type === 'OUTPUT').length).toBeGreaterThan(0)
  })

  it('평가가 없는 콜드스타트 건은 성공률도 만들지 않는다', async () => {
    const agent = await getMarketplaceAgent('agt_29')

    expect(agent.averageRating).toBeNull()
    expect(agent.reviewCount).toBe(0)
    expect(agent.executionCount).toBe(0)
    // 실행이 없으면 비율을 만들 수 없다. 화면은 이 조합을 '—' 로 내야 한다.
    expect(agent.successRate).toBe(0)
  })

  it('실행 예시를 안 적은 건이 섞여 있다', async () => {
    expect((await getMarketplaceAgent('agt_04')).usageExample).toBeNull()
    expect((await getMarketplaceAgent('agt_06')).usageExample).not.toBeNull()
  })

  it('없는 ID 는 MARKET_001 / 404', async () => {
    const error = await getMarketplaceAgent('agt_없음').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ code: 'MARKET_001', status: 404 })
  })
})

/**
 * 즐겨찾기. 이 프로젝트에서 상태를 바꾸는 유일한 mock 이라, 두 번 눌렀을 때와
 * 다시 조회했을 때가 여기서만 드러난다.
 */
describe('즐겨찾기', () => {
  // 다른 테스트가 쓰지 않는 ID 를 전용으로 쓴다. resetHandlers 는 픽스처를 되돌리지 않으므로
  // 직접 원복한다 — 안 하면 실행 순서에 따라 다른 테스트의 단정이 흔들린다.
  const ID = 'agt_20'
  afterEach(() => removeAgentFavorite(ID))

  it('등록하면 상태와 횟수가 함께 온다', async () => {
    const before = await getMarketplaceAgent(ID)
    const res = await addAgentFavorite(ID)

    expect(res).toEqual({
      agentId: ID,
      favorited: true,
      favoriteCount: before.favoriteCount + 1,
    })
    // 명세: 화면이 다시 조회하지 않게 갱신된 횟수를 함께 준다. 다시 조회해도 같아야 한다.
    const after = await getMarketplaceAgent(ID)
    expect(after.favorited).toBe(true)
    expect(after.favoriteCount).toBe(res.favoriteCount)
  })

  it('이미 등록한 것을 또 등록해도 오류가 아니고 횟수도 그대로다', async () => {
    const first = await addAgentFavorite(ID)
    const second = await addAgentFavorite(ID)

    expect(second).toEqual(first)
  })

  it('해제하면 원래 횟수로 돌아간다', async () => {
    const before = await getMarketplaceAgent(ID)
    await addAgentFavorite(ID)
    const res = await removeAgentFavorite(ID)

    expect(res).toEqual({ agentId: ID, favorited: false, favoriteCount: before.favoriteCount })
  })

  it('등록하지 않은 것을 해제해도 오류가 아니다', async () => {
    const before = await getMarketplaceAgent(ID)
    const res = await removeAgentFavorite(ID)

    expect(res).toEqual({ agentId: ID, favorited: false, favoriteCount: before.favoriteCount })
  })

  it('없는 ID 는 등록·해제 모두 MARKET_001 / 404', async () => {
    await expect(addAgentFavorite('agt_없음')).rejects.toMatchObject({
      code: 'MARKET_001',
      status: 404,
    })
    await expect(removeAgentFavorite('agt_없음')).rejects.toMatchObject({
      code: 'MARKET_001',
      status: 404,
    })
  })
})

/**
 * 에이전트 등록. 픽스처에 항목을 더하는 mock 이라, 등록한 것이 목록·상세·마이페이지에
 * 실제로 나타나는지가 여기서만 드러난다.
 */
describe('createAgent', () => {
  const INPUT = {
    name: '테스트 등록',
    summary: '등록 직후 상태를 확인한다',
    description: '상세 설명',
    usageExample: '이렇게 말해 줘',
    onboardingMessage: '무엇을 정리할까요',
  }

  // 픽스처에 밀어넣은 항목을 되돌린다. 안 하면 목록 테스트의 30건 전제가 깨진다.
  // addAgent 가 뒤에 push 하므로 길이를 잘라내면 정확히 원복된다.
  afterEach(async () => {
    const { AGENTS } = await import('@/mocks/fixtures/agents')
    AGENTS.length = 30
  })

  it('등록하면 ID 를 주고, 그 ID 로 상세를 조회할 수 있다', async () => {
    const { agentId } = await createAgent(INPUT)

    expect(agentId).toBeTruthy()
    const agent = await getMarketplaceAgent(agentId)
    expect(agent.name).toBe(INPUT.name)
    expect(agent.summary).toBe(INPUT.summary)
    expect(agent.description).toBe(INPUT.description)
    expect(agent.usageExample).toBe(INPUT.usageExample)
  })

  it('갓 등록한 것은 실적이 없다', async () => {
    const { agentId } = await createAgent(INPUT)
    const agent = await getMarketplaceAgent(agentId)

    // 화면이 '—' 를 내야 하는 조합이다.
    expect(agent.executionCount).toBe(0)
    expect(agent.successRate).toBe(0)
    expect(agent.averageRating).toBeNull()
    expect(agent.reviewCount).toBe(0)
    expect(agent.favoriteCount).toBe(0)
    // 아이콘·스크린샷 업로드는 등록 화면에서 아직 연결되지 않았다.
    expect(agent.iconUrl).toBeNull()
    expect(agent.images).toEqual([])
    // onboardingMessage 는 받아서 버린다 — 상세 응답에 없다.
    expect(agent).not.toHaveProperty('onboardingMessage')
  })

  it('등록 시각이 가장 늦어 목록 첫 장에 뜬다', async () => {
    const { agentId } = await createAgent(INPUT)
    const page = await getMarketplaceAgents()

    expect(page.content[0].agentId).toBe(agentId)
  })

  it('등록한 것이 내 에이전트 목록 맨 앞에 나온다', async () => {
    const { agentId } = await createAgent(INPUT)
    const page = await getMyAgents({ size: 30 })

    // 뒤에 붙기만 하면 30건을 넘긴 순간 첫 장을 벗어나고, 이 화면은 다음 장을 불러오지 않는다.
    expect(page.content[0].agentId).toBe(agentId)
  })

  it('등록 시각이 가장 늦어 마켓 목록 첫 장에도 뜬다', async () => {
    const { agentId } = await createAgent(INPUT)
    const page = await getMarketplaceAgents({ size: 30 })
    const times = page.content.map((a) => Date.parse(a.createdAt))

    expect(page.content[0].agentId).toBe(agentId)
    expect(times).toEqual([...times].sort((a, b) => b - a))
  })

  it('이름이나 한 줄 설명이 비면 COMMON_001', async () => {
    // 목록 카드가 반드시 그리는 값이다. 통과시키면 이름 없는 카드가 생긴다.
    await expect(createAgent({ ...INPUT, name: '   ' })).rejects.toMatchObject({
      code: 'COMMON_001',
      status: 400,
    })
    await expect(createAgent({ ...INPUT, summary: '' })).rejects.toMatchObject({
      code: 'COMMON_001',
      status: 400,
    })
  })
})

/**
 * 등록 시각 비교기. 위 통합 테스트는 오늘 날짜가 픽스처(2026-08)보다 뒤라서
 * 문자열 비교로도 통과한다 — 시계에 의존하지 않는 검사를 따로 둔다.
 */
describe('byNewest', () => {
  it('시각 표기가 달라도 실제 시각 순으로 정렬한다', async () => {
    const { byNewest } = await import('@/mocks/fixtures/agents')
    // 앞은 2026-08-30T00:00Z, 뒤는 같은 날 05:00Z — 뒤가 나중이다.
    // 문자열 비교는 '09' > '05' 라 앞을 먼저 두어 순서를 뒤집는다.
    const fixture = { createdAt: '2026-08-30T09:00:00+09:00' }
    const registered = { createdAt: '2026-08-30T05:00:00.000Z' }

    expect([fixture, registered].sort(byNewest)[0]).toBe(registered)
    expect([registered, fixture].sort(byNewest)[0]).toBe(registered)
  })
})
