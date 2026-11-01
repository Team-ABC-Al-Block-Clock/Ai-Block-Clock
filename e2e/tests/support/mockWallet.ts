import type { Page } from '@playwright/test'
import type { Hex } from 'viem'

import type { Wallet } from './auth'

/** 프론트가 쓰는 SSAFY 체인. frontend/src/lib/wagmi.ts 의 chain.id 와 같아야 한다. */
const SSAFY_CHAIN_ID = 31221

/**
 * MetaMask 대신 `window.ethereum` 을 심는다. 로그인 화면의 "지갑 연결 → 서명" 흐름 자체를 볼 때만 쓴다.
 * 다른 화면은 `login(page.context().request)` 로 쿠키만 넣는 편이 빠르고 덜 깨진다.
 *
 * 서명은 브라우저가 아니라 테스트 프로세스(Node)의 viem 이 한다. 화면은 `personal_sign` 을 부를 뿐이다.
 * 거래 전송(eth_sendTransaction)은 흉내 내지 않는다 — 부르면 4200 으로 거절한다.
 */
export async function installMockWallet(page: Page, wallet: Wallet) {
  await page.exposeFunction('__e2eSignMessage', (message: string) =>
    // viem · wagmi 는 문자열 메시지를 hex 로 바꿔 personal_sign 에 넘긴다.
    wallet.signMessage({ message: message.startsWith('0x') ? { raw: message as Hex } : message }),
  )

  await page.addInitScript(
    ({ address, chainIdHex }) => {
      const sign = (window as unknown as { __e2eSignMessage: (m: string) => Promise<string> })
        .__e2eSignMessage

      const provider = {
        isMetaMask: true,
        async request({ method, params }: { method: string; params?: unknown[] }) {
          switch (method) {
            case 'eth_requestAccounts':
            case 'eth_accounts':
              return [address]
            case 'eth_chainId':
              return chainIdHex
            case 'net_version':
              return String(parseInt(chainIdHex, 16))
            case 'wallet_requestPermissions':
            case 'wallet_getPermissions':
              return [{ parentCapability: 'eth_accounts' }]
            case 'wallet_switchEthereumChain':
            case 'wallet_addEthereumChain':
              return null
            case 'wallet_watchAsset':
              return true
            case 'personal_sign':
              return sign(String(params?.[0]))
            default:
              throw Object.assign(new Error(`mock wallet: ${method} 은 지원하지 않는다`), { code: 4200 })
          }
        },
        on() {},
        removeListener() {},
      }

      Object.defineProperty(window, 'ethereum', { value: provider, configurable: true })
    },
    { address: wallet.address, chainIdHex: `0x${SSAFY_CHAIN_ID.toString(16)}` },
  )
}
