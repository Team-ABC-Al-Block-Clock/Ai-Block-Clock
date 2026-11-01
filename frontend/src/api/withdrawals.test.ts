import { describe, expect, it } from 'vitest'

import { convertPayout, getCreditBalance, getCreditHistory } from '@/api/credits'
import { WITHDRAWAL_ERROR } from '@/api/errors'
import { cancelWithdrawal, createWithdrawal } from '@/api/withdrawals'

/** mock 이 메모리에 쌓여 순서가 중요하다 — 한 describe 안에서 차례로 본다. */
describe('정산금 출금', () => {
  it('1,000 을 넘으면 PAYMENT_010 이다', async () => {
    const call = createWithdrawal({ requestId: crypto.randomUUID(), amount: '1000.5' })

    await expect(call).rejects.toMatchObject({
      code: WITHDRAWAL_ERROR.AMOUNT_OUT_OF_RANGE,
      status: 400,
    })
  })

  const requestId = crypto.randomUUID()
  let withdrawalId = ''

  it('신청하면 심사 대기로 오고 그만큼 정산금에서 빠진다', async () => {
    const before = Number((await getCreditBalance()).payoutAmount)
    const withdrawal = await createWithdrawal({ requestId, amount: '100' })
    withdrawalId = withdrawal.withdrawalId

    expect(withdrawal.status).toBe('REQUESTED')
    expect(before - Number((await getCreditBalance()).payoutAmount)).toBeCloseTo(100, 6)
  })

  it('같은 requestId 는 새로 묶지 않고 앞의 신청을 돌려준다', async () => {
    const before = (await getCreditBalance()).payoutAmount
    const again = await createWithdrawal({ requestId, amount: '100' })

    expect(again.withdrawalId).toBe(withdrawalId)
    expect((await getCreditBalance()).payoutAmount).toBe(before)
  })

  it('처리 중인 출금이 있으면 PAYMENT_012 다', async () => {
    await expect(
      createWithdrawal({ requestId: crypto.randomUUID(), amount: '10' }),
    ).rejects.toMatchObject({ code: WITHDRAWAL_ERROR.ALREADY_IN_PROGRESS, status: 409 })
  })

  it('취소하면 정산금이 돌아오고, 취소한 신청은 24시간 주기에 세지 않는다', async () => {
    const before = Number((await getCreditBalance()).payoutAmount)
    const canceled = await cancelWithdrawal(withdrawalId)

    expect(canceled.status).toBe('CANCELED')
    expect(Number((await getCreditBalance()).payoutAmount) - before).toBeCloseTo(100, 6)

    const next = await createWithdrawal({ requestId: crypto.randomUUID(), amount: '10' })
    expect(next.status).toBe('REQUESTED')
    await cancelWithdrawal(next.withdrawalId)
  })

  it('이미 취소한 신청은 그대로 돌려준다', async () => {
    expect((await cancelWithdrawal(withdrawalId)).status).toBe('CANCELED')
  })

  it('없는 신청은 PAYMENT_015 다', async () => {
    await expect(cancelWithdrawal('999')).rejects.toMatchObject({
      code: WITHDRAWAL_ERROR.NOT_FOUND,
      status: 404,
    })
  })
})

describe('정산금 옮기기', () => {
  it('정산금에서 빼 쓸 수 있는 ABC 에 더하고 옮긴 뒤 잔액을 준다', async () => {
    const before = await getCreditBalance()
    const result = await convertPayout({ requestId: crypto.randomUUID(), amount: '5' })

    expect(result.convertedAmount).toBe('5.000000')
    expect(Number(before.payoutAmount) - Number(result.balance.payoutAmount)).toBeCloseTo(5, 6)
    expect(Number(result.balance.availableAmount) - Number(before.availableAmount)).toBeCloseTo(
      5,
      6,
    )
  })

  it('같은 requestId 는 다시 옮기지 않는다', async () => {
    const requestId = crypto.randomUUID()
    const first = await convertPayout({ requestId, amount: '2' })
    const second = await convertPayout({ requestId, amount: '2' })

    expect(second.balance.payoutAmount).toBe(first.balance.payoutAmount)
  })

  it('정산금보다 많으면 PAYMENT_018 이다', async () => {
    await expect(
      convertPayout({ requestId: crypto.randomUUID(), amount: '999999' }),
    ).rejects.toMatchObject({ code: WITHDRAWAL_ERROR.CONVERSION_INSUFFICIENT })
  })
})

describe('충전 · 출금 내역', () => {
  it('요청 시각 최신순으로 충전과 출금을 섞어 준다', async () => {
    const page = await getCreditHistory({ size: 50 })
    const times = page.content.map((h) => Date.parse(h.requestedAt))

    expect(new Set(page.content.map((h) => h.type))).toEqual(new Set(['CHARGE', 'WITHDRAWAL']))
    expect(times).toEqual([...times].sort((a, b) => b - a))
  })

  it('type 으로 거른다', async () => {
    const page = await getCreditHistory({ type: 'WITHDRAWAL', size: 50 })

    expect(page.content.every((h) => h.type === 'WITHDRAWAL')).toBe(true)
  })

  it('반려된 출금만 rejectReason 이 있다', async () => {
    const page = await getCreditHistory({ type: 'WITHDRAWAL', size: 50 })

    for (const h of page.content) expect(Boolean(h.rejectReason)).toBe(h.status === 'REJECTED')
  })
})
