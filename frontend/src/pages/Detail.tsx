import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Fragment, useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'

import {
  addAgentFavorite,
  getAgentProvenance,
  getAgentReviews,
  getMarketplaceAgent,
  removeAgentFavorite,
  type AgentDetail,
  type AgentImage,
  type AgentProvenance,
} from '@/api/agents'
import { ApiError, AUTH_ERROR } from '@/api/errors'
import {
  blockLabel,
  compactCount,
  dateTimeLabel,
  maskName,
  priceLabel,
  ratingLabel,
  runLabel,
  shortAddress,
  successLabel,
  timeLabel,
} from '@/lib/format'
import { queryKeys } from '@/lib/queryKeys'
import { chainNameOf } from '@/lib/wagmi'
import { paths } from '@/routes/paths'

/** 안내 카드. 그리드 안쪽(272~1168, 896px)에서 가운데 — 272 + (896 - 620) / 2 = 410.
 *  translateX 로 중앙 정렬하지 않는다: transform 이 걸리면 backdrop 루트가 되어
 *  카드의 backdrop-filter 가 바깥 배경을 못 가져온다. */
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
const AUTH_CODES: string[] = [
  AUTH_ERROR.MISSING,
  AUTH_ERROR.UNIDENTIFIABLE,
  AUTH_ERROR.EXPIRED,
  AUTH_ERROR.ENDED,
  AUTH_ERROR.INVALID,
]

/** 한 번에 불러오는 리뷰 개수. */
const REVIEW_PAGE_SIZE = 5

/** 리뷰 한 건이 차지하는 높이. 본문을 2줄로 자르기 때문에 건마다 같다 —
 *  이 페이지는 절대 좌표라 높이를 미리 알아야 바닥이 정해진다. */
const REVIEW_ROW = 84
/** 리뷰 카드의 위아래 여백 합. */
const REVIEW_PAD = 48
/** '더 보기' 버튼 줄. 누르기 전까지만 있다. */
const REVIEW_MORE_BUTTON = 52
/** 버튼을 누른 뒤 목록 끝에 남겨 두는 빈 자리. 센티넬과 '불러오는 중'·'다시 시도' 가
 *  여기 들어가고, 다음 장은 이 자리부터 채워진다. 한 줄 높이로 잡는다 — 바닥에 닿기
 *  전에 끝이 보여야 리뷰가 붙는 것이 눈에 들어온다. */
const REVIEW_LANDING = REVIEW_ROW
/** 리뷰가 없거나 못 불러왔을 때 대신 들어가는 한 줄. */
const REVIEW_NOTICE = 44

/** 바닥에 닿고 나서 다음 장을 받기까지 두는 시간.
 *  닿자마자 채우면 다섯 줄이 한 번에 튀어 깜빡인 것처럼 보인다. 기다리는 동안
 *  '불러오는 중' 을 먼저 보여 주면 목록이 늘어난 것이 사건으로 읽힌다.
 *  튕겨서 바닥을 스치기만 한 경우에는 벗어나는 순간 취소된다. */
const REVIEW_HOLD_MS = 400

/** 아직 블록에 안 들어간 트랜잭션은 되돌려질 수 있다 — 검증됐다고 말하지 않는다. */
function proofBadgeOf(proof: AgentProvenance) {
  if (proof.blockchain.transactionStatus === 'PENDING')
    return { text: '블록 확정 대기 중', color: 'var(--color-muted)' }
  if (!proof.verification.verified)
    return { text: '✗ 원본 검증 불일치', color: 'rgba(255,196,214,.9)' }
  return { text: '✓ 원본 검증됨', color: 'var(--color-accent-bright)' }
}

