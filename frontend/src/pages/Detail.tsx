import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'

import {
  addAgentFavorite,
  getAgentProvenance,
  getAgentReviews,
  getMarketplaceAgent,
  removeAgentFavorite,
  type AgentDetail,
  type AgentProvenance,
} from '@/api/agents'
import { ApiError, AUTH_ERROR } from '@/api/errors'
import {
  blockLabel,
  compactCount,
  dateTimeLabel,
  maskName,
  mismatchReasonLabel,
  priceLabel,
  ratingLabel,
  runLabel,
  shortAddress,
  successLabel,
  timeLabel,
} from '@/lib/format'
import { queryKeys } from '@/lib/queryKeys'
import { chainNameOf } from '@/lib/wallet/wagmi'
import { paths } from '@/routes/paths'

/** 안내 카드. 그리드 안쪽(272~1168, 896px)에서 가운데 — 272 + (896 - 620) / 2 = 410.
 *  translateX 로 중앙 정렬하지 않는다: transform 이 걸리면 backdrop 루트가 되어
 *  카드의 backdrop-filter 가 바깥 배경을 못 가져온다. */
const NOTICE_BOX = { left: '410px', top: '360px', width: '620px' } as const

/** 빈 상태는 실패가 아니라 다음 행동을 고르는 자리다 — 행동을 최소 하나 준다.
 *  <empty-state> 가 JSON 으로 받는다(`components/elements/types.ts` 의 EmptyAction). */
const NOT_FOUND_ACTIONS = JSON.stringify([
  { label: '에이전트 둘러보기', href: paths.search, primary: true },
  { label: '홈으로', href: paths.home },
])

const ERROR_ACTIONS = JSON.stringify([
  { label: '다시 시도', event: 'retry', primary: true },
  { label: '목록으로', href: paths.search },
])

/** 즐겨찾기 실패 중 로그인 문제. 실제로 오는 것은 `AUTH_005` 뿐이다(`api/errors.ts`). */
const AUTH_CODES: string[] = [AUTH_ERROR.NOT_IDENTIFIED, AUTH_ERROR.NOT_IDENTIFIABLE]

/** 한 번에 불러오는 리뷰 개수. */
const REVIEW_PAGE_SIZE = 5

/** 버튼을 누른 뒤 목록 끝에 남겨 두는 빈 자리. 센티넬과 '불러오는 중'·'다시 시도' 가
 *  여기 들어가고, 다음 장은 이 자리부터 채워진다. 바닥에 닿기 전에 끝이 보여야
 *  리뷰가 붙는 것이 눈에 들어온다. */
const REVIEW_LANDING = 84

/** 바닥에 닿고 나서 다음 장을 받기까지 두는 시간.
 *  닿자마자 채우면 다섯 줄이 한 번에 튀어 깜빡인 것처럼 보인다. 기다리는 동안
 *  '불러오는 중' 을 먼저 보여 주면 목록이 늘어난 것이 사건으로 읽힌다.
 *  튕겨서 바닥을 스치기만 한 경우에는 벗어나는 순간 취소된다. */
const REVIEW_HOLD_MS = 400

/** 글자 색. 어두운 배경에서 읽히도록 공용 토큰(muted .66 · faint .48)보다 진하게 둔다. */
const INK = {
  body: 'rgba(255,248,252,.94)',
  label: 'rgba(255,236,246,.84)',
  faint: 'rgba(255,236,246,.7)',
  bad: 'rgba(255,196,214,.95)',
}

/** 아직 블록에 안 들어간 트랜잭션은 되돌려질 수 있다 — 검증됐다고 말하지 않는다. */
function proofBadgeOf(proof: AgentProvenance) {
  if (proof.blockchain.transactionStatus === 'PENDING')
    return { text: '블록 확정 대기 중', color: INK.label }
  if (!proof.verification.verified) return { text: '✗ 원본 검증 불일치', color: INK.bad }
  return { text: '✓ 원본 검증됨', color: 'var(--color-accent-bright)' }
}

/** 증명 카드의 대조 한 줄. */
type ProofRow = [label: string, matched: boolean, value: string]

