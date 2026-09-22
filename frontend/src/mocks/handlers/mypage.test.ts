import { describe, expect, it } from 'vitest'

import { previewUrl, shots } from '@/mocks/handlers/mypage'

const PNG = (name: string) => new File(['\u0089PNG'], name, { type: 'image/png' })

/** 등록 요청의 파일 파트를 상세 응답 모양으로 옮기는 자리. 명세가 오면 함께 고친다. */
describe('등록 이미지 파트', () => {
  it('유형별로 order 를 0 부터 센다', () => {
    const form = new FormData()
    form.append('inputImageFiles', PNG('in-1.png'))
    form.append('inputImageFiles', PNG('in-2.png'))
    form.append('outputImageFiles', PNG('out-1.png'))

    expect(shots(form, 'INPUT').map((image) => [image.type, image.order])).toEqual([
      ['INPUT', 0],
      ['INPUT', 1],
    ])
    expect(shots(form, 'OUTPUT').map((image) => [image.type, image.order])).toEqual([['OUTPUT', 0]])
  })

  it('고르지 않은 유형은 빈 목록이다', () => {
    expect(shots(new FormData(), 'INPUT')).toEqual([])
  })

  it('파일이 아니면 주소를 만들지 않는다', () => {
    expect(previewUrl(null)).toBeNull()
    expect(previewUrl('문자열')).toBeNull()
    expect(previewUrl(PNG('icon.png'))).toBeTruthy()
  })
})
