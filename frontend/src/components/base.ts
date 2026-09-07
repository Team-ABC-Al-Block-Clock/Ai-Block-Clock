/** 'a:1;b:2' 형태의 선언 문자열을 속성 맵으로 */
function parseDecls(text: string): Record<string, string> {
  const out: Record<string, string> = {}
  for (const part of text.split(';')) {
    const at = part.indexOf(':')
    if (at < 0) continue
    const key = part.slice(0, at).trim()
    const val = part.slice(at + 1).trim()
    if (key && val) out[key] = val
  }
  return out
}

/** 커스텀 엘리먼트 공통 기반.
 *  - 속성 읽기 헬퍼(숫자·불리언·문자)
 *  - connected 시 한 번만 build(), 이후 속성 변화에는 update()
 *  - 인라인 스타일 환경에서 :hover를 쓸 수 없으므로 hover 헬퍼 제공
 */
export abstract class BaseElement extends HTMLElement {
  private built = false

  /** 마크업에 작성자가 직접 준 인라인 스타일.
   *  컴포넌트 기본값이 배치(position·left·top 등)를 덮지 않도록 보관합니다. */
  protected authorStyle: Record<string, string> = {}

  /** 테마에 따라 표면을 바꿔야 하는 컴포넌트는 true로 다시 선언하세요.
   *  그러면 테마가 바례는 대로 update()가 다시 호출됩니다. */
  protected usesTheme = false

  /** 자기 DOM 을 shadow 안에 넣을지. 하위 클래스에서 true 로 다시 선언하세요.
   *
   *  React·Vue 같은 가상 DOM 아래에서는 반드시 true 여야 합니다.
   *  light DOM 에 직접 쓰면 프레임워크가 모르는 자식이 생겨,
   *  리렌더 때 removeChild 에서 터집니다. */
  protected usesShadow = false

  /** 컴포넌트가 자기 DOM 을 넣는 곳 — shadow 를 쓰면 shadowRoot, 아니면 호스트 */
  protected root!: HTMLElement | ShadowRoot

  private themeSync?: () => void

  connectedCallback(): void {
    if (!this.built) {
      this.built = true
      this.authorStyle = parseDecls(this.getAttribute('style') || '')
      this.root = this.usesShadow ? this.attachShadow({ mode: 'open' }) : this
      this.build()
    }
    if (this.usesTheme) {
      if (!this.themeSync) this.themeSync = () => this.update()
      window.addEventListener('oceantheme', this.themeSync)
    }
    this.update()
  }

  /** 하위 클래스가 재정의할 땐 super.disconnectedCallback() 를 부를것 */
  disconnectedCallback(): void {
    if (this.themeSync) window.removeEventListener('oceantheme', this.themeSync)
  }

  /** 닮·노을처럼 배경이 밝은 테마인가 — 글자 대보를 지킬 때 표면을 어둡게 합니다 */
  protected get bright(): boolean {
    const store = (window as unknown as { OceanTheme?: { isBright(): boolean } }).OceanTheme
    return store ? store.isBright() : false
  }

  attributeChangedCallback(): void {
    if (this.built) this.update()
  }

  /** 최초 한 번. DOM 구성은 여기서 */
  protected abstract build(): void

  /** 속성이 바뀔 때마다. 값 반영은 여기서 */
  protected update(): void {}

  protected str(name: string, dflt = ''): string {
    const v = this.getAttribute(name)
    return v === null ? dflt : v
  }

  protected num(name: string, dflt: number): number {
    // Number(null) 은 0 이므로 속성 부재를 먼저 걸러야 기본값이 살아난다
    const raw = this.getAttribute(name)
    if (raw === null || raw.trim() === '') return dflt
    const v = Number(raw)
    return Number.isFinite(v) ? v : dflt
  }

  protected bool(name: string): boolean {
    return this.hasAttribute(name)
  }

  /** JSON 속성 — 목록 데이터를 마크업으로 넘길 때 */
  protected json<T>(name: string, dflt: T): T {
    const raw = this.getAttribute(name)
    if (!raw) return dflt
    try {
      return JSON.parse(raw) as T
    } catch {
      console.warn(
        '[' + this.tagName.toLowerCase() + '] ' + name + ' 속성의 JSON을 읽을 수 없습니다',
      )
      return dflt
    }
  }

  /** 테두리를 쓰지 않는 디자인이라, 상태 변화는 배경/밝기로만 */
  protected hover(
    el: HTMLElement,
    on: string,
    off: string,
    prop: 'background' | 'filter' = 'background',
  ): void {
    el.addEventListener('mouseenter', () => {
      el.style[prop] = on
    })
    el.addEventListener('mouseleave', () => {
      el.style[prop] = off
    })
  }

  protected emit<T>(type: string, detail?: T): void {
    this.dispatchEvent(new CustomEvent<T>(type, { detail, bubbles: true }))
  }

  /** 여러 스타일을 한 번에 — 컴포넌트가 직접 만든 요소에만 씁니다.
   *  호스트(this)에는 cssText가 작성자 스타일을 지우므로 host()를 쓰세요. */
  protected css(el: HTMLElement, text: string): HTMLElement {
    el.style.cssText = text
    return el
  }

  /** 호스트 자신의 기본 스타일. 작성자가 지정한 속성은 건드리지 않습니다. */
  protected host(text: string): void {
    for (const [key, val] of Object.entries(parseDecls(text))) {
      if (!(key in this.authorStyle)) this.style.setProperty(key, val)
    }
  }

  /** 작성자가 그 속성을 직접 지정했는지 — 크기·배치를 강제하기 전에 확인 */
  protected authorSet(prop: string): boolean {
    return prop in this.authorStyle
  }

  /** shadow 안에서는 문서의 @keyframes 가 안 닿는다 — 쓰는 것만 안에 넣어준다 */
  protected addStyle(css: string): void {
    const el = document.createElement('style')
    el.textContent = css
    this.root.appendChild(el)
  }

  protected div(style: string, html = ''): HTMLDivElement {
    const d = document.createElement('div')
    d.style.cssText = style
    if (html) d.innerHTML = html
    return d
  }
}

/** 중복 정의를 피해 등록 */
export function define(tag: string, ctor: CustomElementConstructor): void {
  if (!window.customElements.get(tag)) window.customElements.define(tag, ctor)
}
