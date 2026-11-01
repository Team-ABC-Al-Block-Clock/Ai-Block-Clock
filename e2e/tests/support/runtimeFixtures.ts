import { Api, login } from './auth'
import { test as base } from './fixtures'
import { ADMIN_WALLET } from './runtimeEnv'

/**
 * 런타임 테스트(E2E_RUNTIME=1) 전용 픽스처. 기본 픽스처(session · member · api · shot)에 관리자를 더한다.
 * 관리자 지갑은 그 모드로 띄운 백엔드만 관리자로 안다(playwright.config.ts 의 ADMIN_WALLET_ADDRESSES).
 */
export const test = base.extend<{ admin: Api }>({
  admin: async ({ playwright, baseURL }, use) => {
    const request = await playwright.request.newContext({ baseURL })
    const { csrf } = await login(request, ADMIN_WALLET)

    await use(new Api(request, csrf, ADMIN_WALLET))
    await request.dispose()
  },
})

export { expect } from '@playwright/test'
