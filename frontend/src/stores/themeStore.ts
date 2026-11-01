import type { Theme, EffectiveTheme, ThemeOption } from '../components/types'

const KEY = 'oceanTheme'
const DEFAULT: Theme = 'sunset'
const VALID: Theme[] = ['auto', 'day', 'sunset', 'night']

export const THEME_OPTIONS: ThemeOption[] = [
  { value: 'auto', label: '현재 시각' },
  { value: 'day', label: '낮' },
  { value: 'sunset', label: '노을' },
  { value: 'night', label: '밤' },
]

function read(): Theme {
  const win = window as unknown as { __oceanTheme?: Theme }
  if (win.__oceanTheme) return win.__oceanTheme
  let t: string | null = null
  try {
    t = localStorage.getItem(KEY)
  } catch {
    /* 접근 불가 환경 */
  }
  return VALID.includes(t as Theme) ? (t as Theme) : DEFAULT
}

/** 테마 상태. 프레임워크 무관 — localStorage에 남고 window에 'oceantheme'를 발생시킵니다. */
export const OceanTheme = {
  OPTIONS: THEME_OPTIONS,

  get: read,

  set(theme: Theme): Theme {
    if (!VALID.includes(theme)) return read()
    ;(window as unknown as { __oceanTheme?: Theme }).__oceanTheme = theme
    try {
      localStorage.setItem(KEY, theme)
    } catch {
      /* 무시 */
    }
    window.dispatchEvent(new CustomEvent<Theme>('oceantheme', { detail: theme }))
    return theme
  },

  /** 'auto'를 지금 시각으로 해석 */
  effective(): EffectiveTheme {
    const t = read()
    if (t !== 'auto') return t
    const h = new Date().getHours()
    if (h >= 8 && h < 17) return 'day'
    if (h >= 17 && h < 20) return 'sunset'
    return 'night'
  },

  /** 밝은 배경(낮·노을)인지 — 글자 대비 보정이 필요한지 판단 */
  isBright(): boolean {
    const e = OceanTheme.effective()
    return e === 'day' || e === 'sunset'
  },

  subscribe(fn: (theme: Theme) => void): () => void {
    const handler = (e: Event) => fn((e as CustomEvent<Theme>).detail || read())
    window.addEventListener('oceantheme', handler)
    return () => window.removeEventListener('oceantheme', handler)
  },
}

;(window as unknown as { OceanTheme?: typeof OceanTheme }).OceanTheme = OceanTheme
