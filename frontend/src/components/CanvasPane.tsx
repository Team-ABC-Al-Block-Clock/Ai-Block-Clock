import type { DragEvent, PointerEvent, ReactNode, Ref } from 'react'

/** 작업면 div 의 설계 폭. 창 좌표를 설계 좌표로 되돌릴 때 배율의 기준이 된다. */
export const PANE_WIDTH = 1076
/** 작업면 div 의 설계 높이. 창이 더 길면 `--edge-y` 로 넘겨 채운다. */
export const PANE_HEIGHT = 1024

type CanvasPaneProps = {
  children: ReactNode
  /** 창 좌표를 설계 좌표로 되돌리려면 실제로 그려진 크기를 재야 한다. */
  ref?: Ref<HTMLDivElement>
  onPointerDown?: (ev: PointerEvent<HTMLDivElement>) => void
  onDragOver?: (ev: DragEvent<HTMLDivElement>) => void
  onDrop?: (ev: DragEvent<HTMLDivElement>) => void
}

/**
 * 편집·대기·실행 화면이 워크플로우를 그리는 작업면.
 *
 * 설계 좌표 위에 그리는 고정 크기 상자다. 배경 두 겹은 창이 설계 비율보다
 * 크면 `--edge`·`--edge-y` 만큼 밖으로 넘겨 빈 자리가 보이지 않게 한다 —
 * 오른쪽만 패널 폭(96px)을 빼고 멈춘다.
 */
export default function CanvasPane({
  children,
  ref,
  onPointerDown,
  onDragOver,
  onDrop,
}: CanvasPaneProps) {
  const bleed = {
    left: 'calc(-1 * var(--edge, 0px))',
    right: 'calc(96px - var(--edge, 0px))',
    top: 'calc(-1 * var(--edge-y, 0px))',
    bottom: 'calc(-1 * var(--edge-y, 0px))',
  }
  return (
    <div
      ref={ref}
      className="absolute top-0 left-0"
      onPointerDown={onPointerDown}
      onDragOver={onDragOver}
      onDrop={onDrop}
      style={{
        width: `${PANE_WIDTH}px`,
        height: `${PANE_HEIGHT}px`,
        animation: 'paneIn 1.1s cubic-bezier(.18,.86,.24,1) both',
        transformOrigin: '44% 46%',
      }}
    >
      <div
        className="pointer-events-none absolute"
        style={{
          ...bleed,
          backgroundImage: 'radial-gradient(rgba(255,255,255,.10) 1px, transparent 1px)',
          backgroundSize: '26px 26px',
          backgroundPosition: '13px 13px',
          opacity: '.5',
        }}
      ></div>
      <div
        className="pointer-events-none absolute"
        style={{
          ...bleed,
          background:
            'linear-gradient(90deg, rgba(10,5,22,0) 30%, rgba(10,5,22,.26) 70%, rgba(10,5,22,.32) 100%)',
        }}
      ></div>

      {children}
    </div>
  )
}
