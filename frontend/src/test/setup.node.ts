import { afterAll, afterEach, beforeAll } from 'vitest'

import { server } from './server'

/**
 * jsdom 없이 도는 테스트를 위한 설정이다. jsdom 을 씌우면 axios 가 xhr 어댑터를 타고,
 * 거기서 `FormData` 가 `[object FormData]` 라는 문자열로 굳어 multipart 요청을 검사할 수 없다.
 *
 * 대신 mock 핸들러가 읽는 브라우저 저장소가 없으므로 최소한만 세워 둔다.
 */
const stored = new Map<string, string>()

Object.defineProperty(globalThis, 'localStorage', {
  configurable: true,
  value: {
    getItem: (key: string) => stored.get(key) ?? null,
    setItem: (key: string, value: string) => void stored.set(key, String(value)),
    removeItem: (key: string) => void stored.delete(key),
    clear: () => stored.clear(),
  },
})

// scenario.ts 가 `location.search` 로 mock 시나리오를 읽는다. 쿼리 없는 주소면 기본값이 된다.
Object.defineProperty(globalThis, 'location', {
  configurable: true,
  value: new URL('http://localhost/'),
})

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  stored.clear()
})
afterAll(() => server.close())
