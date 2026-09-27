import { useQuery } from '@tanstack/react-query'
import { useEffect, useMemo, useRef, useState, type CSSProperties } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'

import type { ExecutionFailure, StepStatus } from '@/api/executions'
import { useEasedPercent } from '@/hooks/useEasedPercent'
import { downloadResult, fetchResult, type ResultFormat } from '@/api/executions'
import { ApiError, EXECUTION_ERROR } from '@/api/errors'
import { useRunProgress } from '@/hooks/useRunProgress'
import CanvasEdgeLayer from '@/components/CanvasEdgeLayer'
import CanvasNode from '@/components/CanvasNode'
import ControlNode from '@/components/ControlNode'
import CanvasPane, {
  PANEL_CLIP,
  PANE_CONTENT_WIDTH,
  PANE_HEIGHT,
  PANE_WIDTH,
} from '@/components/CanvasPane'
import { useSavedWorkflow } from '@/hooks/useSavedWorkflow'
import {
  canSellWorkflow,
  NODE_SIZE,
  sellProblem,
  chainPositions,
  edgeGeometry,
  type CanvasEdge,
  type CanvasNodeData,
} from '@/lib/canvasWorkflow'
import { describeCondition } from '@/lib/branchCondition'
import { queryKeys } from '@/lib/queryKeys'
import { resultOf, resultProblem } from '@/lib/runResult'
import {
  branchTaken,
  cardStatuses,
  edgeWasTaken,
  inCanvasOrder,
  nameBranchSteps,
  reachedNodes,
  runFocus,
} from '@/lib/runSteps'
import { paths } from '@/routes/paths'
import SidePanel, { PANEL_CONTENT_WIDTH, PANEL_INSET, PANEL_WIDTH } from '@/components/SidePanel'
import StatusDot from '@/components/StatusDot'
import WorkflowLoadNotice from '@/components/WorkflowLoadNotice'

import ResultDocument from './ResultDocument'

// 경로 탐색은 무겁고 자리표시자는 바뀌지 않는다. 진행률이 오를 때마다 다시 찾지 않는다.
/** 워크플로우를 받지 못했을 때. 빈 캔버스를 그린다 — 남의 구성을 대신 보여 주지 않는다. */
const EMPTY_NODES: CanvasNodeData[] = []
const EMPTY_EDGES: CanvasEdge[] = []

/**
 * 이보다 짧으면 카메라를 움직이지 않는다 — 다섯 칸까지는 한 화면에 다 들어온다.
 * 마지막 카드의 오른쪽 끝이 968 이고 패널 앞 경계가 980 이다.
 */
const CAMERA_MIN_NODES = 6

/** 실행 엔진이 매기는 오류 코드. 없는 코드가 와도 화면이 비지 않게 기본 문구를 둔다. */
const FAILURE_TEXT: Record<string, string> = {
  SCHEMA_VALIDATION_FAILED: '단계가 약속한 형식으로 결과를 내지 못했습니다.',
  BRANCH_CONDITION_INVALID: '분기 조건이 없는 값을 가리킵니다.',
  AGENT_BOUNDARY_VIOLATION: '에이전트가 공개한 형식과 다른 결과를 냈습니다.',
  REF_FILE_REJECTED: '단계에 붙은 참고 자료를 읽을 수 없습니다.',
  PROMPT_TEMPLATE_INVALID: '프롬프트가 이 자리에서 받을 수 없는 값을 찾습니다.',
  MODEL_TIMEOUT: '모델이 제때 답하지 않았습니다.',
  MODEL_API_ERROR: '모델 호출이 실패했습니다.',
  FILE_PARSE_FAILED: '올린 파일을 읽지 못했습니다.',
  RATE_LIMITED: '요청이 몰려 잠시 처리하지 못했습니다.',
  LLM_INIT_FAILED: '실행 환경 설정에 문제가 있습니다.',
  RUNTIME_INTERNAL: '실행 엔진에서 오류가 났습니다.',
  ABC_RUNTIME_STALLED: '실행 엔진이 응답을 멈춰 기다리기를 그만두었습니다.',
  ABC_RUNTIME_UNREPORTED: '실행이 실패했지만 까닭은 남아 있지 않습니다.',
}

/**
 * `fault` 는 원래 정산용이다 — 누구에게 환원할지 가르는 값이라 그대로 보여 주면 탓만 하고
 * 할 일을 알려 주지 않는다. 특히 `consumer` 는 사용자 잘못이라는 말이 된다. 행동으로 바꾼다.
 * 서버가 까닭을 못 찾으면 비어 오고, 그때는 아무 말도 덧붙이지 않는다.
 */
