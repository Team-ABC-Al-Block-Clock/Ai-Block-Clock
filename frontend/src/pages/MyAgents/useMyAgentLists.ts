import {
  keepPreviousData,
  useInfiniteQuery,
  useMutation,
  useQueryClient,
} from '@tanstack/react-query'
import { useEffect, useRef, useState, type ChangeEvent } from 'react'

import {
  addAgentFavorite,
  getMyAgents,
  getMyFavorites,
  removeAgentFavorite,
  type AgentDetail,
} from '@/api/agents'
import { getMyWorkflows } from '@/api/workflows'
import { useInfiniteSentinel } from '@/hooks/useInfiniteSentinel'
import { priceLabel, runLabel } from '@/lib/format'
import { queryKeys } from '@/lib/queryKeys'

import { PAGE_SIZE, SORT_LABEL, SORTS, type Row, type Tab } from './config'

/** 입력이 멈춘 뒤에만 검색한다. */
const DEBOUNCE_MS = 300

/**
 * 나의 에이전트 세 탭의 목록. 탭 하나가 API 하나이고, 보이는 탭만 불러온다.
 *
 * 검색어 · 탭별 정렬 · 즐겨찾기 토글 · 무한 스크롤을 함께 든다.
 */
export function useMyAgentLists({ onTabChange }: { onTabChange: () => void }) {
  const [tab, setTab] = useState<Tab>('selling')
  const [q, setQ] = useState('')
  const [keyword, setKeyword] = useState('')
  /** 탭마다 자기 정렬을 기억한다. 하나로 두면 탭을 옮길 때마다 다른 탭의 정렬로 조회한다. */
  const [sortAt, setSortAt] = useState<Record<Tab, number>>({
    selling: 0,
    favorites: 0,
    flows: 0,
  })

  /** 주소를 되읽어 value 로 쓰지 않는다 — 리렌더가 조합 중인 input 을 덮으면 한글이
   *  낱자로 떨어진다. 입력값의 주인은 `q` 하나다. */
  useEffect(() => {
    const timer = setTimeout(() => setKeyword(q.trim()), DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [q])

  const sellingSort = SORTS.selling[sortAt.selling]
  const flowSort = SORTS.flows[sortAt.flows]

  // 탭 하나가 API 하나다. 보이지 않는 목록까지 요청하지 않게 활성 탭만 enabled.
  const selling = useInfiniteQuery({
    queryKey: queryKeys.agents.list({ keyword, sort: sellingSort, size: PAGE_SIZE }),
    queryFn: ({ pageParam }) =>
      getMyAgents({
        keyword: keyword || undefined,
        sort: sellingSort,
        cursor: pageParam,
        size: PAGE_SIZE,
      }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    // 검색어가 바뀌는 동안 목록이 비었다가 차오르지 않게 이전 결과를 남긴다.
    placeholderData: keepPreviousData,
    enabled: tab === 'selling',
  })
  const favorites = useInfiniteQuery({
    queryKey: queryKeys.users.favorites.list({ size: PAGE_SIZE }),
    queryFn: ({ pageParam }) => getMyFavorites({ cursor: pageParam, size: PAGE_SIZE }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    enabled: tab === 'favorites',
  })
  const flows = useInfiniteQuery({
    queryKey: queryKeys.workflows.list({ keyword, sort: flowSort, size: PAGE_SIZE }),
    queryFn: ({ pageParam }) =>
      getMyWorkflows({
        keyword: keyword || undefined,
        sort: flowSort,
        cursor: pageParam,
        size: PAGE_SIZE,
      }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    placeholderData: keepPreviousData,
    enabled: tab === 'flows',
  })

  const active = tab === 'selling' ? selling : tab === 'favorites' ? favorites : flows

  const queryClient = useQueryClient()

  /** 즐겨찾기는 화면 안 상태가 아니라 서버 값이다. 상세에서 켠 별이 목록에도 보여야 한다. */
  const favorite = useMutation({
    mutationFn: ({ agentId, next }: { agentId: string; next: boolean }) =>
      next ? addAgentFavorite(agentId) : removeAgentFavorite(agentId),
    /** 갱신된 횟수가 함께 오므로 상세는 덮고 목록은 무효화한다 —
     *  즐겨찾기 탭에서 해제하면 그 행이 사라지는 것이 맞다. */
    onSuccess: (res) => {
      queryClient.setQueryData(
        queryKeys.marketplace.agents.detail(res.agentId),
        (prev: AgentDetail | undefined) =>
          prev && { ...prev, favorited: res.favorited, favoriteCount: res.favoriteCount },
      )
      void queryClient.invalidateQueries({ queryKey: queryKeys.users.favorites.lists() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.agents.lists() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.marketplace.agents.lists() })
    },
  })

  const handleTab = (next: Tab) => () => {
    setTab(next)
    // 검색어를 남기면 검색을 받지 않는 탭(즐겨찾기)에서 입력만 남아 아무 효과가 없다.
    setQ('')
    setKeyword('')
    onTabChange()
  }
  const handleChangeQuery = (e: ChangeEvent<HTMLInputElement>) => setQ(e.target.value)
  const options: readonly string[] = SORTS[tab]
  const sortLabel = options.length
    ? SORT_LABEL[options[sortAt[tab]] as keyof typeof SORT_LABEL]
    : ''
  /** 옵션이 둘·셋뿐이라 눌러서 다음 값으로 넘긴다. 팝업·바깥 클릭 닫기가 필요 없다.
   *  넷을 넘기면 그때 드롭다운을 만든다. */
  const cycleSort = () =>
    setSortAt((prev) => ({ ...prev, [tab]: (prev[tab] + 1) % Math.max(1, options.length) }))
  /** 같은 행을 연달아 누르면 등록·해제가 엇갈리므로 막는다. 다른 행은 막지 않는다 —
   *  전역 `isPending` 으로 막으면 옆 행의 별 클릭이 아무 표시 없이 사라진다. */
  const pendingFavAt = favorite.isPending ? favorite.variables.agentId : null
  const toggleFav = (agentId: string, next: boolean) => () => {
    if (pendingFavAt !== agentId) favorite.mutate({ agentId, next })
  }

  const rows: Row[] =
    tab === 'selling'
      ? (selling.data?.pages.flatMap((page) => page.content) ?? []).map((a) => ({
          agentId: a.agentId,
          name: a.name,
          desc: a.summary,
          iconUrl: a.iconUrl,
          mine: true,
          favorited: a.favorited,
          metrics: [`사용 ${runLabel(a.executionCount)}`, `단가 ${priceLabel(a.price)}`],
          trend: a.trend,
          note: `누적 ${priceLabel(a.revenue)}`,
        }))
      : tab === 'favorites'
        ? (favorites.data?.pages.flatMap((page) => page.content) ?? []).map((a) => ({
            agentId: a.agentId,
            name: a.name,
            desc: a.summary,
            iconUrl: a.iconUrl,
            mine: false,
            // 이 목록에 있다는 것이 곧 즐겨찾기했다는 뜻이다 — 응답에 favorited 가 없다.
            favorited: true,
            metrics: [`사용 ${runLabel(a.executionCount)}`, `단가 ${priceLabel(a.price)}`],
            trend: [],
          }))
        : []

  const flowRows = tab === 'flows' ? (flows.data?.pages.flatMap((page) => page.content) ?? []) : []
  const count = tab === 'flows' ? flowRows.length : rows.length

  const { hasNextPage, isFetchingNextPage, isFetchNextPageError } = active

  /** 활성 탭의 콜백은 렌더마다 새 함수다. 이벤트 핸들러가 최신 함수를 참조로 들고 간다. */
  const latest = useRef({ fetchNextPage: active.fetchNextPage, refetch: active.refetch })
  useEffect(() => {
    latest.current = { fetchNextPage: active.fetchNextPage, refetch: active.refetch }
  })

  const sentinelRef = useInfiniteSentinel({
    hasNextPage,
    isFetchingNextPage,
    isFetchNextPageError,
    fetchNextPage: active.fetchNextPage,
    getRoot: () => document.getElementById('myRows'),
    resetKey: tab,
  })

  /** observer 는 실패한 커서를 다시 밟지 않으려고 스스로를 끈다. */
  const handleRetryNextPage = () => {
    if (!isFetchingNextPage) void latest.current.fetchNextPage()
  }

  // 이전 검색어의 결과를 보여주는 동안(placeholder)은 아직 '이번 결과' 가 아니다.
  const isSettled = !active.isPending && !active.isPlaceholderData

  return {
    tab,
    handleTab,
    q,
    keyword,
    handleChangeQuery,
    clearQuery: () => setQ(''),
    sortLabel,
    hasSort: options.length > 0,
    cycleSort,
    rows,
    flowRows,
    toggleFav,
    sentinelRef,
    isFetchingNextPage,
    isFetchNextPageError,
    handleRetryNextPage,
    refetch: () => void latest.current.refetch(),
    // 불러오는 중에는 '없음' 을 띄우지 않는다 — 요청마다 한 번씩 깜빡인다.
    notice: isSettled && count === 0,
    /** 커서 목록은 전체 개수를 주지 않는다. 불러온 만큼만 세고 더 있으면 '+' */
    countLabel: isSettled ? `${count}개${hasNextPage ? '+' : ''}` : '…',
    isError: active.isError,
    error: active.error,
  }
}
