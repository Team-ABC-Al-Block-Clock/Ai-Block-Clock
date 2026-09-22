/** components/*.ts 가 등록하는 커스텀 엘리먼트를 JSX에서 쓰기 위한 선언.
 *  React는 하이픈 있는 태그의 속성을 그대로 DOM 속성으로 넘긴다. */
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
      'wallet-dialog': El<{
        /** 'idle' | 'request' | 'signing' | 'rejected' — 화면이 단계를 소유한다 */
        phase?: string
        address?: string
        /** 지갑이 서명할 원문. 이름에 접미사를 붙인 이유는 10절 참고 */
        'message-text'?: string
        'error-code'?: string
      }>
      'confirm-dialog': El<{
        /** 있으면 열린다. bool() 이 hasAttribute 라서 빈 문자열로 넘긴다 */
        open?: string
        heading?: string
        'body-text'?: string
        /** 강조할 한 줄. 산정된 판매가처럼 값이 있을 때만 넘긴다 */
        'note-text'?: string
        'confirm-label'?: string
        'cancel-label'?: string
      }>
      'empty-state': El<{
        heading?: string
        body?: string
        /** EmptyAction[] 의 JSON 문자열 */
        actions?: string
        'body-width'?: number | string
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
    /** dc 화면 전환 셸이 넣어주던 훅 — 라우터가 들어오면 대체 */
    __navigate?: (href: string) => void
  }
}
