import type { CSSProperties } from 'react'

/** 내 것에만 붙는 표시. 다른 사람의 것이 하나라도 섞이면 마켓에 등록할 수 없다. */
const MY_CHIP: CSSProperties = {
  position: 'absolute',
  left: '63px',
  top: '-11px',
  width: '42px',
  height: '22px',
  lineHeight: '22px',
  textAlign: 'center',
  fontSize: '12px',
  fontWeight: 700,
  letterSpacing: '-.005em',
  borderRadius: '11px',
  background: '#70506b',
  color: 'rgba(255,240,248,.95)',
  boxShadow: 'inset 0 0 0 1px #a0758f',
}

const MINE_RING =
  'inset 0 0 0 1.5px rgba(255,190,220,.55), 0 0 0 5px rgba(255,190,220,.07), inset 0 1px 0 rgba(255,255,255,.2)'
const PLAIN_RING = 'inset 0 1px 0 rgba(255,255,255,.12)'

export interface CanvasNodeProps {
  /** 0 부터. 원 안에는 +1 로 보인다 */
  index: number
  name: string
  /** 노드 유형(편집) 또는 진행 상태(실행) */
  sublabel: string
  x: number
  y: number
  mine: boolean
  /** floaty + uiIn 조합 */
  animation: string
  background: string
  numberColor?: string
  nameColor?: string
  sublabelColor?: string
  /** 실행 화면의 유리 재질 */
  glass?: boolean
  title?: string
  /** 끌어서 옮기는 중. 떠 있는 모션과 호버를 멈춰 손끝을 따라오게 한다. */
  dragging?: boolean
  onSelect?: (index: number) => void
  onPointerDown?: (ev: React.PointerEvent<HTMLDivElement>) => void
  onPointerMove?: (ev: React.PointerEvent<HTMLDivElement>) => void
  onPointerUp?: (ev: React.PointerEvent<HTMLDivElement>) => void
  onHover?: (index: number) => void
  onHoverOut?: () => void
}

export default function CanvasNode({
  index,
  name,
  sublabel,
  x,
  y,
  mine,
  animation,
  background,
  numberColor = '#fff',
  nameColor = 'rgba(255,255,255,.9)',
  sublabelColor = 'rgba(255,236,246,.66)',
  glass = false,
  title,
  dragging = false,
  onSelect,
  onPointerDown,
  onPointerMove,
  onPointerUp,
  onHover,
  onHoverOut,
}: CanvasNodeProps) {
  const clickable = !!onSelect
  const draggable = !!onPointerDown

  return (
    <div
      title={title}
      onClick={clickable ? () => onSelect(index) : undefined}
      onMouseEnter={onHover ? () => onHover(index) : undefined}
      onMouseLeave={onHoverOut}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      style={{
        left: `${x}px`,
        top: `${y}px`,
        width: '168px',
        height: '168px',
        background,
        gap: '5px',
        boxShadow: mine ? MINE_RING : PLAIN_RING,
        // 끄는 동안 floaty 가 돌면 카드가 손끝에서 위아래로 흔들린다.
        animation: dragging ? undefined : animation,
        zIndex: dragging ? 5 : undefined,
        ...(glass
          ? {
              backdropFilter: 'blur(18px)',
              WebkitBackdropFilter: 'blur(18px)',
              transition: 'background .5s,box-shadow .5s',
            }
          : // 끄는 동안 transform 에 전환이 걸리면 카드가 0.2초 늦게 따라온다.
            { transition: dragging ? 'none' : 'transform .2s,box-shadow .2s' }),
      }}
      className={
        dragging
          ? 'absolute flex cursor-grabbing flex-col items-center justify-center rounded-full select-none'
          : draggable
            ? 'absolute flex cursor-grab flex-col items-center justify-center rounded-full select-none hover:-translate-y-[5px]'
            : clickable
              ? 'absolute flex cursor-pointer flex-col items-center justify-center rounded-full hover:-translate-y-[5px]'
              : 'absolute flex flex-col items-center justify-center rounded-full'
      }
    >
      {mine && <div style={MY_CHIP}>MY</div>}
      <div
        className="text-[14px] font-semibold"
        style={{ color: numberColor, lineHeight: '1', opacity: 0.72, letterSpacing: '.04em' }}
      >
        {index + 1}
      </div>
      <div
        className="text-[21px] font-bold tracking-[-.01em]"
        style={{ color: nameColor, textShadow: '0 2px 12px rgba(8,4,18,.55)' }}
      >
        {name}
      </div>
      <div
        className="text-[13.5px] font-medium"
        style={{ color: sublabelColor, letterSpacing: '.05em' }}
      >
        {sublabel}
      </div>
    </div>
  )
}
