import type { AgentDetail } from '@/api/agents'
import iconUrl from '@/assets/logo.png'

/** Tier 1(Platinum) ~ Tier 4(Bronze).
 *  등급은 내부 자료라 어떤 응답에도 담기지 않는다. 단가를 만드는 근거로만 쓰이므로
 *  API 타입이 아니라 픽스처에 둔다. 단가 = Base Cost × (1 + Premium Rate) */
type Tier = 1 | 2 | 3 | 4
const PREMIUM_RATE: Record<Tier, number> = { 1: 0.5, 2: 0.3, 3: 0.1, 4: 0 }

/**
 * 에이전트 픽스처 30건.
 *
 * 등급은 성공률·평가 횟수 허들을 만족시키고, 단가는 등급에서 계산한다 — 손으로 적으면
 * 등급을 바꿀 때 같이 안 바뀌어 조용히 거짓 데이터가 된다.
 * 평가 0건인 콜드스타트와 아이콘이 없는 건(`iconUrl: null`)을 섞었다.
 * 화면이 NaN·빈칸·깨진 이미지를 내는지는 그때만 드러난다.
 */
interface Seed {
  name: string
  summary: string
  tier: Tier
  /** 노드 원가 합계(ABC) */
  baseCost: number
  /** 평가 0건이면 null */
  averageRating: number | null
  reviewCount: number
  executionCount: number
  owner: string
  /** false 면 iconUrl 이 null — 화면은 이니셜로 폴백해야 한다 */
  hasIcon: boolean
}

// 한 줄에 한 건 — Prettier 가 펼치면 건당 15줄이 되어 분산을 표처럼 훑을 수 없다.
// prettier-ignore
const SEEDS: Seed[] = [
  // Tier 1 — 성공률 0.97 이상 · 평가 30건 이상
  { name: '메일 초안', summary: '받은 메일에 답장 초안 작성', tier: 1, baseCost: 360, averageRating: 4.8, reviewCount: 214, executionCount: 18420, owner: '이싸피', hasIcon: true },
  { name: 'CS 응답', summary: '문의에 맞는 답변을 제안', tier: 1, baseCost: 420, averageRating: 4.7, reviewCount: 186, executionCount: 15230, owner: '김싸피', hasIcon: true },
  { name: '뉴스 브리핑', summary: '오늘 기사를 세 줄로 요약', tier: 1, baseCost: 240, averageRating: 4.9, reviewCount: 302, executionCount: 22140, owner: '박싸피', hasIcon: false },
  { name: 'Contract Analyzer', summary: '계약 검토와 위험 요소 분석', tier: 1, baseCost: 780, averageRating: 4.6, reviewCount: 94, executionCount: 1248, owner: '이싸피', hasIcon: true },
  { name: '성적 분석 리포트', summary: '엑셀 성적표를 통계 JSON 으로 정리', tier: 1, baseCost: 540, averageRating: 4.8, reviewCount: 128, executionCount: 3860, owner: '최싸피', hasIcon: false },

  // Tier 2 — 성공률 0.90 이상 · 평가 10건 이상
  { name: '문서 정리', summary: '폴더의 파일을 주제별로 분류', tier: 2, baseCost: 300, averageRating: 4.4, reviewCount: 152, executionCount: 9420, owner: '이싸피', hasIcon: true },
  { name: '코드 리뷰', summary: '변경된 코드의 문제를 지적', tier: 2, baseCost: 660, averageRating: 4.3, reviewCount: 78, executionCount: 972, owner: '박싸피', hasIcon: false },
  { name: '회의록 작성', summary: '녹취 텍스트를 항목별로 정리', tier: 2, baseCost: 480, averageRating: 4.5, reviewCount: 110, executionCount: 6180, owner: '김싸피', hasIcon: true },
  { name: '리서치 수집', summary: '주제별 자료를 모아 출처 정리', tier: 2, baseCost: 720, averageRating: 4.2, reviewCount: 66, executionCount: 4310, owner: '정싸피', hasIcon: false },
  { name: '리포트 작성', summary: '데이터를 문장으로 풀어 씀', tier: 2, baseCost: 600, averageRating: 4.4, reviewCount: 88, executionCount: 5240, owner: '최싸피', hasIcon: true },
  { name: '계약서 검토', summary: '조항의 위험 요소를 표시', tier: 2, baseCost: 840, averageRating: 4.1, reviewCount: 42, executionCount: 888, owner: '이싸피', hasIcon: false },
  { name: '슬랙 요약', summary: '읽지 않은 대화를 정리', tier: 2, baseCost: 280, averageRating: 4.5, reviewCount: 134, executionCount: 8760, owner: '김싸피', hasIcon: true },
  { name: 'Market Predictor', summary: '시장 흐름과 가격 변동 예측', tier: 2, baseCost: 900, averageRating: 4.0, reviewCount: 51, executionCount: 2156, owner: '박싸피', hasIcon: false },
  { name: 'API 문서화', summary: '코드에서 API 문서 초안 생성', tier: 2, baseCost: 540, averageRating: 4.2, reviewCount: 61, executionCount: 3480, owner: '최싸피', hasIcon: true },
  { name: '가정통신문 생성', summary: '분석 결과를 학부모용 문장으로', tier: 2, baseCost: 460, averageRating: 4.6, reviewCount: 97, executionCount: 3720, owner: '정싸피', hasIcon: false },

  // Tier 3 — 성공률 0.80 이상
  { name: '일정 요약', summary: '캘린더를 하루 단위로 정리', tier: 3, baseCost: 200, averageRating: 3.9, reviewCount: 64, executionCount: 7180, owner: '김싸피', hasIcon: true },
  { name: '데이터 추출', summary: '표에서 필요한 열만 뽑아냄', tier: 3, baseCost: 340, averageRating: 3.7, reviewCount: 38, executionCount: 640, owner: '최싸피', hasIcon: false },
  { name: '번역 정리', summary: '문서를 번역하고 용어 통일', tier: 3, baseCost: 520, averageRating: 3.8, reviewCount: 55, executionCount: 1240, owner: '이싸피', hasIcon: true },
  { name: '영수증 정리', summary: '영수증 사진에서 금액을 추출', tier: 3, baseCost: 380, averageRating: 3.6, reviewCount: 47, executionCount: 1120, owner: '박싸피', hasIcon: false },
  { name: '자막 생성', summary: '영상 음성을 자막으로 변환', tier: 3, baseCost: 640, averageRating: 3.9, reviewCount: 72, executionCount: 1620, owner: '정싸피', hasIcon: true },
  { name: '이력서 검토', summary: '직무에 맞게 문장을 다듬음', tier: 3, baseCost: 420, averageRating: 3.8, reviewCount: 59, executionCount: 1300, owner: '김싸피', hasIcon: false },
  { name: '가계부 분류', summary: '지출 내역을 항목별로 나눔', tier: 3, baseCost: 260, averageRating: 3.7, reviewCount: 33, executionCount: 764, owner: '최싸피', hasIcon: true },
  { name: '테스트 생성', summary: '함수에 맞는 테스트를 작성', tier: 3, baseCost: 580, averageRating: 3.6, reviewCount: 29, executionCount: 612, owner: '박싸피', hasIcon: false },
  { name: 'Docu Chain', summary: '문서 검증 및 위변조 확인', tier: 3, baseCost: 700, averageRating: 3.9, reviewCount: 41, executionCount: 986, owner: '이싸피', hasIcon: true },

  // Tier 4 — 허들 미달. 프리미엄 0%
  { name: '이미지 태깅', summary: '이미지에 설명 태그를 붙임', tier: 4, baseCost: 300, averageRating: 3.2, reviewCount: 18, executionCount: 418, owner: '정싸피', hasIcon: false },
  { name: '링크 수집', summary: '북마크를 주제별로 묶음', tier: 4, baseCost: 180, averageRating: 3.4, reviewCount: 22, executionCount: 806, owner: '김싸피', hasIcon: true },
  { name: '중복 파일 정리', summary: '같은 파일을 찾아 하나만 남김', tier: 4, baseCost: 160, averageRating: 3.1, reviewCount: 14, executionCount: 523, owner: '최싸피', hasIcon: false },
  { name: '표 정규화', summary: '흩어진 표를 하나 형식으로', tier: 4, baseCost: 240, averageRating: 2.9, reviewCount: 11, executionCount: 347, owner: '박싸피', hasIcon: true },

  // 콜드스타트 — 평가 0건. 등록 시각이 가장 늦어 목록 첫 페이지에 뜬다.
  { name: '이미지 정리', summary: '사진을 날짜·장소로 묶음', tier: 4, baseCost: 220, averageRating: null, reviewCount: 0, executionCount: 0, owner: '이싸피', hasIcon: false },
  { name: '용어집 관리', summary: '문서에서 용어를 모아 정의', tier: 4, baseCost: 280, averageRating: null, reviewCount: 0, executionCount: 4, owner: '정싸피', hasIcon: true },
]

