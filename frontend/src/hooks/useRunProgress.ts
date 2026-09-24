import { useEffect, useState } from 'react'

import { getExecutionProgress, isFinished, type ExecutionProgress } from '@/api/executions'
import { followRun } from '@/lib/runStream'

/** 스트림이 없을 때 물어보는 간격. 명세가 정한 값이다. */
const POLL_MS = 2_000

interface Followed {
  executionId?: string
  progress?: ExecutionProgress
  /** 스트림이 끊겨 폴링으로 따라가는 중 */
  polling: boolean
  /** 폴링마저 닿지 못한다. 마지막으로 받은 값은 그대로 둔다 */
  broken: boolean
}

export interface RunProgressState {
  progress?: ExecutionProgress
  polling: boolean
  broken: boolean
}

/**
 * 실행 하나를 따라간다. 스트림으로 받다가, 붙지 못하면 2초 간격 조회로 내려간다.
 *
 * 내려갈 자리가 필요한 이유는 스트림이 없는 환경이 실제로 있기 때문이다 — mock 은 `EventSource`
 * 를 가로채지 못하고, 운영도 SSE 전용 프록시 설정이 빠지면 닿지 않는다. 그때 화면이 0% 인 채
 * 멈추는 것보다 느려도 도는 편이 낫다.
 *
 * 따라가는 실행 번호를 상태에 함께 들고 렌더할 때 견준다. 이펙트에서 비우면 실행을 갈아타는
 * 사이에 앞 실행의 진행률이 한 프레임 비친다.
 */
export function useRunProgress(executionId?: string): RunProgressState {
  const [followed, setFollowed] = useState<Followed>({
    executionId,
    polling: false,
    broken: false,
  })

  useEffect(() => {
    if (!executionId) return

    let gone = false
    let timer: ReturnType<typeof setTimeout> | undefined

    const remember = (next: Partial<Followed>) => {
      if (!gone) setFollowed((prev) => ({ ...prev, executionId, ...next }))
    }

    /** 끝날 때까지 되풀이한다. 앞 요청이 끝난 뒤에 다음을 예약해 밀리지 않게 한다. */
    const poll = () => {
      getExecutionProgress(executionId)
        .then((progress) => {
          remember({ progress, broken: false })
          if (!gone && !isFinished(progress.status)) timer = setTimeout(poll, POLL_MS)
        })
        .catch(() => remember({ broken: true }))
    }

    const stop = followRun(executionId, {
      onProgress: (progress) => remember({ progress, polling: false, broken: false }),
      onBroken: () => {
        remember({ polling: true })
        poll()
      },
    })

    return () => {
      gone = true
      clearTimeout(timer)
      stop()
    }
  }, [executionId])

  if (followed.executionId !== executionId) {
    return { progress: undefined, polling: false, broken: false }
  }

  return { progress: followed.progress, polling: followed.polling, broken: followed.broken }
}
