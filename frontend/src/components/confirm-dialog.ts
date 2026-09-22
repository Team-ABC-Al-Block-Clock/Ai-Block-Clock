import { BaseElement, define } from './base'

/** 되돌릴 수 없는 작업을 한 번 더 묻는다 — 스스로 닫지 않고 confirm·cancel 만 던진다.
 *
 *    <confirm-dialog open heading="…" body-text="…" note-text="…" />
 */
export class ConfirmDialog extends BaseElement {
  static observedAttributes = [
    'open',
    'heading',
    'body-text',
    'note-text',
    'confirm-label',
    'cancel-label',
  ]

  protected override usesShadow = true

  /** 열기 직전의 포커스 — 닫을 때 여기로 돌려준다. */
  private returnFocusTo: HTMLElement | null = null

  private wasOpen = false

  private onKey = (e: KeyboardEvent) => {
    if (e.key === 'Escape') this.emit('cancel')
  }

  protected build(): void {
    this.host(
      'position:absolute;inset:0;display:none;align-items:center;justify-content:center;z-index:70',
    )
    // shadow 안에는 문서의 @keyframes 가 닿지 않는다.
    this.addStyle(
      '@keyframes cd-in{from{opacity:0;transform:translateY(16px)}to{opacity:1;transform:none}}' +
        '@keyframes cd-fade{from{opacity:0}to{opacity:1}}' +
        '@media (prefers-reduced-motion:reduce){[data-sheet],[data-veil]{animation:none}}',
    )
  }

  override disconnectedCallback(): void {
    super.disconnectedCallback()
    document.removeEventListener('keydown', this.onKey)
  }

  protected override update(): void {
    const open = this.bool('open')
    const changed = open !== this.wasOpen

    this.style.display = open ? 'flex' : 'none'
    document.removeEventListener('keydown', this.onKey)
    if (open) document.addEventListener('keydown', this.onKey)

    if (open && !this.wasOpen) {
      const active = document.activeElement
      this.returnFocusTo = active instanceof HTMLElement ? active : null
    }
    this.wasOpen = open

    if (!open) {
      this.removeAttribute('role')
      this.removeAttribute('aria-modal')
      this.removeAttribute('aria-label')
      this.root.innerHTML = ''
      if (changed) {
        this.returnFocusTo?.focus()
        this.returnFocusTo = null
      }
      return
    }

    const heading = this.str('heading', '계속하시겠습니까?')
    this.setAttribute('role', 'dialog')
    this.setAttribute('aria-modal', 'true')
    this.setAttribute('aria-label', heading)

    this.root.innerHTML =
      '<div data-veil style="position:absolute;inset:0;background:rgba(12,5,22,.5);' +
      'backdrop-filter:blur(6px);-webkit-backdrop-filter:blur(6px);' +
      'animation:cd-fade .2s ease both"></div>' +
      '<div data-sheet style="position:relative;width:456px;box-sizing:border-box;padding:28px;' +
      'border-radius:20px;background:rgba(22,12,38,.96);backdrop-filter:blur(26px) saturate(120%);' +
      '-webkit-backdrop-filter:blur(26px) saturate(120%);box-shadow:0 20px 52px rgba(4,2,12,.55);' +
      'text-align:center;animation:cd-in .3s cubic-bezier(.2,.9,.3,1) both">' +
      this.headingHtml(heading) +
      this.noteHtml(this.str('note-text')) +
      this.leadHtml(this.str('body-text')) +
      this.actionsHtml() +
      '</div>'

    const veil = this.root.querySelector('[data-veil]') as HTMLElement
    veil.addEventListener('click', () => this.emit('cancel'))

    this.root.querySelectorAll<HTMLButtonElement>('[data-btn]').forEach((el) => {
      const confirm = el.dataset.btn === 'confirm'
      if (confirm) {
        el.addEventListener('mouseenter', () => (el.style.transform = 'translateY(-1px)'))
        el.addEventListener('mouseleave', () => (el.style.transform = 'none'))
      } else {
        this.hover(el, 'rgba(255,255,255,.16)', 'var(--color-surface-card)')
      }
      el.addEventListener('click', () => this.emit(confirm ? 'confirm' : 'cancel'))
    })

    // 되돌릴 수 없는 작업이라 물러나는 쪽에 포커스를 둔다.
    if (changed) this.root.querySelector<HTMLElement>('[data-btn="cancel"]')?.focus()
  }

  /** 제목·본문에 화면이 만든 문자열이 들어온다. */
  private esc(text: string): string {
    return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  }

  private headingHtml(text: string): string {
    return (
      '<div style="font-size:19px;font-weight:600;color:#fff;letter-spacing:-.01em">' +
      this.esc(text) +
      '</div>'
    )
  }

  private leadHtml(text: string): string {
    if (!text) return ''
    // 줄바꿈은 \n 으로 쓴다. pre-line 이 없으면 HTML 이 공백으로 접는다.
    return (
      '<div style="margin-top:10px;font-size:13.5px;line-height:1.6;white-space:pre-line;' +
      'color:rgba(255,236,246,.68);letter-spacing:-.01em">' +
      this.esc(text) +
      '</div>'
    )
  }

  private noteHtml(text: string): string {
    if (!text) return ''
    return (
      '<div style="margin-top:16px;box-sizing:border-box;padding:12px 14px;border-radius:11px;' +
      'background:rgba(255,255,255,.06);font-size:13px;font-weight:600;color:#fff;' +
      'letter-spacing:-.01em">' +
      this.esc(text) +
      '</div>'
    )
  }

  /** div 가 아니라 button 이다 — Enter·Space 를 스스로 받는다. */
  private actionsHtml(): string {
    const base =
      'flex:1;height:46px;border:0;border-radius:12px;font:inherit;font-size:14px;' +
      'cursor:pointer;letter-spacing:-.01em;'
    return (
      '<div style="margin-top:22px;display:flex;gap:10px">' +
      '<button type="button" data-btn="cancel" style="' +
      base +
      'background:var(--color-surface-card);color:#fff;font-weight:600;transition:background .16s">' +
      this.esc(this.str('cancel-label', '취소')) +
      '</button>' +
      '<button type="button" data-btn="confirm" style="' +
      base +
      'background:var(--color-accent);color:var(--color-accent-ink);font-weight:700;' +
      'transition:transform .16s">' +
      this.esc(this.str('confirm-label', '확인')) +
      '</button>' +
      '</div>'
    )
  }
}

define('confirm-dialog', ConfirmDialog)
