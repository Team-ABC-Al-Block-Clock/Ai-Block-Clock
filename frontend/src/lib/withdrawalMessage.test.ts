import { describe, expect, it } from 'vitest'

import { ApiError, WITHDRAWAL_ERROR } from '@/api/errors'

import { historyStatus, withdrawalErrorText } from './withdrawalMessage'

const rangeError = (data?: unknown) =>
  new ApiError('범위 밖', { code: WITHDRAWAL_ERROR.AMOUNT_OUT_OF_RANGE, status: 400, data })

describe('withdrawalErrorText', () => {
  it('금액 범위는 서버가 알려 준 값으로 적는다', () => {
    const error = rangeError({ minimumAmount: '5.000000', maximumAmount: '2500.000000' })

    expect(withdrawalErrorText(error)).toBe('한 번에 5 ~ 2,500 ABC 까지 출금할 수 있습니다.')
  })

  it('범위 값이 없으면 화면 상수로 적는다', () => {
    expect(withdrawalErrorText(rangeError())).toBe('한 번에 1 ~ 1,000 ABC 까지 출금할 수 있습니다.')
  })
})

describe('historyStatus', () => {
  it('취소된 충전은 끝난 건으로 적는다', () => {
    expect(historyStatus('CHARGE', 'CANCELED')).toEqual({ label: '취소', tone: 'closed' })
  })
})
