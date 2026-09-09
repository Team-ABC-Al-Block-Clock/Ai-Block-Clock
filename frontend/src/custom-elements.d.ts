/** components/*.ts 가 등록하는 커스텀 엘리먼트를 JSX에서 쓰기 위한 선언.
 *  React는 하이픈 있는 태그의 속성을 그대로 DOM 속성으로 넘깁니다. */
import type { DetailedHTMLProps, HTMLAttributes } from 'react'

type El<P = object> = DetailedHTMLProps<HTMLAttributes<HTMLElement>, HTMLElement> & P

declare module 'react' {
  namespace JSX {
    interface IntrinsicElements {
      'screen-frame': El<{
        width?: number | string
        height?: number | string
        fit?: 'contain' | 'width'
      }>
      'ocean-bg': El<{
        theme?: string
        'wave-intensity'?: number | string
        'sun-height'?: number | string
        'motion-speed'?: number | string
        'day-cycle-min'?: number | string
        dim?: number | string
        'blur-px'?: number | string
      }>
      'app-header': El<{
        'home-href'?: string
        name?: string
        tagline?: string
        'logo-src'?: string
        'wordmark-src'?: string
      }>
      'profile-menu': El<{
        'search-href'?: string
        'mypage-href'?: string
        align?: 'left' | 'right'
        'hide-theme'?: boolean
        initial?: string
        /** 있으면 로그아웃 행이 보인다. `bool()` 이 hasAttribute 라서 빈 문자열로 넘긴다 */
        'signed-in'?: string
      }>
      'profile-sidebar': El
      'tab-row': El<{ items?: string }>
      'agent-card': El
      'metric-card': El<{ label?: string; value?: string; delta?: string }>
      'star-rating': El<{ count?: number | string }>
      'data-table': El
      'donut-gauge': El<{ value?: number | string }>
      'line-chart': El
      'flow-graph': El
      'prompt-bar': El<{ placeholder?: string }>
      'chip-row': El<{ items?: string }>
      'toast-host': El
      'modal-sheet': El
    }
  }
}

declare global {
  interface Window {
    /** dc 화면 전환 셸이 넣어주던 훅 — 라우터가 들어오면 대체됩니다 */
    __navigate?: (href: string) => void
  }
}
