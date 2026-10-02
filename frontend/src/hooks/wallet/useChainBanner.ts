import { useState } from 'react'
import { useAccount, useSwitchChain } from 'wagmi'

import { chain, describeWalletError } from '@/lib/wallet/wagmi'

/** 화면 맨 위를 가로지르는 안내 띠의 높이. 앞쪽 내용이 이만큼 밀린다. */
export const BANNER_HEIGHT = 42

export type SwitchPhase = 'idle' | 'pending' | 'rejected' | 'failed'

/**
 * 네트워크 안내 띠의 상태.
 *
 * `App.tsx` 가 `visible` 을 보고 아래 내용을 밀지 정한다 — 띠와 여백이 같은
 * 조건을 봐야 헤더가 튀지 않는다.
 */
export function useChainBanner() {
  const { isConnected, chainId } = useAccount()
  const { switchChainAsync } = useSwitchChain()

  // 상태에 "어느 체인의 것인지" 를 함께 담는다. 체인이 바뀌면 자연히 초기값으로
  // 돌아가므로 effect 로 되돌릴 필요가 없다 — 옛 실패가 다음 체인까지 따라오지 않는다.
  const [attempt, setAttempt] = useState<{ chainId?: number; phase: SwitchPhase }>({
    phase: 'idle',
  })
  const [dismissedFor, setDismissedFor] = useState<number>()

  // 지갑을 안 붙인 사람에게 네트워크를 말해도 할 수 있는 일이 없다.
  const wrong = isConnected && chainId !== chain.id

  const switchToChain = async () => {
    setAttempt({ chainId, phase: 'pending' })
    try {
      // 지갑에 체인이 없으면(4902) wagmi 가 추가까지 알아서 한다.
      await switchChainAsync({ chainId: chain.id })
      // 성공하면 대개 chainId 가 바뀌어 위 조건이 알아서 idle 로 돌린다. 다만
      // 지갑이 resolve 만 하고 실제로 안 바꾸면 chainId 가 그대로라 버튼이 영영
      // 대기로 남는다 — 여기서 한 번 풀어 준다.
      setAttempt({ chainId, phase: 'idle' })
    } catch (e) {
      // 사용자가 거절한 것과 지갑이 못 바꾼 것은 다음 안내가 다르다.
      setAttempt({ chainId, phase: describeWalletError(e).code ? 'rejected' : 'failed' })
    }
  }

  return {
    visible: wrong && dismissedFor !== chainId,
    phase: attempt.chainId === chainId ? attempt.phase : ('idle' as SwitchPhase),
    chainId,
    switchToChain,
    dismiss: () => setDismissedFor(chainId),
  }
}

export type ChainBannerState = ReturnType<typeof useChainBanner>
