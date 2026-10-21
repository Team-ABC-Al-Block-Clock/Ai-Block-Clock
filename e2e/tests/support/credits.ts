import { expect } from '@playwright/test'

import { expectOk, type Api } from './auth'
import { recordDeposit } from './fakeChain'

export interface Balance {
  availableAmount: string
  reservedAmount: string
  payoutAmount: string
}

/**
 * 충전 API 로 ABC 를 채운다. 체인 거래는 가짜 체인(support/fakeChain.ts)이 대신 답하므로
 * E2E_RUNTIME=1 로 띄운 백엔드에서만 쓴다.
 */
export async function charge(api: Api, amount: string) {
  const transactionHash = await recordDeposit(api.wallet.address, amount)
  const response = await expectOk(await api.post('/api/v1/credits/charges', { transactionHash, amount }))
  const { data } = await response.json()

  expect(data.status, `충전이 확정되지 않았다: ${JSON.stringify(data)}`).toBe('CONFIRMED')
}

export async function balanceOf(api: Api): Promise<Balance> {
  const response = await expectOk(await api.get('/api/v1/credits/balance'))

  return (await response.json()).data
}

/** 금액 문자열을 소수 6자리(백엔드 장부 단위) 정수로. 더하고 빼서 견줄 때 소수 오차를 피한다. */
export function micros(amount: string): bigint {
  const [whole, fraction = ''] = amount.split('.')

  return BigInt(whole) * 1_000_000n + BigInt(fraction.padEnd(6, '0').slice(0, 6) || '0')
}
