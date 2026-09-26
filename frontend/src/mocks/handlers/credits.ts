import { http } from 'msw'

import type { ChargeStatusView, CreditHistoryItem, CreditTransactionType } from '@/api/credits'
import { API_BASE_URL } from '@/api/client'
import { CHARGE_ERROR } from '@/api/errors'
import { MAX_FRACTION_DIGITS } from '@/lib/abcToken'
import { offsetCursorOk } from '@/mocks/cursor'
import { CREDIT_BALANCE, CREDIT_TRANSACTIONS } from '@/mocks/fixtures/credits'
import { PROFILE } from '@/mocks/fixtures/user'
import { fail, ok } from '@/mocks/response'
import { scene } from '@/mocks/scenario'

/** ABC 크레딧 mock. 실패가 전부 COMMON_001 인 것은 서버에 400 용 코드가 따로 없기 때문이다. */

/** 마켓(9·30)·마이페이지 목록(10·30)과 다르다. */
const DEFAULT_SIZE = 20
const MAX_SIZE = 50

const TYPES: CreditTransactionType[] = ['CHARGE', 'RESERVE', 'REFUND', 'PAYOUT_CONVERSION']

const invalidRequest = () => fail(400, 'COMMON_001', '조회 조건이 올바르지 않습니다.')

/** 서버와 같은 UTC 기준으로 `YYYY-MM-DD` 끼리 비교한다. */
const dayOf = (iso: string) => iso.slice(0, 10)

/**
 * 충전은 픽스처가 아니라 메모리에 쌓인다 — 접수한 건을 다시 물어야 하고, 확인 중에서
 * 완료로 넘어가는 것을 봐야 한다. 새로고침하면 사라진다.
 */
const charges = new Map<string, ChargeStatusView>()
/** 같은 해시로 다시 들어온 요청에 앞의 건을 돌려주려고 둔다 — 서버도 새로 만들지 않는다. */
const chargeIdByHash = new Map<string, string>()
let nextChargeId = 1

/** 실제 체인은 블록 하나에 10초쯤 걸린다. 확인 중 화면을 볼 수 있을 만큼만 끈다. */
const CONFIRM_AFTER_MS = 8000

const HASH_PATTERN = /^0x[0-9a-fA-F]{64}$/
/** 서버 금액 칸과 같은 모양으로 돌려준다. */
const amount6 = (amount: string) => Number(amount).toFixed(MAX_FRACTION_DIGITS)

/**
 * 확인 중인 건을 시간이 지났으면 완료로 넘긴다.
 *
 * 잔액과 내역까지 함께 움직여야 충전이 끝난 화면이 말이 된다 — 상태만 바꾸면
 * 완료라고 해 놓고 잔액이 그대로다.
 */
function settle(charge: ChargeStatusView): ChargeStatusView {
  if (charge.status !== 'PENDING') return charge
  if (Date.now() - Date.parse(charge.requestedAt) < CONFIRM_AFTER_MS) return charge

  charge.status = 'CONFIRMED'
  charge.creditedAmount = charge.requestedAmount
  charge.confirmedAt = new Date().toISOString()

  CREDIT_BALANCE.availableAmount = amount6(
    String(Number(CREDIT_BALANCE.availableAmount) + Number(charge.requestedAmount)),
  )
  CREDIT_TRANSACTIONS.unshift({
    transactionId: 1000 + Number(charge.chargeId),
    transactionType: 'CHARGE',
    amount: charge.creditedAmount,
    occurredAt: charge.confirmedAt,
    executionId: null,
    transactionHash: charge.transactionHash,
  })
  return charge
}

