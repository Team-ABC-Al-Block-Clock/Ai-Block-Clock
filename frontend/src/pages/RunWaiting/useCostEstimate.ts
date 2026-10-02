import { useQuery } from '@tanstack/react-query'

import { estimateCost } from '@/api/executions'
import { queryKeys } from '@/lib/queryKeys'

import { costRatio } from './runWaitingState'

/** 실행 전 견적. 그사이 잔액이 바뀔 수 있어 창에 돌아올 때마다 다시 받는다. */
export function useCostEstimate(workflowId: string | undefined) {
  const estimate = useQuery({
    queryKey: queryKeys.executions.costEstimate(workflowId ?? ''),
    queryFn: () => estimateCost(workflowId!),
    enabled: !!workflowId,
    staleTime: 0,
    refetchOnWindowFocus: true,
  })
  const cost = estimate.data

  return {
    cost,
    items: cost?.items ?? [],
    shortage: cost ? Number(cost.shortageAmount) : 0,
    ratio: costRatio(cost),
    /** 견적을 못 받았을 때는 막지 않는다. 최종 판정은 서버가 접수할 때 다시 한다. */
    blocked: cost ? !cost.executable : false,
    pending: estimate.isPending,
    failed: !!estimate.error,
  }
}
