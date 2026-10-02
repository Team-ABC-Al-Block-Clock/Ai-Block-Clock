import { useAccount } from 'wagmi'

import { chain, chainLabel, chainNameOf } from '@/lib/wallet/wagmi'
import { useAuthStore } from '@/stores/authStore'

const PROVIDER_LABELS: Record<string, string> = { METAMASK: 'MetaMask' }

/** 주소는 로그인한 지갑이다 — 출금도 그리로 간다. 상태만 지금 붙은 지갑을 본다. */
export function useWalletStatus() {
  const profile = useAuthStore((s) => s.profile)
  const { address, chainId, status } = useAccount()

  const walletAddress = profile?.wallet.address ?? address ?? ''
  const network =
    status === 'connected' ? chainNameOf(chainId) : (profile?.wallet.network ?? chainLabel)
  const connection =
    status === 'connected'
      ? profile && address && address.toLowerCase() !== profile.wallet.address.toLowerCase()
        ? { text: '다른 지갑 연결됨', color: '#ffb0cd', dot: '#e9aec4' }
        : chainId === chain.id
          ? { text: '정상 연결', color: '#9ad0b4', dot: '#84bf9f' }
          : { text: '다른 네트워크', color: '#ffb0cd', dot: '#e9aec4' }
      : status === 'connecting' || status === 'reconnecting'
        ? { text: '연결 확인 중', color: 'rgba(255,236,246,.62)', dot: 'rgba(255,236,246,.45)' }
        : { text: '연결 끊김', color: '#ff8a8a', dot: '#ff8a8a' }

  const provider = profile?.wallet.provider ?? 'METAMASK'
  return { walletAddress, network, connection, provider: PROVIDER_LABELS[provider] ?? provider }
}
