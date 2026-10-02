import { useMutation, useQueryClient } from '@tanstack/react-query'

import { addAgentFavorite, removeAgentFavorite, type AgentDetail } from '@/api/agents'
import { ApiError, AUTH_ERROR } from '@/api/errors'
import { queryKeys } from '@/lib/queryKeys'

/** 즐겨찾기 실패 중 로그인 문제. 실제로 오는 것은 `AUTH_005` 뿐이다(`api/errors.ts`). */
const AUTH_CODES: string[] = [AUTH_ERROR.NOT_IDENTIFIED, AUTH_ERROR.NOT_IDENTIFIABLE]

/** 상세 화면의 즐겨찾기 별. */
export function useFavoriteToggle(agentId: string) {
  const queryClient = useQueryClient()
  const favorite = useMutation({
    mutationFn: (next: boolean) =>
      next ? addAgentFavorite(agentId) : removeAgentFavorite(agentId),
    /** 명세 「화면이 다시 조회하지 않게 갱신된 횟수를 함께 반환한다」 — 상세는 refetch 하지 않고 덮는다.
     *
     *  목록은 무효화한다. 즐겨찾기 탭 자체가 `favorited` 로 만들어져서, 안 하면
     *  여기서 켠 별이 목록에서는 빈 별로 남는다. */
    onSuccess: (res) => {
      queryClient.setQueryData(
        queryKeys.marketplace.agents.detail(agentId),
        (prev: AgentDetail | undefined) =>
          prev && { ...prev, favorited: res.favorited, favoriteCount: res.favoriteCount },
      )
      void queryClient.invalidateQueries({ queryKey: queryKeys.users.favorites.lists() })
      // 사용한 에이전트 탭은 지금 없지만 API 가 생기면 다시 붙이므로 남겨 둔다.
      void queryClient.invalidateQueries({ queryKey: queryKeys.users.usedAgents.lists() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.agents.lists() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.marketplace.agents.lists() })
    },
  })

  const error = favorite.error
  return {
    /** 진행 중에 또 누르면 등록·해제가 엇갈린다. mutations retry 는 false 가 기본이다. */
    toggle: (favorited: boolean) => {
      if (!favorite.isPending) favorite.mutate(!favorited)
    },
    isPending: favorite.isPending,
    message: !error
      ? null
      : error instanceof ApiError && error.code && AUTH_CODES.includes(error.code)
        ? '로그인이 필요합니다'
        : '즐겨찾기에 실패했습니다',
  }
}
