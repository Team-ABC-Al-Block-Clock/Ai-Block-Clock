import { keepPreviousData, useInfiniteQuery } from '@tanstack/react-query'
import { Fragment, useEffect, useRef, useState } from 'react'

import { getNodePalette } from '@/api/workflows'
import { nodeTypeLabel, priceLabel, runLabel } from '@/lib/format'
import { queryKeys } from '@/lib/queryKeys'
import CanvasNode from '@/components/CanvasNode'
import {
  CANVAS_NODES,
  NODE_SIZE,
  canSellWorkflow,
  moveNode,
  placeNode,
  resolveOverlap,
} from '@/lib/canvasWorkflow'
import { paths } from '@/routes/paths'
import { cssToStyle } from '../lib/css'
import SidePanel from '@/components/SidePanel'
import SearchIcon from '@/components/SearchIcon'
import StatusDot from '@/components/StatusDot'

/** 한 번에 불러오는 개수. */
const PAGE_SIZE = 10
/** 입력이 멈춘 뒤에만 검색한다. */
const DEBOUNCE_MS = 300
/** 내 노드와 마켓 에이전트를 한 목록으로 본다. 등록 가능 여부는 조회가 아니라 놓인 구성(`canSell`)이 정한다. */
const SCOPE = 'ALL' as const
/** 작업면 div 의 설계 폭. 창 좌표를 설계 좌표로 되돌릴 때 배율의 기준이 된다. */
const PANE_WIDTH = 1076
/** 끌어 놓기로 주고받는 것은 팔레트 항목 id 하나뿐이다. 브라우저가 소문자로만 받는다. */
const DND_TYPE = 'application/x-placeable-item'
/** 이 거리보다 움직였으면 고른 것이 아니라 옮긴 것으로 본다. */
const DRAG_SLOP = 4

