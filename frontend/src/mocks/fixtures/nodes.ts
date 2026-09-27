import type { NodeDetail } from '@/api/nodes'

/**
 * 작업 노드 원본.
 *
 * 팔레트에서 파생하지 않는다 — 기본 노드는 캔버스에서 적어 워크플로우를 저장할 때 만들어지고,
 * 재사용되지 않아 팔레트에 오르지 않는다. 여기 있는 것은 앞서 저장하며 만들어진 셈 치는 값이다.
 */
export const NODE_DETAILS: NodeDetail[] = [
  {
    nodeId: 'node_001',
    name: '파일 수집',
    prompt: '지정한 폴더를 훑어 새로 들어온 파일을 모아 준다.',
    model: 'GEMINI_2_5_FLASH',
    output: { kind: 'TEXT', example: null },
    baseCost: '4',
    currency: 'ABC',
    refFiles: [],
  },
  {
    nodeId: 'node_002',
    name: '내용 분석',
    prompt: '문서를 읽고 주제와 핵심 문장을 뽑는다.',
    model: 'GEMINI_2_5_FLASH',
    // 분기 조건이 읽을 수 있는 노드. 필드 목록이 예시에서 나오는지 여기서 본다.
    output: {
      kind: 'JSON',
      example: { topic: '성적', score: 0, urgent: false, tags: ['…'], detail: { level: 'high' } },
    },
    baseCost: '12',
    currency: 'ABC',
    // 붙여 둔 자료가 있는 노드. 저장본을 다시 열었을 때 목록이 서는지 여기서 확인한다.
    refFiles: [
      { refFileId: 'ref_001', filename: '채점 기준표.pdf', size: 204800 },
      { refFileId: 'ref_002', filename: '분류 규칙.md', size: 3120 },
    ],
  },
  {
    nodeId: 'node_003',
    name: '요약 생성',
    prompt: '분석 결과를 세 줄로 줄인다.',
    // 기본값이 아닌 모델을 하나 섞는다 — 저장본을 다시 열 때 고른 값이 서는지 여기서 드러난다.
    model: 'GEMINI_2_5_PRO',
    output: { kind: 'TEXT', example: null },
    baseCost: '15',
    currency: 'ABC',
    refFiles: [],
  },
]

/** 팔레트에 없는 남의 노드. 권한 실패(`NODE_002`) 경로를 확인할 때 쓴다. */
export const OTHERS_NODE_ID = 'node_900'
