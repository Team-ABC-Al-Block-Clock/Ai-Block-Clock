import { QueryClient } from '@tanstack/react-query'

import { ApiError } from '@/api/errors'

/** 재시도해도 결과가 달라지지 않는 응답 코드. */
const NO_RETRY_STATUS = [400, 401, 403, 404, 409, 413, 415]

/** ApiError 는 상태 코드를 `status` 에 직접 들고 있고 `response` 가 없다.
 *  뒤쪽 분기는 인터셉터를 타지 않은 raw axios 오류용. */
function statusOf(error: unknown): number | undefined {
  if (error instanceof ApiError) return error.status
  if (typeof error !== 'object' || error === null) return undefined
  const response = (error as { response?: { status?: unknown } }).response
  return typeof response?.status === 'number' ? response.status : undefined
}

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // 목록·상세는 실시간성이 낮다. 실행 상태처럼 즉시성이 필요한 쿼리는
      // 해당 훅에서 staleTime 을 따로 낮춘다.
      staleTime: 60 * 1000,
      // 클라이언트 잘못이거나 리소스가 없는 경우는 재시도해도 같은 응답이 온다.
      retry: (failureCount, error) => {
        const status = statusOf(error)
        if (status !== undefined && NO_RETRY_STATUS.includes(status)) return false
        return failureCount < 2
      },
      refetchOnWindowFocus: false,
    },
    mutations: {
      retry: false,
    },
  },
})
