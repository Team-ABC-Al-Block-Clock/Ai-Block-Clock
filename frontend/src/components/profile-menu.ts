import { BaseElement, define } from './base'
import { OceanTheme } from './theme-store'
import type { Theme } from './types'

const ROW_STYLE =
  'height:50px;border-radius:11px;display:flex;align-items:center;gap:12px;' +
  'padding:0 14px;color:#fff;font-size:14.5px;font-weight:500;letter-spacing:-.01em;' +
  'text-decoration:none;transition:background .16s'

const ICON_SEARCH =
  '<div style="width:18px;height:18px;flex:none;position:relative">' +
  '<div style="position:absolute;left:0;top:0;width:14px;height:14px;border-radius:50%;' +
  'box-shadow:inset 0 0 0 2px rgba(255,255,255,.8)"></div>' +
  '<div style="position:absolute;left:11px;top:12px;width:8px;height:2px;border-radius:1px;' +
  'background:rgba(255,255,255,.8);transform:rotate(45deg)"></div>' +
  '</div>'

const ICON_PERSON =
  '<div style="width:18px;height:18px;flex:none;position:relative">' +
  '<div style="position:absolute;left:5px;top:1px;width:9px;height:9px;border-radius:50%;' +
  'box-shadow:inset 0 0 0 2px rgba(255,255,255,.8)"></div>' +
  '<div style="position:absolute;left:1px;top:12px;width:17px;height:9px;border-radius:9px 9px 0 0;' +
  'box-shadow:inset 0 2px 0 rgba(255,255,255,.8),inset 2px 0 0 rgba(255,255,255,.8),' +
  'inset -2px 0 0 rgba(255,255,255,.8)"></div>' +
  '</div>'

/** 테마 아이콘 — fg는 선택 여부에 따라 바뀌는 색 */
function themeIcon(kind: Theme, fg: string): string {
  if (kind === 'auto')
    return (
      '' +
      '<div style="position:relative;width:20px;height:20px">' +
      '<div style="position:absolute;left:1px;top:1px;width:18px;height:18px;border-radius:50%;' +
      'box-shadow:inset 0 0 0 2px ' +
      fg +
      '"></div>' +
      '<div style="position:absolute;left:9px;top:5px;width:2px;height:6px;border-radius:1px;background:' +
      fg +
      '"></div>' +
      '<div style="position:absolute;left:9.5px;top:9px;width:5px;height:2px;border-radius:1px;background:' +
      fg +
      ';' +
      'transform-origin:left center;transform:rotate(28deg)"></div>' +
      '</div>'
    )
  if (kind === 'day')
    return (
      '' +
      '<div style="position:relative;width:20px;height:20px">' +
      '<div style="position:absolute;left:5px;top:5px;width:10px;height:10px;border-radius:50%;background:' +
      fg +
      '"></div>' +
      '<div style="position:absolute;left:9px;top:0;width:2px;height:4px;border-radius:1px;background:' +
      fg +
      '"></div>' +
      '<div style="position:absolute;left:9px;top:16px;width:2px;height:4px;border-radius:1px;background:' +
      fg +
      '"></div>' +
      '<div style="position:absolute;left:0;top:9px;width:4px;height:2px;border-radius:1px;background:' +
      fg +
      '"></div>' +
      '<div style="position:absolute;left:16px;top:9px;width:4px;height:2px;border-radius:1px;background:' +
      fg +
      '"></div>' +
      '</div>'
    )
  if (kind === 'sunset')
    return (
      '' +
      '<div style="position:relative;width:20px;height:20px">' +
      '<div style="position:absolute;left:4px;top:4px;width:12px;height:6px;border-radius:6px 6px 0 0;background:' +
      fg +
      '"></div>' +
      '<div style="position:absolute;left:0;top:12px;width:20px;height:2px;border-radius:1px;background:' +
      fg +
      '"></div>' +
      '<div style="position:absolute;left:3px;top:16px;width:14px;height:2px;border-radius:1px;background:' +
      fg +
      ';opacity:.6"></div>' +
      '</div>'
    )
  return (
    '' +
    '<div style="position:relative;width:20px;height:20px">' +
    '<div style="position:absolute;left:3px;top:2px;width:14px;height:14px;border-radius:50%;background:' +
    fg +
    '"></div>' +
    '<div style="position:absolute;left:8px;top:0;width:13px;height:13px;border-radius:50%;background:rgba(24,11,38,.94)"></div>' +
    '</div>'
  )
}

/** 아바타 + 드롭다운(에이전트 검색 · 마이페이지 · 테마 선택).
 *
 *    <profile-menu style="position:absolute;left:1374px;top:26px"
 *                  search-href="search.html" mypage-href="my-agents.html"></profile-menu>
 *
 *  테마 선택은 OceanTheme에 반영되므로 <ocean-bg>가 자동으로 따라옵니다.
 */
export class ProfileMenu extends BaseElement {
  static observedAttributes = ['search-href', 'mypage-href', 'align', 'hide-theme', 'initial']

  protected override usesShadow = true

  private panel!: HTMLDivElement
  private veil!: HTMLDivElement
  private avatar!: HTMLElement
  private opened = false
  private unsub?: () => void
  private onKey = (e: KeyboardEvent) => {
    if (e.key === 'Escape') this.close()
  }

  get open(): boolean {
    return this.opened
  }

