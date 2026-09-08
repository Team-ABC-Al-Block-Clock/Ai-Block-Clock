import { env } from '@/lib/env'

/**
 * mock 서버를 켠다. `main.tsx` 가 렌더 전에 await 한다 —
 * 워커 등록이 끝나기 전에 나간 요청은 mock 을 타지 않고 그냥 새어 나간다.
 *
 * `import.meta.env.DEV` 를 여기서만 직접 읽는다. Vite 가 프로덕션 빌드에서
 * 이 값을 `false` 로 치환해 아래 블록을 통째로 지우고, 그래야 devDependency 인
 * msw 가 번들에 들어가지 않는다. `env.useMock` 만으로는 정적으로 지울 수 없다.
 */
export async function startMocks() {
  if (!import.meta.env.DEV || !env.useMock) return

  const { worker } = await import('@/mocks/browser')
  await worker.start({
    // 핸들러가 없는 요청은 그대로 통과시킨다. 'warn' 이면 이미지·폰트 같은
    // 정적 요청까지 콘솔을 채운다.
    onUnhandledRequest: 'bypass',
  })
}
