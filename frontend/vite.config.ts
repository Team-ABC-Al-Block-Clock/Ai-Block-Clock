import { fileURLToPath, URL } from 'node:url'

import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'
import type { InlineConfig } from 'vitest/node'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [react(), tailwindcss()],
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
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
    } satisfies InlineConfig,
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  }
})
