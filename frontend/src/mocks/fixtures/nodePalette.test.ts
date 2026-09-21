import { describe, expect, it } from 'vitest'

import { PLACEABLE_ITEMS } from './nodePalette'

describe('PLACEABLE_ITEMS', () => {
  // 눈으로 세면 어긋난다. 11건 아래로 줄면 scope=MINE 의 무한 스크롤 검증이 조용히 사라진다.
  it('내 항목이 11건이다', () => {
    expect(PLACEABLE_ITEMS.filter((item) => item.mine)).toHaveLength(11)
  })

  it('항목 id 가 겹치지 않는다', () => {
    const ids = PLACEABLE_ITEMS.map((item) => item.itemId)
    expect(new Set(ids).size).toBe(ids.length)
  })
})
