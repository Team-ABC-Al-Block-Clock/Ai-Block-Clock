import { useEffect, useMemo, useRef, useState, type CSSProperties } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useParams, useSearchParams } from 'react-router-dom'

import { getExecutionProgress, type StepStatus } from '@/api/executions'
import CanvasEdgeLayer from '@/components/CanvasEdgeLayer'
import CanvasNode from '@/components/CanvasNode'
import CanvasPane, { PANEL_CLIP, PANE_HEIGHT, PANE_WIDTH } from '@/components/CanvasPane'
import { useSavedWorkflow } from '@/hooks/useSavedWorkflow'
import {
  canSellWorkflow,
  chainPositions,
  edgeGeometry,
  type CanvasEdge,
  type CanvasNodeData,
} from '@/lib/canvasWorkflow'
import { queryKeys } from '@/lib/queryKeys'
import { paths } from '@/routes/paths'
import SidePanel, { PANEL_CONTENT_WIDTH, PANEL_INSET, PANEL_WIDTH } from '@/components/SidePanel'
import StatusDot from '@/components/StatusDot'
import WorkflowLoadNotice from '@/components/WorkflowLoadNotice'

// 경로 탐색은 무겁고 자리표시자는 바뀌지 않는다. 진행률이 오를 때마다 다시 찾지 않는다.
/** 워크플로우를 받지 못했을 때. 빈 캔버스를 그린다 — 남의 구성을 대신 보여 주지 않는다. */
const EMPTY_NODES: CanvasNodeData[] = []
const EMPTY_EDGES: CanvasEdge[] = []

/**
 * 이보다 짧으면 카메라를 움직이지 않는다 — 다섯 칸까지는 한 화면에 다 들어온다.
 * 마지막 카드의 오른쪽 끝이 968 이고 패널 앞 경계가 980 이다.
 */
const CAMERA_MIN_NODES = 6
/** 앞에 남겨 두는 자리 수. 세 번째 카드에 닿을 때부터 밀린다. */
const CAMERA_LEAD = 2
/** 한 칸 보폭. 카드를 늘어놓는 간격과 같다. */
const CAMERA_STEP = 197

