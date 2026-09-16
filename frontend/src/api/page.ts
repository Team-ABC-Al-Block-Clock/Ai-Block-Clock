/**
 * 커서 페이지네이션 봉투. 불러오는 동안 새 항목이 등록되면 페이지 번호 방식은
 * 항목이 밀려 중복·누락이 생긴다. 전수를 만들지 않으므로 `totalElements` 가 없다.
 */
export interface CursorPage<T> {
  content: T[]
  /** 다음 조회에 넘길 커서. 마지막이면 null */
  nextCursor: string | null
  hasNext: boolean
}

export interface CursorParams {
  /** 이전 응답의 `nextCursor`. 없으면 처음부터 */
  cursor?: string
  /** 기본 9, 최대 30 */
  size?: number
}
