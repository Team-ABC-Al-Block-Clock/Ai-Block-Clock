import { BaseElement, define } from './base'
import type { EmptyAction } from './types'

/**
 * 목록이 비었을 때 그 자리에 놓는 카드.
 *
 *   <empty-state heading="…" body="…"
 *     actions='[{"label":"직접 만들어보기","href":"/","primary":true}]'></empty-state>
 *
 * 호스트 자신이 카드다 — 배치와 등장 애니메이션만 쓰는 쪽에서 style 로 준다.
 * 버튼은 href 가 있으면 링크, 없으면 event 이름으로 이벤트를 올린다.
 */
export class EmptyState extends BaseElement {
  static observedAttributes = ['heading', 'body', 'actions', 'body-width']

  protected override usesTheme = true

  /** light DOM 에 직접 쓰면 React 리렌더 때 removeChild 에서 터진다 */
  protected override usesShadow = true

  /** React 19 는 JSX 에서 넘긴 JSON 문자열을 프로퍼티로 넣는다 —
   *  무조건 stringify 하면 이중 인코딩된다. */
  set actions(v: EmptyAction[] | string) {
    this.setAttribute('actions', typeof v === 'string' ? v : JSON.stringify(v))
  }

  protected build(): void {
    // --shadow-menu 는 쓰지 않는다 — 거기 든 상단 하이라이트가 큰 패널에서는
    // 테두리 선으로 읽힌다.
    this.host(
      'display:flex;flex-direction:column;align-items:center;justify-content:center;' +
        'box-sizing:border-box;padding:38px 36px;' +
        'border-radius:var(--radius-panel);' +
        'backdrop-filter:blur(20px) saturate(125%);' +
        '-webkit-backdrop-filter:blur(20px) saturate(125%);' +
        'box-shadow:var(--shadow-hover)',
    )
  }

  protected override update(): void {
    const actions = this.json<EmptyAction[]>('actions', [])
    const body = this.str('body')

    // 밝은 배경에서는 표면을 어둡게 해 대비를 지킨다. 알파는 카드보다 낮춘다 —
    // 같은 알파여도 면적이 커지면 배경 바다가 죽는다.
    if (!this.authorSet('background') && !this.authorSet('background-color')) {
      this.style.backgroundColor = this.bright ? 'rgb(24 13 40 / .26)' : 'rgb(255 255 255 / .07)'
    }

    // 정적 마크업이라 innerHTML 로 둔다. heading·body·라벨은 절대 이 경로로 넣지
    // 않는다 — 호출부가 검색어를 그대로 넘겨 `?keyword=` 에 실은 태그가 그대로 산다.
    this.root.innerHTML =
      '<div style="width:60px;height:60px;border-radius:18px;position:relative;' +
      'background:var(--color-surface-quiet)">' +
      '<div style="position:absolute;left:19px;top:18px;width:18px;height:18px;border-radius:50%;' +
      'box-shadow:inset 0 0 0 2px rgba(255,255,255,.5)"></div>' +
      '<div style="position:absolute;left:34px;top:34px;width:9px;height:2px;border-radius:1px;' +
      'background:rgba(255,255,255,.5);transform:rotate(45deg)"></div>' +
      '</div>'

    // 텍스트는 textContent 로만 넣는다. 사용자가 입력한 검색어가 여기로 들어온다.
    const heading = document.createElement('div')
    heading.style.cssText =
      'margin-top:20px;font-size:20px;font-weight:700;letter-spacing:-.015em;' +
      'text-align:center;word-break:keep-all;color:#fff;' +
      'text-shadow:0 1px 12px rgba(24,8,34,.5)'
    heading.textContent = this.str('heading')
    this.root.append(heading)

    if (body) {
      const paragraph = document.createElement('div')
      paragraph.style.cssText =
        `margin-top:10px;width:${this.num('body-width', 440)}px;text-align:center;` +
        'font-size:14.5px;line-height:1.8;letter-spacing:-.01em;' +
        'color:var(--color-muted);' +
        // 한글은 keep-all 이 없으면 어절 중간에서 끊긴다 — "여러 에이전트를 이 / 어 붙이는"
        'word-break:keep-all;text-wrap:pretty'
      paragraph.textContent = body
      this.root.append(paragraph)
    }

    if (!actions.length) return

    const row = document.createElement('div')
    row.style.cssText = 'margin-top:24px;display:flex;gap:12px'

    for (const action of actions) {
      // href 는 `routes/paths.ts` 가 만든 내부 경로만 받는다. 'javascript:' 같은 스킴이
      // 섞이면 클릭 한 번이 실행이 된다 — 내부 링크가 아니면 버튼으로 떨어뜨린다.
      const href = action.href
      const isInternal = typeof href === 'string' && href.startsWith('/')
      const el = document.createElement(isInternal ? 'a' : 'div')
      if (isInternal && el instanceof HTMLAnchorElement) el.setAttribute('href', href)
      el.style.cssText =
        'height:46px;padding:0 26px;border-radius:23px;' +
        'display:flex;align-items:center;font-size:15px;font-weight:600;letter-spacing:-.01em;' +
        'text-decoration:none;cursor:pointer;transition:background .16s,filter .16s;' +
        (action.primary
          ? 'background:var(--color-accent);color:var(--color-accent-ink);' +
            'box-shadow:0 8px 22px rgb(10 4 20 / .28)'
          : 'background:rgb(255 255 255 / .12);color:#fff')
      el.textContent = action.label
      row.append(el)

      // 인라인 스타일이라 :hover를 못 쓴다
      if (action.primary) this.hover(el, 'brightness(1.08)', 'none', 'filter')
      else this.hover(el, 'rgb(255 255 255 / .2)', 'rgb(255 255 255 / .12)')
      if (!isInternal && action.event) {
        el.addEventListener('click', () => this.emit(action.event as string))
      }
    }

    this.root.append(row)
  }
}

define('empty-state', EmptyState)
