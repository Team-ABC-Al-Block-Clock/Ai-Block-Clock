import { useState } from 'react'

import SearchIcon from '@/components/SearchIcon'
import { useTypewriter } from '@/hooks/useTypewriter'

import AgentCard from './AgentCard'
import { FULL_TITLE } from './config'
import NextPageRetry from './NextPageRetry'
import SearchNotice from './SearchNotice'
import { useBrightTheme } from './useBrightTheme'
import { useMarketplaceSearch } from './useMarketplaceSearch'
import { useSearchKeyword } from './useSearchKeyword'

/**
 * 마켓 검색. 검색어·주소 동기화는 `useSearchKeyword`, 결과 목록은 `useMarketplaceSearch` 가 든다.
 */
export default function Search() {
  const { textRef: titleRef, caretRef } = useTypewriter(FULL_TITLE, 780)
  const { q, keyword, handleChangeQuery, clearQuery } = useSearchKeyword()
  const {
    agents,
    sentinelRef,
    isError,
    isFetchingNextPage,
    isFetchNextPageError,
    fetchNextPage,
    countLabel,
    notice,
  } = useMarketplaceSearch(keyword)
  // 카드 index 로 잡으면 무한 스크롤로 목록이 늘어날 때 엉뚱한 카드가 열린다.
  const [infoAt, setInfoAt] = useState<string | null>(null)
  const bright = useBrightTheme()
  const headShadow = bright ? '0 2px 14px rgba(24,8,34,.85)' : '0 1px 10px rgba(35,8,45,.6)'
  const toggleInfo = (agentId: string) => setInfoAt((v) => (v === agentId ? null : agentId))

  return (
    <>
      <div
        className="pointer-events-none absolute"
        style={{
          left: '360px',
          top: '110px',
          width: '720px',
          height: '210px',
          background:
            'radial-gradient(50% 58% at 50% 46%, rgba(38,14,48,.26) 0%, rgba(38,14,48,.13) 55%, rgba(38,14,48,0) 80%)',
          animation: 'srchCards .8s .7s ease both',
        }}
      ></div>
      <div
        className="absolute text-center text-[40px] font-bold tracking-[-.02em] text-white"
        style={{
          left: '220px',
          top: '148px',
          width: '1000px',
          height: '56px',
          lineHeight: '1.4',
          textShadow: '0 1px 10px rgba(35,8,45,.45)',
        }}
      >
        <span ref={titleRef}></span>
        <span
          className="inline-block"
          ref={caretRef}
          style={{
            width: '3px',
            height: '34px',
            marginLeft: '6px',
            verticalAlign: 'baseline',
            borderRadius: '2px',
            background: 'rgba(255,255,255,.85)',
            animation: 'caretBlink .9s steps(1) infinite',
          }}
        ></span>
      </div>

      <div
        className="absolute"
        style={{
          left: '360px',
          top: '240px',
          width: '720px',
          height: '68px',
          borderRadius: '34px',
          background: 'rgba(28,10,38,.32)',
          backdropFilter: 'blur(24px) saturate(130%)',
          WebkitBackdropFilter: 'blur(24px) saturate(130%)',
          boxShadow: '0 20px 54px rgba(30,8,40,.34)',
          animation: 'srchUp .7s cubic-bezier(.22,.8,.24,1) both',
        }}
      >
        <SearchIcon left={28} top={24} size={20} color="rgba(255,255,255,.85)" handleLength={9} />
        <input
          className="absolute border-0 bg-transparent font-[inherit] text-[16px] tracking-[-.01em] text-white outline-0"
          id="searchQ"
          type="text"
          placeholder="에이전트 검색"
          value={q}
          onChange={handleChangeQuery}
          style={{ left: '64px', top: '16px', width: '620px', height: '36px' }}
        />
      </div>

      <div
        className="absolute text-[16px] font-semibold tracking-[-.01em] text-white"
        style={{
          left: '272px',
          top: '352px',
          textShadow: `${headShadow}`,
          animation: 'srchCards .8s 1.55s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        추천 에이전트
      </div>
      <div
        className="absolute text-right text-[14px]"
        style={{
          left: '1046px',
          top: '354px',
          width: '122px',
          color: 'rgba(255,236,246,.7)',
          animation: 'srchCards .8s 1.55s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        {countLabel}
      </div>

      <div
        className="absolute overflow-x-hidden"
        id="gridScroll"
        style={{
          left: '232px',
          top: '390px',
          width: '1000px',
          height: '626px',
          overflowY: 'auto',
          animation: 'srchCards .9s 1.6s cubic-bezier(.2,.8,.25,1) both',
          scrollbarWidth: 'thin',
          scrollbarColor: 'rgba(255,255,255,.28) transparent',
        }}
      >
        <div
          className="flex flex-wrap"
          style={{
            width: '896px',
            margin: '14px 40px 40px',
            gap: '28px',
            alignContent: 'flex-start',
          }}
        >
          {agents.map((a, i) => (
            <AgentCard
              key={a.agentId}
              agent={a}
              index={i}
              bright={bright}
              infoOpen={infoAt === a.agentId}
              onToggleInfo={toggleInfo}
            />
          ))}
          {/* 이 칸이 보이면 다음 페이지를 불러온다. 마지막 페이지면 걸지 않는다. */}
          <div ref={sentinelRef} style={{ width: '100%', height: '1px' }} />
          {isFetchNextPageError && (
            <NextPageRetry isFetching={isFetchingNextPage} onRetry={() => void fetchNextPage()} />
          )}
        </div>
      </div>

      {/* 그리드(1.6s)와 같이 올라와야 한다. 지연이 없으면 "추천 에이전트"·개수가
          아직 안 뜬 화면에 안내만 혼자 떠 있는다. */}
      {notice && <SearchNotice isError={isError} keyword={keyword} onClear={clearQuery} />}
    </>
  )
}
