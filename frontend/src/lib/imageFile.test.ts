import { describe, expect, it } from 'vitest'

import { describeImageProblem } from '@/lib/imageFile'

const PNG = () => new File(['\u0089PNG'], 'me.png', { type: 'image/png' })

/** 가입·내 정보 수정·에이전트 등록이 같은 함수를 본다 — 따로 두면 판정이 갈린다. */
describe('describeImageProblem', () => {
  it('PNG · JPG 는 통과한다', () => {
    expect(describeImageProblem(PNG())).toBeNull()
    expect(describeImageProblem(new File(['x'], 'a.jpg', { type: 'image/jpeg' }))).toBeNull()
  })

  it('type 을 못 읽어도 확장자가 맞으면 통과한다', () => {
    // 플랫폼이 확장자를 매핑하지 못하면 File.type 이 빈 문자열이다. 여기서 막으면
    // 정상 이미지가 요청도 못 나간다.
    expect(describeImageProblem(new File(['x'], 'photo.JPG', { type: '' }))).toBeNull()
    // 잘못 등록된 .jpg 는 image/jpg 로 온다 — 표준 타입 목록에 없다.
    expect(describeImageProblem(new File(['x'], 'photo.jpg', { type: 'image/jpg' }))).toBeNull()
  })

  it('이미지가 아니면 문구를 준다', () => {
    expect(describeImageProblem(new File(['x'], 'a.gif', { type: 'image/gif' }))).toMatch(/PNG/)
    expect(describeImageProblem(new File(['x'], 'a.pdf', { type: 'application/pdf' }))).toMatch(
      /PNG/,
    )
  })

  it('5MB 를 넘으면 문구를 준다', () => {
    const big = new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'big.png', { type: 'image/png' })

    expect(describeImageProblem(big)).toMatch(/5MB/)
  })
})
