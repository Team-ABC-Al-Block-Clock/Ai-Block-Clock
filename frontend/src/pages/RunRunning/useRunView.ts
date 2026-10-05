import { useMemo } from 'react'

import type { ExecutionStep } from '@/api/executions'
import { useSavedWorkflow } from '@/hooks/canvas/useSavedWorkflow'
import { useRunProgress } from '@/hooks/run/useRunProgress'
import { useEasedPercent } from '@/hooks/useEasedPercent'
import { describeCondition } from '@/lib/canvas/branchCondition'
import {
  canSellWorkflow,
  chainPositions,
  edgeGeometry,
  NODE_SIZE,
  nodesBounds,
  type CanvasEdge,
  type CanvasNodeData,
} from '@/lib/canvas/canvasWorkflow'
import { PANE_CONTENT_WIDTH, PANE_HEIGHT } from '@/lib/canvas/pane'
import { fitVerticalViewport } from '@/lib/canvas/viewport'
import {
  branchTaken,
  cardStatuses,
  edgeWasTaken,
  inCanvasOrder,
  reachedNodes,
  runFocus,
  stepGroups,
  type RunCard,
} from '@/lib/run/runSteps'
import { paths } from '@/routes/paths'

import { failureNotice } from './runFailure'
import { runHeadline } from './runHeadline'
import { cardLook, edgeStroke, gaugeBackground, glowStyle, logRow } from './runLook'

// 경로 탐색은 무겁고 자리표시자는 바뀌지 않는다. 진행률이 오를 때마다 다시 찾지 않는다.
/** 워크플로우를 받지 못했을 때. 빈 캔버스를 그린다 — 남의 구성을 대신 보여 주지 않는다. */
const EMPTY_NODES: CanvasNodeData[] = []
const EMPTY_EDGES: CanvasEdge[] = []

/**
 * 이보다 짧으면 카메라를 움직이지 않는다 — 다섯 칸까지는 한 화면에 다 들어온다.
 * 마지막 카드의 오른쪽 끝이 968 이고 패널 앞 경계가 980 이다.
 */
const CAMERA_MIN_NODES = 6

/**
 * 실행 화면이 그리는 모든 값. 서버가 주는 진행과 불러온 워크플로우를 짝지어
 * 카드 상태 · 칠할 선 · 카메라 · 로그 · 안내 문구로 바꾼다.
 */
