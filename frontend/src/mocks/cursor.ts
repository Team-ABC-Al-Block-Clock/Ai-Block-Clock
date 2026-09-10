import type { CursorPage } from '@/api/page'
import { fail, ok } from '@/mocks/response'

/**
 * 커서 페이지네이션. 이 프로젝트의 목록은 전부 이 방식이다.
 * 핸들러 안에 인라인하면 목록마다 계산이 조용히 갈라진다.
 */

/** 명세 기본값 9, 최대 30. */
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

/** `size` 가 범위를 벗어나면 `null` 로 알린다 — 핸들러가 COMMON_001 로 응답한다. */
export function cursorParams(url: URL) {
  const cursor = url.searchParams.get('cursor') ?? undefined
  const raw = url.searchParams.get('size')
  if (raw === null) return { cursor, size: DEFAULT_SIZE }

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

/**
 * 명세가 아직 오지 않은 목록들의 공통 응답. 계산이 같아서 핸들러마다 다시 쓰지 않는다.
 *
 * 커서가 어긋나도 COMMON_001 로 돌려준다 — 마켓 목록의 MARKET_003 처럼 전용 코드를
 * 두려면 명세가 있어야 한다. 마켓 목록은 명세가 확정됐으므로 핸들러에서 직접 다룬다.
 */
export function cursorOk<T>(url: URL, items: T[], idOf: (item: T) => string) {
  const { cursor, size } = cursorParams(url)
  if (size === null) return fail(400, 'COMMON_001', '조회 조건이 올바르지 않습니다.')

  const page = cursorPage(items, idOf, cursor, size)
  if (!page) return fail(400, 'COMMON_001', '조회 기준이 올바르지 않습니다.')
  return ok(page)
}
