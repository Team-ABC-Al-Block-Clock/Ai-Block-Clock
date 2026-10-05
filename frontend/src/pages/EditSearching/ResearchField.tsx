import { useState } from 'react'

import { RECOMMEND_LIMITS } from '@/api/agents'
import SearchIcon from '@/components/SearchIcon'
import { PANEL_CONTENT_WIDTH, PANEL_INSET } from '@/components/SidePanel'

interface ResearchFieldProps {
  query: string
  onSearch: (next: string) => void
}

/** 패널 맨 위의 다시 찾기 칸. Enter 로 바뀐 검색어만 넘긴다. */
export default function ResearchField({ query, onSearch }: ResearchFieldProps) {
  // 처음부터 채워 두지 않는다 — 다시 찾으려면 먼저 지워야 해서 손이 하나 더 간다.
  const [draft, setDraft] = useState('')

  const handleKeyDown = (ev: React.KeyboardEvent<HTMLInputElement>) => {
    if (ev.key !== 'Enter') return
    const next = draft.trim()
    if (!next || next === query) return
    onSearch(next)
  }

  return (
    <div
      className="absolute bg-[rgba(255,255,255,.10)]"
      style={{
        left: `${PANEL_INSET}px`,
        top: `${PANEL_INSET}px`,
        width: `${PANEL_CONTENT_WIDTH}px`,
        height: '40px',
        borderRadius: '10px',
      }}
    >
      <SearchIcon left={14} top={13} color="rgba(255,255,255,.7)" />
      <input
        className="absolute border-0 bg-transparent font-[inherit] text-[14px] text-white outline-0"
        id="searchSide"
        type="text"
        value={draft}
        maxLength={RECOMMEND_LIMITS.query.max}
        placeholder="하고 싶은 일을 다시 알려주세요"
        onChange={(ev) => setDraft(ev.target.value)}
        onKeyDown={handleKeyDown}
        style={{ left: '38px', top: '9px', width: '310px', height: '22px' }}
      />
    </div>
  )
}
