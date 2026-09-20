import { useState, type CSSProperties } from 'react'

import { NODE_SIZE } from '@/lib/canvasWorkflow'

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

/** 선을 잇는 자리. */
const PORT_DOT = 12
const PORT_HIT = 24

const PORT_BASE: CSSProperties = {
  position: 'absolute',
  width: `${PORT_HIT}px`,
  height: `${PORT_HIT}px`,
  top: `${NODE_SIZE / 2 - PORT_HIT / 2}px`,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  cursor: 'crosshair',
}

const MINE_RING =
  'inset 0 0 0 1.5px rgba(255,190,220,.55), 0 0 0 5px rgba(255,190,220,.07), inset 0 1px 0 rgba(255,255,255,.2)'
const PLAIN_RING = 'inset 0 1px 0 rgba(255,255,255,.12)'

/** 모드가 달라도 카드 모양은 같다. 커서와 눌림 표시만 다르다. */
const CARD_BASE = 'absolute flex flex-col items-center justify-center rounded-full'
const CARD_MODE = {
  dragging: ' cursor-grabbing select-none',
  draggable: ' cursor-grab select-none',
  clickable: ' cursor-pointer hover:-translate-y-[5px]',
  plain: '',
} as const

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
  /** 등장 모션. 없으면 생략 */
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
  /** 브라우저가 손짓을 가져갔을 때. 받지 않으면 끌던 상태가 그대로 갇힌다. */
  onPointerCancel?: (ev: React.PointerEvent<HTMLDivElement>) => void
  onHover?: (index: number) => void
  onHoverOut?: () => void
  /** 주면 좌우에 연결 점이 생긴다. */
  onPortDown?: (side: 'in' | 'out', ev: React.PointerEvent<HTMLDivElement>) => void
  /** 선을 놓은 자리의 노드를 화면에서 되찾을 때. */
  nodeId?: string
  /** 참이면 그 포트를 흐리게 둔다. */
  portDim?: (side: 'in' | 'out') => boolean
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
  onPointerCancel,
  onHover,
  onHoverOut,
  onPortDown,
  nodeId,
  portDim,
}: CanvasNodeProps) {
  const clickable = !!onSelect
  const draggable = !!onPointerDown
  const mode = dragging ? 'dragging' : draggable ? 'draggable' : clickable ? 'clickable' : 'plain'

  // 한 번이라도 이동했으면 등장 모션 제외. 되돌릴 때 다시 재생되어 카드가 사라졌다 나타난다.
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
      data-node={nodeId}
      title={title}
      onClick={clickable ? () => onSelect(index) : undefined}
      onMouseEnter={onHover ? () => onHover(index) : undefined}
      onMouseLeave={onHoverOut}
      onPointerDown={handlePointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerCancel}
      style={{
        left: `${x}px`,
        top: `${y}px`,
        width: `${NODE_SIZE}px`,
        height: `${NODE_SIZE}px`,
        background,
        gap: '5px',
        boxShadow: mine ? MINE_RING : PLAIN_RING,
        // 끌 수 있는 카드는 터치를 브라우저에 넘기지 않는다 — 넘기면 팬으로
        // 가로채여 pointercancel 이 나고 드래그가 중간에 끊긴다.
        touchAction: draggable ? 'none' : undefined,
        // floaty 가 인라인 transform 보다 우선이라 드래그 중에는 꺼 둔다.
        animation: dragging ? undefined : motion,
        // floaty 를 끄면 레이어 승격도 풀려 링과 글씨가 늦게 그려진다.
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
      className={CARD_BASE + CARD_MODE[mode]}
    >
      {onPortDown &&
        (['in', 'out'] as const).map((side) => (
          <div
            key={side}
            data-port={side}
            style={{
              ...PORT_BASE,
              [side === 'in' ? 'left' : 'right']: `${-PORT_HIT / 2}px`,
            }}
            onPointerDown={(ev) => onPortDown(side, ev)}
          >
            <div
              className="rounded-full"
              style={{
                width: `${PORT_DOT}px`,
                height: `${PORT_DOT}px`,
                background: 'rgba(255,224,238,.9)',
                boxShadow: '0 0 0 3px rgba(40,20,40,.55), 0 0 10px rgba(255,190,220,.5)',
                pointerEvents: 'none',
                opacity: portDim?.(side) ? 0.22 : 1,
                transition: 'opacity .18s',
              }}
            />
          </div>
        ))}
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
