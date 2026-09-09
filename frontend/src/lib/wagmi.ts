import { createConfig, http } from 'wagmi'
import { polygonAmoy } from 'wagmi/chains'
import { injected } from 'wagmi/connectors'

/**
 * 지갑 연결 설정.
 *
 * MetaMask 하나만 붙인다. RainbowKit·WalletConnect 를 쓰지 않으므로 projectId 가
 * 필요 없고, 연결 대화상자는 `components/wallet-dialog.ts` 가 직접 그린다.
 */

/** 앱이 쓰는 체인. 화면에 네트워크 이름을 적을 때도 이 값을 쓴다 —
 *  문자열을 손으로 적으면 메인넷으로 옮길 때 화면마다 갈라진다. */
export const chain = polygonAmoy

/** 화면에 그대로 쓰는 네트워크 이름. `Polygon Amoy` */
export const chainLabel = chain.name

/** MetaMask 를 명시한다. 지정하지 않으면 `window.ethereum` 을 선점한
 *  다른 지갑 확장을 잡는다. */
export const wagmiConfig = createConfig({
  chains: [chain],
  connectors: [injected({ target: 'metaMask' })],
  transports: { [chain.id]: http() },
})

/** 훅이 이 설정의 체인·커넥터 타입을 알게 한다. */
declare module 'wagmi' {
  interface Register {
    config: typeof wagmiConfig
  }
}

/**
 * 지갑이 던진 오류를 화면 문구와 코드로 바꾼다. 182 의 서명 거절도 같은 함수를 쓴다.
 *
 * wagmi 는 원인을 `cause` 로 감싸서 올려보내기도 해서 몇 겹 벗겨 본다.
 */
export function describeWalletError(error: unknown): { message: string; code: string | null } {
  for (let e: unknown = error, depth = 0; e && depth < 4; e = (e as { cause?: unknown }).cause) {
    depth += 1
    const name = e instanceof Error ? e.name : ''
    const code = (e as { code?: unknown }).code

    // 사용자가 MetaMask 창에서 거절했다 — EIP-1193 의 4001
    if (code === 4001 || name === 'UserRejectedRequestError') {
      return {
        message: '지갑에서 요청을 거절했습니다. 다시 시도해 주세요.',
        code: 'ACTION_REJECTED · 4001',
      }
    }
    // 확장이 없다. wagmi 가 프로바이더를 못 찾을 때.
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
