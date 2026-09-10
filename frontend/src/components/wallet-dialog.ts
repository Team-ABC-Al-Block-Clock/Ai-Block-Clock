import { BaseElement, define } from './base'

/** 지갑 연결·서명 대화상자 — 연결 요청 → 서명 대기 → 거절.
 *
 *    <wallet-dialog phase="request" address="0x1f9c…072a" />
 *
 *  단계는 화면이 속성으로 정하고 컴포넌트는 `sign`·`cancel` 만 던진다.
 *  React 가 단계를 들고 있어야 화면 상태와 갈라지지 않으므로 스스로 닫지 않는다.
 *  지갑 호출도 화면 쪽 몫이라, 이 컴포넌트는 체인도 지갑도 모른다.
 */
export type WalletPhase = 'idle' | 'request' | 'signing' | 'rejected'

export class WalletDialog extends BaseElement {
  static observedAttributes = ['phase', 'address', 'nonce', 'error-code']

  protected override usesShadow = true

  private onKey = (e: KeyboardEvent) => {
    if (e.key === 'Escape') this.emit('cancel')
  }

  /** 속성과 같은 이름의 프로퍼티를 두면 React 19 가 거기 대입하려다 터진다(10절). */
  private readPhase(): WalletPhase {
    return this.str('phase', 'idle') as WalletPhase
  }

  protected build(): void {
    this.host(
      'position:absolute;inset:0;display:none;align-items:center;justify-content:center;z-index:70',
    )
    // shadow 안에는 문서의 @keyframes 가 닿지 않는다 — 쓰는 것만 직접 넣는다
    this.addStyle(
      '@keyframes wd-in{from{opacity:0;transform:translateY(16px)}to{opacity:1;transform:none}}' +
        '@keyframes wd-fade{from{opacity:0}to{opacity:1}}' +
        '@keyframes wd-spin{to{transform:rotate(360deg)}}',
    )
  }

  override disconnectedCallback(): void {
    super.disconnectedCallback()
    document.removeEventListener('keydown', this.onKey)
  }

  protected override update(): void {
    const phase = this.readPhase()
    const open = phase !== 'idle'

    this.style.display = open ? 'flex' : 'none'
    document.removeEventListener('keydown', this.onKey)
    if (open) document.addEventListener('keydown', this.onKey)

    if (!open) {
      this.root.innerHTML = ''
      return
    }

    const sheet =
      'position:relative;width:400px;box-sizing:border-box;padding:28px;border-radius:20px;' +
      'background:rgba(22,12,38,.96);backdrop-filter:blur(26px) saturate(120%);' +
      '-webkit-backdrop-filter:blur(26px) saturate(120%);box-shadow:0 20px 52px rgba(4,2,12,.55);' +
      'text-align:center;animation:wd-in .3s cubic-bezier(.2,.9,.3,1) both'

    this.root.innerHTML =
      '<div data-veil style="position:absolute;inset:0;background:rgba(12,5,22,.5);' +
      'backdrop-filter:blur(6px);-webkit-backdrop-filter:blur(6px);' +
      'animation:wd-fade .2s ease both"></div>' +
      '<div style="' +
      sheet +
      '">' +
      this.bodyFor(phase) +
      '</div>'

    const veil = this.root.querySelector('[data-veil]') as HTMLElement
    veil.addEventListener('click', () => this.emit('cancel'))

    this.root.querySelectorAll<HTMLElement>('[data-btn]').forEach((el) => {
      const kind = el.dataset.btn
      const primary = el.dataset.primary === '1'
      if (primary) {
        // hover() 는 배경·밝기만 받는다. 주 버튼은 살짝 뜨는 것이 디자인이라 직접 붙인다.
        el.addEventListener('mouseenter', () => (el.style.transform = 'translateY(-1px)'))
        el.addEventListener('mouseleave', () => (el.style.transform = 'none'))
      } else {
        this.hover(el, 'rgba(255,255,255,.16)', 'rgba(255,255,255,.08)')
      }
      el.addEventListener('click', () => this.emit(kind === 'sign' ? 'sign' : 'cancel'))
    })
  }

