/**
 * 목록 응답 봉투. 이 프로젝트의 목록은 전부 커서 방식이다 —
 * 페이지 번호 봉투(Spring `Page` 축약형)는 쓰는 곳이 없어 지웠다.
 */

/**
 * 커서 페이지네이션 봉투. **확정** — GET /marketplace/agents 명세에서 왔다.
 *
 * 무한 스크롤로 불러오는 동안 새 항목이 등록되면 페이지 번호 방식은 항목이 밀려
 * 중복·누락이 생긴다. 그래서 커서를 쓴다.
 *
 * 전수를 만들지 않으므로 `totalElements` 가 없다. 화면에 전체 개수를 띄우려 하지 않는다.
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
  /** 한 번에 가져올 개수. 기본 9, 최대 30 */
  size?: number
}
