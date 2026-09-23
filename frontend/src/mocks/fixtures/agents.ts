import type {
  AgentDetail,
  AgentFavorite,
  AgentImage,
  AgentProvenance,
  AgentSummary,
  FavoriteAgent,
  MyAgent,
  UsedAgent,
} from '@/api/agents'
import iconUrl from '@/assets/logo.png'

/**
 * 에이전트 픽스처 30건. 단가·성공률·즐겨찾기 수는 서버가 기록해 주는 값이라 그대로 적는다.
 * 평가 0건인 콜드스타트와 아이콘이 없는 건을 섞었다 — 화면이 NaN·빈칸·깨진 이미지를
 * 내는지는 그때만 드러난다.
 */

/** 프로필 이미지가 없는 사람을 섞었다 — 화면의 아바타 폴백을 확인해야 한다. */
const OWNERS = {
  이싸피: { userId: 'usr_01', profileImageUrl: iconUrl },
  김싸피: { userId: 'usr_02', profileImageUrl: null },
  박싸피: { userId: 'usr_03', profileImageUrl: iconUrl },
  최싸피: { userId: 'usr_04', profileImageUrl: null },
  정싸피: { userId: 'usr_05', profileImageUrl: null },
} satisfies Record<string, { userId: string; profileImageUrl: string | null }>

/** 인증 연동 전까지 이 닉네임이 '나' 다. 12건을 갖는다 — 11건 미만이면
 *  「판매 중」 탭에 두 번째 장이 없어 무한 스크롤을 확인할 수 없다. */
const ME = '이싸피' satisfies keyof typeof OWNERS

interface Seed {
  name: string
  summary: string
  /** 서버가 계산해 문자열로 준다 — 여기서 만들지 않는다 */
  price: string
  /** 누적 수익. 서버가 기록해 주는 값이라 계산하지 않는다 */
  revenue: string
  /** 0~1. 실행이 0회면 서버도 비율을 만들 수 없어 0 이다 */
  successRate: number
  /** 평가 0건이면 null */
  averageRating: number | null
  reviewCount: number
  executionCount: number
  favoriteCount: number
  owner: keyof typeof OWNERS
  /** false 면 iconUrl 이 null — 화면은 이니셜로 폴백해야 한다 */
  hasIcon: boolean
}

