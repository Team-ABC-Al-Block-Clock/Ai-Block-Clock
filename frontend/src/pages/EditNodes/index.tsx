import { Fragment, type CSSProperties } from 'react'

import CanvasEdgeLayer from '@/components/CanvasEdgeLayer'
import CanvasNode from '@/components/CanvasNode'
import CanvasPane from '@/components/CanvasPane'
import CanvasWorld from '@/components/CanvasWorld'
import SidePanel, { PANEL_INSET } from '@/components/SidePanel'
import StatusDot from '@/components/StatusDot'
import { useCanvasWorkflow } from '@/hooks/useCanvasWorkflow'
import { DND_TYPE, useNodePalette } from '@/hooks/useNodePalette'
import { nodeTypeLabel } from '@/lib/format'
import { paths } from '@/routes/paths'

import NodeInfoPanel from './NodeInfoPanel'
import NodePalettePanel from './NodePalettePanel'

/**
 * 노드 편집 화면.
 *
 * 캔버스와 팔레트는 각자 훅이 들고 있다. 여기는 그 둘을 그리고, 팔레트에서
 * 캔버스로 건너오는 놓기만 잇는다.
 */
export default function EditNodes() {
  const canvas = useCanvasWorkflow()
  const palette = useNodePalette()

  const {
    nodes,
    geometry,
    selId,
    hovId,
    selEdge,
    dragId,
    linkFrom,
    canSell,
    portOpen,
    onHover,
    onSelectEdge,
    handleSelect,
    handleHoverOut,
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
    view,
    zoomLabel,
    zoomIn,
    zoomOut,
  } = canvas
  const { paneRef, worldRef, gridRef, glowRef, tempRef } = canvas.refs
  const { panelOpen, q, cards, isEmpty, isError, handleTogglePanel, handleSearch } = palette
  const { isFetchNextPageError, isFetchingNextPage, handleRetryNextPage } = palette

  const handleDragOver = (ev: React.DragEvent<HTMLDivElement>) => {
    if (!ev.dataTransfer.types.includes(DND_TYPE)) return
    // 막지 않으면 브라우저가 drop 을 내지 않는다.
    ev.preventDefault()
    ev.dataTransfer.dropEffect = 'copy'
  }

  /** 팔레트에서 캔버스로 건너오는 것은 항목 id 하나뿐이다. 나머지는 여기서 되찾는다. */
  const handleDrop = (ev: React.DragEvent<HTMLDivElement>) => {
    const itemId = ev.dataTransfer.getData(DND_TYPE)
    if (!itemId) return
    ev.preventDefault()
    const item = palette.findItem(itemId)
    const point = item && canvas.toCanvasPoint(ev.clientX, ev.clientY)
    if (!item || !point) return
    canvas.placeFromPalette({ name: item.name, nodeType: item.nodeType, mine: item.mine }, point)
  }

  const n = nodes.find((node) => node.id === selId)
  const g = nodes.find((node) => node.id === hovId) ?? n // 빛은 호버 중인 노드를 따라간다
  const selRing: CSSProperties = g
    ? {
        position: 'absolute',
        left: `${g.x - 16}px`,
        top: `${g.y - 16}px`,
        width: '200px',
        height: '200px',
        borderRadius: '50%',
        pointerEvents: 'none',
        opacity: hovId && hovId !== selId ? 0.6 : 1,
        // 카드가 호버에 5px 떠오른다. 빛도 같은 만큼 같은 시간에 따라가야 붙어 보인다.
        transform: `translateY(${hovId && !dragId ? -5 : 0}px)`,
        background:
          'radial-gradient(circle, rgba(255,160,200,.30) 0 42%, rgba(255,160,200,.14) 62%, rgba(255,160,200,0) 76%)',
        // 자리는 전환 없이 노드에 붙인다. 미끄러지면 손을 뗄 때 빛만 따로 날아간다.
        transition: 'transform .2s,opacity .25s ease',
      }
    : { display: 'none' }
  const hasSelection = !!n
  const selName = n ? n.name : ''
  const selInitial = n ? n.name.trim().charAt(0) : ''
  const selTypeLabel = n ? nodeTypeLabel(n.nodeType) : ''

  return (
    <>
      <CanvasPane
        ref={paneRef}
        gridRef={gridRef}
        view={view}
        onPointerDown={handlePaneDown}
        onPointerMove={handlePaneMove}
        onPointerUp={handlePaneUp}
        onPointerCancel={handlePaneCancel}
        onDragOver={handleDragOver}
        onDrop={handleDrop}
      >
        <div
          className="pointer-events-none absolute left-0 text-center text-[16px] tracking-[-.005em]"
          style={{
            top: 'calc(74px - var(--edge-y, 0px))',
            width: '980px',
            color: 'rgba(255,240,248,.78)',
            animation: 'uiIn .8s .5s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          드래그하여 노드를 배치하고, 선으로 연결합니다
        </div>

        {(selEdge || n) && (
          <div
            className="pointer-events-none absolute left-0 flex items-center justify-center text-[14px]"
            style={{
              top: 'calc(102px - var(--edge-y, 0px))',
              width: '980px',
              gap: '7px',
              color: 'rgba(255,214,234,.82)',
              animation: 'uiIn .3s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            <StatusDot size={6} color="rgba(255,190,220,.9)" />
            {selEdge ? '연결선 선택됨' : '노드 선택됨'} · Delete 로 삭제
          </div>
        )}

        <CanvasWorld view={view} ref={worldRef}>
          <CanvasEdgeLayer clip={false}>
            <defs>
              <linearGradient id="edgeOn" x1="0" y1="0" x2="1" y2="0">
                <stop offset="0%" stopColor="#e3b7c8" stopOpacity=".9" />
                <stop offset="100%" stopColor="#dba18b" stopOpacity=".8" />
              </linearGradient>
            </defs>
            {/* 노드가 다 떠오른 뒤에 잇는다. 먼저 나오면 빈자리에 선만 떠 있다. */}
            <g style={{ animation: 'fadeIn .5s .5s ease backwards' }}>
              {geometry.map((edge, i) => (
                <Fragment key={edge.id}>
                  <path
                    data-edge={edge.id}
                    d={edge.d}
                    fill="none"
                    stroke={selEdge === edge.id ? 'rgba(255,214,234,.95)' : 'url(#edgeOn)'}
                    strokeWidth={selEdge === edge.id ? 5 : 3.4}
                  />
                  <path
                    data-edge={edge.id}
                    d={edge.d}
                    fill="none"
                    stroke="rgba(255,224,238,.85)"
                    strokeWidth="3.4"
                    strokeLinecap="round"
                    strokeDasharray="3 18"
                    style={{ animation: `dash ${1.4 + i * 0.1}s ${i * 0.2}s linear infinite` }}
                  />
                  {/* 선이 얇아 그대로는 못 누른다. 굵은 투명선에 판정만 맡긴다. */}
                  <path
                    data-edge={edge.id}
                    d={edge.d}
                    fill="none"
                    stroke="transparent"
                    strokeWidth="18"
                    style={{ pointerEvents: 'stroke', cursor: 'pointer' }}
                    onPointerDown={(ev) => {
                      ev.stopPropagation()
                      onSelectEdge(edge.id)
                    }}
                  />
                </Fragment>
              ))}
            </g>
          </CanvasEdgeLayer>

          {/* 판정은 흘려보내고 카드가 제 몫만 다시 켠다. 자르기는 `CanvasWorld` 가 한다. */}
          <div className="pointer-events-none absolute inset-0">
            <div ref={glowRef} style={selRing}></div>

            {nodes.map((node, i) => (
              <CanvasNode
                key={node.id}
                index={i}
                name={node.name}
                sublabel={nodeTypeLabel(node.nodeType)}
                x={node.x}
                y={node.y}
                mine={node.mine}
                floaty={node.floaty}
                enter={node.enter}
                background={node.id === selId ? 'rgba(255,255,255,.16)' : 'rgba(255,255,255,.11)'}
                sublabelColor="rgba(230,196,212,.85)"
                title={
                  node.mine
                    ? undefined
                    : '다른 사람의 에이전트 — 이 구성은 마켓에 등록할 수 없습니다'
                }
                nodeId={node.id}
                dragging={dragId === node.id}
                onSelect={handleSelect}
                onPointerDown={handleNodePointerDown(node.id)}
                onPortDown={handlePortDown(node.id)}
                portDim={(side) => !portOpen(node.id, side)}
                // 노드 이동은 grab ref, 선 연결은 linkFrom state 로 각자 가른다.
                onPointerMove={(ev) => {
                  handleNodePointerMove(ev)
                  handlePortMove(ev)
                }}
                onPointerUp={(ev) => {
                  handleNodePointerUp()
                  handlePortUp(ev)
                }}
                onPointerCancel={handlePointerCancel}
                onHover={() => onHover(node.id)}
                onHoverOut={handleHoverOut}
              />
            ))}
          </div>

          {/* 끄는 중인 선은 카드 위에 둔다 — 이어진 선과 달리 카드를 피해 가지 않아서,
            아래에 깔면 겨냥하는 카드에 가려 끝이 보이지 않는다. 판 전체가 판정을 흘린다. */}
          {linkFrom && (
            <CanvasEdgeLayer clip={false}>
              <path
                ref={tempRef}
                fill="none"
                stroke="rgba(255,224,238,.7)"
                strokeWidth="3.4"
                strokeLinecap="round"
                strokeDasharray="6 10"
              />
            </CanvasEdgeLayer>
          )}
        </CanvasWorld>

        <div
          className="absolute flex items-center overflow-hidden"
          style={{
            left: 'calc(32px - var(--edge, 0px))',
            top: 'calc(952px + var(--edge-y, 0px))',
            height: '36px',
            borderRadius: '18px',
            background: 'rgba(14,7,28,.55)',
            backdropFilter: 'blur(18px)',
            WebkitBackdropFilter: 'blur(18px)',
            animation: 'uiIn .7s .56s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          <div
            onClick={zoomOut}
            title="축소"
            style={{ width: '41px', height: '36px', color: 'rgba(255,255,255,.82)' }}
            className="flex cursor-pointer items-center justify-center text-[17px] font-semibold hover:bg-[rgba(255,255,255,.12)]"
          >
            −
          </div>
          <div
            className="flex items-center justify-center text-[15px] font-semibold"
            style={{ width: '67px', height: '36px', color: 'rgba(255,255,255,.82)' }}
          >
            {zoomLabel}
          </div>
          <div
            onClick={zoomIn}
            title="확대"
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
        )}

        <a
          href={paths.run.waiting.withoutId}
          style={{
            left: 'calc(800px + var(--edge, 0px))',
            top: 'calc(943px + var(--edge-y, 0px))',
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
      </CanvasPane>

      <SidePanel>
        <div
          className="absolute text-[17px] font-semibold tracking-[-.005em] text-white"
          style={{ left: `${PANEL_INSET}px`, top: `${PANEL_INSET}px` }}
        >
          노드 정보
        </div>

        <NodeInfoPanel
          hasSelection={hasSelection}
          name={selName}
          initial={selInitial}
          typeLabel={selTypeLabel}
        />

        <NodePalettePanel
          q={q}
          cards={cards}
          isEmpty={isEmpty}
          isError={isError}
          isFetchNextPageError={isFetchNextPageError}
          isFetchingNextPage={isFetchingNextPage}
          onSearch={handleSearch}
          onRetryNextPage={handleRetryNextPage}
          refs={palette.refs}
        />
      </SidePanel>

      {/* 이름과 판매 여부를 한 줄로 묶어 작업면(0~980) 가운데에 맞춘다 —
          각각 left 를 주면 알약 글자 수에 따라 묶음이 좌우로 흔들린다. */}
      <div
        className="absolute flex items-center justify-center"
        style={{
          left: '0px',
          top: 'calc(18px - var(--edge-y, 0px))',
          width: '980px',
          gap: '12px',
        }}
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
