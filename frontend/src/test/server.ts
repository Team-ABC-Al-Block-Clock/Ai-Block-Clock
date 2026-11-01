import { setupServer } from 'msw/node'

import { handlers } from '@/mocks/handlers'

/**
 * 브라우저의 `mocks/browser.ts` 와 같은 핸들러를 Node 에서 돌린다.
 *
 * 핸들러를 테스트용으로 따로 만들지 않는 것이 요점이다 — 갈라지면 테스트가 통과해도
 * 화면이 받는 응답은 다를 수 있다.
 */
export const server = setupServer(...handlers)
