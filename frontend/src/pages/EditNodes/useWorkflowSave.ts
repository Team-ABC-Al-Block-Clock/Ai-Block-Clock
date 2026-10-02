import { useMutation } from '@tanstack/react-query'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router'

import { ApiError } from '@/api/errors'
import { updateNode } from '@/api/nodes'
import { replaceWorkflow, saveWorkflow } from '@/api/workflows'
import type { useCanvasWorkflow } from '@/hooks/canvas/useCanvasWorkflow'
import type { useNodeRefFiles } from '@/hooks/canvas/useNodeRefFiles'
import { useSavedWorkflow } from '@/hooks/canvas/useSavedWorkflow'
import { graphProblem, runProblem } from '@/lib/canvas/canvasWorkflow'
import type { NodeValues } from '@/lib/canvas/canvasReducer'
import { toWorkflowGraph } from '@/lib/canvas/workflowPayload'
import { queryClient } from '@/lib/queryClient'
import { queryKeys } from '@/lib/queryKeys'
import { paths } from '@/routes/paths'

import { settledShape, shapeOf } from './saveShape'

const SAVE_FAILED = '저장에 실패했습니다. 잠시 후 다시 시도해 주세요'

/** 원본이 생긴 카드만 — 자료를 보낼 주소가 있는 것들이다. */
export const targetsOf = (nodes: { id: string; sourceId: string }[]) =>
  Object.fromEntries(nodes.filter((node) => node.sourceId).map((node) => [node.id, node.sourceId]))

const dataOf = (error: ApiError) =>
  typeof error.data === 'object' && error.data !== null
    ? (error.data as Record<string, unknown>)
    : {}

/** 다시 눌러도 통과하지 않는 코드는 문구를 가른다 — "잠시 후 다시" 로 뭉개면 계속 누른다. */
function describeSaveError(error: unknown): string | null {
  if (!error) return null
  if (!(error instanceof ApiError)) return SAVE_FAILED
  if (error.code === 'WORKFLOW_016')
    return '다른 곳에서 먼저 수정되었습니다. 화면을 다시 열어 주세요'
  const data = dataOf(error)
  if (error.code === 'WORKFLOW_006') return `노드는 ${data.limit ?? 20}개까지 이을 수 있습니다`
  if (error.code === 'WORKFLOW_007')
    return `연결이 너무 깁니다. ${data.limit ?? 10}단계까지 이을 수 있습니다`
  if (error.code === 'WORKFLOW_015') return '캔버스에 없는 노드가 섞여 있습니다. 다시 놓아 주세요'
  // 서버가 그래프를 물리는 나머지 갈래. 빠져 있으면 고칠 곳을 모른 채 같은 저장만 되풀이한다.
  if (error.code === 'WORKFLOW_005') return '연결이 고리를 이룹니다. 돌아오는 선을 지워 주세요'
  if (error.code === 'WORKFLOW_013')
    return '연결이 서버 규칙에 맞지 않습니다. 시작·분기·종료 연결을 확인해 주세요'
  if (error.code === 'WORKFLOW_014') return '노드를 하나 이상 놓아야 저장할 수 있습니다'
  if (error.code === 'WORKFLOW_003') return '이 워크플로우를 수정할 권한이 없습니다'
  if (error.code === 'WORKFLOW_002') return '워크플로우를 찾을 수 없습니다'
  // COMMON_001 은 검증 실패 전체다. 이름만 따로 알린다.
  if (error.code === 'COMMON_001')
    return 'name' in data
      ? '이름을 확인해 주세요'
      : '저장할 수 없는 값이 있습니다. 노드 설정을 확인해 주세요'
  return SAVE_FAILED
}

type Canvas = ReturnType<typeof useCanvasWorkflow>
type RefFiles = ReturnType<typeof useNodeRefFiles>

/**
 * 편집 화면의 저장 · 실행 · 원본 수정.
 *
 * 불러온 워크플로우를 캔버스에 한 번 옮기고, 그 뒤로는 화면이 보낼 내용과 마지막으로 저장한
 * 내용을 글자로 견줘 「저장됨」을 정한다. 실행은 바뀐 것이 있으면 먼저 저장하고 넘어간다.
 */
