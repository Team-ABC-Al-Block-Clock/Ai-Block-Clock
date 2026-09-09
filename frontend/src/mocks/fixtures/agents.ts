import type { AgentDetail, AgentImage, AgentSummary, MyAgent } from '@/api/agents'
import iconUrl from '@/assets/logo.png'

/**
 * 에이전트 픽스처 30건.
 *
 * 단가·성공률·노드 개수·즐겨찾기 수는 서버가 기록해서 주는 값이라 여기서도 그대로 적는다 —
 * mock 이 정책을 재구현하면 계약 밖을 흉내 내는 것이 된다.
 * 평가 0건인 콜드스타트와 아이콘이 없는 건(`iconUrl: null`)을 섞었다.
 * 화면이 NaN·빈칸·깨진 이미지를 내는지는 그때만 드러난다.
 */

/** 만든 사람 5명. `Seed.owner` 가 `keyof typeof OWNERS` 라서 오타는 컴파일에서 걸린다.
 *  프로필 이미지가 없는 사람을 섞었다 — 화면의 아바타 폴백을 확인해야 한다. */
const OWNERS = {
  이싸피: { userId: 'usr_01', profileImageUrl: iconUrl },
  김싸피: { userId: 'usr_02', profileImageUrl: null },
  박싸피: { userId: 'usr_03', profileImageUrl: iconUrl },
  최싸피: { userId: 'usr_04', profileImageUrl: null },
  정싸피: { userId: 'usr_05', profileImageUrl: null },
} satisfies Record<string, { userId: string; profileImageUrl: string | null }>

interface Seed {
  name: string
  summary: string
  /** 실행 1회당 판매 가격. 서버가 계산해서 문자열로 준다 — 여기서 만들지 않는다. */
  price: string
  /** 몇 단계로 돌아가는지 */
  nodeCount: number
  /** 실행 성공률 0~1. 실행이 0회면 서버도 비율을 만들 수 없어 0 이다 */
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
  { name: '메일 초안', summary: '받은 메일에 답장 초안 작성', price: '540', nodeCount: 4, successRate: 0.986, averageRating: 4.8, reviewCount: 214, executionCount: 18420, favoriteCount: 642, owner: '이싸피', hasIcon: true },
  { name: 'CS 응답', summary: '문의에 맞는 답변을 제안', price: '630', nodeCount: 5, successRate: 0.982, averageRating: 4.7, reviewCount: 186, executionCount: 15230, favoriteCount: 517, owner: '김싸피', hasIcon: true },
  { name: '뉴스 브리핑', summary: '오늘 기사를 세 줄로 요약', price: '360', nodeCount: 3, successRate: 0.991, averageRating: 4.9, reviewCount: 302, executionCount: 22140, favoriteCount: 908, owner: '박싸피', hasIcon: false },
  { name: 'Contract Analyzer', summary: '계약 검토와 위험 요소 분석', price: '1170', nodeCount: 8, successRate: 0.964, averageRating: 4.6, reviewCount: 94, executionCount: 1248, favoriteCount: 213, owner: '이싸피', hasIcon: true },
  { name: '성적 분석 리포트', summary: '엑셀 성적표를 통계 JSON 으로 정리', price: '810', nodeCount: 6, successRate: 0.978, averageRating: 4.8, reviewCount: 128, executionCount: 3860, favoriteCount: 341, owner: '최싸피', hasIcon: false },

