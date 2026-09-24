import { isFinished, openRunStream, type ExecutionProgress } from '@/api/executions'

export interface RunStreamHandlers {
  onProgress: (progress: ExecutionProgress) => void
  /** 다시 붙을 수 없게 끊겼을 때. 되붙는 중에는 부르지 않는다. */
  onBroken: () => void
}

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

  const close = () => {
    if (closed) return
    closed = true
    source.close()
  }

  source.addEventListener('progress', (event) => {
    const progress = parseProgress((event as MessageEvent).data)
    if (!progress) return

    handlers.onProgress(progress)
    if (isFinished(progress.status)) close()
  })

  // 스트림 도중 끊기면 브라우저가 알아서 다시 붙는다. 상태가 CLOSED 일 때만 끝난 것이다 —
  // 없는 실행이나 권한 없는 실행은 200 이 아니라 되붙지 않고 여기서 멈춘다.
  source.addEventListener('error', () => {
    if (closed || source.readyState !== EventSource.CLOSED) return
    closed = true
    handlers.onBroken()
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

function isProgress(value: unknown): value is ExecutionProgress {
  if (typeof value !== 'object' || value === null) return false
  const candidate = value as Partial<ExecutionProgress>

  return typeof candidate.status === 'string' && typeof candidate.percent === 'number'
}
