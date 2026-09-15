import type { CursorPage } from '@/api/page'
import { fail, ok } from '@/mocks/response'

/**
 * 필드를 **각각** 본다. 이어 붙여 한 덩어리로 보면 이름 끝과 설명 앞에 걸친 말이
 * 매칭되는데, 서버는 `LIKE` 를 필드별로 걸어 실서버에서는 안 걸린다.
 */
export function matchKeyword(keyword: string, ...fields: string[]) {
  const q = keyword.trim().toLowerCase()
  if (!q) return true
  return fields.some((field) => field.toLowerCase().includes(q))
}

/** 마켓 기본값. 다른 목록은 호출부가 넘긴다. 최대는 공통 30. */
const DEFAULT_SIZE = 9
const MAX_SIZE = 30

/** 명세 예시가 base64({"id":"…"}) 라 그대로 흉내 낸다. */
export function encodeCursor(id: string) {
  return btoa(JSON.stringify({ id }))
}

/** 깨졌거나 모양이 다르면 null */
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

/**
 * `size` 가 범위를 벗어나면 `null` — 핸들러가 COMMON_001 로 응답한다.
 * 기본값을 인자로 받는다. 목록마다 달라서 한쪽으로 박으면 mock 과 실서버가 갈린다.
 */
export function cursorParams(url: URL, defaultSize = DEFAULT_SIZE) {
  const cursor = url.searchParams.get('cursor') ?? undefined
  const raw = url.searchParams.get('size')
  if (raw === null) return { cursor, size: defaultSize }

  const size = Number(raw)
  const isValid = Number.isInteger(size) && size >= 1 && size <= MAX_SIZE
  return { cursor, size: isValid ? size : null }
}

/**
 * 커서는 마지막으로 내려준 항목의 id 다. 그 id 가 목록에 없으면(삭제·위조) 만료된
 * 것이므로 `null` — 핸들러가 MARKET_003 으로 응답한다.
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

interface PageOptions {
  /** 마켓 9, 마이페이지·팔레트 10 */
  defaultSize?: number
  /** 커서가 만료·위조됐을 때의 코드 */
  cursorCode?: string
  /** 페이지 봉투와 나란히 놓이는 필드. 노드 팔레트가 적용된 `scope` 를 되돌려 준다 */
  extra?: Record<string, unknown>
}

/** 이미 걸러지고 정렬된 배열을 받아 페이지만 계산한다. */
export function cursorOk<T>(
  url: URL,
  items: T[],
  idOf: (item: T) => string,
  { defaultSize = DEFAULT_SIZE, cursorCode = 'COMMON_001', extra }: PageOptions = {},
) {
  const { cursor, size } = cursorParams(url, defaultSize)
  if (size === null) return fail(400, 'COMMON_001', '조회 조건이 올바르지 않습니다.')

  const page = cursorPage(items, idOf, cursor, size)
  if (!page) return fail(400, cursorCode, '조회 기준이 만료되었습니다. 처음부터 다시 불러오세요.')
  return ok({ ...extra, ...page })
}
