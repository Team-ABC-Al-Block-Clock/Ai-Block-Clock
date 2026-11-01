import { describe, expect, it } from 'vitest'

import { endReasonFrom, redirectTargetFrom } from '@/routes/access'
import { paths } from '@/routes/paths'
import { SESSION_END_REASONS } from '@/stores/authStore'

/**
 * 로그인 화면이 읽는 두 값. 둘 다 라우터 state 에서 오고, state 는 사용자가 조작할 수 있다.
 *
 * `endReasonFrom` 이 사유 목록을 손으로 들고 있던 동안 `revoked`·`invalid` 가 걸러져
 * 안내가 화면에 닿지 못했다. 사유를 늘릴 때 같은 일이 반복되지 않게 표로 고정한다.
 */

describe('endReasonFrom', () => {
  it.each(SESSION_END_REASONS)('%s 는 그대로 통과한다', (reason) => {
    expect(endReasonFrom({ endedBy: reason })).toBe(reason)
  })

  it('사유가 없으면 null — 스스로 누른 로그아웃에 안내를 붙이지 않는다', () => {
    expect(endReasonFrom({ endedBy: null })).toBeNull()
    expect(endReasonFrom({})).toBeNull()
    expect(endReasonFrom(null)).toBeNull()
    expect(endReasonFrom(undefined)).toBeNull()
  })

  it('모르는 값은 버린다 — state 는 사용자가 만질 수 있다', () => {
    expect(endReasonFrom({ endedBy: 'hacked' })).toBeNull()
    expect(endReasonFrom({ endedBy: 42 })).toBeNull()
  })
})

describe('redirectTargetFrom', () => {
  it('내부 경로는 그대로 돌려준다', () => {
    expect(redirectTargetFrom({ from: '/my/agents' })).toBe('/my/agents')
    expect(redirectTargetFrom({ from: '/agent/agt_1?tab=INPUT#shot' })).toBe(
      '/agent/agt_1?tab=INPUT#shot',
    )
  })

  it('외부로 나가는 값은 홈으로 돌린다', () => {
    // `//evil.com` 과 `/\evil.com`(브라우저가 백슬래시를 슬래시로 읽는다)은
    // `/` 로 시작하지만 외부 주소가 된다.
    expect(redirectTargetFrom({ from: '//evil.com' })).toBe(paths.home)
    expect(redirectTargetFrom({ from: '/\\evil.com' })).toBe(paths.home)
    expect(redirectTargetFrom({ from: 'https://evil.com' })).toBe(paths.home)
  })

  it('값이 없거나 문자열이 아니면 홈으로', () => {
    expect(redirectTargetFrom({})).toBe(paths.home)
    expect(redirectTargetFrom(null)).toBe(paths.home)
    expect(redirectTargetFrom({ from: 7 })).toBe(paths.home)
  })
})
