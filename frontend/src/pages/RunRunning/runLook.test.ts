import { describe, expect, it } from 'vitest'

import { cardLook, labelOf, logRow } from './runLook'

const base = { status: undefined, index: 1, known: true, active: 1, lost: false, finished: false }

describe('labelOf', () => {
  it('모르는 상태는 대기로 둔다', () => {
    expect(labelOf('skipped')).toBe('건너뜀')
    expect(labelOf('paused' as never)).toBe('대기')
  })
})

describe('cardLook', () => {
  it('단계를 받았으면 상태를 그대로 쓴다', () => {
    expect(cardLook({ ...base, status: 'failed' }).stateLabel).toBe('실패')
    expect(cardLook({ ...base, status: 'running', index: 0 }).background).toBe(
      'rgba(255,255,255,.17)',
    )
  })

  it('못 받았으면 차례로 짐작한다 — 앞은 완료, 지금은 진행 중, 뒤는 대기', () => {
    expect(cardLook({ ...base, index: 0 }).stateLabel).toBe('완료')
    expect(cardLook(base).stateLabel).toBe('진행 중')
    expect(cardLook({ ...base, index: 2 }).stateLabel).toBe('대기')
  })

  it('끝났으면 짐작한 카드도 모두 완료다', () => {
    expect(cardLook({ ...base, index: 2, finished: true }).stateLabel).toBe('완료')
  })

  it('소식이 끊겼거나 단계가 아닌 카드는 아무것도 켜지 않는다', () => {
    expect(cardLook({ ...base, lost: true }).stateLabel).toBe('대기')
    expect(cardLook({ ...base, index: undefined, known: false }).stateLabel).toBe('')
  })
})

describe('logRow', () => {
  it('마지막 줄에만 커서를 붙인다', () => {
    expect(logRow('요약', true).caret).toMatchObject({
      animation: 'caret 1s steps(1,end) infinite',
    })
    expect(logRow('요약', false).caret).toEqual({ display: 'none' })
  })
})
