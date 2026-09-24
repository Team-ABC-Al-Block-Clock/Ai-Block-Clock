import { describe, expect, it } from 'vitest'

import {
  FIT_MARGIN,
  GRID,
  INITIAL_VIEWPORT,
  ZOOM_MAX,
  ZOOM_MIN,
  clampZoom,
  fitViewport,
  gridPosition,
  gridSize,
  panBy,
  viewportTransform,
  wheelZoom,
  zoomAtPoint,
  zoomPercent,
  zoomStep,
  type Viewport,
} from './viewport'

/** 구현에서 가져오지 않는다 — 나란히 틀리면 못 잡는다. */
const toDesign = (view: Viewport, cx: number, cy: number) => ({
  x: (cx - view.tx) / view.k,
  y: (cy - view.ty) / view.k,
})

describe('clampZoom', () => {
  it('범위 안의 값은 그대로 둔다', () => {
    expect(clampZoom(1)).toBe(1)
    expect(clampZoom(0.5)).toBe(0.5)
  })

  it('범위 밖은 끝으로 잘라 낸다', () => {
    expect(clampZoom(0.01)).toBe(ZOOM_MIN)
    expect(clampZoom(99)).toBe(ZOOM_MAX)
  })
})

describe('panBy', () => {
  it('끈 만큼 그대로 민다', () => {
    expect(panBy(INITIAL_VIEWPORT, 40, -25)).toEqual({ tx: 40, ty: -25, k: 1 })
  })

  it('배율을 바꾸지 않는다', () => {
    expect(panBy({ tx: 0, ty: 0, k: 1.5 }, 10, 10).k).toBe(1.5)
  })

  it('확대된 상태에서도 화면 이동량과 1:1 이다', () => {
    expect(panBy({ tx: 0, ty: 0, k: 2 }, 30, 0).tx).toBe(30)
  })
})

describe('zoomAtPoint', () => {
  it('붙잡은 점의 설계 좌표가 그대로다', () => {
    const before = { tx: -120, ty: 40, k: 1 }
    const [cx, cy] = [500, 300]

    const after = zoomAtPoint(before, 1.75, cx, cy)

    expect(toDesign(after, cx, cy).x).toBeCloseTo(toDesign(before, cx, cy).x)
    expect(toDesign(after, cx, cy).y).toBeCloseTo(toDesign(before, cx, cy).y)
  })

  it('한계까지 확대해도 붙잡은 점은 그대로다', () => {
    const before = { tx: 10, ty: 10, k: 1.9 }
    const [cx, cy] = [200, 150]

    const after = zoomAtPoint(before, 5, cx, cy)

    expect(after.k).toBe(ZOOM_MAX)
    expect(toDesign(after, cx, cy).x).toBeCloseTo(toDesign(before, cx, cy).x)
  })

  it('배율이 그대로면 아무것도 움직이지 않는다', () => {
    const view = { tx: 33, ty: -7, k: 1.25 }
    expect(zoomAtPoint(view, 1.25, 400, 400)).toEqual(view)
  })
})

describe('zoomStep', () => {
  it('확대 뒤 축소하면 제자리로 돌아온다', () => {
    const back = zoomStep(zoomStep(INITIAL_VIEWPORT, 'in', 300, 200), 'out', 300, 200)

    expect(back.k).toBeCloseTo(1)
    expect(back.tx).toBeCloseTo(0)
    expect(back.ty).toBeCloseTo(0)
  })

  it('한계를 넘지 않는다', () => {
    let view = INITIAL_VIEWPORT
    for (let i = 0; i < 20; i += 1) view = zoomStep(view, 'in', 0, 0)
    expect(view.k).toBe(ZOOM_MAX)

    for (let i = 0; i < 40; i += 1) view = zoomStep(view, 'out', 0, 0)
    expect(view.k).toBe(ZOOM_MIN)
  })
})

