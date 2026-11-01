import { expect, test } from '../support/fixtures'

test('로그인하지 않으면 보호 API가 AUTH_005로 거절한다', async ({ request }) => {
  const response = await request.get('/api/v1/workflows')

  expect(response.status()).toBe(401)
  expect((await response.json()).error.code).toBe('AUTH_005')
})

test('지갑 서명으로 로그인하면 세션 쿠키로 보호 API를 부른다', async ({ api }) => {
  const response = await api.get('/api/v1/workflows')

  expect(response.status()).toBe(200)
})
