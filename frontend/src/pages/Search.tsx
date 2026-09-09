import { keepPreviousData, useInfiniteQuery } from '@tanstack/react-query'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { getMarketplaceAgents } from '@/api/agents'
import { priceLabel, ratingLabel, runLabel } from '@/lib/format'
import { queryKeys } from '@/lib/queryKeys'
import { paths } from '@/routes/paths'
import { OceanTheme } from '../components/theme-store'

const FULL_TITLE = '어떤 일을 맡겨볼까요?'
/** 한 번에 불러오는 개수. 명세 기본값이다. */
const PAGE_SIZE = 9
/** 입력이 멈춘 뒤에만 검색한다. 없으면 타자 한 번에 요청 한 번이다. */
const DEBOUNCE_MS = 300

/** 빈 상태는 실패가 아니라 다음 행동을 고르는 자리다 — 행동을 최소 하나 준다.
 *  <empty-state> 가 JSON 으로 받는다(`components/types.ts` 의 EmptyAction). */
const EMPTY_ACTIONS = JSON.stringify([
  { label: '직접 만들어보기', href: paths.home, primary: true },
  { label: '검색어 지우기', event: 'clear' },
])

const EMPTY_BODY =
  '찾는 일을 다르게 적어보거나, 프롬프트로 에이전트를 직접 만들어 보세요.\n ' +
  '여러 에이전트를 이어 붙이는 일도 프롬프트 한 줄로 됩니다.'

/** 실패 안내는 그리드 안쪽 폭(272~1168)에 맞춘다. */
const NOTICE_BOX = { left: '272px', top: '470px', width: '896px' } as const

/** 빈 상태 카드. 그리드 안쪽(896px)에서 가운데 — 272 + (896 - 620) / 2 = 410.
 *  translateX 로 중앙 정렬하지 않는다: transform 이 걸리면 backdrop 루트가 되어
 *  카드의 backdrop-filter 가 바깥 배경을 못 가져온다(컨벤션 10절). */
const EMPTY_BOX = { left: '410px', top: '486px', width: '620px' } as const

