import type { AiModel, NodeOutput } from '@/api/nodes'
import type { RefFileSlot } from '@/hooks/canvas/useNodeRefFiles'
import type { NodeValues } from '@/lib/canvas/canvasReducer'
import type { CanvasCondition } from '@/lib/canvas/canvasWorkflow'

import AgentInfo from './AgentInfo'
import BranchForm from './BranchForm'
import type { ConditionSource } from './branchFormState'
import { rowStyle } from './layout'
import NodeForm from './NodeForm'

type NodeInfoPanelProps = {
  /** 캔버스에서 고른 노드가 있는가. */
  hasSelection: boolean
  name: string
  /** 이름 첫 글자. 아이콘이 아직 없어 이것으로 대신한다. */
  initial: string
  typeLabel: string
  /**
   * 고른 것이 캔버스에서 적은 기본 노드일 때의 값.
   *
   * 에이전트면 undefined — 남의 내부 구성이라 열지 않고 한 줄 설명만 보여준다.
   */
  basic?: {
    id: string
    name: string
    prompt: string
    model: AiModel
    output: NodeOutput | null
    price: string | null
  }
  /** 고른 기본 노드에 붙은 참고 자료. 에이전트면 쓰지 않는다 */
  files: RefFileSlot
  /** 에이전트의 한 줄 설명. */
  summary?: string | null
  /** 에이전트의 단가·실행 횟수·만든 사람. 모르는 값은 비워 둔다. */
  meta?: { price: string | null; executionCount?: number | null; owner?: string | null }
  /** 고른 것이 분기 노드일 때. `sources` 는 어느 길로 와도 앞에 있는 노드·에이전트다. */
  branch?: { id: string; condition: CanvasCondition | null; sources: ConditionSource[] }
  /** 고친 조건을 캔버스에 돌려준다. */
  onApplyCondition: (id: string, condition: CanvasCondition) => void
  /** 고친 값을 캔버스에 돌려준다. 저장은 워크플로우를 저장할 때 서버로 함께 나간다. */
  onApply: (id: string, values: NodeValues) => void
}

/**
 * 「노드 정보」 탭 — 고른 노드를 살펴보고 고치는 자리. 좌표는 탭 아래 스크롤 영역 기준이다.
 *
 * 고른 것의 종류에 따라 분기 조건 폼 · 기본 노드 폼 · 에이전트 정보 중 하나를 세운다.
 * 고친 값은 캔버스에만 머문다. 기본 노드는 워크플로우를 저장할 때 배치와 함께
 * 서버에 만들어지기 때문이다(`NodePlacementRequest`).
 */
export default function NodeInfoPanel({
  hasSelection,
  name,
  initial,
  typeLabel,
  basic,
  files,
  summary,
  meta,
  branch,
  onApply,
  onApplyCondition,
}: NodeInfoPanelProps) {
  if (!hasSelection)
    return (
      <div
        className="absolute text-[15.5px]"
        style={{ ...rowStyle('28px'), color: 'rgba(255,236,246,.66)' }}
      >
        캔버스에서 노드를 선택하면 상세 정보를 조회할 수 있습니다
      </div>
    )

  // 분기는 이름이 종류로 정해져 있어 고칠 것이 조건뿐이다.
  if (branch)
    return (
      <BranchForm
        key={branch.id}
        id={branch.id}
        head={{ initial, name, typeLabel }}
        initial={branch.condition}
        sources={branch.sources}
        onApply={(condition) => onApplyCondition(branch.id, condition)}
      />
    )

  // 기본 노드는 폼이 이름을 들고 있어 머리 카드도 종류 줄도 겹친다. 폼만 세운다.
  if (basic)
    return (
      <NodeForm
        key={basic.id}
        initial={basic}
        price={basic.price}
        files={files}
        onApply={(values) => onApply(basic.id, values)}
      />
    )

  return <AgentInfo head={{ initial, name, typeLabel }} summary={summary} meta={meta} />
}
