import { describe, expect, it } from 'vitest'

import {
  blockLabel,
  chargeAmountLabel,
  dateTimeLabel,
  josa,
  maskName,
  priceLabel,
  ratingLabel,
  successLabel,
  timeLabel,
} from '@/lib/format'

/** 돈과 콜드스타트 경로. 손으로 쓴 자릿수 정규식과 null 분기는 검사가 있어야 한다. */

describe('priceLabel', () => {
  it('천 단위를 끊는다', () => {
    expect(priceLabel('540')).toBe('540 ABC')
    expect(priceLabel('1248')).toBe('1,248 ABC')
    expect(priceLabel(1248000)).toBe('1,248,000 ABC')
  })

  it('소수점 뒤 0 은 떼고, 의미 있는 소수는 남긴다', () => {
    // Jackson 이 BigDecimal 을 자릿수까지 붙여 준다 — 그게 화면에 새면 안 된다.
    expect(priceLabel('540.00')).toBe('540 ABC')
    expect(priceLabel('1248.50')).toBe('1,248.5 ABC')
    expect(priceLabel('1248.05')).toBe('1,248.05 ABC')
  })

  it('문자열을 숫자로 되돌리지 않는다', () => {
    // Number 를 거치면 오차가 생기는 자릿수. 문자열 그대로 통과해야 한다.
    expect(priceLabel('9007199254740993.1')).toBe('9,007,199,254,740,993.1 ABC')
  })
})

describe('chargeAmountLabel', () => {
  it('충전이 보내는 소수 6자리를 끊지 않는다', () => {
    // priceLabel 로 적으면 '1.12 ABC' 가 되어 실제로 보내는 값과 달라진다
    expect(chargeAmountLabel('1.123456')).toBe('1.123456 ABC')
    expect(chargeAmountLabel('2500')).toBe('2,500 ABC')
    expect(chargeAmountLabel('11.25')).toBe('11.25 ABC')
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

describe('successLabel', () => {
  it('0~1 을 백분율로 바꾸고 뒤의 0 은 뗀다', () => {
    expect(successLabel(0.982, 128)).toBe('98.2%')
    expect(successLabel(1, 128)).toBe('100%')
    expect(successLabel(0.75, 4)).toBe('75%')
  })

  it('실제로 100% 일 때만 100% 라고 쓴다', () => {
    // 반올림하면 9,999/10,000 이 '100%' 가 되어 '한 번도 실패하지 않았다' 는 과장이 된다.
    expect(successLabel(0.9999, 10000)).toBe('99.9%')
    expect(successLabel(0.9995, 10000)).toBe('99.9%')
    expect(successLabel(1, 10000)).toBe('100%')
  })

  it('실행이 0회면 0% 가 아니라 —', () => {
    // 실행이 없으면 서버도 비율을 만들 수 없어 0 이 온다.
    // 그대로 그리면 '전부 실패했다' 는 거짓말이 된다.
    expect(successLabel(0, 0)).toBe('—')
  })
})

describe('blockLabel', () => {
  it('블록 번호에 천 단위 구분을 넣는다', () => {
    expect(blockLabel(15230491)).toBe('#15,230,491')
  })

  it('아직 블록에 안 들어간 트랜잭션은 —', () => {
    expect(blockLabel(null)).toBe('—')
  })
})

describe('timeLabel', () => {
  it('서울 시각으로 MM.DD HH:mm 을 만든다', () => {
    // 명세 예시가 Z 표기다. 14:22Z 는 서울에서 다음 날 23:22 이다.
    expect(timeLabel('2026-09-07T14:22:00Z')).toBe('09.07 23:22')
    // 픽스처는 +09:00 표기 — 같은 시각이면 같은 문자열이어야 한다.
    expect(timeLabel('2026-09-07T23:22:00+09:00')).toBe('09.07 23:22')
  })

  it('자정을 24:00 이 아니라 00:00 으로 쓴다', () => {
    expect(timeLabel('2026-09-08T00:00:00+09:00')).toBe('09.08 00:00')
  })

  it('아직 실행하지 않았거나 값이 깨졌으면 —', () => {
    // 그대로 그리면 'Invalid Date' 가 카드에 박힌다.
    expect(timeLabel(null)).toBe('—')
    expect(timeLabel('언젠가')).toBe('—')
  })
})

describe('dateTimeLabel', () => {
  it('해를 떼지 않는다', () => {
    expect(dateTimeLabel('2026-09-01T09:00:00+09:00')).toBe('2026.09.01 09:00')
  })

  it('값이 없거나 깨졌으면 —', () => {
    expect(dateTimeLabel(null)).toBe('—')
    expect(dateTimeLabel('언젠가')).toBe('—')
  })
})

describe('maskName', () => {
  it('앞뒤 한 글자만 남기고 가운데를 가린다', () => {
    expect(maskName('김도현')).toBe('김*현')
    expect(maskName('남궁민수')).toBe('남**수')
  })

  it('두 글자는 뒤를, 한 글자는 그대로 둔다', () => {
    // 두 글자에서 가운데가 없다고 원본을 흘리면 마스킹을 넣은 의미가 없다.
    expect(maskName('한별')).toBe('한*')
    expect(maskName('별')).toBe('별')
  })

  it('앞뒤 공백은 길이에 세지 않는다', () => {
    // 공백까지 길이로 세면 ' 별 ' 이 '  *' 처럼 엉뚱하게 가려진다.
    expect(maskName('  한별  ')).toBe('한*')
    expect(maskName('   ')).toBe('')
  })
})

describe('josa', () => {
  it('받침이 있으면 을, 없으면 를', () => {
    expect(josa('메일 초안')).toBe('을')
    expect(josa('성적 분석 리포트')).toBe('를')
    // 받침이 없는 글자로 끝나는 이름
    expect(josa('문서 정리')).toBe('를')
  })

  it('한글이 아닌 끝 글자와 빈 이름은 를', () => {
    expect(josa('Contract Analyzer')).toBe('를')
    expect(josa('워크플로우 2')).toBe('를')
    expect(josa('   ')).toBe('를')
  })
})
