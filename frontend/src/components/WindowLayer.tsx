import type { ReactNode } from 'react'
import { createPortal } from 'react-dom'

import { useWindowBox } from '@/hooks/useWindowBox'

/** 껍데기(헤더 · Studio · 메뉴)가 zIndex 40 이다. 팝업은 그보다 위에 둔다. */
export const WINDOW_LAYER_Z = 50

/**
 * 팝업을 띄우는 창 전체 레이어. 껍데기와 같은 크기 · 배율이라 안에서는 설계 좌표를 그대로 쓴다.
 *
 * 화면 틀(`screen-frame`) 안에 두면 껍데기가 위에 남아 팝업을 연 채로 헤더 · Studio 가 눌린다.
 * 레이어 자체는 클릭을 통과시킨다 — 닫힌 `<confirm-dialog>` 처럼 늘 붙어 있는 팝업이 화면을 막지 않는다.
 */
export default function WindowLayer({ children }: { children: ReactNode }) {
  const box = useWindowBox()
  return createPortal(
    <div
      className="pointer-events-none fixed top-0 left-0"
      style={{
        width: `${box.width}px`,
        height: `${box.height}px`,
        transform: `scale(${box.scale})`,
        transformOrigin: 'top left',
        zIndex: WINDOW_LAYER_Z,
      }}
    >
      <div className="pointer-events-auto contents">{children}</div>
    </div>,
    document.body,
  )
}
