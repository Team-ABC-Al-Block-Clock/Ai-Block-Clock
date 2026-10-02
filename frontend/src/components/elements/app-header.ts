import { BaseElement, define } from './base'

/**
 * 좌상단 로고 + 앱 이름.
 *
 *   <app-header home-href="/" logo-src="assets/logo.png"></app-header>
 *
 * 기본은 글자 로크업이고, `wordmark-src` 를 주면 그 자리에 이미지를 넣는다.
 */
export class AppHeader extends BaseElement {
  static observedAttributes = ['home-href', 'name', 'tagline', 'logo-src', 'wordmark-src']

  protected override usesShadow = true

  private logo!: HTMLAnchorElement
  private plate!: HTMLDivElement

  protected build(): void {
    this.host('position:absolute;left:0;top:0;width:317px;height:95px;z-index:3')

    const logo = document.createElement('a')
    logo.style.cssText =
      'position:absolute;left:6px;top:6px;width:72px;height:89px;display:block;' +
      'overflow:hidden;padding:20px 4px;box-sizing:border-box'
    this.root.appendChild(logo)
    this.logo = logo

    const plate = this.div(
      'position:absolute;left:80px;top:6px;width:237px;height:89px;box-sizing:border-box;' +
        'display:flex;flex-direction:column;justify-content:center;gap:6px;overflow:hidden',
    )
    this.root.appendChild(plate)
    this.plate = plate
  }

  protected override update(): void {
    this.logo.href = this.str('home-href', '/')

    const logoSrc = this.str('logo-src')
    this.logo.innerHTML = logoSrc
      ? '<img src="' +
        logoSrc +
        '" alt="' +
        this.str('name', 'ABC') +
        ' 로고" ' +
        'style="width:100%;height:100%;object-fit:contain">'
      : ''

    const markSrc = this.str('wordmark-src')
    if (markSrc) {
      // contain은 남는 폭만큼 이미지를 가운데로 밀어 로고와 벌어진다 — 왼쪽에 붙인다
      this.plate.style.padding = '12px 8px'
      this.plate.innerHTML =
        '<img src="' +
        markSrc +
        '" alt="' +
        this.str('name', 'ABC') +
        '" ' +
        'style="width:100%;height:100%;object-fit:contain;object-position:left center">'
      return
    }

    this.plate.style.padding = '0'
    this.plate.innerHTML =
      "<div style=\"font-family:'Outfit',system-ui,sans-serif;font-size:34px;font-weight:600;" +
      'letter-spacing:.05em;color:#fff;line-height:1;text-shadow:0 1px 10px rgba(20,6,32,.5)">' +
      this.str('name', 'ABC') +
      '</div>' +
      "<div style=\"font-family:'Outfit',system-ui,sans-serif;font-size:9.5px;font-weight:500;" +
      'letter-spacing:.28em;color:rgba(255,255,255,.9);line-height:1;text-shadow:0 1px 9px rgba(20,6,32,.55)">' +
      this.str('tagline', 'AI BLOCK CLOCK') +
      '</div>'
  }
}

define('app-header', AppHeader)
