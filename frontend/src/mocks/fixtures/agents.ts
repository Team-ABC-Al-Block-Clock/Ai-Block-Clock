import type { AgentCategory, AgentDetail, Tier } from '@/api/agents'
import { PREMIUM_RATE } from '@/api/agents'

/**
 * 에이전트 픽스처 30건.
 *
 * 등급은 성공률·평가 횟수 허들을 만족시키고, 단가는 등급에서 계산한다 — 손으로 적으면
 * 등급을 바꿀 때 같이 안 바뀌어 조용히 거짓 데이터가 된다.
 * 평가 0건인 콜드스타트도 섞었다. 화면이 NaN·빈칸을 내는지 확인해야 한다.
 */
interface Seed {
  name: string
  description: string
  category: AgentCategory
  tier: Tier
  /** 노드 원가 합계(ABC) */
  baseCost: number
  /** 평가 0건이면 null */
  rating: number | null
  ratingCount: number
  runCount: number
  /** 0~1 */
  successRate: number
  favoriteCount: number
  owner: string
  nodeCount: number
  avgDurationSec: number
}

// 한 줄에 한 건 — Prettier 가 펼치면 건당 15줄이 되어 분산을 표처럼 훑을 수 없다.
// prettier-ignore
const SEEDS: Seed[] = [
  // Tier 1 — 성공률 0.97 이상 · 평가 30건 이상
  { name: '메일 초안', description: '받은 메일에 답장 초안 작성', category: '커뮤니케이션', tier: 1, baseCost: 360, rating: 4.8, ratingCount: 214, runCount: 18420, successRate: 0.991, favoriteCount: 3100, owner: '이싸피', nodeCount: 4, avgDurationSec: 12 },
  { name: 'CS 응답', description: '문의에 맞는 답변을 제안', category: '커뮤니케이션', tier: 1, baseCost: 420, rating: 4.7, ratingCount: 186, runCount: 15230, successRate: 0.984, favoriteCount: 2700, owner: '김싸피', nodeCount: 5, avgDurationSec: 15 },
  { name: '뉴스 브리핑', description: '오늘 기사를 세 줄로 요약', category: '리서치', tier: 1, baseCost: 240, rating: 4.9, ratingCount: 302, runCount: 22140, successRate: 0.995, favoriteCount: 3400, owner: '박싸피', nodeCount: 3, avgDurationSec: 8 },
  { name: 'Contract Analyzer', description: '계약 검토와 위험 요소 분석', category: '문서', tier: 1, baseCost: 780, rating: 4.6, ratingCount: 94, runCount: 1248, successRate: 0.972, favoriteCount: 640, owner: '이싸피', nodeCount: 7, avgDurationSec: 34 },
  { name: '성적 분석 리포트', description: '엑셀 성적표를 통계 JSON 으로 정리', category: '데이터', tier: 1, baseCost: 540, rating: 4.8, ratingCount: 128, runCount: 3860, successRate: 0.988, favoriteCount: 910, owner: '최싸피', nodeCount: 6, avgDurationSec: 41 },

  // Tier 2 — 성공률 0.90 이상 · 평가 10건 이상
  { name: '문서 정리', description: '폴더의 파일을 주제별로 분류', category: '문서', tier: 2, baseCost: 300, rating: 4.4, ratingCount: 152, runCount: 9420, successRate: 0.951, favoriteCount: 2400, owner: '이싸피', nodeCount: 4, avgDurationSec: 18 },
  { name: '코드 리뷰', description: '변경된 코드의 문제를 지적', category: '개발', tier: 2, baseCost: 660, rating: 4.3, ratingCount: 78, runCount: 972, successRate: 0.934, favoriteCount: 972, owner: '박싸피', nodeCount: 5, avgDurationSec: 27 },
  { name: '회의록 작성', description: '녹취 텍스트를 항목별로 정리', category: '문서', tier: 2, baseCost: 480, rating: 4.5, ratingCount: 110, runCount: 6180, successRate: 0.963, favoriteCount: 1500, owner: '김싸피', nodeCount: 5, avgDurationSec: 22 },
  { name: '리서치 수집', description: '주제별 자료를 모아 출처 정리', category: '리서치', tier: 2, baseCost: 720, rating: 4.2, ratingCount: 66, runCount: 4310, successRate: 0.918, favoriteCount: 1900, owner: '정싸피', nodeCount: 6, avgDurationSec: 52 },
  { name: '리포트 작성', description: '데이터를 문장으로 풀어 씀', category: '문서', tier: 2, baseCost: 600, rating: 4.4, ratingCount: 88, runCount: 5240, successRate: 0.947, favoriteCount: 1400, owner: '최싸피', nodeCount: 5, avgDurationSec: 31 },
  { name: '계약서 검토', description: '조항의 위험 요소를 표시', category: '문서', tier: 2, baseCost: 840, rating: 4.1, ratingCount: 42, runCount: 888, successRate: 0.906, favoriteCount: 888, owner: '이싸피', nodeCount: 7, avgDurationSec: 46 },
  { name: '슬랙 요약', description: '읽지 않은 대화를 정리', category: '커뮤니케이션', tier: 2, baseCost: 280, rating: 4.5, ratingCount: 134, runCount: 8760, successRate: 0.958, favoriteCount: 2100, owner: '김싸피', nodeCount: 3, avgDurationSec: 11 },
  { name: 'Market Predictor', description: '시장 흐름과 가격 변동 예측', category: '금융', tier: 2, baseCost: 900, rating: 4.0, ratingCount: 51, runCount: 2156, successRate: 0.912, favoriteCount: 730, owner: '박싸피', nodeCount: 8, avgDurationSec: 58 },
  { name: 'API 문서화', description: '코드에서 API 문서 초안 생성', category: '개발', tier: 2, baseCost: 540, rating: 4.2, ratingCount: 61, runCount: 3480, successRate: 0.941, favoriteCount: 1160, owner: '최싸피', nodeCount: 5, avgDurationSec: 26 },
  { name: '가정통신문 생성', description: '분석 결과를 학부모용 문장으로', category: '커뮤니케이션', tier: 2, baseCost: 460, rating: 4.6, ratingCount: 97, runCount: 3720, successRate: 0.969, favoriteCount: 820, owner: '정싸피', nodeCount: 4, avgDurationSec: 19 },

  // Tier 3 — 성공률 0.80 이상
  { name: '일정 요약', description: '캘린더를 하루 단위로 정리', category: '커뮤니케이션', tier: 3, baseCost: 200, rating: 3.9, ratingCount: 64, runCount: 7180, successRate: 0.872, favoriteCount: 1800, owner: '김싸피', nodeCount: 3, avgDurationSec: 9 },
  { name: '데이터 추출', description: '표에서 필요한 열만 뽑아냄', category: '데이터', tier: 3, baseCost: 340, rating: 3.7, ratingCount: 38, runCount: 640, successRate: 0.845, favoriteCount: 640, owner: '최싸피', nodeCount: 4, avgDurationSec: 16 },
  { name: '번역 정리', description: '문서를 번역하고 용어 통일', category: '문서', tier: 3, baseCost: 520, rating: 3.8, ratingCount: 55, runCount: 1240, successRate: 0.861, favoriteCount: 1200, owner: '이싸피', nodeCount: 5, avgDurationSec: 29 },
  { name: '영수증 정리', description: '영수증 사진에서 금액을 추출', category: '금융', tier: 3, baseCost: 380, rating: 3.6, ratingCount: 47, runCount: 1120, successRate: 0.828, favoriteCount: 1100, owner: '박싸피', nodeCount: 4, avgDurationSec: 21 },
  { name: '자막 생성', description: '영상 음성을 자막으로 변환', category: '데이터', tier: 3, baseCost: 640, rating: 3.9, ratingCount: 72, runCount: 1620, successRate: 0.883, favoriteCount: 1600, owner: '정싸피', nodeCount: 4, avgDurationSec: 63 },
  { name: '이력서 검토', description: '직무에 맞게 문장을 다듬음', category: '문서', tier: 3, baseCost: 420, rating: 3.8, ratingCount: 59, runCount: 1300, successRate: 0.857, favoriteCount: 1300, owner: '김싸피', nodeCount: 4, avgDurationSec: 24 },
  { name: '가계부 분류', description: '지출 내역을 항목별로 나눔', category: '금융', tier: 3, baseCost: 260, rating: 3.7, ratingCount: 33, runCount: 764, successRate: 0.834, favoriteCount: 764, owner: '최싸피', nodeCount: 3, avgDurationSec: 13 },
  { name: '테스트 생성', description: '함수에 맞는 테스트를 작성', category: '개발', tier: 3, baseCost: 580, rating: 3.6, ratingCount: 29, runCount: 612, successRate: 0.812, favoriteCount: 612, owner: '박싸피', nodeCount: 5, avgDurationSec: 33 },
  { name: 'Docu Chain', description: '문서 검증 및 위변조 확인', category: '문서', tier: 3, baseCost: 700, rating: 3.9, ratingCount: 41, runCount: 986, successRate: 0.877, favoriteCount: 520, owner: '이싸피', nodeCount: 6, avgDurationSec: 37 },

  // Tier 4 — 허들 미달. 프리미엄 0%
  { name: '이미지 태깅', description: '이미지에 설명 태그를 붙임', category: '데이터', tier: 4, baseCost: 300, rating: 3.2, ratingCount: 18, runCount: 418, successRate: 0.764, favoriteCount: 418, owner: '정싸피', nodeCount: 3, avgDurationSec: 17 },
  { name: '링크 수집', description: '북마크를 주제별로 묶음', category: '리서치', tier: 4, baseCost: 180, rating: 3.4, ratingCount: 22, runCount: 806, successRate: 0.791, favoriteCount: 806, owner: '김싸피', nodeCount: 2, avgDurationSec: 7 },
  { name: '중복 파일 정리', description: '같은 파일을 찾아 하나만 남김', category: '문서', tier: 4, baseCost: 160, rating: 3.1, ratingCount: 14, runCount: 523, successRate: 0.742, favoriteCount: 523, owner: '최싸피', nodeCount: 2, avgDurationSec: 6 },
  { name: '표 정규화', description: '흩어진 표를 하나 형식으로', category: '데이터', tier: 4, baseCost: 240, rating: 2.9, ratingCount: 11, runCount: 347, successRate: 0.708, favoriteCount: 347, owner: '박싸피', nodeCount: 3, avgDurationSec: 14 },

  // 콜드스타트 — 평가 0건
  { name: '이미지 정리', description: '사진을 날짜·장소로 묶음', category: '데이터', tier: 4, baseCost: 220, rating: null, ratingCount: 0, runCount: 0, successRate: 1, favoriteCount: 0, owner: '이싸피', nodeCount: 3, avgDurationSec: 12 },
  { name: '용어집 관리', description: '문서에서 용어를 모아 정의', category: '문서', tier: 4, baseCost: 280, rating: null, ratingCount: 0, runCount: 4, successRate: 1, favoriteCount: 2, owner: '정싸피', nodeCount: 3, avgDurationSec: 15 },
]

