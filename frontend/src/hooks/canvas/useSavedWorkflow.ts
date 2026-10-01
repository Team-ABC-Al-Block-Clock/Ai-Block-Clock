import { useQueries, useQuery } from '@tanstack/react-query'
import { useMemo } from 'react'

import { getNode } from '@/api/nodes'
import { getNodePalette, getWorkflow, type PlaceableItem } from '@/api/workflows'
import { queryKeys } from '@/lib/queryKeys'
import { toCanvasWorkflow, type NodeLabel } from '@/lib/canvas/workflowPayload'

/** 이름표를 받아 오는 한 쪽 크기. 명세 최대가 30 이라 더 키우면 `COMMON_001` 로 거절된다. */
const LABEL_PAGE = 30
/** 넘길 쪽 수 상한. 저장된 노드가 뒤쪽에 있을 수 있어 넘기되, 끝없이 돌지는 않는다. */
const LABEL_PAGES_MAX = 10

/**
 * 저장된 워크플로우를 캔버스 모양으로 읽는다. 편집·대기·진행 세 화면이 같이 쓴다.
 *
 * 이름·판매 판정은 상세 응답에 오지만 단가·설명·만든 사람은 없어 팔레트에서 따로 받아
 * 채운다. 옆 팔레트를 쓸 수 없다 — 그쪽은 검색어와 스크롤에 따라 일부만 들고 있어서,
 * 저장된 노드가 거기 없을 수 있다. 여기서는 끝까지 넘겨 받아 표로 만든다.
 *
 * 이름표가 도착할 때까지 `canvas` 를 내주지 않는다. 먼저 내주면 카드 단가가 전부 비어 굳는다.
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
   * 넣은 노드는 여기로 빠진다. 프롬프트·모델·출력 형식은 여기서만 받아 편집 폼이 선다.
   */
  const missing = useMemo(() => {
    if (!detail.data || !labels.isSuccess) return []
    const known = new Set((labels.data ?? []).map((item) => item.itemId))
    const ids = detail.data.nodes
      .filter((node) => node.sourceType === 'NODE')
      .map((node) => node.sourceId)
      .filter((id): id is string => !!id && !known.has(id))
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
    // 팔레트 항목을 그대로 넘기지 않는다 — `owner` 의 모양이 다르다.
    const found = new Map<string, NodeLabel>(
      (labels.data ?? []).map((item) => [
        item.itemId,
        {
          name: item.name,
          nodeType: item.nodeType,
          price: item.price,
          summary: item.summary,
          executionCount: item.executionCount,
          owner: item.owner.nickname,
          onboardingMessage: item.onboardingMessage,
          usageExample: item.usageExample,
          mine: item.mine,
        },
      ]),
    )
    for (const query of extras) {
      const node = query.data
      if (!node) continue
      found.set(node.nodeId, {
        name: node.name,
        // 만들어 둔 노드는 서버가 늘 LLM 으로 둔다. 상세 응답에는 종류가 없다.
        nodeType: 'LLM',
        price: node.baseCost,
        summary: null,
        prompt: node.prompt,
        model: node.model,
        output: node.output,
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
    // 이름표가 빠지면 단가와 편집 폼이 통째로 빈다. 개별 조회도 같은 실패로 다룬다.
    error: detail.error ?? labels.error ?? extras.find((query) => query.error)?.error ?? null,
    refetch: () => {
      void detail.refetch()
      void labels.refetch()
      for (const query of extras) void query.refetch()
    },
  }
}
