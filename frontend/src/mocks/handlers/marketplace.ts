import { http } from 'msw'

import type {
  AgentProvenance,
  AgentRecommendations,
  AgentReview,
  RecommendedAgent,
} from '@/api/agents'
import { RECOMMEND_LIMITS } from '@/api/agents'
import { API_BASE_URL } from '@/api/client'
import type { PlaceableItem } from '@/api/workflows'
import { cursorOk, matchKeyword } from '@/mocks/cursor'
import { AGENTS, byNewest, provenanceOf, setFavorite, toSummary } from '@/mocks/fixtures/agents'
import { PLACEABLE_ITEMS } from '@/mocks/fixtures/nodePalette'
import { fail, ok } from '@/mocks/response'
import { scene } from '@/mocks/scenario'

/**
 * 검색과 커서를 핸들러 안에서 실제로 계산한다 — 전체를 한 번에 돌려주면 화면의
 * 검색어·무한 스크롤이 아무 효과가 없어 검증이 안 된다. 필터와 정렬은 없다.
 */

/** 못 찾았을 때의 코드는 MARKET_001 하나다. */
const notFound = () => fail(404, 'MARKET_001', '에이전트를 찾을 수 없습니다.')

/** 에이전트는 있는데 온체인 기록이 없을 때. */
const noProvenance = () =>
  fail(404, 'BLOCKCHAIN_003', '에이전트의 원본 증명 기록을 찾을 수 없습니다.')

/** 검색어가 비었거나 길이·개수가 범위를 벗어났을 때. 서버는 셋을 가르지 않는다. */
const invalidRequest = () => fail(400, 'COMMON_001', '요청이 올바르지 않습니다.')

const find = (agentId: unknown) => AGENTS.find((a) => a.agentId === agentId)

/** 추천은 네 칸만 내려준다. 목록 필드를 흘려보내면 화면이 없는 값을 몰래 쓴다. */
const toRecommended = (item: PlaceableItem): RecommendedAgent => ({
  agentId: item.itemId,
  name: item.name,
  // 한 줄 소개를 안 적은 에이전트가 있다. 추천 응답에는 빈 문자열로 내려간다.
  summary: item.summary ?? '',
  price: item.price,
})

/**
 * 등록 시각 역순. 상수가 아니라 함수다 — 모듈 로드 시점에 정렬해 두면 새로 등록한
 * 것이 목록에 안 나온다. 기준이 고정이라 요청마다 계산해도 순서는 같다.
 */
const published = () => [...AGENTS].sort(byNewest)

/**
 * 리뷰 mock 생성기.
 *
 * 난수를 쓰지 않는다 — 요청마다 값이 바뀌면 커서로 다음 장을 받을 때 목록이 통째로
 * 어긋나 중복·누락이 실제 버그인지 mock 탓인지 구별할 수 없다. 인덱스만으로 결정한다.
 */

/**
 * 닉네임은 길이를 섞는다. 마스킹이 2·3·4글자에서 각각 다르게 동작하므로 한 길이만
 * 깔아 두면 규칙이 깨진 것을 화면에서 못 본다.
 */
// prettier-ignore
const NICKNAMES = [
  '김도현', '이서준', '박지우', '최민서', '정하윤',
  '강태오', '윤소율', '임준혁', '한별', '오세아',
  '서지민', '남궁민수', '문채원', '배현우', '신유나',
  '조은', '황시우', '권나윤', '안도경', '유재이',
]

/**
 * 본문도 길이를 섞는다. 두 줄에서 잘리는 것과 한 줄로 끝나는 것이 함께 나와야
 * 줄 높이를 고정으로 잡은 목록의 높이 계산이 맞는지 드러난다.
 */
const CONTENTS = [
  '입력 형식만 맞추면 결과가 안정적입니다.',
  '표가 섞인 문서를 넣으면 가끔 열을 놓치는데, 같은 파일을 다시 돌리면 제대로 나옵니다. 급하지 않으면 쓸 만합니다.',
  '설명대로 동작합니다.',
  '단가 대비 만족합니다. 하루 스무 번쯤 돌리는데 아직 실패한 적은 없습니다.',
  '첫 실행이 좀 느립니다.',
  '결과 문장이 길어서 그대로 쓰기는 어렵고 손은 좀 봐야 합니다.',
  '원하는 형식으로 바로 나와서 복사해 붙이기만 하면 됩니다. 이전에 쓰던 것보다 손이 훨씬 덜 갑니다.',
  '오탈자가 가끔 있습니다.',
  '같은 입력인데 결과가 조금씩 달라집니다. 편차가 크지 않아 쓰는 데 문제는 없었습니다.',
  '문의했더니 하루 만에 고쳐 주셨습니다.',
  '긴 문서를 넣으면 뒷부분이 잘립니다. 나눠서 넣으면 괜찮습니다.',
  '무난합니다.',
  '팀에서 같이 쓰고 있는데 반응이 좋습니다. 특히 초안 잡는 시간이 많이 줄었습니다.',
  '기대한 것보다 정확도가 낮았습니다. 검수는 반드시 필요합니다.',
  '업데이트 뒤로 속도가 눈에 띄게 빨라졌습니다.',
  '예시가 더 있으면 좋겠습니다. 처음에 형식 맞추느라 몇 번 실패했습니다.',
]

/** 평점 편차. 합이 0 근처라 평균이 상세의 `averageRating` 에서 멀어지지 않는다. */
const RATING_GAP = [0, 0, 1, 0, -1, 0, 0, 1, -1, 0, 0, -2, 0, 1, 0]

/**
 * 목록 길이와 서로소인 수를 곱해 고른다. 인덱스를 그대로 나누면 닉네임과 본문이
 * 같은 주기로 돌아 '같은 사람이 같은 말을 반복하는' 목록이 된다.
 */
