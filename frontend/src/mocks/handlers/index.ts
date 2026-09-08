import { authHandlers } from '@/mocks/handlers/auth'

/**
 * 목 핸들러 전체.
 *
 * 도메인 하나가 파일 하나이고 `api/<도메인>.ts` 와 짝을 맞춘다.
 * 도메인을 추가하면 파일을 만들고 이 배열에 펼쳐 넣는다.
 */
export const handlers = [...authHandlers]