// 한 줄에 한 건 — Prettier 가 펼치면 건당 15줄이 되어 분산을 표처럼 훑을 수 없다.
// prettier-ignore
const SEEDS: Seed[] = [
  // 평점·실행 횟수가 높은 쪽
  { name: '메일 초안', summary: '받은 메일에 답장 초안 작성', price: '540', revenue: '9946800', successRate: 0.986, averageRating: 4.8, reviewCount: 214, executionCount: 18420, favoriteCount: 642, owner: '이싸피', hasIcon: true },
  { name: 'CS 응답', summary: '문의에 맞는 답변을 제안', price: '630', revenue: '9594900', successRate: 0.982, averageRating: 4.7, reviewCount: 186, executionCount: 15230, favoriteCount: 517, owner: '이싸피', hasIcon: true },
  { name: '뉴스 브리핑', summary: '오늘 기사를 세 줄로 요약', price: '360', revenue: '7970400', successRate: 0.991, averageRating: 4.9, reviewCount: 302, executionCount: 22140, favoriteCount: 908, owner: '박싸피', hasIcon: false },
  { name: 'Contract Analyzer', summary: '계약 검토와 위험 요소 분석', price: '1170', revenue: '1460160', successRate: 0.964, averageRating: 4.6, reviewCount: 94, executionCount: 1248, favoriteCount: 213, owner: '이싸피', hasIcon: true },
  { name: '성적 분석 리포트', summary: '엑셀 성적표를 통계 JSON 으로 정리', price: '810', revenue: '3126600', successRate: 0.978, averageRating: 4.8, reviewCount: 128, executionCount: 3860, favoriteCount: 341, owner: '최싸피', hasIcon: false },

  { name: '문서 정리', summary: '폴더의 파일을 주제별로 분류', price: '390', revenue: '3673800', successRate: 0.972, averageRating: 4.4, reviewCount: 152, executionCount: 9420, favoriteCount: 428, owner: '이싸피', hasIcon: true },
  { name: '코드 리뷰', summary: '변경된 코드의 문제를 지적', price: '858', revenue: '833976', successRate: 0.951, averageRating: 4.3, reviewCount: 78, executionCount: 972, favoriteCount: 186, owner: '이싸피', hasIcon: false },
  { name: '회의록 작성', summary: '녹취 텍스트를 항목별로 정리', price: '624', revenue: '3856320', successRate: 0.968, averageRating: 4.5, reviewCount: 110, executionCount: 6180, favoriteCount: 297, owner: '김싸피', hasIcon: true },
  { name: '리서치 수집', summary: '주제별 자료를 모아 출처 정리', price: '936', revenue: '4034160', successRate: 0.943, averageRating: 4.2, reviewCount: 66, executionCount: 4310, favoriteCount: 158, owner: '정싸피', hasIcon: false },
  { name: '리포트 작성', summary: '데이터를 문장으로 풀어 씀', price: '780', revenue: '4087200', successRate: 0.959, averageRating: 4.4, reviewCount: 88, executionCount: 5240, favoriteCount: 224, owner: '최싸피', hasIcon: true },
  { name: '계약서 검토', summary: '조항의 위험 요소를 표시', price: '1092', revenue: '969696', successRate: 0.937, averageRating: 4.1, reviewCount: 42, executionCount: 888, favoriteCount: 121, owner: '이싸피', hasIcon: false },
  { name: '슬랙 요약', summary: '읽지 않은 대화를 정리', price: '364', revenue: '3188640', successRate: 0.974, averageRating: 4.5, reviewCount: 134, executionCount: 8760, favoriteCount: 386, owner: '김싸피', hasIcon: true },
  { name: 'Market Predictor', summary: '시장 흐름과 가격 변동 예측', price: '1170', revenue: '2522520', successRate: 0.912, averageRating: 4.0, reviewCount: 51, executionCount: 2156, favoriteCount: 147, owner: '이싸피', hasIcon: false },
  { name: 'API 문서화', summary: '코드에서 API 문서 초안 생성', price: '702', revenue: '2442960', successRate: 0.946, averageRating: 4.2, reviewCount: 61, executionCount: 3480, favoriteCount: 172, owner: '최싸피', hasIcon: true },
  { name: '가정통신문 생성', summary: '분석 결과를 학부모용 문장으로', price: '598', revenue: '2224560', successRate: 0.969, averageRating: 4.6, reviewCount: 97, executionCount: 3720, favoriteCount: 253, owner: '정싸피', hasIcon: false },

  { name: '일정 요약', summary: '캘린더를 하루 단위로 정리', price: '220', revenue: '1579600', successRate: 0.965, averageRating: 3.9, reviewCount: 64, executionCount: 7180, favoriteCount: 208, owner: '김싸피', hasIcon: true },
  { name: '데이터 추출', summary: '표에서 필요한 열만 뽑아냄', price: '374', revenue: '239360', successRate: 0.928, averageRating: 3.7, reviewCount: 38, executionCount: 640, favoriteCount: 94, owner: '최싸피', hasIcon: false },
  { name: '번역 정리', summary: '문서를 번역하고 용어 통일', price: '572', revenue: '709280', successRate: 0.934, averageRating: 3.8, reviewCount: 55, executionCount: 1240, favoriteCount: 131, owner: '이싸피', hasIcon: true },
  { name: '영수증 정리', summary: '영수증 사진에서 금액을 추출', price: '418', revenue: '468160', successRate: 0.906, averageRating: 3.6, reviewCount: 47, executionCount: 1120, favoriteCount: 108, owner: '박싸피', hasIcon: false },
  { name: '자막 생성', summary: '영상 음성을 자막으로 변환', price: '704', revenue: '1140480', successRate: 0.921, averageRating: 3.9, reviewCount: 72, executionCount: 1620, favoriteCount: 164, owner: '이싸피', hasIcon: true },
  { name: '이력서 검토', summary: '직무에 맞게 문장을 다듬음', price: '462', revenue: '600600', successRate: 0.941, averageRating: 3.8, reviewCount: 59, executionCount: 1300, favoriteCount: 126, owner: '김싸피', hasIcon: false },
  { name: '가계부 분류', summary: '지출 내역을 항목별로 나눔', price: '286', revenue: '218504', successRate: 0.953, averageRating: 3.7, reviewCount: 33, executionCount: 764, favoriteCount: 87, owner: '최싸피', hasIcon: true },
  { name: '테스트 생성', summary: '함수에 맞는 테스트를 작성', price: '638', revenue: '390456', successRate: 0.918, averageRating: 3.6, reviewCount: 29, executionCount: 612, favoriteCount: 73, owner: '박싸피', hasIcon: false },
  { name: 'Docu Chain', summary: '문서 검증 및 위변조 확인', price: '770', revenue: '759220', successRate: 0.932, averageRating: 3.9, reviewCount: 41, executionCount: 986, favoriteCount: 112, owner: '이싸피', hasIcon: true },

  // 평점·실행 횟수가 낮은 쪽
  { name: '이미지 태깅', summary: '이미지에 설명 태그를 붙임', price: '300', revenue: '125400', successRate: 0.884, averageRating: 3.2, reviewCount: 18, executionCount: 418, favoriteCount: 46, owner: '이싸피', hasIcon: false },
  { name: '링크 수집', summary: '북마크를 주제별로 묶음', price: '180', revenue: '145080', successRate: 0.897, averageRating: 3.4, reviewCount: 22, executionCount: 806, favoriteCount: 58, owner: '김싸피', hasIcon: true },
  { name: '중복 파일 정리', summary: '같은 파일을 찾아 하나만 남김', price: '160', revenue: '83680', successRate: 0.876, averageRating: 3.1, reviewCount: 14, executionCount: 523, favoriteCount: 31, owner: '최싸피', hasIcon: false },
  { name: '표 정규화', summary: '흩어진 표를 하나 형식으로', price: '240', revenue: '83280', successRate: 0.862, averageRating: 2.9, reviewCount: 11, executionCount: 347, favoriteCount: 24, owner: '박싸피', hasIcon: true },

  // 콜드스타트 — 평가 0건. 등록 시각이 가장 늦어 목록 첫 페이지에 뜬다.
  // 실행이 없으면 성공률도 만들 수 없다(0). 화면은 '—' 를 내야 한다.
  { name: '이미지 정리', summary: '사진을 날짜·장소로 묶음', price: '220', revenue: '0', successRate: 0, averageRating: null, reviewCount: 0, executionCount: 0, favoriteCount: 0, owner: '이싸피', hasIcon: false },
  { name: '용어집 관리', summary: '문서에서 용어를 모아 정의', price: '280', revenue: '1120', successRate: 0.75, averageRating: null, reviewCount: 0, executionCount: 4, favoriteCount: 1, owner: '정싸피', hasIcon: true },
]

