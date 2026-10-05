import { useMemo, useState } from 'react'
import { useLocation, useParams } from 'react-router'

import { DEFAULT_AI_MODEL, type NodeOutput } from '@/api/nodes'
import type { ControlKind } from '@/api/workflows'
import CanvasPane from '@/components/canvas/CanvasPane'
import SidePanel from '@/components/SidePanel'
import StatusDot from '@/components/StatusDot'
import WorkflowLoadNotice from '@/components/WorkflowLoadNotice'
import { useCanvasWorkflow } from '@/hooks/canvas/useCanvasWorkflow'
import { useNodeRefFiles } from '@/hooks/canvas/useNodeRefFiles'
import { DND_TYPE, useNodePalette } from '@/hooks/canvas/useNodePalette'
import {
  chainPositions,
  isConditionSource,
  sellProblem,
  upstreamOf,
} from '@/lib/canvas/canvasWorkflow'
import { scalarFields } from '@/lib/canvas/nodeOutput'
import { controlKindHint, controlKindName, placedKindLabel } from '@/lib/format'
import { seedsFrom } from '@/routes/handoff'

import AddNodePanel from './AddNodePanel'
import EditCanvasWorld from './EditCanvasWorld'
import { EMPTY_DRAFT } from './nodeDraft'
import NodeInfoPanel from './NodeInfoPanel'
import NodePalettePanel from './NodePalettePanel'
import PanelTabs, { TABS_HEIGHT, type PanelTab } from './PanelTabs'
import SaveRunBar from './SaveRunBar'
import { targetsOf, useWorkflowSave } from './useWorkflowSave'
import WorkflowTitleBar from './WorkflowTitleBar'
import ZoomBar from './ZoomBar'