/** 노드 편집 화면 */
export default function EditNodes() {
  const [sel, setSel] = useState(-1)
  const [hov, setHov] = useState(-1)
  const [panelOpen, setPanelOpen] = useState(false)
  // 카드 index 로 잡으면 다음 장이 붙을 때 엉뚱한 카드가 열린다.
  const [infoAt, setInfoAt] = useState<string | null>(null)
  const [q, setQ] = useState('')
  const [keyword, setKeyword] = useState('')

  // 자리표시자를 첫 값으로만 쓴다.
  const [nodes, setNodes] = useState(CANVAS_NODES)
  const canSell = canSellWorkflow(nodes)

  const paneRef = useRef<HTMLDivElement>(null)
  const [dragId, setDragId] = useState<string | null>(null)
  /** 끄는 동안의 값은 리렌더가 필요 없다 — 상태로 두면 포인터가 움직일 때마다 두 번 그린다. */
  const glowRef = useRef<HTMLDivElement>(null)
  const grab = useRef<{
    id: string
    /** 잡은 지점과 카드 왼쪽 위의 차이 */
    dx: number
    dy: number
    /** 누른 창 좌표 — 고르기와 옮기기를 가르는 기준 */
    ox: number
    oy: number
    el: HTMLDivElement
    baseX: number
    baseY: number
    /** 선택 빛이 지금 이 카드를 따라다니는가 */
    glow: boolean
  } | null>(null)
  /** 끄는 동안의 마지막 자리. 손을 뗄 때 한 번만 상태로 옮긴다. */
  const landing = useRef<{ x: number; y: number } | null>(null)
  const moved = useRef(false)
  const placedCount = useRef(0)

  /** 창 좌표 → 설계 좌표. 프레임이 화면 크기에 맞춰 배율을 걸어 둬서 그대로 쓰면 어긋난다. */
  const toCanvasPoint = (clientX: number, clientY: number) => {
    const rect = paneRef.current?.getBoundingClientRect()
    if (!rect || rect.width === 0) return null
    const scale = rect.width / PANE_WIDTH
    return { x: (clientX - rect.left) / scale, y: (clientY - rect.top) / scale }
  }

  // 옮기고 손을 뗀 직후에 click 이 한 번 더 온다. 그때 선택이 뒤집히지 않게 막는다.
  const handleSelect = (i: number) => {
    if (moved.current) {
      moved.current = false
      return
    }
    setSel(sel === i ? -1 : i)
  }

  /**
   * 포인터를 카드에 묶어 둔다 — 창 리스너를 상태로 붙이면 이펙트가 붙기 전에 지나간
   * 움직임을 놓친다. 캡처해 두면 카드 밖으로 나가도 move·up 이 카드로 온다.
   */
  const handleNodePointerDown = (id: string) => (ev: React.PointerEvent<HTMLDivElement>) => {
    if (ev.button !== 0) return
    const node = nodes.find((n) => n.id === id)
    const point = node && toCanvasPoint(ev.clientX, ev.clientY)
    if (!node || !point) return
    grab.current = {
      id,
      dx: point.x - node.x,
      dy: point.y - node.y,
      ox: ev.clientX,
      oy: ev.clientY,
      el: ev.currentTarget,
      baseX: node.x,
      baseY: node.y,
      glow: (nodes[hov] ?? nodes[sel])?.id === id,
    }
    landing.current = { x: node.x, y: node.y }
    moved.current = false
    setDragId(id)
    // 캡처가 막히는 환경이 있어도 끌기 자체는 살려 둔다 — 카드 위에서는 그대로 따라온다.
    try {
      ev.currentTarget.setPointerCapture(ev.pointerId)
    } catch {
      /* 이미 놓인 포인터 */
    }
  }

  /**
   * 끄는 동안에는 상태를 고치지 않는다.
   *
   * 포인터가 움직일 때마다 `setNodes` 를 하면 팔레트 카드와 연결선까지 한 화면을 통째로
   * 다시 그린다 — 카드가 손끝을 늦게 따라오는 이유가 그것이다. 여기서는 옮길 카드의
   * `transform` 만 직접 건드리고, 상태는 손을 뗄 때 한 번만 맞춘다.
   */
  const handleNodePointerMove = (ev: React.PointerEvent<HTMLDivElement>) => {
    const held = grab.current
    const point = held && toCanvasPoint(ev.clientX, ev.clientY)
    if (!held || !point) return
    // 손이 떨린 정도로는 선택을 막지 않는다.
    const slid = Math.abs(ev.clientX - held.ox) + Math.abs(ev.clientY - held.oy)
    if (slid > DRAG_SLOP) moved.current = true

    const next = resolveOverlap(nodes, held.id, point.x - held.dx, point.y - held.dy)
    landing.current = next
    held.el.style.transform = `translate3d(${next.x - held.baseX}px, ${next.y - held.baseY}px, 0)`
    // 빛은 left·top 으로 민다 — transform 은 React 가 들고 있지 않아 지우면 되살아나지 않는다.
    if (held.glow && glowRef.current) {
      glowRef.current.style.left = `${next.x - 16}px`
      glowRef.current.style.top = `${next.y - 16}px`
    }
  }

  const handleNodePointerUp = () => {
    const held = grab.current
    const next = landing.current
    if (held && next) {
      // 상태가 반영되기 전 한 프레임 동안 제자리로 튀지 않게 먼저 맞춰 둔다.
      held.el.style.left = `${next.x}px`
      held.el.style.top = `${next.y}px`
      held.el.style.transform = ''
      setNodes((prev) => moveNode(prev, held.id, next.x, next.y))
    }
    grab.current = null
    landing.current = null
    setDragId(null)
  }

  const handleDragOver = (ev: React.DragEvent<HTMLDivElement>) => {
    if (!ev.dataTransfer.types.includes(DND_TYPE)) return
    // 막지 않으면 브라우저가 drop 을 내지 않는다.
    ev.preventDefault()
    ev.dataTransfer.dropEffect = 'copy'
  }

  const handleDrop = (ev: React.DragEvent<HTMLDivElement>) => {
    const itemId = ev.dataTransfer.getData(DND_TYPE)
    if (!itemId) return
    ev.preventDefault()
    const item = items.find((it) => it.itemId === itemId)
    const point = item && toCanvasPoint(ev.clientX, ev.clientY)
    if (!item || !point) return
    placedCount.current += 1
    // 커서가 카드 한가운데를 잡고 있는 것처럼 놓는다.
    setNodes((prev) =>
      placeNode(
        prev,
        {
          id: `placed-${placedCount.current}`,
          name: item.name,
          nodeType: item.nodeType,
          mine: item.mine,
        },
        point.x - NODE_SIZE / 2,
        point.y - NODE_SIZE / 2,
      ),
    )
  }
  const handleHovOut = () => setHov(-1)
  const handleTogglePanel = () => setPanelOpen((v) => !v)
  const handleSearch = (ev: React.ChangeEvent<HTMLInputElement>) => setQ(ev.target.value)

  useEffect(() => {
    const timer = setTimeout(() => setKeyword(q.trim()), DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [q])

  const {
    data,
    isPending,
    isPlaceholderData,
    isError,
    hasNextPage,
    isFetchingNextPage,
    isFetchNextPageError,
    fetchNextPage,
  } = useInfiniteQuery({
    queryKey: queryKeys.workflows.nodePalette.list({ scope: SCOPE, keyword, size: PAGE_SIZE }),
    queryFn: ({ pageParam }) =>
      getNodePalette({
        scope: SCOPE,
        keyword: keyword || undefined,
        cursor: pageParam,
        size: PAGE_SIZE,
      }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    // 검색어가 바뀌는 동안 목록이 비었다가 차오르지 않게 이전 결과를 남긴다.
    placeholderData: keepPreviousData,
  })

  const items = data?.pages.flatMap((page) => page.content) ?? []
  // 이전 검색어의 결과를 보여주는 동안(placeholder)은 아직 이번 검색의 결과가 아니다.
  // isPending 만 보면 그 사이 옛 목록을 '없음' 으로 덮어 한 글자마다 깜빡인다.
  const isSettled = !isPending && !isPlaceholderData
  const isEmpty = isSettled && !isError && items.length === 0

  const paletteRef = useRef<HTMLDivElement>(null)
  const sentinelRef = useRef<HTMLDivElement>(null)

  // 검색어가 바뀌면 맨 위로 스크롤을 올린다. 아래쪽에 머문 채 목록만 바뀌면 새 결과의 앞부분을
  // 못 보고 지나쳐 검색에 실패한 것처럼 보일 수 있다.
  useEffect(() => {
    paletteRef.current?.scrollTo({ top: 0 })
  }, [keyword])

  useEffect(() => {
    const node = sentinelRef.current
    // 실패한 커서로 다시 시도하지 않는다 — 교차할 때마다 같은 요청이 반복된다.
    if (!node || !hasNextPage || isFetchNextPageError) return
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && !isFetchingNextPage) void fetchNextPage()
      },
      { root: paletteRef.current, rootMargin: '160px' },
    )
    observer.observe(node)
    return () => observer.disconnect()
  }, [hasNextPage, isFetchingNextPage, isFetchNextPageError, fetchNextPage])

  const n = nodes[sel]
  const g = nodes[hov] || n // 빛은 호버 중인 노드를 따라간다
  const selRing = g
    ? 'position:absolute;left:' +
      (g.x - 16) +
      'px;top:' +
      (g.y - 16) +
      'px;width:200px;height:200px;border-radius:50%;pointer-events:none;opacity:' +
      (hov >= 0 && hov !== sel ? '.6' : '1') +
      ';transform:translateY(' +
      (hov >= 0 && !dragId ? -5 : 0) +
      'px);background:radial-gradient(circle, rgba(255,160,200,.30) 0 42%, rgba(255,160,200,.14) 62%, rgba(255,160,200,0) 76%)' +
      // 끄는 동안 빛이 전환으로 따라오면 손끝보다 늦어 어긋나 보인다.
      (dragId
        ? ''
        : ';transition:left .34s cubic-bezier(.2,.8,.25,1),top .34s cubic-bezier(.2,.8,.25,1),transform .2s,opacity .25s ease')
    : 'display:none'
  const cards = items.map((item) => ({
    ...item,
    initial: item.name.trim().charAt(0),
    uses: runLabel(item.executionCount),
    unit: priceLabel(item.price),
    typeLabel: nodeTypeLabel(item.nodeType),
    infoOpen: infoAt === item.itemId,
    infoBg: infoAt === item.itemId ? 'rgba(255,255,255,.34)' : 'rgba(255,255,255,.18)',
    drag: (ev: React.DragEvent<HTMLDivElement>) => {
      // 항목 id 만 넘긴다. 놓는 쪽이 같은 목록에서 다시 찾는다.
      ev.dataTransfer.setData(DND_TYPE, item.itemId)
      ev.dataTransfer.effectAllowed = 'copy'
    },
    info: (ev: React.MouseEvent) => {
      ev.preventDefault()
      ev.stopPropagation()
      setInfoAt((v) => (v === item.itemId ? null : item.itemId))
    },
  }))
  const hasSelection = !!n
  const selName = n ? n.name : ''
  const selInitial = n ? n.name.trim().charAt(0) : ''
  const selTypeLabel = n ? nodeTypeLabel(n.nodeType) : ''

  return (
    <>
      <div
        ref={paneRef}
        className="absolute top-0 left-0"
        onDragOver={handleDragOver}
        onDrop={handleDrop}
        style={{
          width: `${PANE_WIDTH}px`,
          height: '1024px',
          animation: 'paneIn 1.1s cubic-bezier(.18,.86,.24,1) both',
          transformOrigin: '44% 46%',
        }}
      >
        <div
          className="pointer-events-none absolute"
          style={{
            left: 'calc(-1 * var(--edge, 0px))',
            right: 'calc(96px - var(--edge, 0px))',
            top: 0,
            bottom: 0,
            backgroundImage: 'radial-gradient(rgba(255,255,255,.10) 1px, transparent 1px)',
            backgroundSize: '26px 26px',
            backgroundPosition: '13px 13px',
            opacity: '.55',
          }}
        ></div>
        <div
          className="pointer-events-none absolute"
          style={{
            left: 'calc(-1 * var(--edge, 0px))',
            right: 'calc(96px - var(--edge, 0px))',
            top: 0,
            bottom: 0,
            background:
              'linear-gradient(90deg, rgba(10,5,22,0) 30%, rgba(10,5,22,.26) 70%, rgba(10,5,22,.32) 100%)',
          }}
        ></div>

        <div
          className="absolute left-0 text-center text-[16px] tracking-[-.005em]"
          style={{
            top: '74px',
            width: '980px',
            color: 'rgba(255,240,248,.78)',
            animation: 'uiIn .8s .5s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          오른쪽에서 끌어다 놓고, 놓인 노드를 끌어 옮깁니다
        </div>

        <svg
          className="pointer-events-none absolute top-0 left-0"
          width="1076"
          height="1024"
          viewBox="0 0 1076 1024"
          style={{ overflow: 'visible' }}
        >
          <defs>
            <linearGradient id="edgeOn" x1="0" y1="0" x2="1" y2="0">
              <stop offset="0%" stopColor="#e3b7c8" stopOpacity=".9" />
              <stop offset="100%" stopColor="#dba18b" stopOpacity=".8" />
            </linearGradient>
            <marker id="ah" markerWidth="7" markerHeight="7" refX="5.5" refY="3.5" orient="auto">
              <path d="M0 0.6 L6.4 3.5 L0 6.4 z" fill="rgba(255,190,214,.8)" />
            </marker>
          </defs>
          <path
            d="M180 484 C 195 484, 195 404, 209 404"
            fill="none"
            stroke="url(#edgeOn)"
            strokeWidth="2.2"
          />
          <path
            d="M180 484 C 195 484, 195 404, 209 404"
            fill="none"
            stroke="rgba(255,224,238,.85)"
            strokeWidth="2.2"
            strokeLinecap="round"
            strokeDasharray="1 26"
            style={{ animation: 'dash 1.4s linear infinite' }}
            opacity=".8"
          />
          <path
            d="M377 404 C 392 404, 392 524, 406 524"
            fill="none"
            stroke="url(#edgeOn)"
            strokeWidth="2.2"
          />
          <path
            d="M377 404 C 392 404, 392 524, 406 524"
            fill="none"
            stroke="rgba(255,224,238,.85)"
            strokeWidth="2.2"
            strokeLinecap="round"
            strokeDasharray="1 26"
            style={{ animation: 'dash 1.5s .2s linear infinite' }}
            opacity=".8"
          />
          <path
            d="M574 524 C 589 524, 589 414, 603 414"
            fill="none"
            stroke="url(#edgeOn)"
            strokeWidth="2.2"
          />
          <path
            d="M574 524 C 589 524, 589 414, 603 414"
            fill="none"
            stroke="rgba(255,224,238,.85)"
            strokeWidth="2.2"
            strokeLinecap="round"
            strokeDasharray="1 26"
            style={{ animation: 'dash 1.6s .4s linear infinite' }}
            opacity=".8"
          />
          <path
            d="M771 414 C 786 414, 786 514, 800 514"
            fill="none"
            stroke="url(#edgeOn)"
            strokeWidth="2.2"
          />
          <path
            d="M771 414 C 786 414, 786 514, 800 514"
            fill="none"
            stroke="rgba(255,224,238,.85)"
            strokeWidth="2.2"
            strokeLinecap="round"
            strokeDasharray="1 26"
            style={{ animation: 'dash 1.7s .6s linear infinite' }}
            opacity=".8"
          />
        </svg>

        <div ref={glowRef} style={cssToStyle(selRing)}></div>

        {nodes.map((node, i) => (
          <CanvasNode
            key={node.id}
            index={i}
            name={node.name}
            sublabel={nodeTypeLabel(node.nodeType)}
            x={node.x}
            y={node.y}
            mine={node.mine}
            animation={node.animation}
            background={i === sel ? 'rgba(255,255,255,.16)' : 'rgba(255,255,255,.11)'}
            sublabelColor="rgba(230,196,212,.85)"
            title={
              node.mine ? undefined : '다른 사람의 에이전트 — 이 구성은 마켓에 등록할 수 없습니다'
            }
            dragging={dragId === node.id}
            onSelect={handleSelect}
            onPointerDown={handleNodePointerDown(node.id)}
            onPointerMove={handleNodePointerMove}
            onPointerUp={handleNodePointerUp}
            onHover={setHov}
            onHoverOut={handleHovOut}
          />
        ))}

        <div
          className="absolute flex items-center overflow-hidden"
          style={{
            left: 'calc(32px - var(--edge, 0px))',
            top: '952px',
            height: '36px',
            borderRadius: '18px',
            background: 'rgba(14,7,28,.55)',
            backdropFilter: 'blur(18px)',
            WebkitBackdropFilter: 'blur(18px)',
            animation: 'uiIn .7s .56s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          <div
            style={{ width: '41px', height: '36px', color: 'rgba(255,255,255,.82)' }}
            className="flex cursor-pointer items-center justify-center text-[17px] font-semibold hover:bg-[rgba(255,255,255,.12)]"
          >
            −
          </div>
          <div
            className="flex items-center justify-center text-[15px] font-semibold"
            style={{ width: '67px', height: '36px', color: 'rgba(255,255,255,.82)' }}
          >
            100%
          </div>
          <div
            style={{ width: '41px', height: '36px', color: 'rgba(255,255,255,.82)' }}
            className="flex cursor-pointer items-center justify-center text-[17px] font-semibold hover:bg-[rgba(255,255,255,.12)]"
          >
            +
          </div>
          <div style={{ width: '1px', height: '22px', background: 'rgba(255,255,255,.20)' }}></div>
          <div
            onClick={handleTogglePanel}
            style={{ height: '36px', padding: '0 16px', gap: '7px' }}
            className="flex cursor-pointer items-center text-[15px] font-semibold text-white hover:bg-[rgba(255,255,255,.12)]"
          >
            <span>+</span>
            <span>노드 추가</span>
          </div>
        </div>

        {panelOpen && (
          <>
            <div
              className="absolute box-border flex flex-col"
              style={{
                left: 'calc(32px - var(--edge, 0px))',
                top: '262px',
                width: '500px',
                height: '700px',
                borderRadius: '14px',
                background: 'rgba(20,11,36,.88)',
                backdropFilter: 'blur(28px) saturate(125%)',
                WebkitBackdropFilter: 'blur(28px) saturate(125%)',
                boxShadow: 'inset 0 1px 0 rgba(255,255,255,.18),0 26px 60px rgba(4,2,12,.6)',
                padding: '20px',
                animation: 'panelPop .34s cubic-bezier(.2,.9,.3,1) both',
                transformOrigin: '20% 100%',
              }}
            >
              <div
                className="flex flex-none items-center justify-between"
                style={{ marginBottom: '16px' }}
              >
                <div className="text-[20px] font-semibold tracking-[-.01em] text-white">
                  노드 추가
                </div>
                <div
                  onClick={handleTogglePanel}
                  style={{
                    width: '22px',
                    height: '22px',
                    borderRadius: '6px',
                    color: 'rgba(255,255,255,.6)',
                  }}
                  className="flex cursor-pointer items-center justify-center text-[16px] hover:bg-[rgba(255,255,255,.14)] hover:text-[#fff]"
                >
                  ×
                </div>
              </div>

              <div
                className="flex-1 overflow-x-hidden"
                id="addPanelFields"
                style={{
                  minHeight: '0',
                  overflowY: 'auto',
                  paddingRight: '4px',
                  scrollbarWidth: 'thin',
                  scrollbarColor: 'rgba(255,255,255,.26) transparent',
                }}
              >
                <div
                  className="text-[17px]"
                  style={{ color: 'rgba(255,214,232,.85)', marginBottom: '8px' }}
                >
                  노드 이름
                </div>
                <div
                  className="bg-surface-card flex items-center"
                  style={{
                    height: '44px',
                    borderRadius: '11px',
                    padding: '0 12px',
                    marginBottom: '14px',
                  }}
                >
                  <input
                    className="border-0 bg-transparent font-[inherit] text-[17px] text-white outline-0"
                    type="text"
                    placeholder="예: 내용 분석"
                    style={{ width: '100%' }}
                  />
                </div>

                <div
                  className="text-[17px]"
                  style={{ color: 'rgba(255,214,232,.85)', marginBottom: '8px' }}
                >
                  입력 파일
                </div>
                <div
                  className="bg-surface-card flex items-center justify-between"
                  style={{
                    height: '44px',
                    borderRadius: '11px',
                    padding: '0 6px 0 12px',
                    marginBottom: '14px',
                  }}
                >
                  <div className="text-[17px]" style={{ color: 'rgba(255,255,255,.5)' }}>
                    파일을 선택하세요
                  </div>
                  <div
                    style={{
                      height: '30px',
                      padding: '0 14px',
                      borderRadius: '8px',
                      background: 'rgba(255,255,255,.16)',
                    }}
                    className="flex cursor-pointer items-center text-[12.5px] font-semibold text-white hover:bg-[rgba(255,255,255,.28)]"
                  >
                    찾기
                  </div>
                </div>

                <div
                  className="text-[17px]"
                  style={{ color: 'rgba(255,214,232,.85)', marginBottom: '8px' }}
                >
                  AI 모델
                </div>
                <div
                  className="bg-surface-card flex items-center"
                  style={{
                    height: '44px',
                    borderRadius: '11px',
                    padding: '0 12px',
                    marginBottom: '14px',
                  }}
                >
                  <div className="text-[17px] text-white">기본 모델</div>
                </div>

                <div
                  className="text-[17px] font-semibold"
                  style={{ color: 'rgba(255,214,232,.85)', marginBottom: '8px' }}
                >
                  프롬프트
                </div>
                <div
                  className="bg-surface-card"
                  style={{
                    height: '150px',
                    borderRadius: '9px',
                    padding: '11px 12px',
                    marginBottom: '14px',
                  }}
                >
                  <textarea
                    className="border-0 bg-transparent font-[inherit] text-[17px] text-white outline-0"
                    placeholder="이 노드가 할 일을 프롬프트로 적으세요&#10;예: 이 문서를 분석하여 핵심 내용을 요약해 주세요."
                    style={{ width: '100%', height: '128px', resize: 'none', lineHeight: '1.5' }}
                  ></textarea>
                </div>

                <div
                  className="text-[17px]"
                  style={{ color: 'rgba(255,214,232,.85)', marginBottom: '10px' }}
                >
                  제어 흐름 노드
                </div>
                <div className="flex" style={{ gap: '8px', marginBottom: '2px' }}>
                  <div
                    style={{ height: '36px', padding: '0 13px', borderRadius: '10px' }}
                    className="bg-surface-card flex cursor-pointer items-center text-[12.5px] font-semibold whitespace-nowrap text-white hover:bg-[rgba(255,255,255,.2)]"
                  >
                    조건 분기 (if / else)
                  </div>
                  <div
                    style={{ height: '36px', padding: '0 15px', borderRadius: '10px' }}
                    className="bg-surface-card flex cursor-pointer items-center text-[12.5px] font-semibold text-white hover:bg-[rgba(255,255,255,.2)]"
                  >
                    반복
                  </div>
                  <div
                    style={{ height: '36px', padding: '0 15px', borderRadius: '10px' }}
                    className="bg-surface-card flex cursor-pointer items-center text-[12.5px] font-semibold text-white hover:bg-[rgba(255,255,255,.2)]"
                  >
                    병합
                  </div>
                </div>
              </div>

              <div
                className="flex flex-none"
                style={{ justifyContent: 'flex-end', gap: '8px', paddingTop: '14px' }}
              >
                <div
                  onClick={handleTogglePanel}
                  style={{
                    height: '40px',
                    padding: '0 22px',
                    borderRadius: '11px',
                    boxShadow: 'inset 0 0 0 1px rgba(255,255,255,.18)',
                    color: 'rgba(255,255,255,.9)',
                  }}
                  className="bg-surface-card flex cursor-pointer items-center text-[17px] font-semibold hover:bg-[rgba(255,255,255,.18)]"
                >
                  취소
                </div>
                <div
                  onClick={handleTogglePanel}
                  style={{
                    height: '40px',
                    padding: '0 24px',
                    borderRadius: '11px',
                    background: 'var(--color-accent)',
                    boxShadow: '0 8px 20px rgba(140,100,130,.24)',
                    color: 'var(--color-accent-ink)',
                  }}
                  className="flex cursor-pointer items-center text-[17px] font-bold hover:brightness-[1.05]"
                >
                  추가
                </div>
              </div>
            </div>
          </>
        )}

        <a
          href={paths.run.waiting.withoutId}
          style={{
            left: 'calc(800px + var(--edge, 0px))',
            top: '943px',
            width: '160px',
            height: '52px',
            borderRadius: '26px',
            background: 'var(--color-accent)',
            boxShadow: '0 10px 26px rgba(140,100,130,.24),inset 0 1px 0 rgba(255,255,255,.6)',
            color: 'var(--color-accent-ink)',
            transition: 'transform .18s,box-shadow .18s',
            animation: 'uiIn .7s .62s cubic-bezier(.2,.8,.25,1) both',
          }}
          className="absolute flex cursor-pointer items-center justify-center text-[17px] font-bold tracking-[-.01em] hover:-translate-y-[2px] hover:shadow-[0_14px_32px_rgba(140,100,130,.28),inset_0_1px_0_rgba(255,255,255,.6)]"
        >
          실행하기
        </a>
      </div>

      <SidePanel>
        <div
          className="absolute text-[17px] font-semibold tracking-[-.005em] text-white"
          style={{ left: '30px', top: '30px' }}
        >
          노드 정보
        </div>

        {hasSelection ? (
          <>
            <div
              className="absolute box-border flex items-center"
              style={{
                left: '30px',
                top: '58px',
                width: '400px',
                height: '104px',
                borderRadius: '12px',
                background: 'rgba(255,255,255,.11)',
                padding: '0 16px',
                gap: '16px',
              }}
            >
              <div
                className="relative flex-none overflow-hidden bg-[rgba(255,255,255,.10)]"
                style={{ width: '48px', height: '48px', borderRadius: '10px' }}
              >
                <div
                  className="absolute inset-0 flex items-center justify-center text-[17px] font-semibold"
                  style={{ color: 'rgba(255,226,238,.65)' }}
                >
                  {selInitial}
                </div>
              </div>
              <div className="flex min-w-0 flex-col" style={{ gap: '4px' }}>
                <div className="truncate text-[18px] font-semibold tracking-[-.005em] text-white">
                  {selName}
                </div>
                <div className="text-[14.5px]" style={{ color: 'rgba(255,224,238,.88)' }}>
                  {selTypeLabel} 노드
                </div>
              </div>
            </div>

            <div
              className="absolute text-[15.5px] font-semibold"
              style={{ left: '30px', top: '172px', color: 'rgba(255,255,255,.92)' }}
            >
              노드 개요
            </div>
            <div
              className="absolute"
              style={{
                left: '30px',
                top: '196px',
                width: '400px',
                height: '108px',
                borderRadius: '10px',
                background: 'var(--color-surface-inset)',
              }}
            >
              <div
                className="absolute"
                style={{
                  left: '14px',
                  top: '16px',
                  width: '264px',
                  height: '10px',
                  borderRadius: '3px',
                  background: 'rgba(255,255,255,.16)',
                }}
              ></div>
              <div
                className="absolute"
                style={{
                  left: '14px',
                  top: '38px',
                  width: '240px',
                  height: '10px',
                  borderRadius: '3px',
                  background: 'rgba(255,255,255,.13)',
                }}
              ></div>
              <div
                className="absolute"
                style={{
                  left: '14px',
                  top: '60px',
                  width: '252px',
                  height: '10px',
                  borderRadius: '3px',
                  background: 'rgba(255,255,255,.13)',
                }}
              ></div>
              <div
                className="absolute bg-[rgba(255,255,255,.10)]"
                style={{
                  left: '14px',
                  top: '82px',
                  width: '176px',
                  height: '10px',
                  borderRadius: '3px',
                }}
              ></div>
            </div>

            <div
              className="absolute text-[15.5px] font-semibold"
              style={{ left: '30px', top: '326px', color: 'rgba(255,255,255,.92)' }}
            >
              모델
            </div>
            <div
              className="absolute flex items-center"
              style={{
                left: '30px',
                top: '350px',
                width: '400px',
                height: '44px',
                borderRadius: '10px',
                padding: '0 14px',
                background: 'var(--color-surface-inset)',
              }}
            >
              <div className="text-[15.5px]" style={{ color: 'rgba(255,255,255,.9)' }}>
                기본 모델
              </div>
            </div>
          </>
        ) : (
          <div
            className="absolute text-[15.5px]"
            style={{ left: '30px', top: '64px', width: '400px', color: 'rgba(255,236,246,.66)' }}
          >
            캔버스에서 노드를 고르면 여기서 살펴봅니다
          </div>
        )}

        <div
          className="absolute"
          style={{
            left: '30px',
            top: '424px',
            width: '400px',
            height: '1px',
            background: 'rgba(255,255,255,.14)',
          }}
        ></div>

        {/* 돋보기는 장식이라 클릭을 흘려보낸다. input 이 남는 자리를 전부 채워
            상자 어디를 눌러도 입력으로 들어간다 — 예전에는 왼쪽 250px 만 먹었다. */}
        <div
          className="absolute bg-[rgba(255,255,255,.10)]"
          style={{
            left: '30px',
            top: '448px',
            width: '400px',
            height: '40px',
            borderRadius: '10px',
          }}
        >
          <SearchIcon left={14} top={13} color="rgba(255,255,255,.7)" />
          <input
            className="absolute inset-y-0 border-0 bg-transparent font-[inherit] text-[15.5px] text-white outline-0"
            type="text"
            value={q}
            onChange={handleSearch}
            placeholder="노드 · 마켓 에이전트 검색"
            aria-label="노드 · 마켓 에이전트 검색"
            style={{ left: '42px', right: '16px', width: 'auto' }}
          />
        </div>

        <div
          ref={paletteRef}
          className="scroll-thin absolute grid"
          style={{
            left: '30px',
            top: '502px',
            width: '408px',
            height: '498px',
            // 한 줄에 두 장. 남는 폭을 열이 나눠 갖게 한다.
            gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
            gap: '14px',
            alignContent: 'start',
          }}
        >
          {isError && (
            <div
              className="text-[14.5px]"
              style={{ gridColumn: '1 / -1', color: 'rgba(255,236,246,.78)' }}
            >
              목록을 불러오지 못했습니다. 잠시 뒤 다시 시도해 주세요.
            </div>
          )}
          {isEmpty && (
            <div
              className="text-[14.5px]"
              style={{ gridColumn: '1 / -1', color: 'rgba(255,236,246,.66)' }}
            >
              검색 결과가 없습니다.
            </div>
          )}
          {cards.map((item) => (
            <Fragment key={item.itemId}>
              <div
                draggable
                onDragStart={item.drag}
                title="캔버스로 끌어다 놓으세요"
                style={{
                  // 폭은 주지 않고 그리드 열을 그대로 채운다.
                  height: '160px',
                  borderRadius: '12px',
                  transition: 'background .18s,transform .18s',
                  // 자신의 에이전트와 다른 사람 에이전트를 구분할 수 있게 테두리와 칩으로 표시한다.
                  boxShadow: item.mine
                    ? 'inset 0 0 0 1.5px rgba(255,190,220,.8)'
                    : 'inset 0 0 0 1px rgba(255,255,255,.10)',
                }}
                className="relative cursor-grab bg-[rgba(255,255,255,.09)] hover:-translate-y-[2px] hover:bg-[rgba(255,255,255,.17)] active:cursor-grabbing"
              >
                <div
                  className="absolute overflow-hidden bg-[rgba(255,255,255,.10)]"
                  style={{
                    left: '16px',
                    top: '16px',
                    width: '44px',
                    height: '44px',
                    borderRadius: '10px',
                  }}
                >
                  {item.iconUrl ? (
                    <img
                      src={item.iconUrl}
                      alt=""
                      className="absolute inset-0 h-full w-full object-cover"
                    />
                  ) : (
                    <div
                      className="absolute inset-0 flex items-center justify-center text-[16px] font-semibold"
                      style={{ color: 'rgba(255,226,238,.65)' }}
                    >
                      {item.initial}
                    </div>
                  )}
                </div>
                <div
                  className="absolute truncate text-[15.5px] font-semibold tracking-[-.005em] text-white"
                  style={{ left: '16px', right: '16px', top: '74px' }}
                >
                  {item.name}
                </div>
                <div
                  className="absolute text-[13px] font-medium"
                  style={{ left: '16px', top: '100px', color: 'rgba(255,224,238,.85)' }}
                >
                  {item.typeLabel}
                </div>
                <div
                  className="absolute text-[13px]"
                  style={{ left: '16px', top: '123px', color: 'rgba(255,236,246,.7)' }}
                >
                  {item.unit}
                </div>
                {/* 내 에이전트에만 MY 칩을 붙인다 */}
                {item.mine && (
                  <div
                    className="absolute text-[12px] font-bold tracking-[-.005em]"
                    style={{
                      right: '12px',
                      top: '120px',
                      height: '20px',
                      padding: '0 8px',
                      lineHeight: '20px',
                      borderRadius: '10px',
                      background: 'rgba(255,190,220,.26)',
                      color: 'rgba(255,240,248,.95)',
                    }}
                  >
                    MY
                  </div>
                )}
                <div
                  onClick={item.info}
                  title="항목 정보"
                  style={{
                    right: '16px',
                    top: '12px',
                    width: '24px',
                    height: '24px',
                    borderRadius: '12px',
                    background: `${item.infoBg}`,
                    zIndex: '2',
                  }}
                  className="absolute flex cursor-pointer items-center justify-center text-[13px] font-bold text-white transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.34)]"
                >
                  i
                </div>
                {item.infoOpen && (
                  <div
                    className="absolute inset-0 box-border flex cursor-pointer flex-col"
                    onClick={item.info}
                    style={{
                      padding: '14px',
                      borderRadius: '12px',
                      background: 'rgba(20,11,36,.94)',
                      backdropFilter: 'blur(20px) saturate(120%)',
                      WebkitBackdropFilter: 'blur(20px) saturate(120%)',
                      gap: '6px',
                      animation: 'fadeIn .16s ease both',
                      zIndex: '3',
                    }}
                  >
                    <div className="text-[14.5px] font-semibold tracking-[-.005em] text-white">
                      {item.name}
                    </div>
                    <div className="flex justify-between text-[13px]">
                      <span style={{ color: 'rgba(255,236,246,.72)' }}>총 실행 횟수</span>
                      <span className="font-semibold text-white">{item.uses}</span>
                    </div>
                    <div className="flex justify-between text-[13px]">
                      <span style={{ color: 'rgba(255,236,246,.72)' }}>실행 단가</span>
                      <span className="font-semibold text-white">{item.unit}</span>
                    </div>
                    <div className="flex justify-between text-[13px]">
                      <span style={{ color: 'rgba(255,236,246,.72)' }}>만든 사람</span>
                      <span className="font-semibold text-white">{item.owner.nickname}</span>
                    </div>
                    <div
                      className="text-[12.5px]"
                      style={{
                        marginTop: '3px',
                        lineHeight: '1.45',
                        color: 'rgba(255,236,246,.78)',
                      }}
                    >
                      {item.summary ?? '설명이 없습니다'}
                    </div>
                  </div>
                )}
              </div>
            </Fragment>
          ))}
          <div ref={sentinelRef} style={{ gridColumn: '1 / -1', height: '1px' }}></div>
        </div>
      </SidePanel>

      {/* 이름과 판매 여부를 한 줄로 묶어 작업면(0~980) 가운데에 맞춘다 —
          각각 left 를 주면 알약 글자 수에 따라 묶음이 좌우로 흔들린다. */}
      <div
        className="absolute flex items-center justify-center"
        style={{ left: '0px', top: '18px', width: '980px', gap: '12px' }}
      >
        <div
          className="relative flex-none"
          style={{
            // 바탕을 진하게 하고 테두리를 둘러 입력창이라는 것이 드러나게 한다.
            width: '300px',
            height: '46px',
            borderRadius: '12px',
            background: 'rgba(30,14,46,.78)',
            boxShadow: 'inset 0 0 0 1px rgba(255,255,255,.22), 0 6px 18px rgba(10,4,20,.3)',
            backdropFilter: 'blur(14px)',
            WebkitBackdropFilter: 'blur(14px)',
            animation: 'uiIn .7s .44s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          <input
            className="absolute rounded-md border-0 bg-transparent font-[inherit] text-[17px] font-semibold tracking-[-.005em] text-white outline-0 transition-colors duration-[160ms] hover:bg-white/10 focus:bg-white/14"
            defaultValue="문서 정리"
            aria-label="워크플로우 이름"
            style={{ left: '14px', top: '9px', width: '245px', height: '28px', padding: '0 6px' }}
          />
          <svg
            className="pointer-events-none absolute"
            width="17"
            height="17"
            viewBox="0 0 14 14"
            fill="none"
            style={{ left: '269px', top: '15px' }}
          >
            <path
              d="M0 12.5L1.5 8L9.5 0L12.5 3L4.5 11L0 12.5ZM0 12.5L3.7 11.8"
              stroke="rgba(255,255,255,.6)"
              strokeWidth="1.4"
              strokeLinejoin="round"
            />
          </svg>
        </div>

        <div
          title={
            canSell
              ? '실행이 끝나면 완료 화면에서 마켓에 등록할 수 있어요'
              : '다른 사람의 에이전트가 섞여 있어 마켓에 등록할 수 없습니다'
          }
          style={{
            height: '28px',
            padding: '0 12px',
            borderRadius: '14px',
            gap: '7px',
            background: 'rgba(30,14,46,.78)',
            boxShadow: 'inset 0 0 0 1px rgba(255,255,255,.22)',
            backdropFilter: 'blur(14px)',
            WebkitBackdropFilter: 'blur(14px)',
            animation: 'uiIn .7s .58s cubic-bezier(.2,.8,.25,1) both',
          }}
          className="flex flex-none items-center whitespace-nowrap"
        >
          <StatusDot color={canSell ? '#a8ddc0' : '#e79a9a'} />
          <div
            className="text-[14.5px] font-bold tracking-[-.005em]"
            style={{ color: 'rgba(255,255,255,.9)' }}
          >
            {canSell ? '판매 등록 가능' : '판매 등록 불가'}
          </div>
        </div>
      </div>
    </>
  )
}
