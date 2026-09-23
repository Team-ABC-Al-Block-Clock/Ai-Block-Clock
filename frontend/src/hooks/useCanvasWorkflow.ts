import { useEffect, useMemo, useReducer, useRef, useState } from 'react'

import { PANE_CONTENT_WIDTH, PANE_HEIGHT, PANE_WIDTH } from '@/components/CanvasPane'
import {
  canvasReducer,
  initialCanvasState,
  type PlacedSeed,
  type PortSide,
} from '@/lib/canvasReducer'
import {
  NODE_SIZE,
  canConnect,
  canSellWorkflow,
  edgeGeometry,
  edgePath,
  portPoint,
  resolveOverlap,
} from '@/lib/canvasWorkflow'
import {
  INITIAL_VIEWPORT,
  gridPosition,
  gridSize,
  panBy,
  viewportTransform,
  wheelZoom,
  zoomPercent,
  zoomStep,
  type Viewport,
} from '@/lib/viewport'

/** 이 거리보다 움직였으면 선택이 아니라 이동으로 본다. */
const DRAG_SLOP = 4
/** 겹친 자리에 놓았을 때 빈자리로 미끄러지는 시간(ms). */
const SETTLE_MS = 160

/** 끌고 있는 카드에 대해 포인터가 움직이는 동안 알아야 하는 것. 리렌더가 필요 없어 ref 에 둔다. */
interface Grab {
  id: string
  /** 잡은 지점과 카드 왼쪽 위의 차이 */
  dx: number
  dy: number
  /** 누른 창 좌표. 선택과 이동의 기준 */
  ox: number
  oy: number
  el: HTMLDivElement
  baseX: number
  baseY: number
  /** 선택 빛이 지금 이 카드를 따라다니는가 */
  glow: boolean
}

/** 줌 툴바·이름 입력·실행 버튼이 작업면 안에 있다. 조상까지 올려보면 그 위에서도
 *  팬이 시작돼 포인터를 캡처하고, 정작 그 버튼의 click 이 오지 않는다. */
function isPaneBackground(target: EventTarget | null, pane: Element): boolean {
  if (!(target instanceof Element)) return false
  return target === pane || target.hasAttribute('data-canvas-bg')
}

/** 노드 추가 팝업처럼 스스로 굴러가는 것 위에서는 줌을 비켜 간다. */
function overScrollable(target: EventTarget | null, pane: Element): boolean {
  let node = target instanceof Element ? target : null
  while (node && node !== pane) {
    const { overflowY } = getComputedStyle(node)
    if ((overflowY === 'auto' || overflowY === 'scroll') && node.scrollHeight > node.clientHeight) {
      return true
    }
    node = node.parentElement
  }
  return false
}

/** Firefox 는 줄 단위로 ±3 쯤을 준다. 그대로 쓰면 한 칸에 0.5% 도 안 움직인다. */
function wheelPixels(ev: WheelEvent): number {
  if (ev.deltaMode === 1) return ev.deltaY * 16
  if (ev.deltaMode === 2) return ev.deltaY * window.innerHeight
  return ev.deltaY
}

interface Pan {
  ox: number
  oy: number
  from: Viewport
  dx: number
  dy: number
  /** 끌었나. 아니면 빈 곳 클릭이라 선택만 푼다 */
  moved: boolean
}

/**
 * 캔버스의 상태와 손짓을 한데 묶는다.
 *
 * 상태 전이는 전부 `canvasReducer` 가 한다. 여기는 화면을 재고(`toCanvasPoint`),
 * 포인터를 따라 DOM 을 직접 고치고(드래그 중 리렌더를 피한다), 언제 어떤 액션을
 * 낼지만 정한다. 좌표 변환은 `lib/viewport` 한 곳에 모여 있다.
 */
