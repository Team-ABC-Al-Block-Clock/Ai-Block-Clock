import { describe, expect, it } from 'vitest'

import { describeFailure, failureNotice } from './runFailure'

describe('describeFailure', () => {
  it('어디서 · 무엇이 · 무엇을 할지 한 줄씩 적는다', () => {
    expect(
      describeFailure({ stepName: '요약', errorCode: 'MODEL_TIMEOUT', fault: 'platform' }),
    ).toBe(
      '요약 단계에서 중단되었습니다.\n모델이 제때 답하지 않았습니다.\n잠시 후 다시 실행해 주세요.',
    )
  })

  it('모르는 코드는 기본 문구로, 비어 온 단계와 책임은 줄째 뺀다', () => {
    expect(describeFailure({ stepName: null, errorCode: 'NEW_CODE', fault: null })).toBe(
      '실행이 실패했습니다.',
    )
  })
})

describe('failureNotice', () => {
  const failure = { stepName: 'b1', errorCode: 'BRANCH_CONDITION_INVALID', fault: null }

  it('실행을 못 찾았거나 소식이 끊기면 그 안내가 먼저다', () => {
    expect(failureNotice({ missing: true, lost: true, failure, failedLabel: undefined })).toBe(
      '워크플로우를 다시 실행해 주세요.',
    )
    expect(failureNotice({ missing: false, lost: true, failure, failedLabel: undefined })).toBe(
      '실행은 계속되고 있습니다. 새로고침해 주세요.',
    )
  })

  it('엔진 id 대신 화면 이름으로 단계를 적는다', () => {
    expect(
      failureNotice({ missing: false, lost: false, failure, failedLabel: '점수 분기 · 평균 ≥ 60' }),
    ).toBe('점수 분기 · 평균 ≥ 60 단계에서 중단되었습니다.\n분기 조건이 없는 값을 가리킵니다.')
  })

  it('공개 이름을 아직 모르면 서버의 단계명을 대신 표시하지 않는다', () => {
    expect(failureNotice({ missing: false, lost: false, failure, failedLabel: undefined })).toBe(
      '분기 조건이 없는 값을 가리킵니다.',
    )
  })

  it('까닭이 없으면 워크플로우를 확인하라고 한다', () => {
    expect(
      failureNotice({ missing: false, lost: false, failure: null, failedLabel: undefined }),
    ).toBe(
      '실행 엔진이 사유를 알려 주지 않았습니다.\n재실행 후에도 실패한다면 워크플로우를 확인해주세요.',
    )
  })
})
