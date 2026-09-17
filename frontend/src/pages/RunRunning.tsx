import { Fragment, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import CanvasNode from '@/components/CanvasNode'
import { CANVAS_NODES, canSellWorkflow } from '@/lib/canvasWorkflow'
import { cssToStyle } from '../lib/css'
import { paths } from '@/routes/paths'
import SidePanel from '@/components/SidePanel'
import StatusDot from '@/components/StatusDot'

export default function RunRunning() {
  // 등록 화면에서 취소 버튼을 누르면 실행 결과 화면으로 되돌아간다.
  const [searchParams] = useSearchParams()
  const startDone = searchParams.get('done') === '1'
  const [pct, setPct] = useState(startDone ? 100 : 0)
  const [copied, setCopied] = useState(false)

  const canSell = canSellWorkflow(CANVAS_NODES)

  const copyTimer = useRef<ReturnType<typeof setTimeout>>(undefined)
  const rafId = useRef<number>(undefined)

  const defs = CANVAS_NODES
  const edgePaths = [
    'M180 484 C 195 484, 195 404, 209 404',
    'M377 404 C 392 404, 392 524, 406 524',
    'M574 524 C 589 524, 589 414, 603 414',
    'M771 414 C 786 414, 786 514, 800 514',
  ]
  const logLines = [
    '입력 파일 128개 수집 완료',
    'PDF 112개 · 이미지 16개 판별',
    '문서 본문 추출 및 정규화',
    '주제 클러스터 7개 도출',
    '태그 규칙 적용 중',
    '분류 결과 검증',
    '요약 생성 대기',
  ]
  const pctR = Math.round(pct)
  const per = 100 / defs.length
  const active = Math.min(defs.length - 1, Math.floor(pctR / per))
  const g = defs[active]
  const glowStyle =
    pctR < 100
      ? 'position:absolute;left:' +
        (g.x - 10) +
        'px;top:' +
        (g.y - 10) +
        'px;width:188px;height:188px;border-radius:50%;pointer-events:none;background:radial-gradient(circle, rgba(255,160,200,.20) 0 48%, rgba(255,160,200,0) 66%);animation:runGlow 2.4s ease-in-out infinite;transition:left .6s cubic-bezier(.2,.8,.25,1),top .6s cubic-bezier(.2,.8,.25,1)'
      : 'display:none'
  const nodes = defs.map((d, i) => {
    const done = i < active || pctR >= 100
    const running = i === active && pctR < 100
    return {
      ...d,
      background: running
        ? 'rgba(255,255,255,.17)'
        : done
          ? 'rgba(255,255,255,.115)'
          : 'rgba(255,255,255,.06)',
      numberColor: running ? '#fff' : done ? 'rgba(255,255,255,.9)' : 'rgba(255,255,255,.5)',
      nameColor: running ? '#fff' : done ? 'rgba(255,255,255,.9)' : 'rgba(255,255,255,.55)',
      sublabelColor: running
        ? 'rgba(230,196,212,.85)'
        : done
          ? 'rgba(154,208,180,.85)'
          : 'rgba(255,236,246,.4)',
      stateLabel: running ? '진행 중' : done ? '완료' : '대기',
    }
  })
  const edges = edgePaths.map((d, i) => {
    const lit = i < active
    return {
      d,
      stroke: lit ? 'rgba(255,190,214,.75)' : 'rgba(255,255,255,.38)',
      marker: lit ? 'url(#ahR)' : 'url(#ahRd)',
    }
  })
  const liveEdges = edgePaths.slice(0, Math.max(0, active)).map((d) => ({ d }))
  const shown = Math.min(
    logLines.length,
    Math.max(1, Math.round((pctR / 100) * logLines.length) + 1),
  )
  const logs = logLines.slice(0, shown).map((text, i) => {
    const last = i === shown - 1 && pctR < 100
    return {
      text,
      dot: last ? 'rgba(255,194,216,.9)' : 'rgba(154,208,180,.7)',
      color: last ? 'rgba(255,255,255,.92)' : 'rgba(255,255,255,.6)',
      caret: last
        ? 'width:7px;height:14px;flex:none;border-radius:2px;background:rgba(255,194,216,.9);animation:caret 1s steps(1,end) infinite'
        : 'display:none',
    }
  })
  const resultText = [
    '# 문서 정리 결과',
    '',
    '처리 파일  128개 (PDF 112 · 이미지 16)',
    '분류 주제  7개',
    '소요 시간  1분 42초',
    '',
    '[계약]      문서 24개',
    '[영수증]    문서 31개',
    '[회의록]    문서 18개',
    '[리서치]    문서 22개',
    '[기타]      문서 33개',
    '',
    '저장 위치  /Documents/정리결과',
  ]
  const handleCopyResult = () => {
    const txt = resultText.join('\n')
    const ta = document.createElement('textarea')
    ta.value = txt
    ta.setAttribute('readonly', '')
    ta.style.cssText =
      'position:fixed;top:0;left:0;width:1px;height:1px;opacity:0;pointer-events:none'
    document.body.appendChild(ta)
    ta.select()
    ta.setSelectionRange(0, txt.length)
    try {
      document.execCommand('copy')
    } catch {
      /* execCommand 미지원 — 아래 clipboard API 로 재시도 */
    }
    document.body.removeChild(ta)
    if (navigator.clipboard && navigator.clipboard.writeText) {
      try {
        navigator.clipboard.writeText(txt).catch(() => undefined)
      } catch {
        /* 권한 거부·비보안 컨텍스트 */
      }
    }
    setCopied(true)
    clearTimeout(copyTimer.current)
    copyTimer.current = setTimeout(() => setCopied(false), 1900)
  }
  const resultLines = resultText.map((text) => ({
    text: text === '' ? '\u00a0' : text,
    color: text.startsWith('#')
      ? 'rgba(255,255,255,.92)'
      : text.startsWith('[')
        ? 'rgba(255,206,226,.88)'
        : 'rgba(255,255,255,.62)',
  }))
  const copyLabel = copied ? '복사됨' : '복사'
  const copyBg = copied ? 'rgba(154,208,180,.26)' : 'rgba(255,255,255,.1)'
  const copyFg = copied ? '#a8ddc0' : 'rgba(255,255,255,.82)'
  const running = pctR < 100
  const done = pctR >= 100
  const pctLabel = pctR + '%'
  const gaugeBg =
    'conic-gradient(from 0deg, var(--color-accent) 0 ' +
    pctR +
    '%, rgba(255,255,255,.10) ' +
    pctR +
    '% 100%)'
  const stepTitle = pctR >= 100 ? '결과 전달 완료' : defs[active].name + ' 중'
  const stepMeta = (pctR >= 100 ? defs.length : active + 1) + ' / ' + defs.length + ' 단계'

  useEffect(() => {
    if (startDone) return
    const t0 = performance.now()
    const tick = (now: number) => {
      const p = Math.min(100, ((now - t0) / 1000) * 5.2) // 약 19초에 완주
      if (p >= 100) {
        setPct(100)
        return // 다음 프레임을 예약하지 않으면 루프가 스스로 멈춘다
      }
      setPct((prev) => (Math.floor(p) !== Math.floor(prev) ? p : prev))
      rafId.current = requestAnimationFrame(tick)
    }
    rafId.current = requestAnimationFrame(tick)
    return () => {
      if (rafId.current) cancelAnimationFrame(rafId.current)
      clearTimeout(copyTimer.current)
    }
  }, [startDone])

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

        <div style={cssToStyle(glowStyle)}></div>

        <svg
          className="pointer-events-none absolute top-0 left-0"
          width="1076"
          height="1024"
          viewBox="0 0 1076 1024"
          style={{ overflow: 'visible' }}
        >
          <defs>
            <marker id="ahR" markerWidth="7" markerHeight="7" refX="5.5" refY="3.5" orient="auto">
              <path d="M0 0.6 L6.4 3.5 L0 6.4 z" fill="rgba(255,190,214,.8)" />
            </marker>
            <marker id="ahRd" markerWidth="7" markerHeight="7" refX="5.5" refY="3.5" orient="auto">
              <path d="M0 0.6 L6.4 3.5 L0 6.4 z" fill="rgba(255,255,255,.38)" />
            </marker>
          </defs>
          {edges.map((e, i) => (
            <Fragment key={i}>
              <path d={e.d} fill="none" stroke={e.stroke} strokeWidth="2.2" />
            </Fragment>
          ))}
          {liveEdges.map((e, i) => (
            <Fragment key={i}>
              <path
                d={e.d}
                fill="none"
                stroke="rgba(255,224,238,.85)"
                strokeWidth="2.2"
                strokeLinecap="round"
                strokeDasharray="1 26"
                style={{ animation: 'dash 1.5s linear infinite' }}
                opacity=".8"
              />
            </Fragment>
          ))}
        </svg>

        {nodes.map((n, i) => (
          <CanvasNode
            key={n.id}
            index={i}
            name={n.name}
            sublabel={n.stateLabel}
            x={n.x}
            y={n.y}
            mine={n.mine}
            animation={n.animation}
            background={n.background}
            numberColor={n.numberColor}
            nameColor={n.nameColor}
            sublabelColor={n.sublabelColor}
            glass
          />
        ))}
      </div>

      <SidePanel>
        <div
          className="absolute text-[23px] font-bold tracking-[-.02em] text-white"
          style={{ left: '30px', top: '36px' }}
        >
          워크플로우 실행
        </div>

        {/* 이전 단계로 돌아가 프롬프트를 수정하여 재실행할 수 있다. */}
        <a
          href={paths.run.waiting.withoutId}
          title="실행 재설정"
          aria-label="실행 재설정"
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
          className="absolute"
          style={{ left: '148px', top: '138px', width: '164px', height: '164px' }}
        >
          <div
            className="absolute rounded-full"
            style={{
              inset: '-18px',
              background:
                'radial-gradient(circle, rgba(255,160,200,.26) 0 46%, rgba(255,160,200,0) 74%)',
              animation: 'runGlow 2.6s ease-in-out infinite',
            }}
          ></div>
          <div className="absolute inset-0 rounded-full" style={{ background: `${gaugeBg}` }}></div>
          <div
            className="absolute rounded-full"
            style={{ inset: '13px', background: 'rgba(14,8,26,.92)' }}
          ></div>
          <div className="absolute inset-0 flex items-center justify-center text-[36px] font-bold tracking-[-.02em] text-white">
            {pctLabel}
          </div>
        </div>

        <div
          className="absolute left-0 text-center text-[19.5px] font-semibold tracking-[-.01em]"
          style={{ top: '330px', width: '460px', color: 'rgba(255,255,255,.9)' }}
        >
          {stepTitle}
        </div>
        <div
          className="absolute left-0 text-center text-[17px]"
          style={{ top: '356px', width: '460px', color: 'rgba(255,236,246,.72)' }}
        >
          {stepMeta}
        </div>

        {running && (
          <>
            <div
              className="absolute text-[17.5px] font-semibold tracking-[-.005em]"
              style={{ left: '30px', top: '420px', color: 'rgba(255,255,255,.94)' }}
            >
              실시간 출력
            </div>
            <div
              className="scroll-thin absolute flex flex-col"
              style={{
                left: '30px',
                top: '452px',
                width: '400px',
                // 패널이 길어진 아래까지 쓴다.
                height: 'calc(558px + 2 * var(--edge-y, 0px))',
                gap: '15px',
              }}
            >
              {logs.map((l, i) => (
                <Fragment key={i}>
                  <div
                    className="flex items-center"
                    style={{ gap: '8px', animation: 'lineIn .34s ease both' }}
                  >
                    <StatusDot size={6} color={l.dot} />
                    <div
                      className="overflow-hidden text-[18px] tracking-[-.005em] whitespace-nowrap"
                      style={{ color: `${l.color}`, textOverflow: 'ellipsis' }}
                    >
                      {l.text}
                    </div>
                    <div style={cssToStyle(l.caret)}></div>
                  </div>
                </Fragment>
              ))}
            </div>
          </>
        )}

        {done && (
          <>
            <div
              className="bg-surface-card absolute"
              style={{
                left: '30px',
                top: '400px',
                width: '400px',
                height: '88px',
                borderRadius: '12px',
                animation: 'uiIn .6s cubic-bezier(.2,.8,.25,1) both',
              }}
            >
              <div
                className="absolute left-0 text-center text-[17px] font-semibold tracking-[-.01em] text-white"
                style={{ top: '20px', width: '400px' }}
              >
                완료되었습니다.
              </div>
              <div
                className="absolute left-0 text-center text-[17px]"
                style={{ top: '48px', width: '400px', color: 'rgba(255,236,246,.76)' }}
              >
                결과를 텍스트 파일로 저장하세요.
              </div>
            </div>

            <div
              style={{
                left: '30px',
                top: '508px',
                width: '400px',
                height: '56px',
                borderRadius: '12px',
                background: 'var(--color-accent)',
                boxShadow: '0 10px 26px rgba(140,100,130,.24),inset 0 1px 0 rgba(255,255,255,.5)',
                gap: '10px',
                transition: 'transform .18s',
                animation: 'uiIn .6s .08s cubic-bezier(.2,.8,.25,1) both',
              }}
              className="absolute flex cursor-pointer items-center justify-center hover:-translate-y-[2px]"
            >
              <svg className="block" width="16" height="17" viewBox="0 0 14 15" fill="none">
                <path
                  d="M6.5 0V9.5M2 6L6.5 10.5L11 6M0 13.5H13"
                  style={{ stroke: 'var(--color-accent-ink)' }}
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
              <div
                className="text-[17px] font-bold tracking-[-.01em]"
                style={{ color: 'var(--color-accent-ink)' }}
              >
                결과 다운로드 (.txt)
              </div>
            </div>

            <a
              href={paths.edit.nodes.withoutId}
              style={{
                left: '30px',
                top: '576px',
                width: '193px',
                height: '56px',
                borderRadius: '12px',
                boxShadow: 'inset 0 0 0 1px rgba(255,255,255,.18)',
                gap: '8px',
                transition: 'background .18s',
                animation: 'uiIn .6s .14s cubic-bezier(.2,.8,.25,1) both',
              }}
              className="bg-surface-card absolute flex cursor-pointer items-center justify-center hover:bg-[rgba(255,255,255,.16)]"
            >
              <svg className="block" width="15" height="15" viewBox="0 0 14 14" fill="none">
                <path
                  d="M0 12.5L1.5 8L9.5 0L12.5 3L4.5 11L0 12.5ZM0 12.5L3.7 11.8"
                  stroke="rgba(255,255,255,.75)"
                  strokeWidth="1.4"
                  strokeLinejoin="round"
                />
              </svg>
              <div
                className="text-[15px] font-semibold tracking-[-.01em] whitespace-nowrap"
                style={{ color: 'rgba(255,255,255,.9)' }}
              >
                수정하기
              </div>
            </a>

            {/* 워크플로우 전체 실행에 성공해야 마켓에 판매 등록 가능. */}
            <a
              {...(canSell ? { href: paths.register } : {})}
              title={
                canSell ? undefined : '다른 사람의 에이전트가 섞여 있어 마켓에 등록할 수 없습니다'
              }
              aria-disabled={!canSell}
              style={{
                left: '237px',
                top: '576px',
                width: '193px',
                height: '56px',
                borderRadius: '12px',
                // 흐릿하게 하되 opacity 는 쓰지 않는다.
                boxShadow: `inset 0 0 0 1px rgba(255,255,255,${canSell ? '.18' : '.08'})`,
                gap: '8px',
                cursor: canSell ? 'pointer' : 'not-allowed',
                transition: 'background .18s',
                animation: 'uiIn .6s .2s cubic-bezier(.2,.8,.25,1) both',
              }}
              className={
                canSell
                  ? 'bg-surface-card absolute flex items-center justify-center hover:bg-[rgba(255,255,255,.16)]'
                  : 'absolute flex items-center justify-center bg-[rgba(255,255,255,.04)]'
              }
            >
              <svg className="block" width="15" height="15" viewBox="0 0 14 14" fill="none">
                <path
                  d="M7.4 0.6H12.3A1.1 1.1 0 0 1 13.4 1.7V6.6L6.6 13.4L0.6 7.4L7.4 0.6Z"
                  stroke={canSell ? 'rgba(255,255,255,.75)' : 'rgba(255,255,255,.3)'}
                  strokeWidth="1.4"
                  strokeLinejoin="round"
                />
                <circle
                  cx="10.2"
                  cy="3.8"
                  r="1"
                  fill={canSell ? 'rgba(255,255,255,.75)' : 'rgba(255,255,255,.3)'}
                />
              </svg>
              <div
                className="text-[15px] font-semibold tracking-[-.01em] whitespace-nowrap"
                style={{ color: canSell ? 'rgba(255,255,255,.9)' : 'rgba(255,255,255,.38)' }}
              >
                판매 등록
              </div>
            </a>

            <div
              className="absolute text-[17.5px] font-semibold tracking-[-.005em]"
              style={{ left: '30px', top: '660px', color: 'rgba(255,255,255,.94)' }}
            >
              출력 결과
            </div>
            <div
              onClick={handleCopyResult}
              style={{
                // 라벨이 '복사'/'복사됨' 으로 바뀌어 폭이 달라지므로 결과 상자의 오른쪽 끝에 맞춘다.
                right: '30px',
                top: '655px',
                height: '26px',
                padding: '0 11px',
                borderRadius: '8px',
                background: `${copyBg}`,
                gap: '6px',
              }}
              className="absolute flex cursor-pointer items-center transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.18)]"
            >
              <svg
                width="12"
                height="12"
                viewBox="0 0 12 12"
                fill="none"
                stroke={copyFg}
                strokeWidth="1.3"
                strokeLinejoin="round"
              >
                <rect x="3.6" y="3.6" width="7.4" height="7.4" rx="1.4" />
                <path d="M8.4 1H2.4A1.4 1.4 0 0 0 1 2.4v6" />
              </svg>
              <div
                className="text-[13.5px] font-semibold tracking-[-.005em] whitespace-nowrap"
                style={{ color: `${copyFg}` }}
              >
                {copyLabel}
              </div>
            </div>
            <div
              className="absolute box-border overflow-x-hidden"
              id="resultBox"
              style={{
                left: '30px',
                top: '692px',
                width: '400px',
                height: 'calc(318px + 2 * var(--edge-y, 0px))',
                padding: '16px',
                borderRadius: '12px',
                background: 'var(--color-surface-quiet)',
                overflowY: 'auto',
                userSelect: 'text',
                WebkitUserSelect: 'text',
                cursor: 'text',
                scrollbarWidth: 'thin',
                scrollbarColor: 'rgba(255,255,255,.28) transparent',
                animation: 'uiIn .6s .2s cubic-bezier(.2,.8,.25,1) both',
              }}
            >
              <div className="flex flex-col" style={{ width: '100%', gap: '9px' }}>
                {resultLines.map((r, i) => (
                  <Fragment key={i}>
                    <div
                      className="text-[17px] tracking-[-.005em]"
                      style={{
                        lineHeight: '1.55',
                        color: `${r.color}`,
                        whiteSpace: 'pre-wrap',
                        wordBreak: 'break-word',
                      }}
                    >
                      {r.text}
                    </div>
                  </Fragment>
                ))}
              </div>
            </div>
          </>
        )}
      </SidePanel>

      {copied && (
        <>
          <div
            className="pointer-events-none absolute flex items-center justify-center"
            style={{
              // 결과 상자 위에 겹쳐 토스트를 띄운다. 상자와 같은 기준을 써야 한다.
              right: 'calc(44px - var(--edge, 0px))',
              top: '706px',
              width: '372px',
              height: '46px',
              justifyContent: 'flex-end',
              zIndex: '6',
            }}
          >
            <div
              className="flex items-center"
              style={{
                height: '46px',
                padding: '0 22px',
                borderRadius: '23px',
                background: 'rgba(24,11,38,.82)',
                backdropFilter: 'blur(20px) saturate(120%)',
                WebkitBackdropFilter: 'blur(20px) saturate(120%)',
                boxShadow: '0 16px 44px rgba(10,4,20,.42)',
                gap: '11px',
                animation: 'toastIn 1.8s ease both',
              }}
            >
              <div
                className="flex flex-none items-center justify-center rounded-full"
                style={{ width: '22px', height: '22px', background: 'rgba(154,208,180,.24)' }}
              >
                <svg
                  width="12"
                  height="10"
                  viewBox="0 0 12 10"
                  fill="none"
                  stroke="#a8ddc0"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d="M1 5.2 4.3 8.5 11 1.5" />
                </svg>
              </div>
              <div className="text-[16px] font-semibold tracking-[-.01em] whitespace-nowrap text-white">
                복사 완료
              </div>
            </div>
          </div>
        </>
      )}
    </>
  )
}
