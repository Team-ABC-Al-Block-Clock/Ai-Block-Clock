import { Fragment, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { paths } from '@/routes/paths'
import SidePanel from '@/components/SidePanel'
import SearchIcon from '@/components/SearchIcon'

export default function EditSearching() {
  const navigate = useNavigate()

  const names = [
    '문서 정리',
    '일정 요약',
    '메일 초안',
    '코드 리뷰',
    '회의록 요약',
    '데이터 정제',
    '번역 도우미',
    'CS 응답',
    '리서치 수집',
    '리포트 작성',
  ]
  const results = names.map((name, i) => ({ name, i, initial: name.trim().charAt(0) }))

  useEffect(() => {
    const _go = setTimeout(() => {
      navigate(paths.edit.nodes.withoutId)
    }, 4200)
    return () => {
      clearTimeout(_go)
    }
  }, [navigate])

  return (
    <>
      <div
        className="absolute top-0 left-0"
        style={{
          width: '980px',
          height: '1024px',
          animation: 'paneInDeep 1.15s cubic-bezier(.18,.86,.24,1) both',
          // 줌의 기준점을 내용 한가운데에 둔다 — 어긋나면 들어오는 동안 원형이 옆으로 쓸린다
          transformOrigin: '50% 46%',
        }}
      >
        <div className="pointer-events-none absolute inset-0 overflow-hidden">
          <div
            className="absolute left-0"
            style={{
              top: '-20%',
              width: '38%',
              height: '150%',
              background:
                'linear-gradient(90deg,rgba(255,255,255,0),rgba(255,236,246,.16),rgba(255,255,255,0))',
              animation: 'streak 1.7s .25s cubic-bezier(.3,.7,.2,1) both',
            }}
          ></div>
        </div>

        <div
          className="pointer-events-none absolute"
          style={{ left: '350px', top: '392px', width: '280px', height: '192px' }}
        >
          <div
            className="absolute rounded-full"
            style={{
              left: '104px',
              top: '60px',
              width: '72px',
              height: '72px',
              background:
                'radial-gradient(circle at 50% 45%, rgba(255,225,240,.34), rgba(255,190,225,.06) 62%, transparent 70%)',
              animation: 'glow 3.2s ease-in-out infinite',
            }}
          ></div>
          <div
            className="absolute rounded-full"
            style={{
              left: '68px',
              top: '24px',
              width: '144px',
              height: '144px',
              border: '1.5px solid rgba(255,222,240,.75)',
              animation: 'ripple 3.4s cubic-bezier(.2,.6,.25,1) infinite',
            }}
          ></div>
          <div
            className="absolute rounded-full"
            style={{
              left: '68px',
              top: '24px',
              width: '144px',
              height: '144px',
              border: '1.5px solid rgba(255,222,240,.6)',
              animation: 'ripple 3.4s 1.13s cubic-bezier(.2,.6,.25,1) infinite',
            }}
          ></div>
          <div
            className="absolute rounded-full"
            style={{
              left: '68px',
              top: '24px',
              width: '144px',
              height: '144px',
              border: '1.5px solid rgba(255,222,240,.45)',
              animation: 'ripple 3.4s 2.26s cubic-bezier(.2,.6,.25,1) infinite',
            }}
          ></div>
        </div>

        <div
          className="absolute left-0 text-center text-[24px] font-semibold tracking-[-.01em] text-white"
          style={{
            top: '560px',
            width: '980px',
            textShadow: '0 2px 20px rgba(35,8,45,.8)',
            animation: 'uiInPop .8s .5s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          <span className="relative inline-block">검색 중</span>
        </div>
        <div
          className="absolute left-0 text-center text-[15px]"
          style={{
            top: '598px',
            width: '980px',
            color: 'rgba(255,236,246,.8)',
            textShadow: '0 1px 10px rgba(35,8,45,.7)',
            animation: 'uiInPop .8s .58s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          관련 에이전트를 찾고 있습니다
        </div>
      </div>

      <SidePanel animation="sideInDeep 1.05s .12s cubic-bezier(.18,.86,.24,1) both">
        <div
          className="absolute bg-[rgba(255,255,255,.10)]"
          style={{
            left: '30px',
            top: '30px',
            width: '400px',
            height: '40px',
            borderRadius: '10px',
          }}
        >
          <SearchIcon left={14} top={13} color="rgba(255,255,255,.7)" />
          <input
            className="absolute border-0 bg-transparent font-[inherit] text-[14px] text-white outline-0"
            id="searchSide"
            type="text"
            placeholder="에이전트 검색"
            style={{ left: '38px', top: '9px', width: '310px', height: '22px' }}
          />
        </div>

        <div
          className="absolute text-[15px] font-semibold tracking-[-.01em] text-white"
          style={{ left: '30px', top: '88px' }}
        >
          검색 결과
        </div>
        <div
          className="absolute text-right text-[13px]"
          style={{ right: '30px', top: '90px', color: 'rgba(255,236,246,.6)' }}
        >
          10개
        </div>

        <div
          className="scroll-thin absolute grid"
          style={{
            left: '30px',
            top: '116px',
            width: '408px',
            height: 'calc(876px + 2 * var(--edge-y, 0px))',
            // 한 줄에 두 장. 남는 폭을 열이 나눠 갖게 한다.
            gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
            gap: '14px',
            alignContent: 'start',
          }}
        >
          {results.map((item, i) => (
            <Fragment key={i}>
              <div
                style={{
                  // 폭은 주지 않고 그리드 열을 그대로 채운다.
                  height: '160px',
                  borderRadius: '12px',
                  transition: 'background .18s,transform .18s',
                }}
                className="relative cursor-pointer bg-[rgba(255,255,255,.09)] hover:-translate-y-[2px] hover:bg-[rgba(255,255,255,.17)]"
              >
                <div
                  className="absolute overflow-hidden bg-[rgba(255,255,255,.10)]"
                  style={{
                    left: '16px',
                    top: '16px',
                    width: '44px',
                    height: '44px',
                    borderRadius: '10px',
                  }}
                >
                  <div
                    className="absolute inset-0 flex items-center justify-center text-[16px] font-semibold"
                    style={{ color: 'rgba(255,226,238,.65)' }}
                  >
                    {item.initial}
                  </div>
                </div>
                <div
                  className="absolute truncate text-[14px] font-semibold tracking-[-.01em] text-white"
                  style={{ left: '16px', right: '16px', top: '76px' }}
                >
                  {item.name}
                </div>
                <div
                  className="absolute"
                  style={{
                    left: '16px',
                    top: '100px',
                    width: '113px',
                    height: '10px',
                    borderRadius: '3px',
                    background: 'rgba(255,255,255,.14)',
                  }}
                ></div>
                <div
                  className="absolute bg-[rgba(255,255,255,.10)]"
                  style={{
                    left: '16px',
                    top: '116px',
                    width: '84px',
                    height: '10px',
                    borderRadius: '3px',
                  }}
                ></div>
              </div>
            </Fragment>
          ))}
        </div>
      </SidePanel>
    </>
  )
}
