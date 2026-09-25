import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { RECOMMEND_LIMITS } from '@/api/agents'
import { searchPath } from '@/routes/handoff'
import { paths } from '@/routes/paths'

const FULL_TITLE = '안녕하세요.\n어떤 워크플로우를 만들고 싶으신가요?'

/**
 * 입력창 아래 칩. 누르면 그 자리에서 찾기가 시작된다.
 *
 * 칩에 적힌 말과 찾을 때 보내는 말이 다르다 — 추천은 문장을 받아야 잘 찾는다.
 * 짧은 이름은 칩에, 하고 싶은 일을 적은 문장은 질의에 둔다.
 */
const SUGGESTIONS = [
  { label: '문서 정리', query: '문서를 항목별로 정리하고 싶어' },
  { label: '일정 요약', query: '이번 주 일정을 한눈에 보게 요약하고 싶어' },
  { label: '메일 초안', query: '받은 메일에 답장 초안을 써 주면 좋겠어' },
  { label: '코드 리뷰', query: '우리 팀이 쓴 코드를 검토해 주면 좋겠어' },
] as const

export default function Main() {
  const navigate = useNavigate()
  const [dragging, setDragging] = useState(false)
  const [uploadOpen, setUploadOpen] = useState(false)
  /** 빈 채로 엔터를 눌렀나. 한 글자라도 적으면 내린다. */
  const [needsQuery, setNeedsQuery] = useState(false)

  /** 찾기를 시작하는 유일한 경로. 엔터·버튼·칩이 같이 쓴다. */
  const startSearch = useCallback(
    (preset?: string) => {
      const input = document.getElementById('agentInput')
      const typed = input instanceof HTMLInputElement ? input.value.trim() : ''
      // 칩은 적힌 것이 없어도 제 문장으로 찾는다.
      const query = (preset ?? typed).trim()
      // 빈 문장으로는 찾을 것이 없다(서버도 COMMON_001 로 거절한다). 조용히 넘기면
      // 엔터가 고장 난 것처럼 보이므로, 무엇이 없는지 말하고 입력창으로 커서를 보낸다.
      if (!query) {
        setNeedsQuery(true)
        input?.focus()
        return
      }
      navigate(searchPath(paths.edit.searching, query))
    },
    [navigate],
  )

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
      if (e.key === 'Enter') startSearch()
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
  }, [navigate, startSearch])

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
          maxLength={RECOMMEND_LIMITS.query.max}
          onChange={() => setNeedsQuery(false)}
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
        {/* 링크가 아니라 버튼이다 — 주소는 적은 문장에 따라 달라지는데, 링크로 두면
            새 탭으로 열었을 때 검색어 없는 주소로 들어가 랜딩으로 되튕긴다. */}
        <button
          type="button"
          aria-label="에이전트 찾기"
          onClick={() => startSearch()}
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
          }}
          className="absolute flex cursor-pointer items-center justify-center border-0 p-0 hover:brightness-[1.05]"
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
        </button>
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
        {SUGGESTIONS.map((suggestion) => (
          <button
            key={suggestion.label}
            type="button"
            onClick={() => startSearch(suggestion.query)}
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
            {suggestion.label}
          </button>
        ))}
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

      {needsQuery && (
        <div
          className="absolute text-[13px] font-semibold tracking-[-.01em]"
          style={{
            // 입력창 바로 아래 — 아래쪽 추천 칩(656px)에 닿지 않는 자리다.
            // 왼쪽 끝은 자리표시자 글자가 시작하는 자리(바 360 + 입력 64)에 맞춘다.
            left: '424px',
            top: '628px',
            width: '656px',
            color: 'rgba(255,226,240,1)',
            // 배경이 밝은 하늘이라 그림자를 겹쳐 깔지 않으면 글자가 묻힌다.
            textShadow: '0 1px 10px rgba(35,8,45,.95),0 0 20px rgba(35,8,45,.7)',
            animation: 'uiInPop .35s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          내용을 입력해주세요
        </div>
      )}
    </>
  )
}
