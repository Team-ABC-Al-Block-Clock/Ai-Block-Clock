import { describe, expect, it } from 'vitest'

import { priceLabel } from '@/lib/format'

import { AGENT_COLUMNS, ENTRY_COLUMNS } from './config'
import { growthLabel, shareOf, shareSlots, tableRows } from './salesView'

describe('shareOf', () => {
  it('소수 첫째 자리까지 반올림한 백분율이다', () => {
    expect(shareOf('1', 3)).toBe(33.3)
    expect(shareOf('2', 3)).toBe(66.7)
  })

  it('합계가 0 이면 0 이다', () => {
    expect(shareOf('5', 0)).toBe(0)
  })
})

describe('growthLabel', () => {
  it('지난달이 없으면 그렇게 적고, 늘면 + 를 붙인다', () => {
    expect(growthLabel(null)).toBe('지난달 매출 없음')
    expect(growthLabel(12)).toBe('+12%')
    expect(growthLabel(0)).toBe('+0%')
    expect(growthLabel(-5)).toBe('-5%')
  })
})

describe('shareSlots', () => {
  const name = (id: string | null) => `이름-${id}`

  it('1위는 빼고 2 ~ 4위를 늘 세 칸으로 채운다', () => {
    const slots = shareSlots(
      [
        { agentId: 'a', amount: '50' },
        { agentId: 'b', amount: '30' },
      ],
      100,
      name,
    )
    expect(slots).toHaveLength(3)
    expect(slots[0]).toEqual({
      label: '이름-b',
      value: `${priceLabel('30')} (30%)`,
      width: '30%',
    })
    expect(slots[1]).toEqual({ label: '—', value: `${priceLabel(0)} (0%)`, width: '0%' })
  })
})

describe('tableRows', () => {
  it('건별 표는 구분 칸을 판매 / 직접 사용으로 색칠하고 굵게 쓴다', () => {
    const [sold, self] = tableRows(
      [
        ['t', 'n', '1', '판매'],
        ['t', 'n', '1', '직접 사용'],
      ],
      ENTRY_COLUMNS,
      false,
    )
    expect(sold.cells[3]).toMatchObject({ color: '#9ad0b4', weight: '600', w: '110px' })
    expect(self.cells[3]).toMatchObject({ color: '#ff8a8a', weight: '600' })
    expect(sold.cells[0]).toMatchObject({ color: 'rgba(255,255,255,.84)', weight: '400' })
    expect(sold.cells[2]).toMatchObject({ color: '#fff', weight: '600' })
  })

  it('에이전트별 표는 이름 칸을 본문색으로 쓰고 마지막 칸은 평범하다', () => {
    const [row] = tableRows([['n', '3회', '1', '10%']], AGENT_COLUMNS, true)
    expect(row.cells[0]).toMatchObject({ color: 'var(--color-body)', w: '380px' })
    expect(row.cells[3]).toMatchObject({ color: 'rgba(255,255,255,.84)', weight: '400' })
  })
})
