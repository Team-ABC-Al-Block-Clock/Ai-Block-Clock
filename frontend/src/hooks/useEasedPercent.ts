import { useEffect, useRef, useState } from 'react'

import { SETTLE_MS, creepTo, rollRate, rollTo } from '@/lib/ease'

/**
 * 게이지를 부드럽게 움직인다.
 *
 * `settled` 는 서버가 알려 준 값이다. 거기까지는 일정한 속도로 굴러간다.
 * `ceiling` 은 도는 단계가 끝나면 닿을 값이다. `settled` 에 닿은 뒤로는 그 앞까지 기어간다 —
 * 단계 하나가 도는 내내 게이지가 서 있으면 멈춘 것처럼 보이기 때문이다. 닿지는 않는다.
 *
 * `settle` 이면 기어가기를 그만두고 짧게 `settled` 로 붙는다. 끝났거나 멎은 뒤인데 게이지만
 * 기어오르면 아직 도는 것처럼 보이고, 먼저 기어간 만큼 되돌아와야 할 수도 있다.
 */
export function useEasedPercent(settled: number, ceiling: number, settle: boolean): number {
  const [shown, setShown] = useState(settled)
  const current = useRef(settled)

  useEffect(() => {
    let frame: number | undefined

    const snap = () => {
      current.current = settled
      setShown(settled)
    }

    const perMs = rollRate(current.current, settled, settle ? SETTLE_MS : undefined)
    let last = performance.now()

    const step = (now: number) => {
      const dt = now - last
      last = now

      current.current =
        settle || current.current < settled
          ? rollTo(current.current, settled, dt, perMs)
          : creepTo(current.current, ceiling, dt)

      setShown(current.current)
      frame = requestAnimationFrame(step)
    }

    // 덮인 탭에서는 rAF 가 돌지 않는다. 기어가려다 옛 값에 굳는 것보다 바로 맞추는 편이 낫다.
    const onHide = () => {
      if (!document.hidden) return
      if (frame !== undefined) cancelAnimationFrame(frame)
      snap()
    }

    if (document.hidden) {
      snap()
    } else {
      frame = requestAnimationFrame(step)
      document.addEventListener('visibilitychange', onHide)
    }

    return () => {
      if (frame !== undefined) cancelAnimationFrame(frame)
      document.removeEventListener('visibilitychange', onHide)
    }
  }, [settled, ceiling, settle])

  return shown
}
