import { describe, expect, it } from 'vitest'

import { paths, routePatterns } from '@/routes/paths'

/** 실행 화면의 임시 경로를 걷어냈다. 되살아나면 결과 화면이 접수 번호 없이 다시 열린다. */

describe('실행 경로', () => {
  it('결과 화면은 접수 번호를 받아야 만들어진다', () => {
    expect(paths.run.running('exe_1')).toBe('/run/exe_1/running')
    expect('withoutId' in paths.run.running).toBe(false)
  })

  it('결과 화면 패턴은 접수 번호가 붙은 하나뿐이다', () => {
    expect(routePatterns.runRunning).toEqual(['/run/:executionId/running'])
  })

  it('대기 화면은 접수 전이라 접수 번호를 받지 않는다', () => {
    expect(paths.run.waiting).toBe('/run/waiting')
    expect(routePatterns.runWaiting).toEqual(['/run/waiting'])
  })
})
