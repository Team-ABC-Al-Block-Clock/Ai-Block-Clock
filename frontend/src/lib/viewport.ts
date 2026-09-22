/**
 * 작업면을 들여다보는 창.
 *
 * 노드 좌표(설계 좌표)는 캔버스 위 절대 위치이고 끝이 없다. 화면은 그중 일부만
 * 보여 주는데, 어디를 얼마나 크게 보여 줄지가 여기 세 값에 담긴다. 좌표 변환이
 * 여러 곳에 흩어지면 팬 중에 드롭한 자리와 그려진 자리가 어긋나므로 전부 이 모듈에 둔다.
 *
 * 화면 = 설계 × k + t 이고, 되돌리면 설계 = (화면 − t) / k 다.
 */
export interface Viewport {
  /** 화면 기준 이동량(px). 오른쪽·아래가 양수다 */
  tx: number
  ty: number
  /** 확대 배율. 1 이 100% */
  k: number
}

export const INITIAL_VIEWPORT: Viewport = { tx: 0, ty: 0, k: 1 }

/** 더 줄이면 카드 글자를 못 읽고, 더 키우면 카드 한 장이 화면을 덮는다. */
export const ZOOM_MIN = 0.25
export const ZOOM_MAX = 2

/** 툴바 − / + 한 번에 움직이는 폭. 곱으로 걸어야 확대·축소가 대칭이다. */
const ZOOM_STEP = 1.2

/**
 * 휠 한 칸에 걸리는 배율. 지수로 걸어 어느 배율에서 돌리든 체감이 같게 한다 —
 * 덧셈으로 하면 0.3 에서 한 칸이 크고 1.8 에서 한 칸이 작다.
 */
const WHEEL_SENSITIVITY = 0.0015

export function clampZoom(k: number): number {
  return Math.min(Math.max(k, ZOOM_MIN), ZOOM_MAX)
}

/** 화면에서 끈 만큼 그대로 민다 — 배율이 얼마든 손가락과 배경이 같이 움직여야 한다. */
export function panBy(view: Viewport, dx: number, dy: number): Viewport {
  return { ...view, tx: view.tx + dx, ty: view.ty + dy }
}

/**
 * 화면의 한 점을 붙잡은 채 배율만 바꾼다.
 *
 * 그 점의 설계 좌표가 전후로 같아야 하므로 `(c − tx) / k` 를 고정한다.
 * 이게 없으면 커서 아래가 아니라 좌상단 기준으로 커져 보던 자리를 놓친다.
 */
export function zoomAtPoint(view: Viewport, k: number, cx: number, cy: number): Viewport {
  const next = clampZoom(k)
  const ratio = next / view.k
  return {
    k: next,
    tx: cx - (cx - view.tx) * ratio,
    ty: cy - (cy - view.ty) * ratio,
  }
}

/** 툴바 − / + . 커서가 아니라 화면 가운데를 붙잡는다 — 누른 자리는 툴바다. */
export function zoomStep(
  view: Viewport,
  direction: 'in' | 'out',
  cx: number,
  cy: number,
): Viewport {
  const k = direction === 'in' ? view.k * ZOOM_STEP : view.k / ZOOM_STEP
  return zoomAtPoint(view, k, cx, cy)
}

/** 휠. `deltaY` 는 위로 굴리면 음수라 확대가 된다. */
export function wheelZoom(view: Viewport, deltaY: number, cx: number, cy: number): Viewport {
  return zoomAtPoint(view, view.k * Math.exp(-deltaY * WHEEL_SENSITIVITY), cx, cy)
}

/** 툴바에 띄우는 값. 25% 부터 200% 까지 정수로만 보여 준다. */
export function zoomPercent(view: Viewport): number {
  return Math.round(view.k * 100)
}

/** world 레이어에 거는 값. `translate` 가 먼저다 — 순서를 바꾸면 이동량이 배율을 탄다. */
export function viewportTransform(view: Viewport): string {
  return `translate(${view.tx}px, ${view.ty}px) scale(${view.k})`
}

/** 격자 한 칸. 팬·줌을 따라갈 때도 이 간격이 기준이다. */
export const GRID = 26

/**
 * 격자는 변환 판이 아니라 화면에 고정된 층에 남는다 — 판에 얹어 무한히 늘리는
 * 것보다 싸고, 아무리 멀리 가도 끊기지 않는다. 대신 위상과 간격을 여기서 맞춘다.
 *
 * 컴포넌트가 그리는 값과 팬 중에 직접 써 넣는 값이 갈리면 손을 떼는 순간 격자가
 * 튀므로, 두 곳이 이 함수 하나를 같이 쓴다.
 */
export function gridSize(view: Viewport): string {
  return `${GRID * view.k}px ${GRID * view.k}px`
}

export function gridPosition(view: Viewport): string {
  const half = (GRID / 2) * view.k
  return `${half + view.tx}px ${half + view.ty}px`
}