function build(seed: Seed, index: number): AgentDetail {
  const premiumRate = PREMIUM_RATE[seed.tier]
  const day = String((index % 28) + 1).padStart(2, '0')
  return {
    agentId: `agt_${String(index + 1).padStart(3, '0')}`,
    name: seed.name,
    description: seed.description,
    category: seed.category,
    tier: seed.tier,
    rating: seed.rating,
    ratingCount: seed.ratingCount,
    runCount: seed.runCount,
    successRate: seed.successRate,
    unitPrice: Math.round(seed.baseCost * (1 + premiumRate)),
    favoriteCount: seed.favoriteCount,
    ownerNickname: seed.owner,
    createdAt: `2026-08-${day}T09:00:00+09:00`,
    baseCost: seed.baseCost,
    // 실행 횟수에서 만든다 — 통계 엔드포인트가 없어 실측값을 알 수 없다.
    tokenUsage: seed.runCount * 1900,
    premiumRate,
    visibility: 'PUBLIC',
    nodeCount: seed.nodeCount,
    avgDurationSec: seed.avgDurationSec,
    inputSchema: '{ "text": "string", "files": "file[]" }',
    outputSchema: '{ "summary": "string", "items": "object[]" }',
  }
}

export const AGENTS: AgentDetail[] = SEEDS.map(build)

// 눈으로 세면 어긋난다 — 실제로 한 번 29건이 됐다.
if (AGENTS.length !== 30) {
  console.warn(`[mock] 에이전트 픽스처가 ${AGENTS.length}건이다. 30건이어야 한다.`)
}

/** 마이페이지의 "내 에이전트" */
export const MY_AGENTS: AgentDetail[] = AGENTS.filter((a) => a.ownerNickname === '이싸피')
