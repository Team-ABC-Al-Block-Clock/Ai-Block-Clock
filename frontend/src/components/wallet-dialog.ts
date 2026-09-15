import { BaseElement, define } from './base'

/** 지갑 서명 대화상자 — 서명 대기 → 거절.
 *
 *    <wallet-dialog phase="signing" address="0x1f9c…072a" message-text={message} />
 *
 *  단계는 화면이 속성으로 정하고 컴포넌트는 `sign`·`cancel` 만 던진다 — 스스로 닫지
 *  않고, 지갑 호출도 화면 몫이라 체인도 지갑도 모른다.
 */
export type WalletPhase = 'idle' | 'signing' | 'rejected'

/** 단계마다 대화상자의 이름. 스크린리더가 열릴 때 이것을 읽는다. */
const TITLE: Record<Exclude<WalletPhase, 'idle'>, string> = {
  signing: '지갑에서 서명을 기다리는 중',
  rejected: '서명이 거절되었습니다',
}

export class WalletDialog extends BaseElement {
  /** 이름에 접미사를 붙인다 — 표준 프로퍼티(`HTMLElement.nonce` 등)와 겹치면
   *  React 가 속성 대신 프로퍼티에 넣어 조용히 기본값이 그려진다(10절). */
  static observedAttributes = ['phase', 'address', 'message-text', 'error-code']

  protected override usesShadow = true

  /** 열기 직전에 포커스를 갖고 있던 요소 — 닫을 때 여기로 돌려준다.
   *  돌려주지 않으면 키보드 사용자가 문서 맨 앞부터 다시 짚어 내려와야 한다. */
  private returnFocusTo: HTMLElement | null = null

