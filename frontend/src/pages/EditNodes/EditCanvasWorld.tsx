import { Fragment, type CSSProperties } from 'react'

import CanvasEdgeLayer from '@/components/canvas/CanvasEdgeLayer'
import CanvasNode, { type CanvasNodeProps } from '@/components/canvas/CanvasNode'
import ControlNode from '@/components/canvas/ControlNode'
import CanvasWorld from '@/components/canvas/CanvasWorld'
import type { useCanvasWorkflow } from '@/hooks/canvas/useCanvasWorkflow'
import { describeCondition } from '@/lib/canvas/branchCondition'
import { priceLabel } from '@/lib/format'

interface EditCanvasWorldProps {
  canvas: ReturnType<typeof useCanvasWorkflow>
  /** 카드 순번. 갈래가 있으면 차례가 없으므로 `null` 이고 번호를 감춘다. */
  chainAt: Map<string, number> | null
}

/** 편집 화면의 작업면 속 — 이어진 선, 카드, 선택 빛, 끄는 중인 선. */
export default function EditCanvasWorld({ canvas, chainAt }: EditCanvasWorldProps) {
  const {
    nodes,
    geometry,
    selId,
    hovId,
    selEdge,
    dragId,
    linkFrom,
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
    view,
  } = canvas
  const { worldRef, glowRef, tempRef } = canvas.refs

  const g = nodes.find((node) => node.id === hovId) ?? nodes.find((node) => node.id === selId) // 빛은 호버 중인 노드를 따라간다
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

  return (
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

        {nodes.map((node) => {
          // 끌고 놓기와 선 잇기는 종류를 가리지 않는다. 모양만 갈린다.
          const grip: Pick<
            CanvasNodeProps,
            | 'x'
            | 'y'
            | 'floaty'
            | 'enter'
            | 'nodeId'
            | 'dragging'
            | 'onSelect'
            | 'onPointerDown'
            | 'onPortDown'
            | 'portDim'
            | 'onPointerMove'
            | 'onPointerUp'
            | 'onPointerCancel'
            | 'onHover'
            | 'onHoverOut'
          > = {
            x: node.x,
            y: node.y,
            floaty: node.floaty,
            enter: node.enter,
            nodeId: node.id,
            dragging: dragId === node.id,
            onSelect: handleSelect,
            onPointerDown: handleNodePointerDown(node.id),
            onPortDown: handlePortDown(node.id),
            portDim: (side) => !portOpen(node.id, side),
            // 노드 이동은 grab ref, 선 연결은 linkFrom state 로 각자 가른다.
            onPointerMove: (ev) => {
              handleNodePointerMove(ev)
              handlePortMove(ev)
            },
            onPointerUp: (ev) => {
              handleNodePointerUp()
              handlePortUp(ev)
            },
            onPointerCancel: handlePointerCancel,
            onHover: () => onHover(node.id),
            onHoverOut: handleHoverOut,
          }

          const kind = node.controlType ?? node.ioType
          if (kind) {
            return (
              <ControlNode
                key={node.id}
                {...grip}
                kind={kind}
                selected={node.id === selId}
                sublabel={
                  node.controlType
                    ? describeCondition(
                        node.condition,
                        (id) => nodes.find((it) => it.id === id)?.name,
                      )
                    : undefined
                }
              />
            )
          }

          return (
            <CanvasNode
              key={node.id}
              {...grip}
              index={chainAt?.get(node.id)}
              name={node.name}
              sublabel={node.price === null ? '—' : priceLabel(node.price)}
              mine={node.mine}
              background={node.id === selId ? 'rgba(255,255,255,.16)' : 'rgba(255,255,255,.11)'}
              sublabelColor="rgba(230,196,212,.85)"
              title={
                node.mine === false
                  ? '다른 사람의 에이전트 — 이 구성은 마켓에 등록할 수 없습니다'
                  : undefined
              }
            />
          )
        })}
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
  )
}