const pick = <T>(pool: T[], i: number, step: number) => pool[(i * step) % pool.length]

/** 가장 최근 리뷰의 시각. 여기서 과거로 내려간다. */
const REVIEW_LATEST = Date.UTC(2026, 8, 21, 9, 0)

/** 최신순이라 i 가 커질수록 과거다. 간격을 조금씩 흔들어 같은 시각이 줄줄이 서지 않게 한다. */
function buildReview(agentId: string, averageRating: number | null, i: number): AgentReview {
  const at = REVIEW_LATEST - i * 11 * 3600_000 - ((i * 29) % 97) * 60_000
  return {
    reviewId: `rev_${agentId}_${String(i + 1).padStart(3, '0')}`,
    authorNickname: pick(NICKNAMES, i, 7),
    rating: Math.max(1, Math.min(5, Math.round((averageRating ?? 3) + pick(RATING_GAP, i, 4)))),
    content: pick(CONTENTS, i, 5),
    createdAt: new Date(at).toISOString(),
  }
}

export const marketplaceHandlers = [
  http.get(`${API_BASE_URL}/marketplace/agents`, async ({ request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const url = new URL(request.url)
    const keyword = url.searchParams.get('keyword') ?? ''
    // 투영을 페이지 계산 앞에 둔다. 상세 필드를 흘려보내면 실서버에 없는 필드를
    // 화면이 몰래 쓴다.
    const filtered = published()
      .filter((a) => matchKeyword(keyword, a.name, a.summary))
      .map(toSummary)

    return cursorOk(url, filtered, (a) => a.agentId, { cursorCode: 'MARKET_003' })
  }),
  /**
   * 자연어 추천. `:agentId` 보다 먼저 선언한다 — 뒤에 두면 'recommendations' 가 ID 로 잡힌다.
   *
   * 임베딩은 흉내 낼 수 없다. 단어가 겹치는 것을 앞에 두고 실행이 많은 순으로 뒤를 채운다 —
   * '단어가 안 겹쳐도 무언가는 나온다' 는 실제 동작만 맞춘다.
   *
   * ★ 대상은 마켓 픽스처가 아니라 팔레트의 에이전트다. 추천으로 올린 노드는 그대로 저장되고
   *   다시 불러올 때 팔레트에서 이름을 찾으므로, 두 곳의 ID 가 같아야 한다 — 실서버에서도
   *   한 에이전트는 ID 가 하나다. 마켓 픽스처를 쓰면 저장이 `WORKFLOW_015` 로 막힌다.
   */
  http.get(`${API_BASE_URL}/marketplace/agents/recommendations`, async ({ request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const url = new URL(request.url)
    const query = url.searchParams.get('query') ?? ''
    const rawSize = url.searchParams.get('size')
    const size = rawSize === null ? RECOMMEND_LIMITS.size.default : Number(rawSize)
    if (!query.trim() || query.length > RECOMMEND_LIMITS.query.max) return invalidRequest()
    if (!Number.isInteger(size) || size < RECOMMEND_LIMITS.size.min) return invalidRequest()
    if (size > RECOMMEND_LIMITS.size.max) return invalidRequest()

    const agents = PLACEABLE_ITEMS.filter((item) => item.itemType === 'AGENT')
    const matched = agents.filter((item) => matchKeyword(query, item.name, item.summary ?? ''))
    const rest = agents
      .filter((item) => !matched.includes(item))
      .sort((a, b) => b.executionCount - a.executionCount)

    return ok<AgentRecommendations>({
      items: [...matched, ...rest].slice(0, size).map(toRecommended),
    })
  }),
  http.get(`${API_BASE_URL}/marketplace/agents/:agentId`, async ({ params, request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const agent = find(params.agentId)
    return agent ? ok(agent) : notFound()
  }),
  /** 명세상 201 이다 */
  http.post(
    `${API_BASE_URL}/marketplace/agents/:agentId/favorites`,
    async ({ params, request }) => {
      const forced = await scene(request)
      if (forced) return forced

      const result = setFavorite(params.agentId, true)
      return result ? ok(result, { status: 201 }) : notFound()
    },
  ),
  /** 즐겨찾기 해제 */
  http.delete(
    `${API_BASE_URL}/marketplace/agents/:agentId/favorites`,
    async ({ params, request }) => {
      const forced = await scene(request)
      if (forced) return forced

      const result = setFavorite(params.agentId, false)
      return result ? ok(result) : notFound()
    },
  ),
  http.get(
    `${API_BASE_URL}/marketplace/agents/:agentId/provenance`,
    async ({ params, request }) => {
      const forced = await scene(request)
      if (forced) return forced

      const agent = find(params.agentId)
      if (!agent) return notFound()

      const provenance = provenanceOf(agent)
      return provenance ? ok<AgentProvenance>(provenance) : noProvenance()
    },
  ),
  http.get(`${API_BASE_URL}/marketplace/agents/:agentId/reviews`, async ({ request, params }) => {
    const forced = await scene(request)
    if (forced) return forced

    const agent = find(params.agentId)
    if (!agent) return notFound()

    // 상세가 말한 개수를 그대로 만든다. 잘라 두면 '214건' 이라 적힌 목록이 몇 장 만에
    // 끝나서, 오래 스크롤할 때 드러나는 문제(커서 중복·높이 계산)가 안 보인다.
    const reviews: AgentReview[] = Array.from({ length: agent.reviewCount }, (_, i) =>
      buildReview(agent.agentId, agent.averageRating, i),
    )

    return cursorOk(new URL(request.url), reviews, (r) => r.reviewId)
  }),
]
