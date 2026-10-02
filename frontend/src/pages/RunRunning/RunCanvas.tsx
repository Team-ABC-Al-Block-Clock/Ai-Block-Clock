import type { CSSProperties } from 'react'

import CanvasEdgeLayer from '@/components/canvas/CanvasEdgeLayer'
import CanvasNode from '@/components/canvas/CanvasNode'
import CanvasPane from '@/components/canvas/CanvasPane'
import ControlNode from '@/components/canvas/ControlNode'
import type { CanvasNodeData } from '@/lib/canvas/canvasWorkflow'
import { PANEL_CLIP, PANE_HEIGHT, PANE_WIDTH } from '@/lib/canvas/pane'
import { viewportTransform, type Viewport } from '@/lib/canvas/viewport'

interface RunNode extends CanvasNodeData {
  background: string
  numberColor: string
  nameColor: string
  sublabelColor: string
  stateLabel: string
}

interface RunCanvasProps {
  nodes: RunNode[]
  edges: { id: string; d: string; stroke: string }[]
  /** 지나간 선. 위로 흐르는 점선을 한 겹 더 그린다. */
  liveEdges: { id: string; d: string }[]
  glowStyle: CSSProperties
  camera: Viewport
  chainAt: Map<string, number> | null
  branchCondition: (node: CanvasNodeData) => string
  branchVerdict: (node: CanvasNodeData) => string | null
}

/** 실행 화면의 작업면. 도는 카드를 따라가는 카메라 안에 선 · 카드 · 빛을 그린다. */
export default function RunCanvas({
  nodes,
  edges,
  liveEdges,
  glowStyle,
  camera,
  chainAt,
  branchCondition,
  branchVerdict,
}: RunCanvasProps) {
  return (
    <CanvasPane>
      {/*
        자르는 겹과 미는 겹을 나눈다. 한 요소에 같이 걸면 `clip-path` 가 `transform` 을 따라
        움직여 자르는 선까지 옆으로 밀린다 — 오른쪽에서 들어오는 카드가 미리 잘려 안 보인다.
        바깥은 패널 앞에 고정해 자르고, 안쪽만 카메라로 민다.
      */}
      <div
        className="absolute top-0 left-0"
        style={{
          width: `${PANE_WIDTH}px`,
          height: `${PANE_HEIGHT}px`,
          clipPath: PANEL_CLIP,
          pointerEvents: 'none',
        }}
      >
        <div
          className="absolute top-0 left-0"
          style={{
            width: `${PANE_WIDTH}px`,
            height: `${PANE_HEIGHT}px`,
            transform: viewportTransform(camera),
            transformOrigin: '0 0',
            transition: 'transform .6s cubic-bezier(.2,.8,.25,1)',
          }}
        >
          {/* 빛은 노드 좌표에 그려진다 — 카메라 겹 안에 둬야 카드와 같이 밀린다. */}
          <div style={glowStyle}></div>

          <CanvasEdgeLayer clip={false}>
            <g style={{ animation: 'fadeIn .5s .5s ease backwards' }}>
              {edges.map((e) => (
                <path key={e.id} d={e.d} fill="none" stroke={e.stroke} strokeWidth="3.4" />
              ))}
              {liveEdges.map((e) => (
                <path
                  key={e.id}
                  d={e.d}
                  fill="none"
                  stroke="rgba(255,224,238,.85)"
                  strokeWidth="3.4"
                  strokeLinecap="round"
                  strokeDasharray="3 18"
                  style={{ animation: 'dash 1.5s linear infinite' }}
                />
              ))}
            </g>
          </CanvasEdgeLayer>

          {nodes.map((n) => {
            const kind = n.controlType ?? n.ioType
            if (kind)
              return (
                <ControlNode
                  key={n.id}
                  kind={kind}
                  sublabel={n.controlType ? branchCondition(n) : undefined}
                  note={(n.controlType && branchVerdict(n)) || undefined}
                  x={n.x}
                  y={n.y}
                  floaty={n.floaty}
                  enter={n.enter}
                  glass
                />
              )
            return (
              <CanvasNode
                key={n.id}
                index={chainAt?.get(n.id)}
                name={n.name}
                sublabel={n.stateLabel}
                x={n.x}
                y={n.y}
                mine={n.mine}
                floaty={n.floaty}
                enter={n.enter}
                background={n.background}
                numberColor={n.numberColor}
                nameColor={n.nameColor}
                sublabelColor={n.sublabelColor}
                glass
              />
            )
          })}
        </div>
      </div>
    </CanvasPane>
  )
}
