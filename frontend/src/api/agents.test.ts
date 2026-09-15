import { afterEach, describe, expect, it } from 'vitest'

import {
  addAgentFavorite,
  createAgent,
  getAgentProvenance,
  getMarketplaceAgent,
  getMarketplaceAgents,
  getMyAgents,
  getMyFavorites,
  getUsedAgents,
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

  it('목록 응답은 명세의 필드만 담는다', async () => {
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
  it('상세 응답은 명세의 필드만 담는다', async () => {
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

describe('getAgentProvenance', () => {
  it('증명 응답은 세 묶음을 담는다', async () => {
    const provenance = await getAgentProvenance('agt_06')

    expect(Object.keys(provenance).sort()).toEqual([
      'agentId',
      'blockchain',
      'owner',
      'verification',
    ])
    expect(Object.keys(provenance.owner).sort()).toEqual(['nickname', 'walletAddress'])
    expect(Object.keys(provenance.blockchain).sort()).toEqual([
      'blockNumber',
      'chainId',
      'contractAddress',
      'explorerUrl',
      'network',
      'registeredAt',
      'transactionHash',
      'transactionStatus',
    ])
    expect(Object.keys(provenance.verification).sort()).toEqual([
      'metadataMatched',
      'offChainMetadataHash',
      'offChainOwnerAddress',
      'onChainMetadataHash',
      'onChainOwnerAddress',
      'ownerMatched',
      'verified',
    ])
    expect(provenance).not.toHaveProperty('status')
  })

  it('확정된 건은 해시·블록 번호·등록 시각이 다 있다', async () => {
    const [agent, provenance] = await Promise.all([
      getMarketplaceAgent('agt_06'),
      getAgentProvenance('agt_06'),
    ])

    expect(provenance.blockchain.transactionStatus).toBe('CONFIRMED')
    expect(provenance.blockchain.transactionHash).toMatch(/^0x[0-9a-f]{64}$/)
    expect(provenance.blockchain.blockNumber).toBeGreaterThan(0)
    expect(provenance.blockchain.explorerUrl.endsWith(provenance.blockchain.transactionHash)).toBe(
      true,
    )
    expect(provenance.blockchain.registeredAt).toBe(agent.createdAt)
    expect(provenance.verification.verified).toBe(true)
  })

  it('확정 대기 중이면 blockNumber 가 null 이다', async () => {
    const provenance = await getAgentProvenance('agt_29')

    expect(provenance.blockchain.transactionStatus).toBe('PENDING')
    expect(provenance.blockchain.blockNumber).toBeNull()
  })

  it('대조가 어긋난 건은 verified 가 false 다', async () => {
    const { verification } = await getAgentProvenance('agt_28')

    expect(verification.onChainOwnerAddress).not.toBe(verification.offChainOwnerAddress)
    expect(verification.onChainMetadataHash).not.toBe(verification.offChainMetadataHash)
    expect(verification.ownerMatched).toBe(false)
    expect(verification.metadataMatched).toBe(false)
    expect(verification.verified).toBe(false)
  })

  it('블록 번호는 UUID 로 등록한 건에서도 안전한 정수다', async () => {
    const created = await createAgent({
      name: '증명 확인용',
      summary: '한 줄',
      description: '설명',
      usageExample: '예시',
      onboardingMessage: '안내',
    })
    const { blockchain } = await getAgentProvenance(created.agentId)

    expect(Number.isSafeInteger(blockchain.blockNumber)).toBe(true)
  })

  it('온체인 기록이 없으면 BLOCKCHAIN_003 — 에이전트 자체는 있다', async () => {
    const error = await getAgentProvenance('agt_30').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ code: 'BLOCKCHAIN_003', status: 404 })
    await expect(getMarketplaceAgent('agt_30')).resolves.toMatchObject({ agentId: 'agt_30' })
  })

  it('없는 ID 는 MARKET_001 — 기록 없음과 코드가 갈린다', async () => {
    const error = await getAgentProvenance('agt_없음').catch((e: unknown) => e)

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

/**
 * 마이페이지 「판매 중」.
 *
 * 예전 추정 타입(`tokenUsage`)이 남아 있으면 키 집합 검사에서 걸린다.
 */
describe('getMyAgents', () => {
  it('명세 기본값 10건과 다음 커서를 준다', async () => {
    const page = await getMyAgents()

    // 마켓 목록은 9 다. 한쪽으로 박아 두면 화면이 size 를 안 넘길 때 페이지 크기가 갈린다.
    expect(page.content).toHaveLength(10)
    // ME 가 11건 미만이면 두 번째 장이 없어 화면의 무한 스크롤과 'N개+' 를 검증할 수 없다.
    expect(page.hasNext).toBe(true)
    expect(page.nextCursor).toBeTruthy()
    expect(page).not.toHaveProperty('status')
  })

  it('두 번째 장까지 이어 받으면 내가 등록한 것이 전부 나온다', async () => {
    const first = await getMyAgents()
    const second = await getMyAgents({ cursor: first.nextCursor ?? undefined })
    const ids = [...first.content, ...second.content].map((a) => a.agentId)

    expect(second.hasNext).toBe(false)
    expect(new Set(ids).size).toBe(ids.length)
    expect(ids).toHaveLength((await getMyAgents({ size: 30 })).content.length)
  })

  it('목록 응답은 명세의 필드만 담는다', async () => {
    const [first] = (await getMyAgents()).content

    expect(Object.keys(first).sort()).toEqual([
      'agentId',
      'createdAt',
      'executionCount',
      'favorited',
      'iconUrl',
      'name',
      'price',
      'revenue',
      'summary',
      'trend',
    ])
    // 소수점 오차를 막기 위해 둘 다 문자열이다. 숫자로 바뀌면 화면 포맷이 깨진다.
    expect(typeof first.price).toBe('string')
    expect(typeof first.revenue).toBe('string')
  })

  it('trend 는 7일치이고 실행이 없으면 전부 0 이다', async () => {
    const page = await getMyAgents({ size: 30 })

    for (const agent of page.content) expect(agent.trend).toHaveLength(7)
    // 콜드스타트. 화면이 평평한 선을 내는지는 이 상태에서만 드러난다.
    const cold = page.content.filter((a) => a.executionCount === 0)
    expect(cold.length).toBeGreaterThan(0)
    for (const agent of cold) {
      expect(agent.trend).toEqual([0, 0, 0, 0, 0, 0, 0])
      expect(agent.revenue).toBe('0')
    }
  })

  it('keyword 는 이름과 한 줄 설명을 각각 본다', async () => {
    const page = await getMyAgents({ keyword: '계약', size: 30 })

    expect(page.content.length).toBeGreaterThan(0)
    for (const agent of page.content) {
      // 이어 붙인 문자열로 단정하면 필드 경계를 넘는 매칭을 통과시킨다. 각각을 본다.
      expect(agent.name.includes('계약') || agent.summary.includes('계약')).toBe(true)
    }
  })

  it('두 필드에 걸친 검색어는 매칭되지 않는다', async () => {
    // '메일 초안' + '받은 메일에 답장 초안 작성' 을 이어 붙이면 '초안 받은' 이 걸린다.
    // 서버는 LIKE 를 필드별로 걸므로 실서버에서는 0건이다.
    const page = await getMyAgents({ keyword: '초안 받은', size: 30 })
    expect(page.content).toHaveLength(0)
  })

  it('sort 마다 순서가 실제로 달라진다', async () => {
    const latest = await getMyAgents({ size: 30 })
    const runs = await getMyAgents({ sort: 'EXECUTIONS', size: 30 })
    const revenue = await getMyAgents({ sort: 'REVENUE', size: 30 })

    // 기본값은 LATEST — 등록 시각 역순
    const times = latest.content.map((a) => Date.parse(a.createdAt))
    expect(times).toEqual([...times].sort((a, b) => b - a))

    const counts = runs.content.map((a) => a.executionCount)
    expect(counts).toEqual([...counts].sort((a, b) => b - a))

    // 수익은 문자열이라 사전순으로 정렬하면 '9946800' 이 '1120' 보다 앞에 온다.
    const amounts = revenue.content.map((a) => Number(a.revenue))
    expect(amounts).toEqual([...amounts].sort((a, b) => b - a))

    // 세 정렬이 같은 결과를 주면 무엇도 검증하지 못한다.
    expect(runs.content[0].agentId).not.toBe(latest.content[0].agentId)
  })

  it('sort 가 명세에 없는 값이면 COMMON_001', async () => {
    // 조용히 기본값으로 떨어지면 화면이 잘못된 값을 보내도 실서버에서만 400 이 난다.
    await expect(getMyAgents({ sort: 'RATING' as unknown as 'LATEST' })).rejects.toMatchObject({
      code: 'COMMON_001',
      status: 400,
    })
  })

  it('size 가 범위를 벗어나면 COMMON_001, 커서가 어긋나면 COMMON_001', async () => {
    await expect(getMyAgents({ size: 31 })).rejects.toMatchObject({ code: 'COMMON_001' })
    await expect(getMyAgents({ cursor: 'not-a-cursor' })).rejects.toMatchObject({
      code: 'COMMON_001',
    })
  })

  it('커서로 이어 받으면 중복도 누락도 없다', async () => {
    const whole = await getMyAgents({ size: 30 })
    const expected = whole.content.map((a) => a.agentId)

    const collected: string[] = []
    let cursor: string | undefined
    let last
    do {
      last = await getMyAgents({ cursor, size: 3 })
      collected.push(...last.content.map((a) => a.agentId))
      cursor = last.nextCursor ?? undefined
    } while (cursor)

    expect(collected).toEqual(expected)
    expect(last.hasNext).toBe(false)
  })
})

/** 마이페이지 「즐겨찾기」 — 검색어도 정렬도 받지 않는다. */
describe('getMyFavorites', () => {
  it('목록 응답은 명세의 필드만 담는다', async () => {
    const [first] = (await getMyFavorites()).content

    // 수익은 오지 않는다 — 내가 만들지 않은 에이전트가 섞여 있다(명세 참고사항).
    expect(Object.keys(first).sort()).toEqual([
      'agentId',
      'executionCount',
      'favoritedAt',
      'iconUrl',
      'name',
      'owner',
      'price',
      'summary',
    ])
  })

  it('즐겨찾기한 시각 역순이다', async () => {
    const page = await getMyFavorites({ size: 30 })

    const times = page.content.map((a) => Date.parse(a.favoritedAt))
    expect(times).toEqual([...times].sort((a, b) => b - a))
    expect(page.content.length).toBeGreaterThan(10)
  })

  it('별을 켜면 목록 맨 앞에 오고, 끄면 사라진다', async () => {
    // 즐겨찾기 상태가 곧 이 목록이다. 두 곳에 나눠 두면 별은 켜졌는데 탭에는 안 나온다.
    const ID = 'agt_20'
    const before = (await getMyFavorites({ size: 30 })).content.map((a) => a.agentId)
    expect(before).not.toContain(ID)

    await addAgentFavorite(ID)
    const after = await getMyFavorites({ size: 30 })
    // 방금 켠 것이 즐겨찾기한 시각이 가장 늦다.
    expect(after.content[0].agentId).toBe(ID)

    await removeAgentFavorite(ID)
    const restored = (await getMyFavorites({ size: 30 })).content.map((a) => a.agentId)
    expect(restored).toEqual(before)
  })

  it('커서가 유효하지 않으면 MARKET_003', async () => {
    // 마이페이지 목록 중 /users/me/* 두 개만 이 코드다(명세).
    await expect(getMyFavorites({ cursor: 'not-a-cursor' })).rejects.toMatchObject({
      code: 'MARKET_003',
      status: 400,
    })
  })
})

describe('getUsedAgents', () => {
  it('목록 응답은 명세의 필드만 담는다', async () => {
    const [first] = (await getUsedAgents()).content

    expect(Object.keys(first).sort()).toEqual([
      'agentId',
      'averageRating',
      'favorited',
      'iconUrl',
      'lastExecutedAt',
      'myExecutionCount',
      'name',
      'owner',
      'price',
      'reviewCount',
      'summary',
      'totalSpent',
    ])
    // 통화 단위는 ABC 하나 뿐이다.
    expect(first).not.toHaveProperty('currency')
    // 남이 만든 것이 대부분이라 수익은 오지 않는다.
    expect(first).not.toHaveProperty('revenue')
  })

  it('내 실행 횟수는 전체 누적과 다른 값이다', async () => {
    const page = await getUsedAgents({ size: 30 })

    for (const agent of page.content) {
      expect(agent.myExecutionCount).toBeGreaterThan(0)
      // 단가 x 내 실행 횟수. 둘 다 응답에 있으니 화면이 검산할 수 있어야 한다.
      expect(Number(agent.totalSpent)).toBe(Number(agent.price) * agent.myExecutionCount)
    }
  })

  it('기본은 마지막 실행 역순, MOST_EXECUTED 는 내 실행 횟수 순', async () => {
    const recent = await getUsedAgents({ size: 30 })
    const most = await getUsedAgents({ sort: 'MOST_EXECUTED', size: 30 })

    const times = recent.content.map((a) => Date.parse(a.lastExecutedAt))
    expect(times).toEqual([...times].sort((a, b) => b - a))

    const counts = most.content.map((a) => a.myExecutionCount)
    expect(counts).toEqual([...counts].sort((a, b) => b - a))
  })

  it('내가 등록한 에이전트도, 평점이 없는 건도 섞여 있다', async () => {
    const page = await getUsedAgents({ size: 30 })
    const mine = await getMyAgents({ size: 30 })
    const myIds = new Set(mine.content.map((a) => a.agentId))

    // 「판매 중」 탭과 양쪽에 나타날 수 있다 — 보는 축이 다르다(명세 참고사항).
    expect(page.content.some((a) => myIds.has(a.agentId))).toBe(true)
    // 콜드스타트. 화면이 '—' 를 내는지는 이 건에서만 드러난다.
    expect(page.content.some((a) => a.averageRating === null)).toBe(true)
  })

  it('실행이 0회인 에이전트는 목록에 없다', async () => {
    // COMPLETED 실행이 하나도 없으면 차감된 금액이 없으므로 사용했다고 보지 않는다.
    const page = await getUsedAgents({ size: 30 })
    const cold = (await getMyAgents({ size: 30 })).content.filter((a) => a.executionCount === 0)

    expect(cold.length).toBeGreaterThan(0)
    for (const agent of cold) {
      expect(page.content.map((a) => a.agentId)).not.toContain(agent.agentId)
    }
  })

  it('sort 가 명세에 없으면 COMMON_001, 커서가 어긋나면 MARKET_003', async () => {
    await expect(
      getUsedAgents({ sort: 'REVENUE' as unknown as 'LAST_EXECUTED' }),
    ).rejects.toMatchObject({ code: 'COMMON_001' })
    await expect(getUsedAgents({ cursor: 'not-a-cursor' })).rejects.toMatchObject({
      code: 'MARKET_003',
    })
  })
})
