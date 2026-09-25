import { formatUnits, parseUnits, type Address } from 'viem'

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
  {
    type: 'function',
    name: 'balanceOf',
    stateMutability: 'view',
    inputs: [{ name: 'account', type: 'address' }],
    outputs: [{ name: '', type: 'uint256' }],
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

/** 10500000000000000000n → '10.5'. 지갑 잔액을 화면과 요청에 같은 모양으로 쓴다. */
export function toAbcAmount(value: bigint): string {
  return trimAbcAmount(formatUnits(value, DECIMALS))
}

/** 보낼 수 있는 금액인가. 0 이하·숫자가 아닌 값·잘라 놓고도 0 이 되는 값을 막는다. */
export function isSendableAmount(amount: string): boolean {
  const trimmed = trimAbcAmount(amount)
  if (!/^\d+(\.\d+)?$/.test(trimmed)) return false
  return Number(trimmed) > 0
}
