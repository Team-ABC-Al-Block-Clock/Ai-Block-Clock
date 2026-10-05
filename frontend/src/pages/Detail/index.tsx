import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'

import { getAgentProvenance, getMarketplaceAgent } from '@/api/agents'
import { ApiError } from '@/api/errors'
import { compactCount } from '@/lib/format'
import { queryKeys } from '@/lib/queryKeys'
import { paths } from '@/routes/paths'

import AgentHeader from './AgentHeader'
import DetailStatus from './DetailStatus'
import { INK } from './ink'
import ProvenanceCard from './ProvenanceCard'
import { provenanceView } from './provenanceView'
import ReviewEndSentinel from './ReviewEndSentinel'
import ReviewList from './ReviewList'
import SectionTitle from './SectionTitle'
import StatChips from './StatChips'
import { useAgentReviews } from './useAgentReviews'
import { useFavoriteToggle } from './useFavoriteToggle'

/**
 * 마켓 에이전트 상세.
 *
 * 상세 · 원본 증명 · 리뷰가 나란히 나간다. 위에서부터 쌓아 설명 · 리뷰 길이만큼 아래가 따라 내려간다.
 */
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

  const reviews = useAgentReviews(agentId)
  const favorite = useFavoriteToggle(agentId ?? '')

  // ID 가 없거나 404 면 '없는 에이전트' 다. 로딩 판정보다 먼저 와야 한다 —
  // enabled: false 면 isPending 이 계속 true 라서 뒤에 두면 영원히 '불러오는 중' 이 된다.
  if (!agentId || (error instanceof ApiError && error.status === 404))
    return <DetailStatus kind="notFound" />
  if (isError) return <DetailStatus kind="error" error={error} onRetry={() => void refetch()} />
  if (isPending || !agent) return <DetailStatus kind="loading" />

  const proof = provenance.data
  const view = proof ? provenanceView(proof) : null

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

        <AgentHeader
          agent={agent}
          badge={view?.badge ?? null}
          mismatchText={view?.mismatchText ?? null}
          favoritePending={favorite.isPending}
          favoriteMessage={favorite.message}
          onToggleFavorite={() => favorite.toggle(agent.favorited)}
        />

        <div
          style={{ marginTop: '36px', height: '1px', background: 'rgba(255,255,255,.18)' }}
        ></div>

        <StatChips agent={agent} />

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
        <ProvenanceCard
          proof={proof}
          rows={view?.rows ?? []}
          noCompare={view?.noCompare ?? ''}
          isPending={provenance.isPending}
          missing={provenanceMissing}
          isError={provenance.isError}
          onRetry={() => void provenance.refetch()}
        />

        <SectionTitle delay=".5s">리뷰 {compactCount(agent.reviewCount)}</SectionTitle>
        <ReviewList
          items={reviews.items}
          isPending={reviews.isPending}
          isError={reviews.isError}
          isFetching={reviews.isFetching}
          onRetry={reviews.refetch}
          hasNextPage={reviews.hasNextPage}
          isFetchingNextPage={reviews.isFetchingNextPage}
          isFetchNextPageError={reviews.isFetchNextPageError}
          onFetchNextPage={reviews.fetchNextPage}
          autoLoad={reviews.autoLoad}
          onStartAutoLoad={reviews.startAutoLoad}
          hold={reviews.hold}
        />

        {/* 페이지의 마지막 1px. 여기가 보인다는 것은 바닥에 닿았다는 뜻이다. */}
        <div style={{ height: '63px', flex: 'none' }}></div>
        <ReviewEndSentinel
          enabled={reviews.autoLoad && reviews.hasNextPage && !reviews.isFetchNextPageError}
          isFetchingNextPage={reviews.isFetchingNextPage}
          onHold={reviews.setHold}
          onReach={reviews.fetchNextPage}
        />
      </div>
    </div>
  )
}
