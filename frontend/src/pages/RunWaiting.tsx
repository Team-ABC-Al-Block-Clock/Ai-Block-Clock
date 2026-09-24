import { useState, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'

import { estimateCost } from '@/api/executions'
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

  /** 실행 전 견적. 그사이 잔액이 바뀔 수 있어 창에 돌아올 때마다 다시 받는다. */
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

  /** 견적을 못 받았을 때는 막지 않는다. 최종 판정은 서버가 접수할 때 다시 한다. */
  const blocked = cost ? !cost.executable : false

  const handleRun = () => {
    if (!filled || blocked) return
    // 돌릴 워크플로우를 그대로 넘긴다. 없으면 자리표시자 그대로다.
    navigate(
      workflowId
        ? `${paths.run.running.withoutId}?workflowId=${workflowId}`
        : paths.run.running.withoutId,
    )
  }
  const on = filled && !blocked
  const btnBg = on ? 'var(--color-accent)' : 'rgba(255,255,255,.09)'
  const btnColor = on ? 'var(--color-accent-ink)' : 'rgba(255,255,255,.42)'
  const btnShadow = on
    ? '0 10px 26px rgba(140,100,130,.24),inset 0 1px 0 rgba(255,255,255,.5)'
    : 'inset 0 1px 0 rgba(255,255,255,.12)'
  const btnCursor = on ? 'pointer' : 'not-allowed'

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
            height: '180px',
            borderRadius: '12px',
            background: 'var(--color-surface-inset)',
          }}
        >
          <textarea
            className="absolute border-0 bg-transparent font-[inherit] text-[16.5px] tracking-[-.005em] text-white outline-0"
            id="runPrompt"
            value={prompt}
            onChange={(ev) => setPrompt(ev.target.value)}
            placeholder="예: 다운로드 폴더의 PDF를 주제별로 분류해줘"
            style={{
              left: '18px',
              top: '16px',
              width: '364px',
              height: '148px',
              resize: 'none',
              lineHeight: '1.55',
            }}
          ></textarea>
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
          <span>실행하기</span>
        </div>

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