type ShotTab = AgentImage['type']

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
    // 라우트가 '/agent/:agentId' 뿐이라 실제로는 늘 있지만 useParams 는 string | undefined 다.
    // 단언 대신 enabled 로 막는다 — 빈 ID 로 요청이 나가지 않는다.
    enabled: !!agentId,
  })

  // 상세를 기다리지 않는다 — 두 요청이 나란히 나간다.
  const provenance = useQuery({
    queryKey: queryKeys.marketplace.agents.provenance(agentId ?? ''),
    queryFn: () => getAgentProvenance(agentId ?? ''),
    enabled: !!agentId,
  })

  // 404 가 두 가지라 status 로는 못 가른다.
  const provenanceMissing =
    provenance.error instanceof ApiError && provenance.error.code === 'BLOCKCHAIN_003'

  // 상세·증명과 나란히 나간다. 리뷰는 페이지 맨 아래라 늦게 와도 위쪽을 밀지 않는다.
  const reviews = useInfiniteQuery({
    queryKey: queryKeys.marketplace.agents.reviews.list(agentId ?? '', {
      size: REVIEW_PAGE_SIZE,
    }),
    queryFn: ({ pageParam }) =>
      getAgentReviews(agentId ?? '', { cursor: pageParam, size: REVIEW_PAGE_SIZE }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    enabled: !!agentId,
  })

  const reviewItems = reviews.data?.pages.flatMap((page) => page.content) ?? []

  /* 처음에는 '더 보기' 버튼만 둔다. 상세를 보러 온 사람에게 리뷰가 먼저 자라면
     읽던 자리를 잃는다 — 더 볼지는 사용자가 정한다. 한 번 누르면 그 뒤로는
     끝에 닿을 때마다 이어받는다.
     불린 대신 어느 에이전트에서 눌렀는지를 담는다 — 다른 상세로 옮기면 effect 로
     되돌리지 않아도 저절로 버튼 상태로 돌아간다. */
  const [autoLoadFor, setAutoLoadFor] = useState<string | null>(null)
  const reviewAutoLoad = autoLoadFor === agentId

  /* root 를 주지 않아도 된다 — 교차 계산은 조상의 클립(#detailScroll)까지 함께 본다.
     실패한 커서로 다시 관찰하지 않는다 — 교차할 때마다 같은 요청이 반복된다.

     rootMargin 은 두지 않고 센티넬도 목록이 아니라 페이지 맨 아래에 둔다. 조금이라도
     미리 받으면 사용자가 끝에 닿기 전에 다음 장이 화면 밖에 그려져, 내려가 보면
     처음부터 다 있었던 것처럼 보인다. 바닥에 닿은 뒤에만 받는다.

     그래도 자라는 것이 보이도록 목록 끝에 한 줄짜리 빈 자리(REVIEW_LANDING)를 남긴다.
     바닥에 닿은 순간 그 자리가 화면에 있으므로, 비어 있던 곳이 새 리뷰로 채워진다. */
  const reviewEndRef = useRef<HTMLDivElement>(null)
  /** 바닥에 닿아 다음 장을 기다리는 중. 아직 요청 전이다. */
  const [reviewHold, setReviewHold] = useState(false)
  const { hasNextPage, isFetchingNextPage, isFetchNextPageError, fetchNextPage } = reviews
  useEffect(() => {
    const node = reviewEndRef.current
    if (!node || !reviewAutoLoad || !hasNextPage || isFetchNextPageError) return
    let hold: ReturnType<typeof setTimeout> | undefined
    const observer = new IntersectionObserver(([entry]) => {
      clearTimeout(hold)
      // 바닥을 벗어나면 기다리던 것을 취소한다 — 스쳐 지나간 것은 요청이 아니다.
      if (!entry.isIntersecting || isFetchingNextPage) return setReviewHold(false)
      setReviewHold(true)
      hold = setTimeout(() => {
        setReviewHold(false)
        void fetchNextPage()
      }, REVIEW_HOLD_MS)
    })
    observer.observe(node)
    return () => {
      clearTimeout(hold)
      observer.disconnect()
    }
  }, [reviewAutoLoad, hasNextPage, isFetchingNextPage, isFetchNextPageError, fetchNextPage])

  const queryClient = useQueryClient()
  const favorite = useMutation({
    mutationFn: (next: boolean) =>
      next ? addAgentFavorite(agentId ?? '') : removeAgentFavorite(agentId ?? ''),
    /** 명세 「화면이 다시 조회하지 않게 갱신된 횟수를 함께 반환한다」 — 상세는 refetch 하지 않고 덮는다.
     *
     *  목록은 무효화한다. 즐겨찾기 탭 자체가 `favorited` 로 만들어져서, 안 하면
     *  여기서 켠 별이 목록에서는 빈 별로 남는다. */
    onSuccess: (res) => {
      queryClient.setQueryData(
        queryKeys.marketplace.agents.detail(agentId ?? ''),
        (prev: AgentDetail | undefined) =>
          prev && { ...prev, favorited: res.favorited, favoriteCount: res.favoriteCount },
      )
      void queryClient.invalidateQueries({ queryKey: queryKeys.users.favorites.lists() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.users.usedAgents.lists() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.agents.lists() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.marketplace.agents.lists() })
    },
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
  // enabled: false 면 isPending 이 계속 true 라서 뒤에 두면 영원히 '불러오는 중' 이 된다.
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

  // 불러오는 중에 '없음' 을 띄우지 않는다 — 요청마다 한 번씩 깜빡인다.
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
  // 진행 중에 또 누르면 등록·해제가 엇갈린다. mutations retry 는 false 가 기본이다.
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
  // 실행 예시가 없으면 그 자리가 비므로 증명 섹션을 그만큼 끌어올린다.
  const proofTop = agent.usageExample ? 1440 : 1268

  /* 리뷰는 위쪽 좌표를 건드리지 않고 바닥을 늘릴 수 있도록 페이지의 마지막 블록으로 한다.
     증명 카드가 끝나는 자리(proofTop + 426)에서 32px 띄운다. */
  const reviewsTop = proofTop + 458
  const reviewsBody = reviewItems.length ? reviewItems.length * REVIEW_ROW : REVIEW_NOTICE
  const reviewsFoot =
    reviews.hasNextPage || reviews.isFetchNextPageError
      ? reviewAutoLoad
        ? REVIEW_LANDING
        : REVIEW_MORE_BUTTON
      : 0
  const reviewsCard = REVIEW_PAD + reviewsBody + reviewsFoot
  // 제목 32px + 카드 + 바닥 여백 64px.
  const pageHeight = reviewsTop + 32 + reviewsCard + 64
  const proof = provenance.data
  const proofBadge = proof && proofBadgeOf(proof)
  // 대조해 보라고 두는 값이라 주소는 줄이지 않는다.
  const onChainOwner = proof?.verification.onChainOwnerAddress ?? ''
  const onChainHash = proof ? `${proof.verification.onChainMetadataHash.slice(0, 18)}…` : ''

  const thumbs = shots.map((img, i) => ({
    imageId: img.imageId,
    url: img.url,
    pick: pickShot(i),
    bg: i === at ? 'rgba(255,255,255,.16)' : 'var(--color-surface-card)',
    ring:
      i === at ? 'inset 0 0 0 2px rgba(255,194,216,.9)' : 'inset 0 0 0 1px rgba(255,255,255,.12)',
  }))

  return (
    <>
      <div
        className="scroll-quiet absolute top-0 left-0 overflow-x-hidden"
        id="detailScroll"
        style={{
          width: '1440px',
          height: '1024px',
        }}
      >
        {/* 426 = 증명 카드 높이 + 아래 여백 */}
        <div className="relative" style={{ width: '1440px', height: `${pageHeight}px` }}>
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
            className="absolute flex items-center"
            style={{
              left: '424px',
              top: '180px',
              gap: '12px',
              animation: 'rise .7s .08s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            <div
              className="text-[32px] font-bold tracking-[-.02em] text-white"
              style={{ textShadow: '0 2px 20px rgba(35,8,45,.6)' }}
            >
              {agent.name}
            </div>
            {proofBadge && (
              <div
                className="flex flex-none items-center text-[12.5px] font-semibold tracking-[-.01em]"
                style={{
                  height: '26px',
                  padding: '0 12px',
                  borderRadius: '13px',
                  background: 'var(--color-surface-quiet)',
                  color: proofBadge.color,
                }}
              >
                {proofBadge.text}
              </div>
            )}
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
              background: 'var(--color-accent)',
              boxShadow: '0 10px 26px rgba(140,100,130,.24)',
              color: 'var(--color-accent-ink)',
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
                {priceLabel(agent.price)}
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
                  background: 'var(--color-surface-inset)',
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

          <div
            className="absolute text-[16px] font-semibold tracking-[-.01em] text-white"
            style={{
              left: '272px',
              top: `${proofTop}px`,
              animation: 'rise .7s .54s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            원본 증명
          </div>
          <div
            className="absolute box-border text-[13.5px] tracking-[-.01em]"
            style={{
              left: '272px',
              top: `${proofTop + 32}px`,
              width: '896px',
              padding: '24px',
              borderRadius: '14px',
              background: 'var(--color-surface-inset)',
              animation: 'unfold .8s .56s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            {provenance.isPending && <div className="text-faint">불러오는 중…</div>}

            {/* 재조회가 실패해도 직전 응답은 캐시에 남는다 — 안내와 증명을 같이 띄우지 않는다. */}
            {provenanceMissing && !proof && (
              <>
                <div className="text-body">아직 온체인에 기록되지 않았습니다.</div>
                <div className="text-muted" style={{ marginTop: '8px' }}>
                  등록이 끝나면 원작자 주소와 트랜잭션이 여기에 표시됩니다.
                </div>
              </>
            )}

            {provenance.isError && !provenanceMissing && !proof && (
              <div className="flex items-center" style={{ gap: '12px' }}>
                <span style={{ color: 'rgba(255,196,214,.9)' }}>
                  원본 증명을 불러오지 못했습니다.
                </span>
                <button
                  className="text-muted cursor-pointer underline"
                  onClick={() => void provenance.refetch()}
                  style={{ background: 'none', border: 0, padding: 0 }}
                  type="button"
                >
                  다시 시도
                </button>
              </div>
            )}

            {proof && (
              <>
                <div>
                  {(
                    [
                      [
                        '원작자',
                        `${proof.owner.nickname}  ${shortAddress(proof.owner.walletAddress)}`,
                      ],
                      // 네트워크 이름은 손으로 적지 않는다 — chainId 로 찾는다.
                      [
                        '네트워크',
                        `${chainNameOf(proof.blockchain.chainId)} · ${proof.blockchain.chainId}`,
                      ],
                      ['트랜잭션', shortAddress(proof.blockchain.transactionHash)],
                      ['블록', blockLabel(proof.blockchain.blockNumber)],
                      ['등록 시각', dateTimeLabel(proof.blockchain.registeredAt)],
                    ] as const
                  ).map(([label, value], i) => (
                    <div className="flex" key={label} style={{ marginTop: i === 0 ? 0 : '10px' }}>
                      <div className="text-muted" style={{ width: '88px' }}>
                        {label}
                      </div>
                      <div className="text-body">{value}</div>
                    </div>
                  ))}
                </div>

                <div style={{ marginTop: '18px' }}>
                  {(
                    [
                      ['원작자 주소', proof.verification.ownerMatched, onChainOwner],
                      ['메타데이터 해시', proof.verification.metadataMatched, onChainHash],
                    ] as const
                  ).map(([label, matched, value], i) => (
                    <div
                      className="flex items-center"
                      key={label}
                      style={{ gap: '10px', marginTop: i === 0 ? 0 : '8px' }}
                    >
                      <span
                        style={{
                          color: matched ? 'var(--color-accent-bright)' : 'rgba(255,196,214,.9)',
                        }}
                      >
                        {matched ? `✓ ${label} 일치` : `✗ ${label} 불일치`}
                      </span>
                      <span className="text-faint text-[12.5px]">{value}</span>
                    </div>
                  ))}
                </div>

                {/* 외부 주소라 paths 를 거치지 않는다. */}
                <a
                  className="text-accent-bright inline-block"
                  href={proof.blockchain.explorerUrl}
                  rel="noreferrer"
                  style={{ marginTop: '18px' }}
                  target="_blank"
                >
                  Polygonscan에서 보기 →
                </a>

                <div className="text-faint text-[12.5px]" style={{ marginTop: '16px' }}>
                  노드 구성·연결 관계·프롬프트는 블록체인에 기록하지 않습니다. 소유권과 해시만
                  기록합니다.
                </div>
              </>
            )}
          </div>

          <div
            className="absolute text-[16px] font-semibold tracking-[-.01em] text-white"
            style={{
              left: '272px',
              top: `${reviewsTop}px`,
              animation: 'rise .7s .58s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            리뷰 {compactCount(agent.reviewCount)}
          </div>
          <div
            className="absolute box-border text-[13.5px] tracking-[-.01em]"
            style={{
              left: '272px',
              top: `${reviewsTop + 32}px`,
              width: '896px',
              padding: '24px',
              borderRadius: '14px',
              background: 'var(--color-surface-inset)',
              animation: 'unfold .8s .6s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            {/* 목록 대신 들어가는 한 줄들은 가운데로 둔다 — 왼쪽에 붙이면 첫 번째
                리뷰처럼 보인다. 빈 화면을 가운데 두는 것은 empty-state 와 같은 규칙이다. */}
            {reviews.isPending && <div className="text-faint text-center">불러오는 중…</div>}

            {/* 상세가 reviewCount 를 주므로 0건은 실패가 아니라 정상이다. */}
            {!reviews.isPending && !reviews.isError && reviewItems.length === 0 && (
              <div className="text-muted text-center">
                아직 리뷰가 없습니다. 첫 리뷰를 남겨 보세요.
              </div>
            )}

            {/* 다음 장이 실패한 것은 목록 전체의 실패가 아니다 — 이미 받은 건은 남긴다.
                여기서도 다시 받는 중이면 오류 대신 진행을 보여 준다. */}
            {reviews.isError &&
              reviewItems.length === 0 &&
              (reviews.isFetching ? (
                <div className="text-faint text-center">불러오는 중…</div>
              ) : (
                <div className="flex items-center justify-center" style={{ gap: '12px' }}>
                  <span style={{ color: 'rgba(255,196,214,.9)' }}>리뷰를 불러오지 못했습니다.</span>
                  <button
                    className="text-muted cursor-pointer underline"
                    onClick={() => void reviews.refetch()}
                    style={{ background: 'none', border: 0, padding: 0 }}
                    type="button"
                  >
                    다시 시도
                  </button>
                </div>
              ))}

            {reviewItems.map((review, i) => (
              <div
                key={review.reviewId}
                style={{
                  borderTop: i === 0 ? 0 : '1px solid rgba(255,255,255,.16)',
                  paddingTop: i === 0 ? 0 : '16px',
                  marginTop: i === 0 ? 0 : '16px',
                }}
              >
                <div className="flex items-center" style={{ gap: '10px' }}>
                  <span className="text-body font-semibold">{maskName(review.authorNickname)}</span>
                  <span style={{ color: 'var(--color-accent-bright)' }}>★ {review.rating}</span>
                  <span className="text-faint text-[12.5px]">{timeLabel(review.createdAt)}</span>
                </div>
                {/* 2줄로 자른다 — 건마다 높이가 같아야 페이지 바닥을 미리 계산할 수 있다. */}
                <div
                  className="text-muted"
                  style={{
                    marginTop: '6px',
                    display: '-webkit-box',
                    WebkitLineClamp: 2,
                    WebkitBoxOrient: 'vertical',
                    overflow: 'hidden',
                  }}
                >
                  {review.content}
                </div>
              </div>
            ))}

            {(reviews.hasNextPage || reviews.isFetchNextPageError) &&
              (reviewAutoLoad ? (
                /* 빈 자리 한가운데에 놓는다. 위에 붙여 두면 마지막 리뷰의 꼬리처럼
                   보여서 안내인 줄 모른다. */
                <div
                  className="text-faint flex items-center justify-center text-[12.5px]"
                  style={{ marginTop: '16px', height: `${REVIEW_LANDING - 16}px` }}
                >
                  {/* 진행 중이 실패보다 앞선다. 재시도 중에도 오류 문구가 그대로면
                      눌린 것인지 알 수 없다 — 재시도는 백오프까지 붙어 몇 초가 걸린다.
                      다시 실패하면 버튼이 돌아온다. */}
                  {reviewHold || reviews.isFetchingNextPage ? (
                    '불러오는 중…'
                  ) : reviews.isFetchNextPageError ? (
                    <button
                      className="text-muted cursor-pointer underline"
                      onClick={() => void reviews.fetchNextPage()}
                      style={{ background: 'none', border: 0, padding: 0 }}
                      type="button"
                    >
                      리뷰를 더 불러오지 못했습니다. 다시 시도
                    </button>
                  ) : null}
                </div>
              ) : (
                <button
                  className="text-muted w-full cursor-pointer"
                  disabled={reviews.isFetchingNextPage}
                  onClick={() => {
                    setAutoLoadFor(agentId)
                    void reviews.fetchNextPage()
                  }}
                  style={{
                    marginTop: '20px',
                    padding: '8px 0',
                    borderRadius: '10px',
                    border: '1px solid rgba(255,255,255,.16)',
                    background: 'none',
                  }}
                  type="button"
                >
                  더 보기
                </button>
              ))}
          </div>

          {/* 페이지의 마지막 1px. 여기가 보인다는 것은 바닥에 닿았다는 뜻이다.
              센티넬을 목록 끝에 두면 카드 여백·페이지 여백만큼(156px) 먼저 걸려,
              사용자가 도착하기 전에 다음 장이 화면 밖에 이미 그려져 있다. */}
          <div
            ref={reviewEndRef}
            style={{ position: 'absolute', top: `${pageHeight - 1}px`, left: 0, height: '1px' }}
          ></div>
        </div>
      </div>
    </>
  )
}
