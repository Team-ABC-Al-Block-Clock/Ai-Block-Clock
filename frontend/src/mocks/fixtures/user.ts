import type { UserProfile } from '@/api/auth'
import type { CreditBalance, CreditTransaction } from '@/api/credits'

/** 로그인 사용자와 ABC 사용 내역 픽스처. 사람 이름은 가명만 쓴다. */
export const PROFILE: UserProfile = {
  userId: 'u_1001',
  nickname: '이싸피',
  introduction: '온체인 리서치 에이전트를 만듭니다.',
  profileImageUrl: null,
  wallet: {
    provider: 'MetaMask',
    address: '0x1f9c4b2e7a0d5c83916fe2b47c0a91d3e5b8072a',
    network: 'POLYGON_AMOY',
    chainId: 80002,
  },
  onboardingCompleted: true,
  createdAt: '2026-09-08T15:02:00Z',
}

export const CREDIT_BALANCE: CreditBalance = {
  balance: 12480,
  locked: 540,
  updatedAt: '2026-09-08T11:20:00+09:00',
}

/**
 * 충전·차감·환불이 섞여 있어야 화면의 분류·부호 처리를 확인할 수 있다.
 * 최신순으로 둔다 — 핸들러가 정렬을 다시 하지 않는다.
 */
// prettier-ignore
export const CREDIT_TRANSACTIONS: CreditTransaction[] = [
  { transactionId: 'ctx_0031', type: 'USE', amount: 594, balanceAfter: 12480, executionId: 'exe_0031', agentName: '성적 분석 리포트', createdAt: '2026-09-08T09:15:00+09:00' },
  { transactionId: 'ctx_0030', type: 'USE', amount: 468, balanceAfter: 13074, executionId: 'exe_0030', agentName: '메일 초안', createdAt: '2026-09-07T18:42:00+09:00' },
  // 공급자 책임 실패 → 해당 노드분 환원
  { transactionId: 'ctx_0029', type: 'REFUND', amount: 210, balanceAfter: 13542, executionId: 'exe_0029', agentName: '계약서 검토', createdAt: '2026-09-07T14:10:00+09:00' },
  { transactionId: 'ctx_0028', type: 'USE', amount: 1092, balanceAfter: 13332, executionId: 'exe_0029', agentName: '계약서 검토', createdAt: '2026-09-07T14:08:00+09:00' },
  { transactionId: 'ctx_0027', type: 'CHARGE', amount: 10000, balanceAfter: 14424, executionId: null, agentName: null, createdAt: '2026-09-06T21:03:00+09:00' },
  { transactionId: 'ctx_0026', type: 'USE', amount: 312, balanceAfter: 4424, executionId: 'exe_0026', agentName: '뉴스 브리핑', createdAt: '2026-09-06T08:30:00+09:00' },
  { transactionId: 'ctx_0025', type: 'USE', amount: 726, balanceAfter: 4736, executionId: 'exe_0025', agentName: '리서치 수집', createdAt: '2026-09-05T16:22:00+09:00' },
  { transactionId: 'ctx_0024', type: 'USE', amount: 390, balanceAfter: 5462, executionId: 'exe_0024', agentName: '문서 정리', createdAt: '2026-09-05T11:47:00+09:00' },
  // 분기로 실행되지 않은 노드분 반환
  { transactionId: 'ctx_0023', type: 'REFUND', amount: 156, balanceAfter: 5852, executionId: 'exe_0022', agentName: '가정통신문 생성', createdAt: '2026-09-04T19:15:00+09:00' },
  { transactionId: 'ctx_0022', type: 'USE', amount: 598, balanceAfter: 5696, executionId: 'exe_0022', agentName: '가정통신문 생성', createdAt: '2026-09-04T19:12:00+09:00' },
  { transactionId: 'ctx_0021', type: 'USE', amount: 546, balanceAfter: 6294, executionId: 'exe_0021', agentName: 'CS 응답', createdAt: '2026-09-04T10:05:00+09:00' },
  { transactionId: 'ctx_0020', type: 'USE', amount: 858, balanceAfter: 6840, executionId: 'exe_0020', agentName: '코드 리뷰', createdAt: '2026-09-03T17:38:00+09:00' },
  { transactionId: 'ctx_0019', type: 'USE', amount: 624, balanceAfter: 7698, executionId: 'exe_0019', agentName: '회의록 작성', createdAt: '2026-09-03T13:20:00+09:00' },
  { transactionId: 'ctx_0018', type: 'CHARGE', amount: 5000, balanceAfter: 8322, executionId: null, agentName: null, createdAt: '2026-09-02T20:11:00+09:00' },
  { transactionId: 'ctx_0017', type: 'USE', amount: 468, balanceAfter: 3322, executionId: 'exe_0017', agentName: '슬랙 요약', createdAt: '2026-09-02T09:44:00+09:00' },
]
