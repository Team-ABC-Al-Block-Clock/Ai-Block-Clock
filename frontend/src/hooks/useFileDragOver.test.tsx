import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { useFileDragOver } from './useFileDragOver'

function Probe() {
  return <span>{String(useFileDragOver())}</span>
}

let container: HTMLDivElement
let root: Root

const fire = (type: string, types: string[] = ['Files']) => {
  const e = new Event(type, { cancelable: true })
  Object.defineProperty(e, 'dataTransfer', { value: { types } })
  act(() => void window.dispatchEvent(e))
  return e
}
const shown = () => container.textContent

beforeEach(() => {
  ;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true
  container = document.createElement('div')
  document.body.appendChild(container)
  root = createRoot(container)
  act(() => root.render(<Probe />))
})

afterEach(() => {
  act(() => root.unmount())
  container.remove()
})

describe('useFileDragOver', () => {
  it('파일이 아닌 것을 끌면 반응하지 않는다', () => {
    fire('dragenter', ['text/plain'])
    expect(shown()).toBe('false')
    expect(fire('dragover').defaultPrevented).toBe(false)
  })

  it('자식 요소를 지나는 enter·leave 짝에는 꺼지지 않고, 마지막 leave 에 꺼진다', () => {
    fire('dragenter')
    fire('dragenter')
    fire('dragleave')
    expect(shown()).toBe('true')
    expect(fire('dragover').defaultPrevented).toBe(true)
    fire('dragleave')
    expect(shown()).toBe('false')
  })

  it('놓으면 꺼지고 브라우저가 파일을 열지 않게 막는다', () => {
    fire('dragenter')
    const drop = fire('drop')
    expect(drop.defaultPrevented).toBe(true)
    expect(shown()).toBe('false')
  })
})
