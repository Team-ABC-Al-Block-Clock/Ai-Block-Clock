import { useQueries, useQuery } from '@tanstack/react-query'
import { useMemo } from 'react'

import { getNode } from '@/api/nodes'
import { getNodePalette, getWorkflow, type PlaceableItem } from '@/api/workflows'
import { queryKeys } from '@/lib/queryKeys'
import { toCanvasWorkflow, type NodeLabel } from '@/lib/workflowPayload'

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

  /**
   * 팔레트가 모르는 내 노드. 팔레트에는 마켓 에이전트만 실려서, 「노드 추가」로 만들어
   * 넣은 노드는 여기로 빠진다. 이름을 못 채우면 카드가 「알 수 없는 노드」로 굳고
   * `mine` 이 false 가 되어 편집도 막힌다.
   */
  const missing = useMemo(() => {
    if (!detail.data || !labels.isSuccess) return []
    const known = new Set((labels.data ?? []).map((item) => item.itemId))
    const ids = detail.data.nodes
      .filter((node) => node.sourceType === 'NODE' && !known.has(node.sourceId))
      .map((node) => node.sourceId)
    return [...new Set(ids)]
  }, [detail.data, labels.data, labels.isSuccess])

  const extras = useQueries({
    queries: missing.map((nodeId) => ({
      queryKey: queryKeys.nodes.detail(nodeId),
      queryFn: () => getNode(nodeId),
      staleTime: Infinity,
    })),
  })

  const extrasSettled = extras.every((query) => !query.isPending)

  const canvas = useMemo(() => {
    // 이름표가 「도착」해야 한다. 실패도 isPending 은 false 라, 그것만 보면 이름 없는 카드가 굳는다.
    if (!detail.data || !labels.isSuccess || !extrasSettled) return null
    const found = new Map<string, NodeLabel>((labels.data ?? []).map((item) => [item.itemId, item]))
    for (const query of extras) {
      const node = query.data
      if (!node) continue
      found.set(node.nodeId, {
        name: node.name,
        // 만들어 둔 노드는 서버가 늘 LLM 으로 둔다. 상세 응답에는 종류가 없다.
        nodeType: 'LLM',
        price: node.baseCost,
        // 내 노드는 설명 대신 프롬프트를 보여준다. 패널이 원본을 따로 읽는다.
        summary: null,
        // 남의 노드는 서버가 `NODE_002` 로 막아 여기까지 오지 않는다.
        mine: true,
      })
    }
    return toCanvasWorkflow(detail.data, (sourceId) => found.get(sourceId))
    // `extras` 는 렌더마다 새 배열이라 넣으면 매번 다시 만든다. 도착 여부로 가른다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [detail.data, labels.data, labels.isSuccess, extrasSettled])

  return {
    detail: detail.data,
    canvas,
    isPending: !!workflowId && (detail.isPending || labels.isPending || !extrasSettled),
    // 이름표가 빠져도 카드가 통째로 이름을 잃는다. 같은 실패로 다룬다.
    error: detail.error ?? labels.error,
    refetch: () => {
      void detail.refetch()
      void labels.refetch()
    },
  }
}
