import { Fragment, useState, useEffect } from 'react'
import { OceanTheme } from '../components/theme-store'

export default function Search() {
  const [q, setQ] = useState('')
  const [infoAt, setInfoAt] = useState(null)

  let _themeSync: any
  let _type: any

  const bright = OceanTheme.isBright()
  const cardBg = bright ? 'rgba(24,13,40,.42)' : 'rgba(255,255,255,.10)'
  const descColor = bright ? 'rgba(255,240,248,.82)' : 'rgba(255,236,246,.7)'
  const headShadow = bright ? '0 2px 14px rgba(24,8,34,.85)' : '0 1px 10px rgba(35,8,45,.6)'
  const fullTitle = '어떤 일을 맡겨볼까요?'
  const all = [
    { name: '문서 정리', desc: '폴더의 파일을 주제별로 분류', favs: '2.4k' },
    { name: '일정 요약', desc: '캘린더를 하루 단위로 정리', favs: '1.8k' },
    { name: '메일 초안', desc: '받은 메일에 답장 초안 작성', favs: '3.1k' },
    { name: '코드 리뷰', desc: '변경된 코드의 문제를 지적', favs: '972' },
    { name: '회의록 작성', desc: '녹취 텍스트를 항목별로 정리', favs: '1.5k' },
    { name: '데이터 추출', desc: '표에서 필요한 열만 뽑아냄', favs: '640' },
    { name: '번역 정리', desc: '문서를 번역하고 용어 통일', favs: '1.2k' },
    { name: '이미지 태깅', desc: '이미지에 설명 태그를 붙임', favs: '418' },
    { name: '링크 수집', desc: '북마크를 주제별로 묶음', favs: '806' },
    { name: '영수증 정리', desc: '영수증 사진에서 금액을 추출', favs: '1.1k' },
    { name: 'CS 응답', desc: '문의에 맞는 답변을 제안', favs: '2.7k' },
    { name: '리서치 수집', desc: '주제별 자료를 모아 출처 정리', favs: '1.9k' },
    { name: '리포트 작성', desc: '데이터를 문장으로 풀어 씀', favs: '1.4k' },
    { name: '중복 파일 정리', desc: '같은 파일을 찾아 하나만 남김', favs: '523' },
    { name: '계약서 검토', desc: '조항의 위험 요소를 표시', favs: '888' },
    { name: '자막 생성', desc: '영상 음성을 자막으로 변환', favs: '1.6k' },
    { name: '표 정규화', desc: '흩어진 표를 하나 형식으로', favs: '347' },
    { name: '뉴스 브리핑', desc: '오늘 기사를 세 줄로 요약', favs: '3.4k' },
    { name: '이력서 검토', desc: '직무에 맞게 문장을 다듬음', favs: '1.3k' },
    { name: '가계부 분류', desc: '지출 내역을 항목별로 나눔', favs: '764' },
    { name: '슬랙 요약', desc: '읽지 않은 대화를 정리', favs: '2.1k' },
    { name: '테스트 생성', desc: '함수에 맞는 테스트를 작성', favs: '612' },
    { name: '이미지 정리', desc: '사진을 날짜·장소로 묶음', favs: '939' },
    { name: '용어집 관리', desc: '문서에서 용어를 모아 정의', favs: '281' },
  ]
  const query = q.trim()
  const list = query
    ? all.filter((c: any) => c.name.includes(query) || c.desc.includes(query))
    : all
  const cards = list.map((c: any, i: any) => ({
    ...c,
    initial: c.name.trim().charAt(0).toUpperCase(),
    uses: c.uses || (900 + i * 137).toLocaleString() + '회',
    credit: 40 + (i % 5) * 25 + ' Credit',
    author: ['김선미', '이도현', '박서준'][i % 3],
    infoOpen: infoAt === i,
    infoBg: infoAt === i ? 'rgba(255,255,255,.34)' : 'rgba(255,255,255,.18)',
    info: (e: any) => {
      e.preventDefault()
      e.stopPropagation()
      setInfoAt((v) => (v === i ? null : i))
    },
    anim:
      'rise .6s ' + (0.16 + Math.min(i, 8) * 0.04).toFixed(2) + 's cubic-bezier(.2,.8,.25,1) both',
  }))
  const countLabel = list.length + '개'
  const empty = list.length === 0

  useEffect(() => {
    window.addEventListener('oceantheme', _themeSync)
    // 타자 치듯 타이틀 입력
    let i = 0
    const startTyping = (tries: any) => {
      const el = (document.getElementById as (id: string) => any)('srchTypeText')
      if (!el) {
        if ((tries || 0) < 60) setTimeout(() => startTyping((tries || 0) + 1), 100)
        return
      }
      const t0 = performance.now()
      const _type = setInterval(() => {
        const node = (document.getElementById as (id: string) => any)('srchTypeText')
        if (!node) return
        i = Math.min(fullTitle.length, Math.floor((performance.now() - t0) / 55))
        node.textContent = fullTitle.slice(0, i)
        if (i >= fullTitle.length) {
          clearInterval(_type)
          setTimeout(() => {
            const c = (document.getElementById as (id: string) => any)('srchCaret')
            if (c) c.style.display = 'none'
          }, 900)
        }
      }, 55)
    }
    setTimeout(() => startTyping(0), 780)
    const input = (document.getElementById as (id: string) => any)('searchQ')
    if (input) input.addEventListener('input', () => setQ(input.value))
    return () => {
      window.removeEventListener('oceantheme', _themeSync)
      clearInterval(_type)
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
                href="/agent"
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

      {empty && (
        <>
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
            검색 결과가 없습니다
          </div>
        </>
      )}
    </>
  )
}