export default function Detail() {
  const { agentId } = useParams<{ agentId: string }>()

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

  // 상세·증명과 나란히 나간다.
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

     그래도 자라는 것이 보이도록 목록 끝에 빈 자리(REVIEW_LANDING)를 남긴다.
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
      // 사용한 에이전트 탭은 지금 없지만 API 가 생기면 다시 붙이므로 남겨 둔다.
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
  const proof = provenance.data
  const proofBadge = proof && proofBadgeOf(proof)
  // 대조해 보라고 두는 값이라 주소는 줄이지 않는다.
  const onChainOwner = proof?.verification.onChainOwnerAddress ?? ''
  const onChainHash = proof?.verification.onChainMetadataHash
    ? `${proof.verification.onChainMetadataHash.slice(0, 18)}…`
    : ''
  const proofPending = proof?.blockchain.transactionStatus === 'PENDING'
  const proofReason = proof?.verification.mismatchReason ?? null
  // 체인에 없으면 대조할 값이 비어 있다. 빈칸 대신 이유를 적는다.
  const onChainMissing = proofReason === 'NOT_ON_CHAIN' ? '체인 기록 없음' : ''
  // 확정 전과 체인 기록 없음은 대조할 상대가 없다. 불일치로 적지 않는다.
  const noCompare = proofPending ? '대조 대기' : onChainMissing ? '대조 불가' : ''
  const proofRows: ProofRow[] = proof
    ? [
        ['원작자 주소', proof.verification.ownerMatched, onChainOwner || onChainMissing],
        ['메타데이터 해시', proof.verification.metadataMatched, onChainHash || onChainMissing],
        // 두 값은 맞는데 서버에 적어 둔 기록이 체인과 다르다. 어긋난 줄을 따로 보여 준다.
        // 이유는 배지 옆 줄이 이미 말하므로 값 칸은 비운다 — 대조할 단일 값도 없다.
        ...(proofReason === 'RECORD_MISMATCH' ? [['서버 기록', false, ''] as ProofRow] : []),
      ]
    : []
  // 확정 대기는 배지가 이미 말한다.
  const mismatchText =
    proof && !proof.verification.verified && !proofPending
      ? mismatchReasonLabel(proof.verification.mismatchReason)
      : null

  return (
    <div
      className="scroll-quiet absolute top-0 left-0 overflow-x-hidden"
      id="detailScroll"
      style={{ width: '1440px', height: '1024px' }}
    >
      {/* 위에서부터 쌓는다. 설명·리뷰 길이만큼 아래가 따라 내려간다. */}
      <div
        className="flex flex-col"
        style={{ marginLeft: '272px', width: '896px', paddingTop: '132px' }}
      >
        <a
          className="flex items-center self-start text-[16px] font-medium tracking-[-.01em]"
          href={paths.search}
          style={{
            height: '28px',
            gap: '7px',
            color: INK.label,
            animation: 'rise .6s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          ← 목록으로
        </a>

        <div className="flex items-start" style={{ marginTop: '14px', gap: '32px' }}>
          <div
            className="relative flex-none overflow-hidden bg-[rgba(255,255,255,.10)]"
            style={{
              width: '128px',
              height: '128px',
              borderRadius: '18px',
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
              <img className="relative h-full w-full object-cover" src={agent.iconUrl} alt="" />
            ) : (
              <div
                className="absolute inset-0 flex items-center justify-center text-[48px] font-bold"
                style={{ color: 'rgba(255,226,238,.85)' }}
              >
                {agent.name.trim().charAt(0).toUpperCase()}
              </div>
            )}
          </div>

          <div
            className="flex min-w-0 flex-1 flex-col"
            style={{ gap: '10px', animation: 'rise .7s .08s cubic-bezier(.2,.8,.25,1) both' }}
          >
            <div className="flex flex-wrap items-center" style={{ gap: '12px' }}>
              <div
                className="text-[36px] leading-[1.2] font-bold tracking-[-.02em] text-white"
                style={{ textShadow: '0 2px 20px rgba(35,8,45,.6)' }}
              >
                {agent.name}
              </div>
              {proofBadge && (
                <div
                  className="flex flex-none items-center text-[14.5px] font-bold tracking-[-.01em]"
                  style={{
                    height: '30px',
                    padding: '0 14px',
                    borderRadius: '15px',
                    background: 'rgba(255,255,255,.1)',
                    color: proofBadge.color,
                  }}
                >
                  {proofBadge.text}
                </div>
              )}
              {mismatchText && (
                <div className="flex-none text-[14.5px] font-medium" style={{ color: INK.bad }}>
                  {mismatchText}
                </div>
              )}
            </div>
            <div
              className="text-[18px] font-medium tracking-[-.01em]"
              style={{ lineHeight: '1.5', color: INK.body }}
            >
              {agent.summary}
            </div>
            <div className="flex items-center" style={{ marginTop: '4px', gap: '12px' }}>
              <div
                className="flex-none overflow-hidden rounded-full"
                style={{ width: '40px', height: '40px', background: 'rgba(255,255,255,.14)' }}
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
                <div className="text-[16px] font-bold tracking-[-.01em] text-white">
                  {agent.owner.nickname ?? '—'}
                </div>
                <div className="text-[14px] font-medium" style={{ color: INK.faint }}>
                  만든 사람
                </div>
              </div>
            </div>
          </div>

          <div
            className="flex flex-none flex-col items-end"
            style={{
              marginTop: '30px',
              gap: '8px',
              animation: 'rise .7s .2s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            <div className="flex" style={{ gap: '12px' }}>
              <div
                onClick={handleToggleFav}
                style={{
                  width: '136px',
                  height: '54px',
                  borderRadius: '27px',
                  background: `${favBg}`,
                  backdropFilter: 'blur(18px)',
                  WebkitBackdropFilter: 'blur(18px)',
                  gap: '8px',
                  opacity: favorite.isPending ? 0.6 : 1,
                  transition: 'background .18s,opacity .18s',
                }}
                className="flex cursor-pointer items-center justify-center hover:bg-[rgba(255,255,255,.2)]"
              >
                <div className="text-[17px]" style={{ color: `${favStarColor}` }}>
                  ★
                </div>
                <div className="text-[17px] font-bold tracking-[-.01em] text-white">즐겨찾기</div>
              </div>
              <a
                href={paths.run.waiting}
                style={{
                  width: '164px',
                  height: '54px',
                  borderRadius: '27px',
                  background: 'var(--color-accent)',
                  boxShadow: '0 10px 26px rgba(140,100,130,.24)',
                  color: 'var(--color-accent-ink)',
                  transition: 'transform .18s',
                }}
                className="flex items-center justify-center text-[18px] font-bold tracking-[-.01em] hover:-translate-y-[2px]"
              >
                실행하기
              </a>
            </div>
            {/* 실패를 조용히 넘기지 않는다. 토스트가 없어 버튼 아래에 한 줄로 둔다. */}
            {favMessage && (
              <div
                className="text-[14px] font-medium tracking-[-.01em]"
                style={{ color: INK.bad, animation: 'fadeIn .2s ease both' }}
              >
                {favMessage}
              </div>
            )}
          </div>
        </div>

        <div
          style={{ marginTop: '36px', height: '1px', background: 'rgba(255,255,255,.18)' }}
        ></div>

        <div
          className="flex flex-wrap"
          style={{
            marginTop: '24px',
            gap: '10px',
            animation: 'rise .7s .28s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          {chips.map((label) => (
            <div
              key={label}
              className="flex items-center text-[15px] font-semibold tracking-[-.01em] whitespace-nowrap"
              style={{
                height: '36px',
                padding: '0 16px',
                borderRadius: '100px',
                background: 'rgba(255,255,255,.12)',
                color: INK.body,
              }}
            >
              {label}
            </div>
          ))}
          {/* 명세 「가격은 한 줄이다」 — 실행 1회당 가격만 보여준다. 토큰 단위 단가는 없다. */}
          <div
            className="flex flex-none items-center text-[15px] font-bold tracking-[-.01em] whitespace-nowrap"
            style={{
              height: '36px',
              padding: '0 16px',
              borderRadius: '100px',
              background: 'rgba(185,141,169,.3)',
              color: '#ffe4f0',
            }}
          >
            {priceLabel(agent.price)}
          </div>
        </div>

        <SectionTitle delay=".32s">설명</SectionTitle>
        <div
          className="text-[17px] font-medium tracking-[-.01em]"
          style={{
            lineHeight: '1.75',
            color: INK.body,
            textWrap: 'pretty',
            whiteSpace: 'pre-line',
            animation: 'rise .7s .34s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          {agent.description}
        </div>

        {agent.usageExample && (
          <>
            <SectionTitle delay=".38s">입력 예시</SectionTitle>
            <div
              className="box-border"
              style={{
                padding: '24px',
                borderRadius: '14px',
                background: 'var(--color-surface-inset)',
                animation: 'unfold .8s .4s cubic-bezier(.2,.8,.25,1) both',
              }}
            >
              <div
                className="text-[16px] font-medium tracking-[-.01em]"
                style={{ lineHeight: '1.7', color: INK.body, whiteSpace: 'pre-line' }}
              >
                {agent.usageExample}
              </div>
            </div>
          </>
        )}

        <SectionTitle delay=".44s">원본 증명</SectionTitle>
        <div
          className="box-border text-[16px] tracking-[-.01em]"
          style={{
            padding: '24px',
            borderRadius: '14px',
            background: 'var(--color-surface-inset)',
            animation: 'unfold .8s .46s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          {provenance.isPending && <div style={{ color: INK.faint }}>불러오는 중…</div>}

          {/* 재조회가 실패해도 직전 응답은 캐시에 남는다 — 안내와 증명을 같이 띄우지 않는다. */}
          {provenanceMissing && !proof && (
            <>
              <div className="font-medium text-white">아직 온체인에 기록되지 않았습니다.</div>
              <div style={{ marginTop: '8px', color: INK.label }}>
                등록이 끝나면 원작자 주소와 트랜잭션이 여기에 표시됩니다.
              </div>
            </>
          )}

          {provenance.isError && !provenanceMissing && !proof && (
            <div className="flex items-center" style={{ gap: '12px' }}>
              <span className="font-medium" style={{ color: INK.bad }}>
                원본 증명을 불러오지 못했습니다.
              </span>
              <button
                className="cursor-pointer underline"
                onClick={() => void provenance.refetch()}
                style={{ background: 'none', border: 0, padding: 0, color: INK.label }}
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
                      proof.owner.nickname
                        ? `${proof.owner.nickname}  ${shortAddress(proof.owner.walletAddress)}`
                        : shortAddress(proof.owner.walletAddress),
                    ],
                    // 네트워크 이름은 손으로 적지 않는다 — chainId 로 찾는다.
                    [
                      '네트워크',
                      `${chainNameOf(proof.blockchain.chainId)} · ${proof.blockchain.chainId}`,
                    ],
                    [
                      '트랜잭션',
                      proof.blockchain.transactionHash
                        ? shortAddress(proof.blockchain.transactionHash)
                        : '—',
                    ],
                    ['블록', blockLabel(proof.blockchain.blockNumber)],
                    ['등록 시각', dateTimeLabel(proof.blockchain.registeredAt)],
                  ] as const
                ).map(([label, value], i) => (
                  <div className="flex" key={label} style={{ marginTop: i === 0 ? 0 : '12px' }}>
                    <div className="flex-none" style={{ width: '104px', color: INK.label }}>
                      {label}
                    </div>
                    <div className="font-semibold text-white">{value}</div>
                  </div>
                ))}
              </div>

              <div style={{ marginTop: '20px' }}>
                {proofRows.map(([label, matched, value], i) => (
                  <div
                    className="flex items-center"
                    key={label}
                    style={{ gap: '10px', marginTop: i === 0 ? 0 : '10px' }}
                  >
                    <span
                      className="font-semibold"
                      style={{
                        color: noCompare
                          ? INK.label
                          : matched
                            ? 'var(--color-accent-bright)'
                            : INK.bad,
                      }}
                    >
                      {noCompare
                        ? `${label} ${noCompare}`
                        : matched
                          ? `✓ ${label} 일치`
                          : `✗ ${label} 불일치`}
                    </span>
                    <span className="text-[14px]" style={{ color: INK.faint }}>
                      {value}
                    </span>
                  </div>
                ))}
              </div>

              {proof.verification.verifiedAt && (
                <div className="text-[14px]" style={{ marginTop: '12px', color: INK.faint }}>
                  마지막 검증 {dateTimeLabel(proof.verification.verifiedAt)}
                </div>
              )}

              {/* 외부 주소라 paths 를 거치지 않는다. SSAFY 체인은 탐색기가 없어 기록 조회 주소다. */}
              {proof.blockchain.explorerUrl && (
                <a
                  className="text-accent-bright inline-block font-semibold"
                  href={proof.blockchain.explorerUrl}
                  rel="noreferrer"
                  style={{ marginTop: '18px' }}
                  target="_blank"
                >
                  체인 기록 보기 →
                </a>
              )}

              <div className="text-[14px]" style={{ marginTop: '16px', color: INK.faint }}>
                노드 구성·연결 관계·프롬프트는 블록체인에 기록하지 않습니다. 소유권과 해시만
                기록합니다.
              </div>
            </>
          )}
        </div>

        <SectionTitle delay=".5s">리뷰 {compactCount(agent.reviewCount)}</SectionTitle>
        <div
          className="box-border text-[16px] tracking-[-.01em]"
          style={{
            padding: '24px',
            borderRadius: '14px',
            background: 'var(--color-surface-inset)',
            animation: 'unfold .8s .52s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          {/* 목록 대신 들어가는 한 줄들은 가운데로 둔다 — 왼쪽에 붙이면 첫 번째
              리뷰처럼 보인다. 빈 화면을 가운데 두는 것은 empty-state 와 같은 규칙이다. */}
          {reviews.isPending && (
            <div className="text-center" style={{ color: INK.faint }}>
              불러오는 중…
            </div>
          )}

          {/* 상세가 reviewCount 를 주므로 0건은 실패가 아니라 정상이다.
              쓰라고 부추기지 않는다 — 리뷰를 남기는 화면이 아직 없다. */}
          {!reviews.isPending && !reviews.isError && reviewItems.length === 0 && (
            <div className="text-center font-medium" style={{ color: INK.label }}>
              아직 리뷰가 없습니다.
            </div>
          )}

          {/* 다음 장이 실패한 것은 목록 전체의 실패가 아니다 — 이미 받은 건은 남긴다.
              여기서도 다시 받는 중이면 오류 대신 진행을 보여 준다. */}
          {reviews.isError &&
            reviewItems.length === 0 &&
            (reviews.isFetching ? (
              <div className="text-center" style={{ color: INK.faint }}>
                불러오는 중…
              </div>
            ) : (
              <div className="flex items-center justify-center" style={{ gap: '12px' }}>
                <span className="font-medium" style={{ color: INK.bad }}>
                  리뷰를 불러오지 못했습니다.
                </span>
                <button
                  className="cursor-pointer underline"
                  onClick={() => void reviews.refetch()}
                  style={{ background: 'none', border: 0, padding: 0, color: INK.label }}
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
                paddingTop: i === 0 ? 0 : '18px',
                marginTop: i === 0 ? 0 : '18px',
              }}
            >
              <div className="flex items-center" style={{ gap: '10px' }}>
                <span className="font-bold text-white">{maskName(review.authorNickname)}</span>
                <span className="font-semibold" style={{ color: 'var(--color-accent-bright)' }}>
                  ★ {review.rating}
                </span>
                <span className="text-[14px]" style={{ color: INK.faint }}>
                  {timeLabel(review.createdAt)}
                </span>
              </div>
              <div
                className="font-medium"
                style={{
                  marginTop: '8px',
                  lineHeight: '1.65',
                  color: INK.body,
                  whiteSpace: 'pre-line',
                }}
              >
                {review.content}
              </div>
            </div>
          ))}

          {(hasNextPage || isFetchNextPageError) &&
            (reviewAutoLoad ? (
              /* 빈 자리 한가운데에 놓는다. 위에 붙여 두면 마지막 리뷰의 꼬리처럼
                 보여서 안내인 줄 모른다. */
              <div
                className="flex items-center justify-center text-[14px]"
                style={{ marginTop: '16px', height: `${REVIEW_LANDING - 16}px`, color: INK.faint }}
              >
                {/* 진행 중이 실패보다 앞선다. 재시도 중에도 오류 문구가 그대로면
                    눌린 것인지 알 수 없다 — 재시도는 백오프까지 붙어 몇 초가 걸린다.
                    다시 실패하면 버튼이 돌아온다. */}
                {reviewHold || isFetchingNextPage ? (
                  '불러오는 중…'
                ) : isFetchNextPageError ? (
                  <button
                    className="cursor-pointer underline"
                    onClick={() => void fetchNextPage()}
                    style={{ background: 'none', border: 0, padding: 0, color: INK.label }}
                    type="button"
                  >
                    리뷰를 더 불러오지 못했습니다. 다시 시도
                  </button>
                ) : null}
              </div>
            ) : (
              <button
                className="w-full cursor-pointer font-semibold disabled:cursor-default"
                disabled={isFetchingNextPage}
                onClick={() => {
                  setAutoLoadFor(agentId)
                  void fetchNextPage()
                }}
                style={{
                  marginTop: '20px',
                  padding: '10px 0',
                  borderRadius: '10px',
                  border: '1px solid rgba(255,255,255,.2)',
                  background: 'none',
                  color: INK.label,
                }}
                type="button"
              >
                더 보기
              </button>
            ))}
        </div>

        {/* 페이지의 마지막 1px. 여기가 보인다는 것은 바닥에 닿았다는 뜻이다. */}
        <div style={{ height: '63px', flex: 'none' }}></div>
        <div ref={reviewEndRef} style={{ height: '1px', flex: 'none' }}></div>
      </div>
    </div>
  )
}

/** 섹션 제목. 카드와의 간격도 여기서 준다. */
function SectionTitle({ delay, children }: { delay: string; children: React.ReactNode }) {
  return (
    <div
      className="text-[21px] font-bold tracking-[-.01em] text-white"
      style={{
        marginTop: '48px',
        marginBottom: '14px',
        animation: `rise .7s ${delay} cubic-bezier(.2,.8,.25,1) both`,
      }}
    >
      {children}
    </div>
  )
}
