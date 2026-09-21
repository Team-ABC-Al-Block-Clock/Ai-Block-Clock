import { delay } from 'msw'

import { fail } from '@/mocks/response'

/**
 * 로딩·실패 UI 를 확인하는 스위치. 화면을 고치지 않고 지연과 실패를 만든다.
 *
 *   ?mockDelay=1500                        모든 응답을 늦춘다
 *   ?mockFail=COMMON_001                   모든 응답을 실패시킨다 (기본 400)
 *   ?mockFail=AUTH_014&mockFailStatus=401
 *   ?mockDelay=0&mockFail=                 끈다
 *
 * localStorage 에 남겨 두므로 화면을 옮겨도 유지된다.
 */
const KEY = 'abc:mock-scenario'

interface Scenario {
  delayMs: number
  failCode: string
  failStatus: number
}

const EMPTY: Scenario = { delayMs: 0, failCode: '', failStatus: 400 }

// localStorage 는 시크릿 창·차단 설정에서 접근 자체가 던진다.
function safeRead(): Scenario {
  try {
    const raw = localStorage.getItem(KEY)
    return raw ? { ...EMPTY, ...(JSON.parse(raw) as Partial<Scenario>) } : EMPTY
  } catch {
    return EMPTY
  }
}

function safeWrite(next: Scenario) {
  try {
    localStorage.setItem(KEY, JSON.stringify(next))
  } catch {
    /* 저장 못 해도 이번 세션 값은 살아 있다 */
  }
}

/** URL 쿼리가 있으면 그것이 이긴다. */
function resolve(): Scenario {
  const q = new URLSearchParams(location.search)
  const stored = safeRead()
  const next: Scenario = {
    delayMs: q.has('mockDelay') ? Number(q.get('mockDelay')) || 0 : stored.delayMs,
    failCode: q.has('mockFail') ? (q.get('mockFail') ?? '') : stored.failCode,
    failStatus: q.has('mockFailStatus')
      ? Number(q.get('mockFailStatus')) || 400
      : stored.failStatus,
  }
  if (q.has('mockDelay') || q.has('mockFail') || q.has('mockFailStatus')) safeWrite(next)
  return next
}

/**
 * 핸들러 맨 앞에서 부른다. 지연을 적용하고, 강제 실패면 그 응답을 돌려준다.
 * 응답이 오면 핸들러 본문을 실행하지 않는다.
 */
export async function scene() {
  const { delayMs, failCode, failStatus } = resolve()
  if (delayMs > 0) await delay(delayMs)
  if (failCode) return fail(failStatus, failCode, 'mock 시나리오로 강제한 실패입니다.')
  return null
}
