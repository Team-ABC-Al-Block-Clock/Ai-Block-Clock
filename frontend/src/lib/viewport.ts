/** 작업면을 들여다보는 창. 화면 = 설계 × k + t. */
export interface Viewport {
  tx: number
  ty: number
  k: number
}

export const INITIAL_VIEWPORT: Viewport = { tx: 0, ty: 0, k: 1 }

export const ZOOM_MIN = 0.25
export const ZOOM_MAX = 2

/** 곱으로 걸어야 확대·축소가 대칭이다. */
const ZOOM_STEP = 1.2
/** 지수로 걸어야 어느 배율에서 굴리든 한 칸 체감이 같다. */
const WHEEL_SENSITIVITY = 0.0015

export const GRID = 26

export function clampZoom(k: number): number {
  return Math.min(Math.max(k, ZOOM_MIN), ZOOM_MAX)
}

export function panBy(view: Viewport, dx: number, dy: number): Viewport {
  return { ...view, tx: view.tx + dx, ty: view.ty + dy }
}

export function zoomAtPoint(view: Viewport, k: number, cx: number, cy: number): Viewport {
  const next = clampZoom(k)
  const ratio = next / view.k
  return {
    k: next,
    tx: cx - (cx - view.tx) * ratio,
    ty: cy - (cy - view.ty) * ratio,
  }
}

export function zoomStep(
  view: Viewport,
  direction: 'in' | 'out',
  cx: number,
  cy: number,
): Viewport {
  const k = direction === 'in' ? view.k * ZOOM_STEP : view.k / ZOOM_STEP
  return zoomAtPoint(view, k, cx, cy)
}

export function wheelZoom(view: Viewport, deltaY: number, cx: number, cy: number): Viewport {
  return zoomAtPoint(view, view.k * Math.exp(-deltaY * WHEEL_SENSITIVITY), cx, cy)
}

export function zoomPercent(view: Viewport): number {
  return Math.round(view.k * 100)
}

/** `translate` 가 먼저다 — 순서를 바꾸면 이동량이 배율을 탄다. */
export function viewportTransform(view: Viewport): string {
  return `translate(${view.tx}px, ${view.ty}px) scale(${view.k})`
}

// 격자는 화면에 고정된 층에 남는다. 컴포넌트와 팬이 아래 둘을 같이 써야 손 뗄 때 안 튄다.

export function gridSize(view: Viewport): string {
  return `${GRID * view.k}px ${GRID * view.k}px`
}

export function gridPosition(view: Viewport): string {
  const half = (GRID / 2) * view.k
  return `${half + view.tx}px ${half + view.ty}px`
}
