import type { ReactNode } from 'react'

/** 패널 자체의 가로 폭. 작업면은 캔버스에서 패널을 제외한 나머지 부분이다. */
export const PANEL_WIDTH = 460
/** 패널 안쪽 여백 */
export const PANEL_INSET = 30
/** 안쪽 내용이 쓸 수 있는 가로 폭 */
export const PANEL_CONTENT_WIDTH = PANEL_WIDTH - PANEL_INSET * 2

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
      className="absolute top-0"
      style={{
        right: 'calc(-1 * var(--edge, 0px))',
        width: `${PANEL_WIDTH}px`,
        height: '1024px',
        boxShadow: 'inset 1px 0 0 rgba(255,255,255,.14),-30px 0 60px rgba(10,4,20,.35)',
        animation,
      }}
    >
      {children}
    </div>
  )
}
