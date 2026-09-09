import { describe, expect, it } from 'vitest'

import { creditLabel, ratingLabel } from '@/lib/format'

/** 돈과 콜드스타트 경로. 손으로 쓴 자릿수 정규식과 null 분기는 검사가 있어야 한다. */

describe('creditLabel', () => {
  it('천 단위를 끊는다', () => {
    expect(creditLabel('540')).toBe('540 Credit')
    expect(creditLabel('1248')).toBe('1,248 Credit')
    expect(creditLabel(1248000)).toBe('1,248,000 Credit')
  })

  it('소수점 뒤 0 은 떼고, 의미 있는 소수는 남긴다', () => {
    // Jackson 이 BigDecimal 을 자릿수까지 붙여 준다 — 그게 화면에 새면 안 된다.
    expect(creditLabel('540.00')).toBe('540 Credit')
    expect(creditLabel('1248.50')).toBe('1,248.5 Credit')
    expect(creditLabel('1248.05')).toBe('1,248.05 Credit')
  })

  it('문자열을 숫자로 되돌리지 않는다', () => {
    // Number 를 거치면 오차가 생기는 자릿수. 문자열 그대로 통과해야 한다.
    expect(creditLabel('9007199254740993.1')).toBe('9,007,199,254,740,993.1 Credit')
  })
})

describe('ratingLabel', () => {
  it('평점과 리뷰 수를 함께 보여준다', () => {
    expect(ratingLabel(4.7, 35)).toBe('4.7 (35)')
    // 정수 평점도 소수점 한 자리로 맞춘다 — 카드마다 자릿수가 흔들리지 않게
    expect(ratingLabel(4, 12)).toBe('4.0 (12)')
    expect(ratingLabel(4.5, 2400)).toBe('4.5 (2.4k)')
  })

  it('콜드스타트는 NaN·빈칸이 아니라 —', () => {
    expect(ratingLabel(null, 0)).toBe('—')
    // 평점은 있는데 리뷰가 0 인 모순 응답도 같은 자리로 떨어뜨린다
    expect(ratingLabel(0, 0)).toBe('—')
  })
})
