import type { PlaceableItem } from '@/api/workflows'
import iconUrl from '@/assets/logo.png'

/**
 * 캔버스에 놓을 수 있는 항목. 내가 만든 노드와 마켓 에이전트가 한 목록에 섞여 있다.
 *
 * 한 쪽이 10건이라, 무한 스크롤을 확인하려면 내 에이전트가 11건이어야 한다.
 */
type Seed = Omit<PlaceableItem, 'owner'> & { owner: string }

const ME = '이싸피'

// 한 줄에 한 건 — Prettier 가 펼치면 건당 10줄이 되어 분산을 표처럼 훑을 수 없다.
// prettier-ignore
const SEEDS: Seed[] = [
  // 내가 등록한 에이전트. 기본 노드는 캔버스에서 만들고 저장할 때 생겨 팔레트에 오르지 않는다.
  { itemId: 'agt_2001', itemType: 'AGENT', nodeType: 'AGENT',   name: '파일 수집',    mine: true,  owner: ME,          iconUrl: null,    executionCount: 312, price: '4',   summary: '지정한 폴더를 훑어 새 파일을 집어 온다' },
  { itemId: 'agt_2002', itemType: 'AGENT', nodeType: 'AGENT',       name: '내용 분석',    mine: true,  owner: ME,          iconUrl: null,    executionCount: 128, price: '12',  summary: '문서에서 주제를 추출합니다' },
  { itemId: 'agt_2003', itemType: 'AGENT', nodeType: 'AGENT',      name: '분류 및 태깅', mine: true,  owner: ME,          iconUrl: null,    executionCount: 96,  price: '6',   summary: '주제별로 태그를 붙여 묶는다' },
  { itemId: 'agt_2004', itemType: 'AGENT', nodeType: 'AGENT',       name: '요약 생성',    mine: true,  owner: ME,          iconUrl: null,    executionCount: 74,  price: '15',  summary: '핵심만 세 줄로 줄인다' },
  { itemId: 'agt_2005', itemType: 'AGENT', nodeType: 'AGENT',      name: '결과 전달',    mine: true,  owner: ME,          iconUrl: null,    executionCount: 71,  price: '3',   summary: null },
  { itemId: 'agt_2006', itemType: 'AGENT', nodeType: 'AGENT',       name: '번역 정리',    mine: true,  owner: ME,          iconUrl: null,    executionCount: 18,  price: '11',  summary: '번역문에서 용어를 하나로 맞춘다' },
  { itemId: 'agt_2007', itemType: 'AGENT', nodeType: 'AGENT',   name: '메일 수신',    mine: true,  owner: ME,          iconUrl: null,    executionCount: 0,   price: '4',   summary: '새 메일이 오면 흐름을 시작한다' },
  // 내가 등록한 에이전트. scope=MINE 에도 나온다.
  { itemId: 'agt_001',  itemType: 'AGENT', nodeType: 'AGENT',   name: '성적 리포트',  mine: true,  owner: ME,          iconUrl,          executionCount: 204, price: '150', summary: '엑셀 성적표를 통계와 가정통신문으로' },
  { itemId: 'agt_002',  itemType: 'AGENT', nodeType: 'AGENT',   name: '계약서 검토',  mine: true,  owner: ME,          iconUrl: null,    executionCount: 88,  price: '220', summary: '조항을 뽑아 위험한 곳을 표시한다' },
  { itemId: 'agt_003',  itemType: 'AGENT', nodeType: 'AGENT',   name: 'CS 답변 초안', mine: true,  owner: ME,          iconUrl,          executionCount: 46,  price: '90',  summary: '문의를 분류해 답변 초안을 만든다' },
  { itemId: 'agt_004',  itemType: 'AGENT', nodeType: 'AGENT',   name: '회의록 정리',  mine: true,  owner: ME,          iconUrl: null,    executionCount: 0,   price: '70',  summary: null },
  // 다른 사람의 에이전트. scope=ALL 에서만 나온다.
  { itemId: 'agt_101',  itemType: 'AGENT', nodeType: 'AGENT',   name: '통계 분석',    mine: false, owner: 'stat-lab',  iconUrl,          executionCount: 820, price: '180', summary: '평균과 분포를 계산합니다' },
  { itemId: 'agt_102',  itemType: 'AGENT', nodeType: 'AGENT',   name: '문서 자동 분류', mine: false, owner: '김싸피',  iconUrl,          executionCount: 640, price: '120', summary: '폴더를 훑어 주제별로 옮긴다' },
  { itemId: 'agt_103',  itemType: 'AGENT', nodeType: 'AGENT',   name: '코드 리뷰',    mine: false, owner: '박싸피',    iconUrl: null,    executionCount: 455, price: '260', summary: '변경 diff 를 받아 문제를 지적한다' },
  { itemId: 'agt_104',  itemType: 'AGENT', nodeType: 'AGENT',   name: '이미지 태깅',  mine: false, owner: '최싸피',    iconUrl,          executionCount: 288, price: '95',  summary: '사진에 설명 태그를 붙인다' },
  { itemId: 'agt_105',  itemType: 'AGENT', nodeType: 'AGENT',   name: '영수증 정산',  mine: false, owner: '정싸피',    iconUrl: null,    executionCount: 176, price: '140', summary: '영수증 이미지에서 금액을 뽑는다' },
  { itemId: 'agt_106',  itemType: 'AGENT', nodeType: 'AGENT',   name: '리서치 수집',  mine: false, owner: 'stat-lab',  iconUrl,          executionCount: 133, price: '210', summary: '주제별 자료를 모아 정리한다' },
  { itemId: 'agt_107',  itemType: 'AGENT', nodeType: 'AGENT',   name: '번역 도우미',  mine: false, owner: '김싸피',    iconUrl: null,    executionCount: 91,  price: '80',  summary: null },
  { itemId: 'agt_108',  itemType: 'AGENT', nodeType: 'AGENT',   name: '일정 요약',    mine: false, owner: '박싸피',    iconUrl,          executionCount: 52,  price: '60',  summary: '캘린더를 훑어 하루를 요약한다' },
  { itemId: 'agt_109',  itemType: 'AGENT', nodeType: 'AGENT',   name: '야간 배치 요약', mine: false, owner: '최싸피', iconUrl: null,    executionCount: 0,   price: '110', summary: '밤새 쌓인 로그를 아침에 요약한다' },
]

