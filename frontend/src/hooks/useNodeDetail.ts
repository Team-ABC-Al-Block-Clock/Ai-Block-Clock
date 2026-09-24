import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { deleteNode, getNode, updateNode, type NodeDetail, type NodeSaveBody } from '@/api/nodes'
import { queryKeys } from '@/lib/queryKeys'

/**
 * 캔버스에서 고른 노드의 원본을 읽고 고친다.
 *
 * 에이전트는 열지 않는다 — 남의 내부 구성이라 서버가 내주지 않고, 내 것이라도 노드가 아니다.
 * 부르는 쪽에서 `sourceType === 'NODE'` 일 때만 id 를 넘긴다.
 */
export function useNodeDetail(nodeId: string | undefined) {
  const queryClient = useQueryClient()

  const detail = useQuery({
    queryKey: queryKeys.nodes.detail(nodeId ?? ''),
    queryFn: () => getNode(nodeId!),
    enabled: !!nodeId,
  })

  /** 고치고 나면 팔레트의 이름·단가가 옛 값이 된다. */
  const invalidate = (saved?: NodeDetail) => {
    if (saved) queryClient.setQueryData(queryKeys.nodes.detail(saved.nodeId), saved)
    void queryClient.invalidateQueries({ queryKey: queryKeys.workflows.nodePalette.lists() })
  }

  const save = useMutation({
    mutationFn: (body: NodeSaveBody) => updateNode(nodeId!, body),
    onSuccess: invalidate,
  })

  const remove = useMutation({
    mutationFn: () => deleteNode(nodeId!),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: queryKeys.nodes.detail(nodeId ?? '') })
      invalidate()
    },
  })

  return { detail, save, remove }
}
