import { fileURLToPath, URL } from 'node:url'

import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv, type Plugin } from 'vite'

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
