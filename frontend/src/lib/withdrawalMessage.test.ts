import { describe, expect, it } from 'vitest'

import { historyStatus } from './withdrawalMessage'

describe('historyStatus', () => {
  it('취소된 충전은 끝난 건으로 적는다', () => {
    expect(historyStatus('CHARGE', 'CANCELED')).toEqual({ label: '취소', tone: 'closed' })
  })
})
