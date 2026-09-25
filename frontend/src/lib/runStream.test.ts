import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { ExecutionProgress } from '@/api/executions'
import { followRun } from '@/lib/runStream'

/**
 * jsdom 에는 EventSource 가 없다. 서버가 보내는 것과 끊기는 것을 손으로 흉내 낸다.
 *
 * 브라우저 규격대로 CLOSED 는 다시 붙지 않는 상태고, CONNECTING 은 되붙는 중이다.
 */
class FakeEventSource {
  static readonly CONNECTING = 0
  static readonly OPEN = 1
  static readonly CLOSED = 2

  static opened: FakeEventSource[] = []

  readyState = FakeEventSource.OPEN
  closed = false

  private readonly listeners = new Map<string, ((event: Event) => void)[]>()

  readonly url: string
  readonly init?: { withCredentials?: boolean }

  constructor(url: string, init?: { withCredentials?: boolean }) {
    this.url = url
    this.init = init
    FakeEventSource.opened.push(this)
  }

  addEventListener(type: string, listener: (event: Event) => void) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener])
  }

  close() {
    this.closed = true
    this.readyState = FakeEventSource.CLOSED
  }

  /** 붙은 것으로 친다. */
  open() {
    this.readyState = FakeEventSource.OPEN
    this.listeners.get('open')?.forEach((listener) => listener(new Event('open')))
  }

  /** 서버가 progress 이벤트를 보낸 것으로 친다. */
  emit(data: unknown) {
    const event = new MessageEvent('progress', {
      data: typeof data === 'string' ? data : JSON.stringify(data),
    })
    this.listeners.get('progress')?.forEach((listener) => listener(event))
  }

  /** 끊긴 것으로 친다. `permanent` 면 다시 붙지 않는다. */
  fail(permanent: boolean) {
    this.readyState = permanent ? FakeEventSource.CLOSED : FakeEventSource.CONNECTING
    this.listeners.get('error')?.forEach((listener) => listener(new Event('error')))
  }
}

const progress = (over: Partial<ExecutionProgress> = {}): ExecutionProgress => ({
  executionId: 'e-1',
  status: 'RUNNING',
  done: 1,
  total: 2,
  percent: 50,
  currentStepName: '가정통신문 작성',
  steps: [
    { name: '성적 분석', status: 'succeeded' },
    { name: '가정통신문 작성', status: 'running' },
  ],
  failure: null,
  ...over,
})

const latest = () => FakeEventSource.opened.at(-1)!

beforeEach(() => {
  FakeEventSource.opened = []
  vi.stubGlobal('EventSource', FakeEventSource)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('followRun', () => {
  it('세션 쿠키를 실어 실행의 스트림을 연다', () => {
    followRun('e-1', { onProgress: vi.fn(), onBroken: vi.fn() })

    expect(latest().url).toMatch(/\/runs\/e-1\/stream$/)
    expect(latest().init?.withCredentials).toBe(true)
  })

  it('받은 진행 상황을 그대로 넘긴다', () => {
    const onProgress = vi.fn()
    followRun('e-1', { onProgress, onBroken: vi.fn() })

    latest().emit(progress())

    expect(onProgress).toHaveBeenCalledWith(progress())
  })

  it('끝나면 스스로 닫는다 — 열어 두면 브라우저가 끝난 실행에 계속 다시 붙는다', () => {
    followRun('e-1', { onProgress: vi.fn(), onBroken: vi.fn() })

    latest().emit(progress({ status: 'SUCCEEDED', percent: 100, done: 2 }))

    expect(latest().closed).toBe(true)
  })

  it('실패도 끝난 것이라 닫는다', () => {
    const onProgress = vi.fn()
    followRun('e-1', { onProgress, onBroken: vi.fn() })

    latest().emit(
      progress({
        status: 'FAILED',
        failure: { stepName: '가정통신문 작성', errorCode: 'MODEL_API_ERROR', fault: 'supplier' },
      }),
    )

    expect(onProgress.mock.lastCall?.[0].failure.errorCode).toBe('MODEL_API_ERROR')
    expect(latest().closed).toBe(true)
  })

  it('깨진 줄이 와도 화면을 비우지 않는다', () => {
    const onProgress = vi.fn()
    followRun('e-1', { onProgress, onBroken: vi.fn() })

    latest().emit('{ 어쩌다 잘린 JSON')
    latest().emit({ hello: 'world' })

    expect(onProgress).not.toHaveBeenCalled()
  })

  it('되붙는 중이면 끊겼다고 알리지 않는다', () => {
    const onBroken = vi.fn()
    followRun('e-1', { onProgress: vi.fn(), onBroken })

    latest().fail(false)

    expect(onBroken).not.toHaveBeenCalled()
  })

  it('다시 붙을 수 없게 끊기면 알린다', () => {
    const onBroken = vi.fn()
    followRun('e-1', { onProgress: vi.fn(), onBroken })

    latest().fail(true)

    expect(onBroken).toHaveBeenCalledTimes(1)
  })

  // 서버에 닿지도 못하면 readyState 가 CLOSED 가 아니라 CONNECTING 이라, 상한이 없으면
  // 화면이 0% 인 채 아무 말도 없이 끝없이 다시 붙는다.
  it('끝내 붙지 못하면 되붙는 중이라도 포기한다', () => {
    const onBroken = vi.fn()
    followRun('e-1', { onProgress: vi.fn(), onBroken })

    latest().fail(false)
    latest().fail(false)
    expect(onBroken).not.toHaveBeenCalled()

    latest().fail(false)

    expect(onBroken).toHaveBeenCalledTimes(1)
    expect(latest().closed).toBe(true)
  })

  // 버퍼링하는 프록시를 만나면 붙기는 붙고 아무것도 오지 않는다. 오류가 안 나 기다리기만 하면
  // 화면이 0% 에 선다.
  it('붙고도 첫 소식이 없으면 포기한다', () => {
    vi.useFakeTimers()
    const onBroken = vi.fn()
    followRun('e-1', { onProgress: vi.fn(), onBroken })

    latest().open()
    vi.advanceTimersByTime(4_000)
    expect(onBroken).not.toHaveBeenCalled()

    vi.advanceTimersByTime(1_500)

    expect(onBroken).toHaveBeenCalledTimes(1)
    expect(latest().closed).toBe(true)
    vi.useRealTimers()
  })

  it('첫 소식이 오면 기다리기를 그만둔다', () => {
    vi.useFakeTimers()
    const onBroken = vi.fn()
    followRun('e-1', { onProgress: vi.fn(), onBroken })

    latest().open()
    latest().emit(progress())
    vi.advanceTimersByTime(30_000)

    expect(onBroken).not.toHaveBeenCalled()
    vi.useRealTimers()
  })

  it('그만 보겠다고 하면 닫고, 그 뒤에 끊겨도 알리지 않는다', () => {
    const onBroken = vi.fn()
    const stop = followRun('e-1', { onProgress: vi.fn(), onBroken })

    stop()
    latest().fail(true)

    expect(latest().closed).toBe(true)
    expect(onBroken).not.toHaveBeenCalled()
  })
})
