import { delay } from 'msw'

import { fail } from '@/mocks/response'

/**
 * 로딩·실패 UI 를 확인하는 스위치. 화면을 고치지 않고 지연과 실패를 만든다.
 *
 *   ?mockDelay=1500                        모든 응답을 늦춘다
 *   ?mockFail=COMMON_001                   모든 응답을 실패시킨다 (기본 400)
 *   ?mockFail=AUTH_005&mockFailStatus=401
 *   ?mockFail=MARKET_009&mockFailPath=/reviews   경로가 맞는 요청만 실패시킨다
 *   ?mockFail=MARKET_009&mockFailAfter=1   앞의 1건은 통과시키고 그 뒤부터 실패
 *   ?mockDelay=0&mockFail=&mockFailAfter=0 끈다
 *
 * 한 화면이 여러 요청을 나란히 쏘면 `mockFail` 만으로는 전부 죽어서 그 화면의
 * 부분 실패 UI 를 볼 수 없다 — 상세가 죽으면 리뷰 카드 자체가 안 그려진다.
 * `mockFailPath` 는 요청 경로에 이 문자열이 들어간 것만 실패시킨다.
 *
 * 목록의 '다음 장' 실패는 첫 장이 성공한 뒤에만 볼 수 있다. `mockFailAfter` 로
 * 앞의 몇 건을 통과시킨다 — 새로고침하면 0 부터 다시 센다.
 *
 * localStorage 에 남겨 두므로 화면을 옮겨도 유지된다.
 */
const KEY = 'abc:mock-scenario'

interface Scenario {
  delayMs: number
  failCode: string
  failStatus: number
  /** 빈 문자열이면 모든 경로 */
  failPath: string
  /** 실패시키기 전에 통과시킬 요청 수 */
  failAfter: number
}

const EMPTY: Scenario = { delayMs: 0, failCode: '', failStatus: 400, failPath: '', failAfter: 0 }

/** 통과시킨 요청 수. 새로고침하면 0 으로 돌아간다 — 다시 보려면 새로고침이다. */
let passed = 0

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
    failPath: q.has('mockFailPath') ? (q.get('mockFailPath') ?? '') : stored.failPath,
    failAfter: q.has('mockFailAfter') ? Number(q.get('mockFailAfter')) || 0 : stored.failAfter,
  }
  if (
    q.has('mockDelay') ||
    q.has('mockFail') ||
    q.has('mockFailStatus') ||
    q.has('mockFailPath') ||
    q.has('mockFailAfter')
  )
    safeWrite(next)
  return next
}

/**
 * 핸들러 맨 앞에서 부른다. 지연을 적용하고, 강제 실패면 그 응답을 돌려준다.
 * 응답이 오면 핸들러 본문을 실행하지 않는다.
 *
 * `request` 를 넘기면 `mockFailPath` 로 경로를 좁힐 수 있다. 안 넘기면 경로를
 * 알 수 없으므로 좁히지 않고 늘 실패시킨다 — 조용히 안 걸리는 편보다 낫다.
 */
export async function scene(request?: Request) {
  const { delayMs, failCode, failStatus, failPath, failAfter } = resolve()
  if (delayMs > 0) await delay(delayMs)
  if (!failCode) return null
  if (failPath && request && !new URL(request.url).pathname.includes(failPath)) return null
  // 세는 것은 범위에 걸린 요청뿐이다 — 다른 화면의 요청까지 세면 몇 번째인지 어긋난다.
  if (passed < failAfter) {
    passed += 1
    return null
  }
  return fail(failStatus, failCode, 'mock 시나리오로 강제한 실패입니다.')
}
