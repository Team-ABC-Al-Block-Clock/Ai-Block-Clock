import { http } from 'msw'

import type { CreditTransactionType } from '@/api/credits'
import { API_BASE_URL } from '@/api/client'
import { offsetCursorOk } from '@/mocks/cursor'
import { CREDIT_BALANCE, CREDIT_TRANSACTIONS } from '@/mocks/fixtures/credits'
import { fail, ok } from '@/mocks/response'
import { scene } from '@/mocks/scenario'

/** ABC 크레딧 mock. 실패가 전부 COMMON_001 인 것은 서버에 400 용 코드가 따로 없기 때문이다. */

/** 마켓(9·30)·마이페이지 목록(10·30)과 다르다. */
const DEFAULT_SIZE = 20
const MAX_SIZE = 50

const TYPES: CreditTransactionType[] = ['CHARGE', 'RESERVE', 'REFUND']

const invalidRequest = () => fail(400, 'COMMON_001', '조회 조건이 올바르지 않습니다.')

/** 서버와 같은 UTC 기준으로 `YYYY-MM-DD` 끼리 비교한다. */
const dayOf = (iso: string) => iso.slice(0, 10)

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
]
