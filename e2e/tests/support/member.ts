import { expectOk, type Api } from './auth'

/**
 * 프로필을 채운다. 새 지갑은 프로필이 비어 있어 화면이 `/signup` 으로 붙잡는다 —
 * 가입 화면 자체가 이번 기능이 아니면 이것으로 건너뛴다.
 */
export async function signup(api: Api, nickname = `e2e-${api.wallet.address.slice(2, 8)}`) {
  await expectOk(
    await api.postForm('/api/v1/users/me/signup', {
      nickname,
      termsAgreed: 'true',
      recoveryNoticeConfirmed: 'true',
    }),
  )
}
