import { describe, expect, it } from 'vitest'

import { ApiError } from '@/api/errors'
import { getCreditBalance, getCreditTransactions } from '@/api/credits'
import { CREDIT_TRANSACTIONS } from '@/mocks/fixtures/credits'

/** api 레이어 + mock 핸들러 + offset 커서를 한 번에 통과시킨다. */

describe('getCreditBalance', () => {
  it('쓸 수 있는 금액과 잠긴 금액 둘만 준다', async () => {
    const balance = await getCreditBalance()

    expect(Object.keys(balance).sort()).toEqual(['availableAmount', 'reservedAmount'])
    expect(balance).not.toHaveProperty('status')
  })

  it('금액은 소수점이 살아 있는 문자열이다', async () => {
    const { availableAmount } = await getCreditBalance()

    expect(typeof availableAmount).toBe('string')
    expect(availableAmount).toMatch(/^\d+\.\d{6}$/)
  })
})

describe('getCreditTransactions', () => {
  it('명세 기본값 20건과 다음 커서를 준다', async () => {
    const page = await getCreditTransactions()

    expect(page.content).toHaveLength(20)
    // 픽스처가 24건이라 다음 장이 있다. 20건 이하로 줄면 무한 스크롤 검증이 사라진다.
    expect(page.hasNext).toBe(true)
    expect(page.nextCursor).toBeTruthy()
  })

  it('목록 배열은 content 다', async () => {
    const page = await getCreditTransactions()

    expect(Object.keys(page).sort()).toEqual(['content', 'hasNext', 'nextCursor'])
  })

  it('한 줄은 확정 명세의 여섯 필드만 담는다', async () => {
    const [first] = (await getCreditTransactions()).content

    expect(Object.keys(first).sort()).toEqual([
      'amount',
      'executionId',
      'occurredAt',
      'transactionHash',
      'transactionId',
      'transactionType',
    ])
  })

  it('거래 ID 는 문자열이 아니라 숫자다', async () => {
    const [first] = (await getCreditTransactions()).content

    expect(typeof first.transactionId).toBe('number')
  })

  it('최신순이다', async () => {
    const page = await getCreditTransactions({ size: 50 })
    const times = page.content.map((t) => Date.parse(t.occurredAt))

    expect(times).toEqual([...times].sort((a, b) => b - a))
  })

  it('커서를 이어 받으면 앞 장과 겹치지 않는다', async () => {
    const first = await getCreditTransactions({ size: 10 })
    const second = await getCreditTransactions({ size: 10, cursor: first.nextCursor ?? undefined })

    const ids = first.content.map((t) => t.transactionId)
    expect(second.content.some((t) => ids.includes(t.transactionId))).toBe(false)
    expect(second.content).toHaveLength(10)
  })

  it('마지막 장은 nextCursor 가 null 이다', async () => {
    const page = await getCreditTransactions({ size: 50 })

    expect(page.content).toHaveLength(CREDIT_TRANSACTIONS.length)
    expect(page.hasNext).toBe(false)
    expect(page.nextCursor).toBeNull()
  })

  it('금액은 부호를 달고 온다 — 충전·환불 +, 선차감 -', async () => {
    const page = await getCreditTransactions({ size: 50 })

    for (const t of page.content) {
      const sign = t.transactionType === 'RESERVE' ? '-' : '+'
      expect(t.amount.startsWith(sign)).toBe(true)
    }
  })

  it('충전만 트랜잭션 해시가 있고 실행 ID 가 없다', async () => {
    const page = await getCreditTransactions({ transactionType: 'CHARGE', size: 50 })

    expect(page.content.length).toBeGreaterThan(0)
    for (const t of page.content) {
      expect(t.transactionHash).toMatch(/^0x[0-9a-f]{64}$/)
      expect(t.executionId).toBeNull()
    }
  })

  it('유형으로 거른다', async () => {
    const page = await getCreditTransactions({ transactionType: 'REFUND', size: 50 })

    expect(page.content.length).toBeGreaterThan(0)
    expect(page.content.every((t) => t.transactionType === 'REFUND')).toBe(true)
  })

  it('명세에 없는 유형은 COMMON_001 이다', async () => {
    const call = getCreditTransactions({
      transactionType: 'SETTLEMENT' as never,
    })

    await expect(call).rejects.toThrow(ApiError)
    await expect(call).rejects.toMatchObject({ code: 'COMMON_001', status: 400 })
  })

  it('기간으로 거른다', async () => {
    const page = await getCreditTransactions({ from: '2026-09-01', to: '2026-09-10', size: 50 })

    expect(page.content.length).toBeGreaterThan(0)
    for (const t of page.content) {
      expect(t.occurredAt.slice(0, 10) >= '2026-09-01').toBe(true)
      expect(t.occurredAt.slice(0, 10) <= '2026-09-10').toBe(true)
    }
  })

  it('종료일 당일도 들어온다', async () => {
    const page = await getCreditTransactions({ from: '2026-09-23', to: '2026-09-23', size: 50 })

    expect(page.content.map((t) => t.transactionId)).toEqual([241])
  })

  it('기간을 한쪽만 주면 COMMON_001 이다', async () => {
    await expect(getCreditTransactions({ from: '2026-09-01' })).rejects.toMatchObject({
      code: 'COMMON_001',
      status: 400,
    })
    await expect(getCreditTransactions({ to: '2026-09-01' })).rejects.toMatchObject({
      code: 'COMMON_001',
      status: 400,
    })
  })

  it('기간이 뒤집히면 COMMON_001 이다', async () => {
    await expect(
      getCreditTransactions({ from: '2026-09-10', to: '2026-09-01' }),
    ).rejects.toMatchObject({ code: 'COMMON_001', status: 400 })
  })

  it('size 가 명세 최대 50 을 넘으면 COMMON_001 이다', async () => {
    await expect(getCreditTransactions({ size: 51 })).rejects.toMatchObject({
      code: 'COMMON_001',
      status: 400,
    })
  })

  it('마켓 상한 30 을 넘는 size 도 받는다', async () => {
    await expect(getCreditTransactions({ size: 31 })).resolves.toBeTruthy()
    await expect(getCreditTransactions({ size: 50 })).resolves.toBeTruthy()
  })

  it('커서가 깨지면 COMMON_001 이다', async () => {
    await expect(getCreditTransactions({ cursor: '!!not-base64!!' })).rejects.toMatchObject({
      code: 'COMMON_001',
      status: 400,
    })
  })

  it('커서는 서버처럼 offset 을 감싼 base64 다', async () => {
    const page = await getCreditTransactions({ size: 20 })

    expect(atob(page.nextCursor ?? '')).toBe('20')
  })
})
