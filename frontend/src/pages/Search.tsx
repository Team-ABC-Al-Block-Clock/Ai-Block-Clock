import { useQuery } from '@tanstack/react-query'
import { Fragment, useState, useEffect } from 'react'

import { getMarketplaceAgents } from '@/api/agents'
import { compactCount, creditLabel, runLabel } from '@/lib/format'
import { queryKeys } from '@/lib/queryKeys'
import { paths } from '@/routes/paths'
import { OceanTheme } from '../components/theme-store'

const FULL_TITLE = '어떤 일을 맡겨볼까요?'

export default function Search() {
  const [q, setQ] = useState('')
  const [infoAt, setInfoAt] = useState<number | null>(null)

  // OceanTheme 는 프레임워크 밖의 상태 — subscribe 로 받아 리렌더를 유발한다
  const [bright, setBright] = useState(OceanTheme.isBright)
  useEffect(() => OceanTheme.subscribe(() => setBright(OceanTheme.isBright())), [])

  const cardBg = bright ? 'rgba(24,13,40,.42)' : 'rgba(255,255,255,.10)'
  const descColor = bright ? 'rgba(255,240,248,.82)' : 'rgba(255,236,246,.7)'
  const headShadow = bright ? '0 2px 14px rgba(24,8,34,.85)' : '0 1px 10px rgba(35,8,45,.6)'
  const query = q.trim()
  // 검색·필터·정렬은 서버가 한다. 화면에 페이지 이동 UI 가 없어 한 페이지에 다 담는다.
  const params = { keyword: query, size: 30 }
  const { data, isPending, isError } = useQuery({
    queryKey: queryKeys.marketplace.agents.list(params),
    queryFn: () => getMarketplaceAgents(params),
  })

  const agents = data?.content ?? []
  const cards = agents.map((a, i) => ({
    agentId: a.agentId,
    name: a.name,
    desc: a.description,
    favs: compactCount(a.favoriteCount),
    initial: a.name.trim().charAt(0).toUpperCase(),
    uses: runLabel(a.runCount),
    credit: creditLabel(a.unitPrice),
    author: a.ownerNickname,
    infoOpen: infoAt === i,
    infoBg: infoAt === i ? 'rgba(255,255,255,.34)' : 'rgba(255,255,255,.18)',
    info: (e: React.MouseEvent) => {
      e.preventDefault()
      e.stopPropagation()
      setInfoAt((v) => (v === i ? null : i))
    },
    anim:
      'rise .6s ' + (0.16 + Math.min(i, 8) * 0.04).toFixed(2) + 's cubic-bezier(.2,.8,.25,1) both',
  }))
  const countLabel = isPending ? '…' : (data?.totalElements ?? 0) + '개'
  // 불러오는 중에는 "결과 없음" 을 띄우지 않는다 — 매 검색마다 한 번씩 깜빡인다.
  const empty = !isPending && !isError && agents.length === 0

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

    const input = document.getElementById('searchQ') as HTMLInputElement | null
    const onInput = () => setQ(input!.value)
    input?.addEventListener('input', onInput)

    return () => {
      clearInterval(typing)
      timers.forEach(clearTimeout)
      input?.removeEventListener('input', onInput)
    }
  }, [])

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
          {cards.map((c, i) => (
            <Fragment key={i}>
              <a
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
                  <div
                    className="absolute inset-0 flex items-center justify-center text-[18px] font-semibold"
                    style={{ color: 'rgba(255,226,238,.72)' }}
                  >
                    {c.initial}
                  </div>
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
                      {c.favs}
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
                        <span style={{ color: 'rgba(255,236,246,.6)' }}>평균 크레딧</span>
                        <span className="font-semibold text-white">{c.credit}</span>
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
            </Fragment>
          ))}
        </div>
      </div>

      {(empty || isError) && (
        <div
          className="absolute text-center text-[15px]"
          style={{
            left: '272px',
            top: '452px',
            width: '896px',
            color: 'rgba(255,236,246,.62)',
            animation: 'fadeIn .3s ease both',
          }}
        >
          {isError ? '목록을 불러오지 못했습니다' : '검색 결과가 없습니다'}
        </div>
      )}
    </>
  )
}
