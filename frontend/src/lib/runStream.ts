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

/** 붙지 못한 채 이만큼 되풀이하면 포기한다. 상한이 없으면 0% 인 채 끝없이 다시 붙는다. */
const MAX_RETRIES = 3

/**
 * 붙은 뒤 이 시간 안에 첫 소식이 없으면 포기한다. 서버는 붙는 즉시 지금 상태를 한 번 보낸다 —
 * 그런데도 조용하면 중간에서 버퍼에 쌓고 있다는 뜻이다(프록시 설정이 빠진 경우). 그때는
 * 오류조차 나지 않아 기다리기만 하면 화면이 0% 에 선다.
 */
const FIRST_EVENT_MS = 5_000

const STATUSES: ExecutionStatus[] = ['QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED']

/**
 * 진행 상황 스트림을 따라간다. 그만 볼 때 부를 함수를 돌려준다.
 * 끝난 실행에서 직접 닫는다 — `EventSource` 는 닫히면 스스로 다시 붙는다.
 */
export function followRun(executionId: string, handlers: RunStreamHandlers): () => void {
  const source = openRunStream(executionId)
  let closed = false
  let retries = 0
  let silence: ReturnType<typeof setTimeout> | undefined

  const close = () => {
    if (closed) return
    closed = true
    clearTimeout(silence)
    source.close()
  }

  const giveUp = () => {
    close()
    handlers.onBroken()
  }

  source.addEventListener('open', () => {
    retries = 0
    clearTimeout(silence)
    silence = setTimeout(giveUp, FIRST_EVENT_MS)
  })

  source.addEventListener('progress', (event) => {
    const progress = parseProgress((event as MessageEvent).data)
    if (!progress) return

    clearTimeout(silence)
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

/** 화면이 읽는 칸이 다 있는지 본다. 두엇만 보면 `steps` 가 `undefined` 인 채 타입만 맞는다. */
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
