/** 컴포넌트 전반에서 쓰는 공용 타입 */

/** 바다 배경 테마. 'auto'는 실제 시각으로 해석됩니다. */
export type Theme = 'auto' | 'day' | 'sunset' | 'night'

/** 'auto'가 해석된 뒤의 실제 테마 */
export type EffectiveTheme = Exclude<Theme, 'auto'>

export interface ThemeOption {
  value: Theme
  label: string
}

/** 빈 상태에서 고를 수 있는 다음 행동.
 *  `href` 가 있으면 링크, 없으면 `event` 이름으로 CustomEvent 를 올립니다. */
export interface EmptyAction {
  label: string
  href?: string
  /** href 가 없을 때 올릴 이벤트 이름 */
  event?: string
  primary?: boolean
}