  private bodyFor(phase: WalletPhase): string {
    if (phase === 'request')
      return (
        this.badge('M') +
        this.heading('지갑 연결 요청') +
        this.lead('MetaMask 확장에서 연결을 승인해 주세요.<br>승인하면 지갑 주소로 로그인됩니다.') +
        '<div style="margin-top:18px;height:44px;border-radius:11px;background:rgba(255,255,255,.07);' +
        'display:flex;align-items:center;justify-content:center;font-size:13.5px;font-weight:600;' +
        'color:#fff;letter-spacing:.01em">' +
        this.str('address', '0x0000…0000') +
        '</div>' +
        this.actions([
          { label: '취소', kind: 'cancel' },
          { label: '서명 요청', kind: 'sign', primary: true },
        ])
      )

    if (phase === 'signing')
      return (
        this.spinner() +
        this.heading('서명을 기다리는 중') +
        this.lead('MetaMask 창에서 서명을 승인해 주세요.<br>자산이 이동하지는 않습니다.') +
        this.note('서명할 메시지', 'ABC 로그인 · nonce ' + this.str('nonce', '000000')) +
        // 승인·거절은 MetaMask 창이 받는다. 여기에는 물러날 길만 둔다.
        this.actions([{ label: '취소', kind: 'cancel' }])
      )

    return (
      this.badge('×', 22, 600) +
      this.heading('서명이 거절되었습니다') +
      this.lead(
        '서명하지 않으면 로그인할 수 없습니다.<br>다시 시도하거나 지갑을 바꿔 연결해 주세요.',
      ) +
      this.note('오류 코드', this.str('error-code', 'ACTION_REJECTED · 4001'), '#f0b6c8') +
      this.actions([
        { label: '닫기', kind: 'cancel' },
        { label: '다시 서명', kind: 'sign', primary: true },
      ])
    )
  }

  // ── 조각들 ──────────────────────────────────────────────────

  private badge(glyph: string, size = 19, weight = 700): string {
    return (
      '<div style="width:52px;height:52px;margin:0 auto;border-radius:15px;' +
      'background:rgba(233,174,196,.2);display:flex;align-items:center;justify-content:center;' +
      'font-size:' +
      size +
      'px;font-weight:' +
      weight +
      ';color:#ffd7e6">' +
      glyph +
      '</div>'
    )
  }

  private spinner(): string {
    const ring = 'radial-gradient(farthest-side, transparent 66%, #000 68%)'
    return (
      '<div style="width:52px;height:52px;margin:0 auto;position:relative">' +
      '<div style="position:absolute;inset:0;border-radius:50%;background:rgba(255,255,255,.09)"></div>' +
      '<div style="position:absolute;inset:0;border-radius:50%;background:conic-gradient(from 0deg,' +
      'rgba(201,169,221,0) 0deg, var(--color-lilac,#c9a9dd) 300deg, rgba(201,169,221,0) 360deg);' +
      'mask:' +
      ring +
      ';-webkit-mask:' +
      ring +
      ';animation:wd-spin 1.4s linear infinite"></div>' +
      '</div>'
    )
  }

  private heading(text: string): string {
    return (
      '<div style="margin-top:16px;font-size:17px;font-weight:600;color:#fff;' +
      'letter-spacing:-.01em">' +
      text +
      '</div>'
    )
  }

  private lead(html: string): string {
    return (
      '<div style="margin-top:8px;font-size:13px;line-height:1.6;' +
      'color:rgba(255,236,246,.68);letter-spacing:-.01em">' +
      html +
      '</div>'
    )
  }

  private note(label: string, value: string, color = 'rgba(255,240,247,.8)'): string {
    return (
      '<div style="margin-top:18px;box-sizing:border-box;padding:12px 14px;border-radius:11px;' +
      'background:rgba(255,255,255,.06);text-align:left">' +
      '<div style="font-size:11px;color:rgba(255,236,246,.55);letter-spacing:-.01em">' +
      label +
      '</div>' +
      '<div style="margin-top:5px;font-size:12px;line-height:1.55;color:' +
      color +
      ';letter-spacing:-.01em">' +
      value +
      '</div></div>'
    )
  }

  private actions(list: { label: string; kind: string; primary?: boolean }[]): string {
    return (
      '<div style="margin-top:20px;display:flex;gap:10px">' +
      list
        .map(
          (a) =>
            '<div data-btn="' +
            a.kind +
            '"' +
            (a.primary ? ' data-primary="1"' : '') +
            ' role="button" tabindex="0"' +
            ' style="flex:1;height:46px;border-radius:12px;display:flex;align-items:center;' +
            'justify-content:center;font-size:14px;cursor:pointer;letter-spacing:-.01em;' +
            (a.primary
              ? 'background:var(--color-accent,#b98da9);color:var(--color-accent-ink,#2f1527);' +
                'font-weight:700;transition:transform .16s'
              : 'background:rgba(255,255,255,.08);color:#fff;font-weight:600;transition:background .16s') +
            '">' +
            a.label +
            '</div>',
        )
        .join('') +
      '</div>'
    )
  }
}

define('wallet-dialog', WalletDialog)
