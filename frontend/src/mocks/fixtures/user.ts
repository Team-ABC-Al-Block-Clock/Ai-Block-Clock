import type { UserProfile } from '@/api/auth'

/** 로그인 사용자 픽스처. 사람 이름은 가명만 쓴다. */
export const PROFILE: UserProfile = {
  userId: 'u_1001',
  nickname: '이싸피',
  introduction: '온체인 리서치 에이전트를 만듭니다.',
  profileImageUrl: null,
  wallet: {
    provider: 'METAMASK',
    address: '0x1f9c4b2e7a0d5c83916fe2b47c0a91d3e5b8072a',
    network: 'SSAFY',
    chainId: 31221,
  },
  onboardingCompleted: true,
  createdAt: '2026-09-08T15:02:00Z',
}