const FAULT_ADVICE: Record<string, string> = {
  supplier: '재실행 후에도 실패한다면 노드를 교체해주세요.',
  consumer: '올린 파일과 입력한 내용을 확인해 주세요.',
  platform: '잠시 후 다시 실행해 주세요.',
}

/** 안내 패널 안의 버튼. 실패와 조회 불가가 같은 모양을 쓴다. */
const PANEL_ACTION: CSSProperties = {
  marginTop: '6px',
  height: '38px',
  padding: '0 16px',
  borderRadius: '10px',
  background: 'rgba(255,255,255,.12)',
}

/** 한 줄에 한 마디씩 — 어디서 멈췄나, 무슨 일이 났나, 무엇을 하면 되나. */
function describeFailure(failure: ExecutionFailure): string {
  const lines = [
    failure.stepName && `${failure.stepName} 단계에서 중단되었습니다.`,
    FAILURE_TEXT[failure.errorCode] ?? '실행이 실패했습니다.',
    failure.fault && FAULT_ADVICE[failure.fault],
  ]

  return lines.filter(Boolean).join('\n')
}

/**
 * 실행 엔진이 보낸 소문자가 그대로 온다 — 서버가 대조하지 않아 모르는 값이 올 수 있다.
 * 못 찾으면 대기로 둔다. 빈 칸이면 카드 아래 한 줄이 통째로 사라진다.
 */
const STEP_LABEL: Record<string, string> = {
  pending: '대기',
  running: '진행 중',
  succeeded: '완료',
  failed: '실패',
  skipped: '건너뜀',
}
const labelOf = (status: StepStatus) => STEP_LABEL[status] ?? '대기'

