import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { ApiError } from '@/api/errors'
import { deleteWorkflow } from '@/api/workflows'
import { queryKeys } from '@/lib/queryKeys'

import { DELETE_MESSAGE } from './config'

/** 워크플로우 삭제. 되돌릴 수 없어 확인 모달을 거친다. */
export function useWorkflowDelete() {
  /** 이름을 함께 붙들어 둔다 — 목록을 무효화하면 그 행이 사라져 제목이 비어 버린다 */
  const [target, setTarget] = useState<{ workflowId: string; name: string } | null>(null)

  const queryClient = useQueryClient()
  const remove = useMutation({
    mutationFn: (workflowId: string) => deleteWorkflow(workflowId),
    onSuccess: () => {
      setTarget(null)
      void queryClient.invalidateQueries({ queryKey: queryKeys.workflows.lists() })
    },
    onError: (error) => {
      // 이미 지워진 것이면 목록이 낡았다. 모달은 열어 두고 목록만 맞춘다.
      if (error instanceof ApiError && error.code === 'WORKFLOW_002')
        void queryClient.invalidateQueries({ queryKey: queryKeys.workflows.lists() })
    },
  })

  const error = remove.error
  const message =
    error instanceof ApiError
      ? (DELETE_MESSAGE[error.code ?? ''] ??
        (error.isNetworkError ? '서버에 연결할 수 없습니다' : '삭제에 실패했습니다'))
      : error
        ? '삭제에 실패했습니다'
        : null

  return {
    target,
    ask: (workflowId: string, name: string) => setTarget({ workflowId, name }),
    close: () => {
      setTarget(null)
      remove.reset()
    },
    confirm: () => {
      if (target && !remove.isPending) remove.mutate(target.workflowId)
    },
    reset: remove.reset,
    isPending: remove.isPending,
    message,
  }
}