  { name: '문서 정리', summary: '폴더의 파일을 주제별로 분류', price: '390', nodeCount: 4, successRate: 0.972, averageRating: 4.4, reviewCount: 152, executionCount: 9420, favoriteCount: 428, owner: '이싸피', hasIcon: true },
  { name: '코드 리뷰', summary: '변경된 코드의 문제를 지적', price: '858', nodeCount: 7, successRate: 0.951, averageRating: 4.3, reviewCount: 78, executionCount: 972, favoriteCount: 186, owner: '박싸피', hasIcon: false },
  { name: '회의록 작성', summary: '녹취 텍스트를 항목별로 정리', price: '624', nodeCount: 5, successRate: 0.968, averageRating: 4.5, reviewCount: 110, executionCount: 6180, favoriteCount: 297, owner: '김싸피', hasIcon: true },
  { name: '리서치 수집', summary: '주제별 자료를 모아 출처 정리', price: '936', nodeCount: 9, successRate: 0.943, averageRating: 4.2, reviewCount: 66, executionCount: 4310, favoriteCount: 158, owner: '정싸피', hasIcon: false },
  { name: '리포트 작성', summary: '데이터를 문장으로 풀어 씀', price: '780', nodeCount: 6, successRate: 0.959, averageRating: 4.4, reviewCount: 88, executionCount: 5240, favoriteCount: 224, owner: '최싸피', hasIcon: true },
  { name: '계약서 검토', summary: '조항의 위험 요소를 표시', price: '1092', nodeCount: 8, successRate: 0.937, averageRating: 4.1, reviewCount: 42, executionCount: 888, favoriteCount: 121, owner: '이싸피', hasIcon: false },
  { name: '슬랙 요약', summary: '읽지 않은 대화를 정리', price: '364', nodeCount: 3, successRate: 0.974, averageRating: 4.5, reviewCount: 134, executionCount: 8760, favoriteCount: 386, owner: '김싸피', hasIcon: true },
  { name: 'Market Predictor', summary: '시장 흐름과 가격 변동 예측', price: '1170', nodeCount: 9, successRate: 0.912, averageRating: 4.0, reviewCount: 51, executionCount: 2156, favoriteCount: 147, owner: '박싸피', hasIcon: false },
  { name: 'API 문서화', summary: '코드에서 API 문서 초안 생성', price: '702', nodeCount: 5, successRate: 0.946, averageRating: 4.2, reviewCount: 61, executionCount: 3480, favoriteCount: 172, owner: '최싸피', hasIcon: true },
  { name: '가정통신문 생성', summary: '분석 결과를 학부모용 문장으로', price: '598', nodeCount: 4, successRate: 0.969, averageRating: 4.6, reviewCount: 97, executionCount: 3720, favoriteCount: 253, owner: '정싸피', hasIcon: false },

  { name: '일정 요약', summary: '캘린더를 하루 단위로 정리', price: '220', nodeCount: 2, successRate: 0.965, averageRating: 3.9, reviewCount: 64, executionCount: 7180, favoriteCount: 208, owner: '김싸피', hasIcon: true },
  { name: '데이터 추출', summary: '표에서 필요한 열만 뽑아냄', price: '374', nodeCount: 3, successRate: 0.928, averageRating: 3.7, reviewCount: 38, executionCount: 640, favoriteCount: 94, owner: '최싸피', hasIcon: false },
  { name: '번역 정리', summary: '문서를 번역하고 용어 통일', price: '572', nodeCount: 5, successRate: 0.934, averageRating: 3.8, reviewCount: 55, executionCount: 1240, favoriteCount: 131, owner: '이싸피', hasIcon: true },
  { name: '영수증 정리', summary: '영수증 사진에서 금액을 추출', price: '418', nodeCount: 4, successRate: 0.906, averageRating: 3.6, reviewCount: 47, executionCount: 1120, favoriteCount: 108, owner: '박싸피', hasIcon: false },
  { name: '자막 생성', summary: '영상 음성을 자막으로 변환', price: '704', nodeCount: 6, successRate: 0.921, averageRating: 3.9, reviewCount: 72, executionCount: 1620, favoriteCount: 164, owner: '정싸피', hasIcon: true },
  { name: '이력서 검토', summary: '직무에 맞게 문장을 다듬음', price: '462', nodeCount: 4, successRate: 0.941, averageRating: 3.8, reviewCount: 59, executionCount: 1300, favoriteCount: 126, owner: '김싸피', hasIcon: false },
  { name: '가계부 분류', summary: '지출 내역을 항목별로 나눔', price: '286', nodeCount: 3, successRate: 0.953, averageRating: 3.7, reviewCount: 33, executionCount: 764, favoriteCount: 87, owner: '최싸피', hasIcon: true },
  { name: '테스트 생성', summary: '함수에 맞는 테스트를 작성', price: '638', nodeCount: 5, successRate: 0.918, averageRating: 3.6, reviewCount: 29, executionCount: 612, favoriteCount: 73, owner: '박싸피', hasIcon: false },
  { name: 'Docu Chain', summary: '문서 검증 및 위변조 확인', price: '770', nodeCount: 7, successRate: 0.932, averageRating: 3.9, reviewCount: 41, executionCount: 986, favoriteCount: 112, owner: '이싸피', hasIcon: true },

