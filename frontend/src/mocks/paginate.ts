import type { Page } from '@/api/page'

/** 마켓 카드가 한 화면에 12개 들어간다. */
const DEFAULT_SIZE = 12

export function pageParams(url: URL) {
  const page = Math.max(0, Number(url.searchParams.get('page')) || 0)
  const raw = Number(url.searchParams.get('size')) || DEFAULT_SIZE
  return { page, size: Math.min(Math.max(1, raw), 100) }
}

/** 전체를 한 번에 주면 화면의 페이지 이동이 동작하지 않아 검증이 안 된다. */
export function paginate<T>(items: T[], page: number, size: number): Page<T> {
  const start = page * size
  return {
    content: items.slice(start, start + size),
    page,
    size,
    totalElements: items.length,
    totalPages: Math.max(1, Math.ceil(items.length / size)),
  }
}