/** 등록 시각은 건당 하루씩 벌린다. 겹치면 등록 시각 역순 정렬이 흔들려
 *  커서 페이지네이션에서 중복·누락이 생긴다. */
function build(seed: Seed, index: number): AgentDetail {
  const premiumRate = PREMIUM_RATE[seed.tier]
  const day = String(index + 1).padStart(2, '0')
  return {
    agentId: `agt_${day}`,
    name: seed.name,
    summary: seed.summary,
    iconUrl: seed.hasIcon ? iconUrl : null,
    // 단가는 등급에서 계산한다. 소수점 오차를 막기 위해 명세가 문자열로 준다.
    price: String(Math.round(seed.baseCost * (1 + premiumRate))),
    currency: 'ABC',
    executionCount: seed.executionCount,
    averageRating: seed.averageRating,
    reviewCount: seed.reviewCount,
    // 미로그인 기준. 로그인별 계산은 인증 연동(S15P21A403-180) 뒤에 붙인다.
    favorited: false,
    owner: { nickname: seed.owner },
    createdAt: `2026-08-${day}T09:00:00+09:00`,
    // 실행 횟수에서 만든다 — 통계 엔드포인트가 없어 실측값을 알 수 없다.
    tokenUsage: seed.executionCount * 1900,
  }
}

export const AGENTS: AgentDetail[] = SEEDS.map(build)

// 눈으로 세면 어긋난다 — 실제로 한 번 29건이 됐다.
// 31건을 넘기면 build() 의 day 가 8월 범위를 벗어나 createdAt 이 Invalid Date 가 되는데,
// 정렬은 문자열 비교라 계속 동작하는 것처럼 보인다. warn 으로는 아무도 못 잡는다.
if (AGENTS.length !== 30) {
  throw new Error(`[mock] 에이전트 픽스처가 ${AGENTS.length}건이다. 30건이어야 한다.`)
}

/** 마이페이지의 "내 에이전트" */
export const MY_AGENTS: AgentDetail[] = AGENTS.filter((a) => a.owner.nickname === '이싸피')