/**
 * 노드 편집 화면.
 *
 * 캔버스와 팔레트는 각자 훅이 들고 있고, 저장 · 실행은 `useWorkflowSave` 가 든다. 여기는 그것들을
 * 그리고, 팔레트에서 캔버스로 건너오는 놓기와 오른쪽 패널의 선택을 잇는다.
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

  const { nodes, edges, selId, selEdge, canSell, view } = canvas
  const { paneRef, gridRef } = canvas.refs

  const n = nodes.find((node) => node.id === selId)
  // 에이전트는 남의 내부 구성이라 자료를 다루지 않는다. 기본 노드만 칸을 세운다.
  const refFiles = useNodeRefFiles(
    n && n.sourceType === 'NODE' ? { canvasId: n.id, sourceId: n.sourceId } : null,
  )
  const workflow = useWorkflowSave({ workflowId, canvas, refFiles })

  // 카드 순번. 갈래가 있으면 차례가 없으므로 번호를 감춘다.
  const chainAt = useMemo(() => chainPositions(nodes, edges), [nodes, edges])

  const [panelTab, setPanelTab] = useState<PanelTab>('search')
  const [tabSelId, setTabSelId] = useState(selId)
  // 고르면 정보 탭, 풀리면 검색 탭. 고른 채로 직접 누른 탭은 다음 선택까지 둔다.
  if (tabSelId !== selId) {
    setTabSelId(selId)
    setPanelTab(selId ? 'info' : 'search')
  }

  /**
   * 저장은 됐는데 붙지 못한 자료. 저장을 실패로 되돌리지 않고 여기서만 알린다.
   *
   * 세는 값을 따로 들지 않는다 — 들고 있으면 사용자가 그 자료를 지워도 안내가 남는다.
   */
  const targets = targetsOf(nodes)
  const unsentFiles = refFiles.unsentIn(Object.keys(targets))
  const unsentNotice = unsentFiles ? `참고 자료 ${unsentFiles}개가 붙지 않았습니다` : null
  // 실행이 막힌 까닭도 버튼 아래에 적는다. 비활성 버튼의 툴팁은 보이지 않는 브라우저가 있다.
  // 막힌 까닭은 회색, 실패는 분홍. 둘 다 있으면 실패를 먼저 보여준다.
  const failure = workflow.saveError ?? workflow.editError
  const statusNotice = failure ?? workflow.saveBlocked ?? unsentNotice ?? workflow.runBlocked

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

  /** 왼쪽 아래 「노드 추가」 패널에 적는 값. 「취소」로 닫을 때만 버린다. */
  const [draft, setDraft] = useState(EMPTY_DRAFT)

  const handleCancelAdd = () => {
    setDraft(EMPTY_DRAFT)
    refFiles.clearDraft()
    handleTogglePanel()
  }

  /**
   * 적은 것을 캔버스에 올린다. 서버에 만들지 않는다 —
   * 기본 노드는 워크플로우를 저장할 때 배치와 함께 만들어진다(`NodePlacementRequest`).
   */
  const handleCreate = (output: NodeOutput) => {
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
      output,
      mine: true,
    })
    // 카드가 생긴 뒤에 옮긴다. 원본은 저장할 때 만들어지고, 그때 이 카드 몫으로 올라간다.
    refFiles.handOff(placedId)
    refFiles.clearDraft()
    // 만든 노드는 바로 손볼 일이 많다. 골라 두면 정보 탭이 열린다.
    canvas.selectNode(placedId)
    setDraft(EMPTY_DRAFT)
    handleTogglePanel()
  }

  /**
   * 제어·입출력 노드를 캔버스 가운데에 놓는다.
   *
   * 팔레트를 거치지 않는다 — 가리킬 원본이 없어 서버에 만들 것도, 값을 받아 올 곳도 없다.
   * 종류만 실어 두면 저장할 때 `sourceType` 이 `CONTROL`·`IO` 로 나간다.
   */
  const handlePlaceControl = (kind: ControlKind) => {
    // 시작은 맨 앞이라 왼쪽, 분기·종료는 뒤로 이어지니 오른쪽에 둔다.
    canvas.placeBeside(kind === 'START' ? 'left' : 'right', {
      name: controlKindName(kind),
      nodeType: null,
      sourceType: kind === 'BRANCH' ? 'CONTROL' : 'IO',
      sourceId: '',
      price: null,
      summary: null,
      prompt: null,
      model: null,
      mine: null,
      ...(kind === 'BRANCH' ? { controlType: 'BRANCH' as const } : { ioType: kind }),
    })
    // 패널만 닫는다. 적던 노드 초안과 붙인 자료는 × 로 닫을 때처럼 남긴다.
    handleTogglePanel()
  }

  // 프롬프트를 모르면 빈 폼이 원본을 덮어쓴다. 종류가 LLM 이 아니면 저장이 종류를 바꿔 버린다.
  const selBasic =
    n && n.sourceType === 'NODE' && n.nodeType === 'LLM' && n.prompt !== null
      ? {
          id: n.id,
          name: n.name,
          prompt: n.prompt ?? '',
          model: n.model ?? DEFAULT_AI_MODEL,
          output: n.output ?? null,
          price: n.price,
        }
      : undefined
  /** 고른 것이 분기일 때 조건이 읽을 수 있는 자리(`WorkflowGraph.ensureConditionReadsUpstream`). */
  const selBranch = useMemo(() => {
    if (!n?.controlType) return undefined
    // 글 출력 노드는 견줄 칸이 없다. 에이전트는 출력 형식을 몰라 경로를 손으로 적는다.
    const sources = upstreamOf(nodes, edges, n.id)
      .filter((node) => isConditionSource(node) && node.output?.kind !== 'TEXT')
      .map((node) => ({
        id: node.id,
        name: node.name,
        fields: node.output?.example ? scalarFields(node.output.example) : null,
      }))
    return { id: n.id, condition: n.condition ?? null, sources }
  }, [n, nodes, edges])

  const selKind = n?.controlType ?? n?.ioType
  const selName = n ? n.name : ''

  // 없거나 남의 워크플로우다. 여기서 끊지 않으면 자리표시자 다섯 개가 그대로 떠 있어
  // 무언가 불러와진 것처럼 보이고, 거기서 저장하면 엉뚱한 새 워크플로우가 하나 더 생긴다.
  const { loaded } = workflow
  if (loaded.error) return <WorkflowLoadNotice error={loaded.error} onRetry={loaded.refetch} />

  return (
    <>
      <CanvasPane
        ref={paneRef}
        gridRef={gridRef}
        view={view}
        onPointerDown={canvas.handlePaneDown}
        onPointerMove={canvas.handlePaneMove}
        onPointerUp={canvas.handlePaneUp}
        onPointerCancel={canvas.handlePaneCancel}
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

        <EditCanvasWorld canvas={canvas} chainAt={chainAt} />

        <ZoomBar
          zoomLabel={canvas.zoomLabel}
          onZoomIn={canvas.zoomIn}
          onZoomOut={canvas.zoomOut}
          onToggleAddPanel={handleTogglePanel}
        />

        {panelOpen && (
          <AddNodePanel
            draft={draft}
            onDraftChange={setDraft}
            fileSlot={refFiles.draft}
            placed={{
              start: nodes.some((node) => node.ioType === 'START'),
              end: nodes.some((node) => node.ioType === 'END'),
            }}
            onClose={handleTogglePanel}
            onCancel={handleCancelAdd}
            onCreate={handleCreate}
            onPlaceControl={handlePlaceControl}
          />
        )}

        <SaveRunBar
          isSaving={workflow.isSaving}
          hasSaved={!!workflow.saved}
          dirty={workflow.dirty}
          canSave={workflow.canSave}
          saveBlocked={workflow.saveBlocked}
          onSave={workflow.save}
          canRun={workflow.canRun}
          runBlocked={workflow.runBlocked}
          onRun={() => void workflow.runNow()}
          notice={statusNotice}
          noticeIsError={statusNotice === failure || statusNotice === unsentNotice}
          onResendFiles={
            statusNotice === unsentNotice ? () => void refFiles.flush(targets) : undefined
          }
        />
      </CanvasPane>

      <SidePanel>
        <PanelTabs tab={panelTab} onChange={setPanelTab} selectedName={n ? selName : null} />

        {/* 숨기기만 하고 내리지 않는다. 저장 전에 탭을 옮겨도 고치던 값이 남는다. */}
        <div
          role="tabpanel"
          id="panel-info"
          aria-labelledby="panel-tab-info"
          hidden={panelTab !== 'info'}
          className="scroll-thin absolute right-0 bottom-0 left-0 overflow-x-hidden overflow-y-auto"
          style={{ top: `${TABS_HEIGHT}px` }}
        >
          <NodeInfoPanel
            hasSelection={!!n}
            name={selName}
            initial={n ? n.name.trim().charAt(0) : ''}
            typeLabel={n ? placedKindLabel(n) : ''}
            basic={selBasic}
            branch={selBranch}
            onApplyCondition={canvas.editCondition}
            files={refFiles.selected}
            summary={selKind ? controlKindHint(selKind) : n?.summary}
            meta={
              n && n.sourceType === 'AGENT'
                ? { price: n.price, executionCount: n.executionCount, owner: n.owner }
                : undefined
            }
            onApply={workflow.applyNodeEdit}
          />
        </div>

        <div
          role="tabpanel"
          id="panel-search"
          aria-labelledby="panel-tab-search"
          hidden={panelTab !== 'search'}
        >
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
        </div>
      </SidePanel>

      <WorkflowTitleBar
        name={workflow.name}
        onNameChange={workflow.setName}
        canSell={canSell}
        sellProblem={sellProblem(nodes)}
      />
    </>
  )
}
