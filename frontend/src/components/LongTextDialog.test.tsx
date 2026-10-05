import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import LongTextDialog from './LongTextDialog'

let container: HTMLDivElement
let root: Root
let opener: HTMLButtonElement
const onClose = vi.fn()

const render = (open: boolean) =>
  act(() =>
    root.render(
      open ? (
        <LongTextDialog title="프롬프트" value="요약" onChange={() => {}} onClose={onClose} />
      ) : null,
    ),
  )

const press = (key: string, shiftKey = false) =>
  act(() => {
    document.activeElement?.dispatchEvent(
      new KeyboardEvent('keydown', { key, shiftKey, bubbles: true, cancelable: true }),
    )
  })

beforeEach(() => {
  ;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true
  opener = document.createElement('button')
  document.body.appendChild(opener)
  opener.focus()
  container = document.createElement('div')
  document.body.appendChild(container)
  root = createRoot(container)
  onClose.mockClear()
})

afterEach(() => {
  act(() => root.unmount())
  container.remove()
  opener.remove()
})

describe('LongTextDialog', () => {
  it('열리면 입력 칸 끝에 커서를 둔다', () => {
    render(true)
    const area = document.querySelector('textarea')!
    expect(document.activeElement).toBe(area)
    expect(area.selectionStart).toBe(2)
  })

  it('껍데기(헤더 · Studio, zIndex 40)보다 위에 창 전체로 뜬다', () => {
    render(true)
    const layer = document
      .querySelector<HTMLElement>('[role="dialog"]')!
      .closest<HTMLElement>('.fixed')!
    expect(layer.parentElement).toBe(document.body)
    expect(Number(layer.style.zIndex)).toBeGreaterThan(40)
  })

  it('Tab 은 입력 칸과 완료 사이에서만 돈다', () => {
    render(true)
    const done = [...document.querySelectorAll('button')].find((b) => b.textContent === '완료')
    press('Tab')
    expect(document.activeElement).toBe(done)
    press('Tab')
    expect(document.activeElement).toBe(document.querySelector('textarea'))
    press('Tab', true)
    expect(document.activeElement).toBe(done)
  })

  it('Esc 로 닫고, 닫히면 연 버튼으로 포커스를 돌려준다', () => {
    render(true)
    press('Escape')
    expect(onClose).toHaveBeenCalledOnce()
    render(false)
    expect(document.activeElement).toBe(opener)
  })
})
