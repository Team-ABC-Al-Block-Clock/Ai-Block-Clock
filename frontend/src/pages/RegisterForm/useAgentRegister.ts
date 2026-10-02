import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { AGENT_LIMITS, createAgent } from '@/api/agents'
import { getWorkflow } from '@/api/workflows'
import { sellProblem } from '@/lib/canvas/canvasWorkflow'
import { queryKeys } from '@/lib/queryKeys'
import { paths } from '@/routes/paths'

import { FIELD, read } from './fields'
import { describeRegisterError, FIELD_LABEL } from './registerError'

/**
 * 판매 등록 흐름 — 굳힐 워크플로우를 확인하고, 빈 칸을 먼저 막고, 한 번 더 묻고, 보낸다.
 *
 * 입력칸은 제어 컴포넌트가 아니다. 보낼 때 `FIELD` 의 id 로 한 번 읽는다.
 */
export function useAgentRegister() {
  /** 등록된 에이전트와 서버가 산정한 판매 단가. 완료 화면이 그대로 보여 준다 — 화면은 계산하지 않는다.
   *  별도 done 플래그를 두면 ID 없이 완료 화면이 뜨는 상태가 생긴다. */
  const [registered, setRegistered] = useState<{ agentId: string; price: string | null } | null>(
    null,
  )

  const queryClient = useQueryClient()
  const register = useMutation({
    mutationFn: createAgent,
    onSuccess: (res) => {
      setRegistered({ agentId: res.agentId, price: res.price })
      // 등록하면 마켓 목록과 마이페이지에 바로 보여야 한다.
      void queryClient.invalidateQueries({ queryKey: queryKeys.marketplace.agents.lists() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.agents.lists() })
    },
  })

  /**
   * 굳힐 워크플로우와 사용자가 보던 회차. 실행 결과 화면이 실어 보낸다.
   *
   * 회차를 여기서 다시 받아 오지 않는다 — 그러면 그 사이 원본이 바뀌어도 늘 최신과 맞아,
   * 어긋남을 잡으라고 둔 `WORKFLOW_016` 이 영영 안 걸린다. 서버는 `@Positive` 라 0 을 받지
   * 않으므로, 없거나 0 이면 보내지 않고 화면에서 막는다.
   */
  const [searchParams] = useSearchParams()
  const workflowId = searchParams.get('workflowId') ?? ''
  const revision = Number(searchParams.get('revision') ?? 0)
  const ready = !!workflowId && Number.isInteger(revision) && revision > 0

  /**
   * 에이전트 이름은 워크플로우 이름을 그대로 쓴다. 상세만 받는다 — `useSavedWorkflow` 는
   * 팔레트와 노드까지 끌어와 캔버스를 세우는데, 이 화면은 이름 한 줄이면 된다.
   */
  const workflow = useQuery({
    queryKey: queryKeys.workflows.detail(workflowId),
    queryFn: () => getWorkflow(workflowId),
    enabled: !!workflowId,
    staleTime: Infinity,
  })
  const registrationProblem = !workflowId
    ? '등록할 워크플로우를 선택해 주세요'
    : workflow.isError
      ? '워크플로우를 확인하지 못했습니다. 다시 열어 주세요'
      : !workflow.data
        ? '워크플로우를 확인하고 있습니다'
        : sellProblem(workflow.data.nodes)
  /**
   * 취소하고 돌아갈 자리. 실행 결과에서 들어왔으면 그 실행으로 되돌린다 — 워크플로우를 잃으면
   * 빈 캔버스가 뜬다. 접수 번호 없이 들어오는 길은 내 에이전트 목록뿐이라 그리로 보낸다.
   */
  const executionId = searchParams.get('executionId') ?? ''
  const backTo = executionId
    ? `${paths.run.running(executionId)}?workflowId=${workflowId}`
    : paths.my.agents

  /**
   * 등록하면 곧바로 공개되고 내릴 길이 없어 한 번 더 묻는다.
   *
   * 「수정 불가」는 더 이상 참이 아니다 — 서버에 `PUT /agents/{agentId}` 가 생겼다.
   * 지우는 길만 없다(`AgentController` 에 DELETE 매핑 없음).
   */
  const [asking, setAsking] = useState<{ name: string } | null>(null)
  const [localProblem, setLocalProblem] = useState<string | null>(null)

  /**
   * 서버가 `@NotBlank` 로 막는 세 칸. 확인 팝업까지 가고 나서 되돌아오면 무엇을 잘못했는지
   * 알기 어려워, 묻기 전에 먼저 본다. 위 한계는 `maxLength` 가 이미 막는다.
   */
  const missingFields = () => {
    const problems: string[] = []
    if (read(FIELD.name).length < AGENT_LIMITS.name.min) problems.push(FIELD_LABEL.name)
    if (!read(FIELD.summary)) problems.push(FIELD_LABEL.summary)
    if (!read(FIELD.description)) problems.push(FIELD_LABEL.description)
    return problems
  }

  const askSubmit = () => {
    if (register.isPending || registrationProblem) return

    // 굳힐 대상 없이 보내면 서버가 `COMMON_001` 로 끊는다. 여기서 먼저 막고 길을 알려 준다.
    if (!ready)
      return setLocalProblem('실행 결과 화면의 「판매 등록」으로 들어와야 등록할 수 있습니다')

    const problems = missingFields()
    if (problems.length) return setLocalProblem(`${problems.join(' · ')} 을(를) 채워 주세요`)

    setLocalProblem(null)
    setAsking({ name: read(FIELD.name) })
  }

  const submit = () => {
    setAsking(null)
    if (register.isPending || registrationProblem) return
    register.mutate({
      workflowId,
      revision,
      name: read(FIELD.name),
      summary: read(FIELD.summary),
      description: read(FIELD.description),
      usageExample: read(FIELD.usageExample),
      onboardingMessage: read(FIELD.onboardingMessage),
    })
  }

  return {
    workflowName: workflow.data?.name ?? '',
    // 넘겨받은 워크플로우가 없으면 쿼리가 꺼져 있어 `isPending` 이 참인 채로 굳는다.
    namePlaceholder: !workflowId
      ? ''
      : workflow.isPending
        ? '이름을 읽는 중…'
        : '이름을 읽지 못했습니다',
    backTo,
    askSubmit,
    isPending: register.isPending,
    canSubmit: !register.isPending && !registrationProblem,
    /** 보내기 전에 화면이 잡아낸 문제. 서버 오류와 같은 자리에 쓴다. */
    errorMessage: localProblem ?? registrationProblem ?? describeRegisterError(register.error),
    asking,
    submit,
    cancelAsking: () => setAsking(null),
    registered,
  }
}
