import {
  isFinished,
  openRunStream,
  type ExecutionProgress,
  type ExecutionStatus,
} from '@/api/executions'

export interface RunStreamHandlers {
  onProgress: (progress: ExecutionProgress) => void
  /** 다시 붙을 수 없게 끊겼을 때. 되붙는 중에는 부르지 않는다. */
  onBroken: () => void
}

/**
 * 붙지 못한 채 이만큼 되풀이하면 포기한다.
 *
 * `EventSource` 는 응답이 비-2xx 면 CLOSED 가 되지만, 서버에 닿지도 못하면 CONNECTING 으로
 * 끝없이 다시 붙는다 — 그대로 두면 화면이 0% 인 채 아무 말도 없이 멈춘다. mock 모드처럼
 * 스트림이 아예 없는 자리가 그렇다.
 */
const MAX_RETRIES = 3

const STATUSES: ExecutionStatus[] = ['QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED']

/**
 * 진행 상황 스트림을 따라간다. 그만 볼 때 부를 함수를 돌려준다.
 *
 * 끝난 실행에서 직접 닫는 것이 핵심이다. 서버가 종료 이벤트를 보내고 연결을 닫는데,
 * `EventSource` 는 닫히면 스스로 다시 붙는다 — 닫아 주지 않으면 끝난 실행에 계속
 * 재연결해 매번 종료 이벤트 하나를 받고 끊기기를 반복한다.
 */
export function followRun(executionId: string, handlers: RunStreamHandlers): () => void {
  const source = openRunStream(executionId)
  let closed = false
  let retries = 0

  const close = () => {
    if (closed) return
    closed = true
    source.close()
  }

  const giveUp = () => {
    close()
    handlers.onBroken()
  }

  source.addEventListener('open', () => {
    retries = 0
  })

  source.addEventListener('progress', (event) => {
    const progress = parseProgress((event as MessageEvent).data)
    if (!progress) return

    handlers.onProgress(progress)
    if (isFinished(progress.status)) close()
  })

  source.addEventListener('error', () => {
    if (closed) return

    // 없는 실행이나 권한 없는 실행은 200 이 아니라 되붙지 않고 여기서 멈춘다.
    if (source.readyState === EventSource.CLOSED) {
      giveUp()
      return
    }

    // 되붙는 중. 몇 번까지는 두고 보되 끝내 못 붙으면 포기한다.
    retries += 1
    if (retries >= MAX_RETRIES) giveUp()
  })

  return close
}

/** 서버가 보낸 것만 통과시킨다. 깨진 줄 하나가 화면을 비우게 두지 않는다. */
function parseProgress(raw: unknown): ExecutionProgress | null {
  if (typeof raw !== 'string') return null

  try {
    const parsed: unknown = JSON.parse(raw)
    return isProgress(parsed) ? parsed : null
  } catch {
    return null
  }
}

/**
 * 화면이 실제로 읽는 칸이 다 있는지 본다. 두엇만 보고 통과시키면 `steps` 가 `undefined` 인 채
 * 타입만 맞아 보여, 그것을 쓰기 시작하는 순간 터진다.
 */
function isProgress(value: unknown): value is ExecutionProgress {
  if (typeof value !== 'object' || value === null) return false
  const candidate = value as Partial<ExecutionProgress>

  return (
    typeof candidate.status === 'string' &&
    STATUSES.includes(candidate.status as ExecutionStatus) &&
    typeof candidate.percent === 'number' &&
    typeof candidate.done === 'number' &&
    typeof candidate.total === 'number' &&
    Array.isArray(candidate.steps)
  )
}