/**
 * 처음부터 즐겨찾기된 건들(SEEDS 인덱스). 즐겨찾기 목록을 이 상태에서 만든다 — 별도 배열을
 * 두면 목록과 `favorited` 가 갈라져 별은 켜졌는데 탭에는 안 나온다. `agt_20`(19)은
 * 등록·해제 테스트가 전용으로 쓴다.
 */
const FAVORED = new Set([2, 5, 6, 8, 10, 12, 13, 15, 16, 18, 21, 23, 28, 29])

/** 두 개를 번갈아 써서 길이가 갈리게 한다 — 한 줄일 때만 멀쩡한 레이아웃 사고가 있다. */
const DESCRIPTION_TAILS = [
  '실행하면 대상 목록을 먼저 보여주고, 확인한 뒤에 처리합니다. 처리 내역은 실행이 끝난 뒤 목록으로 확인할 수 있습니다.',
  '입력 형식을 강제하지 않으므로 가진 자료를 그대로 넣으면 됩니다. 중간에 멈춰도 그때까지 처리한 것은 그대로 남습니다.',
]

const IMAGE_LABEL = { INPUT: '입력', OUTPUT: '출력' } as const

/** 자산이 없어 라벨이 박힌 SVG 를 만든다. 같은 그림 5장으로는 전환을 확인할 수 없다. */
function shot(name: string, label: string, index: number, total: number) {
  const svg =
    '<svg xmlns="http://www.w3.org/2000/svg" width="896" height="480">' +
    '<rect width="896" height="480" fill="#2a1638"/>' +
    '<rect x="40" y="40" width="816" height="400" rx="16" fill="#3d2154"/>' +
    '<text x="448" y="252" fill="#ffd9ec" font-family="sans-serif" font-size="32" ' +
    `text-anchor="middle">${name} · ${label} ${index}/${total}</text>` +
    '</svg>'
  return `data:image/svg+xml,${encodeURIComponent(svg)}`
}