export function useWorkflowSave({
  workflowId,
  canvas,
  refFiles,
}: {
  workflowId: string | undefined
  canvas: Canvas
  refFiles: RefFiles
}) {
  const { nodes, edges, attachSources, repriceBySource, editNode } = canvas

  /** 빈 칸으로 시작한다. 자리표시자를 깔아 두면 손대지 않은 이름이 그대로 저장되고,
   *  판매 등록이 워크플로우 이름을 그대로 쓰므로 에이전트까지 같은 이름이 된다. */
  const [name, setName] = useState('')
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

  // 서버가 받는 모양이 아니면 null 이다. 저장 가능 여부와 보낼 내용이 같은 계산에서 나온다.
  const graph = useMemo(() => toWorkflowGraph(nodes, edges), [nodes, edges])
  const trimmedName = name.trim()

  /** 지금 화면이 보낼 내용. 저장한 것과 글자로 비교해 「저장됨」을 되돌린다. */
  const pending = useMemo(() => shapeOf(graph, trimmedName), [graph, trimmedName])
  const dirty = savedShape !== pending

  /** 원가는 프롬프트와 모델로 다시 매겨진다. 받은 값을 카드에 되쓴다. */
  const editOriginal = useMutation({
    mutationFn: ({ sourceId, values }: { sourceId: string; values: NodeValues }) =>
      updateNode(sourceId, values),
    onSuccess: (node) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.nodes.detail(node.nodeId) })
      repriceBySource(node.nodeId, node.baseCost)
    },
  })

  const save = useMutation({
    // 보낸 내용을 함께 돌려준다. `onSuccess` 에서 `pending` 을 다시 읽으면 요청이 나가는 동안
    // 바뀐 최신 값을 집는다 — 보내지도 않은 것을 「저장됨」으로 표시하게 된다.
    mutationFn: async () => {
      if (!graph) throw new Error('서버가 받는 모양의 워크플로우가 아니다')
      const sent = { nodes, edges, name: trimmedName }
      const { nodeIdByKey } = graph
      const result = saved
        ? await replaceWorkflow(saved.workflowId, {
            ...graph,
            name: trimmedName,
            revision: saved.revision,
          })
        : await saveWorkflow({ ...graph, name: trimmedName })
      return { result, sent, nodeIdByKey }
    },
    // 회차가 어긋나면 들고 있던 값이 낡은 것이다. 다시 받아 두지 않으면 몇 번을 눌러도 같은 409 다.
    onError: (error) => {
      if (error instanceof ApiError && error.code === 'WORKFLOW_016') void loaded.refetch()
    },
    onSuccess: ({ result, sent, nodeIdByKey }) => {
      setSaved({ workflowId: result.workflowId, revision: result.revision })
      // 저장하며 만들어진 노드의 원본 id 를 캔버스에 되쓴다. 비워 두면 다음 저장에서 또 만들어진다.
      const attached: Record<string, string> = {}
      for (const placed of result.nodes ?? []) {
        const nodeId = nodeIdByKey[placed.nodeKey]
        if (nodeId && placed.sourceId) attached[nodeId] = placed.sourceId
      }
      attachSources(attached)
      setSavedShape(settledShape(sent.nodes, sent.edges, sent.name, attached))
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
    : (graphProblem(nodes, edges) ?? (trimmedName ? null : '워크플로우 이름을 적어 주세요'))
  const canSave = !saveBlocked && !save.isPending && dirty

  /** 못 실행하는 이유. 저장보다 한 단계 더 본다 — 어디서 시작해 어디서 끝나는지까지. */
  const runBlocked = saveBlocked ?? runProblem(nodes, edges)
  const canRun = !runBlocked && !save.isPending

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

  /**
   * 고친 값을 캔버스에 넣고, 원본이 이미 있으면 서버에도 반영한다.
   *
   * 원본을 가리키는 배치는 저장할 때 `sourceId` 만 보내므로 이름·프롬프트가 함께 나가지 않는다.
   * 여기서 `PUT` 하지 않으면 고친 값이 다음 저장에서 버려진다.
   */
  const applyNodeEdit = (id: string, values: NodeValues) => {
    editNode(id, values)
    const sourceId = nodes.find((node) => node.id === id)?.sourceId
    if (sourceId) editOriginal.mutate({ sourceId, values })
  }

  return {
    loaded,
    name,
    setName,
    saved,
    dirty,
    isSaving: save.isPending,
    save: () => save.mutate(),
    saveBlocked,
    canSave,
    saveError: describeSaveError(save.error),
    runBlocked,
    canRun,
    runNow,
    applyNodeEdit,
    // 원본을 못 고치면 캔버스에는 고친 값이 남고 서버는 예전 값을 든다. 워크플로우 저장은 원본을 건드리지 않는다.
    editError: editOriginal.isError ? '노드를 서버에 고치지 못했습니다. 다시 저장해 주세요' : null,
  }
}
