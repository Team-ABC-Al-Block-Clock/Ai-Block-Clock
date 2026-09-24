import { useRef, useState, useMemo } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'

import { ApiError } from '@/api/errors'
import {
  RUN_FILE_LIMITS,
  describeRunFileProblem,
  estimateCost,
  startExecution,
} from '@/api/executions'
import CanvasEdgeLayer from '@/components/CanvasEdgeLayer'
import CanvasNode from '@/components/CanvasNode'
import CanvasPane, { PANEL_CLIP, PANE_HEIGHT, PANE_WIDTH } from '@/components/CanvasPane'
import { useSavedWorkflow } from '@/hooks/useSavedWorkflow'
import {
  chainPositions,
  edgeGeometry,
  type CanvasEdge,
  type CanvasNodeData,
} from '@/lib/canvasWorkflow'
import { amountLabel, priceLabel } from '@/lib/format'
import { queryKeys } from '@/lib/queryKeys'
import { useNavigate } from 'react-router-dom'
import { paths } from '@/routes/paths'
import SidePanel, { CARD_GUTTER, PANEL_CONTENT_WIDTH, PANEL_INSET } from '@/components/SidePanel'
import WorkflowLoadNotice from '@/components/WorkflowLoadNotice'

/** 판매자가 비워 두었거나, 마켓 에이전트가 아니라 직접 만든 워크플로우를 돌릴 때 쓴다. */
const DEFAULT_ONBOARDING_MESSAGE = '무엇을 도와드릴까요?'

/** 서버가 받는 코멘트 길이. 넘겨 보내면 `COMMON_001` 이라 입력칸에서 먼저 막는다. */
const COMMENT_MAX = 500

/** 잔액이 모자라 접수가 막혔다. 같은 금액이 위 카드에 이미 떠 있어 문구만 쓰고 금액은 읽지 않는다. */
const INSUFFICIENT = 'PAYMENT_008'
/** 같은 누름이 아직 처리 중. 앞서 보낸 요청이 곧 진행 화면으로 넘겨 주므로 오류로 보이면 안 된다. */
const ALREADY_REQUESTED = 'EXECUTION_006'

// 경로 탐색은 무겁고 자리표시자는 바뀌지 않는다. 렌더마다 다시 찾지 않는다.
/**
 * 진행 화면과 달리 카메라가 없다. 여섯 번째부터는 패널 앞에서 잘려 보이지 않는데, 여기서 할
 * 일은 입력과 실행이지 구성 확인이 아니다 — 방금 편집기에서 만들고 온 것이고, 무엇이 도는지는
 * 실행 견적 카드가 노드마다 이름과 단가로 보여 준다.
 */

/** 워크플로우를 받지 못했을 때. 빈 캔버스를 그린다 — 남의 구성을 대신 보여 주지 않는다. */
const EMPTY_NODES: CanvasNodeData[] = []
const EMPTY_EDGES: CanvasEdge[] = []

