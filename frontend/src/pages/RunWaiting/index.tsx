import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { ApiError } from '@/api/errors'
import { RUN_FILE_LIMITS } from '@/api/executions'
import RefFileField from '@/components/RefFileField'
import SidePanel, { PANEL_CONTENT_WIDTH, PANEL_INSET } from '@/components/SidePanel'
import WorkflowLoadNotice from '@/components/WorkflowLoadNotice'
import { useSavedWorkflow } from '@/hooks/canvas/useSavedWorkflow'
import { chainPositions, type CanvasEdge, type CanvasNodeData } from '@/lib/canvas/canvasWorkflow'

import { FILE_Y, INSUFFICIENT, PROMPT_EXAMPLE_MAX } from './config'
import CostCard from './CostCard'
import EditBackLink from './EditBackLink'
import PromptField from './PromptField'
import RunAction from './RunAction'
import { clamp, itemsNoticeOf, leadAgentOf, runNoticeOf } from './runWaitingState'
import StepCostList from './StepCostList'
import { useCostEstimate } from './useCostEstimate'
import { useRunFile } from './useRunFile'
import { useRunStart } from './useRunStart'
import WaitingCanvas from './WaitingCanvas'

/** 워크플로우를 받지 못했을 때. 빈 캔버스를 그린다 — 남의 구성을 대신 보여 주지 않는다. */
const EMPTY_NODES: CanvasNodeData[] = []
const EMPTY_EDGES: CanvasEdge[] = []

export default function RunWaiting() {
  // 어떤 워크플로우를 돌리는지. 편집 화면이 저장한 뒤 넘겨준다.
  const [searchParams] = useSearchParams()
  const workflowId = searchParams.get('workflowId') ?? undefined
  const loaded = useSavedWorkflow(workflowId)

  // 저장된 것이 있으면 그것을, 없으면 자리표시자를 그린다 — 상세 화면처럼 id 없이 들어오는 길이 있다.
  const nodes = loaded.canvas?.nodes ?? EMPTY_NODES
  const edges = loaded.canvas?.edges ?? EMPTY_EDGES
  const chainAt = useMemo(() => chainPositions(nodes, edges), [nodes, edges])
  const leadAgent = useMemo(() => leadAgentOf(nodes, chainAt), [nodes, chainAt])

  /**
   * 입력칸을 React 가 들고 있는다. DOM 리스너로 붙이면 안내를 거쳐 입력칸이 사라졌다 돌아올 때
   * 리스너도 값도 어긋난다 — 빈 칸인데 실행이 켜져 있는 상태가 된다.
   */
  const [prompt, setPrompt] = useState('')
  const filled = prompt.trim().length > 0

  const { file, slot } = useRunFile()
  const estimate = useCostEstimate(workflowId)
  const start = useRunStart({ workflowId, prompt, file })

  /** 워크플로우가 없으면 돌릴 것이 없다. 견적도 못 받아 `blocked` 로는 걸러지지 않는다. */
  const on = !!workflowId && filled && !estimate.blocked && !start.isPending
  const handleRun = () => {
    if (on) start.mutate()
  }

  // 없는 워크플로우는 실행할 수도 없다. 편집 화면과 같은 안내로 끊는다.
  if (loaded.error) return <WorkflowLoadNotice error={loaded.error} onRetry={loaded.refetch} />

  return (
    <>
      <WaitingCanvas nodes={nodes} edges={edges} chainAt={chainAt} />

      <SidePanel>
        <div
          className="absolute text-[20px] font-bold tracking-[-.02em] text-white"
          style={{ left: `${PANEL_INSET}px`, top: '38px' }}
        >
          워크플로우 실행
        </div>
        <EditBackLink workflowId={workflowId} />

        <PromptField
          greeting={leadAgent?.onboardingMessage ?? ''}
          // 서버가 500자까지 받는 값이라 placeholder 자리에 넘친다. 세 줄에 담기는 만큼만 쓴다.
          example={clamp(leadAgent?.usageExample ?? '', PROMPT_EXAMPLE_MAX)}
          value={prompt}
          onChange={setPrompt}
        />

        {/* 첫 단계가 읽을 파일. 편집 화면의 참고 자료 칸과 같은 차림이다. */}
        <RefFileField
          slot={slot}
          accept={RUN_FILE_LIMITS.extensions.join(',')}
          multiple={false}
          emptyHint="엑셀 또는 CSV 파일 (선택)"
          style={{
            position: 'absolute',
            left: `${PANEL_INSET}px`,
            top: `${FILE_Y}px`,
            width: `${PANEL_CONTENT_WIDTH}px`,
          }}
        />

        <CostCard
          cost={estimate.cost}
          ratio={estimate.ratio}
          shortage={estimate.shortage}
          blocked={estimate.blocked}
        />

        <RunAction
          on={on}
          pending={start.isPending}
          notice={runNoticeOf(start.error, workflowId)}
          failed={!!start.error}
          insufficient={start.error instanceof ApiError && start.error.code === INSUFFICIENT}
          onRun={handleRun}
        />

        <StepCostList
          items={estimate.items}
          notice={itemsNoticeOf({
            workflowId,
            pending: estimate.pending,
            failed: estimate.failed,
            count: estimate.items.length,
          })}
        />
      </SidePanel>
    </>
  )
}
