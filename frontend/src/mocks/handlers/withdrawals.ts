import { http } from 'msw'

import { API_BASE_URL } from '@/api/client'
import { WITHDRAWAL_ERROR } from '@/api/errors'
import { WITHDRAWAL_LIMITS, type Withdrawal } from '@/api/withdrawals'
import { MAX_FRACTION_DIGITS } from '@/lib/abcToken'
import { CREDIT_BALANCE, CREDIT_TRANSACTIONS } from '@/mocks/fixtures/credits'
import { PROFILE } from '@/mocks/fixtures/user'
import { fail, ok } from '@/mocks/response'
import { scene } from '@/mocks/scenario'

/** 새로고침하면 사라진다. 조회 API 가 없어 심사 뒤 상태는 mock 에서도 보이지 않는다. */
const withdrawals = new Map<string, Withdrawal>()
const byRequestId = new Map<string, string>()
const conversions = new Map<string, string>()
let nextId = 1

const IN_PROGRESS = new Set(['REQUESTED', 'APPROVED', 'PAYING'])
const COUNTED = new Set(['REQUESTED', 'APPROVED', 'PAYING', 'PAID'])

const amount6 = (n: number) => n.toFixed(MAX_FRACTION_DIGITS)
const addPayout = (delta: number) => {
  CREDIT_BALANCE.payoutAmount = amount6(Number(CREDIT_BALANCE.payoutAmount) + delta)
}

function readAmount(raw: unknown): number | null {
  const text = String(raw ?? '')
  const fraction = text.split('.')[1] ?? ''
  const n = Number(text)
  return n > 0 && fraction.length <= MAX_FRACTION_DIGITS ? n : null
}

export const withdrawalHandlers = [
  http.post(`${API_BASE_URL}/withdrawals`, async ({ request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const body = (await request.json()) as { requestId?: string; amount?: string }
    const amount = readAmount(body.amount)
    if (!body.requestId || amount === null) {
      return fail(400, 'COMMON_001', '요청 본문이 올바르지 않습니다.')
    }
    const seen = byRequestId.get(body.requestId)
    if (seen) return ok(withdrawals.get(seen)!, { status: 202 })

    if (amount < WITHDRAWAL_LIMITS.MIN_AMOUNT || amount > WITHDRAWAL_LIMITS.MAX_AMOUNT) {
      return fail(400, WITHDRAWAL_ERROR.AMOUNT_OUT_OF_RANGE, '출금 금액이 범위를 벗어났습니다.', {
        minimumAmount: amount6(WITHDRAWAL_LIMITS.MIN_AMOUNT),
        maximumAmount: amount6(WITHDRAWAL_LIMITS.MAX_AMOUNT),
      })
    }
    const all = [...withdrawals.values()]
    if (all.some((w) => IN_PROGRESS.has(w.status))) {
      return fail(409, WITHDRAWAL_ERROR.ALREADY_IN_PROGRESS, '처리 중인 출금이 있습니다.')
    }
    const last = all
      .filter((w) => COUNTED.has(w.status))
      .map((w) => Date.parse(w.requestedAt))
      .sort((a, b) => b - a)[0]
    const intervalMs = WITHDRAWAL_LIMITS.INTERVAL_HOURS * 3_600_000
    if (last !== undefined && Date.now() < last + intervalMs) {
      return fail(409, WITHDRAWAL_ERROR.TOO_FREQUENT, '출금 신청 주기가 지나지 않았습니다.', {
        availableAt: new Date(last + intervalMs).toISOString(),
      })
    }
    if (Number(CREDIT_BALANCE.payoutAmount) < amount) {
      return fail(409, WITHDRAWAL_ERROR.PAYOUT_INSUFFICIENT, '정산금이 부족합니다.', {
        payoutAmount: CREDIT_BALANCE.payoutAmount,
      })
    }

    const withdrawal: Withdrawal = {
      withdrawalId: String(nextId++),
      status: 'REQUESTED',
      amount: amount6(amount),
      walletAddress: PROFILE.wallet.address,
      requestedAt: new Date().toISOString(),
      reviewedAt: null,
      rejectReason: null,
    }
    withdrawals.set(withdrawal.withdrawalId, withdrawal)
    byRequestId.set(body.requestId, withdrawal.withdrawalId)
    addPayout(-amount)
    return ok(withdrawal, { status: 202 })
  }),

  http.post(`${API_BASE_URL}/withdrawals/:withdrawalId/cancel`, async ({ params, request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const withdrawal = withdrawals.get(String(params.withdrawalId))
    if (!withdrawal) {
      return fail(404, WITHDRAWAL_ERROR.NOT_FOUND, '출금 신청을 찾을 수 없습니다.')
    }
    if (withdrawal.status === 'CANCELED') return ok(withdrawal)
    if (withdrawal.status !== 'REQUESTED') {
      return fail(409, WITHDRAWAL_ERROR.INVALID_STATE, '현재 상태에서는 바꿀 수 없습니다.')
    }
    withdrawal.status = 'CANCELED'
    addPayout(Number(withdrawal.amount))
    return ok(withdrawal)
  }),

  http.post(`${API_BASE_URL}/credits/payout-conversions`, async ({ request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const body = (await request.json()) as { requestId?: string; amount?: string }
    const amount = readAmount(body.amount)
    if (!body.requestId || amount === null) {
      return fail(400, 'COMMON_001', '요청 본문이 올바르지 않습니다.')
    }
    const seen = conversions.get(body.requestId)
    if (seen) return ok({ convertedAmount: seen, balance: CREDIT_BALANCE })

    if (Number(CREDIT_BALANCE.payoutAmount) < amount) {
      return fail(409, WITHDRAWAL_ERROR.CONVERSION_INSUFFICIENT, '정산금이 부족합니다.', {
        payoutAmount: CREDIT_BALANCE.payoutAmount,
      })
    }
    addPayout(-amount)
    CREDIT_BALANCE.availableAmount = amount6(Number(CREDIT_BALANCE.availableAmount) + amount)
    CREDIT_TRANSACTIONS.unshift({
      transactionId: 2000 + conversions.size,
      transactionType: 'PAYOUT_CONVERSION',
      amount: amount6(amount),
      occurredAt: new Date().toISOString(),
      executionId: null,
      transactionHash: null,
    })
    conversions.set(body.requestId, amount6(amount))
    return ok({ convertedAmount: amount6(amount), balance: CREDIT_BALANCE })
  }),
]
