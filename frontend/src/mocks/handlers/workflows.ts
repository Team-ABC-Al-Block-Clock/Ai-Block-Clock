import { http } from 'msw'

import { API_BASE_URL } from '@/api/client'
import type { PaletteScope } from '@/api/workflows'
import { cursorOk, matchKeyword } from '@/mocks/cursor'
import { PLACEABLE_ITEMS, byMineThenUsed } from '@/mocks/fixtures/nodePalette'
import { fail } from '@/mocks/response'
import { scene } from '@/mocks/scenario'

/** `/workflows` 목록과 삭제는 마이페이지 화면이 쓰는 묶음이라 `mypage.ts` 에 있다. */

/** 명세 기본값. 마켓 목록(9)과 다르다. */
const PAGE_SIZE = 10

const isScope = (value: unknown): value is PaletteScope => value === 'MINE' || value === 'ALL'

export const workflowsHandlers = [
  http.get(`${API_BASE_URL}/workflows/node-palette`, async ({ request }) => {
    const forced = await scene()
    if (forced) return forced

    const url = new URL(request.url)
    const scope = url.searchParams.get('scope')
    // 필수값이다. 없을 때 ALL 로 떨어뜨리면 판매자 모드에 남의 에이전트가 섞인다.
    if (!isScope(scope)) return fail(400, 'COMMON_001', '조회 조건이 올바르지 않습니다.')

    const keyword = url.searchParams.get('keyword') ?? ''
    const items = PLACEABLE_ITEMS.filter((item) => scope === 'ALL' || item.mine)
      // 명세상 검색 대상은 이름뿐이다. 한 줄 설명은 보지 않는다.
      .filter((item) => matchKeyword(keyword, item.name))
      .sort(byMineThenUsed)

    return cursorOk(url, items, (item) => item.itemId, {
      defaultSize: PAGE_SIZE,
      cursorCode: 'MARKET_003',
      extra: { scope },
    })
  }),
]
