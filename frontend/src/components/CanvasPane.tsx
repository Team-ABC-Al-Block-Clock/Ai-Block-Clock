import type { DragEvent, PointerEvent, ReactNode, Ref } from 'react'

/** 작업면 div 의 설계 폭. 창 좌표를 설계 좌표로 되돌릴 때 배율의 기준이 된다. */
export const PANE_WIDTH = 1076
/** 작업면 div 의 설계 높이. 창이 더 길면 `--edge-y` 로 넘겨 채운다. */
export const PANE_HEIGHT = 1024

/** 패널이 작업면을 덮기 시작하는 자리. 오른쪽 끝에서 이만큼 안쪽이다. */
const PANEL_EDGE = 'calc(96px - var(--edge, 0px))'
/**
 * 패널 뒤로 넘어간 만큼만 가리는 클립.
 *
 * 패널은 칠하지 않아 뒤가 비친다. 오른쪽만 패널 앞에서 자르고 나머지 세 방향은
 * 넉넉히 열어 둔다 — 카드가 작업면 끝에 있어도 거기서 나가는 선이 잘리면 안 된다.
 */
export const PANEL_CLIP = `inset(-200% ${PANEL_EDGE} -200% -200%)`

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
    right: PANEL_EDGE,
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
      {/*
        배경은 작업면 상자 밖으로 넘치는데 드롭 판정은 상자에만 걸려 있었다 — 격자가
        보이는 띠에 팔레트 카드를 놓으면 아무 일도 일어나지 않았다. 배경과 같은 범위로
        받아 주는 판을 깐다. 여기서 받은 것은 위로 올리지 않는다 — 상자에도 같은 처리가
        걸려 있어 그대로 두면 두 번 놓인다.
      */}
      <div
        className="absolute"
        style={bleed}
        onDragOver={onDragOver}
        onDrop={
          onDrop &&
          ((ev) => {
            ev.stopPropagation()
            onDrop(ev)
          })
        }
      ></div>
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