  /** 마지막으로 그린 단계. 포커스는 단계가 바뀔 때만 옮긴다 —
   *  `message-text` 가 늦게 붙어 다시 그릴 때마다 옮기면 포커스를 빼앗는다. */
  private drawn: WalletPhase = 'idle'

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
    // shadow 안에는 문서의 @keyframes 가 닿지 않는다 — 쓰는 것만 직접 넣는다.
    // 서명 대기 표시는 조각이 많아 클래스로 둔다. shadow 안이라 이름이 새지 않는다.
    this.addStyle(
      '@keyframes wd-in{from{opacity:0;transform:translateY(16px)}to{opacity:1;transform:none}}' +
        '@keyframes wd-fade{from{opacity:0}to{opacity:1}}' +
        '@keyframes wd-halo{0%,100%{transform:scale(.88);opacity:.4}50%{transform:scale(1.08);opacity:.8}}' +
        '@keyframes wd-orbit{to{transform:rotate(360deg)}}' +
        // 가는 링 하나. 안쪽에 뭘 더 넣으면 카메라 렌즈처럼 보인다.
        '.wd-orb{position:relative;width:56px;height:56px;margin:0 auto}' +
        '.wd-orb>span{position:absolute;display:block;border-radius:50%}' +
        // 달빛이 번지는 듯한 후광 — 대기 중이라는 것을 멀리서도 알아보게 한다
        '.wd-halo{inset:-9px;background:radial-gradient(circle,rgba(233,183,210,.3),rgba(233,183,210,0) 66%);animation:wd-halo 2.4s ease-in-out infinite}' +
        '.wd-track{inset:0;background:rgba(255,255,255,.07)}' +
        '.wd-arc{inset:0;background:conic-gradient(from 0deg,rgba(233,183,210,0) 0deg,' +
        'var(--color-accent-bright,#e9b7d2) 176deg,#fff5fa 296deg,rgba(233,183,210,0) 342deg);' +
        'mask:radial-gradient(farthest-side,transparent 70%,#000 72%);' +
        '-webkit-mask:radial-gradient(farthest-side,transparent 70%,#000 72%);' +
        'animation:wd-orbit 1.5s cubic-bezier(.55,.12,.45,.88) infinite}' +
        // 움직임을 줄여 달라고 설정한 사람에게는 후광만 천천히 남긴다
        '@media (prefers-reduced-motion:reduce){.wd-arc{animation:none}' +
        '.wd-halo{animation-duration:4s}}',
    )
  }

  override disconnectedCallback(): void {
    super.disconnectedCallback()
    document.removeEventListener('keydown', this.onKey)
  }

  protected override update(): void {
    const phase = this.readPhase()
    const open = phase !== 'idle'
    const changed = phase !== this.drawn

    this.style.display = open ? 'flex' : 'none'
    document.removeEventListener('keydown', this.onKey)
    if (open) document.addEventListener('keydown', this.onKey)

    // 열리기 직전의 포커스를 잡아 둔다. 다시 그릴 때 덮어쓰면 대화상자 안의
    // 버튼으로 돌아가게 되므로 닫힌 상태에서 열릴 때만 잡는다.
    if (open && this.drawn === 'idle') {
      const active = document.activeElement
      this.returnFocusTo = active instanceof HTMLElement ? active : null
    }
    this.drawn = phase

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

    // 모달이라는 것을 보조 기술에 알린다. `observedAttributes` 에 없으니
    // 여기서 붙여도 다시 그리지 않는다.
    this.setAttribute('role', 'dialog')
    this.setAttribute('aria-modal', 'true')
    this.setAttribute('aria-label', TITLE[phase])

    const sheet =
      'position:relative;width:456px;box-sizing:border-box;padding:28px;border-radius:20px;' +
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
      const fire = () => this.emit(kind === 'sign' ? 'sign' : 'cancel')
      el.addEventListener('click', fire)
      // `div[role=button]` 은 Enter·Space 를 스스로 누르지 않는다. 포커스를 주는
      // 이상 키보드로도 눌려야 한다.
      el.addEventListener('keydown', (e) => {
        if (e.key !== 'Enter' && e.key !== ' ') return
        e.preventDefault()
        fire()
      })
    })

    // 단계가 바뀐 순간에만 안으로 포커스를 넣는다. 주 버튼이 있으면 그쪽이
    // 다음 행동이고, 서명 대기처럼 없으면 첫 버튼(취소)이 유일한 길이다.
    if (changed) {
      const enter =
        this.root.querySelector<HTMLElement>('[data-primary="1"]') ??
        this.root.querySelector<HTMLElement>('[data-btn]')
      enter?.focus()
    }
  }

  private bodyFor(phase: WalletPhase): string {
    if (phase === 'signing')
      return (
        this.spinner() +
        this.heading('지갑에서 서명을 기다리는 중') +
        this.lead('MetaMask 창에서 서명을 완료해 주세요.\n서명에는 가스비가 들지 않습니다.') +
        // 지갑이 실제로 서명하는 원문을 그대로 보여 준다. 요약을 손으로 만들면
        // MetaMask 창과 달라져, 대조해 확인하려는 사람을 오히려 속인다.
        this.note([
          ...this.str('message-text').split(/\r?\n/).filter(Boolean),
          `서명 계정: ${this.str('address', '0x0000…0000')}`,
        ]) +
        // 승인·거절은 MetaMask 창이 받는다. 여기에는 물러날 길만 둔다.
        this.actions([{ label: '취소', kind: 'cancel' }])
      )

    return (
      this.badge('!', 20, 700) +
      this.heading('서명이 거절되었습니다') +
      this.lead('지갑에서 서명을 취소했습니다. 로그인을 계속하려면 다시 시도해 주세요.') +
      this.code(this.str('error-code', 'ACTION_REJECTED · 4001')) +
      this.actions([
        { label: '취소', kind: 'cancel' },
        { label: '다시 시도', kind: 'sign', primary: true },
      ])
    )
  }

  // ── 조각들 ──────────────────────────────────────────────────

  private badge(glyph: string, size = 19, weight = 700): string {
    return (
      '<div style="width:52px;height:52px;margin:0 auto;border-radius:50%;' +
      'background:rgba(240,150,170,.22);display:flex;align-items:center;justify-content:center;' +
      'font-size:' +
      size +
      'px;font-weight:' +
      weight +
      ';color:#ffb4c8">' +
      glyph +
      '</div>'
    )
  }

  /** 서명을 기다리는 동안의 표시. 스타일은 `build()` 의 클래스에 있다. */
  private spinner(): string {
    return (
      '<div class="wd-orb">' +
      '<span class="wd-halo"></span>' +
      '<span class="wd-track"></span>' +
      '<span class="wd-arc"></span>' +
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

  /** 마크업으로 해석되지 않게 막는다. `note` 에는 서버가 준 문자열이 들어온다. */
  private esc(text: string): string {
    return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  }

  private lead(text: string): string {
    // 줄바꿈은 `\n` 으로 쓴다. `pre-line` 이 없으면 HTML 이 공백으로 접어 버린다.
    return (
      '<div style="margin-top:8px;font-size:13px;line-height:1.6;white-space:pre-line;' +
      'color:rgba(255,236,246,.68);letter-spacing:-.01em">' +
      this.esc(text) +
      '</div>'
    )
  }

  private note(lines: string[]): string {
    return (
      '<div style="margin-top:18px;box-sizing:border-box;padding:12px 14px;border-radius:11px;' +
      'background:rgba(255,255,255,.06)">' +
      lines
        .map(
          (line, i) =>
            '<div style="' +
            (i === 0
              ? 'font-size:12.5px;font-weight:600;color:#fff'
              : 'margin-top:5px;font-size:11.5px;color:rgba(255,236,246,.62)') +
            ';letter-spacing:-.01em">' +
            this.esc(line) +
            '</div>',
        )
        .join('') +
      '</div>'
    )
  }

  /** 오류 코드는 박스 없이 한 줄로 — 시안에서 안내문과 버튼 사이에 조용히 놓인다. */
  private code(text: string): string {
    return (
      '<div style="margin-top:14px;font-size:11.5px;color:rgba(255,236,246,.55);' +
      'letter-spacing:-.01em">' +
      text +
      '</div>'
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
