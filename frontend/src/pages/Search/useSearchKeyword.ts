import { useCallback, useEffect, useState, type ChangeEvent } from 'react'
import { useSearchParams } from 'react-router-dom'

import { DEBOUNCE_MS } from './config'

/**
 * 검색어의 원본은 주소다. 상태에만 두면 새로고침하면 사라지고 링크로 공유할 수 없다 —
 * 뒤로가기도 검색을 되돌리지 않고 화면을 벗어난다.
 */
export function useSearchKeyword() {
  const [searchParams, setSearchParams] = useSearchParams()
  /** 첫 진입의 ?keyword= 만 읽는다. 그 뒤로 입력값의 주인은 이 state 다.
   *  주소를 되읽어 value 로 쓰면 한글이 낱자로 떨어진다 — React 는 IME 조합 중에
   *  onChange 를 쏘지 않는데, 디바운스·쿼리 전환으로 리렌더는 계속 일어나서
   *  조합 중인 input 에 옛 값이 덮인다. 그 순간 브라우저 IME 조합이 끊긴다. */
  const [q, setQ] = useState(() => searchParams.get('keyword') ?? '')
  const [keyword, setKeyword] = useState(q)

  /** 요청도 주소도 타자가 멎은 뒤에 갱신한다. 없으면 타자 한 번에 요청 한 번이다.
   *  히스토리는 쌓지 않는다(replace) — 안 그러면 '계'·'계약'·'계약서' 를 전부 거쳐야
   *  뒤로 나갈 수 있다. mockDelay 같은 다른 쿼리는 그대로 남긴다. */
  useEffect(() => {
    const timer = setTimeout(() => {
      const next = q.trim()
      setKeyword(next)
      setSearchParams(
        (prev) => {
          const params = new URLSearchParams(prev)
          if (next) params.set('keyword', next)
          else params.delete('keyword')
          return params
        },
        { replace: true },
      )
    }, DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [q, setSearchParams])

  const handleChangeQuery = (e: ChangeEvent<HTMLInputElement>) => setQ(e.target.value)
  const clearQuery = useCallback(() => setQ(''), [])

  return { q, keyword, handleChangeQuery, clearQuery }
}
