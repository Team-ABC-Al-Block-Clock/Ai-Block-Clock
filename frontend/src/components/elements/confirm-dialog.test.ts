import { beforeEach, describe, expect, it } from 'vitest'

import '@/components/elements/confirm-dialog'

/** 되돌릴 수 없는 등록을 막는 마지막 관문이라, 열림·닫힘과 두 이벤트만 확인한다. */
describe('confirm-dialog', () => {
  const mount = (attrs: Record<string, string> = {}) => {
    const el = document.createElement('confirm-dialog')
    for (const [name, value] of Object.entries(attrs)) el.setAttribute(name, value)
    document.body.appendChild(el)
    return el
  }

  const buttons = (el: HTMLElement) => [
    ...(el.shadowRoot?.querySelectorAll<HTMLButtonElement>('[data-btn]') ?? []),
  ]

  /** 포커스는 마이크로태스크 뒤에 잡는다. */
  const settled = () => Promise.resolve()

  beforeEach(() => {
    document.body.innerHTML = ''
  })

  it('open 이 없으면 그리지 않는다', () => {
    const el = mount({ heading: '등록하시겠습니까?' })

    expect(el.style.display).toBe('none')
    expect(el.shadowRoot?.textContent).toBe('')
    expect(el.getAttribute('role')).toBeNull()
  })

  it('열리면 대화상자로 알리고 넘긴 문구를 그린다', () => {
    const el = mount({
      open: '',
      heading: '마켓에 등록하시겠습니까?',
      'body-text': '수정도 삭제도 할 수 없습니다.',
      'note-text': '문서 정리',
    })

    expect(el.style.display).toBe('flex')
    expect(el.getAttribute('role')).toBe('dialog')
    expect(el.getAttribute('aria-modal')).toBe('true')
    expect(el.getAttribute('aria-label')).toBe('마켓에 등록하시겠습니까?')
    expect(el.shadowRoot?.textContent).toContain('문서 정리')
    expect(el.shadowRoot?.textContent).toContain('수정도 삭제도 할 수 없습니다.')
  })

  it('이름에 든 태그를 글자로 그린다', () => {
    const el = mount({ open: '', 'note-text': '<img src=x onerror="globalThis.__pwned=1">' })

    expect(el.shadowRoot?.querySelector('img')).toBeNull()
    expect(el.shadowRoot?.textContent).toContain('<img src=x')
  })

  it('버튼이 confirm · cancel 을 던진다', () => {
    const el = mount({ open: '', 'confirm-label': '등록하기' })
    const fired: string[] = []
    el.addEventListener('confirm', () => fired.push('confirm'))
    el.addEventListener('cancel', () => fired.push('cancel'))

    const [cancel, confirm] = buttons(el)
    cancel.click()
    confirm.click()

    expect(fired).toEqual(['cancel', 'confirm'])
  })

  it('Escape 와 바깥 클릭은 cancel 이다', () => {
    const el = mount({ open: '' })
    const fired: string[] = []
    el.addEventListener('cancel', () => fired.push('cancel'))

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    el.shadowRoot?.querySelector<HTMLElement>('[data-veil]')?.click()

    expect(fired).toEqual(['cancel', 'cancel'])
  })

  it('열린 뒤 문구가 바뀌어도 포커스를 잃지 않는다', async () => {
    // React 는 open 과 note-text 를 따로 반영한다 — 두 번째 반영에서 다시 그리며 포커스가 날아갔었다.
    const el = mount({ open: '' })
    await settled()
    expect(el.shadowRoot?.activeElement).toBe(buttons(el)[0])

    el.setAttribute('note-text', '문서 정리')
    await settled()

    expect(el.shadowRoot?.activeElement).toBe(buttons(el)[0])
  })

  it('먼저 포커스를 뺏겨도 다시 잡는다', async () => {
    // React 가 커밋 끝에 직전 포커스를 되돌린다 — 그 뒤에 잡지 않으면 대화상자가 포커스를 못 갖는다.
    const opener = document.createElement('button')
    document.body.appendChild(opener)

    const el = mount({ open: '' })
    opener.focus()
    await settled()

    expect(el.shadowRoot?.activeElement).toBe(buttons(el)[0])
  })

  it('닫으면 열기 전 포커스로 돌려준다', async () => {
    const opener = document.createElement('button')
    document.body.appendChild(opener)
    opener.focus()

    const el = mount({ open: '' })
    await settled()
    expect(el.shadowRoot?.activeElement).toBe(buttons(el)[0])

    el.removeAttribute('open')
    await settled()
    expect(document.activeElement).toBe(opener)
  })
})
