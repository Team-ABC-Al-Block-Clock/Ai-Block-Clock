import { useEffect, useRef } from 'react'

interface InfiniteSentinelOptions {
  hasNextPage: boolean
  isFetchingNextPage: boolean
  isFetchNextPageError: boolean
  fetchNextPage: () => unknown
  /**
   * 목록이 스크롤되는 상자. 뷰포트를 root 로 두면 상자를 끝까지 내려도 sentinel 이 교차하지 않는다.
   * 붙일 때마다 다시 부르므로 렌더마다 새 함수여도 된다.
   */
  getRoot: () => Element | null
  rootMargin?: string
  /** 바뀌면 observer 를 다시 붙인다. 한 sentinel 로 여러 목록을 오가는 화면(탭)이 쓴다. */
  resetKey?: unknown
}

/**
 * 목록 끝의 sentinel 이 보이면 다음 장을 불러온다. 돌려준 ref 를 목록 맨 끝 요소에 단다.
 *
 * - 실패한 커서로는 다시 시도하지 않는다. 교차할 때마다 같은 요청이 반복된다. 다시 시도는
 *   화면의 버튼이 `fetchNextPage` 를 직접 부른다.
 * - `isFetchingNextPage` 가 바뀔 때마다 observer 를 다시 붙인다. `observe()` 는 이미 교차 중인
 *   sentinel 에도 콜백을 한 번 쏘므로, 한 장이 화면을 다 채우지 못해 sentinel 이 계속 보이는
 *   목록도 다음 장으로 이어진다. 교차 '변화' 만 기다리면 거기서 멈춘다.
 * - `fetchNextPage` 는 최신 것만 참조로 들고 간다. 탭마다 다른 쿼리를 쓰면 렌더마다 새 함수라
 *   deps 에 넣는 순간 observer 가 매 렌더 다시 붙어 다음 장이 줄줄이 딸려 온다.
 */
export function useInfiniteSentinel<T extends Element = HTMLDivElement>({
  hasNextPage,
  isFetchingNextPage,
  isFetchNextPageError,
  fetchNextPage,
  getRoot,
  rootMargin = '200px',
  resetKey,
}: InfiniteSentinelOptions) {
  const sentinelRef = useRef<T>(null)
  const latest = useRef({ fetchNextPage, getRoot })
  useEffect(() => {
    latest.current = { fetchNextPage, getRoot }
  })

  useEffect(() => {
    const node = sentinelRef.current
    if (!node || !hasNextPage || isFetchNextPageError) return
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && !isFetchingNextPage) void latest.current.fetchNextPage()
      },
      { root: latest.current.getRoot(), rootMargin },
    )
    observer.observe(node)
    return () => observer.disconnect()
  }, [hasNextPage, isFetchingNextPage, isFetchNextPageError, rootMargin, resetKey])

  return sentinelRef
}
