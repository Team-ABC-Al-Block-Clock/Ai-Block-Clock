import { useEffect, useMemo, useReducer, useRef } from 'react'

import {
  canvasReducer,
  initialCanvasState,
  type PlacedSeed,
  type PortSide,
} from '@/lib/canvasReducer'
import {
  NODE_SIZE,
  NO_BOUNDS,
  canConnect,
  canSellWorkflow,
  clampToBounds,
  edgeGeometry,
  edgePath,
  portPoint,
  resolveOverlap,
  type CanvasBounds,
} from '@/lib/canvasWorkflow'

/** 작업면 div 의 설계 폭. 창 좌표를 설계 좌표로 되돌릴 때 배율의 기준이 된다. */
export const PANE_WIDTH = 1076
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
  bounds: CanvasBounds
  /** 선택 빛이 지금 이 카드를 따라다니는가 */
  glow: boolean
}

/**
 * 캔버스의 상태와 손짓을 한데 묶는다.
 *
 * 상태 전이는 전부 `canvasReducer` 가 한다. 여기는 화면을 재고(`toCanvasPoint`·
 * `canvasBounds`), 포인터를 따라 DOM 을 직접 고치고(드래그 중 리렌더를 피한다),
 * 언제 어떤 액션을 낼지만 정한다.
 */
export function useCanvasWorkflow() {
  const [state, dispatch] = useReducer(canvasReducer, initialCanvasState)
  const { nodes, edges, selId, hovId, selEdge, dragId, linkFrom } = state

  const paneRef = useRef<HTMLDivElement>(null)
  const panelRef = useRef<HTMLDivElement>(null)
  const glowRef = useRef<HTMLDivElement>(null)
  const tempRef = useRef<SVGPathElement>(null)

  const grab = useRef<Grab | null>(null)
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

  /** 창 좌표 → 설계 좌표. 프레임이 화면 크기에 맞춰 배율을 걸어 둬서 그대로 쓰면 어긋난다. */
  const toCanvasPoint = (clientX: number, clientY: number) => {
    const rect = paneRef.current?.getBoundingClientRect()
    if (!rect || rect.width === 0) return null
    const scale = rect.width / PANE_WIDTH
    return { x: (clientX - rect.left) / scale, y: (clientY - rect.top) / scale }
  }

  /**
   * 카드가 갈 수 있는 범위를 화면에서 잰다. 패널 위치가 `--edge` 만큼 창 너비를 타서
   * 설계 좌표로 박을 수 없다. 네 방향 모두 카드 절반까지만 — 남은 절반으로 되꺼낸다.
   */
  const canvasBounds = (): CanvasBounds | null => {
    const pane = paneRef.current?.getBoundingClientRect()
    const panel = panelRef.current?.getBoundingClientRect()
    if (!pane || !panel || pane.width === 0) return null
    const scale = pane.width / PANE_WIDTH
    const toX = (clientX: number) => (clientX - pane.left) / scale
    const toY = (clientY: number) => (clientY - pane.top) / scale
    const half = NODE_SIZE / 2
    return {
      minX: toX(0) - half,
      maxX: toX(panel.left) - half,
      minY: toY(0) - half,
      maxY: toY(window.innerHeight) - half,
    }
  }

  /** 드래그 중 직접 써 넣은 선은 React 가 모른다. 좌표가 정해질 때마다 여기서 고친다. */
  const paintEdges = (at: { id: string; x: number; y: number }, only = edges) => {
    const preview = nodes.map((node) => (node.id === at.id ? { ...node, x: at.x, y: at.y } : node))
    for (const edge of edgeGeometry(preview, only)) {
      paneRef.current
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
      bounds: canvasBounds() ?? NO_BOUNDS,
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

    // 겹침은 손을 뗄 때 한 번만 정리한다.
    const next = clampToBounds(point.x - held.dx, point.y - held.dy, held.bounds)
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

    const settled = resolveOverlap(nodes, held.id, raw.x, raw.y, held.bounds)
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

  /** 빈 곳을 누르면 선택 해제. */
  const handlePaneDown = (ev: React.PointerEvent<HTMLDivElement>) => {
    if ((ev.target as HTMLElement).closest('[data-node],[data-edge]')) return
    dispatch({ type: 'clearSelection' })
  }

  /** 팔레트에서 끌어온 항목을 커서가 카드 한가운데를 잡고 있는 것처럼 놓는다. */
  const placeFromPalette = (seed: Omit<PlacedSeed, 'id'>, point: { x: number; y: number }) => {
    placedCount.current += 1
    dispatch({
      type: 'placeNode',
      seed: { ...seed, id: `placed-${placedCount.current}` },
      x: point.x - NODE_SIZE / 2,
      y: point.y - NODE_SIZE / 2,
      bounds: canvasBounds() ?? NO_BOUNDS,
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
    geometry,
    selId,
    hovId,
    selEdge,
    dragId,
    linkFrom,
    canSell: canSellWorkflow(nodes),
    portOpen,
    refs: { paneRef, panelRef, glowRef, tempRef },
    placeFromPalette,
    toCanvasPoint,
    onHover: (id: string) => dispatch({ type: 'hover', id }),
    handleHoverOut: () => dispatch({ type: 'hover', id: null }),
    onSelectEdge: (id: string) => dispatch({ type: 'selectEdge', id }),
    handleSelect,
    handleNodePointerDown,
    handleNodePointerMove,
    handleNodePointerUp,
    handlePortDown,
    handlePortMove,
    handlePortUp,
    handlePaneDown,
  }
}
