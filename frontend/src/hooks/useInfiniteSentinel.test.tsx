import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useInfiniteSentinel } from './useInfiniteSentinel'

/** 붙은 observer 마다 콜백을 모아 두고, 테스트가 교차를 직접 쏜다. */
let observers: { callback: IntersectionObserverCallback; root: Element | null; alive: boolean }[]

class FakeObserver {
  private entry: (typeof observers)[number]
  constructor(callback: IntersectionObserverCallback, options?: IntersectionObserverInit) {
    this.entry = { callback, root: (options?.root as Element | null) ?? null, alive: true }
    observers.push(this.entry)
  }
  observe() {}
  disconnect() {
    this.entry.alive = false
  }
}

const intersect = () => {
  for (const o of observers.filter((o) => o.alive)) {
    o.callback([{ isIntersecting: true } as IntersectionObserverEntry], {} as IntersectionObserver)
  }
}

type Props = Parameters<typeof useInfiniteSentinel>[0]

function List(props: Props) {
  const ref = useInfiniteSentinel(props)
  return <div ref={ref} />
}

let container: HTMLDivElement
let root: Root

const render = (props: Props) => act(() => root.render(<List {...props} />))

const base = (over: Partial<Props> = {}): Props => ({
  hasNextPage: true,
  isFetchingNextPage: false,
  isFetchNextPageError: false,
  fetchNextPage: vi.fn(),
  getRoot: () => null,
  ...over,
})

beforeEach(() => {
  ;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true
  observers = []
  vi.stubGlobal('IntersectionObserver', FakeObserver)
  container = document.createElement('div')
  root = createRoot(container)
})

afterEach(() => {
  act(() => root.unmount())
  vi.unstubAllGlobals()
})

describe('useInfiniteSentinel', () => {
  it('sentinel 이 보이면 다음 장을 부른다', () => {
    const props = base()
    render(props)
    intersect()
    expect(props.fetchNextPage).toHaveBeenCalledTimes(1)
  })

  it('불러오는 중에는 또 부르지 않는다', () => {
    const props = base({ isFetchingNextPage: true })
    render(props)
    intersect()
    expect(props.fetchNextPage).not.toHaveBeenCalled()
  })

  it('다음 장이 없거나 직전 장이 실패했으면 observer 를 붙이지 않는다', () => {
    render(base({ hasNextPage: false }))
    render(base({ isFetchNextPageError: true }))
    expect(observers.filter((o) => o.alive)).toHaveLength(0)
  })

  it('한 장을 다 받으면 observer 를 다시 붙여 계속 보이는 sentinel 에서 이어 간다', () => {
    const props = base({ isFetchingNextPage: true })
    render(props)
    render({ ...props, isFetchingNextPage: false })
    expect(observers.filter((o) => o.alive)).toHaveLength(1)
    intersect()
    expect(props.fetchNextPage).toHaveBeenCalledTimes(1)
  })

  it('fetchNextPage 가 렌더마다 바뀌어도 다시 붙지 않고 최신 함수를 부른다', () => {
    render(base())
    const next = vi.fn()
    render(base({ fetchNextPage: next }))
    expect(observers).toHaveLength(1)
    intersect()
    expect(next).toHaveBeenCalledTimes(1)
  })

  it('resetKey 가 바뀌면 다시 붙는다', () => {
    render(base({ resetKey: 'agents' }))
    render(base({ resetKey: 'flows' }))
    expect(observers).toHaveLength(2)
    expect(observers.filter((o) => o.alive)).toHaveLength(1)
  })

  it('스크롤 상자를 root 로 쓴다', () => {
    const box = document.createElement('section')
    render(base({ getRoot: () => box }))
    expect(observers[0].root).toBe(box)
  })
})
