import type { WorkflowSummary } from '@/api/workflows'

/**
 * 내 워크플로우 픽스처.
 *
 * 캡슐화 가능·실행 전용을 섞는다 — 화면이 둘을 구분해 표시해야 한다.
 * 직전 실행이 실패한 건도 넣었다. 부분 재실행 버튼이 뜨는 조건이다.
 */
// prettier-ignore
export const WORKFLOWS: WorkflowSummary[] = [
  { workflowId: 'wf_001', name: '월간 성적 리포트', description: '엑셀 성적표 → 통계 → 가정통신문 PDF', kind: 'RUN_ONLY', nodeCount: 6, estimatedCost: 1192, lastExecutedAt: '2026-09-08T09:15:00+09:00', lastExecutionStatus: 'SUCCEEDED', updatedAt: '2026-09-08T09:15:00+09:00' },
  { workflowId: 'wf_002', name: '주간 리서치 정리', description: '주제별 자료 수집 후 세 줄 요약', kind: 'RUN_ONLY', nodeCount: 4, estimatedCost: 1038, lastExecutedAt: '2026-09-07T14:10:00+09:00', lastExecutionStatus: 'FAILED', updatedAt: '2026-09-07T14:10:00+09:00' },
  { workflowId: 'wf_003', name: '계약서 검토 파이프라인', description: '조항 추출 → 위험 표시 → 요약', kind: 'ENCAPSULABLE', nodeCount: 5, estimatedCost: 1560, lastExecutedAt: '2026-09-05T11:47:00+09:00', lastExecutionStatus: 'SUCCEEDED', updatedAt: '2026-09-06T10:02:00+09:00' },
  { workflowId: 'wf_004', name: '문서 자동 분류', description: '폴더를 훑어 주제별로 이동', kind: 'ENCAPSULABLE', nodeCount: 3, estimatedCost: 390, lastExecutedAt: '2026-09-04T19:12:00+09:00', lastExecutionStatus: 'SUCCEEDED', updatedAt: '2026-09-04T19:12:00+09:00' },
  { workflowId: 'wf_005', name: 'CS 답변 초안', description: '문의 분류 후 답변 초안 생성', kind: 'RUN_ONLY', nodeCount: 4, estimatedCost: 1014, lastExecutedAt: '2026-09-03T17:38:00+09:00', lastExecutionStatus: 'SUCCEEDED', updatedAt: '2026-09-03T17:38:00+09:00' },
  { workflowId: 'wf_006', name: '영수증 정산 초안', description: '영수증 이미지에서 금액 추출', kind: 'ENCAPSULABLE', nodeCount: 4, estimatedCost: 418, lastExecutedAt: null, lastExecutionStatus: null, updatedAt: '2026-09-02T15:30:00+09:00' },
  { workflowId: 'wf_007', name: '코드 리뷰 자동화', description: '변경 diff 를 받아 문제를 지적', kind: 'ENCAPSULABLE', nodeCount: 5, estimatedCost: 858, lastExecutedAt: '2026-09-01T09:12:00+09:00', lastExecutionStatus: 'SUCCEEDED', updatedAt: '2026-09-01T09:12:00+09:00' },
  { workflowId: 'wf_008', name: '회의록 정리', description: '녹취 텍스트를 항목별로 정리', kind: 'RUN_ONLY', nodeCount: 3, estimatedCost: 624, lastExecutedAt: null, lastExecutionStatus: null, updatedAt: '2026-08-30T18:00:00+09:00' },
]