export default function Search() {
  /**
   * 검색어의 원본은 주소다. 컴포넌트 상태에만 두면 새로고침하면 사라지고 링크로
   * 공유할 수도 없다 — 뒤로가기도 검색을 되돌리지 않고 화면을 벗어난다.
   * mock 시나리오 스위치(`mocks/scenario.ts`)가 쓰는 것과 같은 성격의 값이다.
   */
  const [searchParams, setSearchParams] = useSearchParams()
  const q = searchParams.get('keyword') ?? ''
  const [keyword, setKeyword] = useState(q)

  /** 주소는 타자마다 갱신하되 히스토리는 쌓지 않는다(replace) — 안 그러면
   *  '계'·'계약'·'계약서' 를 전부 거쳐야 뒤로 나갈 수 있다.
   *  mockDelay 같은 다른 쿼리는 그대로 남긴다. */
  const setQ = useCallback(
    (next: string) => {
      setSearchParams(
        (prev) => {
          const params = new URLSearchParams(prev)
          if (next) params.set('keyword', next)
          else params.delete('keyword')
          return params
        },
        { replace: true },
      )
    },
    [setSearchParams],
  )
  // 카드 index 로 잡으면 무한 스크롤로 목록이 늘어날 때 엉뚱한 카드가 열린다.
  const [infoAt, setInfoAt] = useState<string | null>(null)

  // OceanTheme 는 프레임워크 밖의 상태 — subscribe 로 받아 리렌더를 유발한다
  const [bright, setBright] = useState(OceanTheme.isBright)
  useEffect(() => OceanTheme.subscribe(() => setBright(OceanTheme.isBright())), [])

  const cardBg = bright ? 'rgba(24,13,40,.42)' : 'rgba(255,255,255,.10)'
  const descColor = bright ? 'rgba(255,240,248,.82)' : 'rgba(255,236,246,.7)'
  const headShadow = bright ? '0 2px 14px rgba(24,8,34,.85)' : '0 1px 10px rgba(35,8,45,.6)'

  // 주소는 즉시 반영하고 요청만 늦춘다. 없으면 타자 한 번에 요청 한 번이다.
  useEffect(() => {
    const timer = setTimeout(() => setKeyword(q.trim()), DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [q])

  // 검색은 서버가 한다. 목록은 커서로 이어 받는다 — 명세가 전체 개수를 주지 않는다.
  const {
    data,
    isPending,
    isPlaceholderData,
    isError,
    hasNextPage,
    isFetchingNextPage,
    isFetchNextPageError,
    fetchNextPage,
  } = useInfiniteQuery({
    queryKey: queryKeys.marketplace.agents.list({ keyword, size: PAGE_SIZE }),
    queryFn: ({ pageParam }) =>
      getMarketplaceAgents({ keyword: keyword || undefined, cursor: pageParam, size: PAGE_SIZE }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    // 검색어가 바뀌는 동안 그리드가 비었다가 차오르지 않게 이전 결과를 남긴다.
    placeholderData: keepPreviousData,
  })

  const agents = data?.pages.flatMap((page) => page.content) ?? []

  // 무한 스크롤. 그리드가 #gridScroll 안에서 스크롤되므로 그것을 root 로 삼는다 —
  // 뷰포트를 root 로 두면 컨테이너를 끝까지 내려도 sentinel 이 교차하지 않는다.
  const sentinelRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const node = sentinelRef.current
    // 실패한 커서로는 다시 시도하지 않는다 — sentinel 이 교차할 때마다 같은 요청이 반복된다.
    if (!node || !hasNextPage || isFetchNextPageError) return
    const observer = new IntersectionObserver(
      ([entry]) => {
        // cancelRefetch 기본값이 true 라 진행 중이면 취소하고 같은 커서로 다시 보낸다.
        if (entry.isIntersecting && !isFetchingNextPage) void fetchNextPage()
      },
      { root: document.getElementById('gridScroll'), rootMargin: '200px' },
    )
    observer.observe(node)
    return () => observer.disconnect()
    // isFetchingNextPage 를 deps 에 두는 것은 낭비가 아니다. 이 값이 바뀔 때 observer 가
    // 다시 만들어지고, observe() 가 이미 교차 중인 sentinel 에 콜백을 다시 발사해 다음
    // 장을 잇는다. 카드 9장이 그리드를 거의 채워(스크롤 여유 41px) sentinel 이 계속
    // 교차 상태로 남기 때문에, 교차 '변화' 만 기다리면 이어지지 않는다.
  }, [hasNextPage, isFetchNextPageError, isFetchingNextPage, fetchNextPage])

  const cards = agents.map((a, i) => ({
    agentId: a.agentId,
    name: a.name,
    desc: a.summary,
    rating: ratingLabel(a.averageRating, a.reviewCount),
    iconUrl: a.iconUrl,
    initial: a.name.trim().charAt(0).toUpperCase(),
    uses: runLabel(a.executionCount),
    price: priceLabel(a.price),
    author: a.owner.nickname,
    infoOpen: infoAt === a.agentId,
    infoBg: infoAt === a.agentId ? 'rgba(255,255,255,.34)' : 'rgba(255,255,255,.18)',
    info: (e: React.MouseEvent) => {
      e.preventDefault()
      e.stopPropagation()
      setInfoAt((v) => (v === a.agentId ? null : a.agentId))
    },
    // 새로 불러온 페이지 안에서만 시차를 준다 — 누적 index 를 쓰면 전부 같은 지연이 된다.
    anim: `rise .6s ${(0.16 + (i % PAGE_SIZE) * 0.04).toFixed(2)}s cubic-bezier(.2,.8,.25,1) both`,
  }))

  // 이전 검색어의 결과를 보여주는 동안(placeholder)은 아직 "이번 검색 결과" 가 아니다.
  // hasNextPage 는 새 쿼리 키의 데이터로 계산되므로 그 사이 '+' 가 사라진다.
  const isSettled = !isPending && !isPlaceholderData
  // 전체 개수는 명세에 없다(전수를 만들지 않는 것이 설계 의도). 불러온 만큼만 센다.
  const countLabel = isSettled ? `${agents.length}개${hasNextPage ? '+' : ''}` : '…'
  // 불러오는 중에는 "결과 없음" 을 띄우지 않는다 — 매 검색마다 한 번씩 깜빡인다.
  // 카드가 한 장이라도 있으면 띄우지 않는다 — absolute 라 카드 위에 겹쳐 그려진다.
  const notice = isSettled && agents.length === 0
  const showEmpty = notice && !isError
  const handleChangeQuery = (e: React.ChangeEvent<HTMLInputElement>) => setQ(e.target.value)

  // <empty-state> 의 '검색어 지우기'. React 는 커스텀 이벤트를 on* 으로 받지 않아
  // 직접 붙인다. emit 이 호스트에서 bubbles 로 올리므로 호스트에서 받으면 된다.
  const emptyRef = useRef<HTMLElement>(null)
  useEffect(() => {
    const node = emptyRef.current
    if (!node) return
    const onClear = () => setQ('')
    node.addEventListener('clear', onClear)
    return () => node.removeEventListener('clear', onClear)
  }, [showEmpty, setQ])

  useEffect(() => {
    // 타자 치듯 타이틀 입력. 예약한 타이머를 모아 두고 정리 시 한 번에 끊는다
    const timers: ReturnType<typeof setTimeout>[] = []
    let typing: ReturnType<typeof setInterval> | undefined
    const startTyping = (tries: number) => {
      if (!document.getElementById('srchTypeText')) {
        if (tries < 60) timers.push(setTimeout(() => startTyping(tries + 1), 100))
        return
      }
      const t0 = performance.now()
      typing = setInterval(() => {
        const node = document.getElementById('srchTypeText')
        if (!node) return
        const i = Math.min(FULL_TITLE.length, Math.floor((performance.now() - t0) / 55))
        node.textContent = FULL_TITLE.slice(0, i)
        if (i < FULL_TITLE.length) return
        clearInterval(typing)
        timers.push(
          setTimeout(() => {
            const c = document.getElementById('srchCaret')
            if (c) c.style.display = 'none'
          }, 900),
        )
      }, 55)
    }
    timers.push(setTimeout(() => startTyping(0), 780))

    return () => {
      clearInterval(typing)
      timers.forEach(clearTimeout)
    }
  }, [])

  return (
    <>
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            'radial-gradient(130% 90% at 50% 44%, rgba(0,0,0,0) 46%, rgba(26,12,40,.22) 100%)',
        }}
      ></div>
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            'linear-gradient(180deg, rgba(22,10,36,.30) 0%, rgba(22,10,36,.16) 22%, rgba(22,10,36,.10) 55%, rgba(22,10,36,.20) 100%)',
        }}
      ></div>

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
        <span id="srchTypeText"></span>
        <span
          className="inline-block"
          id="srchCaret"
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
        <div
          className="absolute rounded-full"
          style={{
            left: '28px',
            top: '24px',
            width: '20px',
            height: '20px',
            boxShadow: 'inset 0 0 0 2px rgba(255,255,255,.85)',
          }}
        ></div>
        <div
          className="absolute"
          style={{
            left: '44px',
            top: '41px',
            width: '9px',
            height: '2px',
            borderRadius: '1px',
            background: 'rgba(255,255,255,.85)',
            transform: 'rotate(45deg)',
          }}
        ></div>
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
          {cards.map((c) => (
            <a
              key={c.agentId}
              href={paths.agentDetail(c.agentId)}
              style={{
                width: '280px',
                height: '176px',
                borderRadius: '14px',
                background: `${cardBg}`,
                boxShadow: '0 10px 34px rgba(10,4,20,.14)',
                transition: 'transform .2s,box-shadow .2s,background .2s',
                animation: `${c.anim}`,
              }}
              className="relative overflow-hidden hover:-translate-y-[4px] hover:shadow-[0_16px_46px_rgba(10,4,20,.26)] hover:brightness-[1.14]"
            >
              <div
                className="absolute overflow-hidden bg-[rgba(255,255,255,.10)]"
                style={{
                  left: '24px',
                  top: '24px',
                  width: '56px',
                  height: '56px',
                  borderRadius: '12px',
                }}
              >
                {c.iconUrl ? (
                  <img className="h-full w-full object-cover" src={c.iconUrl} alt="" />
                ) : (
                  <div
                    className="absolute inset-0 flex items-center justify-center text-[18px] font-semibold"
                    style={{ color: 'rgba(255,226,238,.72)' }}
                  >
                    {c.initial}
                  </div>
                )}
              </div>
              <div
                className="absolute flex items-center"
                style={{ right: '22px', top: '26px', height: '24px', gap: '8px' }}
              >
                <div
                  className="flex items-center"
                  style={{
                    height: '24px',
                    padding: '0 11px',
                    borderRadius: '12px',
                    background: 'rgba(255,255,255,.13)',
                    gap: '5px',
                  }}
                >
                  <div className="text-[10px]" style={{ color: '#e3c8a3' }}>
                    ★
                  </div>
                  <div className="text-[11.5px] font-semibold tracking-[-.01em] text-white">
                    {c.rating}
                  </div>
                </div>
                <div
                  onClick={c.info}
                  title="에이전트 정보"
                  style={{
                    width: '24px',
                    height: '24px',
                    borderRadius: '12px',
                    background: `${c.infoBg}`,
                    letterSpacing: '0',
                  }}
                  className="flex flex-none cursor-pointer items-center justify-center text-[12px] font-bold text-white transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.34)]"
                >
                  i
                </div>
              </div>
              {c.infoOpen && (
                <>
                  <div
                    className="absolute top-0 left-0 box-border flex cursor-pointer flex-col"
                    onClick={c.info}
                    style={{
                      width: '280px',
                      height: '176px',
                      padding: '22px 24px',
                      borderRadius: '14px',
                      background: 'rgba(20,11,36,.93)',
                      backdropFilter: 'blur(22px) saturate(120%)',
                      WebkitBackdropFilter: 'blur(22px) saturate(120%)',
                      gap: '9px',
                      animation: 'fadeIn .18s ease both',
                      zIndex: '3',
                    }}
                  >
                    <div className="text-[13.5px] font-semibold tracking-[-.01em] text-white">
                      {c.name}
                    </div>
                    <div className="flex justify-between text-[12px]">
                      <span style={{ color: 'rgba(255,236,246,.6)' }}>사용 횟수</span>
                      <span className="font-semibold text-white">{c.uses}</span>
                    </div>
                    <div className="flex justify-between text-[12px]">
                      <span style={{ color: 'rgba(255,236,246,.6)' }}>실행 단가</span>
                      <span className="font-semibold text-white">{c.price}</span>
                    </div>
                    <div className="flex justify-between text-[12px]">
                      <span style={{ color: 'rgba(255,236,246,.6)' }}>만든 사람</span>
                      <span className="font-semibold text-white">{c.author}</span>
                    </div>
                    <div
                      className="text-center text-[11px] tracking-[-.01em]"
                      style={{ marginTop: 'auto', color: 'rgba(255,236,246,.5)' }}
                    >
                      다시 누르면 카드로 돌아갑니다
                    </div>
                  </div>
                </>
              )}
              <div
                className="absolute text-[18px] font-semibold tracking-[-.01em] text-white"
                style={{ left: '24px', top: '94px' }}
              >
                {c.name}
              </div>
              <div
                className="absolute text-[13px] tracking-[-.01em]"
                style={{
                  left: '24px',
                  top: '124px',
                  width: '232px',
                  lineHeight: '1.4',
                  color: `${descColor}`,
                }}
              >
                {c.desc}
              </div>
            </a>
          ))}
          {/* 이 칸이 보이면 다음 페이지를 불러온다. 마지막 페이지면 걸지 않는다. */}
          <div ref={sentinelRef} style={{ width: '100%', height: '1px' }} />
        </div>
      </div>

      {/* 그리드(1.6s)와 같이 올라와야 한다. 지연이 없으면 "추천 에이전트"·개수가
          아직 안 뜬 화면에 안내만 혼자 떠 있는다. */}
      {notice &&
        (isError ? (
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
        ) : (
          <empty-state
            ref={emptyRef}
            style={{ position: 'absolute', ...EMPTY_BOX, animation: 'fadeIn .3s 1.6s ease both' }}
            heading={
              keyword
                ? `'${keyword}' 에 맞는 에이전트가 없습니다`
                : '아직 등록된 에이전트가 없습니다'
            }
            body={EMPTY_BODY}
            actions={EMPTY_ACTIONS}
          />
        ))}
    </>
  )
}
