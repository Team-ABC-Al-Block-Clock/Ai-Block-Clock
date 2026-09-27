import type { ReactNode } from 'react'

/** 패널 자체의 가로 폭. 작업면은 캔버스에서 패널을 제외한 나머지 부분이다. */
export const PANEL_WIDTH = 460
/** 패널 안쪽 여백 */
export const PANEL_INSET = 30
/** 안쪽 내용이 쓸 수 있는 가로 폭 */
export const PANEL_CONTENT_WIDTH = PANEL_WIDTH - PANEL_INSET * 2
/** 카드 안 요소가 카드 좌우 변에서 떨어지는 거리. 위아래 간격은 카드마다 다르다. */
export const CARD_GUTTER = 16
/** 설계 캔버스 세로. 패널 안 절대 좌표가 이 높이를 기준으로 놓인다(`CanvasPane` 의 `PANE_HEIGHT` 와 같다). */
export const PANEL_HEIGHT = 1024
/** 패널 바닥 여백 */
export const PANEL_BOTTOM = 24
/**
 * 위아래를 가르는 선. 위는 고른 노드, 아래는 팔레트다.
 *
 * 위쪽은 절대 배치라 늘어나지 않는다 — 칸이 모자라면 이 값을 내리고 아래를 덜어 온다.
 * 팔레트는 카드 한 줄이 148px 이라, 여기서 더 내리면 두 줄이 온전히 서지 못한다.
 */
export const PANEL_SPLIT_Y = 600

type Props = {
  children: ReactNode
  /** 진입 모션 */
  animation?: string
}

/**
 * 편집·실행 화면 오른쪽에 붙는 패널.
 * 패널 자체는 칠하지 않고, 작업면과 나누는 구분선만 남긴다.
 */
export default function SidePanel({
  children,
  animation = 'sideIn 1s .1s cubic-bezier(.18,.86,.24,1) both',
}: Props) {
  return (
    <div
      className="absolute"
      style={{
        right: 'calc(-1 * var(--edge, 0px))',
        // 창 위아래 끝까지 늘리고, 내용물을 늘어난 화면에 맞춰 붙인다.
        top: 'calc(-1 * var(--edge-y, 0px))',
        bottom: 'calc(-1 * var(--edge-y, 0px))',
        width: `${PANEL_WIDTH}px`,
        boxShadow: 'inset 1px 0 0 rgba(255,255,255,.14),-30px 0 60px rgba(10,4,20,.35)',
        animation,
      }}
    >
      {children}
    </div>
  )
}
