import { afterAll, afterEach, beforeAll } from 'vitest'

import { server } from './server'

/** 테스트 전체에서 mock 서버를 켠다. vite.config.ts 의 `test.setupFiles` 가 읽는다. */
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
// 한 테스트에서 덮어쓴 핸들러가 다음 테스트로 새지 않게 한다.
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
