import { describe, expect, it } from 'vitest'

import { queryFrom, searchPath, seedsFrom } from '@/routes/handoff'

/** 주소와 navigate state 는 사용자가 손댈 수 있다. 어긋난 값에서 화면이 죽지 않아야 한다. */

const params = (search: string) => new URLSearchParams(search)

describe('queryFrom', () => {
  it('앞뒤 공백을 떼고 돌려준다', () => {
    expect(queryFrom(params('?q=%20%20성적%20정리%20%20'))).toBe('성적 정리')
  })

  it('서버가 거절하는 길이는 미리 자른다', () => {
    expect(queryFrom(params(`?q=${'ㄱ'.repeat(240)}`))).toHaveLength(200)
  })

  it('없거나 비면 빈 문자열이다 — 화면은 이 값으로 랜딩으로 돌려보낸다', () => {
    expect(queryFrom(params(''))).toBe('')
    expect(queryFrom(params('?q='))).toBe('')
    expect(queryFrom(params('?q=%20%20'))).toBe('')
    expect(queryFrom(params('?query=성적'))).toBe('')
  })
})

describe('searchPath', () => {
  it('검색어를 그대로 실어 나른다 — 특수문자도 돌아온다', () => {
    const query = '성적 & 리포트 100%'

    expect(queryFrom(params(searchPath('/edit/searching', query).split('?')[1]))).toBe(query)
  })
})

describe('seedsFrom', () => {
  const agent = {
    agentId: 'ag_1',
    name: '성적 분석기',
    summary: '표를 받아 리포트를 쓴다',
    price: '6.000000',
  }

  it('선택한 순서를 지켜 캔버스 씨앗으로 바꾼다', () => {
    const seeds = seedsFrom({ agents: [agent, { ...agent, agentId: 'ag_2', name: '요약기' }] })

    expect(seeds.map((s) => s.id)).toEqual(['ag_1', 'ag_2'])
    expect(seeds[0]).toEqual({
      id: 'ag_1',
      name: '성적 분석기',
      nodeType: 'AGENT',
      // 저장할 때 서버가 찾을 원본. 화면용 id 와 값은 같지만 쓰임이 다르다.
      sourceType: 'AGENT',
      sourceId: 'ag_1',
      mine: false,
    })
  })

  it('추천에는 소유자가 없어 전부 남의 것으로 둔다', () => {
    // 반대로 두면 팔 수 없는 구성을 팔 수 있다고 말하게 된다.
    expect(seedsFrom({ agents: [agent] }).every((seed) => !seed.mine)).toBe(true)
  })

  it('모양이 어긋난 항목은 버린다', () => {
    const seeds = seedsFrom({ agents: [agent, { agentId: 'ag_3' }, null, '문자열'] })

    expect(seeds.map((s) => s.id)).toEqual(['ag_1'])
  })

  it('없으면 빈 배열 — 캔버스는 자리표시자로 시작한다', () => {
    expect(seedsFrom(null)).toEqual([])
    expect(seedsFrom({ agents: '두 개' })).toEqual([])
  })
})
