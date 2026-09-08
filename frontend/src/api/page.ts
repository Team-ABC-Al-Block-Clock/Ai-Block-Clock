/**
 * 목록 응답 봉투.
 *
 * ⚠︎ 필드명은 추정이다. API 명세서(S15P21A403-4)가 Notion 에 있고 링크가 아직
 * 공유되지 않아, 백엔드가 Spring 이고 기존 응답이 camelCase 라는 점에 근거해 정했다.
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
