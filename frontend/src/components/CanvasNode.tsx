import { useState, type CSSProperties } from 'react'

import type { NodePort } from '@/lib/canvasReducer'
import { NODE_SIZE, portOffsetY } from '@/lib/canvasWorkflow'

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

/**
 * 이름이 카드를 넘지 않게 가두는 규칙.
 *
 * 폭을 주지 않으면 글자가 원 밖으로 흘러나온다 — 'Market Predictor' 처럼 띄어쓰기가
 * 있어도 접을 자리를 모르기 때문이다. 두 줄까지 접고, 그래도 넘치면 말줄임한다.
 *
 * `keep-all` 이 없으면 한국어가 글자 단위로 끊겨 '성적 분석 리포/트' 가 된다. 어절에서만
 * 접고, 한 어절이 줄보다 길 때만 `anywhere` 가 중간을 끊는다.
 */
const NAME_FIT: CSSProperties = {
  maxWidth: `${NODE_SIZE - 30}px`,
  textAlign: 'center',
  lineHeight: 1.15,
  wordBreak: 'keep-all',
  overflowWrap: 'anywhere',
  display: '-webkit-box',
  WebkitBoxOrient: 'vertical',
  WebkitLineClamp: 2,
  overflow: 'hidden',
}

/** 부제는 한 줄이다. 분기의 조건 요약처럼 길어지면 카드를 넘지 않게 자른다. */
const SUBLABEL_FIT: CSSProperties = {
  maxWidth: `${NODE_SIZE - 30}px`,
  whiteSpace: 'nowrap',
  overflow: 'hidden',
  textOverflow: 'ellipsis',
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

/**
 * 카드 모양. 자리는 어느 모양이든 `NODE_SIZE` 정사각이다.
 *
 * 네모를 그대로 둬야 선 잇는 자리(`portPoint`)와 선이 피해 도는 길(`edgeRoute`)이 모양마다
 * 달라지지 않는다. 안에 그리는 것만 바꾼다.
 *
 * `circle` 은 일하는 노드, `pill` 은 시작·끝, `hex` 는 분기다.
 */
export type NodeShape = 'circle' | 'pill' | 'hex'

/** 알약과 육각의 좌우 변이 서는 구간. 포트가 그 위에 얹힌다. */
const FLAT_TOP = 46
const FLAT_BOTTOM = NODE_SIZE - FLAT_TOP

/**
 * 알약이 네모 폭에서 물러서는 거리.
 *
 * 폭을 꽉 채우면 알약끼리 `NODE_GAP` 만큼만 떨어져 맞닿아 보인다 — 원은 좌우가 곡선이라
 * 같은 간격에도 더 떨어져 보였다. 포트는 네모 변에 그대로 두고 카드만 들인다.
 */
const PILL_INSET = 8

/** 육각이 위아래로 물러서는 거리. 조건 요약이 한 줄 들어갈 만큼은 남긴다. */
const HEX_INSET = 14

/** 위아래가 뾰족하고 좌우가 평평한 육각. 테두리가 잘리지 않게 1px 더 들인다. */
const HEX_POINTS = [
  [NODE_SIZE / 2, HEX_INSET],
  [NODE_SIZE - 1, FLAT_TOP],
  [NODE_SIZE - 1, FLAT_BOTTOM],
  [NODE_SIZE / 2, NODE_SIZE - HEX_INSET],
  [1, FLAT_BOTTOM],
  [1, FLAT_TOP],
]
  .map(([x, y]) => `${x},${y}`)
  .join(' ')

/**
 * 제어·입출력 카드의 테두리.
 *
 * 원형 카드는 `mine` 으로 링이 갈리지만 이쪽은 주인이 없다. 알약과 육각이 같은 한 줄을
 * 두르게 값을 한곳에 둔다 — 육각은 SVG 라 `boxShadow` 가 안 먹어 선으로 긋는다.
 */
const SHAPE_STROKE = 'rgba(214,196,255,.42)'
const SHAPE_RING = `inset 0 0 0 1.5px ${SHAPE_STROKE}`

/**
 * 글자를 받치는 그림자.
 *
 * 원형 카드는 흰 반투명이라 넓게 퍼진 그림자가 어울리지만, 제어·입출력은 채운 라일락 위라
 * 같은 그림자가 글자 가장자리를 뭉갠다. 좁고 짙게 받쳐 획을 세운다.
 */
const TEXT_SHADOW = {
  circle: '0 2px 12px rgba(8,4,18,.55)',
  filled: '0 1px 3px rgba(10,5,22,.92)',
}

const BOTH_PORTS: readonly NodePort[] = [{ side: 'in' }, { side: 'out' }]

/**
 * 바닥 위에 얹히는 글자.
 *
 * 바닥은 `position: absolute` 라 static 인 글자보다 나중에 칠해진다 — 그냥 두면 채운 카드가
 * 제 글자를 덮는다. 글자도 자리를 잡아 주면 둘 다 같은 차례에 들어가 DOM 순서대로 쌓인다.
 */
const OVER_BACKDROP: CSSProperties = { position: 'relative' }

/** 카드 바닥. 모양은 여기서만 갈린다 — 바깥 네모와 포트 자리는 그대로다. */
function Backdrop({
  shape,
  background,
  ring,
  glass,
}: {
  shape: NodeShape
  background: string
  ring: string
  glass: boolean
}) {
  if (shape === 'hex') {
    // 깎인 모서리는 `boxShadow` 로 두를 수 없다. 면과 테두리를 한 번에 그린다.
    return (
      <svg
        viewBox={`0 0 ${NODE_SIZE} ${NODE_SIZE}`}
        className="pointer-events-none absolute inset-0"
      >
        <polygon points={HEX_POINTS} fill={background} stroke={SHAPE_STROKE} strokeWidth="1.5" />
      </svg>
    )
  }

  return (
    <div
      className="pointer-events-none absolute rounded-full"
      style={{
        left: shape === 'pill' ? `${PILL_INSET}px` : 0,
        right: shape === 'pill' ? `${PILL_INSET}px` : 0,
        top: shape === 'pill' ? `${FLAT_TOP}px` : 0,
        bottom: shape === 'pill' ? `${FLAT_TOP}px` : 0,
        background,
        boxShadow: shape === 'pill' ? SHAPE_RING : ring,
        transition: glass ? 'background .5s,box-shadow .5s' : 'box-shadow .2s',
        ...(glass ? { backdropFilter: 'blur(18px)', WebkitBackdropFilter: 'blur(18px)' } : {}),
      }}
    />
  )
}

/**
 * 모드가 달라도 카드 모양은 같다. 커서와 눌림 표시만 다르다.
 *
 * 호버 리프트는 `translate` 라 `floaty` 의 `transform` 과 합성된다 — 겹쳐도 서로 지우지 않는다.
 * 끄는 동안만 뺀다. 손끝을 따라가는 중에 5px 이 얹히면 커서와 어긋난다.
 */
const CARD_BASE = 'absolute flex flex-col items-center justify-center'
const LIFT = ' hover:-translate-y-[5px]'
const CARD_MODE = {
  dragging: ' cursor-grabbing select-none',
  draggable: ` cursor-grab select-none${LIFT}`,
  clickable: ` cursor-pointer${LIFT}`,
  plain: '',
} as const

export interface CanvasNodeProps {
  /** 0 부터. 원 안에는 +1 로 보인다. 차례를 정할 수 없으면(갈래) 비워 두고 감춘다 */
  index?: number
  name: string
  /** 노드 유형(편집) 또는 진행 상태(실행) */
  sublabel: string
  x: number
  y: number
  /** 참이면 MY 칩과 테두리. 판정 대상이 아닌 자리는 null */
  mine: boolean | null
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
  /** 카드 모양. 기본은 원 */
  shape?: NodeShape
  /** 그릴 포트. 시작 노드는 출력만, 종료 노드는 입력만, 분기는 오른쪽에 둘을 낸다 */
  ports?: readonly NodePort[]
  title?: string
  /** 드래그 중. 모션과 호버를 멈춰 손끝을 따라오게 한다. */
  dragging?: boolean
  onSelect?: () => void
  onPointerDown?: (ev: React.PointerEvent<HTMLDivElement>) => void
  onPointerMove?: (ev: React.PointerEvent<HTMLDivElement>) => void
  onPointerUp?: (ev: React.PointerEvent<HTMLDivElement>) => void
  /** 브라우저가 손짓을 가져갔을 때. 받지 않으면 끌던 상태가 그대로 갇힌다. */
  onPointerCancel?: (ev: React.PointerEvent<HTMLDivElement>) => void
  onHover?: () => void
  onHoverOut?: () => void
  /** 주면 좌우에 연결 점이 생긴다. */
  onPortDown?: (port: NodePort, ev: React.PointerEvent<HTMLDivElement>) => void
  /** 선을 놓은 자리의 노드를 화면에서 되찾을 때. */
  nodeId?: string
  /** 참이면 그 포트를 흐리게 둔다. */
  portDim?: (port: NodePort) => boolean
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
  shape = 'circle',
  ports = BOTH_PORTS,
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
      onClick={clickable ? () => onSelect() : undefined}
      onMouseEnter={onHover ? () => onHover() : undefined}
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
        gap: '5px',
        // 판정을 끈 판 안에 들어가도 카드는 눌려야 한다.
        pointerEvents: 'auto',
        // 끌 수 있는 카드는 터치를 브라우저에 넘기지 않는다 — 넘기면 팬으로
        // 가로채여 pointercancel 이 나고 드래그가 중간에 끊긴다.
        touchAction: draggable ? 'none' : undefined,
        // floaty 가 인라인 transform 보다 우선이라 드래그 중에는 꺼 둔다.
        animation: dragging ? undefined : motion,
        // floaty 를 끄면 레이어 승격도 풀려 링과 글씨가 늦게 그려진다.
        willChange: dragging ? 'transform' : undefined,
        zIndex: dragging ? 5 : undefined,
        // 드래그 중 transform 에 전환이 걸리면 0.2초 늦게 따라온다.
        transition: dragging ? 'none' : 'translate .2s,transform .2s',
      }}
      className={CARD_BASE + CARD_MODE[mode]}
    >
      <Backdrop
        shape={shape}
        background={background}
        ring={mine ? MINE_RING : PLAIN_RING}
        glass={glass}
      />
      {onPortDown &&
        ports.map((port) => (
          <div
            key={`${port.side}${port.when ?? ''}`}
            data-port={port.side}
            data-when={port.when === undefined ? undefined : String(port.when)}
            style={{
              ...PORT_BASE,
              top: `${NODE_SIZE / 2 - PORT_HIT / 2 + portOffsetY(port.when)}px`,
              [port.side === 'in' ? 'left' : 'right']: `${-PORT_HIT / 2}px`,
            }}
            onPointerDown={(ev) => onPortDown(port, ev)}
          >
            {port.when !== undefined && (
              <div
                className="text-[11.5px] font-semibold whitespace-nowrap"
                style={{
                  position: 'absolute',
                  // 카드 안쪽, 점 바로 옆. 선이 나가는 자리를 가리지 않는다.
                  right: '100%',
                  color: 'rgba(255,255,255,.8)',
                  letterSpacing: '.02em',
                  pointerEvents: 'none',
                }}
              >
                {port.when ? 'true' : 'false'}
              </div>
            )}
            <div
              className="rounded-full"
              style={{
                width: `${PORT_DOT}px`,
                height: `${PORT_DOT}px`,
                background: 'rgba(255,224,238,.9)',
                boxShadow: '0 0 0 3px rgba(40,20,40,.55), 0 0 10px rgba(255,190,220,.5)',
                pointerEvents: 'none',
                opacity: portDim?.(port) ? 0.22 : 1,
                transition: 'opacity .18s',
              }}
            />
          </div>
        ))}
      {mine && <div style={MY_CHIP}>MY</div>}
      {/* 번호가 사라져도 자리는 남긴다 — 빼 버리면 아래 이름과 유형이 위로 밀려 카드가 들썩인다.
          제어·입출력은 차례를 갖지 않아 자리째 없앤다. 알약 높이에 세 줄은 들어가지 않는다. */}
      {shape === 'circle' && (
        <div
          className="text-[14px] font-semibold"
          style={{
            ...OVER_BACKDROP,
            color: numberColor,
            lineHeight: '1',
            opacity: 0.72,
            letterSpacing: '.04em',
            visibility: index === undefined ? 'hidden' : undefined,
          }}
        >
          {(index ?? 0) + 1}
        </div>
      )}
      <div
        className="text-[21px] font-bold tracking-[-.01em]"
        style={{
          ...OVER_BACKDROP,
          color: nameColor,
          textShadow: shape === 'circle' ? TEXT_SHADOW.circle : TEXT_SHADOW.filled,
          ...NAME_FIT,
        }}
      >
        {name}
      </div>
      {sublabel && (
        <div
          className="text-[13.5px] font-medium"
          style={{
            ...OVER_BACKDROP,
            ...SUBLABEL_FIT,
            color: sublabelColor,
            letterSpacing: '.05em',
          }}
        >
          {sublabel}
        </div>
      )}
    </div>
  )
}
