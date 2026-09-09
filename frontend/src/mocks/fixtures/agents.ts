import type { AgentDetail } from '@/api/agents'
import iconUrl from '@/assets/logo.png'

/**
 * 에이전트 픽스처 30건.
 *
 * 단가는 서버가 계산해 문자열로 주는 값이라 여기서도 그대로 적는다 — mock 이 가격 정책을
 * 재구현하면 계약 밖을 흉내 내는 것이 된다.
 * 평가 0건인 콜드스타트와 아이콘이 없는 건(`iconUrl: null`)을 섞었다.
 * 화면이 NaN·빈칸·깨진 이미지를 내는지는 그때만 드러난다.
 */
interface Seed {
  name: string
  summary: string
  /** 실행 1회당 판매 가격. 서버가 계산해서 문자열로 준다 — 여기서 만들지 않는다. */
  price: string
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
  // 평점·실행 횟수가 높은 쪽
  { name: '메일 초안', summary: '받은 메일에 답장 초안 작성', price: '540', averageRating: 4.8, reviewCount: 214, executionCount: 18420, owner: '이싸피', hasIcon: true },
  { name: 'CS 응답', summary: '문의에 맞는 답변을 제안', price: '630', averageRating: 4.7, reviewCount: 186, executionCount: 15230, owner: '김싸피', hasIcon: true },
  { name: '뉴스 브리핑', summary: '오늘 기사를 세 줄로 요약', price: '360', averageRating: 4.9, reviewCount: 302, executionCount: 22140, owner: '박싸피', hasIcon: false },
  { name: 'Contract Analyzer', summary: '계약 검토와 위험 요소 분석', price: '1170', averageRating: 4.6, reviewCount: 94, executionCount: 1248, owner: '이싸피', hasIcon: true },
  { name: '성적 분석 리포트', summary: '엑셀 성적표를 통계 JSON 으로 정리', price: '810', averageRating: 4.8, reviewCount: 128, executionCount: 3860, owner: '최싸피', hasIcon: false },

  { name: '문서 정리', summary: '폴더의 파일을 주제별로 분류', price: '390', averageRating: 4.4, reviewCount: 152, executionCount: 9420, owner: '이싸피', hasIcon: true },
  { name: '코드 리뷰', summary: '변경된 코드의 문제를 지적', price: '858', averageRating: 4.3, reviewCount: 78, executionCount: 972, owner: '박싸피', hasIcon: false },
  { name: '회의록 작성', summary: '녹취 텍스트를 항목별로 정리', price: '624', averageRating: 4.5, reviewCount: 110, executionCount: 6180, owner: '김싸피', hasIcon: true },
  { name: '리서치 수집', summary: '주제별 자료를 모아 출처 정리', price: '936', averageRating: 4.2, reviewCount: 66, executionCount: 4310, owner: '정싸피', hasIcon: false },
  { name: '리포트 작성', summary: '데이터를 문장으로 풀어 씀', price: '780', averageRating: 4.4, reviewCount: 88, executionCount: 5240, owner: '최싸피', hasIcon: true },
  { name: '계약서 검토', summary: '조항의 위험 요소를 표시', price: '1092', averageRating: 4.1, reviewCount: 42, executionCount: 888, owner: '이싸피', hasIcon: false },
  { name: '슬랙 요약', summary: '읽지 않은 대화를 정리', price: '364', averageRating: 4.5, reviewCount: 134, executionCount: 8760, owner: '김싸피', hasIcon: true },
  { name: 'Market Predictor', summary: '시장 흐름과 가격 변동 예측', price: '1170', averageRating: 4.0, reviewCount: 51, executionCount: 2156, owner: '박싸피', hasIcon: false },
  { name: 'API 문서화', summary: '코드에서 API 문서 초안 생성', price: '702', averageRating: 4.2, reviewCount: 61, executionCount: 3480, owner: '최싸피', hasIcon: true },
  { name: '가정통신문 생성', summary: '분석 결과를 학부모용 문장으로', price: '598', averageRating: 4.6, reviewCount: 97, executionCount: 3720, owner: '정싸피', hasIcon: false },

