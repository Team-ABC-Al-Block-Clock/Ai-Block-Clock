import type { AxiosAdapter } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'

import {
  addAgentFavorite,
  getAgentRecommendations,
  createAgent,
  getAgentProvenance,
  getAgentReviews,
  getMarketplaceAgent,
  getMarketplaceAgents,
  getMyAgents,
  getMyFavorites,
  getUsedAgents,
  removeAgentFavorite,
} from '@/api/agents'
import type { AgentSummary } from '@/api/agents'
import { client } from '@/api/client'
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
      'mismatchReason',
      'offChainMetadataHash',
      'offChainOwnerAddress',
      'onChainMetadataHash',
      'onChainOwnerAddress',
      'ownerMatched',
      'verified',
      'verifiedAt',
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
    // 조회 주소는 거래 해시가 아니라 문서 id 를 가리킨다.
    expect(provenance.blockchain.explorerUrl).toMatch(
      /^https:\/\/sto\.ssafy\.io\/v1\/tokens\/0x[0-9a-f]{40}\/documents\/[\w-]+-v\d+$/,
    )
    expect(provenance.blockchain.registeredAt).toBe(agent.createdAt)
    expect(provenance.verification.verified).toBe(true)
    expect(provenance.verification.mismatchReason).toBeNull()
  })

  it('확정 대기 중이면 체인에서 오는 칸이 전부 null 이다', async () => {
    const { blockchain, verification } = await getAgentProvenance('agt_29')

    expect(blockchain.transactionStatus).toBe('PENDING')
    expect(blockchain.blockNumber).toBeNull()
    expect(blockchain.transactionHash).toBeNull()
    expect(blockchain.contractAddress).toBeNull()
    expect(blockchain.explorerUrl).toBeNull()
    expect(verification.onChainOwnerAddress).toBeNull()
    expect(verification.onChainMetadataHash).toBeNull()
    expect(verification.offChainMetadataHash).toBeNull()
    expect(verification.verifiedAt).toBeNull()
    expect(verification.verified).toBe(false)
    expect(verification.mismatchReason).toBe('NOT_CONFIRMED')
  })

  it('대조가 어긋난 건은 verified 가 false 다', async () => {
    const { verification } = await getAgentProvenance('agt_28')

    expect(verification.onChainOwnerAddress).not.toBe(verification.offChainOwnerAddress)
    expect(verification.onChainMetadataHash).not.toBe(verification.offChainMetadataHash)
    expect(verification.ownerMatched).toBe(false)
    expect(verification.metadataMatched).toBe(false)
    expect(verification.verified).toBe(false)
    expect(verification.mismatchReason).toBe('OWNER_MISMATCH')
  })

  it('서버 기록만 어긋나면 두 값은 일치인데 verified 가 false 다', async () => {
    const { verification } = await getAgentProvenance('agt_27')

    expect(verification.ownerMatched).toBe(true)
    expect(verification.metadataMatched).toBe(true)
    expect(verification.verified).toBe(false)
    expect(verification.mismatchReason).toBe('RECORD_MISMATCH')
  })

  it('체인에 기록이 없으면 체인 값이 null 이고 이유가 NOT_ON_CHAIN 이다', async () => {
    const { verification } = await getAgentProvenance('agt_26')

    expect(verification.onChainOwnerAddress).toBeNull()
    expect(verification.onChainMetadataHash).toBeNull()
    expect(verification.verified).toBe(false)
    expect(verification.mismatchReason).toBe('NOT_ON_CHAIN')
  })

  it('블록 번호는 UUID 로 등록한 건에서도 안전한 정수다', async () => {
    const created = await createAgent({
      workflowId: '33567ab2-e08b-461b-a0f2-51a857df5ec7',
      revision: 1,
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
 * 리뷰 목록. 상세 화면이 커서로 이어 받는 유일한 목록이라, 이어 받기가 깨지면
 * '더 보기' 가 같은 장을 반복하거나 멈춘다.
 */
describe('getAgentReviews', () => {
  it('리뷰 응답은 명세의 필드만 담는다', async () => {
    const [first] = (await getAgentReviews('agt_01')).content

    // 없는 필드를 나열하면 그 필드가 사라진 뒤 공허한 단언이 된다 — 키 집합으로 본다.
    expect(Object.keys(first).sort()).toEqual([
      'authorNickname',
      'content',
      'createdAt',
      'rating',
      'reviewId',
    ])
  })

  it('size 만큼 끊어 주고 다음 커서를 낸다', async () => {
    const page = await getAgentReviews('agt_01', { size: 5 })

    expect(page.content).toHaveLength(5)
    expect(page.hasNext).toBe(true)
    expect(page.nextCursor).toBeTruthy()
  })

  it('커서로 이어 받으면 앞 장과 겹치지 않는다', async () => {
    // '더 보기' 가 같은 장을 다시 받으면 목록에 같은 리뷰가 두 번 쌓인다.
    const first = await getAgentReviews('agt_01', { size: 5 })
    const second = await getAgentReviews('agt_01', { cursor: first.nextCursor ?? '', size: 5 })

    // arrayContaining 은 전부 겹칠 때만 걸린다 — 한 건만 겹쳐도 잡아야 한다.
    const ids = new Set(first.content.map((r) => r.reviewId))
    expect(second.content.filter((r) => ids.has(r.reviewId))).toEqual([])
  })

  it('끝까지 이어 받으면 중복·누락 없이 전부 모인다', async () => {
    // 멈추지 않으면 '더 보기' 가 빈 장을 계속 붙이고, 커서가 밀리면 중간이 샌다.
    // 개수는 상세가 말한 reviewCount 로 맞춘다 — mock 의 건수를 숫자로 박아 두면
    // 목록을 늘릴 때마다 이 테스트가 같이 깨져서, 진짜 회귀와 구별되지 않는다.
    const { reviewCount } = await getMarketplaceAgent('agt_01')
    const size = 30
    let page = await getAgentReviews('agt_01', { size })
    const ids = page.content.map((r) => r.reviewId)
    let pages = 1
    // 상한을 둔다 — 커서가 안 먹으면 hasNext 가 영영 true 라 조건만으로는 멈추지 않는다.
    // 멈추지 않는 테스트는 실패를 알리지 못하고 CI 를 잡아 둔다.
    const limit = Math.ceil(reviewCount / size) + 1
    while (page.hasNext && pages < limit) {
      page = await getAgentReviews('agt_01', { cursor: page.nextCursor ?? '', size })
      ids.push(...page.content.map((r) => r.reviewId))
      pages += 1
    }

    expect(pages).toBeGreaterThan(1)
    expect(page.nextCursor).toBeNull()
    expect(ids).toHaveLength(reviewCount)
    expect(new Set(ids).size).toBe(reviewCount)
  })

  it('리뷰가 없으면 빈 목록이다 — 오류가 아니다', async () => {
    // 상세가 reviewCount 를 주므로 0건은 정상이다. 화면이 실패 안내를 띄우면 안 된다.
    const page = await getAgentReviews('agt_29')

    expect(page.content).toEqual([])
    expect(page.hasNext).toBe(false)
  })

  it('없는 에이전트는 MARKET_001 이다', async () => {
    await expect(getAgentReviews('agt_없음')).rejects.toMatchObject({
      code: 'MARKET_001',
      status: 404,
    })
  })

  it('커서가 유효하지 않으면 COMMON_001', async () => {
    await expect(getAgentReviews('agt_01', { cursor: 'not-a-cursor' })).rejects.toMatchObject({
      code: 'COMMON_001',
      status: 400,
    })
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
    // 굳힐 워크플로우와 사용자가 보던 회차. 둘 다 서버가 필수로 받는다.
    workflowId: '33567ab2-e08b-461b-a0f2-51a857df5ec7',
    revision: 1,
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
    // 파일을 받는 자리가 서버에 없다 — 아이콘도 스크린샷도 비어 있다.
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

  /** 나간 본문은 이 경로로만 볼 수 있다 — mock 핸들러는 읽고 나서 버린다. */
  const capturedBodyOf = async (send: () => Promise<unknown>) => {
    const previous = client.defaults.adapter
    let captured: unknown
    const capture: AxiosAdapter = (config) => {
      captured = JSON.parse(String(config.data))
      return Promise.resolve({
        data: { status: 'success', data: { agentId: 'agt_new', name: '이름', price: '450' } },
        status: 201,
        statusText: 'Created',
        headers: {},
        config,
      })
    }

    client.defaults.adapter = capture
    try {
      await send()
    } finally {
      client.defaults.adapter = previous
    }

    return captured as Record<string, unknown>
  }

  it('JSON 으로 워크플로우와 회차를 함께 보낸다', async () => {
    const body = await capturedBodyOf(() => createAgent(INPUT))

    // 파일 업로드가 서버에 없다 — multipart 로 보내면 415 다.
    expect(body).toEqual(INPUT)
  })

  it('선택 항목이 비면 키째 뺀다', async () => {
    // 빈 문자열을 보내면 "보내지 않음" 과 달라진다 — 서버는 두 값을 nullable 로 둔다.
    const body = await capturedBodyOf(() =>
      createAgent({ ...INPUT, usageExample: '', onboardingMessage: '' }),
    )

    expect(body).not.toHaveProperty('usageExample')
    expect(body).not.toHaveProperty('onboardingMessage')
    expect(body.description).toBe(INPUT.description)
  })

  it('단가는 서버가 준 문자열 그대로다', async () => {
    const created = await createAgent(INPUT)

    // 갓 등록한 것은 최저 등급이라 프리미엄이 0 이다 — 화면이 다시 계산하지 않는다.
    expect(created.price).toBe('450')
    expect(created.name).toBe(INPUT.name)
  })

  it('이름 · 한 줄 설명 · 상세 설명이 비면 COMMON_001', async () => {
    // 목록 카드가 반드시 그리는 값이다. 통과시키면 이름 없는 카드가 생긴다.
    await expect(createAgent({ ...INPUT, name: '   ' })).rejects.toMatchObject({
      code: 'COMMON_001',
      status: 400,
    })
    await expect(createAgent({ ...INPUT, summary: '' })).rejects.toMatchObject({
      code: 'COMMON_001',
      status: 400,
    })
    // 상세 설명도 서버가 필수로 본다.
    await expect(createAgent({ ...INPUT, description: '' })).rejects.toMatchObject({
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

/** 자연어 추천. 커서도 정렬도 없어 목록 조회와 검증할 것이 다르다. */
describe('getAgentRecommendations', () => {
  it('네 칸만 준다 — 목록 필드가 섞여 오지 않는다', async () => {
    const { items } = await getAgentRecommendations({ query: '학원 성적을 정리하고 싶어' })

    expect(items.length).toBeGreaterThan(0)
    expect(Object.keys(items[0]).sort()).toEqual(['agentId', 'name', 'price', 'summary'])
  })

  it('기본 8개, size 를 주면 그만큼만 온다', async () => {
    expect((await getAgentRecommendations({ query: '문서' })).items).toHaveLength(8)
    expect((await getAgentRecommendations({ query: '문서', size: 3 })).items).toHaveLength(3)
  })

  it('단어가 겹치지 않아도 빈손으로 돌려보내지 않는다', async () => {
    // 임베딩 검색의 핵심이다. 키워드 검색과 같아지면 이 화면을 만들 이유가 없다.
    const { items } = await getAgentRecommendations({ query: '부모님께 보낼 안내문' })

    expect(items.length).toBeGreaterThan(0)
  })

  it('단가는 숫자로 되돌리지 않은 문자열이다', async () => {
    const [first] = (await getAgentRecommendations({ query: '요약' })).items

    expect(typeof first.price).toBe('string')
    expect(first.price).toMatch(/^\d+(\.\d+)?$/)
  })

  it('검색어가 비었거나 200자를 넘으면 COMMON_001', async () => {
    await expect(getAgentRecommendations({ query: '   ' })).rejects.toMatchObject({
      code: 'COMMON_001',
    })
    await expect(getAgentRecommendations({ query: 'ㄱ'.repeat(201) })).rejects.toMatchObject({
      code: 'COMMON_001',
    })
  })

  it('개수가 범위를 벗어나면 COMMON_001', async () => {
    await expect(getAgentRecommendations({ query: '요약', size: 0 })).rejects.toMatchObject({
      code: 'COMMON_001',
    })
    await expect(getAgentRecommendations({ query: '요약', size: 21 })).rejects.toMatchObject({
      code: 'COMMON_001',
    })
  })
})
