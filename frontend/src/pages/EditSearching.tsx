import { useEffect, useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom'

import { RECOMMEND_LIMITS, getAgentRecommendations, type RecommendedAgent } from '@/api/agents'
import CanvasNode from '@/components/CanvasNode'
import CanvasPane, { PANE_CONTENT_WIDTH, PANE_HEIGHT } from '@/components/CanvasPane'
import CanvasWorld from '@/components/CanvasWorld'
import SearchIcon from '@/components/SearchIcon'
import SidePanel, { CARD_GUTTER, PANEL_CONTENT_WIDTH, PANEL_INSET } from '@/components/SidePanel'
import { nodesBounds, seedCanvasNodes } from '@/lib/canvasWorkflow'
import { priceLabel } from '@/lib/format'
import { queryKeys } from '@/lib/queryKeys'
import { INITIAL_VIEWPORT, fitViewport } from '@/lib/viewport'
import { queryFrom, seedsFrom } from '@/routes/handoff'
import { paths } from '@/routes/paths'

/**
 * 추천 에이전트를 찾아 선택하는 화면.
 *
 * 왼쪽은 편집 화면과 같은 작업면이다 — 선택한 카드가 곧바로 여기 올라가므로 "완료를 누르면
 * 무엇이 생기는지" 를 따로 설명하지 않는다. 선택한 것은 편집 화면으로 그대로 건너간다.
 */

/** 한 번에 받는 개수. 상한까지 받는다 — 선택할 것이 많을수록 낫고, 서버도 한 번에 잘라 준다. */
const SIZE = RECOMMEND_LIMITS.size.max

/**
 * 「검색 중」을 최소한 이만큼은 보여준다.
 *
 * 추천은 임베딩 검색이라 수십 ms 에 끝난다. 그대로 두면 안내가 한 프레임 번쩍이고 사라져
 * 화면이 고장 난 것처럼 보인다. 반대로 더 오래 붙들지는 않는다 — 없는 대기를 만드는 것이다.
 */
const MIN_SEARCHING_MS = 700

/** 미리보기 카드의 등장 모션. 순서에 따라 달라지면 하나를 뺄 때 남은 카드가 전부 다시 나타난다. */
const PREVIEW_ENTER = 'uiIn .5s cubic-bezier(.2,.8,.25,1) backwards'

/** 작업면 한가운데. 안내와 검색 중 표시가 같은 축에 선다. */
const PANE_TEXT_WIDTH = 980

export default function EditSearching() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()

  // 검색어는 주소가 들고 있다. 화면이 따로 쥐면 뒤로 가기와 새로고침에서 둘이 갈린다.
  const query = queryFrom(params)
  // 처음부터 채워 두지 않는다 — 다시 찾으려면 먼저 지워야 해서 손이 하나 더 간다.
  const [draft, setDraft] = useState('')
  /** 선택한 순서가 캔버스에 놓이는 순서다. Set 으로 두면 순서를 잃는다. */
  const [picked, setPicked] = useState<RecommendedAgent[]>([])
  /** 최소 시간을 채운 검색어. 값으로 들고 있어야 다시 찾을 때 저절로 되감긴다. */
  const [settledFor, setSettledFor] = useState<string | null>(null)

  const { data, status, isFetching, refetch } = useQuery({
    queryKey: queryKeys.marketplace.agents.recommendations({ query, size: SIZE }),
    queryFn: () => getAgentRecommendations({ query, size: SIZE }),
    enabled: query.length > 0,
  })

  useEffect(() => {
    const timer = setTimeout(() => setSettledFor(query), MIN_SEARCHING_MS)
    return () => clearTimeout(timer)
  }, [query])

  const items = data?.items ?? []
  // 재시도를 기다리는 동안은 부르는 중이 아니다. `isFetching` 만 보면 그 틈에
  // 「찾지 못했습니다」가 잠깐 뜬다 — 아직 답을 받지 못한 것이지 없는 것이 아니다.
  const searching = status === 'pending' || isFetching || settledFor !== query
  const failed = status === 'error' && !searching
  const isEmpty = status === 'success' && !searching && items.length === 0

  // 선택한 카드는 다시 찾아도 남는다 — 두 번 찾아 모으는 것이 자연스럽고, 지워 버리면
  // 무엇을 선택했었는지 되찾을 길이 없다.
  const preview = useMemo(
    () =>
      seedCanvasNodes(seedsFrom({ agents: picked }), 'grid').map((node) => ({
        ...node,
        enter: PREVIEW_ENTER,
      })),
    [picked],
  )

  // 미리보기에는 팬·줌이 없다. 선택한 것이 늘어 작업면을 넘치면 그만큼 줄여 다 보이게 한다.
  const previewView = useMemo(() => {
    const bounds = nodesBounds(preview)
    if (!bounds) return INITIAL_VIEWPORT
    return fitViewport(bounds, { width: PANE_CONTENT_WIDTH, height: PANE_HEIGHT })
  }, [preview])

  const toggle = (agent: RecommendedAgent) =>
    setPicked((chosen) =>
      chosen.some((it) => it.agentId === agent.agentId)
        ? chosen.filter((it) => it.agentId !== agent.agentId)
        : [...chosen, agent],
    )

  const handleResearch = (ev: React.KeyboardEvent<HTMLInputElement>) => {
    if (ev.key !== 'Enter') return
    const next = draft.trim()
    if (!next || next === query) return
    // 되돌아갈 곳은 랜딩이다 — 다시 찾은 검색어를 히스토리에 쌓지 않는다.
    setParams({ q: next }, { replace: true })
  }

  const handleStart = () =>
    navigate(paths.edit.nodes.withoutId, { state: picked.length > 0 ? { agents: picked } : null })

  // 랜딩에서 적은 문장 없이는 찾을 것이 없다. 주소를 직접 친 경우다.
  if (!query) return <Navigate to={paths.home} replace />

  return (
    <>
      <CanvasPane>
        {searching ? (
          <div
            className="absolute top-0 left-0"
            style={{ width: `${PANE_TEXT_WIDTH}px`, height: '1024px' }}
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
              {[0, 1.13, 2.26].map((delay, i) => (
                <div
                  key={delay}
                  className="absolute rounded-full"
                  style={{
                    left: '68px',
                    top: '24px',
                    width: '144px',
                    height: '144px',
                    border: `1.5px solid rgba(255,222,240,${[0.75, 0.6, 0.45][i]})`,
                    animation: `ripple 3.4s ${delay}s cubic-bezier(.2,.6,.25,1) infinite`,
                  }}
                ></div>
              ))}
            </div>

            <div
              className="absolute left-0 text-center text-[24px] font-semibold tracking-[-.01em] text-white"
              style={{
                top: '560px',
                width: `${PANE_TEXT_WIDTH}px`,
                textShadow: '0 2px 20px rgba(35,8,45,.8)',
                animation: 'uiInPop .8s .2s cubic-bezier(.2,.8,.25,1) both',
              }}
            >
              <span className="relative inline-block">검색 중</span>
            </div>
            <div
              className="absolute left-0 truncate text-center text-[15px]"
              style={{
                top: '598px',
                width: `${PANE_TEXT_WIDTH}px`,
                padding: '0 60px',
                color: 'rgba(255,236,246,.8)',
                textShadow: '0 1px 10px rgba(35,8,45,.7)',
                animation: 'uiInPop .8s .28s cubic-bezier(.2,.8,.25,1) both',
              }}
            >
              ‘{query}’ 에 맞는 에이전트를 찾고 있습니다
            </div>
          </div>
        ) : (
          <>
            <div
              className="pointer-events-none absolute left-0 text-center text-[16px] tracking-[-.005em]"
              style={{
                top: 'calc(74px - var(--edge-y, 0px))',
                width: `${PANE_TEXT_WIDTH}px`,
                color: 'rgba(255,240,248,.78)',
                animation: 'uiIn .8s .3s cubic-bezier(.2,.8,.25,1) both',
              }}
            >
              {picked.length > 0
                ? '선택한 에이전트입니다. 편집 화면에서 선으로 연결합니다'
                : '오른쪽에서 카드를 누르면 여기에 올라갑니다'}
            </div>

            <CanvasWorld view={previewView}>
              {preview.map((node, i) => (
                <CanvasNode
                  key={node.id}
                  index={i}
                  name={node.name}
                  sublabel="에이전트"
                  x={node.x}
                  y={node.y}
                  mine={node.mine}
                  floaty={node.floaty}
                  enter={node.enter}
                  background="rgba(255,255,255,.11)"
                  sublabelColor="rgba(230,196,212,.85)"
                />
              ))}
            </CanvasWorld>
          </>
        )}
      </CanvasPane>

      <SidePanel animation="sideInDeep 1.05s .12s cubic-bezier(.18,.86,.24,1) both">
        <div
          className="absolute bg-[rgba(255,255,255,.10)]"
          style={{
            left: `${PANEL_INSET}px`,
            top: `${PANEL_INSET}px`,
            width: `${PANEL_CONTENT_WIDTH}px`,
            height: '40px',
            borderRadius: '10px',
          }}
        >
          <SearchIcon left={14} top={13} color="rgba(255,255,255,.7)" />
          <input
            className="absolute border-0 bg-transparent font-[inherit] text-[14px] text-white outline-0"
            id="searchSide"
            type="text"
            value={draft}
            maxLength={RECOMMEND_LIMITS.query.max}
            placeholder="하고 싶은 일을 다시 적어 보세요"
            onChange={(ev) => setDraft(ev.target.value)}
            onKeyDown={handleResearch}
            style={{ left: '38px', top: '9px', width: '310px', height: '22px' }}
          />
        </div>

        <div
          className="absolute text-[15px] font-semibold tracking-[-.01em] text-white"
          style={{ left: `${PANEL_INSET}px`, top: '88px' }}
        >
          추천 결과
        </div>
        <div
          className="absolute text-right text-[13px]"
          style={{ right: `${PANEL_INSET}px`, top: '90px', color: 'rgba(255,236,246,.6)' }}
        >
          {searching ? '찾는 중' : failed ? '' : `${items.length}개`}
        </div>

        {failed && (
          <div
            className="absolute text-[14px] leading-[1.7]"
            style={{
              left: `${PANEL_INSET}px`,
              top: '140px',
              width: `${PANEL_CONTENT_WIDTH}px`,
              color: 'rgba(255,236,246,.72)',
              animation: 'fadeIn .3s ease both',
            }}
          >
            추천을 불러오지 못했습니다.
            <button
              type="button"
              onClick={() => void refetch()}
              className="mt-[14px] block cursor-pointer border-0 font-[inherit] text-[13px] text-white"
              style={{
                height: '34px',
                padding: '0 16px',
                borderRadius: '17px',
                background: 'rgba(255,255,255,.14)',
              }}
            >
              다시 시도
            </button>
          </div>
        )}

        {isEmpty && (
          <div
            className="absolute text-[14px] leading-[1.7]"
            style={{
              left: `${PANEL_INSET}px`,
              top: '140px',
              width: `${PANEL_CONTENT_WIDTH}px`,
              color: 'rgba(255,236,246,.72)',
              animation: 'fadeIn .3s ease both',
            }}
          >
            맞는 에이전트를 찾지 못했습니다.
            <br />
            다른 말로 다시 적어 보거나, 빈 캔버스에서 직접 만들 수 있습니다.
          </div>
        )}

        {!searching && status === 'success' && items.length > 0 && (
          <div
            className="scroll-thin absolute grid"
            style={{
              left: `${PANEL_INSET}px`,
              top: '116px',
              width: '408px',
              height: 'calc(786px + 2 * var(--edge-y, 0px))',
              // 한 줄에 두 장. 남는 폭을 열이 나눠 갖게 한다.
              gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
              gap: '14px',
              alignContent: 'start',
            }}
          >
            {items.map((agent) => {
              const chosen = picked.some((it) => it.agentId === agent.agentId)
              return (
                <button
                  key={agent.agentId}
                  type="button"
                  onClick={() => toggle(agent)}
                  aria-pressed={chosen}
                  style={{
                    // 폭은 주지 않고 그리드 열을 그대로 채운다.
                    height: '176px',
                    borderRadius: '12px',
                    background: chosen ? 'rgba(255,255,255,.20)' : 'rgba(255,255,255,.09)',
                    boxShadow: chosen ? 'inset 0 0 0 1.5px rgba(255,190,220,.75)' : 'none',
                    transition: 'background .18s,transform .18s,box-shadow .18s',
                  }}
                  className="relative cursor-pointer border-0 p-0 text-left font-[inherit] hover:-translate-y-[2px] hover:bg-[rgba(255,255,255,.17)]"
                >
                  <div
                    className="absolute overflow-hidden bg-[rgba(255,255,255,.10)]"
                    style={{
                      left: `${CARD_GUTTER}px`,
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
                      {agent.name.trim().charAt(0)}
                    </div>
                  </div>

                  {chosen && (
                    <div
                      className="absolute flex items-center justify-center rounded-full"
                      style={{
                        right: `${CARD_GUTTER}px`,
                        top: '16px',
                        width: '22px',
                        height: '22px',
                        background: 'var(--color-accent)',
                        animation: 'uiInPop .28s cubic-bezier(.2,.8,.25,1) both',
                      }}
                    >
                      <svg
                        width="12"
                        height="12"
                        viewBox="0 0 12 12"
                        fill="none"
                        style={{ stroke: 'var(--color-accent-ink)' }}
                        strokeWidth="2"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      >
                        <path d="M2.5 6.2 5 8.7l4.5-5" />
                      </svg>
                    </div>
                  )}

                  <div
                    className="absolute truncate text-[14px] font-semibold tracking-[-.01em] text-white"
                    style={{ left: `${CARD_GUTTER}px`, right: `${CARD_GUTTER}px`, top: '76px' }}
                  >
                    {agent.name}
                  </div>
                  <div
                    className="absolute overflow-hidden text-[12.5px] leading-[1.5]"
                    style={{
                      left: `${CARD_GUTTER}px`,
                      right: `${CARD_GUTTER}px`,
                      top: '100px',
                      height: '38px',
                      color: 'rgba(255,236,246,.66)',
                      display: '-webkit-box',
                      WebkitBoxOrient: 'vertical',
                      WebkitLineClamp: 2,
                    }}
                  >
                    {agent.summary}
                  </div>
                  <div
                    className="absolute text-[12px]"
                    style={{
                      left: `${CARD_GUTTER}px`,
                      top: '146px',
                      color: 'rgba(255,214,234,.8)',
                    }}
                  >
                    {priceLabel(agent.price)}
                  </div>
                </button>
              )
            })}
          </div>
        )}

        <div
          className="absolute flex items-center"
          style={{
            left: `${PANEL_INSET}px`,
            top: 'calc(928px + var(--edge-y, 0px))',
            width: `${PANEL_CONTENT_WIDTH}px`,
            height: '46px',
            animation: 'uiIn .6s .5s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          {picked.length > 0 && (
            <div className="text-[13px]" style={{ color: 'rgba(255,236,246,.7)' }}>
              {picked.length}개 선택됨
            </div>
          )}
          <button
            type="button"
            onClick={handleStart}
            className="ml-auto cursor-pointer border-0 font-[inherit] text-[14px] font-semibold hover:brightness-[1.05]"
            style={{
              height: '46px',
              padding: '0 22px',
              borderRadius: '23px',
              background: 'var(--color-accent)',
              color: 'var(--color-accent-ink)',
              boxShadow: '0 6px 18px rgba(140,100,130,.26)',
            }}
          >
            시작
          </button>
        </div>
      </SidePanel>
    </>
  )
}
