import type { WorkflowSummary } from '@/api/workflows'

/**
 * 에이전트로 등록하지 않고 내가 쓰는 워크플로우. 응답 4필드에 핸들러만 읽는 내부 필드
 * 셋을 더한다. 12건이다 — 기본 `size` 가 10 이라 그보다 적으면 무한 스크롤을 볼 수 없다.
 */
interface WorkflowSeed extends WorkflowSummary {
  /** 응답에 없다 — `sort=LATEST` 를 계산할 근거 */
  createdAt: string
  /** 응답에 없다 — 이미 에이전트로 등록된 것은 삭제할 수 없다(WORKFLOW_009). */
  registeredAsAgent: boolean
  /** 응답에 없다 — 실행이 진행 중이면 삭제할 수 없다(WORKFLOW_010). */
  running: boolean
}

/**
 * 원본. `WORKFLOWS` 는 삭제로 바뀌므로 되돌릴 기준이 필요하다.
 * 실행 이력이 없는 건과 설명을 적지 않은 건을 섞는다 — 없으면 정렬도, 화면의
 * 'Invalid Date' 도, 설명 빈자리도 안 드러난다.
 */
// 한 줄에 한 건 — 펼치면 건당 8줄이 되어 분산을 표처럼 훑을 수 없다.
// prettier-ignore
const SEEDS: WorkflowSeed[] = [
  { workflowId: 'wf_001', name: '월간 성적 리포트',      description: '엑셀 성적표 → 통계 → 가정통신문 PDF', lastExecutedAt: '2026-09-08T09:15:00+09:00', createdAt: '2026-07-02T09:00:00+09:00', registeredAsAgent: false, running: false },
  { workflowId: 'wf_002', name: '주간 리서치 정리',      description: '주제별 자료 수집 후 세 줄 요약',       lastExecutedAt: '2026-09-07T14:10:00+09:00', createdAt: '2026-07-05T09:00:00+09:00', registeredAsAgent: false, running: false },
  { workflowId: 'wf_003', name: '계약서 검토 파이프라인', description: '조항 추출 → 위험 표시 → 요약',        lastExecutedAt: '2026-09-05T11:47:00+09:00', createdAt: '2026-08-21T09:00:00+09:00', registeredAsAgent: false, running: false },
  { workflowId: 'wf_004', name: '문서 자동 분류',        description: '올린 문서를 주제별로 나눔',            lastExecutedAt: '2026-09-04T19:12:00+09:00', createdAt: '2026-06-11T09:00:00+09:00', registeredAsAgent: false, running: false },
  { workflowId: 'wf_005', name: 'CS 답변 초안',          description: '문의 분류 후 답변 초안 생성',          lastExecutedAt: '2026-09-03T17:38:00+09:00', createdAt: '2026-08-30T09:00:00+09:00', registeredAsAgent: false, running: false },
  { workflowId: 'wf_006', name: '영수증 정산 초안',      description: '영수증 이미지에서 금액 추출',          lastExecutedAt: null,                        createdAt: '2026-09-02T15:30:00+09:00', registeredAsAgent: false, running: false },
  { workflowId: 'wf_007', name: '코드 리뷰 자동화',      description: '변경 diff 를 받아 문제를 지적',        lastExecutedAt: '2026-09-01T09:12:00+09:00', createdAt: '2026-05-18T09:00:00+09:00', registeredAsAgent: false, running: false },
  { workflowId: 'wf_008', name: '회의록 정리',           description: null,          lastExecutedAt: null,                        createdAt: '2026-08-30T18:00:00+09:00', registeredAsAgent: false, running: false },
  { workflowId: 'wf_009', name: '번역 용어 통일',        description: '번역문에서 용어를 하나로 맞춘다',      lastExecutedAt: '2026-08-29T10:05:00+09:00', createdAt: '2026-07-19T09:00:00+09:00', registeredAsAgent: false, running: false },
  { workflowId: 'wf_010', name: '이미지 태그 정리',      description: '사진에 설명 태그를 붙여 묶는다',       lastExecutedAt: '2026-08-27T16:40:00+09:00', createdAt: '2026-08-12T09:00:00+09:00', registeredAsAgent: false, running: false },
  // 삭제할 수 없는 두 건. 화면이 오류 코드별로 다른 문구를 내는지는 이 둘로만 확인된다.
  { workflowId: 'wf_011', name: '가정통신문 생성',       description: '분석 결과를 학부모용 문장으로',        lastExecutedAt: '2026-08-25T13:22:00+09:00', createdAt: '2026-06-28T09:00:00+09:00', registeredAsAgent: true,  running: false },
  { workflowId: 'wf_012', name: '로그 요약',              description: '올린 로그에서 오류만 추려 요약',         lastExecutedAt: '2026-08-24T07:00:00+09:00', createdAt: '2026-07-31T09:00:00+09:00', registeredAsAgent: false, running: true  },
]

/** 삭제가 실제로 항목을 없애므로 상수가 아니라 복사본이다. */
export const WORKFLOWS: WorkflowSeed[] = [...SEEDS]

// 10건 이하로 줄면 무한 스크롤 검증이 조용히 사라진다.
if (WORKFLOWS.length !== 12) {
  throw new Error(`[mock] 워크플로우 픽스처가 ${WORKFLOWS.length}건이다. 12건이어야 한다.`)
}

/** 길이만 잘라내는 방식으로는 원복되지 않는다. */
export function resetWorkflows() {
  WORKFLOWS.splice(0, WORKFLOWS.length, ...SEEDS)
}

/**
 * 내부 필드를 떼어낸다. 응답에 없는 필드가 한 번이라도 새면 화면이 그것으로 버튼을
 * 감추기 시작하고, 백엔드를 붙이는 날 `undefined` 가 된다.
 */
export function toWorkflowSummary(workflow: WorkflowSeed): WorkflowSummary {
  return {
    workflowId: workflow.workflowId,
    name: workflow.name,
    description: workflow.description,
    lastExecutedAt: workflow.lastExecutedAt,
  }
}
