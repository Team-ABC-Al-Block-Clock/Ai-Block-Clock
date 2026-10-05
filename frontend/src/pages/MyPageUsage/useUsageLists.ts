import { useInfiniteQuery } from '@tanstack/react-query'

import { getCreditHistory, getCreditTransactions } from '@/api/credits'
import { queryKeys } from '@/lib/queryKeys'
import { historyStatus } from '@/lib/wallet/withdrawalMessage'

import { PAGE_SIZE, WALLET_POLL_MS, type ListTab } from './config'

/** 아래 목록 카드의 두 탭 — 기간 안의 사용 내역, 기간과 무관한 충전 · 출금 내역. */
export function useUsageLists({
  range,
  listTab,
}: {
  range: { from?: string; to?: string }
  listTab: ListTab
}) {
  const listParams = { ...range, size: PAGE_SIZE }

  const transactions = useInfiniteQuery({
    queryKey: queryKeys.credits.transactions.list(listParams),
    queryFn: ({ pageParam }) => getCreditTransactions({ ...listParams, cursor: pageParam }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  })

  // 충전 · 출금은 기간 조건이 없다. 처리 중인 줄이 있으면 다시 불러 상태를 따라간다.
  const wallet = useInfiniteQuery({
    queryKey: queryKeys.credits.history.list({ size: PAGE_SIZE }),
    queryFn: ({ pageParam }) => getCreditHistory({ size: PAGE_SIZE, cursor: pageParam }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    enabled: listTab === 'wallet',
    refetchInterval: (query) =>
      query.state.data?.pages[0]?.content.some(
        (h) => historyStatus(h.type, h.status).tone === 'progress',
      )
        ? WALLET_POLL_MS
        : false,
  })

  const walletRows = wallet.data?.pages.flatMap((page) => page.content) ?? []
  const walletTab = listTab === 'wallet'
  const list = walletTab ? wallet : transactions

  const history = transactions.data?.pages.flatMap((page) => page.content) ?? []

  return {
    walletRows,
    history,
    isPending: list.isPending,
    isError: list.isError,
    // 불러오는 중을 빈 목록으로 보면 요청마다 '없음' 이 깜빡인다.
    isEmpty: !list.isPending && !list.isError && (walletTab ? walletRows : history).length === 0,
    hasMore: list.hasNextPage,
    isFetchingMore: list.isFetchingNextPage,
    fetchMore: () => void list.fetchNextPage(),
  }
}
