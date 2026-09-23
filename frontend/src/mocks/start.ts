import { env } from '@/lib/env'

/** 워커가 안 붙어 새로 읽었다는 표시. 탭을 닫으면 사라진다. */
const RELOADED_KEY = 'abc:mock-sw-reloaded'

/** 사설 모드에서는 sessionStorage 접근 자체가 던진다. 표시가 없으면 한 번 더 읽을 뿐이다. */
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
  if (navigator.serviceWorker.controller) {
    mark.clear()
    return
  }
  if (mark.taken) {
    console.error('[mocks] 워커가 이 탭을 제어하지 못합니다. 요청이 프록시로 나가 502 가 됩니다.')
    return
  }
  mark.take()
  window.location.reload()
}
