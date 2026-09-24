import { env } from '@/lib/env'

/** 워커가 안 붙어 새로 읽었다는 표시. 탭을 닫으면 사라진다. */
const RELOADED_KEY = 'abc:mock-sw-reloaded'

/**
 * 사설 모드에서는 sessionStorage 접근 자체가 던진다. 그때는 이미 표시가 있는 것으로 본다 —
 * 표시를 남길 수 없으니 새로 읽어도 같은 자리로 돌아와 새로고침이 끝없이 돈다.
 */
function reloadMark(): { taken: boolean; take: () => void; clear: () => void } {
  try {
    return {
      taken: sessionStorage.getItem(RELOADED_KEY) !== null,
      take: () => sessionStorage.setItem(RELOADED_KEY, '1'),
      clear: () => sessionStorage.removeItem(RELOADED_KEY),
    }
  } catch {
    return { taken: true, take: () => undefined, clear: () => undefined }
  }
}

/**
 * `clients.claim()` 이 `worker.start()` 가 풀린 바로 뒤에 붙는 경우가 있다. 그만큼만
 * 기다려 본다 — 안 기다리면 멀쩡한 탭을 새 탭마다 한 번씩 헛되이 다시 읽는다.
 */
const CLAIM_GRACE_MS = 300

/** 새로고침이 끝내 문서를 바꾸지 않을 때 기다릴 만큼. */
const RELOAD_GIVE_UP_MS = 3000

/** 이 문서가 워커의 제어를 받게 되는지. 이미 받고 있으면 기다리지 않는다. */
function claimed(timeout: number): Promise<boolean> {
  const sw = navigator.serviceWorker
  if (sw.controller) return Promise.resolve(true)
  return new Promise((resolve) => {
    let timer = 0
    const settle = (ok: boolean) => {
      sw.removeEventListener('controllerchange', onChange)
      clearTimeout(timer)
      resolve(ok)
    }
    function onChange() {
      settle(true)
    }
    timer = window.setTimeout(() => settle(sw.controller !== null), timeout)
    sw.addEventListener('controllerchange', onChange)
  })
}

/**
 * `main.tsx` 가 렌더 전에 await 한다 — 워커 등록 전에 나간 요청은 그냥 새어 나간다.
 *
 * `import.meta.env.DEV` 를 여기서만 직접 읽는다. Vite 가 이 값을 `false` 로 치환해
 * 블록을 통째로 지워야 devDependency 인 msw 가 번들에서 빠진다.
 */
export async function startMocks() {
  if (!import.meta.env.DEV || !env.useMock) return

  const { worker } = await import('@/mocks/browser')
  await worker.start({
    // 'warn' 이면 이미지·폰트 같은 정적 요청까지 콘솔을 채운다.
    onUnhandledRequest: 'bypass',
  })

  /*
   * 워커를 등록해도 그 문서가 곧바로 워커의 제어를 받지는 않는다 — 처음 등록한
   * 직후나 워커가 사라졌다 되살아난 뒤가 그렇다. 그런 탭에서는 모든 요청이 mock 을
   * 지나쳐 프록시로 새어 나가 원인 모를 502 만 남는다. 한 번만 다시 읽어 붙인다.
   */
  const mark = reloadMark()
  if (await claimed(CLAIM_GRACE_MS)) {
    mark.clear()
    return
  }
  if (mark.taken) {
    console.error('[mocks] 워커가 이 탭을 제어하지 못합니다. 요청이 프록시로 나가 502 가 됩니다.')
    return
  }
  mark.take()
  window.location.reload()
  // 새로고침은 지금 이 작업을 멈추지 않는다. 그냥 두면 `main.tsx` 가 화면을 그리고
  // 첫 요청들이 워커를 지나쳐 502 로 깜빡인다 — 문서가 바뀔 때까지 여기서 멈춘다.
  // 영영 붙들지는 않는다. 새로고침이 걸리지 않으면 콘솔 한 줄 없는 백지만 남는다.
  await new Promise((resolve) => setTimeout(resolve, RELOAD_GIVE_UP_MS))
  console.error('[mocks] 새로고침이 걸리지 않았습니다. 요청이 프록시로 나가 502 가 됩니다.')
}
