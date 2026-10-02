import type { AgentReview } from '@/api/agents'
import { maskName, timeLabel } from '@/lib/format'

import { INK } from './ink'

/** 버튼을 누른 뒤 목록 끝에 남겨 두는 빈 자리. 센티넬과 '불러오는 중'·'다시 시도' 가
 *  여기 들어가고, 다음 장은 이 자리부터 채워진다. 바닥에 닿기 전에 끝이 보여야
 *  리뷰가 붙는 것이 눈에 들어온다. */
const REVIEW_LANDING = 84

const LINK_BUTTON = { background: 'none', border: 0, padding: 0, color: INK.label } as const

interface ReviewListProps {
  items: AgentReview[]
  isPending: boolean
  isError: boolean
  isFetching: boolean
  onRetry: () => void
  hasNextPage: boolean
  isFetchingNextPage: boolean
  isFetchNextPageError: boolean
  onFetchNextPage: () => void
  /** '더 보기' 를 누른 뒤 — 끝에 닿을 때마다 이어받는다. */
  autoLoad: boolean
  onStartAutoLoad: () => void
  /** 바닥에 닿아 다음 장을 기다리는 중. */
  hold: boolean
}

/** 리뷰 카드 — 목록과 그 대신 들어가는 안내, 다음 장 불러오기. */
export default function ReviewList({
  items,
  isPending,
  isError,
  isFetching,
  onRetry,
  hasNextPage,
  isFetchingNextPage,
  isFetchNextPageError,
  onFetchNextPage,
  autoLoad,
  onStartAutoLoad,
  hold,
}: ReviewListProps) {
  return (
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
      {isPending && (
        <div className="text-center" style={{ color: INK.faint }}>
          불러오는 중…
        </div>
      )}

      {/* 상세가 reviewCount 를 주므로 0건은 실패가 아니라 정상이다.
          쓰라고 부추기지 않는다 — 리뷰를 남기는 화면이 아직 없다. */}
      {!isPending && !isError && items.length === 0 && (
        <div className="text-center font-medium" style={{ color: INK.label }}>
          아직 리뷰가 없습니다.
        </div>
      )}

      {/* 다음 장이 실패한 것은 목록 전체의 실패가 아니다 — 이미 받은 건은 남긴다.
          여기서도 다시 받는 중이면 오류 대신 진행을 보여 준다. */}
      {isError &&
        items.length === 0 &&
        (isFetching ? (
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
              onClick={onRetry}
              style={LINK_BUTTON}
              type="button"
            >
              다시 시도
            </button>
          </div>
        ))}

      {items.map((review, i) => (
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
        (autoLoad ? (
          /* 빈 자리 한가운데에 놓는다. 위에 붙여 두면 마지막 리뷰의 꼬리처럼
             보여서 안내인 줄 모른다. */
          <div
            className="flex items-center justify-center text-[14px]"
            style={{ marginTop: '16px', height: `${REVIEW_LANDING - 16}px`, color: INK.faint }}
          >
            {/* 진행 중이 실패보다 앞선다. 재시도 중에도 오류 문구가 그대로면
                눌린 것인지 알 수 없다 — 재시도는 백오프까지 붙어 몇 초가 걸린다.
                다시 실패하면 버튼이 돌아온다. */}
            {hold || isFetchingNextPage ? (
              '불러오는 중…'
            ) : isFetchNextPageError ? (
              <button
                className="cursor-pointer underline"
                onClick={onFetchNextPage}
                style={LINK_BUTTON}
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
            onClick={onStartAutoLoad}
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
  )
}
