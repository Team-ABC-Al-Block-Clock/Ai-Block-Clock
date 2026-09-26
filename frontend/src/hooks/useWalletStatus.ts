import { useAccount } from 'wagmi'

import { chain, chainLabel, chainNameOf } from '@/lib/wagmi'
import { useAuthStore } from '@/stores/authStore'

/** 지갑이 붙어 있으면 지갑이 말하는 값을, 아직이면 서버가 기억하는 값을 보여 준다. */
export function useWalletStatus() {
  const profile = useAuthStore((s) => s.profile)
  const { address, chainId, status } = useAccount()

  const walletAddress = address ?? profile?.wallet.address ?? ''
  const network =
    status === 'connected'
      ? chainNameOf(chainId)
      : profile
        ? chainNameOf(profile.wallet.chainId)
        : chainLabel
  const connection =
    status === 'connected'
      ? chainId === chain.id
        ? { text: '정상 연결', color: '#9ad0b4', dot: '#84bf9f' }
        : { text: '다른 네트워크', color: '#ffb0cd', dot: '#e9aec4' }
      : status === 'connecting' || status === 'reconnecting'
        ? { text: '연결 확인 중', color: 'rgba(255,236,246,.62)', dot: 'rgba(255,236,246,.45)' }
        : { text: '연결 끊김', color: '#ff8a8a', dot: '#ff8a8a' }

  return { walletAddress, network, connection, provider: profile?.wallet.provider ?? 'MetaMask' }
}
