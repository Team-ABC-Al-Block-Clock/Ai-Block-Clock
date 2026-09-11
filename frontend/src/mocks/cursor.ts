import type { CursorPage } from '@/api/page'
import { fail, ok } from '@/mocks/response'

/**
 * 목록 조회의 공통 계산 — 검색어 매칭과 커서 페이지네이션.
 *
 * 핸들러 안에 인라인하면 목록마다 계산이 조용히 갈라진다. 실제로 갈라진 적이 있다:
 * 마켓은 필드별로 보고 마이페이지는 `name + ' ' + summary` 를 한 덩어리로 봐서,
 * 두 필드에 걸친 검색어('초안 받은')가 마이페이지에서만 매칭됐다.
 */

/**
 * 명세상 검색 대상은 이름과 한 줄 설명뿐이고, **각각을 따로** 본다.
 *
 * 이어 붙여 한 덩어리로 보면 이름 끝과 설명 앞에 걸친 말이 매칭된다 —
 * 서버는 `LIKE` 를 필드별로 걸으므로 실서버에서는 안 걸린다.
 */
export function matchKeyword(keyword: string, ...fields: string[]) {
  const q = keyword.trim().toLowerCase()
  if (!q) return true
  return fields.some((field) => field.toLowerCase().includes(q))
}

/** 마켓 목록의 기본값. 마이페이지 목록은 10 이라 호출부가 넘긴다. 최대는 공통 30. */
const DEFAULT_SIZE = 9
const MAX_SIZE = 30

/** 명세 예시가 base64({"id":"..."}) 다 — `eyJpZCI6ImU2MmIwMWJiIn0`. 그대로 흉내 낸다. */
export function encodeCursor(id: string) {
  return btoa(JSON.stringify({ id }))
}

/** 깨졌거나 모양이 다르면 null. 화면의 만료 커서 처리를 검증할 수 있어야 한다. */
export function decodeCursor(raw: string): string | null {
  try {
    const parsed: unknown = JSON.parse(atob(raw))
    if (typeof parsed !== 'object' || parsed === null) return null
    const id = (parsed as { id?: unknown }).id
    return typeof id === 'string' ? id : null
  } catch {
    return null
  }
}

/** `size` 가 범위를 벗어나면 `null` 로 알린다 — 핸들러가 COMMON_001 로 응답한다.
 *
 *  기본값을 인자로 받는다. 목록마다 명세가 다른데(마켓 9, 마이페이지 10) 한쪽으로 박아 두면
 *  화면이 `size` 를 안 넘길 때 mock 과 실서버의 페이지 크기가 달라진다. */
export function cursorParams(url: URL, defaultSize = DEFAULT_SIZE) {
  const cursor = url.searchParams.get('cursor') ?? undefined
  const raw = url.searchParams.get('size')
  if (raw === null) return { cursor, size: defaultSize }

  const size = Number(raw)
  const isValid = Number.isInteger(size) && size >= 1 && size <= MAX_SIZE
  return { cursor, size: isValid ? size : null }
}

/**
 * 커서는 "마지막으로 내려준 항목의 id" 다. 그 다음 항목부터 `size` 개를 준다.
 *
 * 전체를 한 번에 주면 화면의 무한 스크롤이 아무 효과가 없어 검증이 안 된다.
 * 커서가 가리키는 id 가 목록에 없으면(삭제·비공개 전환·위조) 만료된 것이므로
 * `null` 을 돌려준다 — 핸들러가 MARKET_003 으로 응답한다.
 */
export function cursorPage<T>(
  items: T[],
  idOf: (item: T) => string,
  cursor: string | undefined,
  size: number,
): CursorPage<T> | null {
  let start = 0
  if (cursor) {
    const id = decodeCursor(cursor)
    const at = id === null ? -1 : items.findIndex((item) => idOf(item) === id)
    if (at < 0) return null
    start = at + 1
  }

  const content = items.slice(start, start + size)
  const hasNext = start + content.length < items.length
  const last = content.at(-1)
  return {
    content,
    nextCursor: hasNext && last ? encodeCursor(idOf(last)) : null,
    hasNext,
  }
}

/** 목록마다 다른 것은 기본 개수와 만료 커서의 오류 코드 둘뿐이다. */
interface PageOptions {
  /** 명세의 기본 `size`. 마켓 9, 마이페이지 10 */
  defaultSize?: number
  /** 커서가 만료·위조됐을 때의 코드. 마켓 목록과 `/users/me/*` 는 MARKET_003 */
  cursorCode?: string
}

/**
 * 목록 응답의 공통 마무리. **이미 걸러지고 정렬된 배열**을 받아 페이지만 계산한다.
 *
 * 검색·정렬은 목록마다 달라 핸들러가 하고, 커서 계산은 전부 같아서 여기 하나로 모은다.
 * 핸들러 안에 인라인하면 `size` 검증과 만료 커서 처리가 목록마다 조용히 갈라진다.
 */
export function cursorOk<T>(
  url: URL,
  items: T[],
  idOf: (item: T) => string,
  { defaultSize = DEFAULT_SIZE, cursorCode = 'COMMON_001' }: PageOptions = {},
) {
  const { cursor, size } = cursorParams(url, defaultSize)
  if (size === null) return fail(400, 'COMMON_001', '조회 조건이 올바르지 않습니다.')

  const page = cursorPage(items, idOf, cursor, size)
  if (!page) return fail(400, cursorCode, '조회 기준이 만료되었습니다. 처음부터 다시 불러오세요.')
  return ok(page)
}