export default function RunRunning() {
  const [searchParams] = useSearchParams()
  const [copied, setCopied] = useState(false)

  // 어떤 워크플로우를 돌리는지. 대기 화면이 넘겨준다. 없으면 자리표시자를 그린다.
  const workflowId = searchParams.get('workflowId') ?? undefined

  /** 경로가 접수 번호를 늘 싣는다 — 이 화면은 서버가 주는 진행만 그린다. */
  const { executionId } = useParams()
  const run = useRunProgress(executionId)
  const progress = run.progress
  const loaded = useSavedWorkflow(workflowId)
  const defs = loaded.canvas?.nodes ?? EMPTY_NODES
  const edgeList = loaded.canvas?.edges ?? EMPTY_EDGES
  const geometry = useMemo(() => edgeGeometry(defs, edgeList), [defs, edgeList])
  const chainAt = useMemo(() => chainPositions(defs, edgeList), [defs, edgeList])
  /**
   * 서버가 세는 단계. 제어·입출력은 일을 하지 않아 빠진다.
   *
   * `progress.done` 과 `steps` 는 이 수를 센다. 카드 목록으로 그대로 짝지으면 시작 노드 하나에
   * 한 칸씩 밀려, 도는 중인 카드가 아닌 옆 카드가 켜진다.
   */
  const runnable = useMemo(() => defs.filter((node) => !node.controlType && !node.ioType), [defs])
  /** 갈래가 있으면 단계를 받기 전에는 어느 쪽이 돌지 몰라 짐작해 켜지 않는다. */
  const branching = useMemo(() => defs.some((node) => node.controlType), [defs])
  /** 불러온 캔버스는 서버가 훑은 차례라, 분기도 서버가 엔진에 싣는 차례와 같다. */
  const branchNames = useMemo(
    () =>
      defs
        .filter((node) => node.controlType)
        .map(
          (node) =>
            `${node.name} · ${describeCondition(node.condition, (id) => defs.find((it) => it.id === id)?.name)}`,
        ),
    [defs],
  )
  const steps = useMemo(
    () => nameBranchSteps(progress?.steps ?? [], branchNames),
    [progress?.steps, branchNames],
  )
  const stepAt = useMemo(() => new Map(runnable.map((node, i) => [node.id, i])), [runnable])
  const statuses = useMemo(() => {
    const mapped = cardStatuses(
      runnable.map((node) => node.id),
      branchNames.length,
      steps,
      runnable.map((node) => node.name),
      runnable.map((node) => (node.sourceType === 'AGENT' ? 'AGENT' : 'NODE')),
    )
    if (!mapped) return null
    defs
      .filter((node) => node.controlType)
      .forEach((node, i) => {
        const step = steps[steps.length - branchNames.length + i]
        if (step) mapped.set(node.id, step.status)
      })
    return mapped
  }, [defs, runnable, branchNames, steps])

  const canSell = canSellWorkflow(defs)

  const copyTimer = useRef<ReturnType<typeof setTimeout>>(undefined)

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
    : branching
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
  const glowStyle: CSSProperties =
    g && !finished
      ? {
          position: 'absolute',
          left: `${g.x - 10}px`,
          top: `${g.y - 10}px`,
          width: '188px',
          height: '188px',
          borderRadius: '50%',
          pointerEvents: 'none',
          background:
            'radial-gradient(circle, rgba(255,160,200,.20) 0 48%, rgba(255,160,200,0) 66%)',
          animation: 'runGlow 2.4s ease-in-out infinite',
          transition: 'left .6s cubic-bezier(.2,.8,.25,1),top .6s cubic-bezier(.2,.8,.25,1)',
        }
      : { display: 'none' }
  const nodes = defs.map((d) => {
    // 제어·입출력은 단계가 아니다. 도는 표시도 완료 표시도 붙지 않는다.
    const i = stepAt.get(d.id)
    const status = i === undefined ? undefined : statuses?.get(d.id)
    // 진행을 못 받았으면 아무 카드도 도는 중이 아니다 — active 가 0 이라 첫 카드가 켜진다.
    const known = i !== undefined && (!!statuses || !branching)
    const stepDone = status ? status === 'succeeded' : known && !lost && (i < active || finished)
    const stepRunning = status ? status === 'running' : known && !lost && i === active && !finished
    const stepFailed = status === 'failed'
    const stepSkipped = status === 'skipped'
    return {
      ...d,
      background: stepRunning
        ? 'rgba(255,255,255,.17)'
        : stepDone || stepFailed
          ? 'rgba(255,255,255,.115)'
          : stepSkipped
            ? 'rgba(255,255,255,.03)'
            : 'rgba(255,255,255,.06)',
      numberColor: stepRunning
        ? '#fff'
        : stepDone
          ? 'rgba(255,255,255,.9)'
          : 'rgba(255,255,255,.5)',
      nameColor: stepRunning
        ? '#fff'
        : stepDone
          ? 'rgba(255,255,255,.9)'
          : stepSkipped
            ? 'rgba(255,255,255,.3)'
            : 'rgba(255,255,255,.55)',
      sublabelColor: stepFailed
        ? 'rgba(255,170,194,.9)'
        : stepRunning
          ? 'rgba(230,196,212,.85)'
          : stepDone
            ? 'rgba(154,208,180,.85)'
            : 'rgba(255,236,246,.4)',
      stateLabel: status
        ? labelOf(status)
        : !known
          ? ''
          : stepRunning
            ? '진행 중'
            : stepDone
              ? '완료'
              : '대기',
    }
  })
  // 단계를 받았으면 실제로 지나간 길만 칠한다. 못 받았으면 한 줄 차례로 짐작한다.
  const reached = statuses
    ? reachedNodes(
        defs,
        statuses,
        steps.slice(Math.max(0, steps.length - branchNames.length)),
        finished,
      )
    : null
  const lit = geometry.map((edge, i) => {
    const link = edgeList.find((it) => it.id === edge.id)
    return reached && statuses
      ? !!link && edgeWasTaken(link, edgeList, reached, statuses)
      : i < activeAt
  })
  const edges = geometry.map((edge, i) => ({
    ...edge,
    stroke: lit[i] ? 'rgba(255,190,214,.75)' : 'rgba(255,255,255,.38)',
  }))
  const liveEdges = geometry.filter((_, i) => lit[i])
  const branchCondition = (node: CanvasNodeData) =>
    describeCondition(node.condition, (id) => defs.find((it) => it.id === id)?.name)
  /** 판정은 포트 이름과 같은 true·false 로 적는다. 아직 모르면 null. */
  const branchVerdict = (node: CanvasNodeData) => {
    const taken =
      reached?.has(node.id) && statuses ? branchTaken(node.id, edgeList, reached, statuses) : null
    return taken === null ? null : `→ ${taken}`
  }
  /** 로그와 실패 안내에 쓰는 이름. 분기는 판정이 나면 결과까지 붙인다. */
  const labeledSteps = nameBranchSteps(
    progress?.steps ?? [],
    defs
      .filter((node) => node.controlType)
      .map((node) =>
        [`${node.name} · ${branchCondition(node)}`, branchVerdict(node)].filter(Boolean).join(' '),
      ),
  )
  /**
   * 도는 단계를 따라가는 카메라. 짧은 워크플로우는 한눈에 들어오니 움직이지 않는다.
   *
   * 카드의 실제 위치를 기준으로 가운데에 둔다. 완료·실패 때도 마지막 위치를 유지한다.
   */
  const focusId = statuses ? runFocus(defs, statuses) : g?.id
  const focus = defs.find((node) => node.id === focusId)
  const camera =
    defs.length >= CAMERA_MIN_NODES && focus
      ? Math.min(0, (PANE_CONTENT_WIDTH - NODE_SIZE) / 2 - focus.x)
      : 0
  // `done` 은 분기 노드와 건너뛴 갈래를 세지 않아 순번으로 자르면 어긋난다. 상태로 거른다.
  const logLines = (
    statuses
      ? inCanvasOrder(
          defs,
          runnable.map((node) => node.id),
          labeledSteps,
          runnable.map((node) => node.name),
          runnable.map((node) => (node.sourceType === 'AGENT' ? 'AGENT' : 'NODE')),
        )
      : labeledSteps
  )
    .filter((step) => step.status !== 'pending' && step.status !== 'skipped')
    .map((step) => step.name)
  const logs = logLines.map((text, i) => {
    const last = i === logLines.length - 1 && !finished
    return {
      text,
      dot: last ? 'rgba(255,194,216,.9)' : 'rgba(154,208,180,.7)',
      color: last ? 'rgba(255,255,255,.92)' : 'rgba(255,255,255,.6)',
      caret: (last
        ? {
            width: '7px',
            height: '14px',
            flex: 'none',
            borderRadius: '2px',
            background: 'rgba(255,194,216,.9)',
            animation: 'caret 1s steps(1,end) infinite',
          }
        : { display: 'none' }) satisfies CSSProperties,
    }
  })
  const result = useQuery({
    queryKey: queryKeys.executions.result(executionId ?? ''),
    queryFn: async () => {
      const { blob } = await fetchResult(executionId!, 'JSON')
      return resultOf(JSON.parse(await blob.text()))
    },
    enabled: !!executionId && finished,
    staleTime: Infinity,
    // 파일이 아직 없으면 서버가 그 자리에서 다시 거둬 온다(`ExecutionService.download`). 재시도는 공용 기본에 맡긴다.
  })
  const resultNotice = resultProblem(result)
  const handleCopyResult = () => {
    if (!result.data) return
    const txt = result.data.text
    const ta = document.createElement('textarea')
    ta.value = txt
    ta.setAttribute('readonly', '')
    ta.style.cssText =
      'position:fixed;top:0;left:0;width:1px;height:1px;opacity:0;pointer-events:none'
    document.body.appendChild(ta)
    ta.select()
    ta.setSelectionRange(0, txt.length)
    try {
      document.execCommand('copy')
    } catch {
      /* execCommand 미지원 — 아래 clipboard API 로 재시도 */
    }
    document.body.removeChild(ta)
    if (navigator.clipboard && navigator.clipboard.writeText) {
      try {
        navigator.clipboard.writeText(txt).catch(() => undefined)
      } catch {
        /* 권한 거부·비보안 컨텍스트 */
      }
    }
    setCopied(true)
    clearTimeout(copyTimer.current)
    copyTimer.current = setTimeout(() => setCopied(false), 1900)
  }
  const [downloading, setDownloading] = useState(false)
  const [downloadProblem, setDownloadProblem] = useState<'PDF_MISSING' | 'FAILED' | null>(null)

  const handleDownload = async (format: ResultFormat) => {
    if (!executionId || downloading) return
    setDownloading(true)
    setDownloadProblem(null)

    try {
      await downloadResult(executionId, format)
    } catch (error) {
      setDownloadProblem(
        format === 'PDF' &&
          error instanceof ApiError &&
          error.code === EXECUTION_ERROR.RESULT_NOT_FOUND
          ? 'PDF_MISSING'
          : 'FAILED',
      )
    } finally {
      setDownloading(false)
    }
  }

  const downloadLabel = downloading ? '받는 중…' : '결과 다운로드 (PDF)'
  /** PDF 옆 JSON 버튼 폭. */
  const JSON_BUTTON_WIDTH = 104

  const copyLabel = copied ? '복사됨' : '복사'
  const copyBg = copied ? 'rgba(154,208,180,.26)' : 'rgba(255,255,255,.1)'
  const copyFg = copied ? '#a8ddc0' : 'rgba(255,255,255,.82)'
  const running = !failed && !lost && !finished
  const done = !failed && !lost && finished
  const pctLabel = pctR + '%'
  const gaugeBg =
    'conic-gradient(from 0deg, var(--color-accent) 0 ' +
    pctR +
    '%, rgba(255,255,255,.10) ' +
    pctR +
    '% 100%)'
  // 워크플로우가 도착하기 전에는 defs 가 비어 active 가 -1 이다. 첨자로 집으면 렌더가 통째로 터진다.
  const failureNotice = run.missing
    ? '워크플로우를 다시 실행해 주세요.'
    : lost
      ? '실행은 계속되고 있습니다. 새로고침해 주세요.'
      : progress?.failure
        ? describeFailure({
            ...progress.failure,
            stepName:
              labeledSteps[
                progress.steps.findLastIndex((it) => it.name === progress.failure?.stepName)
              ]?.name ?? progress.failure.stepName,
          })
        : '실행 엔진이 사유를 알려 주지 않았습니다.\n재실행 후에도 실패한다면 워크플로우를 확인해주세요.'

  const stepTitle = run.missing
    ? '실행을 찾을 수 없습니다'
    : lost
      ? '진행 상황을 알 수 없습니다'
      : failed
        ? '실행이 중단되었습니다'
        : finished
          ? '실행 완료'
          : progress
            ? (progress.currentStepName ??
              (progress.total > 0 && progress.done >= progress.total
                ? '결과를 정리하는 중'
                : '실행을 준비하는 중'))
            : g
              ? `${g.name} 중`
              : '워크플로우를 불러오는 중'
  const stepMeta = lost
    ? ''
    : progress
      ? `${progress.done} / ${progress.total} 단계`
      : runnable.length
        ? // 서버처럼 작업 단계만 세고, 진행을 받기 전에는 끝난 단계가 없다.
          `0 / ${runnable.length} 단계`
        : ''

  useEffect(() => () => clearTimeout(copyTimer.current), [])

  // 없는 워크플로우는 실행할 수도 없다. 편집 화면과 같은 안내로 끊는다.
  if (loaded.error) return <WorkflowLoadNotice error={loaded.error} onRetry={loaded.refetch} />

  return (
    <>
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
              transform: `translateX(${camera}px)`,
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

      <SidePanel>
        <div
          className="absolute text-[23px] font-bold tracking-[-.02em] text-white"
          style={{ left: `${PANEL_INSET}px`, top: '36px' }}
        >
          워크플로우 실행
        </div>

        {/* 이전 단계로 돌아가 프롬프트를 수정하여 재실행할 수 있다. */}
        <a
          href={workflowId ? `${paths.run.waiting}?workflowId=${workflowId}` : paths.run.waiting}
          title="다시 실행"
          aria-label="다시 실행"
          style={{
            right: '26px',
            top: '32px',
            width: '40px',
            height: '40px',
            borderRadius: '50%',
            background: 'rgba(255,255,255,.12)',
            boxShadow: 'inset 0 0 0 1px rgba(255,255,255,.16)',
          }}
          className="absolute flex cursor-pointer items-center justify-center transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.24)]"
        >
          <svg width="22" height="22" viewBox="0 0 22 22" fill="none">
            <path
              d="M17 11H6M11 6L6 11L11 16"
              stroke="rgba(255,255,255,.9)"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </a>

        <div
          className="absolute"
          style={{ left: '148px', top: '138px', width: '164px', height: '164px' }}
        >
          <div
            className="absolute rounded-full"
            style={{
              inset: '-18px',
              background:
                'radial-gradient(circle, rgba(255,160,200,.26) 0 46%, rgba(255,160,200,0) 74%)',
              animation: 'runGlow 2.6s ease-in-out infinite',
            }}
          ></div>
          <div className="absolute inset-0 rounded-full" style={{ background: gaugeBg }}></div>
          <div
            className="absolute rounded-full"
            style={{ inset: '13px', background: 'rgba(14,8,26,.92)' }}
          ></div>
          <div className="absolute inset-0 flex items-center justify-center text-[36px] font-bold tracking-[-.02em] text-white">
            {pctLabel}
          </div>
        </div>

        <div
          className="absolute left-0 text-center text-[19.5px] font-semibold tracking-[-.01em]"
          style={{ top: '330px', width: `${PANEL_WIDTH}px`, color: 'rgba(255,255,255,.9)' }}
        >
          {stepTitle}
        </div>
        <div
          className="absolute left-0 text-center text-[17px]"
          style={{ top: '356px', width: `${PANEL_WIDTH}px`, color: 'rgba(255,236,246,.72)' }}
        >
          {stepMeta}
        </div>

        {run.broken && progress && (
          <div
            className="absolute left-0 text-center text-[13px]"
            style={{ top: '380px', width: `${PANEL_WIDTH}px`, color: 'rgba(255,214,232,.6)' }}
          >
            진행 상황을 잠시 받지 못하고 있습니다
          </div>
        )}

        {(failed || lost) && (
          <div
            className="bg-surface-card absolute box-border flex flex-col items-center text-center"
            style={{
              left: `${PANEL_INSET}px`,
              top: '400px',
              width: `${PANEL_CONTENT_WIDTH}px`,
              padding: '22px 20px',
              borderRadius: '12px',
              gap: '10px',
              animation: 'uiIn .6s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            <div className="text-[16px] font-semibold" style={{ color: 'rgba(255,190,209,.95)' }}>
              {run.missing
                ? '잘못된 링크이거나 다른 계정의 실행입니다'
                : lost
                  ? '진행 상황을 불러오지 못했습니다'
                  : '실행이 실패했습니다'}
            </div>
            <div
              className="text-[14.5px]"
              style={{ color: 'rgba(255,236,246,.7)', lineHeight: '1.7', whiteSpace: 'pre-line' }}
            >
              {failureNotice}
            </div>
            {/*
              멎었으면 다시 걸어 준다 — 까닭은 대개 모델이 잠깐 넘어진 것이라 고칠 게 없다.
              중간부터 이어 갈 수는 없다. 서버에 그런 자리가 없어 늘 첫 단계부터 새로 돈다.
              진행을 못 받은 것은 다르다. 실행은 멀쩡히 돌고 있을 수 있어 다시 걸면 두 번 빠진다.
            */}
            {lost && !run.missing ? (
              <button
                type="button"
                onClick={() => location.reload()}
                style={PANEL_ACTION}
                className="flex cursor-pointer items-center text-[14px] font-semibold text-white transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.22)]"
              >
                새로고침
              </button>
            ) : (
              <a
                href={
                  workflowId ? `${paths.run.waiting}?workflowId=${workflowId}` : paths.run.waiting
                }
                style={PANEL_ACTION}
                className="flex cursor-pointer items-center text-[14px] font-semibold text-white transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.22)]"
              >
                처음부터 다시 실행하기
              </a>
            )}
          </div>
        )}

        {running && (
          <>
            <div
              className="absolute text-[17.5px] font-semibold tracking-[-.005em]"
              style={{ left: `${PANEL_INSET}px`, top: '420px', color: 'rgba(255,255,255,.94)' }}
            >
              실시간 출력
            </div>
            <div
              className="scroll-thin absolute flex flex-col"
              style={{
                left: `${PANEL_INSET}px`,
                top: '452px',
                width: `${PANEL_CONTENT_WIDTH}px`,
                // 패널이 길어진 아래까지 쓴다.
                height: 'calc(558px + 2 * var(--edge-y, 0px))',
                gap: '15px',
              }}
            >
              {logs.map((l, i) => (
                <div
                  key={i}
                  className="flex items-start"
                  style={{ gap: '8px', animation: 'lineIn .34s ease both' }}
                >
                  {/* 분기 줄은 조건과 판정까지 적어 길다. 자르면 판정이 사라져 줄을 바꾼다. */}
                  <div className="flex flex-none" style={{ height: '27px', alignItems: 'center' }}>
                    <StatusDot size={6} color={l.dot} />
                  </div>
                  <div
                    className="min-w-0 text-[18px] tracking-[-.005em]"
                    style={{ color: l.color, overflowWrap: 'anywhere' }}
                  >
                    {l.text}
                  </div>
                  <div style={l.caret}></div>
                </div>
              ))}
            </div>
          </>
        )}

        {done && (
          <>
            <div
              className="bg-surface-card absolute"
              style={{
                left: `${PANEL_INSET}px`,
                top: '400px',
                width: `${PANEL_CONTENT_WIDTH}px`,
                height: '88px',
                borderRadius: '12px',
                animation: 'uiIn .6s cubic-bezier(.2,.8,.25,1) both',
              }}
            >
              <div
                className="absolute left-0 text-center text-[17px] font-semibold tracking-[-.01em] text-white"
                style={{ top: '20px', width: `${PANEL_CONTENT_WIDTH}px` }}
              >
                완료되었습니다.
              </div>
              <div
                className="absolute left-0 text-center text-[17px]"
                style={{
                  top: '48px',
                  width: `${PANEL_CONTENT_WIDTH}px`,
                  color: 'rgba(255,236,246,.76)',
                }}
              >
                결과를 PDF 나 JSON 파일로 받을 수 있습니다.
              </div>
            </div>

            <button
              type="button"
              onClick={() => handleDownload('PDF')}
              disabled={!executionId || downloading}
              style={{
                left: `${PANEL_INSET}px`,
                top: '508px',
                width: `${PANEL_CONTENT_WIDTH - JSON_BUTTON_WIDTH - 8}px`,
                height: '56px',
                borderRadius: '12px',
                background: 'var(--color-accent)',
                boxShadow: '0 10px 26px rgba(140,100,130,.24),inset 0 1px 0 rgba(255,255,255,.5)',
                gap: '10px',
                transition: 'transform .18s',
                animation: 'uiIn .6s .08s cubic-bezier(.2,.8,.25,1) both',
                opacity: downloading ? 0.7 : 1,
              }}
              className="absolute flex cursor-pointer items-center justify-center hover:-translate-y-[2px] disabled:cursor-default disabled:hover:translate-y-0"
            >
              <svg className="block" width="16" height="17" viewBox="0 0 14 15" fill="none">
                <path
                  d="M6.5 0V9.5M2 6L6.5 10.5L11 6M0 13.5H13"
                  style={{ stroke: 'var(--color-accent-ink)' }}
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
              <div
                className="text-[17px] font-bold tracking-[-.01em]"
                style={{ color: 'var(--color-accent-ink)' }}
              >
                {downloadLabel}
              </div>
            </button>

            <button
              type="button"
              onClick={() => handleDownload('JSON')}
              disabled={!executionId || downloading}
              title="엔진이 낸 결과 JSON 을 그대로 받습니다"
              style={{
                left: `${PANEL_INSET + PANEL_CONTENT_WIDTH - JSON_BUTTON_WIDTH}px`,
                top: '508px',
                width: `${JSON_BUTTON_WIDTH}px`,
                height: '56px',
                borderRadius: '12px',
                boxShadow: 'inset 0 0 0 1px rgba(255,255,255,.18)',
                animation: 'uiIn .6s .08s cubic-bezier(.2,.8,.25,1) both',
                opacity: downloading ? 0.7 : 1,
              }}
              className="bg-surface-card absolute flex cursor-pointer items-center justify-center border-0 font-[inherit] text-[16px] font-bold text-white hover:bg-[rgba(255,255,255,.16)] disabled:cursor-default"
            >
              JSON
            </button>

            {downloadProblem && (
              <div
                role="alert"
                className="absolute text-center text-[13px]"
                style={{
                  left: `${PANEL_INSET}px`,
                  top: '568px',
                  width: `${PANEL_CONTENT_WIDTH}px`,
                  color: 'rgba(255,214,232,.72)',
                }}
              >
                {downloadProblem === 'PDF_MISSING' ? (
                  <>PDF 를 만들지 못했습니다. 옆의 JSON 으로 받아 주세요</>
                ) : (
                  '결과를 받지 못했습니다. 잠시 후 다시 눌러 주세요'
                )}
              </div>
            )}

            <a
              href={workflowId ? paths.edit.nodes(workflowId) : paths.edit.nodes.withoutId}
              style={{
                left: `${PANEL_INSET}px`,
                top: '576px',
                width: '193px',
                height: '56px',
                borderRadius: '12px',
                boxShadow: 'inset 0 0 0 1px rgba(255,255,255,.18)',
                gap: '8px',
                transition: 'background .18s',
                animation: 'uiIn .6s .14s cubic-bezier(.2,.8,.25,1) both',
              }}
              className="bg-surface-card absolute flex cursor-pointer items-center justify-center hover:bg-[rgba(255,255,255,.16)]"
            >
              <svg className="block" width="15" height="15" viewBox="0 0 14 14" fill="none">
                <path
                  d="M0 12.5L1.5 8L9.5 0L12.5 3L4.5 11L0 12.5ZM0 12.5L3.7 11.8"
                  stroke="rgba(255,255,255,.75)"
                  strokeWidth="1.4"
                  strokeLinejoin="round"
                />
              </svg>
              <div
                className="text-[15px] font-semibold tracking-[-.01em] whitespace-nowrap"
                style={{ color: 'rgba(255,255,255,.9)' }}
              >
                수정하기
              </div>
            </a>

            {/* 워크플로우 전체 실행에 성공해야 마켓에 판매 등록 가능. */}
            {/* 등록 화면에서 취소하면 여기로 돌아온다 — 워크플로우를 실어 보내야 빈 화면이 되지 않는다. */}
            <a
              {...(canSell
                ? {
                    // 접수 번호도 함께 넘긴다. 돌아올 때 이 실행으로 되돌아와야 결과를 잃지 않는다.
                    href: workflowId
                      ? `${paths.register}?workflowId=${workflowId}&executionId=${executionId}`
                      : `${paths.register}?executionId=${executionId}`,
                  }
                : {})}
              title={sellProblem(defs) ?? undefined}
              aria-disabled={!canSell}
              style={{
                left: '237px',
                top: '576px',
                width: '193px',
                height: '56px',
                borderRadius: '12px',
                // 흐릿하게 하되 opacity 는 쓰지 않는다.
                boxShadow: `inset 0 0 0 1px rgba(255,255,255,${canSell ? '.18' : '.08'})`,
                gap: '8px',
                cursor: canSell ? 'pointer' : 'not-allowed',
                transition: 'background .18s',
                animation: 'uiIn .6s .2s cubic-bezier(.2,.8,.25,1) both',
              }}
              className={
                canSell
                  ? 'bg-surface-card absolute flex items-center justify-center hover:bg-[rgba(255,255,255,.16)]'
                  : 'absolute flex items-center justify-center bg-[rgba(255,255,255,.04)]'
              }
            >
              <svg className="block" width="15" height="15" viewBox="0 0 14 14" fill="none">
                <path
                  d="M7.4 0.6H12.3A1.1 1.1 0 0 1 13.4 1.7V6.6L6.6 13.4L0.6 7.4L7.4 0.6Z"
                  stroke={canSell ? 'rgba(255,255,255,.75)' : 'rgba(255,255,255,.3)'}
                  strokeWidth="1.4"
                  strokeLinejoin="round"
                />
                <circle
                  cx="10.2"
                  cy="3.8"
                  r="1"
                  fill={canSell ? 'rgba(255,255,255,.75)' : 'rgba(255,255,255,.3)'}
                />
              </svg>
              <div
                className="text-[15px] font-semibold tracking-[-.01em] whitespace-nowrap"
                style={{ color: canSell ? 'rgba(255,255,255,.9)' : 'rgba(255,255,255,.38)' }}
              >
                판매 등록
              </div>
            </a>

            <div
              className="absolute text-[17.5px] font-semibold tracking-[-.005em]"
              style={{ left: `${PANEL_INSET}px`, top: '660px', color: 'rgba(255,255,255,.94)' }}
            >
              출력 결과
            </div>
            <div
              onClick={handleCopyResult}
              style={{
                // 라벨이 '복사'/'복사됨' 으로 바뀌어 폭이 달라지므로 결과 상자의 오른쪽 끝에 맞춘다.
                right: `${PANEL_INSET}px`,
                top: '655px',
                height: '26px',
                padding: '0 11px',
                borderRadius: '8px',
                background: copyBg,
                gap: '6px',
              }}
              className="absolute flex cursor-pointer items-center transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.18)]"
            >
              <svg
                width="12"
                height="12"
                viewBox="0 0 12 12"
                fill="none"
                stroke={copyFg}
                strokeWidth="1.3"
                strokeLinejoin="round"
              >
                <rect x="3.6" y="3.6" width="7.4" height="7.4" rx="1.4" />
                <path d="M8.4 1H2.4A1.4 1.4 0 0 0 1 2.4v6" />
              </svg>
              <div
                className="text-[13.5px] font-semibold tracking-[-.005em] whitespace-nowrap"
                style={{ color: copyFg }}
              >
                {copyLabel}
              </div>
            </div>
            <div
              className="absolute box-border overflow-x-hidden"
              id="resultBox"
              style={{
                left: `${PANEL_INSET}px`,
                top: '692px',
                width: `${PANEL_CONTENT_WIDTH}px`,
                height: 'calc(318px + 2 * var(--edge-y, 0px))',
                padding: '16px',
                borderRadius: '12px',
                background: 'var(--color-surface-quiet)',
                overflowY: 'auto',
                userSelect: 'text',
                WebkitUserSelect: 'text',
                cursor: 'text',
                scrollbarWidth: 'thin',
                scrollbarColor: 'rgba(255,255,255,.28) transparent',
                animation: 'uiIn .6s .2s cubic-bezier(.2,.8,.25,1) both',
              }}
            >
              {result.data ? (
                <ResultDocument blocks={result.data.blocks} />
              ) : (
                <div className="text-[16px]" style={{ color: 'rgba(255,255,255,.6)' }}>
                  {resultNotice}
                </div>
              )}
            </div>
          </>
        )}
      </SidePanel>

      {copied && (
        <div
          className="pointer-events-none absolute flex items-center justify-center"
          style={{
            // 결과 상자 위에 겹쳐 토스트를 띄운다. 상자와 같은 기준을 써야 한다.
            right: 'calc(44px - var(--edge, 0px))',
            top: '706px',
            width: '372px',
            height: '46px',
            justifyContent: 'flex-end',
            zIndex: '6',
          }}
        >
          <div
            className="flex items-center"
            style={{
              height: '46px',
              padding: '0 22px',
              borderRadius: '23px',
              background: 'rgba(24,11,38,.82)',
              backdropFilter: 'blur(20px) saturate(120%)',
              WebkitBackdropFilter: 'blur(20px) saturate(120%)',
              boxShadow: '0 16px 44px rgba(10,4,20,.42)',
              gap: '11px',
              animation: 'toastIn 1.8s ease both',
            }}
          >
            <div
              className="flex flex-none items-center justify-center rounded-full"
              style={{ width: '22px', height: '22px', background: 'rgba(154,208,180,.24)' }}
            >
              <svg
                width="12"
                height="10"
                viewBox="0 0 12 10"
                fill="none"
                stroke="#a8ddc0"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <path d="M1 5.2 4.3 8.5 11 1.5" />
              </svg>
            </div>
            <div className="text-[16px] font-semibold tracking-[-.01em] whitespace-nowrap text-white">
              복사 완료
            </div>
          </div>
        </div>
      )}
    </>
  )
}
