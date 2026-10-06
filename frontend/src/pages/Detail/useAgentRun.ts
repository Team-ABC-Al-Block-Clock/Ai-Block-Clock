import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useLocation, useNavigate } from 'react-router-dom'

import { ApiError } from '@/api/errors'
import { saveWorkflow } from '@/api/workflows'
import { agentRunGraph } from '@/lib/canvas/workflowPayload'
import { queryKeys } from '@/lib/queryKeys'
import type { LoginRedirectState } from '@/routes/access'
import { paths } from '@/routes/paths'
import { isSessionPending, useAuthStore } from '@/stores/authStore'

/**
 * 상세 화면의 실행하기. 시작 → 에이전트 → 종료로 이은 워크플로우를 저장하고 실행 대기 화면으로 간다.
 *
 * 실행 대기 화면은 저장된 워크플로우만 돌린다. 인사말과 입력 예시는 그 화면이 맨 앞 에이전트에서 읽는다.
 */
export function useAgentRun(agentId: string) {
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const status = useAuthStore((s) => s.status)

  const save = useMutation({
    mutationFn: (name: string) => saveWorkflow({ name, ...agentRunGraph(agentId) }),
    onSuccess: (saved) => {
      // 나의 워크플로우 목록에 새로 생긴다.
      void queryClient.invalidateQueries({ queryKey: queryKeys.workflows.lists() })
      navigate(`${paths.run.waiting}?workflowId=${saved.workflowId}`)
    },
  })

  /** 워크플로우 이름은 에이전트 이름으로 둔다. 나의 워크플로우 목록에서 무엇을 돌렸는지 알아보게. */
  const run = (name: string) => {
    // 세션을 확인하는 중이면 로그인한 사람도 로그인 화면으로 보내게 된다.
    if (save.isPending || isSessionPending(status)) return
    // 저장하려면 세션이 있어야 한다. 로그인을 마치면 이 상세로 돌아온다.
    if (status !== 'authenticated')
      return navigate(paths.login, {
        state: { from: location.pathname + location.search } satisfies LoginRedirectState,
      })
    save.mutate(name)
  }

  const message = save.error
    ? save.error instanceof ApiError && !save.error.isNetworkError
      ? save.error.message
      : '실행을 준비하지 못했습니다. 잠시 후 다시 시도해 주세요'
    : null

  return { run, pending: save.isPending, message }
}
