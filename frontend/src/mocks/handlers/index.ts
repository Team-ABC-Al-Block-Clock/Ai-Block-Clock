import { authHandlers } from '@/mocks/handlers/auth'
import { creditHandlers } from '@/mocks/handlers/credits'
import { marketplaceHandlers } from '@/mocks/handlers/marketplace'
import { mypageHandlers } from '@/mocks/handlers/mypage'
import { workflowsHandlers } from '@/mocks/handlers/workflows'

/** 도메인 하나가 파일 하나. `api/<도메인>.ts` 와 짝을 맞춘다. */
export const handlers = [
  ...authHandlers,
  ...creditHandlers,
  ...marketplaceHandlers,
  ...mypageHandlers,
  ...workflowsHandlers,
]
