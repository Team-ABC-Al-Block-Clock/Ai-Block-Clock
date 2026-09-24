import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import {
  createNode,
  deleteNode,
  getNode,
  updateNode,
  type NodeDetail,
  type NodeSaveBody,
} from '@/api/nodes'
import { queryKeys } from '@/lib/queryKeys'

/**
 * 작업 노드 원본을 읽고 고친다.
 *
 * 에이전트는 열지 않는다 — 남의 내부 구성이라 서버가 내주지 않고, 내 것이라도 노드가 아니다.
 * 부르는 쪽에서 `sourceType === 'NODE'` 일 때만 id 를 넘긴다. id 가 없으면 만들기만 쓴다.
 */
export function useNodeDetail(nodeId: string | undefined) {
  const queryClient = useQueryClient()

  const detail = useQuery({
    queryKey: queryKeys.nodes.detail(nodeId ?? ''),
    queryFn: () => getNode(nodeId!),
    enabled: !!nodeId,
  })

  /** 고치고 나면 팔레트의 이름·단가가 옛 값이 된다. */
  const refreshPalette = () =>
    void queryClient.invalidateQueries({ queryKey: queryKeys.workflows.nodePalette.lists() })

  const cache = (saved: NodeDetail) => {
    queryClient.setQueryData(queryKeys.nodes.detail(saved.nodeId), saved)
    refreshPalette()
  }

  const create = useMutation({
    mutationFn: (body: NodeSaveBody) => createNode(body),
    onSuccess: cache,
  })

  const save = useMutation({
    mutationFn: (body: NodeSaveBody) => updateNode(nodeId!, body),
    onSuccess: cache,
  })

  const remove = useMutation({
    mutationFn: () => deleteNode(nodeId!),
    onSuccess: () => {
      // 지운 뒤 `removeQueries` 를 쓰면 아직 붙어 있는 관찰자가 곧바로 다시 물어 404 를 받는다.
      queryClient.setQueryData(queryKeys.nodes.detail(nodeId ?? ''), undefined)
      refreshPalette()
    },
  })

  return { detail, create, save, remove }
}
