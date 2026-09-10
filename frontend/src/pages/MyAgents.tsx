import { useQuery } from '@tanstack/react-query'
import { Fragment, useState, useEffect } from 'react'

import { getMyAgents } from '@/api/agents'
import { runLabel, tokenLabel } from '@/lib/format'
import { queryKeys } from '@/lib/queryKeys'
import { paths } from '@/routes/paths'
import Sidebar from '../components/Sidebar'

/** 카드의 꺾은선은 장식이다. 통계 엔드포인트가 없어 순번으로 돌려 쓴다. */
const SPARKS = [
  'M1 27L19 19L37 23L55 10L73 14L89 1',
  'M1 24L19 26L37 12L55 16L73 5L89 1',
  'M1 20L19 25L37 11L55 22L73 8L89 2',
  'M1 25L19 14L37 18L55 9L73 13L89 3',
  'M1 22L19 20L37 24L55 12L73 9L89 2',
]

export default function MyAgents() {
  const [tab, setTab] = useState('all')
  const [q, setQ] = useState('')
  const [favs, setFavs] = useState<Record<string, boolean>>({})

  const { data, isPending, isError } = useQuery({
    queryKey: queryKeys.agents.list({ size: 30 }),
    queryFn: () => getMyAgents({ size: 30 }),
  })

  const handleTabAll = () => setTab('all')
  const handleTabFav = () => setTab('fav')
  const toggleFav = (id: string) => () =>
    setFavs((prev) => {
      const next = { ...prev }
      if (next[id]) delete next[id]
      else next[id] = true
      return next
    })
  const query = q.trim()
  // GET /agents 는 내가 등록한 것만 준다 — 전체와 "내가 제작" 이 같다.
  //
  // 즐겨찾기 API(POST·DELETE /marketplace/agents/{id}/favorites)는 생겼고 목록 응답도
  // `favorited` 를 준다. 그런데 이 화면은 아직 화면 안 상태로만 별을 그린다 —
  // 상세에서 즐겨찾기한 것이 여기서는 빈 별로 보이고, 탭 전환하면 토글이 사라진다.
  // 「즐겨찾기」 탭을 제대로 붙이려면 즐겨찾기 목록 조회 명세가 필요해 별도 작업으로 둔다.
  const agents = (data?.content ?? []).map((a, i) => ({
    agentId: a.agentId,
    name: a.name,
    desc: a.summary,
    uses: runLabel(a.executionCount),
    tokens: tokenLabel(a.tokenUsage),
    path: SPARKS[i % SPARKS.length],
    i,
  }))
  const list = agents
    .filter((d) => tab !== 'fav' || favs[d.agentId])
    .filter(
      (d) => !query || d.name.toLowerCase().includes(query.toLowerCase()) || d.desc.includes(query),
    )
  const on = 'rgba(255,255,255,.9)',
    off = 'rgba(255,255,255,.09)'
  // 커서 목록은 전체 개수를 주지 않는다(전수를 만들지 않는 것이 설계 의도).
  // 불러온 만큼만 세고, 더 있으면 '+' 를 붙인다.
  const total = `${agents.length}${data?.hasNext ? '+' : ''}`
  const allLabel = '전체 ' + total
  const favLabel = '즐겨찾기 ' + Object.keys(favs).length
  const mineLabel = '내가 제작 ' + total
  const mineTabBg = tab === 'mine' ? on : off
  const mineTabFg = tab === 'mine' ? '#3a1030' : 'rgba(255,240,247,.78)'
  const handleTabMine = () => setTab('mine')
  const allBg = tab === 'all' ? on : off
  const allFg = tab === 'all' ? '#3a1030' : 'rgba(255,240,247,.78)'
  const favTabBg = tab === 'fav' ? on : off
  const favTabFg = tab === 'fav' ? '#3a1030' : 'rgba(255,240,247,.78)'
  const rows = list.map((d, n) => ({
    ...d,
    initial: d.name.trim().charAt(0).toUpperCase(),
    gradId: 'spk' + d.i,
    gradRef: 'url(#spk' + d.i + ')',
    starColor: favs[d.agentId] ? '#e3c8a3' : 'rgba(255,255,255,.34)',
    mine: true,
    toggleFav: toggleFav(d.agentId),
    anim: 'rise .6s ' + (0.24 + n * 0.05).toFixed(2) + 's cubic-bezier(.2,.8,.25,1) both',
  }))
  const empty = !isPending && !isError && list.length === 0

  useEffect(() => {
    const input = document.getElementById('myQ') as HTMLInputElement | null
    if (input) input.addEventListener('input', () => setQ(input.value))
  }, [])

  return (
    <>
      <Sidebar active="agents" />

      <div
        className="absolute text-[30px] font-semibold tracking-[-.02em] text-white"
        style={{
          left: '442px',
          top: '158px',
          textShadow: '0 2px 20px rgba(35,8,45,.6)',
          animation: 'rise .7s .06s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        나의 에이전트
      </div>
      <div
        className="absolute text-[13px] tracking-[-.01em]"
        style={{
          left: '442px',
          top: '206px',
          width: '560px',
          lineHeight: '1.5',
          color: 'rgba(255,236,246,.7)',
          animation: 'rise .7s .1s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        내가 만든 에이전트와 저장한 에이전트를 사용하고 수정할 수 있는 공간입니다.
      </div>

      <div
        className="absolute bg-[rgba(255,255,255,.09)]"
        style={{
          left: '442px',
          top: '250px',
          width: '574px',
          height: '44px',
          borderRadius: '22px',
          animation: 'rise .7s .14s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div
          className="absolute rounded-full"
          style={{
            left: '18px',
            top: '15px',
            width: '14px',
            height: '14px',
            boxShadow: 'inset 0 0 0 2px rgba(255,255,255,.62)',
          }}
        ></div>
        <div
          className="absolute"
          style={{
            left: '29px',
            top: '27px',
            width: '8px',
            height: '2px',
            borderRadius: '1px',
            background: 'rgba(255,255,255,.62)',
            transform: 'rotate(45deg)',
          }}
        ></div>
        <input
          className="absolute border-0 bg-transparent font-[inherit] text-[13px] tracking-[-.01em] text-white outline-0"
          id="myQ"
          type="text"
          placeholder="에이전트 이름 또는 설명 검색"
          style={{ left: '46px', top: '11px', width: '508px', height: '22px' }}
        />
      </div>

      <div
        style={{
          left: '1036px',
          top: '253px',
          width: '144px',
          height: '38px',
          borderRadius: '19px',
          padding: '0 14px',
          animation: 'rise .7s .16s cubic-bezier(.2,.8,.25,1) both',
        }}
        className="absolute box-border flex cursor-pointer items-center justify-between bg-[rgba(255,255,255,.09)] hover:bg-[rgba(255,255,255,.16)]"
      >
        <div className="text-[12px]" style={{ color: 'rgba(255,240,247,.82)' }}>
          카테고리 전체
        </div>
        <div
          style={{
            width: '0',
            height: '0',
            borderLeft: '4px solid transparent',
            borderRight: '4px solid transparent',
            borderTop: '5px solid rgba(255,255,255,.6)',
          }}
        ></div>
      </div>
      <div
        style={{
          left: '1190px',
          top: '253px',
          width: '148px',
          height: '38px',
          borderRadius: '19px',
          padding: '0 14px',
          animation: 'rise .7s .18s cubic-bezier(.2,.8,.25,1) both',
        }}
        className="absolute box-border flex cursor-pointer items-center justify-between bg-[rgba(255,255,255,.09)] hover:bg-[rgba(255,255,255,.16)]"
      >
        <div className="text-[12px]" style={{ color: 'rgba(255,240,247,.82)' }}>
          최신 순
        </div>
        <div
          style={{
            width: '0',
            height: '0',
            borderLeft: '4px solid transparent',
            borderRight: '4px solid transparent',
            borderTop: '5px solid rgba(255,255,255,.6)',
          }}
        ></div>
      </div>

      <div
        className="absolute flex"
        style={{
          left: '442px',
          top: '314px',
          height: '34px',
          gap: '10px',
          animation: 'rise .7s .2s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div
          className="flex cursor-pointer items-center text-[12px] font-semibold transition-colors duration-[160ms]"
          onClick={handleTabAll}
          style={{
            height: '34px',
            padding: '0 14px',
            borderRadius: '11px',
            background: `${allBg}`,
            color: `${allFg}`,
          }}
        >
          {allLabel}
        </div>
        <div
          className="flex cursor-pointer items-center text-[12px] font-semibold transition-colors duration-[160ms]"
          onClick={handleTabFav}
          style={{
            height: '34px',
            padding: '0 14px',
            borderRadius: '11px',
            background: `${favTabBg}`,
            color: `${favTabFg}`,
          }}
        >
          {favLabel}
        </div>
        <div
          className="flex cursor-pointer items-center text-[12px] font-semibold transition-colors duration-[160ms]"
          onClick={handleTabMine}
          style={{
            height: '34px',
            padding: '0 14px',
            borderRadius: '11px',
            background: `${mineTabBg}`,
            color: `${mineTabFg}`,
          }}
        >
          {mineLabel}
        </div>
      </div>

      <div
        className="absolute box-border overflow-x-hidden"
        id="agentRows"
        style={{
          left: '430px',
          top: '358px',
          width: '920px',
          height: '578px',
          paddingRight: '26px',
          overflowY: 'auto',
          scrollbarWidth: 'thin',
          scrollbarColor: 'rgba(255,255,255,.28) transparent',
        }}
      >
        <div className="flex flex-col" style={{ width: '868px', margin: '8px 12px', gap: '20px' }}>
          {rows.map((r, i) => (
            <Fragment key={i}>
              <div
                className="bg-surface-card rowCard box-border flex flex-none items-center transition-colors duration-200 hover:bg-[rgba(255,255,255,.14)]"
                style={{
                  width: '868px',
                  height: '112px',
                  padding: '0 24px 0 26px',
                  borderRadius: '18px',
                  gap: '18px',
                  animation: `${r.anim}`,
                }}
              >
                <div
                  className="flex flex-none items-center justify-center rounded-full text-[16px] font-semibold"
                  style={{
                    width: '50px',
                    height: '50px',
                    background: 'rgba(255,220,235,.10)',
                    color: 'rgba(255,226,238,.8)',
                  }}
                >
                  {r.initial}
                </div>

                <div className="flex-1" style={{ minWidth: '0' }}>
                  <div className="flex items-center" style={{ gap: '10px' }}>
                    <div
                      className="overflow-hidden text-[16px] font-semibold tracking-[-.01em] whitespace-nowrap text-white"
                      style={{ maxWidth: '270px', textOverflow: 'ellipsis' }}
                    >
                      {r.name}
                    </div>
                    {r.mine && (
                      <>
                        <div
                          className="flex flex-none items-center text-[10px] font-bold text-white"
                          style={{
                            height: '18px',
                            padding: '0 7px',
                            borderRadius: '5px',
                            background: '#7052e6',
                            letterSpacing: '.04em',
                          }}
                        >
                          MY
                        </div>
                      </>
                    )}
                    <div
                      className="flex-none cursor-pointer text-[13px]"
                      onClick={r.toggleFav}
                      style={{ color: `${r.starColor}` }}
                    >
                      ★
                    </div>
                  </div>
                  <div
                    className="overflow-hidden text-[12px] tracking-[-.01em] whitespace-nowrap"
                    style={{
                      marginTop: '7px',
                      maxWidth: '340px',
                      textOverflow: 'ellipsis',
                      color: 'rgba(255,236,246,.6)',
                    }}
                  >
                    {r.desc}
                  </div>
                  <div
                    className="flex text-[11.5px]"
                    style={{
                      marginTop: '8px',
                      gap: '18px',
                      color: 'rgba(255,240,247,.55)',
                      fontVariantNumeric: 'tabular-nums',
                    }}
                  >
                    <div>사용 {r.uses}</div>
                    <div>토큰 {r.tokens}</div>
                  </div>
                </div>

                <div className="spark flex-none" style={{ width: '88px', height: '28px' }}>
                  <svg
                    width="90"
                    height="28"
                    viewBox="0 0 90 28"
                    fill="none"
                    style={{ overflow: 'visible' }}
                  >
                    <defs>
                      <linearGradient id={r.gradId} x1="0" y1="1" x2="1" y2="0">
                        <stop offset="0%" stopColor="#dba18b" />
                        <stop offset="100%" stopColor="#e3b7c8" />
                      </linearGradient>
                    </defs>
                    <path
                      d={r.path}
                      stroke={r.gradRef}
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeDasharray="120"
                    />
                  </svg>
                </div>

                <a
                  href={paths.agentDetail(r.agentId)}
                  style={{
                    width: '96px',
                    height: '34px',
                    marginLeft: '26px',
                    borderRadius: '17px',
                    color: 'rgba(255,255,255,.85)',
                  }}
                  className="flex flex-none items-center justify-center bg-[rgba(255,255,255,.10)] text-[12px] font-semibold tracking-[-.01em] transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.22)] hover:text-[#fff]"
                >
                  상세 보기 →
                </a>
              </div>
            </Fragment>
          ))}
        </div>
      </div>

      {(empty || isError) && (
        <div
          className="absolute text-center text-[15px]"
          style={{
            left: '442px',
            top: '400px',
            width: '868px',
            color: 'rgba(255,236,246,.6)',
            animation: 'fadeIn .3s ease both',
          }}
        >
          {isError ? '목록을 불러오지 못했습니다' : '해당하는 에이전트가 없습니다'}
        </div>
      )}
    </>
  )
}