/** 유형별 0~5장. 0장인 건이 있어야 '스크린샷 없음' 자리를 볼 수 있다. */
function buildImages(agentId: string, name: string, index: number): AgentImage[] {
  const counts = { INPUT: index % 6, OUTPUT: (index + 3) % 6 }
  return (['INPUT', 'OUTPUT'] as const).flatMap((type) =>
    Array.from({ length: counts[type] }, (_, i) => ({
      imageId: `img_${agentId}_${type.toLowerCase()}_${i + 1}`,
      type,
      url: shot(name, IMAGE_LABEL[type], i + 1, counts[type]),
      order: i + 1,
    })),
  )
}

/** 건당 하루씩 벌린다. 겹치면 역순 정렬이 흔들려 커서에서 중복·누락이 생긴다. */
function build(seed: Seed, index: number): AgentDetail {
  const day = String(index + 1).padStart(2, '0')
  const agentId = `agt_${day}`
  const owner = OWNERS[seed.owner]
  return {
    agentId,
    name: seed.name,
    summary: seed.summary,
    // 설명은 1440×1520 고정 좌표라 3줄을 넘기면 '스크린샷' 섹션과 겹친다.
    description: `${seed.summary}. ${DESCRIPTION_TAILS[index % DESCRIPTION_TAILS.length]}`,
    // 일부는 null — 만든 사람이 안 적은 경우다. 화면의 null 분기가 여기서만 드러난다.
    usageExample: index % 7 === 3 ? null : `${seed.summary}해 줘`,
    iconUrl: seed.hasIcon ? iconUrl : null,
    images: buildImages(agentId, seed.name, index),
    price: seed.price,
    executionCount: seed.executionCount,
    successRate: seed.successRate,
    averageRating: seed.averageRating,
    reviewCount: seed.reviewCount,
    favorited: FAVORED.has(index),
    favoriteCount: seed.favoriteCount,
    owner: { userId: owner.userId, nickname: seed.owner, profileImageUrl: owner.profileImageUrl },
    createdAt: `2026-08-${day}T09:00:00+09:00`,
  }
}

export const AGENTS: AgentDetail[] = SEEDS.map(build)

// 31건을 넘기면 build() 의 day 가 8월을 벗어나 createdAt 이 Invalid Date 가 되는데,
// 정렬은 계속 동작하는 것처럼 보인다.
if (AGENTS.length !== 30) {
  throw new Error(`[mock] 에이전트 픽스처가 ${AGENTS.length}건이다. 30건이어야 한다.`)
}

/**
 * 등록 시각 역순. 문자열 비교로는 안 된다 — 픽스처는 `+09:00`, `addAgent` 는 `Z` 라
 * 날짜가 같으면 나중에 등록한 것이 뒤로 밀린다.
 */