/**
 * 판매자가 적어 둔 인사말과 입력 예시. 실행 대기 화면이 첫 에이전트의 것을 빌려 쓴다.
 *
 * 표에 열을 둘 더 붙이면 한 줄에 안 들어와 분산을 훑을 수 없다. 적은 것만 여기 둔다 —
 * 판매자가 비워 두는 쪽이 오히려 흔해서, 빠진 항목이 기본 문구로 떨어지는지도 같이 드러난다.
 */
const GREETINGS: Record<string, { onboardingMessage?: string; usageExample?: string }> = {
  // 픽스처 워크플로우는 모두 이 항목으로 시작한다 — 둘 다 적힌 보통의 경우다.
  agt_2001: {
    onboardingMessage: '어떤 자료를 볼까요?',
    usageExample: '올린 표에서 이번 달 것만 골라 정리해 줘',
  },
  agt_001: {
    onboardingMessage: '성적표를 올려 주세요',
    usageExample: '3학년 2반 성적표로 과목별 평균을 내고 학생별 가정통신문을 써 줘',
  },
  // 인사말만 적고 입력 예시는 비운 판매자. 예시만 기본 문구로 떨어지는지 볼 자리다.
  agt_002: { onboardingMessage: '계약 조건을 표로 올려 주세요' },
  // 상한(500자)을 꽉 채운 경우. placeholder 가 잘리고 줄임표가 붙는지 볼 자리다.
  agt_101: {
    usageExample:
      `지난 분기 매출 표로 지역별 평균과 분포를 내고 ${'이상치를 표시해 줘 '.repeat(30)}`.trim(),
  },
}

export const PLACEABLE_ITEMS: PlaceableItem[] = SEEDS.map((seed) => ({
  ...seed,
  owner: { nickname: seed.owner },
  onboardingMessage: GREETINGS[seed.itemId]?.onboardingMessage ?? null,
  usageExample: GREETINGS[seed.itemId]?.usageExample ?? null,
}))

/** 내 것을 먼저, 그다음 많이 쓰인 순 */
export function byMineThenUsed(a: PlaceableItem, b: PlaceableItem) {
  if (a.mine !== b.mine) return a.mine ? -1 : 1
  return b.executionCount - a.executionCount
}
