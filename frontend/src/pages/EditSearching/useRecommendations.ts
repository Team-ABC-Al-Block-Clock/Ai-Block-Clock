import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'

import { getAgentRecommendations } from '@/api/agents'
import { queryKeys } from '@/lib/queryKeys'

import { MIN_SEARCHING_MS, SIZE } from './config'

/** 검색어에 맞는 추천. 답이 빨라도 「검색 중」을 최소 시간만큼 붙든다. */
export function useRecommendations(query: string) {
  /** 최소 시간을 채운 검색어. 값으로 들고 있어야 다시 찾을 때 저절로 되감긴다. */
  const [settledFor, setSettledFor] = useState<string | null>(null)

  const { data, status, isFetching, refetch } = useQuery({
    queryKey: queryKeys.marketplace.agents.recommendations({ query, size: SIZE }),
    queryFn: () => getAgentRecommendations({ query, size: SIZE }),
    enabled: query.length > 0,
  })

  useEffect(() => {
    const timer = setTimeout(() => setSettledFor(query), MIN_SEARCHING_MS)
    return () => clearTimeout(timer)
  }, [query])

  const items = data?.items ?? []
  // 첫 답을 받기 전만 「검색 중」이다. `status` 는 재시도를 기다리는 동안에도 `pending` 이라
  // 그 틈에 「찾지 못했습니다」가 뜨지 않는다. 뒤에서 다시 부르는 것은 여기 넣지 않는다 —
  // 화면을 통째로 되돌리면 목록 스크롤과 작업면에 올려 둔 선택이 같이 날아간다.
  const searching = status === 'pending' || settledFor !== query

  return {
    items,
    searching,
    isFetching,
    refetch,
    failed: status === 'error' && !searching,
    isEmpty: status === 'success' && !searching && items.length === 0,
    ready: !searching && status === 'success' && items.length > 0,
  }
}
