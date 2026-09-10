import { describe, expect, it } from 'vitest'

import '@/components/empty-state'

/**
 * 빈 상태 카드는 화면이 넘긴 **검색어**를 제목에 그린다(Search·MyAgents).
 * 그 값을 innerHTML 로 넣으면 `?keyword=` 에 태그를 실은 링크 하나로 스크립트가 붙는다.
 */
describe('empty-state', () => {
  const mount = (attrs: Record<string, string>) => {
    const el = document.createElement('empty-state')
    for (const [name, value] of Object.entries(attrs)) el.setAttribute(name, value)
    document.body.appendChild(el)
    return el
  }

  it('제목과 본문의 태그를 글자로 그린다', () => {
    const el = mount({
      heading: '‘<img src=x onerror="globalThis.__pwned=1">’ 에 맞는 결과가 없습니다',
      body: '<script>globalThis.__pwned=1</script>',
    })

    expect(el.shadowRoot?.querySelector('img')).toBeNull()
    expect(el.shadowRoot?.querySelector('script')).toBeNull()
    // 지우는 것이 아니라 글자로 보여야 한다 — 사용자가 무엇을 검색했는지 알아야 한다.
    expect(el.shadowRoot?.textContent).toContain('<img src=x')
  })

  it('내부 경로만 링크로 만든다', () => {
    const el = mount({
      heading: '없음',
      actions: JSON.stringify([
        { label: '마켓', href: '/search', primary: true },
        { label: '수상한 것', href: 'javascript:globalThis.__pwned=1' },
      ]),
    })

    const anchors = [...(el.shadowRoot?.querySelectorAll('a') ?? [])]
    expect(anchors.map((a) => a.getAttribute('href'))).toEqual(['/search'])
    // 링크가 안 되더라도 라벨은 남는다
    expect(el.shadowRoot?.textContent).toContain('수상한 것')
  })
})