  { name: '일정 요약', summary: '캘린더를 하루 단위로 정리', price: '220', averageRating: 3.9, reviewCount: 64, executionCount: 7180, owner: '김싸피', hasIcon: true },
  { name: '데이터 추출', summary: '표에서 필요한 열만 뽑아냄', price: '374', averageRating: 3.7, reviewCount: 38, executionCount: 640, owner: '최싸피', hasIcon: false },
  { name: '번역 정리', summary: '문서를 번역하고 용어 통일', price: '572', averageRating: 3.8, reviewCount: 55, executionCount: 1240, owner: '이싸피', hasIcon: true },
  { name: '영수증 정리', summary: '영수증 사진에서 금액을 추출', price: '418', averageRating: 3.6, reviewCount: 47, executionCount: 1120, owner: '박싸피', hasIcon: false },
  { name: '자막 생성', summary: '영상 음성을 자막으로 변환', price: '704', averageRating: 3.9, reviewCount: 72, executionCount: 1620, owner: '정싸피', hasIcon: true },
  { name: '이력서 검토', summary: '직무에 맞게 문장을 다듬음', price: '462', averageRating: 3.8, reviewCount: 59, executionCount: 1300, owner: '김싸피', hasIcon: false },
  { name: '가계부 분류', summary: '지출 내역을 항목별로 나눔', price: '286', averageRating: 3.7, reviewCount: 33, executionCount: 764, owner: '최싸피', hasIcon: true },
  { name: '테스트 생성', summary: '함수에 맞는 테스트를 작성', price: '638', averageRating: 3.6, reviewCount: 29, executionCount: 612, owner: '박싸피', hasIcon: false },
  { name: 'Docu Chain', summary: '문서 검증 및 위변조 확인', price: '770', averageRating: 3.9, reviewCount: 41, executionCount: 986, owner: '이싸피', hasIcon: true },

  // 평점·실행 횟수가 낮은 쪽
  { name: '이미지 태깅', summary: '이미지에 설명 태그를 붙임', price: '300', averageRating: 3.2, reviewCount: 18, executionCount: 418, owner: '정싸피', hasIcon: false },
  { name: '링크 수집', summary: '북마크를 주제별로 묶음', price: '180', averageRating: 3.4, reviewCount: 22, executionCount: 806, owner: '김싸피', hasIcon: true },
  { name: '중복 파일 정리', summary: '같은 파일을 찾아 하나만 남김', price: '160', averageRating: 3.1, reviewCount: 14, executionCount: 523, owner: '최싸피', hasIcon: false },
  { name: '표 정규화', summary: '흩어진 표를 하나 형식으로', price: '240', averageRating: 2.9, reviewCount: 11, executionCount: 347, owner: '박싸피', hasIcon: true },

  // 콜드스타트 — 평가 0건. 등록 시각이 가장 늦어 목록 첫 페이지에 뜬다.
  { name: '이미지 정리', summary: '사진을 날짜·장소로 묶음', price: '220', averageRating: null, reviewCount: 0, executionCount: 0, owner: '이싸피', hasIcon: false },
  { name: '용어집 관리', summary: '문서에서 용어를 모아 정의', price: '280', averageRating: null, reviewCount: 0, executionCount: 4, owner: '정싸피', hasIcon: true },
]

/** 등록 시각은 건당 하루씩 벌린다. 겹치면 등록 시각 역순 정렬이 흔들려
 *  커서 페이지네이션에서 중복·누락이 생긴다. */
function build(seed: Seed, index: number): AgentDetail {
  const day = String(index + 1).padStart(2, '0')
  return {
    agentId: `agt_${day}`,
    name: seed.name,
    summary: seed.summary,
    iconUrl: seed.hasIcon ? iconUrl : null,
    price: seed.price,
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
