import { test as base } from '@playwright/test'

import { Api, login, type Wallet } from './auth'
import { signup } from './member'

interface Fixtures {
  /** 새 지갑으로 로그인한 상태. 브라우저 테스트에서 쓰면 page 도 그 쿠키로 열린다. */
  session: { wallet: Wallet }
  /**
   * 새 지갑으로 로그인하고 가입까지 마친 상태. page 도 같은 쿠키로 열리고 `/signup` 에 붙잡히지 않는다.
   * `api` 는 그 쿠키로 부르므로 데이터 준비를 화면 밖에서 할 때 쓴다.
   */
  member: { wallet: Wallet; api: Api }
  /** 로그인한 API 호출. API 테스트용. */
  api: Api
  /**
   * 지금 화면을 전체 스크린샷으로 남긴다. test-results/<테스트>/ 에 파일로 남고 HTML 리포트에 붙는다.
   * 화면이 바뀌는 지점마다 부른다 — 실패했을 때뿐 아니라 통과했을 때도 실제 화면을 눈으로 확인하려는 것이다.
   */
  shot: (name: string) => Promise<string>
}

export const test = base.extend<Fixtures>({
  session: async ({ page }, use) => {
    const { wallet } = await login(page.context().request)
    await use({ wallet })
  },

  member: async ({ page }, use) => {
    const request = page.context().request
    const { wallet, csrf } = await login(request)
    const api = new Api(request, csrf, wallet)

    await signup(api)
    await use({ wallet, api })
  },

  api: async ({ request }, use) => {
    const { wallet, csrf } = await login(request)
    await use(new Api(request, csrf, wallet))
  },

  shot: async ({ page }, use, testInfo) => {
    let step = 0

    await use(async (name) => {
      step += 1
      const path = testInfo.outputPath(`${String(step).padStart(2, '0')}-${name}.png`)

      await page.screenshot({ path, fullPage: true })
      await testInfo.attach(name, { path, contentType: 'image/png' })

      return path
    })
  },
})

export { expect } from '@playwright/test'
