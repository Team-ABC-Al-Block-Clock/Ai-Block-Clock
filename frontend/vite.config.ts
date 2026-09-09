import { fileURLToPath, URL } from 'node:url'

import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

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
    // jsdom 이 필요한 이유는 두 가지다 — mocks/scenario.ts 가 location·localStorage 를
    // 읽고, msw 가 '/api/v1/...' 상대 경로 핸들러를 풀려면 location.href 가 있어야 한다.
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
    },
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