export default function RunWaiting() {
  const navigate = useNavigate()
  // 어떤 워크플로우를 돌리는지. 편집 화면이 저장한 뒤 넘겨준다.
  const [searchParams] = useSearchParams()
  const workflowId = searchParams.get('workflowId') ?? undefined
  const loaded = useSavedWorkflow(workflowId)

  // 저장된 것이 있으면 그것을, 없으면 자리표시자를 그린다 — 상세 화면처럼 id 없이 들어오는 길이 있다.
  const nodes = loaded.canvas?.nodes ?? EMPTY_NODES
  const edgeList = loaded.canvas?.edges ?? EMPTY_EDGES
  const geometry = useMemo(() => edgeGeometry(nodes, edgeList), [nodes, edgeList])
  const chainAt = useMemo(() => chainPositions(nodes, edgeList), [nodes, edgeList])
  // 에이전트 조회가 붙으면 여기로 들어온다. 최대 200자라 넘치면 줄바꿈된다.
  const onboardingMessage = ''
  /**
   * 입력칸을 React 가 들고 있는다. DOM 리스너로 붙이면 안내를 거쳐 입력칸이 사라졌다 돌아올 때
   * 리스너도 값도 어긋난다 — 빈 칸인데 실행이 켜져 있는 상태가 된다.
   */
  const [prompt, setPrompt] = useState('')
  const filled = prompt.trim().length > 0

  /** 첫 단계가 읽을 파일. 고르지 않아도 실행할 수 있다 — 파일 없이 도는 워크플로우가 있다. */
  const [file, setFile] = useState<File | null>(null)
  const [fileProblem, setFileProblem] = useState('')
  const filePicker = useRef<HTMLInputElement>(null)

  const handlePick = (ev: React.ChangeEvent<HTMLInputElement>) => {
    const picked = ev.target.files?.[0] ?? null
    // 같은 파일을 다시 고를 수 있어야 한다. 값을 비우지 않으면 change 가 두 번째부터 안 난다.
    ev.target.value = ''
    if (!picked) return
    const problem = describeRunFileProblem(picked)
    setFileProblem(problem ?? '')
    setFile(problem ? null : picked)
  }

  const handleClearFile = () => {
    setFile(null)
    setFileProblem('')
  }

  /**
   * 이 「누름」을 가리키는 값. 접수되는 순간 돈이 빠지므로 재시도에 새 값을 만들면 두 번 빠진다.
   *
   * 그래서 보내는 내용이 같은 동안에는 같은 값을 계속 쓴다 — 시간이 걸려 한 번 더 누르거나
   * 실패 뒤 다시 눌러도 서버가 앞서 접수한 실행을 돌려준다. 코멘트나 파일을 고치면 다른 실행이니
   * 새로 만든다. 옛 값을 그대로 보내면 고친 내용이 아니라 이전 실행이 돌아온다.
   */
  const attempt = useRef<{ key: string; requestId: string } | null>(null)
  const requestIdFor = () => {
    const key = `${prompt}\u0000${file?.name ?? ''}\u0000${file?.size ?? 0}`
    if (attempt.current?.key !== key) attempt.current = { key, requestId: crypto.randomUUID() }
    return attempt.current.requestId
  }

  /**
   * 실행 전 견적. 계산만 하고 차감하지 않아 창을 떠난 사이 다른 실행이 잔액을 잡아 둘 수 있다
   * — 충전하고 돌아오는 길도 마찬가지라 돌아올 때마다 다시 받는다.
   */
  const estimate = useQuery({
    queryKey: queryKeys.executions.costEstimate(workflowId ?? ''),
    queryFn: () => estimateCost(workflowId!),
    enabled: !!workflowId,
    staleTime: 0,
    refetchOnWindowFocus: true,
  })
  const cost = estimate.data
  const items = cost?.items ?? []
  const shortage = cost ? Number(cost.shortageAmount) : 0
  /** 잔액이 0 이면 나눌 수 없다. 쓸 돈이 없는데 견적이 있으면 막대를 가득 채운다. */
  const ratio = cost
    ? Number(cost.availableAmount) > 0
      ? Math.min(1, Number(cost.estimatedAmount) / Number(cost.availableAmount))
      : Number(cost.estimatedAmount) > 0
        ? 1
        : 0
    : 0

  /**
   * 견적을 못 받았을 때는 막지 않는다. 최종 판정은 서버가 실행을 접수할 때 다시 한다 —
   * 화면이 모른다는 이유로 멈추면 서버가 통과시킬 실행까지 못 하게 된다.
   */
  const blocked = cost ? !cost.executable : false

  /**
   * 실행 접수. 서버는 기다리지 않고 접수 번호만 주므로 성공하면 바로 진행 화면으로 넘긴다.
   *
   * 진행 응답에 그래프가 없어 `workflowId` 도 함께 넘긴다 — 진행 화면은 그 값으로 캔버스를
   * 그리고, 받은 단계는 이름과 상태만 그 위에 얹는다.
   */
  const queryClient = useQueryClient()
  const start = useMutation({
    mutationFn: () =>
      startExecution({ workflowId: workflowId!, requestId: requestIdFor(), comment: prompt, file }),
    onSuccess: (started) => {
      // 접수되는 순간 견적만큼 빠진다. 잔액과 견적이 둘 다 옛 값이 된다.
      void queryClient.invalidateQueries({ queryKey: queryKeys.credits.balance() })
      void queryClient.invalidateQueries({
        queryKey: queryKeys.executions.costEstimate(workflowId!),
      })
      navigate(`${paths.run.running(started.executionId)}?workflowId=${workflowId}`)
    },
  })

  const handleRun = () => {
    if (!on) return
    start.mutate()
  }
  /** 워크플로우가 없으면 돌릴 것이 없다. 견적도 못 받아 `blocked` 로는 걸러지지 않는다. */
  const on = !!workflowId && filled && !blocked && !start.isPending
  const btnBg = on ? 'var(--color-accent)' : 'rgba(255,255,255,.09)'
  const btnColor = on ? 'var(--color-accent-ink)' : 'rgba(255,255,255,.42)'
  const btnShadow = on
    ? '0 10px 26px rgba(140,100,130,.24),inset 0 1px 0 rgba(255,255,255,.5)'
    : 'inset 0 1px 0 rgba(255,255,255,.12)'
  const btnCursor = on ? 'pointer' : 'not-allowed'

  /**
   * 버튼이 왜 안 눌리는지, 왜 실패했는지를 버튼 아래에 적는다.
   *
   * 접수 실패는 서버 문구를 그대로 쓴다 — 코드마다 화면 문구를 새로 지으면 서버가 코드를
   * 늘릴 때마다 「알 수 없는 오류」가 된다. 응답을 못 받은 경우만 우리가 문구를 채운다.
   */
  const startCode = start.error instanceof ApiError ? start.error.code : undefined
  /** 연타가 거의 동시에 갔을 뿐이다. 먼저 간 요청이 진행 화면으로 넘겨 준다. */
  const pressedTwice = startCode === ALREADY_REQUESTED
  const runNotice =
    start.error && !pressedTwice
      ? start.error instanceof ApiError && !start.error.isNetworkError
        ? start.error.message
        : '실행을 맡기지 못했습니다. 잠시 뒤 다시 시도해 주세요'
      : !workflowId
        ? '저장된 워크플로우를 열어야 실행할 수 있습니다'
        : ''

  /** 목록이 비어 보이는 이유를 자리에 적는다 — 빈 상자만 두면 노드가 없는 것처럼 읽힌다. */
  const itemsNotice = !workflowId
    ? '저장된 워크플로우를 열면 단계마다 비용이 나옵니다'
    : estimate.isPending
      ? '견적을 계산하는 중입니다'
      : estimate.error
        ? '견적을 불러오지 못했습니다'
        : !items.length
          ? '놓인 노드가 없습니다'
          : null

  // 없는 워크플로우는 실행할 수도 없다. 편집 화면과 같은 안내로 끊는다.
  if (loaded.error) return <WorkflowLoadNotice error={loaded.error} onRetry={loaded.refetch} />

  return (
    <>
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

          {nodes.map((node) => (
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
          ))}
        </div>
      </CanvasPane>

      <SidePanel>
        <div
          className="absolute text-[20px] font-bold tracking-[-.02em] text-white"
          style={{ left: `${PANEL_INSET}px`, top: '38px' }}
        >
          워크플로우 실행
        </div>

        {/* 이전 단계로 돌아가 워크플로우를 수정할 수 있다. */}
        <a
          href={workflowId ? paths.edit.nodes(workflowId) : paths.edit.nodes.withoutId}
          title="워크플로우 수정"
          aria-label="워크플로우 수정"
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
          className="absolute text-[20px] font-semibold tracking-[-.01em]"
          style={{ left: `${PANEL_INSET}px`, top: '96px', color: 'rgba(255,255,255,.96)' }}
        >
          {onboardingMessage || DEFAULT_ONBOARDING_MESSAGE}
        </div>
        <div
          className="absolute"
          style={{
            left: `${PANEL_INSET}px`,
            top: '132px',
            width: `${PANEL_CONTENT_WIDTH}px`,
            height: '136px',
            borderRadius: '12px',
            background: 'var(--color-surface-inset)',
          }}
        >
          <textarea
            className="absolute border-0 bg-transparent font-[inherit] text-[16.5px] tracking-[-.005em] text-white outline-0"
            id="runPrompt"
            value={prompt}
            onChange={(ev) => setPrompt(ev.target.value)}
            maxLength={COMMENT_MAX}
            placeholder="예: 다운로드 폴더의 PDF를 주제별로 분류해줘"
            style={{
              left: '18px',
              top: '16px',
              width: '364px',
              height: '104px',
              resize: 'none',
              lineHeight: '1.55',
            }}
          ></textarea>
        </div>

        {/* 첫 단계가 읽을 파일. 상자를 누르면 고르는 창이 열린다. */}
        <div
          onClick={() => filePicker.current?.click()}
          className="absolute flex cursor-pointer items-center justify-between"
          style={{
            left: `${PANEL_INSET}px`,
            top: '280px',
            width: `${PANEL_CONTENT_WIDTH}px`,
            height: '44px',
            padding: '0 8px 0 16px',
            borderRadius: '11px',
            background: 'var(--color-surface-inset)',
          }}
        >
          <input
            ref={filePicker}
            type="file"
            className="hidden"
            accept={RUN_FILE_LIMITS.extensions.join(',')}
            onChange={handlePick}
          />
          <div
            className="truncate text-[14.5px]"
            style={{
              maxWidth: '280px',
              color: fileProblem
                ? 'rgba(255,190,209,.9)'
                : file
                  ? 'rgba(255,255,255,.9)'
                  : 'rgba(255,255,255,.5)',
            }}
          >
            {fileProblem || file?.name || '엑셀 또는 CSV 파일 (선택)'}
          </div>
          <div
            onClick={(ev) => {
              if (!file) return
              // 상자를 누르면 고르는 창이 열린다. 지우기는 그 위에 있어 따로 끊어 준다.
              ev.stopPropagation()
              handleClearFile()
            }}
            style={{
              height: '30px',
              padding: '0 14px',
              borderRadius: '8px',
              background: 'rgba(255,255,255,.16)',
            }}
            className="flex flex-none items-center text-[12.5px] font-semibold text-white transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.28)]"
          >
            {file ? '지우기' : '찾기'}
          </div>
        </div>

        <div
          className="absolute box-border flex flex-col"
          style={{
            left: `${PANEL_INSET}px`,
            top: '328px',
            width: `${PANEL_CONTENT_WIDTH}px`,
            height: '176px',
            padding: '18px 20px',
            borderRadius: '14px',
            background: 'linear-gradient(180deg, rgba(201,169,221,.13), rgba(255,255,255,.05))',
          }}
        >
          <div
            className="flex-none text-[15px] font-semibold"
            style={{ letterSpacing: '.06em', color: 'rgba(255,214,232,.8)' }}
          >
            실행 예상 비용
          </div>
          <div
            className="flex flex-none"
            style={{ marginTop: '9px', alignItems: 'baseline', gap: '8px' }}
          >
            <div
              className="text-[30px] font-semibold tracking-[-.02em] text-white"
              style={{ lineHeight: '1' }}
            >
              {cost ? amountLabel(cost.estimatedAmount) : '—'}
            </div>
            <div className="text-[16.5px] font-semibold" style={{ color: 'rgba(255,236,246,.9)' }}>
              ABC
            </div>
          </div>
          <div
            className="flex flex-none justify-between"
            style={{ marginTop: '16px', alignItems: 'baseline' }}
          >
            <span className="text-[14.5px]" style={{ color: 'rgba(255,236,246,.78)' }}>
              사용 가능 코인
            </span>
            <span className="text-[15.5px] font-semibold" style={{ color: '#d7bce8' }}>
              {cost ? priceLabel(cost.availableAmount) : '—'}
            </span>
          </div>
          <div
            className="flex-none overflow-hidden"
            style={{
              marginTop: '7px',
              height: '4px',
              borderRadius: '2px',
              background: 'rgba(255,255,255,.1)',
            }}
          >
            <div
              style={{
                width: `${(ratio * 100).toFixed(1)}%`,
                height: '4px',
                borderRadius: '2px',
                background: blocked ? '#e2849f' : '#c9a9dd',
                transition: 'width .3s ease, background .3s ease',
              }}
            ></div>
          </div>
          <div
            className="flex-none text-[13px] tracking-[-.005em]"
            style={{
              marginTop: 'auto',
              color: blocked ? 'rgba(255,190,209,.86)' : 'rgba(255,236,246,.44)',
            }}
          >
            {blocked
              ? `${priceLabel(shortage)} 부족합니다. 충전한 뒤 다시 시도해 주세요`
              : '실제 사용량에 따라 달라질 수 있습니다'}
          </div>
        </div>

        <div
          id="runBtn"
          onClick={handleRun}
          style={{
            left: `${PANEL_INSET}px`,
            top: '528px',
            width: `${PANEL_CONTENT_WIDTH}px`,
            height: '56px',
            borderRadius: '12px',
            background: btnBg,
            boxShadow: btnShadow,
            gap: '8px',
            color: btnColor,
            cursor: btnCursor,
            transition: 'background .25s,color .25s,box-shadow .25s,transform .18s',
          }}
          className="absolute flex items-center justify-center text-[17px] font-bold tracking-[-.01em] hover:-translate-y-[2px]"
        >
          <span>{start.isPending ? '실행을 맡기는 중…' : '실행하기'}</span>
        </div>

        {runNotice && (
          <div
            className="absolute text-[13px] tracking-[-.005em]"
            style={{
              left: `${PANEL_INSET}px`,
              top: '592px',
              width: `${PANEL_CONTENT_WIDTH}px`,
              color: start.error ? 'rgba(255,190,209,.9)' : 'rgba(255,236,246,.5)',
            }}
          >
            {runNotice}
            {startCode === INSUFFICIENT && (
              <a
                href={paths.my.credit}
                style={{ marginLeft: '8px', color: '#d7bce8', textDecoration: 'underline' }}
              >
                충전하러 가기
              </a>
            )}
          </div>
        )}

        <div
          className="absolute text-[20px] font-semibold tracking-[-.01em]"
          style={{ left: `${PANEL_INSET}px`, top: '628px', color: 'rgba(255,255,255,.96)' }}
        >
          단계별 비용
        </div>
        {/* 노드가 스무 개까지 늘어나 창 밖으로 나간다. 패널 안에서만 굴린다. */}
        <div
          className="absolute flex flex-col overflow-x-hidden"
          style={{
            left: `${PANEL_INSET}px`,
            top: '662px',
            width: `${PANEL_CONTENT_WIDTH}px`,
            height: 'calc(300px + 2 * var(--edge-y, 0px))',
            gap: '8px',
            overflowY: 'auto',
            scrollbarWidth: 'thin',
            scrollbarColor: 'rgba(255,255,255,.28) transparent',
          }}
        >
          {itemsNotice ? (
            <div
              className="text-[14px] tracking-[-.005em]"
              style={{ color: 'rgba(255,236,246,.44)' }}
            >
              {itemsNotice}
            </div>
          ) : (
            items.map((item, i) => (
              <div
                key={item.nodeKey}
                style={{
                  width: `${PANEL_CONTENT_WIDTH}px`,
                  height: '46px',
                  borderRadius: '10px',
                  background: 'rgba(255,255,255,.06)',
                  flex: 'none',
                }}
                className="relative"
              >
                <div
                  className="absolute text-[13px] font-semibold tabular-nums"
                  style={{ left: `${CARD_GUTTER}px`, top: '15px', color: 'rgba(255,236,246,.5)' }}
                >
                  {i + 1}
                </div>
                <div
                  className="absolute truncate text-[14.5px] font-semibold tracking-[-.005em]"
                  style={{
                    left: `${CARD_GUTTER + 24}px`,
                    top: '14px',
                    maxWidth: '236px',
                    color: 'rgba(255,255,255,.9)',
                  }}
                >
                  {item.name}
                </div>
                <div
                  className="absolute text-[14px] font-semibold tabular-nums"
                  style={{ right: `${CARD_GUTTER}px`, top: '14px', color: '#d7bce8' }}
                >
                  {priceLabel(item.price)}
                </div>
              </div>
            ))
          )}
        </div>
      </SidePanel>
    </>
  )
}
