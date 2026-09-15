import type { PlaceableItem } from '@/api/workflows'
import iconUrl from '@/assets/logo.png'

/**
 * 캔버스에 놓을 수 있는 항목. 내가 만든 노드와 마켓 에이전트가 한 목록에 섞여 있다.
 *
 * 내 것이 11건이다 — 기본 `size` 가 10 이라 그보다 적으면 `scope=MINE` 에서 `hasNext` 가
 * 늘 false 여서 무한 스크롤을 확인할 방법이 없다.
 */
type Seed = Omit<PlaceableItem, 'currency' | 'owner'> & { owner: string }

const ME = '이싸피'

// 한 줄에 한 건 — Prettier 가 펼치면 건당 10줄이 되어 분산을 표처럼 훑을 수 없다.
// prettier-ignore
const SEEDS: Seed[] = [
  // 내가 만든 기본 노드. 아직 한 번도 안 쓴 것(0회)과 설명을 안 적은 것을 섞는다.
  { itemId: 'node_001', itemType: 'NODE',  nodeType: 'TRIGGER', name: '파일 수집',    mine: true,  owner: ME,          iconUrl: null,    executionCount: 312, price: '4',   summary: '지정한 폴더를 훑어 새 파일을 집어 온다' },
  { itemId: 'node_002', itemType: 'NODE',  nodeType: 'LLM',     name: '내용 분석',    mine: true,  owner: ME,          iconUrl: null,    executionCount: 128, price: '12',  summary: '문서에서 주제를 추출합니다' },
  { itemId: 'node_003', itemType: 'NODE',  nodeType: 'TOOL',    name: '분류 및 태깅', mine: true,  owner: ME,          iconUrl: null,    executionCount: 96,  price: '6',   summary: '주제별로 태그를 붙여 묶는다' },
  { itemId: 'node_004', itemType: 'NODE',  nodeType: 'LLM',     name: '요약 생성',    mine: true,  owner: ME,          iconUrl: null,    executionCount: 74,  price: '15',  summary: '핵심만 세 줄로 줄인다' },
  { itemId: 'node_005', itemType: 'NODE',  nodeType: 'TOOL',    name: '결과 전달',    mine: true,  owner: ME,          iconUrl: null,    executionCount: 71,  price: '3',   summary: null },
  { itemId: 'node_006', itemType: 'NODE',  nodeType: 'LLM',     name: '번역 정리',    mine: true,  owner: ME,          iconUrl: null,    executionCount: 18,  price: '11',  summary: '번역문에서 용어를 하나로 맞춘다' },
  { itemId: 'node_007', itemType: 'NODE',  nodeType: 'TRIGGER', name: '메일 수신',    mine: true,  owner: ME,          iconUrl: null,    executionCount: 0,   price: '4',   summary: '새 메일이 오면 흐름을 시작한다' },
  // 내가 등록한 에이전트. scope=MINE 에도 나온다.
  { itemId: 'agt_001',  itemType: 'AGENT', nodeType: 'AGENT',   name: '성적 리포트',  mine: true,  owner: ME,          iconUrl,          executionCount: 204, price: '150', summary: '엑셀 성적표를 통계와 가정통신문으로' },
  { itemId: 'agt_002',  itemType: 'AGENT', nodeType: 'AGENT',   name: '계약서 검토',  mine: true,  owner: ME,          iconUrl: null,    executionCount: 88,  price: '220', summary: '조항을 뽑아 위험한 곳을 표시한다' },
  { itemId: 'agt_003',  itemType: 'AGENT', nodeType: 'AGENT',   name: 'CS 답변 초안', mine: true,  owner: ME,          iconUrl,          executionCount: 46,  price: '90',  summary: '문의를 분류해 답변 초안을 만든다' },
  { itemId: 'agt_004',  itemType: 'AGENT', nodeType: 'AGENT',   name: '회의록 정리',  mine: true,  owner: ME,          iconUrl: null,    executionCount: 0,   price: '70',  summary: null },
  // 남의 에이전트. scope=ALL 에서만 나온다.
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

export const PLACEABLE_ITEMS: PlaceableItem[] = SEEDS.map((seed) => ({
  ...seed,
  currency: 'ABC',
  owner: { nickname: seed.owner },
}))

/** 내 것을 먼저, 그다음 많이 쓰인 순 */
export function byMineThenUsed(a: PlaceableItem, b: PlaceableItem) {
  if (a.mine !== b.mine) return a.mine ? -1 : 1
  return b.executionCount - a.executionCount
}

// 눈으로 세면 어긋난다. 11건 아래로 줄면 scope=MINE 의 무한 스크롤 검증이 조용히 사라진다.
const mine = PLACEABLE_ITEMS.filter((item) => item.mine)
if (mine.length !== 11) {
  throw new Error(`[mock] 내 항목이 ${mine.length}건이다. 11건이어야 한다.`)
}
