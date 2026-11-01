import { useEffect, useRef } from 'react'

import { ApiError } from '@/api/errors'
import { paths } from '@/routes/paths'

/** 캔버스를 덮는다 — 자리표시자든 빈 화면이든 남겨 두면 불러와진 것으로 보인다. */
const NOTICE_BOX = { left: '180px', top: '380px', width: '620px' } as const

/** 빈 상태는 실패가 아니라 다음 행동을 고르는 자리다(`components/types.ts` 의 EmptyAction). */
const GONE_ACTIONS = JSON.stringify([
  { label: '새 워크플로우 만들기', href: paths.edit.nodes.withoutId, primary: true },
  { label: '내 워크플로우로', href: paths.my.agents },
])

const RETRY_ACTIONS = JSON.stringify([
  { label: '다시 시도', event: 'retry', primary: true },
  { label: '내 워크플로우로', href: paths.my.agents },
])

interface WorkflowLoadNoticeProps {
  error: unknown
  onRetry: () => void
}

/**
 * 워크플로우를 불러오지 못했을 때의 안내. 편집·대기·진행 세 화면이 같이 쓴다.
 *
 * 없는 것과 남의 것은 다시 시도해도 달라지지 않으므로 다른 길을 준다. 나머지만 재시도를 건다.
 */
export default function WorkflowLoadNotice({ error, onRetry }: WorkflowLoadNoticeProps) {
  // <empty-state> 의 '다시 시도'. React 는 커스텀 이벤트를 on* 으로 받지 않아 직접 붙인다.
  const ref = useRef<HTMLElement>(null)
  useEffect(() => {
    const node = ref.current
    if (!node) return
    node.addEventListener('retry', onRetry)
    return () => node.removeEventListener('retry', onRetry)
  }, [onRetry])

  const apiError = error instanceof ApiError ? error : null
  const forbidden = apiError?.code === 'WORKFLOW_003'
  const gone = forbidden || apiError?.code === 'WORKFLOW_002'

  return (
    <empty-state
      ref={ref}
      style={{ position: 'absolute', ...NOTICE_BOX, animation: 'fadeIn .3s ease both' }}
      heading={
        gone
          ? forbidden
            ? '이 워크플로우를 열 수 없습니다'
            : '워크플로우를 찾을 수 없습니다'
          : '워크플로우를 불러오지 못했습니다'
      }
      body={
        gone
          ? forbidden
            ? '다른 사람이 만든 워크플로우입니다.'
            : '삭제되었거나 주소가 잘못되었습니다.'
          : apiError?.isNetworkError
            ? '서버에 연결할 수 없습니다. 네트워크를 확인해 주세요.'
            : '잠시 후 다시 시도해 주세요.'
      }
      actions={gone ? GONE_ACTIONS : RETRY_ACTIONS}
    />
  )
}
