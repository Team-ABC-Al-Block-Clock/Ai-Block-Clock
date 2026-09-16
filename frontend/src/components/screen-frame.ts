import { BaseElement, define } from './base'

/**
 * 고정 설계 캔버스를 뷰포트에 맞춰 확대/축소한다. 자식은 설계 좌표계 그대로 둔다.
 *
 *   <screen-frame width="1440" height="1584" fit="width"> … </screen-frame>
 *
 * 호스트 자신이 무대라 자식을 옮기지 않는다 — 옮기면 React 가 노드 추적을 잃는다.
 */
export class ScreenFrame extends BaseElement {
  static observedAttributes = ['width', 'height', 'fit']

  private currentScale = 1
  private fit = () => this.applyFit()

  get designWidth(): number {
    return this.num('width', 1440)
  }
  get designHeight(): number {
    return this.num('height', 1024)
  }
  /** 현재 배율 */
  get scale(): number {
    return this.currentScale
  }

  protected build(): void {
    this.host('position:fixed;display:block;overflow:hidden;flex:none')
    window.addEventListener('resize', this.fit)
  }

  override disconnectedCallback(): void {
    super.disconnectedCallback()
    window.removeEventListener('resize', this.fit)
  }

  protected override update(): void {
    this.applyFit()
  }

  private applyFit(): void {
    const w = this.designWidth
    const h = this.designHeight
    const vw = window.innerWidth
    const vh = window.innerHeight

    const byWidth = this.str('fit') === 'width'
    const s = byWidth ? vw / w : Math.min(vw / w, vh / h)
    this.currentScale = s

    this.style.width = w + 'px'
    this.style.height = h + 'px'
    this.style.left = '50%'
    this.style.transformOrigin = byWidth ? 'top center' : 'center center'
    if (byWidth) {
      // 세로로 넘치는 설계(스크롤 페이지)는 위에서부터 채운다
      this.style.top = '0'
      this.style.transform = 'translateX(-50%) scale(' + s + ')'
    } else {
      this.style.top = '50%'
      this.style.transform = 'translate(-50%, -50%) scale(' + s + ')'
    }

    this.emit('framefit', { scale: s })
  }
}

define('screen-frame', ScreenFrame)