export const byNewest = (a: { createdAt: string }, b: { createdAt: string }) =>
  Date.parse(b.createdAt) - Date.parse(a.createdAt)

/** 상세 필드를 흘려보내면 화면이 실서버에 없는 필드를 몰래 쓰게 된다. */
export function toSummary(agent: AgentDetail): AgentSummary {
  return {
    agentId: agent.agentId,
    name: agent.name,
    summary: agent.summary,
    iconUrl: agent.iconUrl,
    price: agent.price,
    executionCount: agent.executionCount,
    averageRating: agent.averageRating,
    reviewCount: agent.reviewCount,
    favorited: agent.favorited,
    owner: { nickname: agent.owner.nickname },
    createdAt: agent.createdAt,
  }
}

/** 화면의 세 갈래 — 기록 없음(404) · 확정 대기 · 대조 불일치 — 는 이 셋으로만 확인된다. */
const NO_RECORD = 'agt_30'
const PENDING = 'agt_29'
/** 주소와 해시가 둘 다 어긋난 건. 화면의 ✗ 두 줄을 한 번에 확인한다. */
const TAMPERED = 'agt_28'

/** 난수로 만들면 refetch 마다 해시가 바뀌어 값이 달라진 것처럼 보인다. */
function hex(seed: string, length: number) {
  let h = 0
  for (const ch of seed) h = (h * 31 + ch.charCodeAt(0)) >>> 0

  let out = ''
  for (let i = 0; out.length < length; i++) {
    out += ((h + i * 0x9e3779b9) >>> 0).toString(16).padStart(8, '0')
  }
  return out.slice(0, length)
}

export function provenanceOf(agent: AgentDetail): AgentProvenance | null {
  if (agent.agentId === NO_RECORD) return null

  const pending = agent.agentId === PENDING
  const stale = agent.agentId === TAMPERED

  const walletAddress = `0x${hex(agent.owner.userId, 40)}`
  const transactionHash = `0x${hex(`${agent.agentId}:tx`, 64)}`
  const metadataHash = `0x${hex(`${agent.agentId}:metadata`, 64)}`
  const onChainOwnerAddress = stale ? `0x${hex(`${agent.agentId}:stale-owner`, 40)}` : walletAddress
  const onChainMetadataHash = stale ? `0x${hex(`${agent.agentId}:stale-hash`, 64)}` : metadataHash

  const ownerMatched = onChainOwnerAddress === walletAddress
  const metadataMatched = onChainMetadataHash === metadataHash

  return {
    agentId: agent.agentId,
    owner: { nickname: agent.owner.nickname, walletAddress },
    blockchain: {
      network: 'POLYGON_AMOY',
      chainId: 80002,
      contractAddress: '0x41e2e64a79b19e9329f82d3d55be5e767a6f571d',
      transactionHash,
      transactionStatus: pending ? 'PENDING' : 'CONFIRMED',
      // 등록 화면으로 만든 건은 ID 가 UUID 다 — 숫자만 뽑으면 안전한 정수 범위를 넘는다.
      blockNumber: pending ? null : 15_230_000 + (parseInt(hex(agent.agentId, 6), 16) % 100_000),
      registeredAt: agent.createdAt,
      explorerUrl: `https://amoy.polygonscan.com/tx/${transactionHash}`,
    },
    verification: {
      offChainOwnerAddress: walletAddress,
      onChainOwnerAddress,
      offChainMetadataHash: metadataHash,
      onChainMetadataHash,
      ownerMatched,
      metadataMatched,
      verified: ownerMatched && metadataMatched,
    },
  }
}

/**
 * 카드 스파크라인용 최근 7일. 실행 횟수에서 결정적으로 만든다 — `Math.random` 을 쓰면
 * refetch 마다 꺾은선이 흔들려 데이터가 바뀐 것처럼 보인다.
 */
const TREND_SHAPE = [0.6, 0.9, 0.75, 1.1, 0.95, 1.3, 1.55]

