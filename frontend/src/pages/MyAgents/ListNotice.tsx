import { useCallback, useEffect, useRef } from 'react'

import { ApiError } from '@/api/errors'

import { EMPTY, type Tab } from './config'

/** 442 + (896 - 620) / 2 = 580. translateX 로 중앙 정렬하지 않는다 — transform 이
 *  걸리면 backdrop 루트가 되어 카드가 바깥 배경을 못 가져온다. */
const NOTICE_BOX = { left: '580px', top: '430px', width: '620px' } as const

interface ListNoticeProps {
  tab: Tab
  keyword: string
  isError: boolean
  error: unknown
  onClear: () => void
  onRetry: () => void
}

/** 목록이 비었을 때 — 실패, 검색 결과 없음, 아직 아무것도 없음 셋 중 하나를 띄운다. */
export default function ListNotice({
  tab,
  keyword,
  isError,
  error,
  onClear,
  onRetry,
}: ListNoticeProps) {
  /** 마지막 렌더의 핸들러. 아래 ref 콜백은 한 번 만들어지므로 이것을 거쳐 최신 것을 부른다. */
  const handlers = useRef({ onClear, onRetry })
  useEffect(() => {
    handlers.current = { onClear, onRetry }
  })

  /** React 는 커스텀 이벤트를 on* 으로 받지 않아 직접 붙인다. effect + deps 로 붙이면
   *  빈 상태와 실패 분기 사이를 오갈 때 deps 가 안 바뀌어 '다시 시도' 가 죽는다 —
   *  노드가 붙는 시점에 매달아 분기와 무관하게 만든다. */
  const bindNotice = useCallback((node: HTMLElement | null) => {
    if (!node) return
    const handleClear = () => handlers.current.onClear()
    const handleRetry = () => handlers.current.onRetry()
    node.addEventListener('clear', handleClear)
    node.addEventListener('retry', handleRetry)
    return () => {
      node.removeEventListener('clear', handleClear)
      node.removeEventListener('retry', handleRetry)
    }
  }, [])

  const style = { position: 'absolute', ...NOTICE_BOX, animation: 'fadeIn .3s ease both' } as const

  if (isError)
    return (
      <empty-state
        ref={bindNotice}
        style={style}
        heading="목록을 불러오지 못했습니다"
        body={
          error instanceof ApiError && error.isNetworkError
            ? '서버에 연결할 수 없습니다. 네트워크를 확인해 주세요.'
            : '잠시 후 다시 시도해 주세요.'
        }
        actions={JSON.stringify([{ label: '다시 시도', event: 'retry', primary: true }])}
      />
    )

  if (keyword)
    return (
      <empty-state
        ref={bindNotice}
        style={style}
        heading={`‘${keyword}’ 에 맞는 결과가 없습니다`}
        body="찾는 이름을 다르게 적어보거나 검색어를 지워 전체를 확인해 보세요."
        actions={JSON.stringify([{ label: '검색어 지우기', event: 'clear', primary: true }])}
      />
    )

  return (
    <empty-state
      ref={bindNotice}
      style={style}
      heading={EMPTY[tab].heading}
      body={EMPTY[tab].body}
      actions={EMPTY[tab].actions}
    />
  )
}