  protected build(): void {
    this.host('position:relative;display:block;width:48px;height:48px')

    this.root.innerHTML =
      '<div data-veil style="display:none;position:fixed;inset:0;cursor:default;z-index:40"></div>' +
      '<div data-avatar role="button" tabindex="0" aria-label="프로필 메뉴" ' +
      'style="position:absolute;inset:0;border-radius:24px;background:rgba(28,10,38,.30);' +
      'backdrop-filter:blur(16px);-webkit-backdrop-filter:blur(16px);' +
      'box-shadow:0 4px 16px rgba(30,8,40,.25);cursor:pointer;overflow:hidden;' +
      'display:flex;align-items:center;justify-content:center;z-index:41"></div>' +
      '<div data-panel style="display:none;position:absolute;top:60px;width:246px;box-sizing:border-box;' +
      'padding:10px;border-radius:var(--r-menu,16px);background:var(--surface-menu,rgba(24,11,38,.72));' +
      'backdrop-filter:blur(28px) saturate(130%);-webkit-backdrop-filter:blur(28px) saturate(130%);' +
      'box-shadow:var(--lift-menu,inset 0 1px 0 rgba(255,255,255,.18),0 24px 56px rgba(10,4,20,.5));' +
      'flex-direction:column;gap:4px;z-index:42;transform-origin:88% 0;' +
      'animation:ui-menu .26s cubic-bezier(.2,.9,.3,1) both"></div>'

    this.addStyle(
      '@keyframes ui-menu{from{opacity:0;transform:translateY(-6px) scale(.97)}' +
        'to{opacity:1;transform:none}}',
    )

    this.panel = this.root.querySelector('[data-panel]') as HTMLDivElement
    this.veil = this.root.querySelector('[data-veil]') as HTMLDivElement

    this.avatar = this.root.querySelector('[data-avatar]') as HTMLElement
    this.avatar.addEventListener('click', (e) => {
      e.stopPropagation()
      this.toggle()
    })
    this.veil.addEventListener('click', () => this.close())
    this.unsub = OceanTheme.subscribe(() => this.paintThemes())
  }

  override disconnectedCallback(): void {
    super.disconnectedCallback()
    document.removeEventListener('keydown', this.onKey)
    this.unsub?.()
  }

  protected override update(): void {
    if (!this.panel) return
    this.paintAvatar()
    this.panel.style.left = this.str('align') === 'left' ? '0' : ''
    this.panel.style.right = this.str('align') === 'left' ? '' : '0'

    let html =
      '<a href="' +
      this.str('search-href', 'search.html') +
      '" data-row style="' +
      ROW_STYLE +
      '">' +
      ICON_SEARCH +
      '<span>에이전트 검색</span></a>' +
      '<a href="' +
      this.str('mypage-href', 'my-agents.html') +
      '" data-row style="' +
      ROW_STYLE +
      '">' +
      ICON_PERSON +
      '<span>마이페이지</span></a>'

    if (!this.bool('hide-theme')) {
      html +=
        '<div style="margin:6px 4px 2px;height:1px;background:rgba(255,255,255,.14)"></div>' +
        '<div style="padding:2px 10px 4px;font-size:11.5px;letter-spacing:.04em;' +
        'color:rgba(255,236,246,.55)">테마</div>' +
        '<div data-themes style="display:flex;gap:8px;padding:0 8px 4px"></div>'
    }
    this.panel.innerHTML = html

    this.panel.querySelectorAll<HTMLElement>('[data-row]').forEach((el) => {
      this.hover(el, 'rgba(255,255,255,.14)', 'transparent')
      // 이동하면 메뉴는 닫힌다 — 화면이 리마운트되지 않는 SPA 에서도 남지 않게
      el.addEventListener('click', () => this.close())
    })
    this.paintThemes()
  }

  /** 이름 첫 글자가 있으면 그것을, 없으면 사람 실루엣을 */
  private paintAvatar(): void {
    const initial = this.str('initial')
    this.avatar.innerHTML = initial
      ? '<span style="font-size:15px;font-weight:600;letter-spacing:-.01em;' +
        'color:rgba(255,255,255,.94)">' +
        initial +
        '</span>'
      : '<div style="position:absolute;left:16px;top:11px;width:16px;height:16px;border-radius:50%;' +
        'background:rgba(255,255,255,.82)"></div>' +
        '<div style="position:absolute;left:9px;top:31px;width:30px;height:16px;' +
        'border-radius:15px 15px 0 0;background:rgba(255,255,255,.82)"></div>'
  }

  private paintThemes(): void {
    const host = this.panel?.querySelector('[data-themes]') as HTMLElement | null
    if (!host) return
    const current = OceanTheme.get()
    host.innerHTML = ''

    OceanTheme.OPTIONS.forEach((opt) => {
      const on = opt.value === current
      const fg = on ? 'var(--accent-ink,#2f1527)' : 'rgba(255,255,255,.85)'
      const idle = 'rgba(255,255,255,.08)'
      const cell = this.div(
        'flex:1;height:46px;border-radius:11px;display:flex;align-items:center;' +
          'justify-content:center;cursor:pointer;transition:background .16s;background:' +
          (on ? 'rgba(255,255,255,.9)' : idle),
        themeIcon(opt.value, fg),
      )
      cell.setAttribute('role', 'button')
      cell.setAttribute('title', opt.label)
      cell.setAttribute('aria-pressed', String(on))
      cell.addEventListener('click', (e) => {
        e.stopPropagation()
        OceanTheme.set(opt.value)
      })
      if (!on) this.hover(cell, 'rgba(255,255,255,.18)', idle)
      host.appendChild(cell)
    })
  }

  toggle(): void {
    this.opened ? this.close() : this.show()
  }

  show(): void {
    this.opened = true
    this.panel.style.display = 'flex'
    this.veil.style.display = 'block'
    document.addEventListener('keydown', this.onKey)
    this.emit('menuopen')
  }

  close(): void {
    this.opened = false
    this.panel.style.display = 'none'
    this.veil.style.display = 'none'
    document.removeEventListener('keydown', this.onKey)
    this.emit('menuclose')
  }
}

define('profile-menu', ProfileMenu)
