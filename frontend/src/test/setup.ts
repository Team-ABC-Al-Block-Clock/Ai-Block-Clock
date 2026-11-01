import { afterAll, afterEach, beforeAll } from 'vitest'

import { server } from './server'

/** 테스트 전체에서 mock 서버를 켠다. vite.config.ts 의 `test.setupFiles` 가 읽는다. */
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
// 한 테스트에서 덮어쓴 핸들러가 다음 테스트로 새지 않게 한다.
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

// jsdom 에는 구성 가능한 스타일시트가 없다 — shadow DOM 컴포넌트의 addStyle 이 여기서 터진다.
if (!CSSStyleSheet.prototype.replaceSync) {
  CSSStyleSheet.prototype.replaceSync = function () {}
  const adopted = new WeakMap<ShadowRoot, CSSStyleSheet[]>()
  Object.defineProperty(ShadowRoot.prototype, 'adoptedStyleSheets', {
    get(this: ShadowRoot) {
      return adopted.get(this) ?? []
    },
    set(this: ShadowRoot, sheets: CSSStyleSheet[]) {
      adopted.set(this, sheets)
    },
  })
}
