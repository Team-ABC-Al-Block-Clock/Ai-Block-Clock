import { newWallet } from '../support/auth'
import { expect, test } from '../support/fixtures'
import { installMockWallet } from '../support/mockWallet'

test('로그인하지 않으면 보호 화면에서 로그인 화면으로 보낸다', async ({ page, shot }) => {
  await page.goto('/my/agents')

  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('button', { name: 'MetaMask 지갑 연결' })).toBeVisible()
  await shot('로그인-화면')
})

test('세션 쿠키가 있으면 프로필이 없는 새 지갑은 가입 화면으로 간다', async ({ page, session, shot }) => {
  expect(session.wallet.address).toMatch(/^0x/)

  await page.goto('/my/agents')

  await expect(page).toHaveURL(/\/signup$/)
  await shot('가입-화면')
})

test('MetaMask 지갑 연결 버튼으로 서명하고 로그인한다', async ({ page, shot }) => {
  await installMockWallet(page, newWallet())
  await page.goto('/login')
  await shot('연결-전')

  await page.getByRole('button', { name: 'MetaMask 지갑 연결' }).click()

  await expect(page).toHaveURL(/\/signup$/)
  await shot('로그인-후-가입-화면')
})
