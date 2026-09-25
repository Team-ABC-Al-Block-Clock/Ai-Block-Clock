import { describe, expect, it } from 'vitest'

import { isSendableAmount, toAbcAmount, toTokenValue, trimAbcAmount } from '@/lib/abcToken'

/** 자릿수를 잘못 다루면 토큰이 나간 뒤에야 알게 된다 — 되돌릴 수 없어 검사를 둔다. */

describe('trimAbcAmount', () => {
  it('소수 7자리부터 버린다', () => {
    expect(trimAbcAmount('1.1234567')).toBe('1.123456')
    // 올리면 지갑 잔액을 넘겨 전송 자체가 실패한다
    expect(trimAbcAmount('1.9999999')).toBe('1.999999')
  })

  it('끝의 0 과 소수점을 뗀다', () => {
    expect(trimAbcAmount('10.500000')).toBe('10.5')
    expect(trimAbcAmount('10.000000')).toBe('10')
    expect(trimAbcAmount('10')).toBe('10')
  })
})

describe('toTokenValue', () => {
  it('ABC 1 을 10^18 로 올린다', () => {
    expect(toTokenValue('1')).toBe(10n ** 18n)
    expect(toTokenValue('10.5')).toBe(10_500_000_000_000_000_000n)
  })

  it('소수 6자리까지만 실어 보낸다', () => {
    expect(toTokenValue('1.1234567')).toBe(toTokenValue('1.123456'))
  })
})

describe('toAbcAmount', () => {
  it('10^18 을 ABC 로 되돌린다', () => {
    expect(toAbcAmount(10n ** 18n)).toBe('1')
    expect(toAbcAmount(10_500_000_000_000_000_000n)).toBe('10.5')
  })

  // 지갑 잔액에는 6자리보다 잘게 남은 먼지가 있을 수 있다. 그대로 보내면 서버가 400 으로 막는다
  it('6자리보다 잘게 남은 잔액은 잘라서 돌려준다', () => {
    expect(toAbcAmount(1_000_000_000_001n)).toBe('0.000001')
  })
})

describe('isSendableAmount', () => {
  it('0 이하와 숫자가 아닌 값을 막는다', () => {
    expect(isSendableAmount('0')).toBe(false)
    expect(isSendableAmount('')).toBe(false)
    expect(isSendableAmount('열')).toBe(false)
    expect(isSendableAmount('-1')).toBe(false)
  })

  // 잘라 놓고 보면 0 인 값이다. 자르기 전에 보면 통과해 버린다
  it('잘라서 0 이 되는 값도 막는다', () => {
    expect(isSendableAmount('0.0000001')).toBe(false)
  })

  it('보낼 수 있는 값은 통과한다', () => {
    expect(isSendableAmount('1')).toBe(true)
    expect(isSendableAmount('0.000001')).toBe(true)
  })
})
