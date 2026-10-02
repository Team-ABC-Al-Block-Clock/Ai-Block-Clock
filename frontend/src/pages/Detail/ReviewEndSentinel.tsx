import { useEffect, useRef } from 'react'

/** 바닥에 닿고 나서 다음 장을 받기까지 두는 시간.
 *  닿자마자 채우면 다섯 줄이 한 번에 튀어 깜빡인 것처럼 보인다. 기다리는 동안
 *  '불러오는 중' 을 먼저 보여 주면 목록이 늘어난 것이 사건으로 읽힌다.
 *  튕겨서 바닥을 스치기만 한 경우에는 벗어나는 순간 취소된다. */
const REVIEW_HOLD_MS = 400

interface ReviewEndSentinelProps {
  /** '더 보기' 를 누른 뒤이고, 다음 장이 있고, 직전 장이 실패하지 않았다. */
  enabled: boolean
  isFetchingNextPage: boolean
  onHold: (holding: boolean) => void
  onReach: () => void
}

/**
 * 페이지의 마지막 1px. 여기가 보인다는 것은 바닥에 닿았다는 뜻이다.
 *
 * root 를 주지 않아도 된다 — 교차 계산은 조상의 클립(#detailScroll)까지 함께 본다.
 * rootMargin 은 두지 않는다. 조금이라도 미리 받으면 사용자가 끝에 닿기 전에 다음 장이 화면 밖에
 * 그려져, 내려가 보면 처음부터 다 있었던 것처럼 보인다. 바닥에 닿은 뒤에만 받는다.
 */
export default function ReviewEndSentinel({
  enabled,
  isFetchingNextPage,
  onHold,
  onReach,
}: ReviewEndSentinelProps) {
  const ref = useRef<HTMLDivElement>(null)
  const latest = useRef({ onHold, onReach })
  useEffect(() => {
    latest.current = { onHold, onReach }
  })

  useEffect(() => {
    const node = ref.current
    if (!node || !enabled) return
    let hold: ReturnType<typeof setTimeout> | undefined
    const observer = new IntersectionObserver(([entry]) => {
      clearTimeout(hold)
      // 바닥을 벗어나면 기다리던 것을 취소한다 — 스쳐 지나간 것은 요청이 아니다.
      if (!entry.isIntersecting || isFetchingNextPage) return latest.current.onHold(false)
      latest.current.onHold(true)
      hold = setTimeout(() => {
        latest.current.onHold(false)
        latest.current.onReach()
      }, REVIEW_HOLD_MS)
    })
    observer.observe(node)
    return () => {
      clearTimeout(hold)
      observer.disconnect()
    }
  }, [enabled, isFetchingNextPage])

  return <div ref={ref} style={{ height: '1px', flex: 'none' }}></div>
}
