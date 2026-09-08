import { QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'

import './index.css'
import App from './App.tsx'
import { ErrorBoundary } from '@/components/ErrorBoundary'
import { queryClient } from '@/lib/queryClient'
import { startMocks } from '@/mocks/start'

/** mock 서버가 준비된 뒤에 렌더한다. 워커 등록이 끝나기 전에 나간 요청은
 *  mock 을 타지 않고 실서버로 새어 나간다. mock 이 꺼져 있으면 곧바로 이어진다. */
startMocks().then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <ErrorBoundary>
        <QueryClientProvider client={queryClient}>
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </QueryClientProvider>
      </ErrorBoundary>
    </StrictMode>,
  )
})
