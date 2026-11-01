import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'

import { keccak256, stringToHex } from 'viem'
import { privateKeyToAccount } from 'viem/accounts'

/**
 * 브로커 · 런타임까지 붙여 실행 흐름을 끝까지 보는 테스트(`tests/runtime`)의 환경.
 * `E2E_RUNTIME=1` 일 때만 켠다 — 그 밖의 테스트는 브로커도 런타임도 없이 돈다.
 *
 * 설정 파일(playwright.config.ts)과 도우미가 같은 값을 보도록 여기 한 곳에 둔다.
 * 설정 파일은 워커마다 다시 읽히므로 여기서는 값만 만들고 아무것도 쓰거나 지우지 않는다.
 */
export const RUNTIME_ENABLED = process.env.E2E_RUNTIME === '1'

/**
 * 이미 떠 있는 테스트용 RabbitMQ. 새로 띄우지 않는다.
 * 브로커 장애는 이 컨테이너를 잠깐 멈췄다(docker pause) 되살려(docker unpause) 만든다 — 지우거나 다시 만들지 않는다.
 */
export const BROKER = {
  container: process.env.E2E_RABBITMQ_CONTAINER ?? 'abc-rabbitmq-test',
  host: process.env.E2E_RABBITMQ_HOST ?? 'localhost',
  port: process.env.E2E_RABBITMQ_PORT ?? '5673',
  managementUrl: process.env.E2E_RABBITMQ_MANAGEMENT_URL ?? 'http://localhost:15673',
  username: process.env.E2E_RABBITMQ_USERNAME ?? 'guest',
  password: process.env.E2E_RABBITMQ_PASSWORD ?? 'guest',
} as const

/** ai/spike/linear-mvp. 백엔드가 감시 · 근거 백필에 HTTP 로 묻는 주소와 같아야 한다. */
export const RUNTIME = {
  dir: resolve(import.meta.dirname, '../../../ai/spike/linear-mvp'),
  host: '127.0.0.1',
  port: 8000,
  /**
   * mock 모델 호출 한 번마다 쉬는 초. mock 은 곧바로 끝나 진행 단계가 화면에 비치지 않는다.
   * 단계가 바뀌는 것을 보고, 도는 도중에 런타임을 죽일 틈을 만든다.
   */
  mockDelaySeconds: Number(process.env.E2E_MOCK_DELAY_SECONDS ?? '4'),
} as const

export const RUNTIME_URL = `http://${RUNTIME.host}:${RUNTIME.port}`

/**
 * 충전 확인이 묻는 SSAFY 체인 대신 답하는 가짜 JSON-RPC. 실제 체인에 거래를 만들 수 없어서다.
 * 워커 안에서 뜬다(support/fakeChain.ts).
 */
export const FAKE_CHAIN_PORT = Number(process.env.E2E_FAKE_CHAIN_PORT ?? '18545')

/**
 * 백엔드와 런타임이 함께 보는 폴더. 운영에서는 둘이 같은 볼륨을 붙인다(contract 3 · 5장).
 * test-results 는 실행마다 비워지므로 그 밖에 둔다.
 */
export const VOLUMES = join(tmpdir(), 'abc-e2e-runtime')
export const FILE_STORE_ROOT = join(VOLUMES, 'files')
export const RESULTS_ROOT = join(VOLUMES, 'results')
export const BACKEND_LOG = join(VOLUMES, 'backend.log')

/**
 * 백엔드 logback 설정(support/logging)은 콘솔에만 쓰고, 웹 서버 설정이 콘솔을 버린다.
 * 구현 버그를 재현할 때 볼 수 있게 런타임 테스트에서만 파일로 받는다.
 */
export const BACKEND_LOGBACK = pathToFileURL(join(import.meta.dirname, 'backend-logback.xml')).href

/**
 * 관리자 지갑. 정산 근거(관리자 API)로 DB 에 닫힌 실패 판정 · 근거를 확인할 때 쓴다.
 * 백엔드가 기동할 때 `ADMIN_WALLET_ADDRESSES` 로 받아야 하므로 실행마다 같은 지갑이어야 한다 — 고정 문자열에서 만든다.
 * 돈이 오가지 않는 로컬 H2 전용이다.
 */
export const ADMIN_WALLET = privateKeyToAccount(keccak256(stringToHex('abc-e2e-runtime-admin')))
