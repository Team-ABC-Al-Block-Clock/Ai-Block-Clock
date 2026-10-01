import { useAccount, useReadContract } from 'wagmi'

import { ABC_TOKEN_ABI, ABC_TOKEN_ADDRESS, fromTokenValue } from '@/lib/wallet/abcToken'
import { chain } from '@/lib/wallet/wagmi'

/** 연결된 지갑이 들고 있는 abcToken. 지갑이 없거나 읽지 못하면 null 이다. */
export function useWalletAbcBalance() {
  const { address } = useAccount()
  const balance = useReadContract({
    address: ABC_TOKEN_ADDRESS,
    abi: ABC_TOKEN_ABI,
    functionName: 'balanceOf',
    args: address ? [address] : undefined,
    chainId: chain.id,
    query: { enabled: Boolean(address) },
  })
  return {
    amount: balance.data === undefined ? null : fromTokenValue(balance.data),
    refetch: balance.refetch,
  }
}
