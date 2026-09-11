import type { WorkflowSummary } from '@/api/workflows'

/**
 * 내 워크플로우 픽스처. 에이전트로 등록하지 않고 내가 쓰는 것들이다(MYP-002).
 *
 * 응답 5필드에 **응답에 없는 내부 필드** 셋을 더한다. 핸들러만 읽고 `toWorkflowSummary` 가
 * 응답에서 떼어낸다 — `AgentDetail → toSummary` 와 같은 투영이다.
 *
 * 12건이다. 명세 기본 `size` 가 10 이라 그보다 적으면 `hasNext` 가 늘 false 여서
 * 화면의 무한 스크롤을 확인할 방법이 없다.
 */
interface WorkflowSeed extends WorkflowSummary {
  /** 응답에 없다 — `sort=LATEST` 를 계산할 근거. 명세 응답에 등록 시각이 없어
   *  이것 없이는 정렬을 흉내 낼 수 없다. */
  createdAt: string
  /** 응답에 없다 — 이미 에이전트로 등록된 것은 삭제할 수 없다(WORKFLOW_009). */
  registeredAsAgent: boolean
  /** 응답에 없다 — 실행이 진행 중이면 삭제할 수 없다(WORKFLOW_010). */
  running: boolean
}

/**
 * 원본. `WORKFLOWS` 는 삭제로 바뀌므로 되돌릴 기준이 필요하다.
 *
 * 실행 이력이 없는 건(`lastExecutedAt: null`)을 반드시 섞는다 — 기본 정렬이
 * 「아직 실행하지 않은 것은 뒤로 밀린다」라서 그 건이 없으면 정렬을 검증할 수 없고,
 * 화면이 'Invalid Date' 를 내는지도 드러나지 않는다.
 */
// 한 줄에 한 건 — Prettier 가 펼치면 건당 8줄이 되어 분산을 표처럼 훑을 수 없다.
// prettier-ignore
const SEEDS: WorkflowSeed[] = [
  { workflowId: 'wf_001', name: '월간 성적 리포트',      summary: '엑셀 성적표 → 통계 → 가정통신문 PDF', executionCount: 128, lastExecutedAt: '2026-09-08T09:15:00+09:00', createdAt: '2026-07-02T09:00:00+09:00', registeredAsAgent: false, running: false },
  { workflowId: 'wf_002', name: '주간 리서치 정리',      summary: '주제별 자료 수집 후 세 줄 요약',       executionCount: 46,  lastExecutedAt: '2026-09-07T14:10:00+09:00', createdAt: '2026-07-05T09:00:00+09:00', registeredAsAgent: false, running: false },
  { workflowId: 'wf_003', name: '계약서 검토 파이프라인', summary: '조항 추출 → 위험 표시 → 요약',        executionCount: 74,  lastExecutedAt: '2026-09-05T11:47:00+09:00', createdAt: '2026-08-21T09:00:00+09:00', registeredAsAgent: false, running: false },
  { workflowId: 'wf_004', name: '문서 자동 분류',        summary: '폴더를 훑어 주제별로 이동',            executionCount: 312, lastExecutedAt: '2026-09-04T19:12:00+09:00', createdAt: '2026-06-11T09:00:00+09:00', registeredAsAgent: false, running: false },
  { workflowId: 'wf_005', name: 'CS 답변 초안',          summary: '문의 분류 후 답변 초안 생성',          executionCount: 204, lastExecutedAt: '2026-09-03T17:38:00+09:00', createdAt: '2026-08-30T09:00:00+09:00', registeredAsAgent: false, running: false },
  { workflowId: 'wf_006', name: '영수증 정산 초안',      summary: '영수증 이미지에서 금액 추출',          executionCount: 0,   lastExecutedAt: null,                        createdAt: '2026-09-02T15:30:00+09:00', registeredAsAgent: false, running: false },
  { workflowId: 'wf_007', name: '코드 리뷰 자동화',      summary: '변경 diff 를 받아 문제를 지적',        executionCount: 91,  lastExecutedAt: '2026-09-01T09:12:00+09:00', createdAt: '2026-05-18T09:00:00+09:00', registeredAsAgent: false, running: false },
  { workflowId: 'wf_008', name: '회의록 정리',           summary: '녹취 텍스트를 항목별로 정리',          executionCount: 0,   lastExecutedAt: null,                        createdAt: '2026-08-30T18:00:00+09:00', registeredAsAgent: false, running: false },
  { workflowId: 'wf_009', name: '번역 용어 통일',        summary: '번역문에서 용어를 하나로 맞춘다',      executionCount: 37,  lastExecutedAt: '2026-08-29T10:05:00+09:00', createdAt: '2026-07-19T09:00:00+09:00', registeredAsAgent: false, running: false },
  { workflowId: 'wf_010', name: '이미지 태그 정리',      summary: '사진에 설명 태그를 붙여 묶는다',       executionCount: 12,  lastExecutedAt: '2026-08-27T16:40:00+09:00', createdAt: '2026-08-12T09:00:00+09:00', registeredAsAgent: false, running: false },
  // 삭제할 수 없는 두 건. 화면이 오류 코드별로 다른 문구를 내는지는 이 둘로만 확인된다.
  { workflowId: 'wf_011', name: '가정통신문 생성',       summary: '분석 결과를 학부모용 문장으로',        executionCount: 156, lastExecutedAt: '2026-08-25T13:22:00+09:00', createdAt: '2026-06-28T09:00:00+09:00', registeredAsAgent: true,  running: false },
  { workflowId: 'wf_012', name: '야간 배치 요약',        summary: '밤새 쌓인 로그를 아침에 요약',         executionCount: 88,  lastExecutedAt: '2026-08-24T07:00:00+09:00', createdAt: '2026-07-31T09:00:00+09:00', registeredAsAgent: false, running: true  },
]

/** 삭제가 실제로 항목을 없애므로 상수가 아니라 복사본이다. */
export const WORKFLOWS: WorkflowSeed[] = [...SEEDS]

// 눈으로 세면 어긋난다. 10건 이하로 줄면 무한 스크롤 검증이 조용히 사라진다.
if (WORKFLOWS.length !== 12) {
  throw new Error(`[mock] 워크플로우 픽스처가 ${WORKFLOWS.length}건이다. 12건이어야 한다.`)
}

/** 테스트가 삭제한 건을 되살린다. 길이만 잘라내는 방식으로는 원복되지 않는다. */
export function resetWorkflows() {
  WORKFLOWS.splice(0, WORKFLOWS.length, ...SEEDS)
}

/**
 * 내부 필드를 떼어내고 명세 5필드만 남긴다.
 *
 * 핸들러에서 인라인하지 않는다 — `registeredAsAgent` 처럼 응답에 없는 필드가 한 번이라도
 * 새면 화면이 그것으로 버튼을 감추기 시작하고, 백엔드를 붙이는 날 조용히 `undefined` 가 된다.
 */
export function toWorkflowSummary(workflow: WorkflowSeed): WorkflowSummary {
  return {
    workflowId: workflow.workflowId,
    name: workflow.name,
    summary: workflow.summary,
    executionCount: workflow.executionCount,
    lastExecutedAt: workflow.lastExecutedAt,
  }
}
