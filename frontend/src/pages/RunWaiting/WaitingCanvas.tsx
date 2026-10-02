import { useMemo } from 'react'

import CanvasEdgeLayer from '@/components/canvas/CanvasEdgeLayer'
import CanvasNode from '@/components/canvas/CanvasNode'
import CanvasPane from '@/components/canvas/CanvasPane'
import ControlNode from '@/components/canvas/ControlNode'
import { describeCondition } from '@/lib/canvas/branchCondition'
import {
  edgeGeometry,
  nodesBounds,
  type CanvasEdge,
  type CanvasNodeData,
} from '@/lib/canvas/canvasWorkflow'
import { PANEL_CLIP, PANE_HEIGHT, PANE_WIDTH } from '@/lib/canvas/pane'
import { fitVerticalViewport, viewportTransform } from '@/lib/canvas/viewport'

interface WaitingCanvasProps {
  nodes: CanvasNodeData[]
  edges: CanvasEdge[]
  chainAt: Map<string, number> | null
}

/**
 * 실행을 기다리는 구성. 진행 화면과 달리 카메라가 없다. 여섯 번째부터는 패널 앞에서 잘려
 * 보이지 않는데, 여기서 할 일은 입력과 실행이지 구성 확인이 아니다 — 방금 편집기에서 만들고
 * 온 것이고, 무엇이 도는지는 실행 견적 카드가 노드마다 이름과 단가로 보여 준다.
 */
export default function WaitingCanvas({ nodes, edges, chainAt }: WaitingCanvasProps) {
  // 경로 탐색은 무겁고 자리표시자는 바뀌지 않는다. 렌더마다 다시 찾지 않는다.
  const geometry = useMemo(() => edgeGeometry(nodes, edges), [nodes, edges])
  const view = useMemo(() => fitVerticalViewport(nodesBounds(nodes), PANE_HEIGHT), [nodes])

  return (
    <CanvasPane>
      {/* 패널은 유리라 뒤가 비친다. 카드와 선을 같은 선에서 자른다. */}
      <div
        className="absolute top-0 left-0"
        style={{
          width: `${PANE_WIDTH}px`,
          height: `${PANE_HEIGHT}px`,
          clipPath: PANEL_CLIP,
          pointerEvents: 'none',
        }}
      >
        <div style={{ transform: viewportTransform(view), transformOrigin: '0 0' }}>
          <CanvasEdgeLayer clip={false}>
            <g style={{ animation: 'fadeIn .5s .5s ease backwards' }}>
              {geometry.map((edge) => (
                <path
                  key={edge.id}
                  d={edge.d}
                  fill="none"
                  stroke="rgba(255,255,255,.26)"
                  strokeWidth="3.4"
                />
              ))}
            </g>
          </CanvasEdgeLayer>

          {nodes.map((node) => {
            // 제어·입출력은 돌지 않는다. 「대기」를 붙이면 차례를 기다리는 것처럼 보인다.
            const kind = node.controlType ?? node.ioType
            if (kind)
              return (
                <ControlNode
                  key={node.id}
                  kind={kind}
                  sublabel={
                    node.controlType
                      ? describeCondition(
                          node.condition,
                          (id) => nodes.find((it) => it.id === id)?.name,
                        )
                      : undefined
                  }
                  x={node.x}
                  y={node.y}
                  floaty={node.floaty}
                  enter={node.enter}
                />
              )
            return (
              <CanvasNode
                key={node.id}
                index={chainAt?.get(node.id)}
                name={node.name}
                sublabel="대기"
                x={node.x}
                y={node.y}
                mine={node.mine}
                floaty={node.floaty}
                enter={node.enter}
                background="rgba(255,255,255,.075)"
                numberColor="rgba(255,255,255,.9)"
              />
            )
          })}
        </div>
      </div>
    </CanvasPane>
  )
}