/** 명세가 정한 조회 간격. */
const POLL_MS = 2_000

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
  // 등록 화면에서 취소 버튼을 누르면 실행 결과 화면으로 되돌아간다.
  const [searchParams] = useSearchParams()
  const startDone = searchParams.get('done') === '1'
  const [pct, setPct] = useState(startDone ? 100 : 0)
  const [copied, setCopied] = useState(false)

  // 어떤 워크플로우를 돌리는지. 대기 화면이 넘겨준다. 없으면 자리표시자를 그린다.
  const workflowId = searchParams.get('workflowId') ?? undefined

  /**
   * 실제 진행. 접수 번호가 있을 때만 따라간다 — 없으면 아래 흉내 진행이 돈다.
   * 끝나면 `refetchInterval` 이 멈추고, 화면을 떠나면 쿼리와 함께 타이머도 걷힌다.
   */
  const { executionId } = useParams()
  const live = useQuery({
    queryKey: queryKeys.executions.detail(executionId ?? ''),
    queryFn: () => getExecutionProgress(executionId!),
    enabled: !!executionId,
    staleTime: 0,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      return status === 'SUCCEEDED' || status === 'FAILED' ? false : POLL_MS
    },
    // 탭을 덮어 두어도 실행은 서버에서 계속 돈다. 기본값이면 여기서 멈춰 돌아왔을 때 진행이 얼어 있다.
    refetchIntervalInBackground: true,
  })
  const progress = live.data
  const loaded = useSavedWorkflow(workflowId)
  const defs = loaded.canvas?.nodes ?? EMPTY_NODES
  const edgeList = loaded.canvas?.edges ?? EMPTY_EDGES
  const geometry = useMemo(() => edgeGeometry(defs, edgeList), [defs, edgeList])
  const chainAt = useMemo(() => chainPositions(defs, edgeList), [defs, edgeList])

  const canSell = canSellWorkflow(defs)

  const copyTimer = useRef<ReturnType<typeof setTimeout>>(undefined)
  const rafId = useRef<number>(undefined)

  /** 접수 번호 없이 들어왔을 때 쓰는 표본. 실제 실행에서는 서버가 준 단계 이름이 들어온다. */
  const SAMPLE_LOG_LINES = [
    '입력 파일 128개 수집 완료',
    'PDF 112개 · 이미지 16개 판별',
    '문서 본문 추출 및 정규화',
    '주제 클러스터 7개 도출',
    '태그 규칙 적용 중',
    '분류 결과 검증',
    '요약 생성 대기',
  ]
  const failed = progress?.status === 'FAILED'
  /**
   * 진행을 한 번도 못 받은 상태. 앞서 받아 둔 값이 있으면 실패로 보지 않는다 —
   * 폴링 한 번이 넘어져도 쿼리는 지난 값을 들고 있어, 도는 실행이 잠깐 실패로 보인다.
   */
  const lost = !!live.error && !progress
  const pctR = progress ? progress.percent : Math.round(pct)
  // 워크플로우를 못 받으면 캔버스가 비어 있다. 나누는 수가 0 이 되지 않게 막는다.
  const per = 100 / Math.max(1, defs.length)
  const active = progress
    ? Math.min(defs.length - 1, progress.done)
    : Math.min(defs.length - 1, Math.floor(pctR / per))
  /**
   * `steps` 에 nodeKey 가 없어 순서로만 짝짓는다. 에이전트는 안의 단계 수만큼 펼쳐지므로
   * 수가 어긋나면 포기하고 진행률로만 칠한다 — 잘못 짝지으면 엉뚱한 카드가 완료로 보인다.
   */
  const aligned = progress && progress.steps.length === defs.length ? progress.steps : null
  /**
   * 끝났는지는 진행률이 아니라 상태로 가른다. `percent: 100, status: 'RUNNING'` 인 틱이
   * 한 번 오는데, 진행률로 가르면 그동안 결과 화면이 먼저 뜬다.
   */
  const finished = progress ? progress.status === 'SUCCEEDED' : pctR >= 100
  const g: CanvasNodeData | undefined = defs[active]
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
  const nodes = defs.map((d, i) => {
    const step = aligned?.[i]
    // 진행을 못 받았으면 아무 카드도 도는 중이 아니다 — active 가 0 이라 첫 카드가 켜진다.
    const stepDone = step ? step.status === 'succeeded' : !lost && (i < active || finished)
    const stepRunning = step ? step.status === 'running' : !lost && i === active && !finished
    const stepFailed = step?.status === 'failed'
    return {
      ...d,
      background: stepRunning
        ? 'rgba(255,255,255,.17)'
        : stepDone || stepFailed
          ? 'rgba(255,255,255,.115)'
          : 'rgba(255,255,255,.06)',
      numberColor: stepRunning
        ? '#fff'
        : stepDone
          ? 'rgba(255,255,255,.9)'
          : 'rgba(255,255,255,.5)',
      nameColor: stepRunning ? '#fff' : stepDone ? 'rgba(255,255,255,.9)' : 'rgba(255,255,255,.55)',
      sublabelColor: stepFailed
        ? 'rgba(255,170,194,.9)'
        : stepRunning
          ? 'rgba(230,196,212,.85)'
          : stepDone
            ? 'rgba(154,208,180,.85)'
            : 'rgba(255,236,246,.4)',
      stateLabel: step
        ? labelOf(step.status)
        : stepRunning
          ? '진행 중'
          : stepDone
            ? '완료'
            : '대기',
    }
  })
  const edges = geometry.map((edge, i) => ({
    ...edge,
    stroke: i < active ? 'rgba(255,190,214,.75)' : 'rgba(255,255,255,.38)',
  }))
  const liveEdges = geometry.slice(0, Math.max(0, active))
  /**
   * 도는 단계를 따라가는 카메라. 짧은 워크플로우는 한눈에 들어오니 움직이지 않는다.
   *
   * 앞의 두 자리는 그대로 두고 세 번째부터 밀어, 지금 도는 카드가 늘 화면 가운데쯤에 온다.
   */
  const camera =
    defs.length >= CAMERA_MIN_NODES ? -Math.max(0, active - CAMERA_LEAD) * CAMERA_STEP : 0
  // 펼친 단계를 그대로 적는다 — 카드와 수가 달라도 여기서는 순서만 맞으면 된다.
  const logLines = progress ? progress.steps.map((step) => step.name) : SAMPLE_LOG_LINES
  const shown = progress
    ? Math.min(logLines.length, progress.done + 1)
    : Math.min(logLines.length, Math.max(1, Math.round((pctR / 100) * logLines.length) + 1))
  const logs = logLines.slice(0, shown).map((text, i) => {
    const last = i === shown - 1 && !finished
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
  const resultText = [
    '# 문서 정리 결과',
    '',
    '처리 파일  128개 (PDF 112 · 이미지 16)',
    '분류 주제  7개',
    '소요 시간  1분 42초',
    '',
    '[계약]      문서 24개',
    '[영수증]    문서 31개',
    '[회의록]    문서 18개',
    '[리서치]    문서 22개',
    '[기타]      문서 33개',
    '',
    '저장 위치  /Documents/정리결과',
  ]
  const handleCopyResult = () => {
    const txt = resultText.join('\n')
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
  const resultLines = resultText.map((text) => ({
    text: text === '' ? '\u00a0' : text,
    color: text.startsWith('#')
      ? 'rgba(255,255,255,.92)'
      : text.startsWith('[')
        ? 'rgba(255,206,226,.88)'
        : 'rgba(255,255,255,.62)',
  }))
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
  /** 실패 사유는 아직 진행 응답에 없다(BE !84 범위). 멈춘 단계만 짚어 준다. */
  const failedStep = progress?.steps.find((step) => step.status === 'failed')
  const failureNotice = lost
    ? '잠시 뒤 다시 열어 보시거나, 워크플로우를 확인해 주세요'
    : failedStep
      ? `${failedStep.name} 단계에서 멈췄습니다. 입력과 노드 설정을 확인해 주세요`
      : '실행 엔진이 사유를 알려 주지 않았습니다. 입력과 노드 설정을 확인해 주세요'

  const stepTitle = lost
    ? '진행 상황을 알 수 없습니다'
    : failed
      ? '실행이 중단되었습니다'
      : finished
        ? '결과 전달 완료'
        : progress
          ? (progress.currentStepName ?? '실행을 준비하는 중')
          : g
            ? `${g.name} 중`
            : '워크플로우를 불러오는 중'
  const stepMeta = lost
    ? ''
    : progress
      ? `${progress.done} / ${progress.total} 단계`
      : defs.length
        ? `${finished ? defs.length : active + 1} / ${defs.length} 단계`
        : ''

  useEffect(() => {
    // 실제 진행을 따라가는 중이면 흉내를 내지 않는다. 두 값이 다투면 게이지가 되돌아간다.
    if (startDone || executionId) return
    const t0 = performance.now()
    const tick = (now: number) => {
      const p = Math.min(100, ((now - t0) / 1000) * 5.2) // 약 19초에 완주
      if (p >= 100) {
        setPct(100)
        return // 다음 프레임을 예약하지 않으면 루프가 스스로 멈춘다
      }
      setPct((prev) => (Math.floor(p) !== Math.floor(prev) ? p : prev))
      rafId.current = requestAnimationFrame(tick)
    }
    rafId.current = requestAnimationFrame(tick)
    return () => {
      if (rafId.current) cancelAnimationFrame(rafId.current)
    }
  }, [startDone, executionId])

  // 복사 타이머는 진행률과 무관하다. 위에 얹으면 `?done=1` 로 들어왔을 때
  // 이펙트가 먼저 빠져나가 정리가 등록되지 않는다 — 그 경로가 실제로 쓰인다.
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

            {nodes.map((n) => (
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
            ))}
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
          href={
            workflowId
              ? `${paths.run.waiting.withoutId}?workflowId=${workflowId}`
              : paths.run.waiting.withoutId
          }
          title="실행 재설정"
          aria-label="실행 재설정"
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

        {(failed || lost) && (
          <div
            className="bg-surface-card absolute box-border flex flex-col"
            style={{
              left: `${PANEL_INSET}px`,
              top: '400px',
              width: `${PANEL_CONTENT_WIDTH}px`,
              padding: '18px 20px',
              borderRadius: '12px',
              gap: '8px',
              animation: 'uiIn .6s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            <div className="text-[16px] font-semibold" style={{ color: 'rgba(255,190,209,.95)' }}>
              {lost ? '진행 상황을 불러오지 못했습니다' : '실행이 실패했습니다'}
            </div>
            <div className="text-[14.5px]" style={{ color: 'rgba(255,236,246,.7)' }}>
              {failureNotice}
            </div>
            <a
              href={workflowId ? paths.edit.nodes(workflowId) : paths.edit.nodes.withoutId}
              style={{
                marginTop: '4px',
                height: '38px',
                padding: '0 16px',
                borderRadius: '10px',
                background: 'rgba(255,255,255,.12)',
                alignSelf: 'flex-start',
              }}
              className="flex cursor-pointer items-center text-[14px] font-semibold text-white transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.22)]"
            >
              워크플로우 수정하기
            </a>
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
                  className="flex items-center"
                  style={{ gap: '8px', animation: 'lineIn .34s ease both' }}
                >
                  <StatusDot size={6} color={l.dot} />
                  <div
                    className="overflow-hidden text-[18px] tracking-[-.005em] whitespace-nowrap"
                    style={{ color: l.color, textOverflow: 'ellipsis' }}
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
                결과를 텍스트 파일로 저장하세요.
              </div>
            </div>

            <div
              style={{
                left: `${PANEL_INSET}px`,
                top: '508px',
                width: `${PANEL_CONTENT_WIDTH}px`,
                height: '56px',
                borderRadius: '12px',
                background: 'var(--color-accent)',
                boxShadow: '0 10px 26px rgba(140,100,130,.24),inset 0 1px 0 rgba(255,255,255,.5)',
                gap: '10px',
                transition: 'transform .18s',
                animation: 'uiIn .6s .08s cubic-bezier(.2,.8,.25,1) both',
              }}
              className="absolute flex cursor-pointer items-center justify-center hover:-translate-y-[2px]"
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
                결과 다운로드 (.txt)
              </div>
            </div>

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
                      ? `${paths.register}?workflowId=${workflowId}${executionId ? `&executionId=${executionId}` : ''}`
                      : paths.register,
                  }
                : {})}
              title={
                canSell ? undefined : '다른 사람의 에이전트가 섞여 있어 마켓에 등록할 수 없습니다'
              }
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
              <div className="flex flex-col" style={{ width: '100%', gap: '9px' }}>
                {resultLines.map((r, i) => (
                  <div
                    key={i}
                    className="text-[17px] tracking-[-.005em]"
                    style={{
                      lineHeight: '1.55',
                      color: r.color,
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-word',
                    }}
                  >
                    {r.text}
                  </div>
                ))}
              </div>
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