export function useRunView({
  executionId,
  workflowId,
}: {
  executionId: string | undefined
  workflowId: string | undefined
}) {
  const run = useRunProgress(executionId)
  const progress = run.progress
  const loaded = useSavedWorkflow(workflowId)
  const defs = loaded.canvas?.nodes ?? EMPTY_NODES
  const edgeList = loaded.canvas?.edges ?? EMPTY_EDGES
  const geometry = useMemo(() => edgeGeometry(defs, edgeList), [defs, edgeList])
  const chainAt = useMemo(() => chainPositions(defs, edgeList), [defs, edgeList])
  const verticalView = useMemo(() => fitVerticalViewport(nodesBounds(defs), PANE_HEIGHT), [defs])
  /**
   * 서버가 세는 단계. 제어·입출력은 일을 하지 않아 빠진다.
   *
   * `progress.done` 과 `steps` 는 이 수를 센다. 카드 목록으로 그대로 짝지으면 시작 노드 하나에
   * 한 칸씩 밀려, 도는 중인 카드가 아닌 옆 카드가 켜진다.
   */
  const runnable = useMemo(() => defs.filter((node) => !node.controlType && !node.ioType), [defs])
  /** 갈래가 있으면 단계를 받기 전에는 어느 쪽이 돌지 몰라 짐작해 켜지 않는다. */
  const branching = useMemo(() => defs.some((node) => node.controlType), [defs])
  /** 단계를 가진 카드. 불러온 캔버스는 서버가 훑은 차례라 서버가 엔진에 싣는 차례와 같다. */
  const cards = useMemo<RunCard[]>(
    () =>
      defs
        .filter((node) => !node.ioType)
        .map((node) => ({
          id: node.id,
          kind: node.controlType ? 'BRANCH' : node.sourceType === 'AGENT' ? 'AGENT' : 'NODE',
          name: node.name,
        })),
    [defs],
  )
  const groups = useMemo(() => stepGroups(cards, progress?.steps ?? []), [cards, progress?.steps])
  const stepAt = useMemo(() => new Map(runnable.map((node, i) => [node.id, i])), [runnable])
  const statuses = useMemo(() => (groups ? cardStatuses(groups) : null), [groups])
  const hasSteps = !!progress?.steps.length

  const canSell = canSellWorkflow(defs)

  /**
   * 등록 화면에 넘길 주소. `revision` 은 사용자가 지금 보고 있는 회차다 — 등록 화면이
   * 다시 받아 오면 그 사이 원본이 바뀌어도 회차가 맞아 버려, 어긋남을 잡는 `WORKFLOW_016`
   * 이 영영 안 걸린다. 서버가 `@Positive` 로 받으므로 회차를 얻기 전에는 보내지 않는다.
   */
  const revision = loaded.detail?.revision
  const canSellNow = canSell && !!workflowId && !!revision
  const registerHref = `${paths.register}?workflowId=${workflowId}&revision=${revision}&executionId=${executionId}`

  const failed = progress?.status === 'FAILED'
  /**
   * 진행을 한 번도 못 받은 상태. 앞서 받아 둔 값이 있으면 실패로 보지 않는다 —
   * 폴링 한 번이 넘어져도 쿼리는 지난 값을 들고 있어, 도는 실행이 잠깐 실패로 보인다.
   */
  const lost = run.broken && !progress
  const settled = progress?.percent ?? 0
  /** 끝났거나 멎었거나 소식이 끊겼다. 기어가기를 멈추고 받은 값에 붙는다. */
  const settle = failed || run.broken || progress?.status === 'SUCCEEDED'
  /** 도는 단계가 끝나면 닿을 값. 그 앞까지 기어가 게이지가 서 있지 않게 한다. */
  const ceiling = progress
    ? Math.min(100, Math.round(((progress.done + 1) / Math.max(1, progress.total)) * 100))
    : settled
  // 게이지만 메운다. 카드와 단계 수는 `progress.done` 을 그대로 쓴다.
  const pctR = Math.round(useEasedPercent(settled, ceiling, settle))
  const runningAt = statuses
    ? runnable.findIndex((node) => statuses.get(node.id) === 'running')
    : -1
  const active = statuses
    ? runningAt
    : branching || hasSteps
      ? -1
      : Math.min(runnable.length - 1, progress?.done ?? 0)
  /**
   * 도는 중인 단계가 카드 목록에서 몇 번째인가. 선을 칠할 때 쓴다.
   *
   * 갈래가 없을 때만 센다 — 그때는 선도 카드와 같은 차례라 선 index 와 견줄 수 있다.
   */
  const activeAt = runnable[active] ? defs.findIndex((node) => node.id === runnable[active].id) : -1
  /**
   * 끝났는지는 진행률이 아니라 상태로 가른다. `percent: 100, status: 'RUNNING'` 인 틱이
   * 한 번 오는데, 진행률로 가르면 그동안 결과 화면이 먼저 뜬다.
   */
  const finished = progress?.status === 'SUCCEEDED'
  const g: CanvasNodeData | undefined = runnable[active]
  const nodes = defs.map((d) => {
    // 제어·입출력은 단계가 아니다. 도는 표시도 완료 표시도 붙지 않는다.
    const i = stepAt.get(d.id)
    const status = i === undefined ? undefined : statuses?.get(d.id)
    // 진행을 못 받았으면 아무 카드도 도는 중이 아니다 — active 가 0 이라 첫 카드가 켜진다.
    const known = i !== undefined && (statuses ? statuses.has(d.id) : !branching && !hasSteps)
    return { ...d, ...cardLook({ status, index: i, known, active, lost, finished }) }
  })
  // 단계를 받았으면 실제로 지나간 길만 칠한다. 못 받았으면 한 줄 차례로 짐작한다.
  const reached = statuses ? reachedNodes(defs, statuses, finished) : null
  const lit = geometry.map((edge, i) => {
    const link = edgeList.find((it) => it.id === edge.id)
    return reached && statuses
      ? !!link && edgeWasTaken(link, edgeList, reached, statuses)
      : i < activeAt
  })
  const edges = geometry.map((edge, i) => ({ ...edge, stroke: edgeStroke(lit[i]) }))
  const liveEdges = geometry.filter((_, i) => lit[i])
  const branchCondition = (node: CanvasNodeData) =>
    describeCondition(node.condition, (id) => defs.find((it) => it.id === id)?.name)
  /** 판정은 포트 이름과 같은 true·false 로 적는다. 아직 모르면 null. */
  const branchVerdict = (node: CanvasNodeData) => {
    const taken =
      reached?.has(node.id) && statuses ? branchTaken(node.id, edgeList, reached, statuses) : null
    return taken === null ? null : `→ ${taken}`
  }
  const branchCards = defs.filter((node) => node.controlType)
  const branchLabel = (node: CanvasNodeData) =>
    [`${node.name} · ${branchCondition(node)}`, branchVerdict(node)].filter(Boolean).join(' ')
  const ownerOf = (step: ExecutionStep) =>
    groups && defs.find((node) => groups.get(node.id)?.includes(step))
  /**
   * 로그와 실패 안내에 쓰는 이름. 서버가 분기에 이름을 싣지 않아 엔진은 `b1`·`a1.b1` 같은 id 를 돌려준다.
   *
   * 캔버스 분기는 조건과 판정까지 붙이고, 에이전트 속 분기는 어느 에이전트의 것인지만 적는다.
   */
  const stepLabel = (step: ExecutionStep) => {
    const owner = ownerOf(step)
    if (owner?.controlType) return branchLabel(owner)
    if (owner && /(^|\.)b\d+$/.test(step.name)) return `${owner.name} · 분기`
    const top = /^b(\d+)$/.exec(step.name)
    const branch = top && !groups ? branchCards[Number(top[1]) - 1] : undefined
    return branch ? branchLabel(branch) : step.name
  }
  /**
   * 도는 단계를 따라가는 카메라. 짧은 워크플로우는 한눈에 들어오니 움직이지 않는다.
   *
   * 카드의 실제 위치를 기준으로 가운데에 둔다. 완료·실패 때도 마지막 위치를 유지한다.
   */
  const focusId = statuses ? runFocus(defs, statuses) : g?.id
  const focus = defs.find((node) => node.id === focusId)
  const camera = {
    ...verticalView,
    tx:
      defs.length >= CAMERA_MIN_NODES && focus
        ? Math.min(
            0,
            (PANE_CONTENT_WIDTH - NODE_SIZE * verticalView.k) / 2 - focus.x * verticalView.k,
          )
        : 0,
  }
  // `done` 은 분기 노드와 건너뛴 갈래를 세지 않아 순번으로 자르면 어긋난다. 상태로 거른다.
  // 일부 카드만 짝지었으면 원래 단계를 모두 남긴다. 모호한 에이전트의 로그까지 빠지면 안 된다.
  const logLines = (
    groups?.size === cards.length ? inCanvasOrder(cards, groups) : (progress?.steps ?? [])
  )
    .filter((step) => step.status !== 'pending' && step.status !== 'skipped')
    .map(stepLabel)
  const logs = logLines.map((text, i) => logRow(text, i === logLines.length - 1 && !finished))

  // 워크플로우가 도착하기 전에는 defs 가 비어 active 가 -1 이다. 첨자로 집으면 렌더가 통째로 터진다.
  const currentStep = progress?.steps.findLast((it) => it.name === progress.currentStepName)
  const failedStep = progress?.steps.findLast((it) => it.name === progress.failure?.stepName)
  const notice = failureNotice({
    missing: run.missing,
    lost,
    failure: progress?.failure,
    failedLabel: failedStep ? stepLabel(failedStep) : undefined,
  })
  const { stepTitle, stepMeta } = runHeadline({
    missing: run.missing,
    lost,
    failed,
    finished,
    progress,
    currentLabel: currentStep ? stepLabel(currentStep) : undefined,
    activeName: g?.name,
    runnableCount: runnable.length,
  })

  return {
    loaded,
    defs,
    canvas: {
      nodes,
      edges,
      liveEdges,
      glowStyle: glowStyle(g, finished),
      camera,
      chainAt,
      branchCondition,
      branchVerdict,
    },
    gauge: {
      pctLabel: `${pctR}%`,
      gaugeBg: gaugeBackground(pctR),
      stepTitle,
      stepMeta,
      // 앞서 받은 값은 있는데 지금 소식이 끊겼다.
      stale: run.broken && !!progress,
    },
    failure: {
      shown: failed || lost,
      missing: run.missing,
      lost,
      notice,
    },
    logs,
    running: !failed && !lost && !finished,
    done: !failed && !lost && finished,
    finished,
    sell: { canSell, canSellNow, registerHref },
  }
}
