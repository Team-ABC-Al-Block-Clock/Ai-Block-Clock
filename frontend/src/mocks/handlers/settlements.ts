import { http } from 'msw'

import type { Settlement, SettlementPeriodUnit } from '@/api/settlements'
import { API_BASE_URL } from '@/api/client'
import { offsetCursorOk } from '@/mocks/cursor'
import { settlements } from '@/mocks/fixtures/settlements'
import { fail, ok } from '@/mocks/response'
import { scene } from '@/mocks/scenario'

const DEFAULT_SIZE = 20
const MAX_SIZE = 50
const UNITS: SettlementPeriodUnit[] = ['DAY', 'MONTH']

const invalidRequest = () => fail(400, 'COMMON_001', '조회 조건이 올바르지 않습니다.')
const sum = (rows: Settlement[]) => rows.reduce((acc, r) => acc + Number(r.amount), 0).toFixed(6)
const executionsOf = (rows: Settlement[]) => new Set(rows.map((r) => r.executionId)).size

/** 기간이 틀렸으면 null. 서버는 한쪽만 온 기간과 뒤집힌 기간을 똑같이 거절한다. */
function filtered(url: URL): Settlement[] | null {
  const from = url.searchParams.get('from')
  const to = url.searchParams.get('to')
  if (Boolean(from) !== Boolean(to)) return null
  if (from && to && from > to) return null
  const agentId = url.searchParams.get('agentId')
  return settlements().filter(
    (s) =>
      (!from || s.settledAt.slice(0, 10) >= from) &&
      (!to || s.settledAt.slice(0, 10) <= to) &&
      (!agentId || s.agentId === agentId),
  )
}

function groupBy<K>(rows: Settlement[], keyOf: (row: Settlement) => K) {
  const groups = new Map<K, Settlement[]>()
  for (const row of rows) groups.set(keyOf(row), [...(groups.get(keyOf(row)) ?? []), row])
  return [...groups]
}

export const settlementHandlers = [
  http.get(`${API_BASE_URL}/settlements`, async ({ request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const url = new URL(request.url)
    const rows = filtered(url)
    if (!rows) return invalidRequest()
    return offsetCursorOk(url, rows, { defaultSize: DEFAULT_SIZE, maxSize: MAX_SIZE })
  }),

  http.get(`${API_BASE_URL}/settlements/statistics`, async ({ request }) => {
    const forced = await scene(request)
    if (forced) return forced

    const url = new URL(request.url)
    const unit = (url.searchParams.get('unit') ?? 'DAY') as SettlementPeriodUnit
    if (!UNITS.includes(unit)) return invalidRequest()
    const rows = filtered(url)
    if (!rows) return invalidRequest()

    const periodOf = (r: Settlement) =>
      unit === 'DAY' ? r.settledAt.slice(0, 10) : `${r.settledAt.slice(0, 7)}-01`
    return ok({
      unit,
      totalAmount: sum(rows),
      selfUseAmount: sum(rows.filter((r) => r.selfUse)),
      executionCount: executionsOf(rows),
      periods: groupBy(rows, periodOf)
        .map(([periodStart, group]) => ({
          periodStart,
          amount: sum(group),
          executionCount: executionsOf(group),
        }))
        .sort((a, b) => a.periodStart.localeCompare(b.periodStart)),
      agents: groupBy(rows, (r) => r.agentId)
        .map(([agentId, group]) => ({
          agentId,
          amount: sum(group),
          executionCount: executionsOf(group),
        }))
        .sort((a, b) => Number(b.amount) - Number(a.amount)),
    })
  }),
]
