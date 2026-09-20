import { keepPreviousData, useInfiniteQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'

import { getNodePalette } from '@/api/workflows'
import { nodeTypeLabel, priceLabel, runLabel } from '@/lib/format'
import { queryKeys } from '@/lib/queryKeys'

/** 한 번에 불러오는 개수. */
const PAGE_SIZE = 10
/** 입력이 멈춘 뒤에만 검색한다. */
const DEBOUNCE_MS = 300
/** 내 노드와 마켓 에이전트를 한 목록으로 본다. 등록 가능 여부는 조회가 아니라 놓인 구성(`canSell`)이 정한다. */
const SCOPE = 'ALL' as const

/** 드래그로 주고받는 것은 팔레트 항목 id 하나뿐이다. 브라우저가 소문자로만 받는다. */
export const DND_TYPE = 'application/x-placeable-item'

/**
 * 오른쪽 패널의 노드 팔레트 — 검색·무한 스크롤·항목 정보.
 *
 * 캔버스와는 "무엇을 놓았는가" 로만 만난다. 놓는 동작 자체는 캔버스가 맡고,
 * 여기서는 끌어갈 항목을 찾아 주기만 한다.
 */
export function useNodePalette() {
  const [panelOpen, setPanelOpen] = useState(false)
  // 카드 index 로 잡으면 다음 장이 붙을 때 엉뚱한 카드가 열린다.
  const [infoAt, setInfoAt] = useState<string | null>(null)
  const [q, setQ] = useState('')
  const [keyword, setKeyword] = useState('')

  useEffect(() => {
    const timer = setTimeout(() => setKeyword(q.trim()), DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [q])

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
    queryKey: queryKeys.workflows.nodePalette.list({ scope: SCOPE, keyword, size: PAGE_SIZE }),
    queryFn: ({ pageParam }) =>
      getNodePalette({
        scope: SCOPE,
        keyword: keyword || undefined,
        cursor: pageParam,
        size: PAGE_SIZE,
      }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    // 검색어가 바뀌는 동안 목록이 비었다가 차오르지 않게 이전 결과를 남긴다.
    placeholderData: keepPreviousData,
  })

  const items = data?.pages.flatMap((page) => page.content) ?? []
  // placeholder 는 아직 이번 검색의 결과가 아니다. isPending 만 보면 한 글자마다 깜빡인다.
  const isSettled = !isPending && !isPlaceholderData
  const isEmpty = isSettled && !isError && items.length === 0

  const paletteRef = useRef<HTMLDivElement>(null)
  const sentinelRef = useRef<HTMLDivElement>(null)

  // 아래쪽에 머문 채 목록만 바뀌면 새 결과의 앞부분을 지나쳐 검색에 실패한 것처럼 보인다.
  useEffect(() => {
    paletteRef.current?.scrollTo({ top: 0 })
  }, [keyword])

  useEffect(() => {
    const node = sentinelRef.current
    // 실패한 커서로 다시 시도하지 않는다 — 교차할 때마다 같은 요청이 반복된다.
    if (!node || !hasNextPage || isFetchNextPageError) return
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && !isFetchingNextPage) void fetchNextPage()
      },
      { root: paletteRef.current, rootMargin: '160px' },
    )
    observer.observe(node)
    return () => observer.disconnect()
  }, [hasNextPage, isFetchingNextPage, isFetchNextPageError, fetchNextPage])

  const cards = items.map((item) => ({
    ...item,
    initial: item.name.trim().charAt(0),
    uses: runLabel(item.executionCount),
    unit: priceLabel(item.price),
    typeLabel: nodeTypeLabel(item.nodeType),
    infoOpen: infoAt === item.itemId,
    infoBg: infoAt === item.itemId ? 'rgba(255,255,255,.34)' : 'rgba(255,255,255,.18)',
    drag: (ev: React.DragEvent<HTMLDivElement>) => {
      // 항목 id 만 넘긴다. 놓는 쪽이 같은 목록에서 다시 찾는다.
      ev.dataTransfer.setData(DND_TYPE, item.itemId)
      ev.dataTransfer.effectAllowed = 'copy'
    },
    info: (ev: React.MouseEvent) => {
      ev.preventDefault()
      ev.stopPropagation()
      setInfoAt((v) => (v === item.itemId ? null : item.itemId))
    },
  }))

  return {
    panelOpen,
    q,
    cards,
    isEmpty,
    isError,
    refs: { paletteRef, sentinelRef },
    /** 놓인 항목을 id 로 되찾는다 — 드래그로는 id 하나만 건너온다. */
    findItem: (itemId: string) => items.find((it) => it.itemId === itemId),
    handleTogglePanel: () => setPanelOpen((v) => !v),
    handleSearch: (ev: React.ChangeEvent<HTMLInputElement>) => setQ(ev.target.value),
  }
}
