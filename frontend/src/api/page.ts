/**
 * 목록 응답 봉투. 두 가지가 있다 — 커서(확정)와 페이지(추정).
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

/**
 * 페이지 번호 봉투 (Spring `Page` 축약형).
 *
 * ⚠︎ 필드명은 추정이다. 이 모양을 쓰는 엔드포인트(`/agents`, 리뷰 목록 등)의 명세가
 * 아직 오지 않았다. 백엔드가 Spring 이고 기존 응답이 camelCase 라는 점에 근거해 정했다.
 * 명세가 오면 이 파일과 각 도메인 모듈만 고친다 — 화면은 건드리지 않는다.
 */
export interface Page<T> {
  content: T[]
  /** 0-based */
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface PageParams {
  page?: number
  size?: number
}
