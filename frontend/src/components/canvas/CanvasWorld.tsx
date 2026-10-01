import type { ReactNode, Ref } from 'react'

import { viewportTransform, type Viewport } from '@/lib/canvas/viewport'

import { PANEL_CLIP, PANE_HEIGHT, PANE_WIDTH } from '@/lib/canvas/pane'

type CanvasWorldProps = {
  view: Viewport
  children: ReactNode
  ref?: Ref<HTMLDivElement>
}

/**
 * 팬·줌이 걸리는 판. 그래프만 들어간다.
 *
 * 패널 클립은 판 바깥에 둔다 — 안에 두면 잘리는 자리가 그래프를 따라다닌다.
 * 늘 `transform` 이 걸려 있어 backdrop 루트다. 안쪽 `backdrop-filter` 는 검게 보인다(10절).
 */
export default function CanvasWorld({ view, children, ref }: CanvasWorldProps) {
  return (
    <div className="pointer-events-none absolute inset-0" style={{ clipPath: PANEL_CLIP }}>
      <div
        ref={ref}
        className="absolute top-0 left-0"
        style={{
          width: `${PANE_WIDTH}px`,
          height: `${PANE_HEIGHT}px`,
          transform: viewportTransform(view),
          transformOrigin: '0 0',
        }}
      >
        {children}
      </div>
    </div>
  )
}
