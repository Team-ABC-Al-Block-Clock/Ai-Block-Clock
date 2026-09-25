import { useEffect, useState } from 'react'

import { getExecutionProgress, isFinished, type ExecutionProgress } from '@/api/executions'
import { ApiError } from '@/api/errors'
import { followRun } from '@/lib/runStream'

/** 스트림이 없을 때 물어보는 간격. 명세가 정한 값이다. */
const POLL_MS = 2_000
/** 이만큼 잇따라 실패하면 화면에 알린다. 한 번 걸렀다고 알리면 잠깐 끊긴 것에도 놀란다. */
const MAX_MISSES = 3

/**
 * 마지막으로 받아 둔 진행. 등록 화면에 다녀오면 이 훅이 다시 마운트되는데, 빈손으로 시작하면
 * 첫 응답이 닿기 전까지 끝난 실행이 다시 도는 것처럼 그려진다. 한 건만 들고 있으면 된다.
 */
let last: { executionId: string; progress: ExecutionProgress } | undefined

const lastFor = (executionId?: string) =>
  executionId && last?.executionId === executionId ? last.progress : undefined

interface Followed {
  executionId?: string
  progress?: ExecutionProgress
  /** 잇따라 닿지 못하는 중. 계속 물어보므로 서버가 돌아오면 저절로 풀린다 */
  broken: boolean
  /** 서버가 모르는 실행이다. 다시 물어도 없으므로 그만둔다 */
  missing: boolean
}

export interface RunProgressState {
  progress?: ExecutionProgress
  broken: boolean
  missing: boolean
}

/**
 * 실행 하나를 따라간다. 스트림으로 받다가 붙지 못하면 2초 조회로 내려간다 —
 * mock 은 `EventSource` 를 가로채지 못하고, 운영도 SSE 프록시가 빠지면 닿지 않는다.
 *
 * 따라가는 실행 번호를 상태에 함께 들고 렌더할 때 견준다. 이펙트에서 비우면 실행을
 * 갈아타는 사이에 앞 실행의 진행률이 한 프레임 비친다.
 */
export function useRunProgress(executionId?: string): RunProgressState {
  const [followed, setFollowed] = useState<Followed>({
    executionId,
    progress: lastFor(executionId),
    broken: false,
    missing: false,
  })

  useEffect(() => {
    if (!executionId) return

    let gone = false
    let timer: ReturnType<typeof setTimeout> | undefined

    const remember = (next: Partial<Followed>) => {
      // 떠난 뒤에 닿은 응답도 들고 있는다 — 돌아왔을 때 처음부터 그리지 않게.
      if (next.progress) last = { executionId, progress: next.progress }
      if (!gone) setFollowed((prev) => ({ ...prev, executionId, ...next }))
    }

    let misses = 0

    /** 앞 요청이 끝난 뒤에 다음을 예약한다 — 간격으로 돌리면 느릴 때 밀린다. */
    const poll = () => {
      getExecutionProgress(executionId)
        .then((progress) => {
          misses = 0
          remember({ progress, broken: false, missing: false })
          if (!gone && !isFinished(progress.status)) timer = setTimeout(poll, POLL_MS)
        })
        .catch((error: unknown) => {
          // 서버가 모르는 실행만 그만둔다. 프록시가 낸 404 와 섞이지 않게 코드까지 본다.
          const unknown =
            error instanceof ApiError && error.status === 404 && error.code === 'EXECUTION_001'
          if (unknown) return remember({ missing: true, broken: true })

          // 그 밖에는 끝날 때까지 계속 물어본다 — 포기하면 서버가 돌아와도 화면이 굳는다.
          misses += 1
          if (misses >= MAX_MISSES) remember({ broken: true })
          if (!gone) timer = setTimeout(poll, POLL_MS)
        })
    }

    const stop = followRun(executionId, {
      onProgress: (progress) => remember({ progress, broken: false, missing: false }),
      onBroken: poll,
    })

    return () => {
      gone = true
      clearTimeout(timer)
      stop()
    }
  }, [executionId])

  if (followed.executionId !== executionId) {
    return { progress: lastFor(executionId), broken: false, missing: false }
  }

  return { progress: followed.progress, broken: followed.broken, missing: followed.missing }
}
