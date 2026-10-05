import { useEffect, useRef } from 'react'

import { ApiError } from '@/api/errors'
import { paths } from '@/routes/paths'

/** 안내 카드. 그리드 안쪽(272~1168, 896px)에서 가운데 — 272 + (896 - 620) / 2 = 410.
 *  translateX 로 중앙 정렬하지 않는다: transform 이 걸리면 backdrop 루트가 되어
 *  카드의 backdrop-filter 가 바깥 배경을 못 가져온다. */
const NOTICE_BOX = { left: '410px', top: '360px', width: '620px' } as const

/** 빈 상태는 실패가 아니라 다음 행동을 고르는 자리다 — 행동을 최소 하나 준다.
 *  <empty-state> 가 JSON 으로 받는다(`components/elements/types.ts` 의 EmptyAction). */
const NOT_FOUND_ACTIONS = JSON.stringify([
  { label: '에이전트 둘러보기', href: paths.search, primary: true },
  { label: '홈으로', href: paths.home },
])

const ERROR_ACTIONS = JSON.stringify([
  { label: '다시 시도', event: 'retry', primary: true },
  { label: '목록으로', href: paths.search },
])

type DetailStatusProps =
  | { kind: 'notFound' }
  | { kind: 'loading' }
  | { kind: 'error'; error: unknown; onRetry: () => void }

/** 상세를 그릴 수 없을 때 — 없는 에이전트, 불러오기 실패, 불러오는 중. */
export default function DetailStatus(props: DetailStatusProps) {
  const ref = useRef<HTMLElement>(null)
  const onRetry = props.kind === 'error' ? props.onRetry : null

  // <empty-state> 의 '다시 시도'. React 는 커스텀 이벤트를 on* 으로 받지 않아 직접 붙인다.
  // emit 이 호스트에서 bubbles 로 올리므로 호스트에서 받으면 된다.
  useEffect(() => {
    const node = ref.current
    if (!node || !onRetry) return
    const handleRetry = () => onRetry()
    node.addEventListener('retry', handleRetry)
    return () => node.removeEventListener('retry', handleRetry)
  }, [onRetry])

  const style = { position: 'absolute', ...NOTICE_BOX, animation: 'fadeIn .3s ease both' } as const

  if (props.kind === 'notFound')
    return (
      <empty-state
        style={style}
        heading="에이전트를 찾을 수 없습니다"
        body="삭제되었거나 주소가 잘못된 에이전트입니다."
        actions={NOT_FOUND_ACTIONS}
      />
    )

  if (props.kind === 'error')
    return (
      <empty-state
        ref={ref}
        style={style}
        heading="에이전트를 불러오지 못했습니다"
        body={
          props.error instanceof ApiError && props.error.isNetworkError
            ? '서버에 연결할 수 없습니다. 네트워크를 확인해 주세요.'
            : '잠시 후 다시 시도해 주세요.'
        }
        actions={ERROR_ACTIONS}
      />
    )

  // 불러오는 중에 '없음' 을 띄우지 않는다 — 요청마다 한 번씩 깜빡인다.
  return (
    <div
      className="absolute text-center text-[15px]"
      style={{
        ...NOTICE_BOX,
        top: '420px',
        color: 'rgba(255,236,246,.62)',
        animation: 'fadeIn .3s ease both',
      }}
    >
      불러오는 중…
    </div>
  )
}
