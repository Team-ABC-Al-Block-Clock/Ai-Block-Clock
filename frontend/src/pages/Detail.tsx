import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Fragment, useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'

import {
  addAgentFavorite,
  getMarketplaceAgent,
  removeAgentFavorite,
  type AgentDetail,
} from '@/api/agents'
import { ApiError } from '@/api/errors'
import { compactCount, creditLabel, ratingLabel, runLabel, successLabel } from '@/lib/format'
import { queryKeys } from '@/lib/queryKeys'
import { paths } from '@/routes/paths'

/** 안내 카드. 그리드 안쪽(272~1168, 896px)에서 가운데 — 272 + (896 - 620) / 2 = 410.
 *  translateX 로 중앙 정렬하지 않는다: transform 이 걸리면 backdrop 루트가 되어
 *  카드의 backdrop-filter 가 바깥 배경을 못 가져온다(컨벤션 10절). */
const NOTICE_BOX = { left: '410px', top: '360px', width: '620px' } as const

/** 빈 상태는 실패가 아니라 다음 행동을 고르는 자리다 — 행동을 최소 하나 준다.
 *  <empty-state> 가 JSON 으로 받는다(`components/types.ts` 의 EmptyAction). */
const NOT_FOUND_ACTIONS = JSON.stringify([
  { label: '에이전트 둘러보기', href: paths.search, primary: true },
  { label: '홈으로', href: paths.home },
])

const ERROR_ACTIONS = JSON.stringify([
  { label: '다시 시도', event: 'retry', primary: true },
  { label: '목록으로', href: paths.search },
])

/** 즐겨찾기 실패 중 로그인 문제. 401 안에서도 문구가 갈린다(`api/errors.ts`). */
const AUTH_CODES = ['AUTH_005', 'AUTH_006', 'AUTH_007']

type ShotTab = 'INPUT' | 'OUTPUT'

