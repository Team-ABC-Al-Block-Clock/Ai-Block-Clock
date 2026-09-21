import { env } from '@/lib/env'

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
}