/** 충전 · 출금 내역의 충전 쪽. 픽스처의 지난 충전과 이번에 접수한 충전을 함께 준다. */
export function chargeHistory(): CreditHistoryItem[] {
  const past: CreditHistoryItem[] = CREDIT_TRANSACTIONS.filter(
    (t) => t.transactionType === 'CHARGE' && t.transactionId < 1000,
  ).map((t) => ({
    type: 'CHARGE',
    id: String(t.transactionId),
    status: 'CONFIRMED',
    amount: t.amount,
    walletAddress: PROFILE.wallet.address,
    transactionHash: t.transactionHash,
    requestedAt: new Date(Date.parse(t.occurredAt) - 20_000).toISOString(),
    completedAt: t.occurredAt,
    failureReason: null,
    rejectReason: null,
  }))
  const live: CreditHistoryItem[] = [...charges.values()].map((raw) => {
    const c = settle(raw)
    return {
      type: 'CHARGE',
      id: c.chargeId,
      status: c.status,
      amount: c.creditedAmount ?? c.requestedAmount,
      walletAddress: PROFILE.wallet.address,
      transactionHash: c.transactionHash,
      requestedAt: c.requestedAt,
      completedAt: c.confirmedAt,
      failureReason: c.failureReason,
      rejectReason: null,
    }
  })
  return [...live, ...past]
}

export const creditHandlers = [
  http.get(`${API_BASE_URL}/credits/balance`, async (info) => {
    const forced = await scene(info.request)
    if (forced) return forced

    return ok(CREDIT_BALANCE)
  }),

  http.get(`${API_BASE_URL}/credits/transactions`, async ({ request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const url = new URL(request.url)
    const type = url.searchParams.get('transactionType')
    if (type && !TYPES.includes(type as CreditTransactionType)) return invalidRequest()

    const from = url.searchParams.get('from')
    const to = url.searchParams.get('to')
    // 서버는 한쪽만 온 기간과 뒤집힌 기간을 똑같이 거절한다.
    if (Boolean(from) !== Boolean(to)) return invalidRequest()
    if (from && to && from > to) return invalidRequest()

    const filtered = CREDIT_TRANSACTIONS.filter(
      (t) =>
        (!type || t.transactionType === type) &&
        (!from || dayOf(t.occurredAt) >= from) &&
        (!to || dayOf(t.occurredAt) <= to),
    )

    return offsetCursorOk(url, filtered, { defaultSize: DEFAULT_SIZE, maxSize: MAX_SIZE })
  }),

  /** 접수하면서 체인에 한 번 물어본 척한다 — 여기서는 늘 PENDING 으로 시작한다. */
  http.post(`${API_BASE_URL}/credits/charges`, async ({ request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const body = (await request.json()) as { transactionHash?: string; amount?: string }
    const hash = body.transactionHash ?? ''
    const amount = body.amount ?? ''

    if (!HASH_PATTERN.test(hash)) {
      return fail(400, 'COMMON_001', '거래 해시 형식이 올바르지 않습니다.')
    }
    const fraction = amount.split('.')[1] ?? ''
    if (!(Number(amount) > 0) || fraction.length > MAX_FRACTION_DIGITS) {
      return fail(400, CHARGE_ERROR.INVALID_AMOUNT, '충전 수량은 소수 6자리까지입니다.')
    }

    // 같은 해시는 새로 만들지 않고 앞의 건을 돌려준다. 새로고침 뒤 재시도가 여기로 온다.
    const seen = chargeIdByHash.get(hash.toLowerCase())
    if (seen) return ok(settle(charges.get(seen)!), { status: 202 })

    const chargeId = String(nextChargeId++)
    const charge: ChargeStatusView = {
      chargeId,
      status: 'PENDING',
      failureReason: null,
      transactionHash: hash.toLowerCase(),
      requestedAmount: amount6(amount),
      creditedAmount: null,
      requestedAt: new Date().toISOString(),
      confirmedAt: null,
    }
    charges.set(chargeId, charge)
    chargeIdByHash.set(charge.transactionHash, chargeId)

    return ok(charge, { status: 202 })
  }),

  http.get(`${API_BASE_URL}/credits/charges/:chargeId`, async ({ params, request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const charge = charges.get(String(params.chargeId))
    if (!charge) {
      return fail(404, CHARGE_ERROR.NOT_FOUND, '충전 요청을 찾을 수 없습니다.')
    }

    return ok(settle(charge))
  }),
]
