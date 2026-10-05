import { useEffect, useRef } from 'react'

import { EMPTY_ACTIONS, EMPTY_BODY, EMPTY_BOX, NOTICE_BOX } from './config'

interface SearchNoticeProps {
  isError: boolean
  keyword: string
  onClear: () => void
}

/** 결과가 비었을 때 — 실패 안내 또는 빈 상태. */
export default function SearchNotice({ isError, keyword, onClear }: SearchNoticeProps) {
  if (isError)
    return (
      <div
        className="absolute text-center text-[15px]"
        style={{
          ...NOTICE_BOX,
          color: 'rgba(255,236,246,.62)',
          animation: 'fadeIn .3s 1.6s ease both',
        }}
      >
        목록을 불러오지 못했습니다
      </div>
    )

  return <EmptyNotice keyword={keyword} onClear={onClear} />
}

function EmptyNotice({ keyword, onClear }: { keyword: string; onClear: () => void }) {
  // <empty-state> 의 '검색어 지우기'. React 는 커스텀 이벤트를 on* 으로 받지 않아
  // 직접 붙인다. emit 이 호스트에서 bubbles 로 올리므로 호스트에서 받으면 된다.
  const emptyRef = useRef<HTMLElement>(null)
  useEffect(() => {
    const node = emptyRef.current
    if (!node) return
    node.addEventListener('clear', onClear)
    return () => node.removeEventListener('clear', onClear)
  }, [onClear])

  return (
    <empty-state
      ref={emptyRef}
      style={{ position: 'absolute', ...EMPTY_BOX, animation: 'fadeIn .3s 1.6s ease both' }}
      heading={
        keyword ? `'${keyword}' 에 맞는 에이전트가 없습니다` : '아직 등록된 에이전트가 없습니다'
      }
      body={EMPTY_BODY}
      actions={EMPTY_ACTIONS}
    />
  )
}
