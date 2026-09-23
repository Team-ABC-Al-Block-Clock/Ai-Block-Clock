import { useQuery } from '@tanstack/react-query'
import { useMemo } from 'react'

import { getNodePalette, getWorkflow, type PlaceableItem } from '@/api/workflows'
import { queryKeys } from '@/lib/queryKeys'
import { toCanvasWorkflow } from '@/lib/workflowPayload'

/** 이름표를 받아 오는 한 쪽 크기. 명세 최대가 30 이라 더 키우면 `COMMON_001` 로 거절된다. */
const LABEL_PAGE = 30
/** 넘길 쪽 수 상한. 저장된 노드가 뒤쪽에 있을 수 있어 넘기되, 끝없이 돌지는 않는다. */
const LABEL_PAGES_MAX = 10

/**
 * 저장된 워크플로우를 캔버스 모양으로 읽는다. 편집·대기·진행 세 화면이 같이 쓴다.
 *
 * 서버는 배치에 이름을 싣지 않아(`NodeResponse` 는 `sourceId` 까지다) 팔레트에서 따로 받아
 * 채운다. 옆 팔레트를 쓸 수 없다 — 그쪽은 검색어와 스크롤에 따라 일부만 들고 있어서,
 * 저장된 노드가 거기 없을 수 있다. 여기서는 끝까지 넘겨 받아 표로 만든다.
 *
 * 이름표가 도착할 때까지 `canvas` 를 내주지 않는다. 먼저 내주면 카드가 전부
 * 「알 수 없는 노드」로 굳는다.
 */
export function useSavedWorkflow(workflowId: string | undefined) {
  const detail = useQuery({
    queryKey: queryKeys.workflows.detail(workflowId ?? ''),
    queryFn: () => getWorkflow(workflowId!),
    enabled: !!workflowId,
    // 옮긴 뒤로는 화면이 원본이다. 다시 받아 덮으면 편집이 날아간다.
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  })

  const labels = useQuery({
    queryKey: queryKeys.workflows.nodePalette.list({ scope: 'ALL', size: LABEL_PAGE, every: true }),
    queryFn: async () => {
      const all: PlaceableItem[] = []
      let cursor: string | undefined
      for (let page = 0; page < LABEL_PAGES_MAX; page++) {
        const got = await getNodePalette({ scope: 'ALL', size: LABEL_PAGE, cursor })
        all.push(...got.content)
        if (!got.hasNext || !got.nextCursor) break
        cursor = got.nextCursor
      }
      return all
    },
    enabled: !!workflowId,
    staleTime: Infinity,
  })

  const canvas = useMemo(() => {
    // 이름표가 「도착」해야 한다. 실패도 isPending 은 false 라, 그것만 보면 이름 없는 카드가 굳는다.
    if (!detail.data || !labels.isSuccess) return null
    const found = new Map((labels.data ?? []).map((item) => [item.itemId, item]))
    return toCanvasWorkflow(detail.data, (sourceId) => found.get(sourceId))
  }, [detail.data, labels.data, labels.isSuccess])

  return {
    detail: detail.data,
    canvas,
    isPending: !!workflowId && (detail.isPending || labels.isPending),
    // 이름표가 빠져도 카드가 통째로 이름을 잃는다. 같은 실패로 다룬다.
    error: detail.error ?? labels.error,
    refetch: () => {
      void detail.refetch()
      void labels.refetch()
    },
  }
}
