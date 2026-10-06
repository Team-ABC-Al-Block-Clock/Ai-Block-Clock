import type { ExecutionProgress } from '@/api/executions'

/** 게이지 옆 제목과 단계 수. */
export function runHeadline({
  missing,
  lost,
  failed,
  finished,
  progress,
  currentLabel,
  activeName,
  runnableCount,
  showStepCount = true,
}: {
  missing: boolean
  lost: boolean
  failed: boolean
  finished: boolean
  progress: ExecutionProgress | undefined
  /** 도는 단계의 화면 이름. 단계를 받기 전이면 없다. */
  currentLabel: string | undefined
  /** 진행을 받기 전에 차례로 짐작한 카드 이름. */
  activeName: string | undefined
  runnableCount: number
  /** 에이전트 내부 단계 수를 공개하지 않을 때는 숫자 안내를 숨긴다. */
  showStepCount?: boolean
}) {
  const stepTitle = missing
    ? '실행을 찾을 수 없습니다'
    : lost
      ? '진행 상황을 알 수 없습니다'
      : failed
        ? '실행이 중단되었습니다'
        : finished
          ? '실행 완료'
          : progress
            ? (currentLabel ??
              (progress.total > 0 && progress.done >= progress.total
                ? '결과를 정리하는 중'
                : '실행을 준비하는 중'))
            : activeName
              ? `${activeName} 중`
              : '워크플로우를 불러오는 중'
  const stepMeta =
    lost || !showStepCount
      ? ''
      : progress
        ? `${progress.done} / ${progress.total} 단계`
        : runnableCount
          ? // 서버처럼 작업 단계만 세고, 진행을 받기 전에는 끝난 단계가 없다.
            `0 / ${runnableCount} 단계`
          : ''
  return { stepTitle, stepMeta }
}
