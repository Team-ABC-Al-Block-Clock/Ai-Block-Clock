import { Fragment, useState } from 'react'
import { cssToStyle } from '../lib/css'
import { paths } from '@/routes/paths'

/** 노드 편집 화면. 판매자 흐름(/edit/nodes-seller)은 문구와 하단 버튼만 다릅니다. */
interface EditNodesProps {
  /** 판매자 흐름 — 문구와 하단 버튼이 달라집니다 */
  seller?: boolean
}

export default function EditNodes({ seller = false }: EditNodesProps = {}) {
  const [sel, setSel] = useState(1)
  const [hov, setHov] = useState(-1)
  const [panelOpen, setPanelOpen] = useState(false)
  const [infoAt, setInfoAt] = useState<number | null>(seller ? null : 1)

  const nodes = [
    { name: '파일 수집', kind: '트리거', x: 38, y: 400 },
    { name: '내용 분석', kind: 'LLM', x: 246, y: 320 },
    { name: '분류 및 태깅', kind: '도구', x: 454, y: 440 },
    { name: '요약 생성', kind: 'LLM', x: 662, y: 330 },
    { name: '결과 전달', kind: '출력', x: 870, y: 430 },
  ]
  const pick = (i: number) => () => setSel(sel === i ? -1 : i)
  const hover = (i: number) => () => setHov(i)
  const handleHovOut = () => setHov(-1)
  const handleTogglePanel = () => setPanelOpen((v) => !v)
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
  const n = nodes[sel]
  const g = nodes[hov] || n // 빛은 호버 중인 노드를 따라간다
  const selRing = g
    ? 'position:absolute;left:' +
      (g.x - 16) +
      'px;top:' +
      (g.y - 16) +
      'px;width:200px;height:200px;border-radius:50%;pointer-events:none;opacity:' +
      (hov >= 0 && hov !== sel ? '.6' : '1') +
      ';transform:translateY(' +
      (hov >= 0 ? -5 : 0) +
      'px);background:radial-gradient(circle, rgba(255,160,200,.30) 0 42%, rgba(255,160,200,.14) 62%, rgba(255,160,200,0) 76%);transition:left .34s cubic-bezier(.2,.8,.25,1),top .34s cubic-bezier(.2,.8,.25,1),transform .2s,opacity .25s ease'
    : 'display:none'
  const results = names.map((name, i) => ({
    name,
    i,
    initial: name.trim().charAt(0),
    uses: (820 + i * 143).toLocaleString() + '회',
    credit: 40 + (i % 5) * 25 + ' Credit',
    kind: ['LLM', '도구', '출력'][i % 3],
    infoOpen: infoAt === i,
    infoBg: infoAt === i ? 'rgba(255,255,255,.34)' : 'rgba(255,255,255,.18)',
    info: (ev: React.MouseEvent) => {
      ev.preventDefault()
      ev.stopPropagation()
      setInfoAt((v) => (v === i ? null : i))
    },
  }))
  const nodeList = nodes.map((d, i) => ({ name: d.name, i }))
  const handleSel1 = pick(0)
  const handleSel2 = pick(1)
  const handleSel3 = pick(2)
  const handleSel4 = pick(3)
  const handleSel5 = pick(4)
  const handleHov1 = hover(0)
  const handleHov2 = hover(1)
  const handleHov3 = hover(2)
  const handleHov4 = hover(3)
  const handleHov5 = hover(4)
  const hasSelection = !!n
  const noSelection = !n
  const selName = n ? n.name : ''
  const selInitial = n ? n.name.trim().charAt(0) : ''
  const selKind = n ? n.kind : ''

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
            right: 'calc(36px - var(--edge, 0px))',
            top: 0,
            bottom: 0,
            backgroundImage: 'radial-gradient(rgba(255,255,255,.10) 1px, transparent 1px)',
            backgroundSize: '26px 26px',
            backgroundPosition: '13px 13px',
            opacity: '.55',
          }}
        ></div>
        <div
          className="pointer-events-none absolute"
          style={{
            left: 'calc(-1 * var(--edge, 0px))',
            right: 'calc(36px - var(--edge, 0px))',
            top: 0,
            bottom: 0,
            background:
              'linear-gradient(90deg, rgba(10,5,22,0) 30%, rgba(10,5,22,.26) 70%, rgba(10,5,22,.32) 100%)',
          }}
        ></div>

        <div
          className="absolute left-0 text-center text-[14px] tracking-[-.01em]"
          style={{
            top: '104px',
            width: '1076px',
            color: 'rgba(255,236,246,.62)',
            animation: 'uiIn .8s .5s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          {seller
            ? '노드를 끌어 연결하고, 테스트한 뒤 마켓에 등록하세요'
            : '노드를 끌어 연결하고, 선택한 노드를 오른쪽에서 편집합니다'}
        </div>

        <svg
          className="pointer-events-none absolute top-0 left-0"
          width="1076"
          height="1024"
          viewBox="0 0 1076 1024"
          style={{ overflow: 'visible' }}
        >
          <defs>
            <linearGradient id="edgeOn" x1="0" y1="0" x2="1" y2="0">
              <stop offset="0%" stopColor="#e3b7c8" stopOpacity=".9" />
              <stop offset="100%" stopColor="#dba18b" stopOpacity=".8" />
            </linearGradient>
            <marker id="ah" markerWidth="7" markerHeight="7" refX="5.5" refY="3.5" orient="auto">
              <path d="M0 0.6 L6.4 3.5 L0 6.4 z" fill="rgba(255,190,214,.8)" />
            </marker>
          </defs>
          <path
            d="M206 484 C 226 484, 226 404, 246 404"
            fill="none"
            stroke="url(#edgeOn)"
            strokeWidth="2.2"
          />
          <path
            d="M206 484 C 226 484, 226 404, 246 404"
            fill="none"
            stroke="rgba(255,224,238,.85)"
            strokeWidth="2.2"
            strokeLinecap="round"
            strokeDasharray="1 26"
            style={{ animation: 'dash 1.4s linear infinite' }}
            opacity=".8"
          />
          <path
            d="M414 404 C 434 404, 434 524, 454 524"
            fill="none"
            stroke="url(#edgeOn)"
            strokeWidth="2.2"
          />
          <path
            d="M414 404 C 434 404, 434 524, 454 524"
            fill="none"
            stroke="rgba(255,224,238,.85)"
            strokeWidth="2.2"
            strokeLinecap="round"
            strokeDasharray="1 26"
            style={{ animation: 'dash 1.5s .2s linear infinite' }}
            opacity=".8"
          />
          <path
            d="M622 524 C 642 524, 642 414, 662 414"
            fill="none"
            stroke="url(#edgeOn)"
            strokeWidth="2.2"
          />
          <path
            d="M622 524 C 642 524, 642 414, 662 414"
            fill="none"
            stroke="rgba(255,224,238,.85)"
            strokeWidth="2.2"
            strokeLinecap="round"
            strokeDasharray="1 26"
            style={{ animation: 'dash 1.6s .4s linear infinite' }}
            opacity=".8"
          />
          <path
            d="M830 414 C 850 414, 850 514, 870 514"
            fill="none"
            stroke="url(#edgeOn)"
            strokeWidth="2.2"
          />
          <path
            d="M830 414 C 850 414, 850 514, 870 514"
            fill="none"
            stroke="rgba(255,224,238,.85)"
            strokeWidth="2.2"
            strokeLinecap="round"
            strokeDasharray="1 26"
            style={{ animation: 'dash 1.7s .6s linear infinite' }}
            opacity=".8"
          />
        </svg>

        <div style={cssToStyle(selRing)}></div>

        <div
          onClick={handleSel1}
          onMouseEnter={handleHov1}
          onMouseLeave={handleHovOut}
          style={{
            left: '38px',
            top: '400px',
            width: '168px',
            height: '168px',
            background: 'rgba(255,255,255,.11)',
            gap: '5px',
            transition: 'transform .2s,box-shadow .2s',
            animation:
              'floaty 6.4s ease-in-out infinite, uiIn .7s .18s cubic-bezier(.2,.8,.25,1) both',
          }}
          className="absolute flex cursor-pointer flex-col items-center justify-center rounded-full hover:-translate-y-[5px]"
        >
          <div
            className="text-[26px] font-bold text-white"
            style={{ lineHeight: '1', textShadow: '0 2px 12px rgba(8,4,18,.6)' }}
          >
            1
          </div>
          <div className="text-[16px] font-semibold tracking-[-.01em] text-white">파일 수집</div>
          <div
            className="text-[12.5px]"
            style={{ color: 'rgba(228,190,172,.82)', letterSpacing: '.06em' }}
          >
            트리거
          </div>
        </div>

        <div
          onClick={handleSel2}
          onMouseEnter={handleHov2}
          onMouseLeave={handleHovOut}
          style={{
            left: '246px',
            top: '320px',
            width: '168px',
            height: '168px',
            background: 'rgba(255,255,255,.16)',
            gap: '5px',
            transition: 'transform .2s,box-shadow .2s',
            animation:
              'floaty 6.9s .3s ease-in-out infinite, uiIn .7s .26s cubic-bezier(.2,.8,.25,1) both',
          }}
          className="absolute flex cursor-pointer flex-col items-center justify-center rounded-full hover:-translate-y-[5px]"
        >
          <div
            className="text-[26px] font-bold text-white"
            style={{ lineHeight: '1', textShadow: '0 2px 12px rgba(8,4,18,.6)' }}
          >
            2
          </div>
          <div className="text-[16px] font-semibold tracking-[-.01em] text-white">내용 분석</div>
          <div
            className="text-[12.5px]"
            style={{ color: 'rgba(230,196,212,.85)', letterSpacing: '.06em' }}
          >
            LLM
          </div>
        </div>

        <div
          onClick={handleSel3}
          onMouseEnter={handleHov3}
          onMouseLeave={handleHovOut}
          style={{
            left: '454px',
            top: '440px',
            width: '168px',
            height: '168px',
            background: 'rgba(255,255,255,.11)',
            gap: '5px',
            transition: 'transform .2s,box-shadow .2s',
            animation:
              'floaty 7.1s .8s ease-in-out infinite, uiIn .7s .34s cubic-bezier(.2,.8,.25,1) both',
          }}
          className="absolute flex cursor-pointer flex-col items-center justify-center rounded-full hover:-translate-y-[5px]"
        >
          <div
            className="text-[26px] font-bold text-white"
            style={{ lineHeight: '1', textShadow: '0 2px 12px rgba(8,4,18,.6)' }}
          >
            3
          </div>
          <div className="text-[16px] font-semibold tracking-[-.01em] text-white">분류 및 태깅</div>
          <div
            className="text-[12.5px]"
            style={{ color: 'rgba(226,211,180,.8)', letterSpacing: '.06em' }}
          >
            도구
          </div>
        </div>

        <div
          onClick={handleSel4}
          onMouseEnter={handleHov4}
          onMouseLeave={handleHovOut}
          style={{
            left: '662px',
            top: '330px',
            width: '168px',
            height: '168px',
            background: 'rgba(255,255,255,.11)',
            gap: '5px',
            transition: 'transform .2s,box-shadow .2s',
            animation:
              'floaty 6.8s .4s ease-in-out infinite, uiIn .7s .42s cubic-bezier(.2,.8,.25,1) both',
          }}
          className="absolute flex cursor-pointer flex-col items-center justify-center rounded-full hover:-translate-y-[5px]"
        >
          <div
            className="text-[26px] font-bold text-white"
            style={{ lineHeight: '1', textShadow: '0 2px 12px rgba(8,4,18,.6)' }}
          >
            4
          </div>
          <div className="text-[16px] font-semibold tracking-[-.01em] text-white">요약 생성</div>
          <div
            className="text-[12.5px]"
            style={{ color: 'rgba(230,196,212,.82)', letterSpacing: '.06em' }}
          >
            LLM
          </div>
        </div>

        <div
          onClick={handleSel5}
          onMouseEnter={handleHov5}
          onMouseLeave={handleHovOut}
          style={{
            left: '870px',
            top: '430px',
            width: '168px',
            height: '168px',
            background: 'rgba(255,255,255,.11)',
            gap: '5px',
            transition: 'transform .2s,box-shadow .2s',
            animation:
              'floaty 7.6s 1.2s ease-in-out infinite, uiIn .7s .5s cubic-bezier(.2,.8,.25,1) both',
          }}
          className="absolute flex cursor-pointer flex-col items-center justify-center rounded-full hover:-translate-y-[5px]"
        >
          <div
            className="text-[26px] font-bold text-white"
            style={{ lineHeight: '1', textShadow: '0 2px 12px rgba(8,4,18,.6)' }}
          >
            5
          </div>
          <div className="text-[16px] font-semibold tracking-[-.01em] text-white">결과 전달</div>
          <div
            className="text-[12.5px]"
            style={{ color: 'rgba(190,183,222,.82)', letterSpacing: '.06em' }}
          >
            출력
          </div>
        </div>

        <div
          className="absolute flex items-center overflow-hidden"
          style={{
            left: 'calc(32px - var(--edge, 0px))',
            top: '952px',
            height: '36px',
            borderRadius: '18px',
            background: 'rgba(14,7,28,.55)',
            backdropFilter: 'blur(18px)',
            WebkitBackdropFilter: 'blur(18px)',
            animation: 'uiIn .7s .56s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          <div
            style={{ width: '41px', height: '36px', color: 'rgba(255,255,255,.82)' }}
            className="flex cursor-pointer items-center justify-center text-[15px] font-semibold hover:bg-[rgba(255,255,255,.12)]"
          >
            −
          </div>
          <div
            className="flex items-center justify-center text-[14px] font-semibold"
            style={{ width: '67px', height: '36px', color: 'rgba(255,255,255,.82)' }}
          >
            100%
          </div>
          <div
            style={{ width: '41px', height: '36px', color: 'rgba(255,255,255,.82)' }}
            className="flex cursor-pointer items-center justify-center text-[15px] font-semibold hover:bg-[rgba(255,255,255,.12)]"
          >
            +
          </div>
          <div style={{ width: '1px', height: '22px', background: 'rgba(255,255,255,.20)' }}></div>
          <div
            onClick={handleTogglePanel}
            style={{ height: '36px', padding: '0 16px', gap: '7px' }}
            className="flex cursor-pointer items-center text-[14px] font-semibold text-white hover:bg-[rgba(255,255,255,.12)]"
          >
            <span>+</span>
            <span>에이전트 추가</span>
          </div>
        </div>

        {panelOpen && (
          <>
            <div
              className="absolute box-border flex flex-col"
              style={{
                left: 'calc(32px - var(--edge, 0px))',
                top: '262px',
                width: '500px',
                height: '700px',
                borderRadius: '14px',
                background: 'rgba(20,11,36,.88)',
                backdropFilter: 'blur(28px) saturate(125%)',
                WebkitBackdropFilter: 'blur(28px) saturate(125%)',
                boxShadow: 'inset 0 1px 0 rgba(255,255,255,.18),0 26px 60px rgba(4,2,12,.6)',
                padding: '20px',
                animation: 'panelPop .34s cubic-bezier(.2,.9,.3,1) both',
                transformOrigin: '20% 100%',
              }}
            >
              <div
                className="flex flex-none items-center justify-between"
                style={{ marginBottom: '16px' }}
              >
                <div className="text-[20px] font-semibold tracking-[-.01em] text-white">
                  에이전트 추가
                </div>
                <div
                  onClick={handleTogglePanel}
                  style={{
                    width: '22px',
                    height: '22px',
                    borderRadius: '6px',
                    color: 'rgba(255,255,255,.6)',
                  }}
                  className="flex cursor-pointer items-center justify-center text-[16px] hover:bg-[rgba(255,255,255,.14)] hover:text-[#fff]"
                >
                  ×
                </div>
              </div>

              <div
                className="flex-1 overflow-x-hidden"
                id="addPanelFields"
                style={{
                  minHeight: '0',
                  overflowY: 'auto',
                  paddingRight: '4px',
                  scrollbarWidth: 'thin',
                  scrollbarColor: 'rgba(255,255,255,.26) transparent',
                }}
              >
                <div
                  className="text-[17px]"
                  style={{ color: 'rgba(255,214,232,.85)', marginBottom: '8px' }}
                >
                  에이전트 이름
                </div>
                <div
                  className="bg-surface-card flex items-center"
                  style={{
                    height: '44px',
                    borderRadius: '11px',
                    padding: '0 12px',
                    marginBottom: '14px',
                  }}
                >
                  <input
                    className="border-0 bg-transparent font-[inherit] text-[17px] text-white outline-0"
                    type="text"
                    placeholder="예: 문서 정리 봇"
                    style={{ width: '100%' }}
                  />
                </div>

                <div
                  className="text-[17px]"
                  style={{ color: 'rgba(255,214,232,.85)', marginBottom: '8px' }}
                >
                  입력 파일
                </div>
                <div
                  className="bg-surface-card flex items-center justify-between"
                  style={{
                    height: '44px',
                    borderRadius: '11px',
                    padding: '0 6px 0 12px',
                    marginBottom: '14px',
                  }}
                >
                  <div className="text-[17px]" style={{ color: 'rgba(255,255,255,.5)' }}>
                    파일을 선택하세요
                  </div>
                  <div
                    style={{
                      height: '30px',
                      padding: '0 14px',
                      borderRadius: '8px',
                      background: 'rgba(255,255,255,.16)',
                    }}
                    className="flex cursor-pointer items-center text-[12.5px] font-semibold text-white hover:bg-[rgba(255,255,255,.28)]"
                  >
                    찾기
                  </div>
                </div>

                <div
                  className="text-[17px]"
                  style={{ color: 'rgba(255,214,232,.85)', marginBottom: '8px' }}
                >
                  AI 모델
                </div>
                <div
                  className="bg-surface-card flex items-center justify-between"
                  style={{
                    height: '44px',
                    borderRadius: '11px',
                    padding: '0 12px',
                    marginBottom: '14px',
                  }}
                >
                  <div className="text-[17px] text-white">기본 모델</div>
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
                  className="text-[17px] font-semibold"
                  style={{ color: 'rgba(255,214,232,.85)', marginBottom: '8px' }}
                >
                  프롬프트
                </div>
                <div
                  className="bg-surface-card"
                  style={{
                    height: '150px',
                    borderRadius: '9px',
                    padding: '11px 12px',
                    marginBottom: '14px',
                  }}
                >
                  <textarea
                    className="border-0 bg-transparent font-[inherit] text-[17px] text-white outline-0"
                    placeholder="에이전트에게 전달할 프롬프트를 입력하세요&#10;예: 이 문서를 분석하여 핵심 내용을 요약해 주세요."
                    style={{ width: '100%', height: '128px', resize: 'none', lineHeight: '1.5' }}
                  ></textarea>
                </div>

                <div
                  className="text-[17px]"
                  style={{ color: 'rgba(255,214,232,.85)', marginBottom: '10px' }}
                >
                  제어 흐름 노드
                </div>
                <div className="flex" style={{ gap: '8px', marginBottom: '2px' }}>
                  <div
                    style={{ height: '36px', padding: '0 13px', borderRadius: '10px' }}
                    className="bg-surface-card flex cursor-pointer items-center text-[12.5px] font-semibold whitespace-nowrap text-white hover:bg-[rgba(255,255,255,.2)]"
                  >
                    조건 분기 (if / else)
                  </div>
                  <div
                    style={{ height: '36px', padding: '0 15px', borderRadius: '10px' }}
                    className="bg-surface-card flex cursor-pointer items-center text-[12.5px] font-semibold text-white hover:bg-[rgba(255,255,255,.2)]"
                  >
                    반복
                  </div>
                  <div
                    style={{ height: '36px', padding: '0 15px', borderRadius: '10px' }}
                    className="bg-surface-card flex cursor-pointer items-center text-[12.5px] font-semibold text-white hover:bg-[rgba(255,255,255,.2)]"
                  >
                    병합
                  </div>
                </div>
              </div>

              <div
                className="flex flex-none"
                style={{ justifyContent: 'flex-end', gap: '8px', paddingTop: '14px' }}
              >
                <div
                  onClick={handleTogglePanel}
                  style={{
                    height: '40px',
                    padding: '0 22px',
                    borderRadius: '11px',
                    boxShadow: 'inset 0 0 0 1px rgba(255,255,255,.18)',
                    color: 'rgba(255,255,255,.9)',
                  }}
                  className="bg-surface-card flex cursor-pointer items-center text-[17px] font-semibold hover:bg-[rgba(255,255,255,.18)]"
                >
                  취소
                </div>
                <div
                  onClick={handleTogglePanel}
                  style={{
                    height: '40px',
                    padding: '0 24px',
                    borderRadius: '11px',
                    background: '#b98da9',
                    boxShadow: '0 8px 20px rgba(140,100,130,.24)',
                    color: '#2f1527',
                  }}
                  className="flex cursor-pointer items-center text-[17px] font-bold hover:brightness-[1.05]"
                >
                  추가
                </div>
              </div>
            </div>
          </>
        )}

        {seller && (
          <a
            href={paths.run.waiting.withoutId}
            style={{
              left: 'calc(674px + var(--edge, 0px))',
              top: '943px',
              width: '160px',
              height: '52px',
              borderRadius: '26px',
              boxShadow: 'inset 0 0 0 1px rgba(255,255,255,.22)',
              color: 'rgba(255,255,255,.92)',
              textDecoration: 'none',
              transition: 'background .18s,transform .18s',
              animation: 'uiIn .7s .58s cubic-bezier(.2,.8,.25,1) both',
            }}
            className="absolute flex cursor-pointer items-center justify-center bg-[rgba(255,255,255,.10)] text-[17px] font-semibold tracking-[-.01em] hover:-translate-y-[2px] hover:bg-[rgba(255,255,255,.2)]"
          >
            테스트하기
          </a>
        )}
        <a
          href={seller ? paths.register : paths.run.waiting.withoutId}
          style={{
            left: 'calc(846px + var(--edge, 0px))',
            top: '943px',
            width: '160px',
            height: '52px',
            borderRadius: '26px',
            background: '#b98da9',
            boxShadow: '0 10px 26px rgba(140,100,130,.24),inset 0 1px 0 rgba(255,255,255,.6)',
            color: '#2f1527',
            transition: 'transform .18s,box-shadow .18s',
            animation: 'uiIn .7s .62s cubic-bezier(.2,.8,.25,1) both',
          }}
          className="absolute flex cursor-pointer items-center justify-center text-[17px] font-bold tracking-[-.01em] hover:-translate-y-[2px] hover:shadow-[0_14px_32px_rgba(140,100,130,.28),inset_0_1px_0_rgba(255,255,255,.6)]"
        >
          {seller ? '마켓에 등록' : '실행하기'}
        </a>
      </div>

      <div
        className="absolute top-0"
        style={{
          right: 'calc(-1 * var(--edge, 0px))',
          width: '400px',
          height: '1024px',
          boxShadow: 'inset 1px 0 0 rgba(255,255,255,.14),-30px 0 60px rgba(10,4,20,.35)',
          animation: 'sideIn 1s .1s cubic-bezier(.18,.86,.24,1) both',
        }}
      >
        <div
          className="absolute bg-[rgba(255,255,255,.10)]"
          style={{
            left: '30px',
            top: '30px',
            width: '340px',
            height: '40px',
            borderRadius: '10px',
          }}
        >
          <div
            className="absolute rounded-full"
            style={{
              left: '14px',
              top: '13px',
              width: '14px',
              height: '14px',
              boxShadow: 'inset 0 0 0 2px rgba(255,255,255,.7)',
            }}
          ></div>
          <div
            className="absolute"
            style={{
              left: '25px',
              top: '25px',
              width: '7px',
              height: '2px',
              borderRadius: '1px',
              background: 'rgba(255,255,255,.7)',
              transform: 'rotate(45deg)',
            }}
          ></div>
          <input
            className="absolute border-0 bg-transparent font-[inherit] text-[14px] text-white outline-0"
            type="text"
            placeholder="에이전트 검색"
            style={{ left: '38px', top: '9px', width: '250px', height: '22px' }}
          />
        </div>
        {hasSelection && (
          <>
            <div
              className="absolute text-[15px] font-semibold tracking-[-.01em] text-white"
              style={{ left: '30px', top: '90px' }}
            >
              선택한 노드
            </div>
            <div
              className="absolute"
              style={{
                left: '30px',
                top: '118px',
                width: '340px',
                height: '92px',
                borderRadius: '12px',
                background: 'rgba(255,255,255,.11)',
              }}
            >
              <div
                className="absolute overflow-hidden bg-[rgba(255,255,255,.10)]"
                style={{
                  left: '16px',
                  top: '24px',
                  width: '44px',
                  height: '44px',
                  borderRadius: '10px',
                }}
              >
                <div
                  className="absolute inset-0 flex items-center justify-center text-[16px] font-semibold"
                  style={{ color: 'rgba(255,226,238,.65)' }}
                >
                  {selInitial}
                </div>
              </div>
              <div
                className="absolute text-[16px] font-semibold tracking-[-.01em] text-white"
                style={{ left: '76px', top: '29px' }}
              >
                {selName}
              </div>
              <div
                className="absolute text-[13px]"
                style={{ left: '76px', top: '52px', color: 'rgba(255,214,232,.8)' }}
              >
                {selKind} 노드
              </div>
            </div>

            <div
              className="absolute text-[14px] font-semibold"
              style={{ left: '30px', top: '234px', color: 'rgba(255,255,255,.82)' }}
            >
              노드 개요
              <br />
            </div>
            <div
              className="absolute"
              style={{
                left: '30px',
                top: '258px',
                width: '340px',
                height: '120px',
                borderRadius: '10px',
                background: 'rgba(255,255,255,.07)',
              }}
            >
              <div
                className="absolute"
                style={{
                  left: '14px',
                  top: '16px',
                  width: '264px',
                  height: '10px',
                  borderRadius: '3px',
                  background: 'rgba(255,255,255,.16)',
                }}
              ></div>
              <div
                className="absolute"
                style={{
                  left: '14px',
                  top: '38px',
                  width: '240px',
                  height: '10px',
                  borderRadius: '3px',
                  background: 'rgba(255,255,255,.13)',
                }}
              ></div>
              <div
                className="absolute"
                style={{
                  left: '14px',
                  top: '60px',
                  width: '252px',
                  height: '10px',
                  borderRadius: '3px',
                  background: 'rgba(255,255,255,.13)',
                }}
              ></div>
              <div
                className="absolute bg-[rgba(255,255,255,.10)]"
                style={{
                  left: '14px',
                  top: '82px',
                  width: '176px',
                  height: '10px',
                  borderRadius: '3px',
                }}
              ></div>
            </div>

            <div
              className="absolute text-[14px] font-semibold"
              style={{ left: '30px', top: '398px', color: 'rgba(255,255,255,.82)' }}
            >
              모델
            </div>
            <div
              className="absolute"
              style={{
                left: '30px',
                top: '422px',
                width: '340px',
                height: '44px',
                borderRadius: '10px',
                background: 'rgba(255,255,255,.07)',
              }}
            >
              <div
                className="absolute text-[14px]"
                style={{ left: '14px', top: '14px', color: 'rgba(255,255,255,.8)' }}
              >
                기본 모델
              </div>
              <div
                className="absolute"
                style={{
                  left: '310px',
                  top: '20px',
                  width: '0',
                  height: '0',
                  borderLeft: '5px solid transparent',
                  borderRight: '5px solid transparent',
                  borderTop: '6px solid rgba(255,255,255,.6)',
                }}
              ></div>
            </div>

            <div
              className="absolute text-[15px] font-semibold tracking-[-.01em] text-white"
              style={{ left: '30px', top: '496px' }}
            >
              노드 목록
            </div>
            <div
              className="absolute flex flex-col"
              style={{ left: '30px', top: '524px', width: '340px', gap: '10px' }}
            >
              {nodeList.map((row, i) => (
                <Fragment key={i}>
                  <div
                    style={{
                      width: '340px',
                      height: '56px',
                      borderRadius: '10px',
                      background: 'rgba(255,255,255,.07)',
                    }}
                    className="relative cursor-pointer transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.15)]"
                  >
                    <div
                      className="absolute bg-[rgba(255,255,255,.10)]"
                      style={{
                        left: '14px',
                        top: '14px',
                        width: '28px',
                        height: '28px',
                        borderRadius: '8px',
                      }}
                    ></div>
                    <div
                      className="absolute text-[14px] font-semibold"
                      style={{ left: '54px', top: '19px', color: 'rgba(255,255,255,.92)' }}
                    >
                      {row.name}
                    </div>
                    <div
                      className="absolute"
                      style={{
                        left: '310px',
                        top: '27px',
                        width: '14px',
                        height: '2px',
                        borderRadius: '1px',
                        background: 'rgba(255,255,255,.42)',
                      }}
                    ></div>
                  </div>
                </Fragment>
              ))}
            </div>

            <div
              style={{
                left: '30px',
                top: '870px',
                width: '340px',
                height: '48px',
                borderRadius: '10px',
                background: 'rgba(255,255,255,.06)',
                boxShadow: 'inset 0 0 0 1px rgba(255,255,255,.16)',
                color: 'rgba(255,255,255,.85)',
              }}
              className="absolute flex cursor-pointer items-center justify-center text-[15px] font-semibold hover:bg-[rgba(255,255,255,.14)]"
            >
              + 노드 추가
            </div>
          </>
        )}

        {noSelection && (
          <>
            <div
              className="absolute text-[15px] font-semibold tracking-[-.01em] text-white"
              style={{ left: '30px', top: '88px' }}
            >
              검색 결과
            </div>
            <div
              className="absolute text-right text-[13px]"
              style={{ left: '266px', top: '90px', width: '68px', color: 'rgba(255,236,246,.6)' }}
            >
              10개
            </div>
            <div
              className="absolute flex flex-wrap"
              style={{
                left: '30px',
                top: '116px',
                width: '340px',
                height: '876px',
                gap: '14px',
                alignContent: 'flex-start',
              }}
            >
              {results.map((item, i) => (
                <Fragment key={i}>
                  <div
                    style={{
                      width: '145px',
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
                      className="absolute text-[14px] font-semibold tracking-[-.01em] text-white"
                      style={{ left: '16px', top: '76px' }}
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
                    <div
                      onClick={item.info}
                      title="에이전트 정보"
                      style={{
                        left: '105px',
                        top: '12px',
                        width: '24px',
                        height: '24px',
                        borderRadius: '12px',
                        background: `${item.infoBg}`,
                        zIndex: '2',
                      }}
                      className="absolute flex cursor-pointer items-center justify-center text-[13px] font-bold text-white transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.34)]"
                    >
                      i
                    </div>
                    {item.infoOpen && (
                      <>
                        <div
                          className="absolute top-0 left-0 box-border flex cursor-pointer flex-col"
                          onClick={item.info}
                          style={{
                            width: '145px',
                            height: '160px',
                            padding: '14px',
                            borderRadius: '12px',
                            background: 'rgba(20,11,36,.94)',
                            backdropFilter: 'blur(20px) saturate(120%)',
                            WebkitBackdropFilter: 'blur(20px) saturate(120%)',
                            gap: '7px',
                            animation: 'fadeIn .16s ease both',
                            zIndex: '3',
                          }}
                        >
                          <div className="text-[13px] font-semibold tracking-[-.01em] text-white">
                            {item.name}
                          </div>
                          <div className="flex justify-between text-[12px]">
                            <span style={{ color: 'rgba(255,236,246,.58)' }}>사용</span>
                            <span className="font-semibold text-white">{item.uses}</span>
                          </div>
                          <div className="flex justify-between text-[12px]">
                            <span style={{ color: 'rgba(255,236,246,.58)' }}>크레딧</span>
                            <span className="font-semibold text-white">{item.credit}</span>
                          </div>
                          <div className="flex justify-between text-[12px]">
                            <span style={{ color: 'rgba(255,236,246,.58)' }}>유형</span>
                            <span className="font-semibold text-white">{item.kind}</span>
                          </div>
                          <div
                            className="text-center text-[11.5px] tracking-[-.01em]"
                            style={{
                              marginTop: 'auto',
                              lineHeight: '1.4',
                              color: 'rgba(255,236,246,.48)',
                            }}
                          >
                            다시 누르면
                            <br />
                            카드로 돌아갑니다
                          </div>
                        </div>
                      </>
                    )}
                  </div>
                </Fragment>
              ))}
            </div>
          </>
        )}
      </div>

      <div
        className="absolute"
        style={{
          left: '398px',
          top: '20px',
          width: '280px',
          height: '40px',
          borderRadius: '10px',
          background: 'rgba(28,10,38,.42)',
          animation: 'uiIn .7s .44s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <input
          className="absolute rounded-md border-0 bg-transparent font-[inherit] text-[15px] font-semibold tracking-[-.01em] text-white outline-0 transition-colors duration-[160ms] hover:bg-white/8 focus:bg-white/12"
          defaultValue="문서 정리"
          aria-label="에이전트 이름"
          style={{ left: '10px', top: '8px', width: '234px', height: '24px', padding: '0 4px' }}
        />
        <svg
          className="absolute cursor-pointer"
          width="16"
          height="16"
          viewBox="0 0 16 16"
          fill="none"
          style={{ left: '250px', top: '12px' }}
        >
          <path
            d="M13 8 L3.5 8 M7.5 3.5 L3 8 L7.5 12.5"
            stroke="rgba(255,255,255,.72)"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </div>
    </>
  )
}
