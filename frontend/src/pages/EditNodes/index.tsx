import { useMutation } from '@tanstack/react-query'
import { Fragment, useEffect, useMemo, useRef, useState, type CSSProperties } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router'

import { ApiError } from '@/api/errors'
import { DEFAULT_AI_MODEL, NODE_NAME_MAX, updateNode, type AiModel } from '@/api/nodes'
import { replaceWorkflow, saveWorkflow, type WorkflowGraph } from '@/api/workflows'
import CanvasEdgeLayer from '@/components/CanvasEdgeLayer'
import CanvasNode from '@/components/CanvasNode'
import CanvasPane from '@/components/CanvasPane'
import CanvasWorld from '@/components/CanvasWorld'
import ModelSelect from '@/components/ModelSelect'
import SidePanel, { PANEL_INSET } from '@/components/SidePanel'
import StatusDot from '@/components/StatusDot'
import WorkflowLoadNotice from '@/components/WorkflowLoadNotice'
import { useCanvasWorkflow } from '@/hooks/useCanvasWorkflow'
import { useNodeRefFiles } from '@/hooks/useNodeRefFiles'
import { DND_TYPE, useNodePalette } from '@/hooks/useNodePalette'
import { useSavedWorkflow } from '@/hooks/useSavedWorkflow'
import { chainPositions } from '@/lib/canvasWorkflow'
import { queryClient } from '@/lib/queryClient'
import { queryKeys } from '@/lib/queryKeys'
import { nodeTypeLabel, priceLabel } from '@/lib/format'
import { toWorkflowGraph } from '@/lib/workflowPayload'
import { seedsFrom } from '@/routes/handoff'
import { paths } from '@/routes/paths'

import NodeInfoPanel from './NodeInfoPanel'
import NodePalettePanel from './NodePalettePanel'
import RefFileField from '@/components/RefFileField'

/** 이름 길이. 서버가 `@Size(max = 40)` 으로 막는다. */
const NAME_MAX = 40

const SAVE_FAILED = '저장에 실패했습니다. 잠시 후 다시 시도해 주세요'

/** 원본이 생긴 카드만 — 자료를 보낼 주소가 있는 것들이다. */
const targetsOf = (nodes: { id: string; sourceId: string }[]) =>
  Object.fromEntries(nodes.filter((node) => node.sourceId).map((node) => [node.id, node.sourceId]))

/** 저장할 내용을 글자 하나로 만든다. 저장한 것과 이것을 견줘 「저장됨」을 되돌린다. */
const shapeOf = (graph: WorkflowGraph | null, name: string) => JSON.stringify({ graph, name })

/** 다시 눌러도 통과하지 않는 코드는 문구를 가른다 — "잠시 후 다시" 로 뭉개면 계속 누른다. */
function describeSaveError(error: unknown): string | null {
  if (!error) return null
  if (!(error instanceof ApiError)) return SAVE_FAILED
  if (error.code === 'WORKFLOW_016')
    return '다른 곳에서 먼저 수정되었습니다. 화면을 다시 열어 주세요'
  if (error.code === 'WORKFLOW_006') return '노드는 20개까지 이을 수 있습니다'
  if (error.code === 'WORKFLOW_007') return '연결이 너무 깁니다. 10단계까지 이을 수 있습니다'
  if (error.code === 'WORKFLOW_015') return '캔버스에 없는 노드가 섞여 있습니다. 다시 놓아 주세요'
  // 서버가 그래프를 물리는 나머지 갈래. 빠져 있으면 고칠 곳을 모른 채 같은 저장만 되풀이한다.
  if (error.code === 'WORKFLOW_005') return '연결이 고리를 이룹니다. 돌아오는 선을 지워 주세요'
  if (error.code === 'WORKFLOW_013') return '연결이 규칙에 맞지 않습니다. 한 줄로 이어 주세요'
  if (error.code === 'WORKFLOW_014') return '노드를 하나 이상 놓아야 저장할 수 있습니다'
  if (error.code === 'WORKFLOW_017') return '놓인 노드의 종류가 서버와 다릅니다. 다시 놓아 주세요'
  if (error.code === 'WORKFLOW_003') return '이 워크플로우를 수정할 권한이 없습니다'
  if (error.code === 'WORKFLOW_002') return '워크플로우를 찾을 수 없습니다'
  if (error.code === 'COMMON_001') return '이름을 확인해 주세요'
  return SAVE_FAILED
}

/**
 * 노드 편집 화면.
 *
 * 캔버스와 팔레트는 각자 훅이 들고 있다. 여기는 그 둘을 그리고, 팔레트에서
 * 캔버스로 건너오는 놓기와 저장을 잇는다.
 */