function buildTrend(executionCount: number, index: number) {
  if (executionCount === 0) return TREND_SHAPE.map(() => 0)
  const daily = Math.max(1, Math.round(executionCount / 200))
  return TREND_SHAPE.map((weight, day) => Math.round(daily * (weight + ((index + day) % 5) * 0.08)))
}

/**
 * 목록에만 있는 값(`revenue`·`trend`). `AgentDetail` 에 넣으면 상세 화면이 그것을
 * 쓰기 시작한다. `AGENTS` 와 `SEEDS` 는 인덱스가 1:1 이다.
 */
const LIST_ONLY = new Map(
  AGENTS.map((agent, index) => [
    agent.agentId,
    { revenue: SEEDS[index].revenue, trend: buildTrend(SEEDS[index].executionCount, index) },
  ]),
)

const DAY_MS = 86_400_000

/** 건당 하루씩 벌린다 — 겹치면 즐겨찾기 목록의 커서가 흔들린다. */
const FAVORITED_BASE = Date.parse('2026-09-08T11:20:00+09:00')

const favoritedTimes = new Map<string, string>(
  AGENTS.filter((agent) => agent.favorited).map((agent, i) => [
    agent.agentId,
    new Date(FAVORITED_BASE - i * DAY_MS).toISOString(),
  ]),
)

/**
 * 즐겨찾기 등록·해제. 픽스처를 실제로 바꾼다. 핸들러가 아니라 여기 있는 것은
 * `favoritedAt` 이 `favorited` 를 바꾸는 순간에만 정해지기 때문이다 — 두 곳에 나누면
 * 별은 켜졌는데 탭에는 안 나온다. 멱등이라 같은 상태면 아무것도 건드리지 않는다.
 */
export function setFavorite(agentId: unknown, next: boolean): AgentFavorite | null {
  const agent = AGENTS.find((a) => a.agentId === agentId)
  if (!agent) return null
  if (agent.favorited !== next) {
    agent.favorited = next
    agent.favoriteCount += next ? 1 : -1
    // 방금 켠 것은 즐겨찾기한 시각이 가장 늦어 목록 첫 장에 뜬다.
    if (next) favoritedTimes.set(agent.agentId, new Date().toISOString())
    else favoritedTimes.delete(agent.agentId)
  }
  return { agentId: agent.agentId, favorited: agent.favorited, favoriteCount: agent.favoriteCount }
}

/**
 * 마이페이지 「판매 중」. 상수가 아니라 함수다 — 모듈 로드 시점에 계산해 두면
 * 새로 등록한 것이 목록에 안 나온다. 정렬은 `sort` 를 받는 핸들러가 한다.
 */
export function myAgents(): MyAgent[] {
  return AGENTS.filter((agent) => agent.owner.nickname === ME).map((agent) => {
    const listOnly = LIST_ONLY.get(agent.agentId)
    return {
      agentId: agent.agentId,
      name: agent.name,
      summary: agent.summary,
      iconUrl: agent.iconUrl,
      price: agent.price,
      // 등록 화면으로 갓 만든 것은 LIST_ONLY 에 없다 — 실적이 없으니 수익 0, 추이도 평평하다.
      revenue: listOnly?.revenue ?? '0',
      executionCount: agent.executionCount,
      favorited: agent.favorited,
      trend: listOnly?.trend ?? TREND_SHAPE.map(() => 0),
      createdAt: agent.createdAt,
    }
  })
}

/**
 * 마이페이지 「즐겨찾기」. `favorited` 플래그가 곧 이 목록이다.
 * 수익은 주지 않는다 — 내가 만들지 않은 에이전트가 섞여 있다.
 */
export function favoriteAgents(): FavoriteAgent[] {
  return AGENTS.filter((agent) => agent.favorited).map((agent) => ({
    agentId: agent.agentId,
    name: agent.name,
    summary: agent.summary,
    iconUrl: agent.iconUrl,
    price: agent.price,
    executionCount: agent.executionCount,
    owner: { nickname: agent.owner.nickname },
    // 시각을 못 찾으면 등록 시각으로 떨어뜨린다 — 정렬이 NaN 으로 흔들리지 않게.
    favoritedAt: favoritedTimes.get(agent.agentId) ?? agent.createdAt,
  }))
}

