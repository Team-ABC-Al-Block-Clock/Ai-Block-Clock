import { describe, expect, it } from 'vitest'

import { queryKeys } from '@/lib/queryKeys'

const startsWith = (key: readonly unknown[], prefix: readonly unknown[]) =>
  prefix.every((part, i) => key[i] === part)

describe('credits 키', () => {
  it('사용량 추이는 사용 내역 목록 아래에 있지 않다 — 전환 뒤 무효화에 딸려 가지 않는다', () => {
    const trend = queryKeys.credits.usageTrend({ from: '2026-09-01', to: '2026-09-30' })

    expect(startsWith(trend, queryKeys.credits.transactions.lists())).toBe(false)
    expect(startsWith(trend, queryKeys.credits.all)).toBe(true)
  })
})
