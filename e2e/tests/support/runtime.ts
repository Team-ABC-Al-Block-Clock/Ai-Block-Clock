import { execFileSync, spawn } from 'node:child_process'
import { mkdirSync, openSync } from 'node:fs'
import { join } from 'node:path'

import { BROKER, FILE_STORE_ROOT, RESULTS_ROOT, RUNTIME, RUNTIME_URL, VOLUMES } from './runtimeEnv'

/**
 * 실행 런타임(ai/spike/linear-mvp)을 테스트가 직접 띄우고 내린다. 웹 서버 설정에 두지 않은 것은
 * 런타임이 내려간 동안의 접수, 실행 도중 죽었다 다시 뜨는 경우를 시나리오가 만들어야 해서다.
 *
 * 띄울 때는 실제 모델을 부를 수 없게 키를 비운다. 화면이 mock 을 보내지 않더라도 모델 호출 전에 실패한다.
 */
const LAUNCHER = join(import.meta.dirname, 'runtime_launcher.py')

let currentLog: string | undefined

export interface RuntimeHealth {
  status: string
  broker?: 'connected' | 'disconnected'
}

export async function runtimeHealth(): Promise<RuntimeHealth | null> {
  try {
    const response = await fetch(`${RUNTIME_URL}/v1/health`, { signal: AbortSignal.timeout(2_000) })

    return response.ok ? ((await response.json()) as RuntimeHealth) : null
  } catch {
    return null
  }
}

/** 지금 떠 있는 런타임의 로그 파일. 실패했을 때 리포트에 붙인다. */
export const runtimeLog = () => currentLog

/** 런타임을 띄우고, 브로커에 붙어 SubmitRun 을 받기 시작할 때까지 기다린다. 이미 떠 있으면 그대로 둔다. */
export async function startRuntime() {
  if ((await runtimeHealth())?.broker === 'connected') return

  mkdirSync(FILE_STORE_ROOT, { recursive: true })
  mkdirSync(RESULTS_ROOT, { recursive: true })
  currentLog = join(VOLUMES, `runtime-${Date.now()}.log`)

  const out = openSync(currentLog, 'a')
  const child = spawn('uv', ['run', '--no-sync', 'python', LAUNCHER], {
    cwd: RUNTIME.dir,
    env: {
      ...process.env,
      PYTHONUTF8: '1',
      PYTHONUNBUFFERED: '1',
      BROKER_ENABLED: 'true',
      RABBITMQ_HOST: BROKER.host,
      RABBITMQ_PORT: BROKER.port,
      RABBITMQ_USERNAME: BROKER.username,
      RABBITMQ_PASSWORD: BROKER.password,
      FILE_STORE_ROOT,
      RESULTS_ROOT,
      LLM_API_KEY: '',
      GEMINI_API_KEY: '',
      ABC_SERVICE_TOKEN: '',
      E2E_MOCK_DELAY_SECONDS: String(RUNTIME.mockDelaySeconds),
      E2E_RUNTIME_HOST: RUNTIME.host,
      E2E_RUNTIME_PORT: String(RUNTIME.port),
    },
    stdio: ['ignore', out, out],
    windowsHide: true,
  })

  child.unref()

  await waitFor(
    async () => (await runtimeHealth())?.broker === 'connected',
    120_000,
    `런타임이 ${RUNTIME_URL} 에서 브로커에 붙지 못했다. 로그: ${currentLog}`,
  )
}

/**
 * 런타임을 강제로 끝낸다(SIGKILL · taskkill /F). 정상 종료가 아니라 죽는 것이다 —
 * 받아 둔 SubmitRun 은 ack 되지 않은 채 브로커로 돌아간다.
 */
export async function stopRuntime() {
  for (const pid of listeningPids(RUNTIME.port)) {
    killTree(pid)
  }

  await waitFor(async () => (await runtimeHealth()) === null, 30_000, '런타임이 내려가지 않았다')
}

function listeningPids(port: number): number[] {
  if (process.platform === 'win32') {
    const lines = execFileSync('netstat', ['-ano', '-p', 'TCP'], { encoding: 'utf8' }).split(/\r?\n/)
    const pids = lines
      .map((line) => line.trim().split(/\s+/))
      .filter((cols) => cols[3] === 'LISTENING' && cols[1]?.endsWith(`:${port}`))
      .map((cols) => Number(cols[4]))

    return [...new Set(pids)].filter((pid) => pid > 0)
  }

  try {
    return execFileSync('lsof', ['-ti', `tcp:${port}`, '-sTCP:LISTEN'], { encoding: 'utf8' })
      .split('\n')
      .filter(Boolean)
      .map(Number)
  } catch {
    return []
  }
}

function killTree(pid: number) {
  try {
    if (process.platform === 'win32') {
      execFileSync('taskkill', ['/PID', String(pid), '/T', '/F'], { stdio: 'ignore' })
    } else {
      process.kill(pid, 'SIGKILL')
    }
  } catch {
    // 그 사이 이미 끝났다.
  }
}

export async function waitFor(check: () => Promise<boolean>, timeoutMs: number, message: string) {
  const deadline = Date.now() + timeoutMs

  while (Date.now() < deadline) {
    if (await check()) return
    await new Promise((resolve) => setTimeout(resolve, 500))
  }

  throw new Error(message)
}
