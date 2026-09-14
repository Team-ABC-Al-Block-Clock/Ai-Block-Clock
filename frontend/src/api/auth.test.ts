import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest'

import { getMyProfile, loginWithWallet, signup } from '@/api/auth'
import { API_BASE_URL, client } from '@/api/client'
import { ApiError } from '@/api/errors'

/**
 * 회원가입 완료. api 레이어 + mock 핸들러 + 응답 인터셉터를 한 번에 통과시킨다.
 *
 * 실패 분기가 다섯 개라 표로 확인할 값어치가 있다. 순수 함수만 따로 보면
 * multipart 직렬화와 봉투 해제가 빠지는데, 정작 잘 틀리는 곳은 거기다.
 */

/**
 * multipart 요청만 Node 어댑터로 보낸다.
 *
 * jsdom 의 XMLHttpRequest 는 FormData 를 `text/plain` 으로 내보내서 MSW 의
 * `request.formData()` 가 터진다 — 실제 브라우저는 boundary 까지 붙인 multipart 를 보낸다.
 * axios 의 http 어댑터는 boundary 를 직접 만들므로 화면이 보내는 것과 같은 요청이 된다.
 * 절대 URL 이 필요하다 — Node 어댑터는 상대 경로를 풀지 못한다.
 */
const TRANSPORT = { adapter: client.defaults.adapter, baseURL: client.defaults.baseURL }
beforeAll(() => {
  client.defaults.adapter = 'http'
  client.defaults.baseURL = `${location.origin}${API_BASE_URL}`
})
afterAll(() => {
  client.defaults.adapter = TRANSPORT.adapter
  client.defaults.baseURL = TRANSPORT.baseURL
})

/** mock 은 모양만 본다 — 65바이트 서명 길이. */
const SIGNATURE = `0x${'a'.repeat(130)}`
const WALLET = '0xA1B2c3D4e5F60718293a4B5c6D7e8F901a2B7F3e'

const FORM = { termsAgreed: true, recoveryNoticeConfirmed: true }

/** 처음 보는 지갑으로 로그인하면 mock 이 온보딩 전으로 돌려 준다. */
async function signIn() {
  await loginWithWallet({ walletAddress: WALLET, signature: SIGNATURE })
}

// 프로필·세션·아는 지갑 목록이 전부 localStorage 에 남는다. 비우지 않으면 첫 테스트가
// 가입을 끝내 버려 나머지가 전부 USER_006 이 된다.
beforeEach(() => localStorage.clear())
afterEach(() => localStorage.clear())

describe('signup', () => {
  it('가입에 성공하면 명세가 정한 필드만 돌려준다', async () => {
    await signIn()

    const result = await signup({ nickname: '김선미', introduction: '나는척척박사입니다', ...FORM })

    expect(Object.keys(result).sort()).toEqual([
      'createdAt',
      'introduction',
      'nickname',
      'onboardingCompleted',
      'profileImageUrl',
      'userId',
      'walletAddress',
    ])
    expect(result.nickname).toBe('김선미')
    expect(result.onboardingCompleted).toBe(true)
    expect(result).not.toHaveProperty('status')
  })

  it('가입 뒤에는 프로필 조회도 온보딩을 마친 것으로 본다', async () => {
    await signIn()
    await signup({ nickname: '김선미', ...FORM })

    // 화면이 이 값으로 가입 화면 재진입을 막는다 — 응답만 맞고 저장이 안 되면 무한히 돈다.
    await expect(getMyProfile()).resolves.toMatchObject({ onboardingCompleted: true })
  })

  it('한 줄 소개를 보내지 않으면 빈 문자열이 아니라 null 로 남는다', async () => {
    await signIn()

    const result = await signup({ nickname: '김선미', ...FORM })

    expect(result.introduction).toBeNull()
  })

  it('세션이 없으면 AUTH_005 로 거절한다', async () => {
    await expect(signup({ nickname: '김선미', ...FORM })).rejects.toMatchObject({
      code: 'AUTH_005',
      status: 401,
    })
  })

  it('이름이 비었거나 공백뿐이면 COMMON_001', async () => {
    await signIn()

    await expect(signup({ nickname: '', ...FORM })).rejects.toMatchObject({
      code: 'COMMON_001',
      status: 400,
    })
    await expect(signup({ nickname: '   ', ...FORM })).rejects.toMatchObject({
      code: 'COMMON_001',
    })
  })

  it('이름이 2~20자를 벗어나면 USER_002', async () => {
    await signIn()

    await expect(signup({ nickname: '가', ...FORM })).rejects.toMatchObject({
      code: 'USER_002',
      status: 400,
    })
    await expect(signup({ nickname: '가'.repeat(21), ...FORM })).rejects.toMatchObject({
      code: 'USER_002',
    })
  })

  it('한 줄 소개가 40자를 넘으면 USER_002', async () => {
    await signIn()

    await expect(
      signup({ nickname: '김선미', introduction: '가'.repeat(41), ...FORM }),
    ).rejects.toMatchObject({ code: 'USER_002', status: 400 })
  })

  it('동의 둘 중 하나라도 빠지면 USER_003', async () => {
    await signIn()

    await expect(
      signup({ nickname: '김선미', termsAgreed: false, recoveryNoticeConfirmed: true }),
    ).rejects.toMatchObject({ code: 'USER_003', status: 400 })
    await expect(
      signup({ nickname: '김선미', termsAgreed: true, recoveryNoticeConfirmed: false }),
    ).rejects.toMatchObject({ code: 'USER_003' })
  })

  it('이미 가입을 마쳤으면 USER_006 으로 끊고 이름을 덮지 않는다', async () => {
    await signIn()
    await signup({ nickname: '김선미', ...FORM })

    const error = await signup({ nickname: '다른이름', ...FORM }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ code: 'USER_006', status: 409 })
    // 거부가 저장보다 먼저 와야 한다 — 순서가 뒤집히면 이름이 덮인 뒤 409 가 나간다.
    await expect(getMyProfile()).resolves.toMatchObject({ nickname: '김선미' })
  })
})

/**
 * 프로필 이미지(`updateMyProfile` 의 `profileImageFile`·`removeProfileImage`)는 여기서 검증하지
 * 않는다 — Node 에서 파일 파트를 실어 보낼 길이 없다.
 *
 * jsdom 의 `FormData`·`File` 은 axios 의 http 어댑터(`form-data`)도, undici 의 fetch 도 알아보지
 * 못해 multipart 로 직렬화되지 않는다. 문자열 파트만 쓰는 위 가입 테스트가 http 어댑터로 도는
 * 것이 가능한 최대치다.
 *
 * 그래서 이미지 분기(USER_004 형식 · USER_005 크기 · USER_007 교체와 삭제 동시 전송 · 부분
 * 수정에서 기존 이미지 유지)는 브라우저에서 확인한다. 실제 브라우저는 boundary 를 붙인
 * multipart 를 보내므로 같은 코드가 그대로 동작한다.
 */
