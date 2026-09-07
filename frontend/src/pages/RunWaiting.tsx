import { Fragment, useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'

export default function RunWaiting() {
  const navigate = useNavigate()
  const [filled, setFilled] = useState(false)

  const handleRun = () => {
    if (filled) navigate('/handleRun/running')
  }
  const on = filled
  const btnBg = on ? '#b98da9' : 'rgba(255,255,255,.09)'
  const btnColor = on ? '#2f1527' : 'rgba(255,255,255,.42)'
  const btnShadow = on
    ? '0 10px 26px rgba(140,100,130,.24),inset 0 1px 0 rgba(255,255,255,.5)'
    : 'inset 0 1px 0 rgba(255,255,255,.12)'
  const btnCursor = on ? 'pointer' : 'not-allowed'
  const hintText = on ? '엔터 또는 실행하기를 누르면 시작됩니다' : '입력을 기다리는 중입니다'
  const recents = [
    { title: '다운로드 폴더 정리', meta: '어제 · 파일 128개' },
    { title: '영수증 분류', meta: '3일 전 · 파일 42개' },
    { title: '회의록 요약', meta: '지난주 · 파일 9개' },
  ]

  useEffect(() => {
    const ta = document.getElementById('runPrompt') as HTMLTextAreaElement | null
    if (ta)
      ta.addEventListener('input', () => {
        const f = ta.value.trim().length > 0
        if (f !== filled) setFilled(f)
      })
  }, [])

  return (
    <>
      <div
        className="absolute top-0 left-0"
        style={{
          width: '1076px',
          height: '1024px',
          background: 'linear-gradient(160deg, rgba(16,9,32,.52), rgba(10,6,24,.68))',
          backdropFilter: 'blur(7px) saturate(112%)',
          WebkitBackdropFilter: 'blur(7px) saturate(112%)',
          boxShadow: 'inset -1px 0 0 rgba(255,255,255,.10),inset 0 1px 0 rgba(255,255,255,.08)',
          animation: 'paneIn 1.1s cubic-bezier(.18,.86,.24,1) both',
          transformOrigin: '44% 46%',
        }}
      >
        <div
          className="pointer-events-none absolute inset-0"
          style={{
            backgroundImage: 'radial-gradient(rgba(255,255,255,.10) 1px, transparent 1px)',
            backgroundSize: '26px 26px',
            backgroundPosition: '13px 13px',
            opacity: '.5',
          }}
        ></div>
        <div
          className="pointer-events-none absolute inset-0"
          style={{
            background:
              'linear-gradient(90deg, rgba(10,5,22,0) 30%, rgba(10,5,22,.26) 70%, rgba(10,5,22,.32) 100%)',
          }}
        ></div>

        <svg
          className="pointer-events-none absolute top-0 left-0"
          width="1076"
          height="1024"
          viewBox="0 0 1076 1024"
          style={{ overflow: 'visible' }}
        >
          <defs>
            <marker id="ahW" markerWidth="9" markerHeight="9" refX="7" refY="4.5" orient="auto">
              <path d="M0 0 L9 4.5 L0 9 z" fill="rgba(255,255,255,.30)" />
            </marker>
          </defs>
          <path
            d="M206 484 C 226 484, 226 404, 246 404"
            fill="none"
            stroke="rgba(255,255,255,.26)"
            strokeWidth="2"
          />
          <path
            d="M414 404 C 434 404, 434 524, 454 524"
            fill="none"
            stroke="rgba(255,255,255,.26)"
            strokeWidth="2"
          />
          <path
            d="M622 524 C 642 524, 642 414, 662 414"
            fill="none"
            stroke="rgba(255,255,255,.26)"
            strokeWidth="2"
          />
          <path
            d="M830 414 C 850 414, 850 514, 870 514"
            fill="none"
            stroke="rgba(255,255,255,.26)"
            strokeWidth="2"
          />
        </svg>

        <div
          className="absolute flex flex-col items-center justify-center rounded-full"
          style={{
            left: '38px',
            top: '400px',
            width: '168px',
            height: '168px',
            background: 'rgba(255,255,255,.075)',
            gap: '5px',
            animation:
              'floaty 6.4s ease-in-out infinite, uiIn .7s .18s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          <div
            className="text-[24px] font-bold"
            style={{ color: 'rgba(255,255,255,.9)', lineHeight: '1' }}
          >
            1
          </div>
          <div
            className="text-[15px] font-semibold tracking-[-.01em]"
            style={{ color: 'rgba(255,255,255,.86)' }}
          >
            파일 수집
          </div>
          <div
            className="text-[11px]"
            style={{ color: 'rgba(255,236,246,.5)', letterSpacing: '.06em' }}
          >
            대기
          </div>
        </div>
        <div
          className="absolute flex flex-col items-center justify-center rounded-full"
          style={{
            left: '246px',
            top: '320px',
            width: '168px',
            height: '168px',
            background: 'rgba(255,255,255,.075)',
            gap: '5px',
            animation:
              'floaty 6.9s .3s ease-in-out infinite, uiIn .7s .26s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          <div
            className="text-[24px] font-bold"
            style={{ color: 'rgba(255,255,255,.9)', lineHeight: '1' }}
          >
            2
          </div>
          <div
            className="text-[15px] font-semibold tracking-[-.01em]"
            style={{ color: 'rgba(255,255,255,.86)' }}
          >
            내용 분석
          </div>
          <div
            className="text-[11px]"
            style={{ color: 'rgba(255,236,246,.5)', letterSpacing: '.06em' }}
          >
            대기
          </div>
        </div>
        <div
          className="absolute flex flex-col items-center justify-center rounded-full"
          style={{
            left: '454px',
            top: '440px',
            width: '168px',
            height: '168px',
            background: 'rgba(255,255,255,.075)',
            gap: '5px',
            animation:
              'floaty 7.1s .8s ease-in-out infinite, uiIn .7s .34s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          <div
            className="text-[24px] font-bold"
            style={{ color: 'rgba(255,255,255,.9)', lineHeight: '1' }}
          >
            3
          </div>
          <div
            className="text-[15px] font-semibold tracking-[-.01em]"
            style={{ color: 'rgba(255,255,255,.86)' }}
          >
            분류 및 태깅
          </div>
          <div
            className="text-[11px]"
            style={{ color: 'rgba(255,236,246,.5)', letterSpacing: '.06em' }}
          >
            대기
          </div>
        </div>
        <div
          className="absolute flex flex-col items-center justify-center rounded-full"
          style={{
            left: '662px',
            top: '330px',
            width: '168px',
            height: '168px',
            background: 'rgba(255,255,255,.075)',
            gap: '5px',
            animation:
              'floaty 6.8s .4s ease-in-out infinite, uiIn .7s .42s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          <div
            className="text-[24px] font-bold"
            style={{ color: 'rgba(255,255,255,.9)', lineHeight: '1' }}
          >
            4
          </div>
          <div
            className="text-[15px] font-semibold tracking-[-.01em]"
            style={{ color: 'rgba(255,255,255,.86)' }}
          >
            요약 생성
          </div>
          <div
            className="text-[11px]"
            style={{ color: 'rgba(255,236,246,.5)', letterSpacing: '.06em' }}
          >
            대기
          </div>
        </div>
        <div
          className="absolute flex flex-col items-center justify-center rounded-full"
          style={{
            left: '870px',
            top: '430px',
            width: '168px',
            height: '168px',
            background: 'rgba(255,255,255,.075)',
            gap: '5px',
            animation:
              'floaty 7.6s 1.2s ease-in-out infinite, uiIn .7s .5s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          <div
            className="text-[24px] font-bold"
            style={{ color: 'rgba(255,255,255,.9)', lineHeight: '1' }}
          >
            5
          </div>
          <div
            className="text-[15px] font-semibold tracking-[-.01em]"
            style={{ color: 'rgba(255,255,255,.86)' }}
          >
            결과 전달
          </div>
          <div
            className="text-[11px]"
            style={{ color: 'rgba(255,236,246,.5)', letterSpacing: '.06em' }}
          >
            대기
          </div>
        </div>
      </div>

      <div
        className="absolute top-0"
        style={{
          left: '1076px',
          width: '364px',
          height: '1024px',
          background: 'rgba(13,7,26,.72)',
          backdropFilter: 'blur(26px) saturate(125%)',
          WebkitBackdropFilter: 'blur(26px) saturate(125%)',
          boxShadow: 'inset 1px 0 0 rgba(255,255,255,.14),-30px 0 60px rgba(10,4,20,.35)',
          animation: 'sideIn 1s .1s cubic-bezier(.18,.86,.24,1) both',
        }}
      >
        <div
          className="absolute text-[20px] font-bold tracking-[-.02em] text-white"
          style={{ left: '30px', top: '38px' }}
        >
          에이전트 실행
        </div>

        <div
          className="absolute text-[15px] font-semibold tracking-[-.01em]"
          style={{ left: '30px', top: '100px', color: 'rgba(255,255,255,.88)' }}
        >
          무엇을 정리할까요?
        </div>
        <div
          className="absolute"
          style={{
            left: '30px',
            top: '132px',
            width: '304px',
            height: '180px',
            borderRadius: '12px',
            background: 'rgba(255,255,255,.07)',
          }}
        >
          <textarea
            className="absolute border-0 bg-transparent font-[inherit] text-[14px] tracking-[-.01em] text-white outline-0"
            id="runPrompt"
            placeholder="예: 다운로드 폴더의 PDF를&#10;주제별로 분류해줘"
            style={{
              left: '16px',
              top: '16px',
              width: '272px',
              height: '148px',
              resize: 'none',
              lineHeight: '1.55',
            }}
          ></textarea>
        </div>

        <div
          className="absolute box-border flex flex-col"
          style={{
            left: '30px',
            top: '328px',
            width: '304px',
            height: '176px',
            padding: '18px 20px',
            borderRadius: '14px',
            background: 'linear-gradient(180deg, rgba(201,169,221,.13), rgba(255,255,255,.05))',
          }}
        >
          <div
            className="flex-none text-[11px] font-semibold"
            style={{ letterSpacing: '.08em', color: 'rgba(255,214,232,.6)' }}
          >
            실행 예상 비용
          </div>
          <div
            className="flex flex-none"
            style={{ marginTop: '9px', alignItems: 'baseline', gap: '8px' }}
          >
            <div
              className="text-[26px] font-semibold tracking-[-.02em] text-white"
              style={{ lineHeight: '1' }}
            >
              0.54
            </div>
            <div className="text-[13px] font-semibold" style={{ color: 'rgba(255,236,246,.72)' }}>
              ABC
            </div>
            <div
              className="text-[12.5px]"
              style={{ marginLeft: 'auto', color: 'rgba(255,236,246,.6)' }}
            >
              ≈ 540 토큰
            </div>
          </div>
          <div
            className="flex flex-none justify-between"
            style={{ marginTop: '16px', alignItems: 'baseline' }}
          >
            <span className="text-[11.5px]" style={{ color: 'rgba(255,236,246,.58)' }}>
              사용 가능 코인
            </span>
            <span className="text-[12px] font-semibold" style={{ color: '#c9a9dd' }}>
              640 ABC
            </span>
          </div>
          <div
            className="flex-none overflow-hidden"
            style={{
              marginTop: '7px',
              height: '4px',
              borderRadius: '2px',
              background: 'rgba(255,255,255,.1)',
            }}
          >
            <div
              style={{ width: '8%', height: '4px', borderRadius: '2px', background: '#c9a9dd' }}
            ></div>
          </div>
          <div
            className="flex-none text-[10.5px] tracking-[-.01em]"
            style={{ marginTop: 'auto', color: 'rgba(255,236,246,.44)' }}
          >
            실제 사용량에 따라 달라질 수 있습니다
          </div>
        </div>

        <div
          id="runBtn"
          onClick={handleRun}
          style={{
            left: '30px',
            top: '528px',
            width: '304px',
            height: '56px',
            borderRadius: '12px',
            background: `${btnBg}`,
            boxShadow: `${btnShadow}`,
            gap: '8px',
            color: `${btnColor}`,
            cursor: `${btnCursor}`,
            transition: 'background .25s,color .25s,box-shadow .25s,transform .18s',
          }}
          className="absolute flex items-center justify-center text-[16px] font-bold tracking-[-.01em] hover:-translate-y-[2px]"
        >
          <span>실행하기</span>
        </div>
        <div
          className="absolute text-center text-[12px] tracking-[-.01em]"
          style={{ left: '30px', top: '592px', width: '304px', color: 'rgba(255,236,246,.5)' }}
        >
          {hintText}
        </div>

        <div
          className="absolute text-[15px] font-semibold tracking-[-.01em]"
          style={{ left: '30px', top: '630px', color: 'rgba(255,255,255,.88)' }}
        >
          최근 실행
        </div>
        <div
          className="absolute flex flex-col"
          style={{ left: '30px', top: '662px', width: '304px', gap: '10px' }}
        >
          {recents.map((row, i) => (
            <Fragment key={i}>
              <div
                style={{
                  width: '304px',
                  height: '64px',
                  borderRadius: '10px',
                  background: 'rgba(255,255,255,.06)',
                }}
                className="relative cursor-pointer transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.14)]"
              >
                <div
                  className="absolute text-[13.5px] font-semibold tracking-[-.01em]"
                  style={{ left: '16px', top: '13px', color: 'rgba(255,255,255,.9)' }}
                >
                  {row.title}
                </div>
                <div
                  className="absolute text-[11.5px]"
                  style={{ left: '16px', top: '35px', color: 'rgba(255,236,246,.5)' }}
                >
                  {row.meta}
                </div>
                <div
                  className="absolute rounded-full"
                  style={{
                    right: '16px',
                    top: '27px',
                    width: '6px',
                    height: '6px',
                    background: 'rgba(125,227,176,.7)',
                  }}
                ></div>
              </div>
            </Fragment>
          ))}
        </div>
      </div>
    </>
  )
}