  // 평점·실행 횟수가 낮은 쪽
  { name: '이미지 태깅', summary: '이미지에 설명 태그를 붙임', price: '300', nodeCount: 3, successRate: 0.884, averageRating: 3.2, reviewCount: 18, executionCount: 418, favoriteCount: 46, owner: '정싸피', hasIcon: false },
  { name: '링크 수집', summary: '북마크를 주제별로 묶음', price: '180', nodeCount: 2, successRate: 0.897, averageRating: 3.4, reviewCount: 22, executionCount: 806, favoriteCount: 58, owner: '김싸피', hasIcon: true },
  { name: '중복 파일 정리', summary: '같은 파일을 찾아 하나만 남김', price: '160', nodeCount: 2, successRate: 0.876, averageRating: 3.1, reviewCount: 14, executionCount: 523, favoriteCount: 31, owner: '최싸피', hasIcon: false },
  { name: '표 정규화', summary: '흩어진 표를 하나 형식으로', price: '240', nodeCount: 3, successRate: 0.862, averageRating: 2.9, reviewCount: 11, executionCount: 347, favoriteCount: 24, owner: '박싸피', hasIcon: true },

  // 콜드스타트 — 평가 0건. 등록 시각이 가장 늦어 목록 첫 페이지에 뜬다.
  // 실행이 없으면 성공률도 만들 수 없다(0). 화면은 '—' 를 내야 한다.
  { name: '이미지 정리', summary: '사진을 날짜·장소로 묶음', price: '220', nodeCount: 3, successRate: 0, averageRating: null, reviewCount: 0, executionCount: 0, favoriteCount: 0, owner: '이싸피', hasIcon: false },
  { name: '용어집 관리', summary: '문서에서 용어를 모아 정의', price: '280', nodeCount: 4, successRate: 0.75, averageRating: null, reviewCount: 0, executionCount: 4, favoriteCount: 1, owner: '정싸피', hasIcon: true },
]

/** 상세 설명의 뒷문장. 두 개를 번갈아 써서 길이가 갈리게 한다 — 줄바꿈이 한 줄일 때만
 *  잘 보이는 레이아웃 사고가 있다. */
const DESCRIPTION_TAILS = [
  '실행하면 대상 목록을 먼저 보여주고, 확인한 뒤에 처리합니다. 처리 내역은 실행이 끝난 뒤 목록으로 확인할 수 있습니다.',
  '입력 형식을 강제하지 않으므로 가진 자료를 그대로 넣으면 됩니다. 중간에 멈춰도 그때까지 처리한 것은 그대로 남습니다.',
]

const IMAGE_LABEL = { INPUT: '입력', OUTPUT: '출력' } as const

/** 스크린샷 자산이 없어서 라벨이 박힌 SVG 를 data URI 로 만든다.
 *  같은 그림을 5장 돌려 쓰면 이전/다음·썸네일 전환이 동작하는지 화면으로 확인할 수 없다. */
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

/** 유형별 0~5장(명세상 최대 5장). 0장인 건이 있어야 화면의 '스크린샷 없음' 자리를 볼 수 있다. */
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

/** 등록 시각은 건당 하루씩 벌린다. 겹치면 등록 시각 역순 정렬이 흔들려
 *  커서 페이지네이션에서 중복·누락이 생긴다. */