export function useCanvasWorkflow() {
  const [state, dispatch] = useReducer(canvasReducer, initialCanvasState)
  const { nodes, edges, selId, hovId, selEdge, dragId, linkFrom } = state
  const [view, setView] = useState<Viewport>(INITIAL_VIEWPORT)

  /** 화면에 고정된 상자. 배율을 재는 기준이다. */
  const paneRef = useRef<HTMLDivElement>(null)
  /** 팬·줌이 걸린 판. 노드 좌표는 이 안의 좌표다. */
  const worldRef = useRef<HTMLDivElement>(null)
  const gridRef = useRef<HTMLDivElement>(null)
  const glowRef = useRef<HTMLDivElement>(null)
  const tempRef = useRef<SVGPathElement>(null)

  const grab = useRef<Grab | null>(null)
  const pan = useRef<Pan | null>(null)
  /** 드래그가 끝난 자리. 손을 뗄 때 한 번만 상태로 옮긴다. */
  const landing = useRef<{ x: number; y: number } | null>(null)
  const settleTimer = useRef<ReturnType<typeof setTimeout>>(undefined)
  /**
   * 미끄러짐이 끝나면 할 일. 그 전에 다시 잡으면 기다리지 않고 지금 끝낸다.
   *
   * 확정한 자리를 돌려준다 — `dispatch` 로 넣은 값은 다음 렌더에야 보여서,
   * 방금 끝낸 미끄러짐을 이어 잡을 때는 이 값이 상태보다 최신이다.
   */
  const settle = useRef<(() => { id: string; x: number; y: number }) | null>(null)
  /** 이번 손짓이 이동이었는가. 이동이면 뒤따르는 click 에서 선택을 뒤집지 않는다. */
  const moved = useRef(false)
  /** 누를 때 이미 선택돼 있었는지. 그때만 click 에서 선택을 푼다. */
  const reselect = useRef(false)
  const placedCount = useRef(0)

  const flushSettle = () => {
    clearTimeout(settleTimer.current)
    const pending = settle.current
    settle.current = null
    return pending?.() ?? null
  }
  useEffect(() => () => clearTimeout(settleTimer.current), [])

  /** 창 좌표 → 설계 좌표. 판을 재면 창맞춤 배율과 팬·줌이 이미 반영돼 있다. */
  const toCanvasPoint = (clientX: number, clientY: number) => {
    const rect = worldRef.current?.getBoundingClientRect()
    if (!rect || rect.width === 0) return null
    const scale = rect.width / PANE_WIDTH
    return { x: (clientX - rect.left) / scale, y: (clientY - rect.top) / scale }
  }

  /** 창맞춤 배율만. `tx`·`ty` 는 판 바깥 좌표라 줌을 타지 않는다. */
  const fitScale = () => {
    const rect = paneRef.current?.getBoundingClientRect()
    if (!rect || rect.width === 0) return null
    return rect.width / PANE_WIDTH
  }

  /** 판과 격자가 한 프레임에 같이 움직여야 배경이 따로 놀지 않는다. */
  const paintViewport = (next: Viewport) => {
    if (worldRef.current) worldRef.current.style.transform = viewportTransform(next)
    const grid = gridRef.current
    if (grid) {
      grid.style.backgroundPosition = gridPosition(next)
      grid.style.backgroundSize = gridSize(next)
    }
  }

  /** 드래그 중 직접 써 넣은 선은 React 가 모른다. 좌표가 정해질 때마다 여기서 고친다. */
  const paintEdges = (at: { id: string; x: number; y: number }, only = edges) => {
    const preview = nodes.map((node) => (node.id === at.id ? { ...node, x: at.x, y: at.y } : node))
    for (const edge of edgeGeometry(preview, only)) {
      worldRef.current
        ?.querySelectorAll(`[data-edge="${edge.id}"]`)
        .forEach((path) => path.setAttribute('d', edge.d))
    }
  }

  // 옮기고 손을 뗀 직후에 click 이 한 번 더 온다. 그때 선택이 뒤집히지 않게 막는다.
  const handleSelect = () => {
    if (moved.current) {
      moved.current = false
      return
    }
    if (reselect.current) dispatch({ type: 'releaseSelection' })
  }

  /** 포인터를 카드에 묶는다. 캡처하지 않으면 커서가 카드를 벗어날 때 움직임을 놓친다. */
  const handleNodePointerDown = (id: string) => (ev: React.PointerEvent<HTMLDivElement>) => {
    if (ev.button !== 0) return
    // 미끄러지는 중에 다시 잡으면 상태에 없는 좌표를 기준으로 삼는다. 먼저 끝낸다.
    const flushed = flushSettle()
    const node = nodes.find((n) => n.id === id)
    const point = node && toCanvasPoint(ev.clientX, ev.clientY)
    if (!node || !point) return
    // 방금 끝낸 미끄러짐이 이 카드의 것이면 그 자리가 상태보다 최신이다.
    const at = flushed?.id === id ? flushed : node
    grab.current = {
      id,
      dx: point.x - at.x,
      dy: point.y - at.y,
      ox: ev.clientX,
      oy: ev.clientY,
      el: ev.currentTarget,
      baseX: at.x,
      baseY: at.y,
      glow: (hovId ?? selId) === id,
    }
    landing.current = { x: at.x, y: at.y }
    moved.current = false
    // 누르는 즉시 선택한다. 드래그한 뒤에도 선택된 채로 남는다.
    reselect.current = selId === id
    dispatch({ type: 'pressNode', id })
    // 캡처가 막혀도 카드 위에서는 따라오게 둔다.
    try {
      ev.currentTarget.setPointerCapture(ev.pointerId)
    } catch {
      /* 이미 놓인 포인터 */
    }
  }

  /**
   * 드래그 중에는 상태를 고치지 않는다 — `dispatch` 는 팔레트까지 한 화면을 다시 그린다.
   * 옮길 카드의 `transform` 만 직접 쓰고, 상태는 손을 뗄 때 한 번만 맞춘다.
   */
  const handleNodePointerMove = (ev: React.PointerEvent<HTMLDivElement>) => {
    const held = grab.current
    const point = held && toCanvasPoint(ev.clientX, ev.clientY)
    if (!held || !point) return
    // 손이 떨린 정도로는 선택을 막지 않는다.
    const slid = Math.abs(ev.clientX - held.ox) + Math.abs(ev.clientY - held.oy)
    if (slid > DRAG_SLOP) moved.current = true

    // 겹침은 손을 뗄 때 한 번만. 캔버스는 끝이 없어 자르지 않는다.
    const next = { x: point.x - held.dx, y: point.y - held.dy }
    landing.current = next
    held.el.style.transform = `translate3d(${next.x - held.baseX}px, ${next.y - held.baseY}px, 0)`
    // 빛은 left·top 으로 민다 — transform 은 React 가 들고 있지 않아 지우면 되살아나지 않는다.
    if (held.glow && glowRef.current) {
      glowRef.current.style.left = `${next.x - 16}px`
      glowRef.current.style.top = `${next.y - 16}px`
    }
    // 드래그하는 노드에 붙은 것만 고치면 된다.
    const touched = edges.filter((edge) => edge.from === held.id || edge.to === held.id)
    paintEdges({ id: held.id, ...next }, touched)
  }

  const handleNodePointerUp = () => {
    const held = grab.current
    const raw = landing.current
    grab.current = null
    landing.current = null
    if (!held || !raw) {
      dispatch({ type: 'endDrag' })
      return
    }

    const settled = resolveOverlap(nodes, held.id, raw.x, raw.y)
    const offset = (p: { x: number; y: number }) =>
      `translate3d(${p.x - held.baseX}px, ${p.y - held.baseY}px, 0)`

    const commit = () => {
      held.el.getAnimations().forEach((a) => a.cancel())
      held.el.style.transform = ''
      held.el.style.left = `${settled.x}px`
      held.el.style.top = `${settled.y}px`
      // 재기 전에 리렌더가 끝나면 transition 이 살아난 뒤 transform 이 지워져 미끄러진다.
      void held.el.offsetHeight
      paintEdges({ id: held.id, ...settled })
      // 자리는 위에서 정했다. reducer 가 같은 계산을 한 번 더 하지 않는다.
      dispatch({ type: 'settleNode', id: held.id, x: settled.x, y: settled.y })
      return { id: held.id, ...settled }
    }

    if (settled.x === raw.x && settled.y === raw.y) {
      commit()
      return
    }
    // 겹친 자리에 놓으면 빈자리로 짧게 미끄러지게 한다.
    held.el.animate([{ transform: offset(raw) }, { transform: offset(settled) }], {
      duration: SETTLE_MS,
      easing: 'cubic-bezier(.2,.8,.25,1)',
      fill: 'both',
    })
    settle.current = commit
    settleTimer.current = setTimeout(flushSettle, SETTLE_MS)
  }

  /**
   * 브라우저가 손짓을 가져갔다 — 터치 팬이나 시스템 제스처.
   *
   * 손을 뗀 것으로 쳐서 카드를 지금 보이는 자리에 확정한다. 그냥 두면 `grab`
   * 이 남아 인라인 `transform` 이 지워지지 않는데, React 는 `transform` 을 들고
   * 있지 않아 리렌더로도 못 되돌린다 — 카드가 제자리와 어긋난 채 갇힌다.
   */
  const handlePointerCancel = () => {
    handleNodePointerUp()
    if (linkFrom) dispatch({ type: 'cancelLink' })
  }

  /** 포트를 누르면 노드가 아니라 선을 끈다. */
  const handlePortDown =
    (id: string) => (side: PortSide, ev: React.PointerEvent<HTMLDivElement>) => {
      if (ev.button !== 0) return
      // 막지 않으면 노드까지 같이 드래그된다.
      ev.stopPropagation()
      dispatch({ type: 'startLink', from: { id, side } })
      // 포트에서 손을 떼도 click 이 노드로 올라온다. 선택이 뒤집히지 않게 막는다.
      moved.current = true
      try {
        ev.currentTarget.setPointerCapture(ev.pointerId)
      } catch {
        /* 이미 놓인 포인터 */
      }
    }

  const handlePortMove = (ev: React.PointerEvent<HTMLDivElement>) => {
    const held = linkFrom
    const node = held && nodes.find((n) => n.id === held.id)
    const point = node && toCanvasPoint(ev.clientX, ev.clientY)
    if (!held || !node || !point) return
    const anchor = portPoint(node, held.side)
    const d = held.side === 'out' ? edgePath(anchor, point) : edgePath(point, anchor)
    tempRef.current?.setAttribute('d', d)
  }

  const handlePortUp = (ev: React.PointerEvent<HTMLDivElement>) => {
    if (!linkFrom) return
    // 손을 뗀 자리의 포트는 화면에 물어본다.
    const hit = document.elementFromPoint(ev.clientX, ev.clientY)?.closest('[data-port]')
    const side = hit?.getAttribute('data-port')
    const hitId = hit?.closest('[data-node]')?.getAttribute('data-node')
    const target = nodes.find((n) => n.id === hitId)
    if (!target || (side !== 'in' && side !== 'out')) {
      dispatch({ type: 'cancelLink' })
      return
    }
    dispatch({ type: 'endLink', targetId: target.id, side })
  }

  /** 끌면 화면 이동, 안 끌고 떼면 선택 해제. 손을 떼는 순간 가른다. */
  const handlePaneDown = (ev: React.PointerEvent<HTMLDivElement>) => {
    if (ev.button !== 0) return
    if (!isPaneBackground(ev.target, ev.currentTarget)) return
    pan.current = { ox: ev.clientX, oy: ev.clientY, from: view, dx: 0, dy: 0, moved: false }
    try {
      ev.currentTarget.setPointerCapture(ev.pointerId)
    } catch {
      /* 이미 놓인 포인터 */
    }
  }

  const handlePaneMove = (ev: React.PointerEvent<HTMLDivElement>) => {
    const held = pan.current
    const fit = fitScale()
    if (!held || !fit) return
    const slid = Math.abs(ev.clientX - held.ox) + Math.abs(ev.clientY - held.oy)
    if (slid > DRAG_SLOP) held.moved = true
    if (!held.moved) return

    held.dx = (ev.clientX - held.ox) / fit
    held.dy = (ev.clientY - held.oy) / fit
    paintViewport(panBy(held.from, held.dx, held.dy))
  }

  const handlePaneUp = () => {
    const held = pan.current
    pan.current = null
    if (!held) return
    if (!held.moved) {
      dispatch({ type: 'clearSelection' })
      return
    }
    setView(panBy(held.from, held.dx, held.dy))
  }

  /** 손짓을 뺏겼다. 그냥 두면 `pan` 이 남아 다음 커서 이동에 화면이 딸려 온다. */
  const handlePaneCancel = () => {
    const held = pan.current
    pan.current = null
    if (held?.moved) setView(panBy(held.from, held.dx, held.dy))
  }

  /** 패널에 가리지 않는 한가운데를 붙잡는다. */
  const zoomBy = (direction: 'in' | 'out') =>
    setView((current) => zoomStep(current, direction, PANE_CONTENT_WIDTH / 2, PANE_HEIGHT / 2))

  /** React 의 wheel 은 passive 라 `preventDefault` 가 먹지 않는다. */
  useEffect(() => {
    const pane = paneRef.current
    if (!pane) return
    const onWheel = (ev: WheelEvent) => {
      if (overScrollable(ev.target, pane)) return
      const rect = pane.getBoundingClientRect()
      if (rect.width === 0) return
      const fit = rect.width / PANE_WIDTH
      ev.preventDefault()
      const delta = wheelPixels(ev)
      setView((current) => {
        const next = wheelZoom(
          current,
          delta,
          (ev.clientX - rect.left) / fit,
          (ev.clientY - rect.top) / fit,
        )
        // 끄는 도중이면 기준을 지금 자리로 옮긴다. 안 그러면 손 뗄 때 확대가 되돌아간다.
        if (pan.current) {
          pan.current = { ox: ev.clientX, oy: ev.clientY, from: next, dx: 0, dy: 0, moved: true }
        }
        return next
      })
    }
    pane.addEventListener('wheel', onWheel, { passive: false })
    return () => pane.removeEventListener('wheel', onWheel)
  }, [])

  /** 팔레트에서 끌어온 항목을 커서가 카드 한가운데를 잡고 있는 것처럼 놓는다. */
  const placeFromPalette = (seed: Omit<PlacedSeed, 'id'>, point: { x: number; y: number }) => {
    placedCount.current += 1
    dispatch({
      type: 'placeNode',
      seed: { ...seed, id: `placed-${placedCount.current}` },
      x: point.x - NODE_SIZE / 2,
      y: point.y - NODE_SIZE / 2,
    })
  }

  useEffect(() => {
    const onKey = (ev: KeyboardEvent) => {
      if (ev.key !== 'Delete' && ev.key !== 'Backspace') return
      // 글자를 지우는 중일 수 있다.
      const target = ev.target as HTMLElement | null
      if (target?.closest('input, textarea, [contenteditable="true"]')) return
      if (!selEdge && !nodes.some((n) => n.id === selId)) return
      ev.preventDefault()
      dispatch({ type: 'removeSelected' })
    }
    addEventListener('keydown', onKey)
    return () => removeEventListener('keydown', onKey)
  }, [selId, selEdge, nodes])

  // 선을 드래그하는 동안 이을 수 있는 포트만 밝게.
  const portOpen = (id: string, side: PortSide) => {
    if (!linkFrom) return true
    if (side === linkFrom.side) return false
    const [a, b] = linkFrom.side === 'out' ? [linkFrom.id, id] : [id, linkFrom.id]
    return canConnect(edges, a, b)
  }

  // 경로 탐색은 무겁다. 노드나 선이 바뀔 때만 다시 찾는다.
  const geometry = useMemo(() => edgeGeometry(nodes, edges), [nodes, edges])

  return {
    nodes,
    // 저장할 때 노드와 함께 직렬화한다(`lib/workflowPayload.ts`).
    edges,
    geometry,
    selId,
    hovId,
    selEdge,
    dragId,
    linkFrom,
    canSell: canSellWorkflow(nodes),
    portOpen,
    view,
    zoomLabel: `${zoomPercent(view)}%`,
    zoomIn: () => zoomBy('in'),
    zoomOut: () => zoomBy('out'),
    refs: { paneRef, worldRef, gridRef, glowRef, tempRef },
    placeFromPalette,
    toCanvasPoint,
    onHover: (id: string) => dispatch({ type: 'hover', id }),
    handleHoverOut: () => dispatch({ type: 'hover', id: null }),
    onSelectEdge: (id: string) => dispatch({ type: 'selectEdge', id }),
    handleSelect,
    handleNodePointerDown,
    handleNodePointerMove,
    handleNodePointerUp,
    handlePointerCancel,
    handlePortDown,
    handlePortMove,
    handlePortUp,
    handlePaneDown,
    handlePaneMove,
    handlePaneUp,
    handlePaneCancel,
  }
}
