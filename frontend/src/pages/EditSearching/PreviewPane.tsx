import CanvasNode from '@/components/canvas/CanvasNode'
import CanvasWorld from '@/components/canvas/CanvasWorld'
import type { CanvasNodeData } from '@/lib/canvas/canvasWorkflow'
import type { Viewport } from '@/lib/canvas/viewport'

import { PANE_TEXT_WIDTH } from './config'

interface PreviewPaneProps {
  preview: CanvasNodeData[]
  view: Viewport
  hasPicked: boolean
  onUnpick: (agentId: string) => void
}

/** 고른 카드를 편집 화면과 같은 작업면에 올려 둔다. 누르면 내린다. */
export default function PreviewPane({ preview, view, hasPicked, onUnpick }: PreviewPaneProps) {
  return (
    <>
      <div
        className="pointer-events-none absolute left-0 text-center text-[16px] tracking-[-.005em]"
        style={{
          top: 'calc(74px - var(--edge-y, 0px))',
          width: `${PANE_TEXT_WIDTH}px`,
          color: 'rgba(255,240,248,.78)',
          animation: 'uiIn .8s .3s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        {hasPicked ? '선택한 순서대로 연결됩니다' : '오른쪽에서 카드를 선택하세요'}
      </div>

      <CanvasWorld view={view}>
        {preview.map((node, i) => (
          <CanvasNode
            key={node.id}
            index={i}
            name={node.name}
            sublabel="에이전트"
            x={node.x}
            y={node.y}
            mine={node.mine}
            floaty={node.floaty}
            enter={node.enter}
            background="rgba(255,255,255,.11)"
            sublabelColor="rgba(230,196,212,.85)"
            title="누르면 캔버스에서 내립니다"
            onSelect={() => onUnpick(node.id)}
          />
        ))}
      </CanvasWorld>
    </>
  )
}
