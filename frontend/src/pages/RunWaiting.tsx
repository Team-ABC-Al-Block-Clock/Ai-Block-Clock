import { Fragment, useState, useEffect } from 'react'

import CanvasNode from '@/components/CanvasNode'
import { CANVAS_NODES } from '@/lib/canvasWorkflow'
import { useNavigate } from 'react-router-dom'
import { paths } from '@/routes/paths'
import SidePanel from '@/components/SidePanel'
import StatusDot from '@/components/StatusDot'

/** 판매자가 비워 두었거나, 마켓 에이전트가 아니라 직접 만든 워크플로우를 돌릴 때 쓴다. */
const DEFAULT_ONBOARDING_MESSAGE = '무엇을 도와드릴까요?'

export default function RunWaiting() {
  const navigate = useNavigate()
  // 에이전트 조회가 붙으면 여기로 들어온다. 최대 200자라 넘치면 줄바꿈된다.
  const onboardingMessage = ''
  const [filled, setFilled] = useState(false)

  const handleRun = () => {
    if (filled) navigate(paths.run.running.withoutId)
  }
  const on = filled
  const btnBg = on ? 'var(--color-accent)' : 'rgba(255,255,255,.09)'
  const btnColor = on ? 'var(--color-accent-ink)' : 'rgba(255,255,255,.42)'
  const btnShadow = on
    ? '0 10px 26px rgba(140,100,130,.24),inset 0 1px 0 rgba(255,255,255,.5)'
    : 'inset 0 1px 0 rgba(255,255,255,.12)'
  const btnCursor = on ? 'pointer' : 'not-allowed'
  const recents = [
    { title: '다운로드 폴더 정리', meta: '어제 · 파일 128개' },
    { title: '영수증 분류', meta: '3일 전 · 파일 42개' },
    { title: '회의록 요약', meta: '지난주 · 파일 9개' },
  ]

  useEffect(() => {
    const ta = document.getElementById('runPrompt') as HTMLTextAreaElement | null
    if (!ta) return
    // 같은 값이면 React 가 리렌더를 건너뛴다 — 직전 값을 따로 비교할 필요가 없다.
    const onInput = () => setFilled(ta.value.trim().length > 0)
    ta.addEventListener('input', onInput)
    return () => ta.removeEventListener('input', onInput)
  }, [])

  return (
    <>
      <div
        className="absolute top-0 left-0"
        style={{
          width: '1076px',
          height: '1024px',
          animation: 'paneIn 1.1s cubic-bezier(.18,.86,.24,1) both',
          transformOrigin: '44% 46%',
        }}
      >
        <div
          className="pointer-events-none absolute"
          style={{
            left: 'calc(-1 * var(--edge, 0px))',
            right: 'calc(96px - var(--edge, 0px))',
            top: 'calc(-1 * var(--edge-y, 0px))',
            bottom: 'calc(-1 * var(--edge-y, 0px))',
            backgroundImage: 'radial-gradient(rgba(255,255,255,.10) 1px, transparent 1px)',
            backgroundSize: '26px 26px',
            backgroundPosition: '13px 13px',
            opacity: '.5',
          }}
        ></div>
        <div
          className="pointer-events-none absolute"
          style={{
            left: 'calc(-1 * var(--edge, 0px))',
            right: 'calc(96px - var(--edge, 0px))',
            top: 'calc(-1 * var(--edge-y, 0px))',
            bottom: 'calc(-1 * var(--edge-y, 0px))',
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
            d="M180 484 C 195 484, 195 404, 209 404"
            fill="none"
            stroke="rgba(255,255,255,.26)"
            strokeWidth="2"
          />
          <path
            d="M377 404 C 392 404, 392 524, 406 524"
            fill="none"
            stroke="rgba(255,255,255,.26)"
            strokeWidth="2"
          />
          <path
            d="M574 524 C 589 524, 589 414, 603 414"
            fill="none"
            stroke="rgba(255,255,255,.26)"
            strokeWidth="2"
          />
          <path
            d="M771 414 C 786 414, 786 514, 800 514"
            fill="none"
            stroke="rgba(255,255,255,.26)"
            strokeWidth="2"
          />
        </svg>

        {CANVAS_NODES.map((node, i) => (
          <CanvasNode
            key={node.id}
            index={i}
            name={node.name}
            sublabel="대기"
            x={node.x}
            y={node.y}
            mine={node.mine}
            animation={node.animation}
            background="rgba(255,255,255,.075)"
            numberColor="rgba(255,255,255,.9)"
          />
        ))}
      </div>

      <SidePanel>
        <div
          className="absolute text-[20px] font-bold tracking-[-.02em] text-white"
          style={{ left: '30px', top: '38px' }}
        >
          워크플로우 실행
        </div>

        {/* 이전 단계로 돌아가 워크플로우를 수정할 수 있다. */}
        <a
          href={paths.edit.nodes.withoutId}
          title="워크플로우 수정"
          aria-label="워크플로우 수정"
          style={{
            right: '26px',
            top: '32px',
            width: '40px',
            height: '40px',
            borderRadius: '50%',
            background: 'rgba(255,255,255,.12)',
            boxShadow: 'inset 0 0 0 1px rgba(255,255,255,.16)',
          }}
          className="absolute flex cursor-pointer items-center justify-center transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.24)]"
        >
          <svg width="22" height="22" viewBox="0 0 22 22" fill="none">
            <path
              d="M17 11H6M11 6L6 11L11 16"
              stroke="rgba(255,255,255,.9)"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </a>

        <div
          className="absolute text-[20px] font-semibold tracking-[-.01em]"
          style={{ left: '30px', top: '96px', color: 'rgba(255,255,255,.96)' }}
        >
          {onboardingMessage || DEFAULT_ONBOARDING_MESSAGE}
        </div>
        <div
          className="absolute"
          style={{
            left: '30px',
            top: '132px',
            width: '400px',
            height: '180px',
            borderRadius: '12px',
            background: 'var(--color-surface-inset)',
          }}
        >
          <textarea
            className="absolute border-0 bg-transparent font-[inherit] text-[16.5px] tracking-[-.005em] text-white outline-0"
            id="runPrompt"
            placeholder="예: 다운로드 폴더의 PDF를 주제별로 분류해줘"
            style={{
              left: '18px',
              top: '16px',
              width: '364px',
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
            width: '400px',
            height: '176px',
            padding: '18px 20px',
            borderRadius: '14px',
            background: 'linear-gradient(180deg, rgba(201,169,221,.13), rgba(255,255,255,.05))',
          }}
        >
          <div
            className="flex-none text-[15px] font-semibold"
            style={{ letterSpacing: '.06em', color: 'rgba(255,214,232,.8)' }}
          >
            실행 예상 비용
          </div>
          <div
            className="flex flex-none"
            style={{ marginTop: '9px', alignItems: 'baseline', gap: '8px' }}
          >
            <div
              className="text-[30px] font-semibold tracking-[-.02em] text-white"
              style={{ lineHeight: '1' }}
            >
              0.54
            </div>
            <div className="text-[16.5px] font-semibold" style={{ color: 'rgba(255,236,246,.9)' }}>
              ABC
            </div>
            <div
              className="text-[14.5px]"
              style={{ marginLeft: 'auto', color: 'rgba(255,236,246,.74)' }}
            >
              ≈ 540 토큰
            </div>
          </div>
          <div
            className="flex flex-none justify-between"
            style={{ marginTop: '16px', alignItems: 'baseline' }}
          >
            <span className="text-[14.5px]" style={{ color: 'rgba(255,236,246,.78)' }}>
              사용 가능 코인
            </span>
            <span className="text-[15.5px] font-semibold" style={{ color: '#d7bce8' }}>
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
            className="flex-none text-[13px] tracking-[-.005em]"
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
            width: '400px',
            height: '56px',
            borderRadius: '12px',
            background: `${btnBg}`,
            boxShadow: `${btnShadow}`,
            gap: '8px',
            color: `${btnColor}`,
            cursor: `${btnCursor}`,
            transition: 'background .25s,color .25s,box-shadow .25s,transform .18s',
          }}
          className="absolute flex items-center justify-center text-[17px] font-bold tracking-[-.01em] hover:-translate-y-[2px]"
        >
          <span>실행하기</span>
        </div>

        <div
          className="absolute text-[20px] font-semibold tracking-[-.01em]"
          style={{ left: '30px', top: '628px', color: 'rgba(255,255,255,.96)' }}
        >
          최근 실행
        </div>
        <div
          className="absolute flex flex-col"
          style={{ left: '30px', top: '662px', width: '400px', gap: '10px' }}
        >
          {recents.map((row, i) => (
            <Fragment key={i}>
              <div
                style={{
                  width: '400px',
                  height: '64px',
                  borderRadius: '10px',
                  background: 'rgba(255,255,255,.06)',
                }}
                className="relative cursor-pointer transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.14)]"
              >
                <div
                  className="absolute text-[15px] font-semibold tracking-[-.005em]"
                  style={{ left: '16px', top: '13px', color: 'rgba(255,255,255,.9)' }}
                >
                  {row.title}
                </div>
                <div
                  className="absolute text-[13.5px]"
                  style={{ left: '16px', top: '35px', color: 'rgba(255,236,246,.5)' }}
                >
                  {row.meta}
                </div>
                <StatusDot
                  size={6}
                  color="rgba(125,227,176,.7)"
                  style={{ position: 'absolute', right: '16px', top: '27px' }}
                />
              </div>
            </Fragment>
          ))}
        </div>
      </SidePanel>
    </>
  )
}