export default function Detail() {
  const { agentId } = useParams<{ agentId: string }>()
  const [tab, setTab] = useState<ShotTab>('INPUT')
  const [shot, setShot] = useState(0)

  const {
    data: agent,
    isPending,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: queryKeys.marketplace.agents.detail(agentId ?? ''),
    queryFn: () => getMarketplaceAgent(agentId ?? ''),
    // '/agent' — 등록 API 가 붙기 전까지 남겨 둔 ID 없는 경로. 요청을 보내지 않는다.
    enabled: !!agentId,
  })

  const queryClient = useQueryClient()
  const favorite = useMutation({
    mutationFn: (next: boolean) =>
      next ? addAgentFavorite(agentId ?? '') : removeAgentFavorite(agentId ?? ''),
    /** 명세 「화면이 다시 조회하지 않게 갱신된 횟수를 함께 반환한다」 — refetch 하지 않고 덮는다.
     *
     *  ponytail: 목록은 무효화하지 않는다. Search 는 `favorited` 를 그리지 않고 MyAgents 의
     *  즐겨찾기 탭은 아직 화면 안 상태라 무효화해도 보이는 변화가 없다. 목록이 별을 그리기
     *  시작하면 invalidateQueries({ queryKey: queryKeys.marketplace.agents.lists() }) 를 붙인다. */
    onSuccess: (res) =>
      queryClient.setQueryData(
        queryKeys.marketplace.agents.detail(agentId ?? ''),
        (prev: AgentDetail | undefined) =>
          prev && { ...prev, favorited: res.favorited, favoriteCount: res.favoriteCount },
      ),
  })

  // <empty-state> 의 '다시 시도'. React 는 커스텀 이벤트를 on* 으로 받지 않아 직접 붙인다.
  // emit 이 호스트에서 bubbles 로 올리므로 호스트에서 받으면 된다.
  const noticeRef = useRef<HTMLElement>(null)
  useEffect(() => {
    const node = noticeRef.current
    if (!node) return
    const onRetry = () => void refetch()
    node.addEventListener('retry', onRetry)
    return () => node.removeEventListener('retry', onRetry)
  }, [refetch, isError])

  // ID 가 없거나 404 면 '없는 에이전트' 다. 로딩 판정보다 먼저 와야 한다 —
  // enabled: false 면 isPending 이 계속 true 라서 뒤에 두면 영원히 '불러오는 중' 이다.
  if (!agentId || (error instanceof ApiError && error.status === 404))
    return (
      <empty-state
        style={{ position: 'absolute', ...NOTICE_BOX, animation: 'fadeIn .3s ease both' }}
        heading="에이전트를 찾을 수 없습니다"
        body="삭제되었거나 주소가 잘못된 에이전트입니다."
        actions={NOT_FOUND_ACTIONS}
      />
    )

  if (isError)
    return (
      <empty-state
        ref={noticeRef}
        style={{ position: 'absolute', ...NOTICE_BOX, animation: 'fadeIn .3s ease both' }}
        heading="에이전트를 불러오지 못했습니다"
        body={
          error instanceof ApiError && error.isNetworkError
            ? '서버에 연결할 수 없습니다. 네트워크를 확인해 주세요.'
            : '잠시 후 다시 시도해 주세요.'
        }
        actions={ERROR_ACTIONS}
      />
    )

  // 불러오는 중에 '없음' 을 띄우지 않는다 — 요청마다 한 번씩 깜빡인다(컨벤션 6절).
  if (isPending || !agent)
    return (
      <div
        className="absolute text-center text-[15px]"
        style={{
          ...NOTICE_BOX,
          top: '420px',
          color: 'rgba(255,236,246,.62)',
          animation: 'fadeIn .3s ease both',
        }}
      >
        불러오는 중…
      </div>
    )

  // 유형별로 갈라 order 순으로. 탭마다 장수가 달라서 인덱스를 잘라 준다.
  const shots = agent.images.filter((img) => img.type === tab).sort((a, b) => a.order - b.order)
  const at = Math.min(shot, Math.max(0, shots.length - 1))
  const current = shots[at]

  const handleTab = (next: ShotTab) => () => {
    setTab(next)
    setShot(0)
  }
  const step = (delta: number) => () =>
    setShot(shots.length ? (at + delta + shots.length) % shots.length : 0)
  const pickShot = (i: number) => () => setShot(i)
  // 진행 중에 또 누르면 등록·해제가 엇갈린다. mutations retry 는 false 가 기본이다(컨벤션 6절).
  const handleToggleFav = () => {
    if (!favorite.isPending) favorite.mutate(!agent.favorited)
  }

  const favError = favorite.error
  const favMessage = !favError
    ? null
    : favError instanceof ApiError && favError.code && AUTH_CODES.includes(favError.code)
      ? '로그인이 필요합니다'
      : '즐겨찾기에 실패했습니다'

  const isIn = tab === 'INPUT'
  const slotLabel = isIn ? '입력' : '출력'
  const inBg = isIn ? 'rgba(255,255,255,.9)' : 'transparent'
  const inFg = isIn ? '#3a1030' : 'rgba(255,255,255,.72)'
  const outBg = isIn ? 'transparent' : 'rgba(255,255,255,.9)'
  const outFg = isIn ? 'rgba(255,255,255,.72)' : '#3a1030'
  const favBg = agent.favorited ? 'rgba(255,179,205,.26)' : 'rgba(255,255,255,.11)'
  const favStarColor = agent.favorited ? '#e3c8a3' : 'rgba(255,255,255,.5)'

  // 노드 개수·등급·평균 실행 시간·공개 범위는 명세가 반환하지 않는다
  // (내부 구조 보호 / 내부 자료 / 미집계 / 등록이 곧 공개).
  const chips = [
    `사용 ${runLabel(agent.executionCount)}`,
    `성공률 ${successLabel(agent.successRate, agent.executionCount)}`,
    `★ ${ratingLabel(agent.averageRating, agent.reviewCount)}`,
    `즐겨찾기 ${compactCount(agent.favoriteCount)}`,
  ]
  const thumbs = shots.map((img, i) => ({
    imageId: img.imageId,
    url: img.url,
    pick: pickShot(i),
    bg: i === at ? 'rgba(255,255,255,.16)' : 'rgba(255,255,255,.08)',
    ring:
      i === at ? 'inset 0 0 0 2px rgba(255,194,216,.9)' : 'inset 0 0 0 1px rgba(255,255,255,.12)',
  }))

  return (
    <>
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            'linear-gradient(180deg, rgba(22,10,36,.34) 0%, rgba(22,10,36,.18) 24%, rgba(22,10,36,.14) 58%, rgba(22,10,36,.26) 100%)',
        }}
      ></div>

      <div
        className="absolute top-0 left-0 overflow-x-hidden"
        id="detailScroll"
        style={{
          width: '1440px',
          height: '1024px',
          overflowY: 'auto',
          scrollbarWidth: 'thin',
          scrollbarColor: 'rgba(255,255,255,.28) transparent',
        }}
      >
        <div className="relative" style={{ width: '1440px', height: '1520px' }}>
          <a
            className="absolute flex items-center text-[14px] tracking-[-.01em]"
            href={paths.search}
            style={{
              left: '272px',
              top: '140px',
              height: '26px',
              gap: '7px',
              color: 'rgba(255,236,246,.78)',
              animation: 'rise .6s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            ← 목록으로
          </a>

          <div
            className="absolute overflow-hidden bg-[rgba(255,255,255,.10)]"
            style={{
              left: '272px',
              top: '176px',
              width: '120px',
              height: '120px',
              borderRadius: '16px',
              animation: 'unfold .7s .04s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            <div
              className="absolute inset-0"
              style={{
                background:
                  'radial-gradient(circle at 52% 28%, rgba(255,218,224,.16), rgba(255,214,232,0) 66%)',
              }}
            ></div>
            {agent.iconUrl ? (
              <img className="h-full w-full object-cover" src={agent.iconUrl} alt="" />
            ) : (
              <div
                className="absolute inset-0 flex items-center justify-center text-[44px] font-semibold"
                style={{ color: 'rgba(255,226,238,.75)' }}
              >
                {agent.name.trim().charAt(0).toUpperCase()}
              </div>
            )}
          </div>

          <div
            className="absolute text-[32px] font-bold tracking-[-.02em] text-white"
            style={{
              left: '424px',
              top: '180px',
              textShadow: '0 2px 20px rgba(35,8,45,.6)',
              animation: 'rise .7s .08s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            {agent.name}
          </div>
          <div
            className="absolute text-[15px] tracking-[-.01em]"
            style={{
              left: '424px',
              top: '228px',
              width: '520px',
              lineHeight: '1.5',
              color: 'rgba(255,236,246,.78)',
              animation: 'rise .7s .12s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            {agent.summary}
          </div>

          <div
            className="absolute flex items-center"
            style={{
              left: '424px',
              top: '264px',
              width: '220px',
              height: '36px',
              gap: '12px',
              animation: 'rise .7s .16s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            <div
              className="flex-none overflow-hidden rounded-full"
              style={{ width: '36px', height: '36px', background: 'rgba(255,255,255,.14)' }}
            >
              {agent.owner.profileImageUrl && (
                <img
                  className="h-full w-full object-cover"
                  src={agent.owner.profileImageUrl}
                  alt=""
                />
              )}
            </div>
            <div className="flex flex-col" style={{ gap: '2px' }}>
              <div className="text-[14px] font-semibold tracking-[-.01em] text-white">
                {agent.owner.nickname}
              </div>
              <div className="text-[12px]" style={{ color: 'rgba(255,236,246,.6)' }}>
                만든 사람
              </div>
            </div>
          </div>

          <div
            onClick={handleToggleFav}
            style={{
              left: '860px',
              top: '210px',
              width: '129px',
              height: '52px',
              borderRadius: '26px',
              background: `${favBg}`,
              backdropFilter: 'blur(18px)',
              WebkitBackdropFilter: 'blur(18px)',
              gap: '8px',
              opacity: favorite.isPending ? 0.6 : 1,
              transition: 'background .18s,opacity .18s',
              animation: 'rise .7s .2s cubic-bezier(.2,.8,.25,1) both',
            }}
            className="absolute flex cursor-pointer items-center justify-center hover:bg-[rgba(255,255,255,.2)]"
          >
            <div className="text-[15px]" style={{ color: `${favStarColor}` }}>
              ★
            </div>
            <div className="text-[15px] font-semibold tracking-[-.01em] text-white">즐겨찾기</div>
          </div>

          {/* 실패를 조용히 넘기지 않는다. 토스트가 없어 버튼 아래에 한 줄로 둔다. */}
          {favMessage && (
            <div
              className="absolute text-right text-[12.5px] tracking-[-.01em]"
              style={{
                left: '860px',
                top: '266px',
                width: '308px',
                color: 'rgba(255,196,214,.9)',
                animation: 'fadeIn .2s ease both',
              }}
            >
              {favMessage}
            </div>
          )}

          <a
            href={paths.run.waiting.withoutId}
            style={{
              left: '1008px',
              top: '210px',
              width: '160px',
              height: '52px',
              borderRadius: '26px',
              background: '#b98da9',
              boxShadow: '0 10px 26px rgba(140,100,130,.24)',
              color: '#2f1527',
              transition: 'transform .18s',
              animation: 'rise .7s .24s cubic-bezier(.2,.8,.25,1) both',
            }}
            className="absolute flex items-center justify-center text-[16px] font-bold tracking-[-.01em] hover:-translate-y-[2px]"
          >
            실행하기
          </a>

          <div
            className="absolute"
            style={{
              left: '272px',
              top: '344px',
              width: '896px',
              height: '1px',
              background: 'rgba(255,255,255,.16)',
            }}
          ></div>

          <div
            className="absolute flex"
            style={{
              left: '272px',
              top: '372px',
              width: '896px',
              height: '32px',
              gap: '10px',
              animation: 'rise .7s .28s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            {chips.map((label) => (
              <Fragment key={label}>
                <div
                  className="flex items-center text-[13px] tracking-[-.01em] whitespace-nowrap"
                  style={{
                    height: '32px',
                    padding: '0 15px',
                    borderRadius: '100px',
                    background: 'rgba(255,255,255,.11)',
                    color: 'rgba(255,240,247,.86)',
                  }}
                >
                  {label}
                </div>
              </Fragment>
            ))}
            {/* 명세 「가격은 한 줄이다」 — 실행 1회당 가격만 보여준다. 토큰 단위 단가는 없다. */}
            <div
              className="flex flex-none items-center whitespace-nowrap"
              style={{
                height: '32px',
                padding: '0 15px',
                borderRadius: '100px',
                background: 'rgba(185,141,169,.26)',
              }}
            >
              <div
                className="text-[13px] font-semibold tracking-[-.01em]"
                style={{ color: '#ffe4f0' }}
              >
                {creditLabel(agent.price)}
              </div>
            </div>
          </div>

          <div
            className="absolute text-[16px] font-semibold tracking-[-.01em] text-white"
            style={{
              left: '272px',
              top: '444px',
              animation: 'rise .7s .32s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            설명
          </div>
          <div
            className="absolute text-[14.5px] tracking-[-.01em]"
            style={{
              left: '272px',
              top: '478px',
              width: '872px',
              lineHeight: '1.75',
              color: 'rgba(255,240,247,.76)',
              textWrap: 'pretty',
              animation: 'rise .7s .34s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            {agent.description}
          </div>

          <div
            className="absolute text-[16px] font-semibold tracking-[-.01em] text-white"
            style={{
              left: '272px',
              top: '600px',
              animation: 'rise .7s .36s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            스크린샷
          </div>

          <div
            className="absolute box-border flex bg-[rgba(255,255,255,.10)]"
            style={{
              left: '1050px',
              top: '594px',
              width: '118px',
              height: '32px',
              borderRadius: '100px',
              padding: '3px',
              animation: 'rise .7s .36s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            <div
              className="flex cursor-pointer items-center justify-center text-[13px] font-semibold transition-colors duration-[160ms]"
              onClick={handleTab('INPUT')}
              style={{
                width: '56px',
                height: '26px',
                borderRadius: '100px',
                background: `${inBg}`,
                color: `${inFg}`,
              }}
            >
              입력
            </div>
            <div
              className="flex cursor-pointer items-center justify-center text-[13px] font-semibold transition-colors duration-[160ms]"
              onClick={handleTab('OUTPUT')}
              style={{
                width: '56px',
                height: '26px',
                borderRadius: '100px',
                background: `${outBg}`,
                color: `${outFg}`,
              }}
            >
              출력
            </div>
          </div>

          <div
            className="bg-surface-card absolute overflow-hidden"
            style={{
              left: '272px',
              top: '642px',
              width: '896px',
              height: '480px',
              borderRadius: '14px',
              animation: 'unfold .8s .4s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            {current ? (
              <img
                className="absolute top-0 left-0 h-full w-full object-cover"
                src={current.url}
                alt={`${agent.name} ${slotLabel} 스크린샷 ${at + 1}`}
              />
            ) : (
              <div
                className="absolute top-0 left-0 flex flex-col items-center justify-center"
                style={{ width: '896px', height: '480px', gap: '10px' }}
              >
                <div
                  className="flex items-center justify-center bg-[rgba(255,255,255,.10)] text-[22px]"
                  style={{
                    width: '52px',
                    height: '52px',
                    borderRadius: '14px',
                    fontWeight: '300',
                    color: 'rgba(255,255,255,.6)',
                  }}
                >
                  +
                </div>
                <div
                  className="text-[13px] tracking-[-.01em]"
                  style={{ color: 'rgba(255,240,247,.52)' }}
                >
                  {slotLabel} 스크린샷이 없습니다
                </div>
              </div>
            )}

            {shots.length > 1 && (
              <>
                <div
                  onClick={step(-1)}
                  style={{
                    left: '16px',
                    top: '218px',
                    width: '44px',
                    height: '44px',
                    background: 'rgba(20,9,34,.5)',
                    backdropFilter: 'blur(14px)',
                    WebkitBackdropFilter: 'blur(14px)',
                  }}
                  className="absolute flex cursor-pointer items-center justify-center rounded-full text-[16px] font-semibold text-white transition-colors duration-[160ms] hover:bg-[rgba(20,9,34,.78)]"
                >
                  ←
                </div>
                <div
                  onClick={step(1)}
                  style={{
                    left: '836px',
                    top: '218px',
                    width: '44px',
                    height: '44px',
                    background: 'rgba(20,9,34,.5)',
                    backdropFilter: 'blur(14px)',
                    WebkitBackdropFilter: 'blur(14px)',
                  }}
                  className="absolute flex cursor-pointer items-center justify-center rounded-full text-[16px] font-semibold text-white transition-colors duration-[160ms] hover:bg-[rgba(20,9,34,.78)]"
                >
                  →
                </div>
              </>
            )}

            <div
              className="absolute flex items-center"
              style={{
                left: '16px',
                top: '436px',
                height: '28px',
                padding: '0 13px',
                borderRadius: '100px',
                background: 'rgba(20,9,34,.55)',
                backdropFilter: 'blur(14px)',
                WebkitBackdropFilter: 'blur(14px)',
              }}
            >
              <div className="text-[12px] font-semibold text-white">{slotLabel}</div>
            </div>
            {shots.length > 0 && (
              <div
                className="absolute flex items-center text-[12px] font-semibold text-white"
                style={{
                  left: '812px',
                  top: '436px',
                  height: '28px',
                  padding: '0 14px',
                  borderRadius: '100px',
                  background: 'rgba(20,9,34,.55)',
                  backdropFilter: 'blur(14px)',
                  WebkitBackdropFilter: 'blur(14px)',
                }}
              >
                {at + 1} / {shots.length}
              </div>
            )}
          </div>

          <div
            className="absolute flex"
            style={{
              left: '272px',
              top: '1146px',
              width: '896px',
              height: '72px',
              gap: '12px',
              animation: 'rise .7s .46s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            {thumbs.map((th) => (
              <Fragment key={th.imageId}>
                <div
                  className="relative flex-none cursor-pointer overflow-hidden"
                  onClick={th.pick}
                  style={{
                    width: '104px',
                    height: '72px',
                    borderRadius: '10px',
                    background: `${th.bg}`,
                    boxShadow: `${th.ring}`,
                    transition: 'background .16s,box-shadow .16s',
                  }}
                >
                  <img className="h-full w-full object-cover" src={th.url} alt="" />
                </div>
              </Fragment>
            ))}
          </div>

          {agent.usageExample && (
            <>
              <div
                className="absolute text-[16px] font-semibold tracking-[-.01em] text-white"
                style={{
                  left: '272px',
                  top: '1268px',
                  animation: 'rise .7s .5s cubic-bezier(.2,.8,.25,1) both',
                }}
              >
                실행 예시
              </div>
              <div
                className="absolute box-border"
                style={{
                  left: '272px',
                  top: '1300px',
                  width: '896px',
                  padding: '24px',
                  borderRadius: '14px',
                  background: 'rgba(255,255,255,.07)',
                  animation: 'unfold .8s .52s cubic-bezier(.2,.8,.25,1) both',
                }}
              >
                <div
                  className="text-[13.5px] tracking-[-.01em]"
                  style={{ lineHeight: '1.7', color: 'rgba(255,240,247,.82)' }}
                >
                  {agent.usageExample}
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </>
  )
}
