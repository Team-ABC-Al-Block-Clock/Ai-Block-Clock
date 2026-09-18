import { NODE_SIZE, type CanvasNodeData } from '@/lib/canvasWorkflow'

/** 원 둘레와 선 사이에 남기는 간격(px). 딱 맞추면 선이 테두리에 붙어 지저분하다. */
const CLEARANCE = 4
/** 노드가 작업면 밖으로도 절반 나가므로 마스크는 넉넉히 덮는다. */
const BLEED = 600

interface CanvasEdgeMaskProps {
  /** `mask="url(#…)"` 로 가리킬 이름. 화면마다 달라야 한다. */
  id: string
  nodes: Pick<CanvasNodeData, 'id' | 'x' | 'y'>[]
}

/**
 * 선이 노드 원 안으로 들어오지 않게 도려낸다.
 *
 * 원은 반투명이라 선이 뒤를 지나가면 그대로 비친다. 원마다 구멍을 뚫어 그 안에서는 선을
 * 그리지 않는다. `<defs>` 안에 둔다.
 */
export default function CanvasEdgeMask({ id, nodes }: CanvasEdgeMaskProps) {
  return (
    <mask
      id={id}
      maskUnits="userSpaceOnUse"
      x={-BLEED}
      y={-BLEED}
      width={1076 + BLEED * 2}
      height={1024 + BLEED * 2}
    >
      <rect x={-BLEED} y={-BLEED} width={1076 + BLEED * 2} height={1024 + BLEED * 2} fill="#fff" />
      {nodes.map((node) => (
        <circle
          key={node.id}
          data-mask-node={node.id}
          cx={node.x + NODE_SIZE / 2}
          cy={node.y + NODE_SIZE / 2}
          r={NODE_SIZE / 2 + CLEARANCE}
          fill="#000"
        />
      ))}
    </mask>
  )
}
