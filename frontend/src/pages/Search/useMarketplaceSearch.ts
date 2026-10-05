import { keepPreviousData, useInfiniteQuery } from '@tanstack/react-query'

import { getMarketplaceAgents } from '@/api/agents'
import { useInfiniteSentinel } from '@/hooks/useInfiniteSentinel'
import { queryKeys } from '@/lib/queryKeys'

import { PAGE_SIZE } from './config'

/** 마켓 검색 결과. 검색은 서버가 한다. 목록은 커서로 이어 받는다 — 명세가 전체 개수를 주지 않는다. */
export function useMarketplaceSearch(keyword: string) {
  const {
    data,
    isPending,
    isPlaceholderData,
    isError,
    hasNextPage,
    isFetchingNextPage,
    isFetchNextPageError,
    fetchNextPage,
  } = useInfiniteQuery({
    queryKey: queryKeys.marketplace.agents.list({ keyword, size: PAGE_SIZE }),
    queryFn: ({ pageParam }) =>
      getMarketplaceAgents({ keyword: keyword || undefined, cursor: pageParam, size: PAGE_SIZE }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    // 검색어가 바뀌는 동안 그리드가 비었다가 차오르지 않게 이전 결과를 남긴다.
    placeholderData: keepPreviousData,
  })

  // 커서가 오프셋이라 장 사이에 등록이 끼면 앞 장 끝 항목이 다시 온다. 먼저 받은 것을 남긴다.
  const seen = new Set<string>()
  const agents = (data?.pages.flatMap((page) => page.content) ?? []).filter(
    (a) => !seen.has(a.agentId) && Boolean(seen.add(a.agentId)),
  )

  // 카드 9장이 그리드를 거의 채워(스크롤 여유 41px) sentinel 이 계속 교차 상태로 남는다.
  const sentinelRef = useInfiniteSentinel({
    hasNextPage,
    isFetchingNextPage,
    isFetchNextPageError,
    fetchNextPage,
    getRoot: () => document.getElementById('gridScroll'),
  })

  // 이전 검색어의 결과를 보여주는 동안(placeholder)은 아직 "이번 검색 결과" 가 아니다.
  // hasNextPage 는 새 쿼리 키의 데이터로 계산되므로 그 사이 '+' 가 사라진다.
  const isSettled = !isPending && !isPlaceholderData

  return {
    agents,
    sentinelRef,
    isError,
    isFetchingNextPage,
    isFetchNextPageError,
    fetchNextPage,
    // 전체 개수는 명세에 없다(전수를 만들지 않는 것이 설계 의도). 불러온 만큼만 센다.
    countLabel: isSettled ? `${agents.length}개${hasNextPage ? '+' : ''}` : '…',
    // 불러오는 중에는 "결과 없음" 을 띄우지 않는다 — 매 검색마다 한 번씩 깜빡인다.
    // 카드가 한 장이라도 있으면 띄우지 않는다 — absolute 라 카드 위에 겹쳐 그려진다.
    notice: isSettled && agents.length === 0,
  }
}
