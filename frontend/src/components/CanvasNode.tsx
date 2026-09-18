import { useState, type CSSProperties } from 'react'

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
  /** 계속 도는 모션 */
  floaty: string
  /** 등장 모션. 없으면 생략한다 */
  enter?: string
  background: string
  numberColor?: string
  nameColor?: string
  sublabelColor?: string
  /** 실행 화면의 유리 재질 */
  glass?: boolean
  title?: string
  /** 드래그 중. 모션과 호버를 멈춰 손끝을 따라오게 한다. */
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
  floaty,
  enter,
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

  // 등장 모션이 끝났는지 확인. 끝난 뒤에는 제거해야 드래그를 마치고 animation 을 되돌릴 때
  // uiIn 이 처음부터 다시 돌아 카드가 사라졌다가 나타난다.
  // 한 번이라도 끌었으면 등장 모션을 다시 주지 않는다. 드래그를 마치고 animation 을 되돌릴 때
  // uiIn 이 처음부터 다시 돌아 카드가 사라졌다 나타난다.
  const [dragged, setDragged] = useState(false)
  const motion = dragged || !enter ? floaty : `${floaty}, ${enter}`

  const handlePointerDown = onPointerDown
    ? (ev: React.PointerEvent<HTMLDivElement>) => {
        setDragged(true)
        onPointerDown(ev)
      }
    : undefined

  return (
    <div
      title={title}
      onClick={clickable ? () => onSelect(index) : undefined}
      onMouseEnter={onHover ? () => onHover(index) : undefined}
      onMouseLeave={onHoverOut}
      onPointerDown={handlePointerDown}
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
        // floaty 가 인라인 transform 보다 우선 순위가 높으므로 드래그 중에는 꺼야 한다.
        animation: dragging ? undefined : motion,
        // floaty 를 끄면 레이어 승격도 풀려 이어받지 않으면 링과 글씨가 늦게 그려진다.
        willChange: dragging ? 'transform' : undefined,
        zIndex: dragging ? 5 : undefined,
        ...(glass
          ? {
              backdropFilter: 'blur(18px)',
              WebkitBackdropFilter: 'blur(18px)',
              transition: 'background .5s,box-shadow .5s',
            }
          : // 드래그 중 transform 에 전환이 걸리면 0.2초 늦게 따라온다.
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
