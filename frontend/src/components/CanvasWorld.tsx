import type { ReactNode, Ref } from 'react'

import { viewportTransform, type Viewport } from '@/lib/viewport'

import { PANEL_CLIP, PANE_HEIGHT, PANE_WIDTH } from './CanvasPane'

type CanvasWorldProps = {
  view: Viewport
  children: ReactNode
  /** 창 좌표를 설계 좌표로 되돌리려면 변환까지 반영된 크기를 재야 한다. */
  ref?: Ref<HTMLDivElement>
}

/**
 * 팬·줌이 걸리는 판. 그래프만 여기 들어간다 — 안내 문구·줌 툴바·패널은
 * 화면에 붙어 있어야 하므로 작업면에 그대로 둔다.
 *
 * 패널 클립을 이 판 **바깥**에 둔다. 안에 두면 클립도 함께 변환돼 잘리는 자리가
 * 패널이 아니라 그래프를 따라다닌다. 대신 연결선 판과 카드 판에서는 클립을 뺀다 —
 * 같은 클립이 두 번 걸릴 이유가 없다.
 *
 * `transformOrigin` 은 좌상단이다. 가운데로 두면 `scale` 이 제 이동량을 더해
 * `lib/viewport` 의 「화면 = 설계 × k + t」가 어긋난다.
 *
 * 판정은 흘려보낸다 — 작업면을 덮고 있어 그대로 두면 아래가 눌리지 않는다.
 * 연결선과 카드가 각자 제 몫만 다시 켠다.
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
