import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { paths } from '@/routes/paths'

const FULL_TITLE = '안녕하세요.\n어떤 워크플로우를 만들고 싶으신가요?'

export default function Main() {
  const navigate = useNavigate()
  const [dragging, setDragging] = useState(false)
  const [uploadOpen, setUploadOpen] = useState(false)

  const drag = dragging
  const clipBg = uploadOpen ? 'rgba(255,255,255,.16)' : 'transparent'
  const handleToggleUpload = () => setUploadOpen((v) => !v)
  const handleCloseUpload = () => setUploadOpen(false)
  const barTop = 552
  const barH = drag ? 232 : 68
  const barRadius = drag ? 28 : 34
  const rowOpacity = drag ? 0 : 1
  const rowEvents = drag ? 'none' : 'auto'
  const barBg = '.34'
  const barOutline = drag ? '2px dashed rgba(255,255,255,.4)' : '2px dashed rgba(255,255,255,0)'
  const titleColor = '#fff'

  useEffect(() => {
    // 파일 드래그 → 드롭존 표시
    let _depth = 0
    const _onEnter = (e: DragEvent) => {
      if (!e.dataTransfer || Array.from(e.dataTransfer.types || []).indexOf('Files') < 0) return
      e.preventDefault()
      _depth++
      setDragging(true) // 같은 값이면 React 가 리렌더를 건너뛴다
    }
    const _onOver = (e: DragEvent) => {
      // dragover 를 막지 않으면 브라우저가 drop 을 발생시키지 않는다.
      // state 대신 _depth 를 보는 이유: state 는 이 클로저에 마운트 시점 값으로 갇힌다
      if (_depth) e.preventDefault()
    }
    const _onLeave = () => {
      _depth = Math.max(0, _depth - 1)
      if (!_depth) setDragging(false)
    }
    const _onDrop = (e: DragEvent) => {
      e.preventDefault()
      _depth = 0
      setDragging(false)
    }
    window.addEventListener('dragenter', _onEnter)
    window.addEventListener('dragover', _onOver)
    window.addEventListener('dragleave', _onLeave)
    window.addEventListener('drop', _onDrop)
    // 타이핑 효과. 예약한 타이머를 모아 두고 정리 시 한 번에 끊는다
    const timers: ReturnType<typeof setTimeout>[] = []
    let typing: ReturnType<typeof setInterval> | undefined
    const startTyping = (tries: number) => {
      if (!document.getElementById('mainTypeText')) {
        if (tries < 60) timers.push(setTimeout(() => startTyping(tries + 1), 100))
        return
      }
      const t0 = performance.now()
      typing = setInterval(() => {
        const node = document.getElementById('mainTypeText')
        if (!node) return
        const i = Math.min(FULL_TITLE.length, Math.floor((performance.now() - t0) / 55))
        node.textContent = FULL_TITLE.slice(0, i)
        if (i < FULL_TITLE.length) return
        clearInterval(typing)
        timers.push(
          setTimeout(() => {
            const c = document.getElementById('mainCaret')
            if (c) c.style.display = 'none'
          }, 900),
        )
      }, 55)
    }
    timers.push(setTimeout(() => startTyping(0), 350))

    const input = document.getElementById('agentInput')
    const onKeydown = (e: KeyboardEvent) => {
      if (e.key === 'Enter') navigate(paths.edit.searching)
    }
    input?.addEventListener('keydown', onKeydown)

    return () => {
      clearInterval(typing)
      timers.forEach(clearTimeout)
      input?.removeEventListener('keydown', onKeydown)
      window.removeEventListener('dragenter', _onEnter)
      window.removeEventListener('dragover', _onOver)
      window.removeEventListener('dragleave', _onLeave)
      window.removeEventListener('drop', _onDrop)
    }
  }, [navigate])

  return (
    <>
      <div
        className="pointer-events-none absolute"
        style={{
          left: '120px',
          top: '300px',
          width: '1200px',
          height: '520px',
          background:
            'radial-gradient(58% 52% at 50% 50%, rgba(38,14,48,.46) 0%, rgba(38,14,48,.30) 45%, rgba(38,14,48,0) 78%)',
        }}
      ></div>

      <div
        className="absolute text-center text-[40px] font-bold tracking-[-.02em]"
        style={{
          left: '220px',
          top: '376px',
          width: '1000px',
          height: '112px',
          lineHeight: '1.4',
          color: `${titleColor}`,
          textShadow: '0 2px 24px rgba(35,8,45,.75),0 2px 6px rgba(35,8,45,.55)',
          whiteSpace: 'pre-line',
          transition: 'color .2s',
        }}
      >
        <span id="mainTypeText"></span>
        <span
          className="inline-block"
          id="mainCaret"
          style={{
            width: '3px',
            height: '34px',
            marginLeft: '5px',
            verticalAlign: 'baseline',
            borderRadius: '2px',
            background: 'rgba(255,255,255,.85)',
            animation: 'mainCaret .9s steps(1) infinite',
          }}
        ></span>
      </div>

      <div
        className="absolute border-0"
        style={{
          left: '360px',
          top: `${barTop}px`,
          width: '720px',
          height: `${barH}px`,
          borderRadius: `${barRadius}px`,
          background: `rgba(28,10,38,${barBg})`,
          outline: `${barOutline}`,
          outlineOffset: '-2px',
          backdropFilter: 'blur(24px) saturate(130%)',
          WebkitBackdropFilter: 'blur(24px) saturate(130%)',
          boxShadow: '0 20px 54px rgba(30,8,40,.38),inset 0 1px 0 rgba(255,255,255,.28)',
          transition:
            'top .42s cubic-bezier(.22,.8,.24,1),height .42s cubic-bezier(.22,.8,.24,1),border-radius .42s ease,background .3s ease,outline-color .3s ease',
          zIndex: '42',
        }}
      >
        <div
          onClick={handleToggleUpload}
          title="파일 첨부"
          style={{
            left: '22px',
            top: '18px',
            width: '32px',
            height: '32px',
            borderRadius: '9px',
            opacity: `${rowOpacity}`,
            pointerEvents: `${rowEvents}`,
            background: `${clipBg}`,
          }}
          className="absolute flex cursor-pointer items-center justify-center transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.12)]"
        >
          <svg
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="none"
            style={{ stroke: 'var(--color-body)' }}
            strokeWidth="1.9"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <path d="M21.4 11.05 12.2 20.2a5.5 5.5 0 0 1-7.8-7.8l8.5-8.5a3.7 3.7 0 0 1 5.2 5.2l-8.5 8.5a1.85 1.85 0 0 1-2.6-2.6l7.8-7.8" />
          </svg>
        </div>
        <input
          className="absolute border-0 bg-transparent font-[inherit] text-[16px] tracking-[-.01em] text-white outline-0"
          id="agentInput"
          type="text"
          placeholder="만들고 싶은 에이전트를 설명해 주세요"
          style={{
            left: '64px',
            top: '16px',
            width: '576px',
            height: '36px',
            opacity: `${rowOpacity}`,
            pointerEvents: `${rowEvents}`,
            transition: 'opacity .22s ease',
          }}
        />
        <a
          href={paths.edit.searching}
          style={{
            left: '656px',
            top: '12px',
            width: '44px',
            height: '44px',
            borderRadius: '22px',
            opacity: `${rowOpacity}`,
            pointerEvents: `${rowEvents}`,
            transition: 'opacity .22s ease',
            background: 'var(--color-accent)',
            boxShadow: '0 6px 18px rgba(140,100,130,.26)',
            textDecoration: 'none',
          }}
          className="absolute flex cursor-pointer items-center justify-center hover:brightness-[1.05]"
        >
          <svg
            width="18"
            height="18"
            viewBox="0 0 18 18"
            fill="none"
            style={{ stroke: 'var(--color-accent-ink)' }}
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <path d="M9 15V3.6" />
            <path d="M3.9 8.7 9 3.6l5.1 5.1" />
          </svg>
        </a>
        {dragging && (
          <>
            <div
              className="absolute top-0 left-0 flex flex-col items-center justify-center"
              style={{
                width: '720px',
                height: '232px',
                gap: '14px',
                animation: 'fadeIn .3s .12s ease both',
              }}
            >
              <div
                className="flex items-center justify-center rounded-full"
                style={{
                  width: '60px',
                  height: '60px',
                  background: 'rgba(255,255,255,.9)',
                  boxShadow: '0 10px 26px rgba(8,4,18,.3)',
                }}
              >
                <svg
                  width="22"
                  height="22"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="#3a1030"
                  strokeWidth="2.2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d="M12 19V5" />
                  <path d="M5 12l7-7 7 7" />
                </svg>
              </div>
              <div className="text-[19px] font-semibold tracking-[-.01em] text-white">
                여기에 파일을 놓아주세요
              </div>
              <div
                className="text-[12.5px] tracking-[-.01em]"
                style={{ color: 'rgba(255,236,246,.7)' }}
              >
                PDF · TXT · DOCX · CSV · MD 지원
              </div>
            </div>
          </>
        )}
      </div>

      <div
        className="absolute flex"
        style={{ left: '502px', top: '656px', width: '420px', height: '41px', gap: '12px' }}
      >
        <button
          style={{
            width: '96px',
            height: '41px',
            borderRadius: '100px',
            background: 'rgba(30,10,40,.30)',
            backdropFilter: 'blur(16px)',
            WebkitBackdropFilter: 'blur(16px)',
            boxShadow: '0 4px 16px rgba(30,8,40,.28)',
          }}
          className="cursor-pointer border-0 font-[inherit] text-[14px] font-medium whitespace-nowrap text-white hover:bg-[rgba(30,10,40,.46)]"
        >
          문서 정리
        </button>
        <button
          style={{
            width: '96px',
            height: '41px',
            borderRadius: '100px',
            background: 'rgba(30,10,40,.30)',
            backdropFilter: 'blur(16px)',
            WebkitBackdropFilter: 'blur(16px)',
            boxShadow: '0 4px 16px rgba(30,8,40,.28)',
          }}
          className="cursor-pointer border-0 font-[inherit] text-[14px] font-medium whitespace-nowrap text-white hover:bg-[rgba(30,10,40,.46)]"
        >
          일정 요약
        </button>
        <button
          style={{
            width: '96px',
            height: '41px',
            borderRadius: '100px',
            background: 'rgba(30,10,40,.30)',
            backdropFilter: 'blur(16px)',
            WebkitBackdropFilter: 'blur(16px)',
            boxShadow: '0 4px 16px rgba(30,8,40,.28)',
          }}
          className="cursor-pointer border-0 font-[inherit] text-[14px] font-medium whitespace-nowrap text-white hover:bg-[rgba(30,10,40,.46)]"
        >
          메일 초안
        </button>
        <button
          style={{
            width: '96px',
            height: '41px',
            borderRadius: '100px',
            background: 'rgba(30,10,40,.30)',
            backdropFilter: 'blur(16px)',
            WebkitBackdropFilter: 'blur(16px)',
            boxShadow: '0 4px 16px rgba(30,8,40,.28)',
          }}
          className="cursor-pointer border-0 font-[inherit] text-[14px] font-medium whitespace-nowrap text-white hover:bg-[rgba(30,10,40,.46)]"
        >
          코드 리뷰
        </button>
      </div>

      {uploadOpen && (
        <>
          <div
            className="absolute top-0 left-0"
            onClick={handleCloseUpload}
            style={{ width: '1440px', height: '1024px', zIndex: '30' }}
          ></div>
          <div
            className="absolute box-border flex flex-col items-center text-center"
            style={{
              left: '360px',
              top: '632px',
              width: '340px',
              padding: '22px',
              borderRadius: '16px',
              background: 'rgba(20,11,36,.92)',
              backdropFilter: 'blur(26px) saturate(125%)',
              WebkitBackdropFilter: 'blur(26px) saturate(125%)',
              boxShadow: 'inset 0 1px 0 rgba(255,255,255,.16),0 22px 54px rgba(4,2,12,.55)',
              gap: '10px',
              animation: 'dropIn .22s cubic-bezier(.2,.9,.3,1) both',
              transformOrigin: '12% 0',
              zIndex: '31',
            }}
          >
            <div className="text-[15px] font-semibold tracking-[-.01em] text-white">
              파일 업로드
            </div>
            <div
              className="text-[12.5px] tracking-[-.01em]"
              style={{
                width: '100%',
                lineHeight: '1.6',
                color: 'rgba(255,236,246,.68)',
                textWrap: 'pretty',
              }}
            >
              텍스트로 읽을 수 있는 파일을 올려주세요.
              <br />
              에이전트가 내용을 참고해 작업을 구성합니다.
            </div>
            <div
              onClick={handleCloseUpload}
              style={{
                marginTop: '4px',
                width: '100%',
                height: '44px',
                borderRadius: '12px',
                background: 'var(--color-accent)',
                boxShadow: '0 8px 20px rgba(140,100,130,.26)',
                gap: '8px',
                color: 'var(--color-accent-ink)',
              }}
              className="flex cursor-pointer items-center justify-center text-[14px] font-bold tracking-[-.01em] hover:brightness-[1.05]"
            >
              <svg
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                style={{ stroke: 'var(--color-accent-ink)' }}
                strokeWidth="2.2"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <path d="M12 19V5" />
                <path d="M5 12l7-7 7 7" />
              </svg>
              <span>파일 선택하기</span>
            </div>
            <div
              className="text-[11.5px] tracking-[-.01em]"
              style={{ width: '100%', lineHeight: '1.6', color: 'rgba(255,236,246,.52)' }}
            >
              창에 파일을 끌어다 놓아도 업로드됩니다
              <br />
              PDF · TXT · DOCX · CSV · MD 지원
            </div>
          </div>
        </>
      )}

      <div
        className="absolute text-center text-[13px] tracking-[-.01em]"
        style={{
          left: '220px',
          top: '728px',
          width: '1000px',
          color: 'rgba(255,240,247,.9)',
          textShadow: '0 1px 8px rgba(35,8,45,.7)',
        }}
      >
        엔터를 누르면 에이전트 생성이 시작됩니다
      </div>
    </>
  )
}
