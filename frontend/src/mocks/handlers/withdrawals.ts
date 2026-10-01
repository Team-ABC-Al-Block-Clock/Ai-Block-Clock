import { http } from 'msw'

import { API_BASE_URL } from '@/api/client'
import type { CreditHistoryItem, CreditHistoryType } from '@/api/credits'
import { WITHDRAWAL_ERROR } from '@/api/errors'
import { IN_PROGRESS_WITHDRAWAL, WITHDRAWAL_LIMITS, type Withdrawal } from '@/api/withdrawals'
import { MAX_FRACTION_DIGITS } from '@/lib/wallet/abcToken'
import { offsetCursorOk } from '@/mocks/cursor'
import { CREDIT_BALANCE, CREDIT_TRANSACTIONS } from '@/mocks/fixtures/credits'
import { chargeHistory } from '@/mocks/handlers/credits'
import { PROFILE } from '@/mocks/fixtures/user'
import { fail, ok } from '@/mocks/response'
import { scene } from '@/mocks/scenario'

/** 새로고침하면 새로 신청한 것은 사라지고 지난 출금 셋만 남는다. */
const withdrawals = new Map<string, Withdrawal>()
const txHashes = new Map<string, string>()
const paidAt = new Map<string, string>()
const byRequestId = new Map<string, string>()
const conversions = new Map<string, string>()

const seed = (w: Omit<Withdrawal, 'walletAddress'>, hash?: string, done?: string) => {
  withdrawals.set(w.withdrawalId, { ...w, walletAddress: PROFILE.wallet.address })
  if (hash) txHashes.set(w.withdrawalId, hash)
  if (done) paidAt.set(w.withdrawalId, done)
}
// prettier-ignore
seed({ withdrawalId: '1', status: 'FAILED', amount: '120.000000', requestedAt: '2026-08-28T02:10:00Z', reviewedAt: '2026-08-28T05:00:00Z', rejectReason: null }, '0x4c1e9a07d2b36f58e1a0c94b7d2e5f3816a9c07b4d2e81f3a56c9b07e1d4f2a8')
// prettier-ignore
seed({ withdrawalId: '2', status: 'REJECTED', amount: '300.000000', requestedAt: '2026-09-03T11:20:00Z', reviewedAt: '2026-09-03T14:00:00Z', rejectReason: '본인 확인 정보와 지갑 소유자가 다릅니다.' })
// prettier-ignore
seed({ withdrawalId: '3', status: 'PAID', amount: '250.000000', requestedAt: '2026-09-10T08:00:00Z', reviewedAt: '2026-09-10T09:30:00Z', rejectReason: null }, '0x9b2f4e7a1c03d58e6a2b1f09c4d7e35a81b6c0f2e49d7a13c5b8e0f4a2d61c97', '2026-09-10T09:30:40Z')
let nextId = 4

const IN_PROGRESS = new Set<string>(IN_PROGRESS_WITHDRAWAL)
const COUNTED = new Set(['REQUESTED', 'APPROVED', 'PAYING', 'ON_HOLD', 'PAID'])

/** 심사 · 전송을 흉내 낸다. 10초 뒤 승인, 20초 뒤 전송 중, 35초 뒤 완료. */
function advance(w: Withdrawal): Withdrawal {
  if (!['REQUESTED', 'APPROVED', 'PAYING'].includes(w.status)) return w
  const age = Date.now() - Date.parse(w.requestedAt)
  if (age >= 35_000) {
    w.status = 'PAID'
    paidAt.set(w.withdrawalId, new Date().toISOString())
  } else if (age >= 20_000) w.status = 'PAYING'
  else if (age >= 10_000) w.status = 'APPROVED'
  if (w.status !== 'REQUESTED' && !w.reviewedAt) w.reviewedAt = new Date().toISOString()
  if ((w.status === 'PAYING' || w.status === 'PAID') && !txHashes.has(w.withdrawalId)) {
    const hex = w.withdrawalId.padStart(4, '0')
    txHashes.set(w.withdrawalId, `0x${hex.repeat(16)}`)
  }
  return w
}

function withdrawalHistory(): CreditHistoryItem[] {
  return [...withdrawals.values()].map((raw) => {
    const w = advance(raw)
    return {
      type: 'WITHDRAWAL',
      id: w.withdrawalId,
      status: w.status,
      amount: w.amount,
      walletAddress: w.walletAddress,
      transactionHash: txHashes.get(w.withdrawalId) ?? null,
      requestedAt: w.requestedAt,
      completedAt: paidAt.get(w.withdrawalId) ?? null,
      failureReason: null,
      rejectReason: w.rejectReason,
    }
  })
}

const TYPES: CreditHistoryType[] = ['CHARGE', 'WITHDRAWAL']

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
  http.get(`${API_BASE_URL}/credits/history`, async ({ request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const url = new URL(request.url)
    const type = url.searchParams.get('type')
    if (type && !TYPES.includes(type as CreditHistoryType)) {
      return fail(400, 'COMMON_001', '조회 조건이 올바르지 않습니다.')
    }
    const items = [...chargeHistory(), ...withdrawalHistory()]
      .filter((item) => !type || item.type === type)
      .sort((a, b) => Date.parse(b.requestedAt) - Date.parse(a.requestedAt))
    return offsetCursorOk(url, items, { defaultSize: 20, maxSize: 50 })
  }),

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
    const all = [...withdrawals.values()].map(advance)
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

    const found = withdrawals.get(String(params.withdrawalId))
    const withdrawal = found && advance(found)
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
