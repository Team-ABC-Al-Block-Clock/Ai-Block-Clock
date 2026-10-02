import { describe, expect, it } from 'vitest'

import { ApiError } from '@/api/errors'
import { refFileErrorText } from '@/lib/canvas/refFileMessage'

/** 서버가 거절한 코드가 화면 문구로 이어지는지. 코드는 BE `ErrorType` 에 있는 것만 쓴다. */

const errorOf = (code: string, status: number) => new ApiError('서버 문구', { code, status })

describe('refFileErrorText', () => {
  it('합계 초과는 다음에 할 일을 적는다 — 다시 눌러도 통과하지 않는다', () => {
    const text = refFileErrorText(errorOf('NODE_007', 413))

    expect(text).toContain('10MB')
    expect(text).toContain('삭제')
  })

  it('형식 거절은 받는 형식을 알린다', () => {
    expect(refFileErrorText(errorOf('NODE_006', 400))).toContain('PDF')
  })

  it('다른 곳에서 지워진 자료와 없는 노드를 가른다', () => {
    expect(refFileErrorText(errorOf('NODE_005', 404))).toContain('이미 삭제된')
    expect(refFileErrorText(errorOf('NODE_001', 404))).toContain('노드를 찾을 수 없습니다')
  })

  it('응답을 못 받은 것은 연결을 확인하라고 한다', () => {
    // status 가 없는 것이 곧 응답을 못 받은 것이다(`ApiError.isNetworkError`).
    const text = refFileErrorText(new ApiError('연결 실패', { code: 'NODE_007' }))

    expect(text).toContain('연결을 확인')
  })

  it('모르는 코드와 ApiError 가 아닌 것은 기본 문구로 떨어진다', () => {
    expect(refFileErrorText(errorOf('COMMON_001', 400))).toContain('잠시 후 다시')
    expect(refFileErrorText(new Error('알 수 없음'))).toContain('잠시 후 다시')
  })
})
