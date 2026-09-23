import type { CreditBalance, CreditTransaction } from '@/api/credits'

/** ABC 잔액과 사용 내역 픽스처. 금액은 서버처럼 소수점 6자리 문자열이다. */

export const CREDIT_BALANCE: CreditBalance = {
  availableAmount: '640.000000',
  reservedAmount: '350.000000',
}

/** 최신순. 핸들러가 다시 정렬하지 않는다. 기간 필터를 눌러 볼 수 있게 30일에 걸쳐 둔다. */
// prettier-ignore
export const CREDIT_TRANSACTIONS: CreditTransaction[] = [
  { transactionId: 241, transactionType: 'RESERVE', amount: '-45.600000', occurredAt: '2026-09-23T00:15:00Z', executionId: 'exe_0241', transactionHash: null },
  { transactionId: 240, transactionType: 'RESERVE', amount: '-36.800000', occurredAt: '2026-09-22T09:42:00Z', executionId: 'exe_0240', transactionHash: null },
  // 공급자 책임 실패 → 해당 노드분 환원
  { transactionId: 239, transactionType: 'REFUND', amount: '+21.000000', occurredAt: '2026-09-22T05:10:00Z', executionId: 'exe_0238', transactionHash: null },
  { transactionId: 238, transactionType: 'RESERVE', amount: '-59.200000', occurredAt: '2026-09-22T05:08:00Z', executionId: 'exe_0238', transactionHash: null },
  { transactionId: 237, transactionType: 'CHARGE', amount: '+500.000000', occurredAt: '2026-09-21T12:03:00Z', executionId: null, transactionHash: '0x8f3a91c07d4b25e6a8c1f09b3d75e2410c6a8b93f21d47e50a9c3b6f8d1e042a' },
  { transactionId: 236, transactionType: 'RESERVE', amount: '-26.400000', occurredAt: '2026-09-20T23:30:00Z', executionId: 'exe_0236', transactionHash: null },
  { transactionId: 235, transactionType: 'RESERVE', amount: '-72.600000', occurredAt: '2026-09-19T07:22:00Z', executionId: 'exe_0235', transactionHash: null },
  { transactionId: 234, transactionType: 'RESERVE', amount: '-39.000000', occurredAt: '2026-09-18T02:47:00Z', executionId: 'exe_0234', transactionHash: null },
  // 분기로 실행되지 않은 노드분 반환
  { transactionId: 233, transactionType: 'REFUND', amount: '+15.600000', occurredAt: '2026-09-17T10:15:00Z', executionId: 'exe_0232', transactionHash: null },
  { transactionId: 232, transactionType: 'RESERVE', amount: '-59.800000', occurredAt: '2026-09-17T10:12:00Z', executionId: 'exe_0232', transactionHash: null },
  { transactionId: 231, transactionType: 'RESERVE', amount: '-54.600000', occurredAt: '2026-09-16T01:05:00Z', executionId: 'exe_0231', transactionHash: null },
  { transactionId: 230, transactionType: 'RESERVE', amount: '-85.800000', occurredAt: '2026-09-14T08:38:00Z', executionId: 'exe_0230', transactionHash: null },
  { transactionId: 229, transactionType: 'RESERVE', amount: '-62.400000', occurredAt: '2026-09-13T04:20:00Z', executionId: 'exe_0229', transactionHash: null },
  { transactionId: 228, transactionType: 'CHARGE', amount: '+250.000000', occurredAt: '2026-09-11T11:11:00Z', executionId: null, transactionHash: '0x2d7e4c8a19f03b65d2a7c4e81f09b3576ac1e4d82f95b073a6e1c8d40f2b95a7' },
  { transactionId: 227, transactionType: 'RESERVE', amount: '-46.800000', occurredAt: '2026-09-10T00:44:00Z', executionId: 'exe_0227', transactionHash: null },
  { transactionId: 225, transactionType: 'REFUND', amount: '+31.200000', occurredAt: '2026-09-08T13:31:00Z', executionId: 'exe_0226', transactionHash: null },
  { transactionId: 226, transactionType: 'RESERVE', amount: '-31.200000', occurredAt: '2026-09-08T13:26:00Z', executionId: 'exe_0226', transactionHash: null },
  { transactionId: 224, transactionType: 'RESERVE', amount: '-68.400000', occurredAt: '2026-09-06T06:52:00Z', executionId: 'exe_0224', transactionHash: null },
  { transactionId: 223, transactionType: 'RESERVE', amount: '-42.900000', occurredAt: '2026-09-04T15:09:00Z', executionId: 'exe_0223', transactionHash: null },
  { transactionId: 222, transactionType: 'RESERVE', amount: '-57.800000', occurredAt: '2026-09-02T03:17:00Z', executionId: 'exe_0222', transactionHash: null },
  { transactionId: 221, transactionType: 'CHARGE', amount: '+1000.000000', occurredAt: '2026-08-31T20:00:00Z', executionId: null, transactionHash: '0x6b1f8e2a47c093d5e8a2b4f71c06d39e5a8c2f41b7d093e6a5c8f2b14e70d396' },
  { transactionId: 220, transactionType: 'RESERVE', amount: '-33.600000', occurredAt: '2026-08-29T09:35:00Z', executionId: 'exe_0220', transactionHash: null },
  { transactionId: 219, transactionType: 'RESERVE', amount: '-49.400000', occurredAt: '2026-08-27T17:48:00Z', executionId: 'exe_0219', transactionHash: null },
  { transactionId: 218, transactionType: 'RESERVE', amount: '-74.500000', occurredAt: '2026-08-25T05:30:00Z', executionId: 'exe_0218', transactionHash: null },
]
