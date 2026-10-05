import { useMemo, useState } from 'react'

import type { RecommendedAgent } from '@/api/agents'
import { nodesBounds, seedCanvasNodes } from '@/lib/canvas/canvasWorkflow'
import { PANE_CONTENT_WIDTH, PANE_HEIGHT } from '@/lib/canvas/pane'
import { INITIAL_VIEWPORT, fitViewport } from '@/lib/canvas/viewport'
import { seedsFrom } from '@/routes/handoff'

import { MAX_PICKED, PREVIEW_ENTER } from './config'
import { togglePicked, withoutPicked } from './pickState'

/** 고른 에이전트와 그것을 작업면에 올린 미리보기. */
export function usePickedAgents() {
  /** 선택한 순서가 캔버스에 놓이는 순서다. Set 으로 두면 순서를 잃는다. */
  const [picked, setPicked] = useState<RecommendedAgent[]>([])

  // 선택한 카드는 다시 찾아도 남는다 — 두 번 찾아 모으는 것이 자연스럽고, 지워 버리면
  // 무엇을 선택했었는지 되찾을 길이 없다.
  const preview = useMemo(
    () =>
      seedCanvasNodes(seedsFrom({ agents: picked }), 'grid').map((node) => ({
        ...node,
        enter: PREVIEW_ENTER,
      })),
    [picked],
  )

  // 미리보기에는 팬·줌이 없다. 선택한 것이 늘어 작업면을 넘치면 그만큼 줄여 다 보이게 한다.
  const previewView = useMemo(() => {
    const bounds = nodesBounds(preview)
    if (!bounds) return INITIAL_VIEWPORT
    return fitViewport(bounds, { width: PANE_CONTENT_WIDTH, height: PANE_HEIGHT })
  }, [preview])

  return {
    picked,
    atLimit: picked.length >= MAX_PICKED,
    preview,
    previewView,
    toggle: (agent: RecommendedAgent) => setPicked((chosen) => togglePicked(chosen, agent)),
    /** 다시 찾으면 앞서 선택한 것이 목록에 없을 수 있다. 그때 무를 길은 미리보기뿐이다. */
    unpick: (agentId: string) => setPicked((chosen) => withoutPicked(chosen, agentId)),
  }
}
