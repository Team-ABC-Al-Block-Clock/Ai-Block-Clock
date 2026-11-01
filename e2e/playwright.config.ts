import { defineConfig, devices } from '@playwright/test'

import {
  ADMIN_WALLET,
  BACKEND_LOG,
  BACKEND_LOGBACK,
  BROKER,
  FAKE_CHAIN_PORT,
  FILE_STORE_ROOT,
  RESULTS_ROOT,
  RUNTIME_ENABLED,
  RUNTIME_URL,
} from './tests/support/runtimeEnv'

const BACKEND = 'http://localhost:8080'
const FRONTEND = 'http://localhost:5173'

// 이미 떠 있는 서버를 쓰지 않는 게 기본이다. 개발 중인 프론트는 보통 mock 모드라, 그대로 붙으면
// 요청이 백엔드까지 가지 않고 mock 응답으로 통과한다. 포트가 막혀 있으면 실패하게 둔다.
// 서버를 직접 띄워 두고 반복해서 돌릴 때만 E2E_REUSE_SERVER=1 을 준다.
const reuseExistingServer = process.env.E2E_REUSE_SERVER === '1'

// 화면이 1440 폭 기준으로 그려져 있다.
const desktop = { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } }

/**
 * E2E_RUNTIME=1 이면 백엔드를 테스트용 브로커 · 런타임 · 가짜 체인에 붙이고 `runtime` 프로젝트를 켠다.
 * 런타임은 테스트가 직접 띄우고 내린다(support/runtime.ts) — 도중에 죽였다 다시 띄우는 시나리오가 있어서다.
 */
const runtimeBackendEnv: Record<string, string> = RUNTIME_ENABLED
  ? {
      RABBITMQ_HOST: BROKER.host,
      RABBITMQ_PORT: BROKER.port,
      RABBITMQ_USERNAME: BROKER.username,
      RABBITMQ_PASSWORD: BROKER.password,
      FILE_STORE_ROOT,
      RESULTS_ROOT,
      AI_RUNTIME_BASE_URL: RUNTIME_URL,
      SSAFY_CHAIN_RPC_URL: `http://127.0.0.1:${FAKE_CHAIN_PORT}`,
      ADMIN_WALLET_ADDRESSES: ADMIN_WALLET.address,
      // 구현 버그를 재현할 때 볼 로그. SQL 은 너무 많아 끈다.
      LOGGING_CONFIG: BACKEND_LOGBACK,
      LOGGING_FILE_NAME: BACKEND_LOG,
      SPRING_JPA_SHOWSQL: 'false',
    }
  : {}

export default defineConfig({
  testDir: './tests',
  outputDir: './test-results',
  // 첫 화면은 vite 가 의존성을 묶느라 20 ~ 40초 걸린다.
  timeout: 60_000,
  expect: { timeout: 10_000 },
  // 런타임 테스트는 런타임 프로세스 하나를 같이 쓰고, 메모리도 빠듯하다.
  workers: RUNTIME_ENABLED ? 1 : undefined,
  globalTeardown: RUNTIME_ENABLED ? './tests/support/runtimeTeardown.ts' : undefined,
  reporter: [
    ['list'],
    ['html', { open: 'never', outputFolder: 'playwright-report' }],
    ['json', { outputFile: 'test-results/results.json' }],
  ],
  use: {
    // 브라우저와 API 모두 프론트 dev 서버를 거친다. /api 는 vite 프록시가 백엔드로 넘기므로
    // 쿠키(SESSION)가 화면과 같은 출처에 붙는다 — 실제 사용과 같은 경로다.
    baseURL: FRONTEND,
    locale: 'ko-KR',
    timezoneId: 'Asia/Seoul',
    trace: 'retain-on-failure',
    // 통과한 화면은 테스트가 shot() 으로 남긴다. 여기서는 실패한 순간만 찍는다.
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'browser',
      testDir: './tests/browser',
      use: desktop,
    },
    {
      name: 'api',
      testDir: './tests/api',
    },
    ...(RUNTIME_ENABLED
      ? [
          {
            name: 'runtime',
            testDir: './tests/runtime',
            // 런타임 기동(10 ~ 20초)과 실행 몇 번을 한 테스트에서 기다린다.
            timeout: 240_000,
            use: desktop,
          },
        ]
      : []),
  ],
  webServer: [
    {
      // local 프로파일은 H2 인메모리라 실행마다 빈 DB 에서 시작한다.
      // 윈도우 cmd 가 현재 폴더를 검색하지 않는 환경이 있어 경로를 붙인다.
      command:
        process.platform === 'win32'
          ? '.\\gradlew.bat :core:core-api:bootRun'
          : './gradlew :core:core-api:bootRun',
      cwd: '../backend/ai-block-clock',
      url: `${BACKEND}/api/v1/auth/csrf`,
      env: {
        SPRING_PROFILES_ACTIVE: 'local',
        // 대기 중인 충전을 실제 체인에 다시 확인하지 않는다. 백엔드 테스트 설정과 같다.
        SPRING_APPLICATION_JSON: JSON.stringify({ payment: { charge: { 'polling-enabled': false } } }),
        ...runtimeBackendEnv,
      },
      timeout: 240_000,
      reuseExistingServer,
      stdout: 'ignore',
      stderr: 'pipe',
    },
    {
      command: 'npm run dev',
      cwd: '../frontend',
      url: FRONTEND,
      env: {
        // .env 의 VITE_USE_MOCK=true 보다 실행 환경 변수가 앞선다.
        VITE_USE_MOCK: 'false',
        VITE_API_PROXY_TARGET: BACKEND,
      },
      timeout: 60_000,
      reuseExistingServer,
    },
  ],
})