function build(seed: Seed, index: number): AgentDetail {
  const day = String(index + 1).padStart(2, '0')
  const agentId = `agt_${day}`
  const owner = OWNERS[seed.owner]
  return {
    agentId,
    name: seed.name,
    summary: seed.summary,
    // ponytail: 설명·실행 예시는 summary 기반 템플릿이다. 30건 문장을 손으로 적을 값어치가
    // 없고, 단가처럼 서버가 계산하는 정책값이 아니라 텍스트다. 화면이 1440×1520 고정
    // 좌표라 설명이 3줄을 넘기면 '스크린샷' 섹션과 겹치므로 짧게 유지한다 —
    // 흐름 레이아웃으로 바꾸면 이 제약이 없어진다.
    description: `${seed.summary}. ${DESCRIPTION_TAILS[index % DESCRIPTION_TAILS.length]}`,
    // 일부는 null — 만든 사람이 안 적은 경우다. 화면의 null 분기가 여기서만 드러난다.
    usageExample: index % 7 === 3 ? null : `${seed.summary}해 줘`,
    iconUrl: seed.hasIcon ? iconUrl : null,
    images: buildImages(agentId, seed.name, index),
    price: seed.price,
    currency: 'ABC',
    nodeCount: seed.nodeCount,
    executionCount: seed.executionCount,
    successRate: seed.successRate,
    averageRating: seed.averageRating,
    reviewCount: seed.reviewCount,
    // 미로그인 기준. 로그인별 계산은 인증 연동(S15P21A403-180) 뒤에 붙인다.
    favorited: false,
    favoriteCount: seed.favoriteCount,
    owner: { userId: owner.userId, nickname: seed.owner, profileImageUrl: owner.profileImageUrl },
    createdAt: `2026-08-${day}T09:00:00+09:00`,
  }
}

export const AGENTS: AgentDetail[] = SEEDS.map(build)

// 눈으로 세면 어긋난다 — 실제로 한 번 29건이 됐다.
// 31건을 넘기면 build() 의 day 가 8월 범위를 벗어나 createdAt 이 Invalid Date 가 되는데,
// 정렬은 문자열 비교라 계속 동작하는 것처럼 보인다. warn 으로는 아무도 못 잡는다.
if (AGENTS.length !== 30) {
  throw new Error(`[mock] 에이전트 픽스처가 ${AGENTS.length}건이다. 30건이어야 한다.`)
}

/**
 * 상세 픽스처를 목록 항목으로 줄인다. 상세 필드(설명·스크린샷·노드 개수·성공률)를
 * 흘려보내면 실서버에 없는 필드를 화면이 몰래 쓰게 되고, 백엔드를 붙이는 날
 * 조용히 `undefined` 가 된다.
 *
 * 핸들러가 아니라 여기 있는 이유: `MY_AGENTS` 도 같은 투영이 필요해서, 두 곳에
 * 필드를 나열해 두면 한쪽만 고쳐졌을 때 조용히 갈라진다.
 */
export function toSummary(agent: AgentDetail): AgentSummary {
  return {
    agentId: agent.agentId,
    name: agent.name,
    summary: agent.summary,
    iconUrl: agent.iconUrl,
    price: agent.price,
    currency: agent.currency,
    executionCount: agent.executionCount,
    averageRating: agent.averageRating,
    reviewCount: agent.reviewCount,
    favorited: agent.favorited,
    owner: { nickname: agent.owner.nickname },
    createdAt: agent.createdAt,
  }
}

/** 마이페이지의 "내 에이전트". GET /agents 는 목록 항목 + tokenUsage 다. */
export const MY_AGENTS: MyAgent[] = AGENTS.filter((a) => a.owner.nickname === '이싸피').map(
  (a) => ({
    ...toSummary(a),
    // 실행 횟수에서 만든다 — 통계 엔드포인트가 없어 실측값을 알 수 없다.
    tokenUsage: a.executionCount * 1900,
  }),
)
