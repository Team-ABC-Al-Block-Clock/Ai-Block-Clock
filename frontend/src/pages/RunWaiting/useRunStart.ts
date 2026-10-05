import { useRef } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'

import { startExecution } from '@/api/executions'
import { queryKeys } from '@/lib/queryKeys'
import { paths } from '@/routes/paths'

import { failedBeforeReserve } from './runWaitingState'

/** 실행 접수. 진행 응답에 그래프가 없어 `workflowId` 도 함께 넘긴다. */
export function useRunStart({
  workflowId,
  prompt,
  file,
}: {
  workflowId: string | undefined
  prompt: string
  file: File | null
}) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  /** 이 누름을 가리키는 값. 보낼 내용이 같으면 그대로 쓴다 — 새로 만들면 두 번 빠진다. */
  const attempt = useRef<{ key: string; requestId: string } | null>(null)
  const requestIdFor = () => {
    // 열쇠는 실제로 보내는 값과 같아야 한다. 코멘트는 다듬어 보내고, 파일은 이름·크기가 같아도 다를 수 있다.
    const key = [
      workflowId ?? '',
      prompt.trim(),
      file?.name ?? '',
      file?.size ?? 0,
      file?.lastModified ?? 0,
    ].join('\u0000')
    if (attempt.current?.key !== key) attempt.current = { key, requestId: crypto.randomUUID() }
    return attempt.current.requestId
  }

  return useMutation({
    mutationFn: () =>
      startExecution({ workflowId: workflowId!, requestId: requestIdFor(), comment: prompt, file }),
    onError: (error) => {
      if (failedBeforeReserve(error)) attempt.current = null
    },
    onSuccess: (started) => {
      // 접수되는 순간 견적만큼 빠진다. 잔액과 견적이 둘 다 옛 값이 된다.
      void queryClient.invalidateQueries({ queryKey: queryKeys.credits.balance() })
      void queryClient.invalidateQueries({
        queryKey: queryKeys.executions.costEstimate(workflowId!),
      })
      navigate(`${paths.run.running(started.executionId)}?workflowId=${workflowId}`)
    },
  })
}
