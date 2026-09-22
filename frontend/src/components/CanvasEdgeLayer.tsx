import type { ReactNode } from 'react'

import { PANEL_CLIP, PANE_HEIGHT, PANE_WIDTH } from './CanvasPane'

type CanvasEdgeLayerProps = {
  children: ReactNode
  /**
   * 패널 쪽을 여기서 자를지. 팬·줌이 걸린 화면은 `CanvasWorld` 가 판 바깥에서
   * 이미 자르므로 끈다 — 변환 안에서 자르면 잘리는 자리가 그래프를 따라 움직인다.
   */
  clip?: boolean
}

/**
 * 작업면 위에 연결선을 그리는 판.
 *
 * 카드와 같은 좌표계를 쓰도록 작업면과 크기를 맞춘다. `overflow: visible` 은
 * 카드가 작업면 끝까지 밀렸을 때 거기서 나가는 선이 잘리지 않게 한다 — 대신
 * 패널 쪽만 `PANEL_CLIP` 으로 자른다. 카드와 같은 선에서 가려져야 한다.
 * 판정은 선마다 따로 켠다 — 판 전체가 포인터를 먹으면 카드를 못 잡는다.
 */
export default function CanvasEdgeLayer({ children, clip = true }: CanvasEdgeLayerProps) {
  return (
    <svg
      className="pointer-events-none absolute top-0 left-0"
      width={PANE_WIDTH}
      height={PANE_HEIGHT}
      viewBox={`0 0 ${PANE_WIDTH} ${PANE_HEIGHT}`}
      style={{ overflow: 'visible', clipPath: clip ? PANEL_CLIP : undefined }}
    >
      {children}
    </svg>
  )
}
