import { describe, expect, it } from 'vitest'

import { sellingPrice } from '@/lib/price'

/** 판매가는 실행가에 수수료를 붙인 값이다. 최종 판단은 서버가 한다. */
describe('sellingPrice', () => {
  it('실행가에 수수료를 붙인다', () => {
    expect(sellingPrice('180')).toBe('216')
    expect(sellingPrice('0')).toBe('0')
  })

  it('소수점이 있어도 실수 오차를 남기지 않는다', () => {
    // 180 * 1.2 는 216.00000000000003 이다.
    expect(sellingPrice('180')).not.toContain('.')
    expect(sellingPrice('443.2')).toBe('531.84')
    expect(sellingPrice('0.5')).toBe('0.6')
  })

  it('읽을 수 없는 값이면 null 이다', () => {
    expect(sellingPrice('')).toBeNull()
    expect(sellingPrice('무료')).toBeNull()
    // 음수는 실행가가 될 수 없다.
    expect(sellingPrice('-10')).toBeNull()
  })
})