export default function EditNodes() {
  const { state } = useLocation()
  const { workflowId } = useParams()
  // 한 번만 읽는다. 렌더마다 다시 만들면 캔버스가 첫 상태를 계산할 때 쓰는 값이
  // 매번 새 배열이 된다.
  //
  // 주소에 id 가 있으면 버린다. 저장하며 `replaceState` 로 주소만 갈아 끼우고 state 는
  // 그대로 두기 때문에, 새로고침하면 불러온 그래프 위로 옛 추천 노드가 한 번 겹쳐 그려지고
  // 첫 화면도 그쪽에 맞춰진다.
  const [seeds] = useState(() => (workflowId ? [] : seedsFrom(state)))
  const canvas = useCanvasWorkflow(seeds)
  const palette = useNodePalette()

  const {
    nodes,
    edges,
    geometry,
    selId,
    hovId,
    selEdge,
    dragId,
    linkFrom,
    canSell,
    editNode,
    attachSources,
    repriceBySource,
    portOpen,
    onHover,
    onSelectEdge,
    handleSelect,
    handleHoverOut,
    handleNodePointerDown,
    handleNodePointerMove,
    handleNodePointerUp,
    handlePointerCancel,
    handlePortDown,
    handlePortMove,
    handlePortUp,
    handlePaneDown,
    handlePaneMove,
    handlePaneUp,
    handlePaneCancel,
    view,
    zoomLabel,
    zoomIn,
    zoomOut,
  } = canvas
  const { paneRef, worldRef, gridRef, glowRef, tempRef } = canvas.refs

  const [name, setName] = useState('문서 정리')
  /** 왼쪽 아래 「노드 추가」 패널에 적는 값. 패널을 닫으면 함께 버린다. */
  const [draft, setDraft] = useState({ name: '', prompt: '', model: DEFAULT_AI_MODEL })
  /** 저장하고 나면 생긴다. 다음 저장부터는 이 회차로 갈아 끼운다. */
  const [saved, setSaved] = useState<{ workflowId: string; revision: number } | null>(null)

  const loaded = useSavedWorkflow(workflowId)

  /** 마지막으로 저장에 성공한(또는 불러온) 내용. 한 글자라도 달라지면 다시 저장할 것이 있다. */
  const [savedShape, setSavedShape] = useState<string | null>(null)

  /** 한 번 옮기면 끝이다. 같은 응답으로 다시 덮지 않는다. */
  const applied = useRef<string | null>(null)
  useEffect(() => {
    const { detail, canvas: loadedCanvas } = loaded
    if (!detail || !loadedCanvas || applied.current === detail.workflowId) return
    applied.current = detail.workflowId
    canvas.loadGraph(loadedCanvas)
    setName(detail.name)
    setSaved({ workflowId: detail.workflowId, revision: detail.revision })
    // 불러온 그대로가 곧 「저장된 상태」다. 비워 두면 손대지 않았는데 「저장」으로 보이고,
    // 실행하기가 바뀐 것 없이 PUT 을 한 번 더 보내 회차만 오른다.
    setSavedShape(
      shapeOf(toWorkflowGraph(loadedCanvas.nodes, loadedCanvas.edges), detail.name.trim()),
    )
  }, [loaded, canvas])

  // 한 줄이 아니면 null 이다. 저장 가능 여부와 보낼 내용이 같은 계산에서 나온다.
  const graph = useMemo(() => toWorkflowGraph(nodes, edges), [nodes, edges])
  // 카드 순번. 갈래가 있으면 차례가 없으므로 번호를 감춘다.
  const chainAt = useMemo(() => chainPositions(nodes, edges), [nodes, edges])
  const trimmedName = name.trim()

  /** 지금 화면이 보낼 내용. 저장한 것과 글자로 비교해 「저장됨」을 되돌린다. */
  const pending = useMemo(() => shapeOf(graph, trimmedName), [graph, trimmedName])
  const dirty = savedShape !== pending

  /** 원가는 프롬프트와 모델로 다시 매겨진다. 받은 값을 카드에 되쓴다. */
  const editOriginal = useMutation({
    mutationFn: ({
      sourceId,
      values,
    }: {
      sourceId: string
      values: { name: string; prompt: string; model: AiModel }
    }) => updateNode(sourceId, values),
    onSuccess: (node) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.nodes.detail(node.nodeId) })
      repriceBySource(node.nodeId, node.baseCost)
    },
  })

  const save = useMutation({
    // 보낸 내용을 함께 돌려준다. `onSuccess` 에서 `pending` 을 다시 읽으면 요청이 나가는 동안
    // 바뀐 최신 값을 집는다 — 보내지도 않은 것을 「저장됨」으로 표시하게 된다.
    mutationFn: async () => {
      if (!graph) throw new Error('한 줄로 이어진 워크플로우가 아니다')
      const shape = shapeOf(graph, trimmedName)
      const { nodeIdByKey } = graph
      const result = saved
        ? await replaceWorkflow(saved.workflowId, {
            ...graph,
            name: trimmedName,
            revision: saved.revision,
          })
        : await saveWorkflow({ ...graph, name: trimmedName })
      return { result, shape, nodeIdByKey }
    },
    // 회차가 어긋나면 들고 있던 값이 낡은 것이다. 다시 받아 두지 않으면 몇 번을 눌러도 같은 409 다.
    onError: (error) => {
      if (error instanceof ApiError && error.code === 'WORKFLOW_016') void loaded.refetch()
    },
    onSuccess: ({ result, shape, nodeIdByKey }) => {
      setSaved({ workflowId: result.workflowId, revision: result.revision })
      setSavedShape(shape)
      // 저장하며 만들어진 노드의 원본 id 를 캔버스에 되쓴다. 비워 두면 다음 저장에서 또 만들어진다.
      const attached: Record<string, string> = {}
      for (const placed of result.nodes ?? []) {
        const nodeId = nodeIdByKey[placed.nodeKey]
        if (nodeId && placed.sourceId) attached[nodeId] = placed.sourceId
      }
      attachSources(attached)
      // 원본이 생겼으니 들고 있던 자료를 올린다. 앞서 못 올려 남은 것도 여기서 다시 보낸다.
      void refFiles.flush({ ...targetsOf(nodes), ...attached })
      // 캐시를 비워 둔다. 실행 화면이 같은 키로 읽어서, 그대로 두면 저장 전 구성을 그린다.
      void queryClient.invalidateQueries({
        queryKey: queryKeys.workflows.detail(result.workflowId),
      })
      void queryClient.invalidateQueries({ queryKey: queryKeys.workflows.lists() })
      // 주소에 id 를 남겨 새로고침해도 돌아올 수 있게 한다. `navigate` 는 쓰지 않는다 —
      // 라우트 패턴이 갈려 화면이 새로 마운트되면 방금 배치한 좌표가 복원 배치로 바뀐다.
      // state 는 그대로 넘긴다. null 을 넣으면 라우터가 들고 있던 히스토리 index 가 날아간다.
      if (!workflowId) {
        history.replaceState(history.state, '', paths.edit.nodes(result.workflowId))
      }
    },
  })

  /** 못 누르는 이유. 없으면 누를 수 있다 — 버튼을 죽여 두기만 하면 왜인지 알 길이 없다. */
  const saveBlocked = loaded.isPending
    ? '워크플로우를 불러오는 중입니다'
    : !nodes.length
      ? '노드를 하나 이상 놓아 주세요'
      : !graph
        ? '노드를 한 줄로 이어 주세요'
        : !trimmedName
          ? '워크플로우 이름을 적어 주세요'
          : null
  const canSave = !saveBlocked && !save.isPending && dirty
  const canRun = !saveBlocked && !save.isPending
  const saveError = describeSaveError(save.error)

  const navigate = useNavigate()
  /** 저장하지 않았거나 바뀐 것이 있으면 먼저 저장하고 넘어간다. 실패하면 문구만 남기고 머문다. */
  const runNow = async () => {
    try {
      const id = dirty ? (await save.mutateAsync()).result.workflowId : saved?.workflowId
      if (id) navigate(`${paths.run.waiting}?workflowId=${id}`)
    } catch {
      // 실패 문구는 `save.error` 가 이미 들고 있다. 화면에 머문다.
    }
  }

  const { panelOpen, q, cards, isEmpty, isError, handleTogglePanel, handleSearch } = palette
  const { isFetchNextPageError, isFetchingNextPage, handleRetryNextPage } = palette

  const handleDragOver = (ev: React.DragEvent<HTMLDivElement>) => {
    if (!ev.dataTransfer.types.includes(DND_TYPE)) return
    // 막지 않으면 브라우저가 drop 을 내지 않는다.
    ev.preventDefault()
    ev.dataTransfer.dropEffect = 'copy'
  }

  /** 팔레트에서 캔버스로 건너오는 것은 항목 id 하나뿐이다. 나머지는 여기서 되찾는다. */
  const handleDrop = (ev: React.DragEvent<HTMLDivElement>) => {
    const itemId = ev.dataTransfer.getData(DND_TYPE)
    if (!itemId) return
    ev.preventDefault()
    const item = palette.findItem(itemId)
    const point = item && canvas.toCanvasPoint(ev.clientX, ev.clientY)
    if (!item || !point) return
    // itemId·itemType 을 함께 싣는다 — 저장할 때 서버가 아는 원본을 가리킬 유일한 값이다.
    canvas.placeFromPalette(
      {
        name: item.name,
        nodeType: item.nodeType,
        sourceType: item.itemType,
        sourceId: item.itemId,
        price: item.price,
        summary: item.summary,
        executionCount: item.executionCount,
        owner: item.owner.nickname,
        prompt: null,
        model: null,
        mine: item.mine,
      },
      point,
    )
  }

  const n = nodes.find((node) => node.id === selId)
  // 에이전트는 남의 내부 구성이라 자료를 다루지 않는다. 기본 노드만 칸을 세운다.
  const refFiles = useNodeRefFiles(
    n && n.sourceType === 'NODE' ? { canvasId: n.id, sourceId: n.sourceId } : null,
  )
  /**
   * 저장은 됐는데 붙지 못한 자료. 저장을 실패로 되돌리지 않고 여기서만 알린다.
   *
   * 세는 값을 따로 들지 않는다 — 들고 있으면 사용자가 그 자료를 지워도 안내가 남는다.
   */
  const targets = targetsOf(nodes)
  const unsentFiles = refFiles.unsentIn(Object.keys(targets))
  const unsentNotice = unsentFiles ? `참고 자료 ${unsentFiles}개가 붙지 않았습니다` : null
  const g = nodes.find((node) => node.id === hovId) ?? n // 빛은 호버 중인 노드를 따라간다
  const selRing: CSSProperties = g
    ? {
        position: 'absolute',
        left: `${g.x - 16}px`,
        top: `${g.y - 16}px`,
        width: '200px',
        height: '200px',
        borderRadius: '50%',
        pointerEvents: 'none',
        opacity: hovId && hovId !== selId ? 0.6 : 1,
        // 카드가 호버에 5px 떠오른다. 빛도 같은 만큼 같은 시간에 따라가야 붙어 보인다.
        transform: `translateY(${hovId && !dragId ? -5 : 0}px)`,
        background:
          'radial-gradient(circle, rgba(255,160,200,.30) 0 42%, rgba(255,160,200,.14) 62%, rgba(255,160,200,0) 76%)',
        // 자리는 전환 없이 노드에 붙인다. 미끄러지면 손을 뗄 때 빛만 따로 날아간다.
        transition: 'transform .2s,opacity .25s ease',
      }
    : { display: 'none' }
  const draftFilled = !!draft.name.trim() && !!draft.prompt.trim()

  const closeAddPanel = () => {
    setDraft({ name: '', prompt: '', model: DEFAULT_AI_MODEL })
    refFiles.clearDraft()
    handleTogglePanel()
  }

  /**
   * 적은 것을 캔버스에 올린다. 서버에 만들지 않는다 —
   * 기본 노드는 워크플로우를 저장할 때 배치와 함께 만들어진다(`NodePlacementRequest`).
   */
  const handleCreate = () => {
    if (!draftFilled) return
    const placedId = canvas.placeAtCenter({
      name: draft.name.trim(),
      nodeType: 'LLM',
      sourceType: 'NODE',
      // 아직 원본이 없다. 저장하고 나면 서버가 준 id 로 채워진다.
      sourceId: '',
      // 원가도 저장해 봐야 안다 — 서버가 프롬프트와 모델로 매긴다.
      price: null,
      summary: null,
      prompt: draft.prompt.trim(),
      model: draft.model,
      mine: true,
    })
    // 카드가 생긴 뒤에 옮긴다. 원본은 저장할 때 만들어지고, 그때 이 카드 몫으로 올라간다.
    refFiles.handOff(placedId)
    refFiles.clearDraft()
    setDraft({ name: '', prompt: '', model: DEFAULT_AI_MODEL })
    handleTogglePanel()
  }

  const hasSelection = !!n
  // 에이전트는 열지 않는다 — 남의 내부 구성이다. 기본 노드는 캔버스가 값을 들고 있다.
  /**
   * 고친 값을 캔버스에 넣고, 원본이 이미 있으면 서버에도 반영한다.
   *
   * 원본을 가리키는 배치는 저장할 때 `sourceId` 만 보내므로 이름·프롬프트가 함께 나가지 않는다.
   * 여기서 `PUT` 하지 않으면 고친 값이 다음 저장에서 버려진다.
   */
  const applyNodeEdit = (id: string, values: { name: string; prompt: string; model: AiModel }) => {
    editNode(id, values)
    const sourceId = nodes.find((node) => node.id === id)?.sourceId
    if (sourceId) editOriginal.mutate({ sourceId, values })
  }

  // 프롬프트를 모르면 빈 폼이 원본을 덮어쓴다. 종류가 LLM 이 아니면 저장이 종류를 바꿔 버린다.
  const selBasic =
    n && n.sourceType === 'NODE' && n.nodeType === 'LLM' && n.prompt !== null
      ? {
          id: n.id,
          name: n.name,
          prompt: n.prompt ?? '',
          model: n.model ?? DEFAULT_AI_MODEL,
          price: n.price,
        }
      : undefined
  const selName = n ? n.name : ''
  const selInitial = n ? n.name.trim().charAt(0) : ''
  const selTypeLabel = n ? nodeTypeLabel(n.nodeType) : ''

  // 없거나 남의 워크플로우다. 여기서 끊지 않으면 자리표시자 다섯 개가 그대로 떠 있어
  // 무언가 불러와진 것처럼 보이고, 거기서 저장하면 엉뚱한 새 워크플로우가 하나 더 생긴다.
  if (loaded.error) return <WorkflowLoadNotice error={loaded.error} onRetry={loaded.refetch} />

  return (
    <>
      <CanvasPane
        ref={paneRef}
        gridRef={gridRef}
        view={view}
        onPointerDown={handlePaneDown}
        onPointerMove={handlePaneMove}
        onPointerUp={handlePaneUp}
        onPointerCancel={handlePaneCancel}
        onDragOver={handleDragOver}
        onDrop={handleDrop}
      >
        <div
          className="pointer-events-none absolute left-0 text-center text-[16px] tracking-[-.005em]"
          style={{
            top: 'calc(74px - var(--edge-y, 0px))',
            width: '980px',
            color: 'rgba(255,240,248,.78)',
            animation: 'uiIn .8s .5s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          {seeds.length > 0
            ? '연결되었습니다. 드래그하여 노드를 재배치하고, 선으로 연결합니다'
            : '드래그하여 노드를 배치하고, 선으로 연결합니다'}
        </div>

        {(selEdge || n) && (
          <div
            className="pointer-events-none absolute left-0 flex items-center justify-center text-[14px]"
            style={{
              top: 'calc(102px - var(--edge-y, 0px))',
              width: '980px',
              gap: '7px',
              color: 'rgba(255,214,234,.82)',
              animation: 'uiIn .3s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            <StatusDot size={6} color="rgba(255,190,220,.9)" />
            {selEdge ? '연결선 선택됨' : '노드 선택됨'} · Delete 로 삭제
          </div>
        )}

        <CanvasWorld view={view} ref={worldRef}>
          <CanvasEdgeLayer clip={false}>
            <defs>
              <linearGradient id="edgeOn" x1="0" y1="0" x2="1" y2="0">
                <stop offset="0%" stopColor="#e3b7c8" stopOpacity=".9" />
                <stop offset="100%" stopColor="#dba18b" stopOpacity=".8" />
              </linearGradient>
            </defs>
            {/* 노드가 다 떠오른 뒤에 잇는다. 먼저 나오면 빈자리에 선만 떠 있다. */}
            <g style={{ animation: 'fadeIn .5s .5s ease backwards' }}>
              {geometry.map((edge, i) => (
                <Fragment key={edge.id}>
                  <path
                    data-edge={edge.id}
                    d={edge.d}
                    fill="none"
                    stroke={selEdge === edge.id ? 'rgba(255,214,234,.95)' : 'url(#edgeOn)'}
                    strokeWidth={selEdge === edge.id ? 5 : 3.4}
                  />
                  <path
                    data-edge={edge.id}
                    d={edge.d}
                    fill="none"
                    stroke="rgba(255,224,238,.85)"
                    strokeWidth="3.4"
                    strokeLinecap="round"
                    strokeDasharray="3 18"
                    style={{ animation: `dash ${1.4 + i * 0.1}s ${i * 0.2}s linear infinite` }}
                  />
                  {/* 선이 얇아 그대로는 못 누른다. 굵은 투명선에 판정만 맡긴다. */}
                  <path
                    data-edge={edge.id}
                    d={edge.d}
                    fill="none"
                    stroke="transparent"
                    strokeWidth="18"
                    style={{ pointerEvents: 'stroke', cursor: 'pointer' }}
                    onPointerDown={(ev) => {
                      ev.stopPropagation()
                      onSelectEdge(edge.id)
                    }}
                  />
                </Fragment>
              ))}
            </g>
          </CanvasEdgeLayer>

          {/* 판정은 흘려보내고 카드가 제 몫만 다시 켠다. 자르기는 `CanvasWorld` 가 한다. */}
          <div className="pointer-events-none absolute inset-0">
            <div ref={glowRef} style={selRing}></div>

            {nodes.map((node) => (
              <CanvasNode
                key={node.id}
                index={chainAt?.get(node.id)}
                name={node.name}
                sublabel={node.price === null ? '—' : priceLabel(node.price)}
                x={node.x}
                y={node.y}
                mine={node.mine}
                floaty={node.floaty}
                enter={node.enter}
                background={node.id === selId ? 'rgba(255,255,255,.16)' : 'rgba(255,255,255,.11)'}
                sublabelColor="rgba(230,196,212,.85)"
                title={
                  node.mine
                    ? undefined
                    : '다른 사람의 에이전트 — 이 구성은 마켓에 등록할 수 없습니다'
                }
                nodeId={node.id}
                dragging={dragId === node.id}
                onSelect={handleSelect}
                onPointerDown={handleNodePointerDown(node.id)}
                onPortDown={handlePortDown(node.id)}
                portDim={(side) => !portOpen(node.id, side)}
                // 노드 이동은 grab ref, 선 연결은 linkFrom state 로 각자 가른다.
                onPointerMove={(ev) => {
                  handleNodePointerMove(ev)
                  handlePortMove(ev)
                }}
                onPointerUp={(ev) => {
                  handleNodePointerUp()
                  handlePortUp(ev)
                }}
                onPointerCancel={handlePointerCancel}
                onHover={() => onHover(node.id)}
                onHoverOut={handleHoverOut}
              />
            ))}
          </div>

          {/* 끄는 중인 선은 카드 위에 둔다 — 이어진 선과 달리 카드를 피해 가지 않아서,
            아래에 깔면 겨냥하는 카드에 가려 끝이 보이지 않는다. 판 전체가 판정을 흘린다. */}
          {linkFrom && (
            <CanvasEdgeLayer clip={false}>
              <path
                ref={tempRef}
                fill="none"
                stroke="rgba(255,224,238,.7)"
                strokeWidth="3.4"
                strokeLinecap="round"
                strokeDasharray="6 10"
              />
            </CanvasEdgeLayer>
          )}
        </CanvasWorld>

        <div
          className="absolute flex items-center overflow-hidden"
          style={{
            left: 'calc(32px - var(--edge, 0px))',
            top: 'calc(952px + var(--edge-y, 0px))',
            height: '36px',
            borderRadius: '18px',
            background: 'rgba(14,7,28,.55)',
            backdropFilter: 'blur(18px)',
            WebkitBackdropFilter: 'blur(18px)',
            animation: 'uiIn .7s .56s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          <div
            onClick={zoomOut}
            title="축소"
            style={{ width: '41px', height: '36px', color: 'rgba(255,255,255,.82)' }}
            className="flex cursor-pointer items-center justify-center text-[17px] font-semibold hover:bg-[rgba(255,255,255,.12)]"
          >
            −
          </div>
          <div
            className="flex items-center justify-center text-[15px] font-semibold"
            style={{ width: '67px', height: '36px', color: 'rgba(255,255,255,.82)' }}
          >
            {zoomLabel}
          </div>
          <div
            onClick={zoomIn}
            title="확대"
            style={{ width: '41px', height: '36px', color: 'rgba(255,255,255,.82)' }}
            className="flex cursor-pointer items-center justify-center text-[17px] font-semibold hover:bg-[rgba(255,255,255,.12)]"
          >
            +
          </div>
          <div style={{ width: '1px', height: '22px', background: 'rgba(255,255,255,.20)' }}></div>
          <div
            onClick={handleTogglePanel}
            style={{ height: '36px', padding: '0 16px', gap: '7px' }}
            className="flex cursor-pointer items-center text-[15px] font-semibold text-white hover:bg-[rgba(255,255,255,.12)]"
          >
            <span>+</span>
            <span>노드 추가</span>
          </div>
        </div>

        {panelOpen && (
          <div
            className="absolute box-border flex flex-col"
            style={{
              left: 'calc(32px - var(--edge, 0px))',
              // 패널을 여는 바가 `--edge-y` 만큼 내려간다. 따라가지 않으면 창이 길어질수록 떨어진다.
              top: 'calc(262px + var(--edge-y, 0px))',
              width: '500px',
              height: '700px',
              borderRadius: '14px',
              background: 'rgba(20,11,36,.88)',
              backdropFilter: 'blur(28px) saturate(125%)',
              WebkitBackdropFilter: 'blur(28px) saturate(125%)',
              boxShadow: 'inset 0 1px 0 rgba(255,255,255,.18),0 26px 60px rgba(4,2,12,.6)',
              padding: '20px',
              animation: 'panelPop .34s cubic-bezier(.2,.9,.3,1) both',
              transformOrigin: '20% 100%',
            }}
          >
            <div
              className="flex flex-none items-center justify-between"
              style={{ marginBottom: '16px' }}
            >
              <div className="text-[20px] font-semibold tracking-[-.01em] text-white">
                노드 추가
              </div>
              <div
                onClick={handleTogglePanel}
                style={{
                  width: '22px',
                  height: '22px',
                  borderRadius: '6px',
                  color: 'rgba(255,255,255,.6)',
                }}
                className="flex cursor-pointer items-center justify-center text-[16px] hover:bg-[rgba(255,255,255,.14)] hover:text-[#fff]"
              >
                ×
              </div>
            </div>

            <div
              className="scroll-thin flex-1 overflow-x-hidden"
              style={{ minHeight: '0', paddingRight: '4px' }}
            >
              <div
                className="text-[17px]"
                style={{ color: 'rgba(255,214,232,.85)', marginBottom: '8px' }}
              >
                노드 이름
              </div>
              <div
                className="bg-surface-card flex items-center"
                style={{
                  height: '44px',
                  borderRadius: '11px',
                  padding: '0 12px',
                  marginBottom: '14px',
                }}
              >
                <input
                  className="border-0 bg-transparent font-[inherit] text-[17px] text-white outline-0"
                  type="text"
                  value={draft.name}
                  maxLength={NODE_NAME_MAX}
                  onChange={(ev) => setDraft((it) => ({ ...it, name: ev.target.value }))}
                  placeholder="예: 내용 분석"
                  aria-label="노드 이름"
                  style={{ width: '100%' }}
                />
              </div>

              <div
                className="text-[17px]"
                style={{ color: 'rgba(255,214,232,.85)', marginBottom: '8px' }}
              >
                입력 파일
              </div>
              {/* 아직 원본이 없다 — 고른 파일은 워크플로우를 저장할 때 함께 올라간다. */}
              <RefFileField slot={refFiles.draft} style={{ marginBottom: '14px' }} />

              <div
                className="text-[17px]"
                style={{ color: 'rgba(255,214,232,.85)', marginBottom: '8px' }}
              >
                AI 모델
              </div>
              <div style={{ position: 'relative', height: '44px', marginBottom: '14px' }}>
                <ModelSelect
                  value={draft.model}
                  onChange={(model) => setDraft((it) => ({ ...it, model }))}
                  style={{ inset: 0, fontSize: '17px' }}
                />
              </div>

              <div
                className="text-[17px] font-semibold"
                style={{ color: 'rgba(255,214,232,.85)', marginBottom: '8px' }}
              >
                프롬프트
              </div>
              <div
                className="bg-surface-card"
                style={{
                  height: '150px',
                  borderRadius: '9px',
                  padding: '11px 12px',
                  marginBottom: '14px',
                }}
              >
                <textarea
                  className="border-0 bg-transparent font-[inherit] text-[17px] text-white outline-0"
                  value={draft.prompt}
                  onChange={(ev) => setDraft((it) => ({ ...it, prompt: ev.target.value }))}
                  placeholder="이 노드가 할 일을 프롬프트로 적으세요&#10;예: 이 문서를 분석하여 핵심 내용을 요약해 주세요."
                  aria-label="프롬프트"
                  style={{ width: '100%', height: '128px', resize: 'none', lineHeight: '1.5' }}
                ></textarea>
              </div>

              <div
                className="text-[17px]"
                style={{ color: 'rgba(255,214,232,.85)', marginBottom: '10px' }}
              >
                제어 흐름 노드
              </div>
              <div className="flex" style={{ gap: '8px', marginBottom: '2px' }}>
                <div
                  style={{ height: '36px', padding: '0 13px', borderRadius: '10px' }}
                  className="bg-surface-card flex cursor-pointer items-center text-[12.5px] font-semibold whitespace-nowrap text-white hover:bg-[rgba(255,255,255,.2)]"
                >
                  조건 분기 (if / else)
                </div>
                <div
                  style={{ height: '36px', padding: '0 15px', borderRadius: '10px' }}
                  className="bg-surface-card flex cursor-pointer items-center text-[12.5px] font-semibold text-white hover:bg-[rgba(255,255,255,.2)]"
                >
                  반복
                </div>
                <div
                  style={{ height: '36px', padding: '0 15px', borderRadius: '10px' }}
                  className="bg-surface-card flex cursor-pointer items-center text-[12.5px] font-semibold text-white hover:bg-[rgba(255,255,255,.2)]"
                >
                  병합
                </div>
              </div>
            </div>

            <div
              className="flex flex-none items-center"
              style={{ justifyContent: 'flex-end', gap: '8px', paddingTop: '14px' }}
            >
              <button
                type="button"
                onClick={closeAddPanel}
                style={{
                  height: '40px',
                  padding: '0 22px',
                  borderRadius: '11px',
                  boxShadow: 'inset 0 0 0 1px rgba(255,255,255,.18)',
                  color: 'rgba(255,255,255,.9)',
                }}
                className="bg-surface-card flex cursor-pointer items-center border-0 font-[inherit] text-[17px] font-semibold hover:bg-[rgba(255,255,255,.18)]"
              >
                취소
              </button>
              <button
                type="button"
                onClick={handleCreate}
                disabled={!draftFilled}
                title={
                  draftFilled ? '노드를 만들어 팔레트에 넣습니다' : '이름과 프롬프트를 적어 주세요'
                }
                style={{
                  height: '40px',
                  padding: '0 24px',
                  borderRadius: '11px',
                  background: 'var(--color-accent)',
                  boxShadow: '0 8px 20px rgba(140,100,130,.24)',
                  color: 'var(--color-accent-ink)',
                  cursor: draftFilled ? 'pointer' : 'default',
                }}
                className="flex items-center border-0 font-[inherit] text-[17px] font-bold hover:brightness-[1.05] disabled:opacity-45 disabled:hover:brightness-100"
              >
                추가
              </button>
            </div>
          </div>
        )}

        {/* 실행하기 왼쪽. 주 버튼과 경쟁하지 않게 유리 패널 차림으로 둔다. */}
        <button
          type="button"
          onClick={() => save.mutate()}
          disabled={!canSave}
          title={
            saveBlocked ??
            (!dirty
              ? '마지막으로 저장한 뒤로 바뀐 것이 없습니다'
              : saved
                ? '바뀐 구성으로 덮어씁니다'
                : '워크플로우를 저장합니다')
          }
          style={{
            left: 'calc(624px + var(--edge, 0px))',
            top: 'calc(915px + var(--edge-y, 0px))',
            width: '160px',
            height: '52px',
            borderRadius: '26px',
            background: 'rgba(30,14,46,.78)',
            boxShadow: `inset 0 0 0 1px rgba(255,255,255,${canSave ? '.22' : '.08'})`,
            backdropFilter: 'blur(14px)',
            WebkitBackdropFilter: 'blur(14px)',
            color: canSave ? 'rgba(255,255,255,.9)' : 'rgba(255,255,255,.38)',
            cursor: canSave ? 'pointer' : 'not-allowed',
            transition: 'box-shadow .18s',
            animation: 'uiIn .7s .62s cubic-bezier(.2,.8,.25,1) both',
          }}
          className="absolute flex items-center justify-center border-0 text-[17px] font-bold tracking-[-.01em]"
        >
          {save.isPending ? '저장 중…' : saved && !dirty ? '저장됨' : '저장'}
        </button>

        {(saveError ?? unsentNotice ?? saveBlocked) && (
          <div
            className="absolute text-center text-[13px] tracking-[-.005em]"
            style={{
              // 실행하기 버튼(800~960) 바로 아래 가운데.
              left: 'calc(760px + var(--edge, 0px))',
              top: 'calc(974px + var(--edge-y, 0px))',
              width: '240px',
              color: (saveError ?? unsentNotice) ? 'rgba(255,196,214,.9)' : 'rgba(255,236,246,.6)',
            }}
          >
            {saveError ?? unsentNotice ?? saveBlocked}
            {/* 저장 버튼은 이미 「저장됨」이라 다시 눌리지 않는다. 보낼 길을 여기 둔다. */}
            {!saveError && unsentNotice && (
              <button
                type="button"
                onClick={() => void refFiles.flush(targets)}
                className="cursor-pointer border-0 bg-transparent font-[inherit] text-[13px] font-semibold underline"
                style={{ marginLeft: '6px', color: 'rgba(255,214,232,.95)' }}
              >
                다시 보내기
              </button>
            )}
          </div>
        )}

        {/*
          저장하지 않고 눌러도 넘어간다 — 먼저 저장하고 그 워크플로우로 실행 화면을 연다.
          저장이 막힌 상태(빈 캔버스·갈래·이름 없음)면 저장 버튼과 같은 이유로 여기도 막힌다.
        */}
        <button
          type="button"
          onClick={() => void runNow()}
          disabled={!canRun}
          title={saveBlocked ?? (dirty ? '저장하고 실행 화면으로 갑니다' : '실행 화면으로 갑니다')}
          style={{
            left: 'calc(800px + var(--edge, 0px))',
            top: 'calc(915px + var(--edge-y, 0px))',
            width: '160px',
            height: '52px',
            borderRadius: '26px',
            background: canRun ? 'var(--color-accent)' : 'rgba(255,255,255,.09)',
            boxShadow: canRun
              ? '0 10px 26px rgba(140,100,130,.24),inset 0 1px 0 rgba(255,255,255,.6)'
              : 'inset 0 1px 0 rgba(255,255,255,.12)',
            color: canRun ? 'var(--color-accent-ink)' : 'rgba(255,255,255,.42)',
            cursor: canRun ? 'pointer' : 'not-allowed',
            transition: 'transform .18s,box-shadow .18s',
            animation: 'uiIn .7s .62s cubic-bezier(.2,.8,.25,1) both',
          }}
          className="absolute flex items-center justify-center border-0 text-[17px] font-bold tracking-[-.01em]"
        >
          {save.isPending ? '저장 중…' : '실행하기'}
        </button>
      </CanvasPane>

      <SidePanel>
        <div
          className="absolute text-[17px] font-semibold tracking-[-.005em] text-white"
          style={{ left: `${PANEL_INSET}px`, top: `${PANEL_INSET}px` }}
        >
          노드 정보
        </div>

        <NodeInfoPanel
          hasSelection={hasSelection}
          name={selName}
          initial={selInitial}
          typeLabel={selTypeLabel}
          basic={selBasic}
          files={refFiles.selected}
          summary={n?.summary}
          meta={
            n && n.sourceType === 'AGENT'
              ? { price: n.price, executionCount: n.executionCount, owner: n.owner }
              : undefined
          }
          onApply={applyNodeEdit}
        />

        <NodePalettePanel
          q={q}
          cards={cards}
          isEmpty={isEmpty}
          isError={isError}
          isFetchNextPageError={isFetchNextPageError}
          isFetchingNextPage={isFetchingNextPage}
          hasNextPage={palette.hasNextPage}
          onSearch={handleSearch}
          onRetryNextPage={handleRetryNextPage}
          refs={palette.refs}
        />
      </SidePanel>

      {/* 이름과 판매 여부를 한 줄로 묶어 작업면(0~980) 가운데에 맞춘다 —
          각각 left 를 주면 알약 글자 수에 따라 묶음이 좌우로 흔들린다. */}
      <div
        className="absolute flex items-center justify-center"
        style={{
          left: '0px',
          top: 'calc(18px - var(--edge-y, 0px))',
          width: '980px',
          gap: '12px',
        }}
      >
        <div
          className="relative flex-none"
          style={{
            // 바탕을 진하게 하고 테두리를 둘러 입력창이라는 것이 드러나게 한다.
            width: '300px',
            height: '46px',
            borderRadius: '12px',
            background: 'rgba(30,14,46,.78)',
            boxShadow: 'inset 0 0 0 1px rgba(255,255,255,.22), 0 6px 18px rgba(10,4,20,.3)',
            backdropFilter: 'blur(14px)',
            WebkitBackdropFilter: 'blur(14px)',
            animation: 'uiIn .7s .44s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          <input
            className="absolute rounded-md border-0 bg-transparent font-[inherit] text-[17px] font-semibold tracking-[-.005em] text-white outline-0 transition-colors duration-[160ms] hover:bg-white/10 focus:bg-white/14"
            value={name}
            onChange={(ev) => setName(ev.target.value)}
            maxLength={NAME_MAX}
            aria-label="워크플로우 이름"
            style={{ left: '14px', top: '9px', width: '245px', height: '28px', padding: '0 6px' }}
          />
          <svg
            className="pointer-events-none absolute"
            width="17"
            height="17"
            viewBox="0 0 14 14"
            fill="none"
            style={{ left: '269px', top: '15px' }}
          >
            <path
              d="M0 12.5L1.5 8L9.5 0L12.5 3L4.5 11L0 12.5ZM0 12.5L3.7 11.8"
              stroke="rgba(255,255,255,.6)"
              strokeWidth="1.4"
              strokeLinejoin="round"
            />
          </svg>
        </div>

        <div
          title={
            canSell
              ? '실행이 끝나면 완료 화면에서 마켓에 등록할 수 있어요'
              : '다른 사람의 에이전트가 섞여 있어 마켓에 등록할 수 없습니다'
          }
          style={{
            height: '28px',
            padding: '0 12px',
            borderRadius: '14px',
            gap: '7px',
            background: 'rgba(30,14,46,.78)',
            boxShadow: 'inset 0 0 0 1px rgba(255,255,255,.22)',
            backdropFilter: 'blur(14px)',
            WebkitBackdropFilter: 'blur(14px)',
            animation: 'uiIn .7s .58s cubic-bezier(.2,.8,.25,1) both',
          }}
          className="flex flex-none items-center whitespace-nowrap"
        >
          <StatusDot color={canSell ? '#a8ddc0' : '#e79a9a'} />
          <div
            className="text-[14.5px] font-bold tracking-[-.005em]"
            style={{ color: 'rgba(255,255,255,.9)' }}
          >
            {canSell ? '판매 등록 가능' : '판매 등록 불가'}
          </div>
        </div>
      </div>
    </>
  )
}
