import { fileURLToPath, URL } from 'node:url'

import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv, type Plugin } from 'vite'
import type { InlineConfig } from 'vitest/node'

/**
 * mock 핸들러는 HMR 로 갈아끼우지 않고 페이지를 다시 읽는다.
 *
 * 핫 교체하면 새 모듈이 만들어져도 이미 떠 있는 MSW 워커는 옛 핸들러 목록을
 * 들고 있다. 그러면 요청이 mock 을 안 타고 `onUnhandledRequest: 'bypass'` 로
 * 프록시에 나가, 백엔드가 없으면 **원인 모를 502** 로만 보인다.
 */
function reloadOnMockChange(): Plugin {
  return {
    name: 'reload-on-mock-change',
    handleHotUpdate({ file, server }) {
      if (!file.includes('/src/mocks/')) return
      server.ws.send({ type: 'full-reload' })
      return []
    },
  }
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [react(), tailwindcss(), reloadOnMockChange()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    // vite 의 defineConfig 는 test 키를 검사하지 않고 통째로 통과시킨다. environment 나
    // setupFiles 를 오타 내면 msw 가 안 켜진 채로 테스트가 실서버를 타면서 통과한다.
    // vitest/config 의 defineConfig 는 vite 8 과 Plugin 타입이 어긋나므로 test 만 타입을 붙인다.
    //
    // jsdom 이 필요한 이유는 두 가지다 — mocks/scenario.ts 가 location·localStorage 를
    // 읽고, msw 가 '/api/v1/...' 상대 경로 핸들러를 풀려면 location.href 가 있어야 한다.
    test: {
      projects: [
        {
          extends: true,
          test: {
            name: 'dom',
            environment: 'jsdom',
            setupFiles: ['./src/test/setup.ts'],
            exclude: ['**/node_modules/**', '**/dist/**', '**/*.node.test.ts'],
          },
        },
        // jsdom 에서는 multipart 요청이 문자열로 굳어 검사할 수 없다. setup.node.ts 참고.
        {
          extends: true,
          test: {
            name: 'node',
            environment: 'node',
            setupFiles: ['./src/test/setup.node.ts'],
            include: ['**/*.node.test.ts'],
            // node 의 http 어댑터는 절대 URL 을 요구한다 — 기본값 '/api' 로는 요청이 서지 않는다.
            env: { VITE_API_BASE_URL: 'http://localhost/api' },
          },
        },
      ],
    } satisfies InlineConfig,
    server: {
      port: 5173,
      // 포트가 막히면 조용히 다른 포트로 뜬다 — 출처가 달라진 것을 모른 채
      // 실서버에 붙어 상태 변경 요청만 거절당한다. 여기서 바로 실패하게 둔다.
      strictPort: true,
      proxy: {
        '/api': {
          target: env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  }
})
