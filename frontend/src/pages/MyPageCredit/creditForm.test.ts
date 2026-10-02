import { describe, expect, it, vi } from 'vitest'

import type { CreditBalance } from '@/api/credits'
import { priceLabel } from '@/lib/format'

import { chargeCard } from './chargeCard'
import { creditFormView } from './creditForm'

const balance = (payoutAmount: string, over: Partial<CreditBalance> = {}): CreditBalance => ({
  availableAmount: '50',
  reservedAmount: '0',
  payoutAmount,
  ...over,
})

const view = (over: Partial<Parameters<typeof creditFormView>[0]> = {}) =>
  creditFormView({
    mode: 'charge',
    pick: 2,
    typed: '',
    balance: balance('300'),
    balanceFailed: false,
    wallet: '0x1f9c4b2e7a0d5c83916fe2b47c0a91d3e5b8072a',
    walletAmount: '10',
    ...over,
  })

describe('creditFormView', () => {
  it('충전은 칸을 막지 않고, 고른 칸의 금액을 보낸다', () => {
    const v = view()
    expect(v.presets.every((p) => !p.disabled)).toBe(true)
    expect(v.amount).toBe('2500')
    expect(v.blocked).toBeNull()
  })

  it('직접 입력이 있으면 그 값을 보낸다', () => {
    expect(view({ typed: ' 42 ' }).amount).toBe('42')
  })

  it('정산금을 넘는 칸은 막고, 고른 칸이 막혔으면 고를 수 있는 마지막 칸으로 내린다', () => {
    const v = view({ mode: 'withdraw', pick: 2 })
    expect(v.presets.map((p) => p.disabled)).toEqual([false, false, true, false])
    expect(v.at).toBe(3)
    expect(v.amount).toBe('300')
  })

  it('고를 수 있는 칸이 없으면 고른 자리를 그대로 둔다', () => {
    expect(view({ mode: 'convert', pick: 1, balance: balance('0') }).at).toBe(1)
  })

  it('출금 전액 칸은 한 번의 상한을 넘지 않는다', () => {
    expect(view({ mode: 'withdraw', balance: balance('5000') }).presets[3].amount).toBe('1000')
  })

  it('전환 전액 칸은 서버 문자열을 다듬어서 그대로 쓴다', () => {
    expect(view({ mode: 'convert', balance: balance('8.200000') }).presets[3].amount).toBe('8.2')
  })

  it('출금은 한 번의 범위와 정산금을 넘으면 이유를 준다', () => {
    expect(view({ mode: 'withdraw', typed: '1001', balance: balance('5000') }).blocked).toBe(
      '한 번에 1 ~ 1,000 ABC 까지 출금할 수 있습니다.',
    )
    expect(view({ mode: 'withdraw', typed: '400' }).blocked).toBe('정산금이 부족합니다.')
    expect(view({ mode: 'withdraw', typed: 'abc' }).blocked).toBe('출금할 금액을 입력해 주세요.')
  })

  it('전환은 정산금 안이면 금액 제한이 없다', () => {
    expect(view({ mode: 'convert', typed: '0.5' }).blocked).toBeNull()
    expect(view({ mode: 'convert', typed: '301' }).blocked).toBe('정산금이 부족합니다.')
  })

  it('요약은 보낼 금액으로 뒤 잔액을 계산하고, 정산금을 넘으면 비운다', () => {
    expect(view({ typed: '10' }).details[0].value).toBe(priceLabel(60))
    expect(view({ mode: 'convert', typed: '301' }).details[1].value).toBe('—')
  })

  it('잔액을 읽기 전에는 0 대신 줄표를 띄운다', () => {
    expect(view({ balance: undefined }).bannerValue).toBe('—')
  })

  it('잠긴 금액이 있으면 충전 안내 앞에 붙인다', () => {
    expect(view({ balance: balance('0', { reservedAmount: '3' }) }).bannerNote).toMatch(
      /^실행에 잠긴 금액/,
    )
  })
})

describe('chargeCard', () => {
  const charge = {
    phase: 'idle' as const,
    error: null,
    stalled: false,
    status: null,
    requestedAmount: '5',
    retrySubmit: vi.fn(),
    recheck: vi.fn(),
  }

  it('접수를 못 했으면 전송이 아니라 접수만 다시 한다', () => {
    const card = chargeCard({ ...charge, phase: 'unsubmitted' }, vi.fn())
    expect(card.action?.run).toBe(charge.retrySubmit)
  })

  it('확인이 멈췄을 때만 다시 확인을 내준다', () => {
    expect(chargeCard({ ...charge, phase: 'confirming' }, vi.fn()).action).toBeNull()
    expect(chargeCard({ ...charge, phase: 'confirming', stalled: true }, vi.fn()).action?.run).toBe(
      charge.recheck,
    )
  })

  it('끝나면 확인으로 마무리한다', () => {
    const finish = vi.fn()
    expect(chargeCard({ ...charge, phase: 'confirmed' }, finish).action?.run).toBe(finish)
  })
})