/**
 * 내가 실행한 건들(SEEDS 인덱스).
 *
 * COMPLETED 실행이 하나도 없는 것은 넣지 않는다 — 차감된 금액이 없으므로 사용했다고 보지
 * 않는다(명세 참고사항). 그래서 실행 0회인 콜드스타트(index 28)는 빠져 있다.
 *
 * 내가 등록한 에이전트(index 0·3·5)도 섞는다. 「판매 중」 탭과 양쪽에 나타나는 상태를
 * 화면에서 볼 수 있어야 한다.
 */
const USED = [0, 1, 3, 5, 7, 9, 11, 13, 15, 17, 19, 21, 29]

const USED_BASE = Date.parse('2026-09-07T14:22:00+09:00')

/**
 * 같은 에이전트를 여러 번 실행했어도 한 건으로 묶고 `myExecutionCount` 로 횟수를 낸다.
 */
export function usedAgents(): UsedAgent[] {
  return USED.map((index, i) => {
    const agent = AGENTS[index]
    // 내 실행 횟수는 전체 누적과 무관하다 — 같은 값을 쓰면 두 필드를 구분한 이유가 사라진다.
    const myExecutionCount = ((i * 7) % 23) + 1
    return {
      agentId: agent.agentId,
      name: agent.name,
      summary: agent.summary,
      iconUrl: agent.iconUrl,
      price: agent.price,
      owner: { nickname: agent.owner.nickname },
      myExecutionCount,
      // 단가 x 내 실행 횟수. 둘 다 응답에 있는 값이라 여기서 계산해도 갈라지지 않는다.
      totalSpent: String(Number(agent.price) * myExecutionCount),
      lastExecutedAt: new Date(USED_BASE - i * DAY_MS).toISOString(),
      averageRating: agent.averageRating,
      reviewCount: agent.reviewCount,
      favorited: agent.favorited,
    }
  })
}

/** 등록 요청에서 상세로 옮겨 담는 값. `onboardingMessage` 는 상세 응답에 없어 버린다. */
export interface NewAgentInput {
  name: string
  summary: string
  description: string
  usageExample: string
  onboardingMessage: string
}

/** 속 노드 원가의 합. 서버가 워크플로우에서 계산하는 값이라 여기서는 고정값이다. */
const MOCK_BASE_COST = '450'

/** 화면이 넘기는 값만 받고 나머지는 서버가 채울 기본값을 흉내 낸다. */
export function addAgent(input: NewAgentInput): AgentDetail {
  const owner = OWNERS[ME]
  const agent: AgentDetail = {
    // 실서버는 UUID 를 준다. 픽스처의 `agt_NN` 은 build() 가 날짜에서 만드는 것이라
    // 여기서 이어 붙이면 8월 범위를 넘겨 createdAt 이 깨진다.
    agentId: crypto.randomUUID(),
    name: input.name,
    summary: input.summary,
    description: input.description,
    usageExample: input.usageExample || null,
    // 파일을 받는 자리가 서버에 없다 — 등록 직후에는 비어 있다.
    iconUrl: null,
    images: [],
    // 원가 × (1 + 등급 프리미엄). 갓 등록한 것은 최저 등급이라 프리미엄이 0 이다.
    price: MOCK_BASE_COST,
    // 갓 등록한 것이라 실적이 없다. 상세 화면이 '—' 를 내야 한다.
    executionCount: 0,
    successRate: 0,
    averageRating: null,
    reviewCount: 0,
    favorited: false,
    favoriteCount: 0,
    owner: { userId: owner.userId, nickname: ME, profileImageUrl: owner.profileImageUrl },
    // 목록은 등록 시각 역순이라 방금 만든 것이 첫 장에 뜬다.
    createdAt: new Date().toISOString(),
  }
  AGENTS.push(agent)
  return agent
}
