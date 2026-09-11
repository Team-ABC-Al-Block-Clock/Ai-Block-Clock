import { BaseElement, define } from './base'
import type { EmptyAction } from './types'

/** 목록이 비었을 때 그 자리에 놓는 카드.
 *  검색 결과 없음 · 사용 내역 없음 · 내 에이전트 없음 · 리뷰 없음에 모두 씁니다.
 *
 *    <empty-state
 *      heading="'zzzz' 에 맞는 에이전트가 없습니다"
 *      body="찾는 일을 다르게 적어보거나, 프롬프트로 직접 만들어 보세요."
 *      actions='[{"label":"직접 만들어보기","href":"/","primary":true},
 *                {"label":"검색어 지우기","event":"clear"}]'>
 *    </empty-state>
 *
 *  글씨만 띄우면 바다 배경 위에서 읽히지 않으므로, 카드들과 같은 유리 표면 위에 올립니다.
 *  호스트 자신이 그 카드입니다 — 배치(position·left·top·width)와 등장 애니메이션만
 *  쓰는 쪽에서 style 로 주세요. `host()` 가 작성자 스타일을 덮지 않습니다.
 *
 *  버튼은 href가 있으면 링크, 없으면 event 이름으로 이벤트를 올립니다.
 *  빈 상태는 실패가 아니라 다음 행동을 고르는 자리이므로, 행동을 최소 하나 주세요.
 *  href 는 `routes/paths.ts` 가 만든 내부 경로를 넘깁니다 — App.tsx 의 전역 클릭
 *  인터셉터가 `composedPath()` 로 shadow 경계를 넘어 이 <a> 를 찾아 라우팅합니다.
 */
export class EmptyState extends BaseElement {
  static observedAttributes = ['heading', 'body', 'actions', 'body-width']

  protected override usesTheme = true

  /** React 아래에서 쓰므로 shadow 에 그린다. light DOM 에 직접 쓰면
   *  React 가 모르는 자식이 생겨 리렌더 때 removeChild 에서 터진다. */
  protected override usesShadow = true

  /** 배열이면 직렬화하고, 이미 JSON 문자열이면 그대로 둔다.
   *  React 19 는 프로퍼티가 있으면 속성이 아니라 프로퍼티로 넣기 때문에 JSX 에서 넘긴
   *  JSON 문자열이 여기로 들어온다 — 무조건 stringify 하면 이중 인코딩된다. */
  set actions(v: EmptyAction[] | string) {
    this.setAttribute('actions', typeof v === 'string' ? v : JSON.stringify(v))
  }

  protected build(): void {
    // 호스트가 카드다. 배치는 작성자 몫이라 여기서 정하지 않는다.
    //
    // 표면 값은 index.css 의 @theme 토큰에서 가져온다. shadow DOM 도 custom property 는
    // 상속받으므로 var() 가 닿는다.
    //
    // --shadow-menu 는 쓰지 않는다. 거기 든 `inset 0 1px 0` 상단 하이라이트가 큰 패널에서는
    // 테두리 선으로 읽힌다. 이 프로젝트의 그림자 원칙은 "경계가 생기지 않게 넓고 옅게"
    // (index.css 주석)이므로 --shadow-hover 처럼 외곽만 옅게 깔린 것을 쓴다.
    this.host(
      'display:flex;flex-direction:column;align-items:center;justify-content:center;' +
        'box-sizing:border-box;padding:38px 36px;' +
        'border-radius:var(--radius-panel,20px);' +
        'backdrop-filter:blur(20px) saturate(125%);' +
        '-webkit-backdrop-filter:blur(20px) saturate(125%);' +
        'box-shadow:var(--shadow-hover,0 16px 46px rgb(10 4 20 / .26))',
    )
  }

  protected override update(): void {
    const actions = this.json<EmptyAction[]>('actions', [])
    const body = this.str('body')

    // 밝은 배경(낮·노을)에서는 표면을 어둡게 해 글자 대비를 지킨다.
    // 알파는 카드(--color-surface-card)보다 더 낮춘다 — 같은 알파여도 면적이 커지면
    // 훨씬 무겁게 보여서 배경 바다가 죽는다.
    if (!this.authorSet('background') && !this.authorSet('background-color')) {
      this.style.backgroundColor = this.bright ? 'rgb(24 13 40 / .26)' : 'rgb(255 255 255 / .07)'
    }

    // 아이콘은 돋보기 하나. 장식을 늘리지 않는다.
    // ponytail: 검색 화면 전용 도형이다. "사용 내역 없음" 처럼 다른 그림이 필요해지면
    // 그때 icon 속성을 받는다 — 지금 아이콘 체계를 만들 이유가 없다.
    //
    // 정적 마크업이라 innerHTML 로 둔다. 아래 heading·body·라벨은 절대 이 경로로 넣지
    // 않는다 — 호출부가 검색어를 그대로 넘기므로(Search·MyAgents) `?keyword=` 에 태그를
    // 실은 링크 하나로 shadow DOM 안에 <img onerror> 가 만들어진다.
    this.root.innerHTML =
      '<div style="width:60px;height:60px;border-radius:18px;position:relative;' +
      'background:var(--color-surface-quiet,rgb(255 255 255 / .055))">' +
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
        'color:var(--color-muted,rgb(255 236 246 / .66));' +
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
          ? 'background:var(--color-accent,#b98da9);color:var(--color-accent-ink,#2f1527);' +
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
