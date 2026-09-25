import { parseUnits, type Address } from 'viem'

/**
 * abcToken (ERC-20) 과 충전 입금 규칙.
 *
 * 충전은 `transfer(보관 지갑, 금액)` 한 번이다. `transferByPartition` 이 아니다.
 * 두 주소는 서버 설정(`payment.charge`)과 같아야 한다 — 다르면 토큰은 나간 채로
 * CUSTODY_ADDRESS_MISMATCH 로 닫히고 되돌릴 방법이 없다.
 */
export const ABC_TOKEN_ADDRESS: Address = '0x56DC097C14836473f4Ad4BD675a3d01BE5e4C63c'

/** 입금 전용 지갑. 발행 계정과 나눠 둔 권한 없는 계정이다. */
export const CUSTODY_ADDRESS: Address = '0x64e72A170a7a36cE09806190Ed02085fe23CB6B6'

/** 컨트랙트의 `symbol()` 과 같은 값이다. 지갑에 등록할 때 쓴다. */
const ABC_TOKEN_SYMBOL = 'abcToken'

/** ABC 1 = 10^18 */
const DECIMALS = 18

/** 서버 금액 칸이 DECIMAL(38, 6) 이다. 더 잘게 보내면 400 으로 막힌다. */
export const MAX_FRACTION_DIGITS = 6

/** 쓰는 것만 적는다. 전체 ERC-20 ABI 를 들고 다닐 이유가 없다. */
export const ABC_TOKEN_ABI = [
  {
    type: 'function',
    name: 'transfer',
    stateMutability: 'nonpayable',
    inputs: [
      { name: 'to', type: 'address' },
      { name: 'value', type: 'uint256' },
    ],
    outputs: [{ name: '', type: 'bool' }],
  },
] as const

/** 가스비가 0 이고 EIP-1559 가 없는 체인이다. 지갑이 수수료를 따로 잡지 않게 레거시로 못박는다. */
export const ZERO_GAS = { type: 'legacy', gasPrice: 0n } as const

/**
 * 소수 7자리부터 버린다. 반올림하지 않는다 — 올리면 지갑 잔액을 넘겨 전송 자체가 실패한다.
 * 끝의 0 도 뗀다. '10.500000' 과 '10.5' 는 같은 값인데 서버 대조 로그만 지저분해진다.
 */
export function trimAbcAmount(amount: string): string {
  const [whole, fraction = ''] = amount.trim().split('.')
  const cut = fraction.slice(0, MAX_FRACTION_DIGITS).replace(/0+$/, '')
  return cut ? `${whole}.${cut}` : whole
}

/** '10.5' → 10500000000000000000n */
export function toTokenValue(amount: string): bigint {
  return parseUnits(trimAbcAmount(amount), DECIMALS)
}

/** 보낼 수 있는 금액인가. 0 이하·숫자가 아닌 값·잘라 놓고도 0 이 되는 값을 막는다. */
export function isSendableAmount(amount: string): boolean {
  const trimmed = trimAbcAmount(amount)
  if (!/^\d+(\.\d+)?$/.test(trimmed)) return false
  return Number(trimmed) > 0
}

/** EIP-1193 의 최소 모양. 커넥터가 주는 provider 는 타입이 넓어 여기서 좁힌다. */
interface RequestProvider {
  request: (args: { method: string; params?: unknown }) => Promise<unknown>
}

/**
 * 지갑에 abcToken 을 등록한다(EIP-747).
 *
 * 등록되지 않은 토큰은 전송 창에서 기호 자리가 '알 수 없음' 으로 뜬다 — 무엇을 얼마나
 * 보내는지 확인할 수가 없다. 주소를 손으로 옮겨 적게 하지 않으려고 지갑에 직접 묻는다.
 *
 * 거절하거나 지갑이 이 요청을 모르더라도 로그인·충전은 그대로 된다. 표시 문제라 조용히 넘긴다.
 */
export async function watchAbcToken(provider: unknown): Promise<void> {
  const eip1193 = provider as Partial<RequestProvider>
  if (typeof eip1193.request !== 'function') return

  try {
    await eip1193.request({
      method: 'wallet_watchAsset',
      params: {
        type: 'ERC20',
        options: { address: ABC_TOKEN_ADDRESS, symbol: ABC_TOKEN_SYMBOL, decimals: DECIMALS },
      },
    })
  } catch {
    /* 거절·미지원 — 알릴 일이 아니다 */
  }
}
