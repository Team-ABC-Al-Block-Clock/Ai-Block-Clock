/** 컴포넌트 전반에서 쓰는 공용 타입 */

/** 바다 배경 테마. 'auto'는 실제 시각으로 해석됩니다. */
export type Theme = 'auto' | 'day' | 'sunset' | 'night'

/** 'auto'가 해석된 뒤의 실제 테마 */
export type EffectiveTheme = Exclude<Theme, 'auto'>

export interface ThemeOption {
  value: Theme
  label: string
}
