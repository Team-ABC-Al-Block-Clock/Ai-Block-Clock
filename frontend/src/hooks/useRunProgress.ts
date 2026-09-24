import { useEffect, useState } from 'react'

import { getRunProgress, type ExecutionProgress } from '@/api/executions'
import { followRun } from '@/lib/runStream'

interface Followed {
  executionId?: string
  progress?: ExecutionProgress
  /** 스트림이 끊겨 더 따라갈 수 없다. 마지막으로 받은 값은 그대로 둔다. */
  broken: boolean
}

export interface RunProgressState {
  progress?: ExecutionProgress
  broken: boolean
}

/**
 * 실행 하나를 따라간다. 붙는 즉시 지금 상태가 한 번 오고, 그 뒤로는 달라졌을 때만 온다.
 *
 * 스트림이 끊기면 마지막으로 한 번 조회해 본다 — 그 사이에 끝났을 수 있고, 그러면
 * 화면이 도는 중인 채로 멈춰 있는 것보다 낫다.
 *
 * 따라가는 실행 번호를 상태에 함께 들고 렌더할 때 견준다. 이펙트에서 비우면
 * 실행을 갈아타는 사이에 앞 실행의 진행률이 한 프레임 비친다.
 */
export function useRunProgress(executionId?: string): RunProgressState {
  const [followed, setFollowed] = useState<Followed>({ executionId, broken: false })

  useEffect(() => {
    if (!executionId) return

    let gone = false

    const remember = (next: Partial<Followed>) => {
      if (!gone) setFollowed((prev) => ({ ...prev, executionId, ...next }))
    }

    const stop = followRun(executionId, {
      onProgress: (progress) => remember({ progress }),
      onBroken: () => {
        getRunProgress(executionId)
          .then((progress) => remember({ progress }))
          .catch(() => {})
          .finally(() => remember({ broken: true }))
      },
    })

    return () => {
      gone = true
      stop()
    }
  }, [executionId])

  if (followed.executionId !== executionId) {
    return { progress: undefined, broken: false }
  }

  return { progress: followed.progress, broken: followed.broken }
}