describe('wheelZoom', () => {
  it('위로 굴리면 커진다', () => {
    expect(wheelZoom(INITIAL_VIEWPORT, -100, 0, 0).k).toBeGreaterThan(1)
  })

  it('아래로 굴리면 작아진다', () => {
    expect(wheelZoom(INITIAL_VIEWPORT, 100, 0, 0).k).toBeLessThan(1)
  })

  it('같은 칸을 반대로 굴리면 제자리다', () => {
    const view = wheelZoom(INITIAL_VIEWPORT, -120, 250, 180)
    expect(wheelZoom(view, 120, 250, 180).k).toBeCloseTo(1)
  })

  it('어느 배율에서 굴리든 한 칸의 비율이 같다', () => {
    const 작을때 = wheelZoom({ tx: 0, ty: 0, k: 0.4 }, -100, 0, 0).k / 0.4
    const 클때 = wheelZoom({ tx: 0, ty: 0, k: 1.6 }, -100, 0, 0).k / 1.6
    expect(작을때).toBeCloseTo(클때)
  })

  it('커서 아래를 붙잡는다', () => {
    const before = { tx: 60, ty: -20, k: 1 }
    const [cx, cy] = [720, 410]

    const after = wheelZoom(before, -240, cx, cy)

    expect(toDesign(after, cx, cy).x).toBeCloseTo(toDesign(before, cx, cy).x)
    expect(toDesign(after, cx, cy).y).toBeCloseTo(toDesign(before, cx, cy).y)
  })
})

describe('zoomPercent', () => {
  it('정수로 보여 준다', () => {
    expect(zoomPercent({ tx: 0, ty: 0, k: 1 })).toBe(100)
    expect(zoomPercent({ tx: 0, ty: 0, k: 0.333 })).toBe(33)
  })
})

describe('viewportTransform', () => {
  it('이동이 배율보다 앞에 온다', () => {
    expect(viewportTransform({ tx: 12, ty: -8, k: 1.5 })).toBe('translate(12px, -8px) scale(1.5)')
  })
})

describe('격자', () => {
  it('칸이 배율을 탄다', () => {
    expect(gridSize({ tx: 0, ty: 0, k: 2 })).toBe(`${GRID * 2}px ${GRID * 2}px`)
  })

  it('위상이 팬을 그대로 따라간다', () => {
    const 기준 = (GRID / 2) * 1
    expect(gridPosition({ tx: 40, ty: -15, k: 1 })).toBe(`${기준 + 40}px ${기준 - 15}px`)
  })

  it('팬이 없으면 반 칸 밀린 자리에서 시작한다', () => {
    expect(gridPosition(INITIAL_VIEWPORT)).toBe(`${GRID / 2}px ${GRID / 2}px`)
  })
})

describe('fitViewport', () => {
  const pane = { width: 980, height: 1024 }

  it('여백 안에 들어가면 배율도 자리도 그대로다', () => {
    // 몇 개 안 되는데 축소된 화면으로 시작하면 사용자가 매번 되돌려야 한다.
    const view = fitViewport({ minX: 12, minY: 428, maxX: 968, maxY: 596 }, pane)

    expect(view.k).toBe(1)
    expect(view.tx).toBeCloseTo(0)
    expect(view.ty).toBeCloseTo(0)
  })

  it('가로로 넘치면 다 보이도록 줄인다', () => {
    const view = fitViewport({ minX: 12, minY: 428, maxX: 1165, maxY: 596 }, pane)

    expect(view.k).toBeLessThan(1)
    // 줄인 뒤에는 여백 안에 들어와야 한다.
    expect(12 * view.k + view.tx).toBeGreaterThanOrEqual(FIT_MARGIN - 0.001)
    expect(1165 * view.k + view.tx).toBeLessThanOrEqual(pane.width - FIT_MARGIN + 0.001)
  })

  it('한가운데에 놓는다', () => {
    const view = fitViewport({ minX: 100, minY: 200, maxX: 300, maxY: 400 }, pane)
    const left = 100 * view.k + view.tx
    const right = 300 * view.k + view.tx

    expect(left).toBeCloseTo(pane.width - right)
  })

  it('아무리 넓어도 최소 배율 밑으로는 줄이지 않는다', () => {
    const view = fitViewport({ minX: 0, minY: 0, maxX: 100_000, maxY: 200 }, pane)

    expect(view.k).toBe(ZOOM_MIN)
  })
})
