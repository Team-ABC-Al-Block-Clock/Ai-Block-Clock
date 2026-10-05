import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useTypewriter } from './useTypewriter'

function Title({ text, delay }: { text: string; delay: number }) {
  const { textRef, caretRef } = useTypewriter(text, delay)
  return (
    <div>
      <span data-testid="text" ref={textRef}></span>
      <span data-testid="caret" ref={caretRef}></span>
    </div>
  )
}

let container: HTMLDivElement
let root: Root

const text = () => container.querySelector('[data-testid="text"]') as HTMLElement
const caret = () => container.querySelector('[data-testid="caret"]') as HTMLElement

beforeEach(() => {
  ;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true
  vi.useFakeTimers()
  container = document.createElement('div')
  document.body.appendChild(container)
  root = createRoot(container)
})

afterEach(() => {
  act(() => root.unmount())
  container.remove()
  vi.useRealTimers()
})

describe('useTypewriter', () => {
  it('지연 뒤에 한 글자씩 치고, 다 치면 커서를 감춘다', () => {
    act(() => root.render(<Title text="안녕하세요" delay={300} />))
    act(() => vi.advanceTimersByTime(299))
    expect(text().textContent).toBe('')

    act(() => vi.advanceTimersByTime(1 + 55 * 2))
    expect(text().textContent).toBe('안녕')

    act(() => vi.advanceTimersByTime(55 * 3))
    expect(text().textContent).toBe('안녕하세요')
    expect(caret().style.display).toBe('')

    act(() => vi.advanceTimersByTime(900))
    expect(caret().style.display).toBe('none')
  })

  it('치는 도중에 화면을 벗어나면 남은 타이머를 끊는다', () => {
    act(() => root.render(<Title text="안녕하세요" delay={0} />))
    act(() => vi.advanceTimersByTime(55))
    act(() => root.unmount())
    expect(vi.getTimerCount()).toBe(0)
    root = createRoot(container)
  })
})
