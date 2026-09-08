import { setupWorker } from 'msw/browser'

import { handlers } from '@/mocks/handlers'

/** 브라우저용 mock 서버. 시작은 `mocks/start.ts` 가 한다. */
export const worker = setupWorker(...handlers)
