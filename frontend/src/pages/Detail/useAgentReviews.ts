import { useInfiniteQuery } from '@tanstack/react-query'
import { useState } from 'react'

import { getAgentReviews } from '@/api/agents'
import { queryKeys } from '@/lib/queryKeys'

/** 한 번에 불러오는 리뷰 개수. */
const REVIEW_PAGE_SIZE = 5

/**
 * 상세 화면의 리뷰. 상세 · 증명과 나란히 나간다.
 *
 * 처음에는 '더 보기' 버튼만 둔다. 상세를 보러 온 사람에게 리뷰가 먼저 자라면 읽던 자리를
 * 잃는다 — 더 볼지는 사용자가 정한다. 한 번 누르면 그 뒤로는 끝에 닿을 때마다 이어받는다.
 */
export function useAgentReviews(agentId: string | undefined) {
  const reviews = useInfiniteQuery({
    queryKey: queryKeys.marketplace.agents.reviews.list(agentId ?? '', {
      size: REVIEW_PAGE_SIZE,
    }),
    queryFn: ({ pageParam }) =>
      getAgentReviews(agentId ?? '', { cursor: pageParam, size: REVIEW_PAGE_SIZE }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    enabled: !!agentId,
  })

  /* 불린 대신 어느 에이전트에서 눌렀는지를 담는다 — 다른 상세로 옮기면 effect 로
     되돌리지 않아도 저절로 버튼 상태로 돌아간다. */
  const [autoLoadFor, setAutoLoadFor] = useState<string | null>(null)
  const autoLoad = autoLoadFor === agentId
  /** 바닥에 닿아 다음 장을 기다리는 중. 아직 요청 전이다. */
  const [hold, setHold] = useState(false)

  const { hasNextPage, isFetchingNextPage, isFetchNextPageError, fetchNextPage } = reviews

  return {
    items: reviews.data?.pages.flatMap((page) => page.content) ?? [],
    isPending: reviews.isPending,
    isError: reviews.isError,
    isFetching: reviews.isFetching,
    refetch: () => void reviews.refetch(),
    hasNextPage,
    isFetchingNextPage,
    isFetchNextPageError,
    fetchNextPage: () => void fetchNextPage(),
    autoLoad,
    /** '더 보기' — 이 에이전트에서는 이제부터 끝에 닿을 때마다 이어받는다. */
    startAutoLoad: () => {
      setAutoLoadFor(agentId ?? null)
      void fetchNextPage()
    },
    hold,
    setHold,
  }
}
