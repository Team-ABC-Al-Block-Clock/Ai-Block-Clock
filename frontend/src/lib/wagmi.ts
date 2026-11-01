import { defineChain } from 'viem'
import { createConfig, http } from 'wagmi'
import { injected } from 'wagmi/connectors'

/**
 * 지갑 연결 설정. MetaMask 하나만 붙인다 — RainbowKit·WalletConnect 를 쓰지 않아
 * projectId 가 필요 없고, 연결 대화상자는 `components/wallet-dialog.ts` 가 그린다.
 */

/** SSAFY 체인. wagmi/chains 에 없는 체인이라 여기서 정의한다.
 *  탐색기가 없어 `blockExplorers` 를 두지 않는다 — 지갑에 체인을 추가할 때도 그대로 빠진다.
 *  가스비가 0 이고 EIP-1559 가 없다. 거래를 보낼 때 레거시 + gasPrice 0 으로 맞춘다(`lib/abcToken.ts`). */
export const chain = defineChain({
  id: 31221,
  name: 'SSAFY',
  nativeCurrency: { name: 'SSAFY', symbol: 'SSF', decimals: 18 },
  rpcUrls: { default: { http: ['https://rpc.ssafy-blockchain.com'] } },
})

/** 화면에 네트워크 이름을 적을 때도 이 값에서 가져온다.
 *  손으로 적으면 메인넷으로 옮길 때 화면마다 갈라진다. */
export const chainLabel = chain.name

/** 배너가 "지금 어디에 붙어 있는지" 를 말할 때 쓴다. 모르는 체인은 번호로. */
const CHAIN_NAMES: Record<number, string> = {
  1: 'Ethereum',
  10: 'OP Mainnet',
  56: 'BNB Smart Chain',
  137: 'Polygon',
  8453: 'Base',
  42161: 'Arbitrum One',
  11155111: 'Sepolia',
  [chain.id]: chain.name,
}

export function chainNameOf(id: number | undefined): string {
  if (id === undefined) return '다른 네트워크'
  return CHAIN_NAMES[id] ?? `체인 ${id}`
}

export const wagmiConfig = createConfig({
  chains: [chain],
  // target 을 지정하지 않으면 `window.ethereum` 을 선점한 다른 지갑 확장을 잡는다.
  connectors: [injected({ target: 'metaMask' })],
  transports: { [chain.id]: http() },
})

declare module 'wagmi' {
  interface Register {
    config: typeof wagmiConfig
  }
}

/** 지갑 오류를 화면 문구로. wagmi 가 `cause` 로 감싸 올려보내기도 해서 몇 겹 벗긴다. */
export function describeWalletError(error: unknown): { message: string; code: string | null } {
  for (let e: unknown = error, depth = 0; e && depth < 4; e = (e as { cause?: unknown }).cause) {
    depth += 1
    const name = e instanceof Error ? e.name : ''
    const code = (e as { code?: unknown }).code

    // EIP-1193 의 4001 — 사용자가 지갑 창에서 거절했다
    if (code === 4001 || name === 'UserRejectedRequestError') {
      return {
        message: '지갑에서 요청을 거절했습니다. 다시 시도해 주세요.',
        code: 'ACTION_REJECTED · 4001',
      }
    }
    // 확장이 없다
    if (name === 'ProviderNotFoundError' || name === 'ConnectorNotFoundError') {
      return {
        message: 'MetaMask 확장을 찾을 수 없습니다. 설치한 뒤 다시 시도해 주세요.',
        code: null,
      }
    }
  }

  const message = error instanceof Error ? error.message : ''
  return { message: message || '지갑 연결에 실패했습니다. 다시 시도해 주세요.', code: null }
}
