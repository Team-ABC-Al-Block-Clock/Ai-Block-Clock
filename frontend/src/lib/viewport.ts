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

/** 무언가를 감싸는 사각형. 오른쪽·아래 끝은 포함이다. */
export interface ContentBox {
  minX: number
  minY: number
  maxX: number
  maxY: number
}

/**
 * 맞춰 보일 때 작업면 가장자리에 남기는 여백.
 *
 * 한 줄 배치가 다섯 장까지 딱 들어가는 여백과 같은 값이다 — 그 이하에서는 배율이 1 로
 * 나와 화면이 그대로고, 여섯 장부터 저절로 줄어든다.
 */
export const FIT_MARGIN = 12

/**
 * 담긴 것이 다 보이도록 맞춘 창.
 *
 * 들어가고도 남으면 배율을 건드리지 않는다 — 몇 개 안 되는데 축소된 화면으로 시작하면
 * 사용자가 매번 되돌려야 한다. 넘칠 때만 줄이고, 어느 쪽이든 가운데에 놓는다.
 */
export function fitViewport(
  content: ContentBox,
  size: { width: number; height: number },
  margin: number = FIT_MARGIN,
): Viewport {
  const contentWidth = Math.max(content.maxX - content.minX, 1)
  const contentHeight = Math.max(content.maxY - content.minY, 1)
  const k = clampZoom(
    Math.min(
      1,
      (size.width - margin * 2) / contentWidth,
      (size.height - margin * 2) / contentHeight,
    ),
  )

  return {
    k,
    tx: (size.width - contentWidth * k) / 2 - content.minX * k,
    ty: (size.height - contentHeight * k) / 2 - content.minY * k,
  }
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
