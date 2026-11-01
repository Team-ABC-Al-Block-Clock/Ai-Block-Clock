# e2e

브라우저와 API 로 시스템 전체를 확인하는 Playwright 테스트. 백엔드도 프론트도 아닌 저장소 공용 패키지다.

테스트를 시작하면 백엔드(`bootRun`, local 프로파일 · H2 인메모리)와 프론트(`npm run dev`, mock 끔)를 직접 띄우고, 끝나면 내린다.

## 처음 한 번

```bash
cd frontend && npm ci          # 프론트 dev 서버가 쓴다
cd ../e2e && npm ci
npx playwright install chromium
```

## 실행

```bash
npm test                 # 전부
npm run test:browser     # 브라우저 (tests/browser)
npm run test:api         # API 만 (tests/api) — 화면이 아직 없는 기능
npx playwright test tests/browser/auth.spec.ts --headed   # 브라우저 창을 띄워 보며
npm run report           # HTML 리포트 — 단계별 스크린샷이 붙어 있다
```

- 8080 · 5173 포트가 비어 있어야 한다. 개발 중인 서버를 쓰지 않는 이유는 `playwright.config.ts` 주석에 있다.
  서버를 직접 띄워 두고 반복해서 돌릴 때만 `E2E_REUSE_SERVER=1 npm test`.
- 스크린샷은 `test-results/<테스트>/NN-<이름>.png` 에도 파일로 남는다.

## 쓰는 법

```ts
import { expect, test } from '../support/fixtures'

test('…', async ({ page, session, shot }) => {   // session: 새 지갑으로 로그인한 상태
  await page.goto('/my/agents')
  await shot('내-에이전트')                         // 화면이 바뀌는 지점마다
})

test('…', async ({ api }) => {                     // api: 로그인한 API 호출, CSRF 헤더를 붙인다
  expect((await api.post('/api/v1/...', { … })).status()).toBe(201)
})
```

| 도구 | 위치 | 용도 |
| --- | --- | --- |
| `session` | `support/fixtures.ts` | 쿠키만 넣는 로그인. 지갑 확장이 필요 없다 |
| `api` · `Api` | `support/fixtures.ts` · `support/auth.ts` | 로그인한 API 호출 |
| `shot(name)` | `support/fixtures.ts` | 전체 화면 스크린샷을 파일과 리포트에 남긴다 |
| `installMockWallet(page, wallet)` | `support/mockWallet.ts` | `window.ethereum` 을 심는다. 로그인 화면 자체를 볼 때만 |

테스트마다 새 지갑으로 로그인한다. DB 를 되돌리지 않으므로 다른 테스트가 남긴 데이터에 기대지 않는다.

## 브로커 · 런타임까지 (`tests/runtime`)

실행 접수 → RabbitMQ → 실행 런타임(`ai/spike/linear-mvp`) → 이벤트 → SSE → 화면을 끝까지 본다. `E2E_RUNTIME=1` 일 때만 `runtime` 프로젝트가 생긴다.
그 밖의 테스트는 지금처럼 브로커도 런타임도 없이 돈다.

```bash
# 테스트용 RabbitMQ 가 떠 있어야 한다 (AMQP localhost:5673, 관리 API localhost:15673, guest/guest)
cd ai/spike/linear-mvp && uv sync          # 처음 한 번
cd e2e && E2E_RUNTIME=1 npx playwright test tests/runtime --project=runtime
```

- 8080 · 5173 · 8000 · 18545 포트가 비어 있어야 한다. workers 는 1 이다.
- 11-02 부터 `abc.runs.dead` 의 인자가 바뀌었다(parking lot). 테스트 브로커에 예전 큐가 남아 있으면 406 으로 뜨지 않으니 한 번 지운다:
  `docker exec abc-rabbitmq-test rabbitmqctl delete_queue --if-empty abc.runs.dead`
- 런타임은 테스트가 띄우고 내린다(`support/runtime.ts`) — 내려간 동안의 접수, 도중에 죽였다 다시 띄우기를 시나리오가 만든다.
  모델은 부르지 않는다. 접수 요청에 `mock=true` 를 싣고, 런타임은 LLM 키 없이 띄운다. mock 호출마다 `E2E_MOCK_DELAY_SECONDS`(기본 4)초를 쉬어 진행 단계가 화면에 보이게 한다.
- 충전은 실제 체인 대신 가짜 JSON-RPC(`support/fakeChain.ts`, 18545)가 답한다. 백엔드는 `SSAFY_CHAIN_RPC_URL` 로 거기를 본다.
- 브로커 장애는 컨테이너를 잠깐 멈춰(`docker pause`) 만들고 반드시 되살린다. 컨테이너 이름은 `E2E_RABBITMQ_CONTAINER`(기본 `abc-rabbitmq-test`).
  브로커 주소가 다르면 `E2E_RABBITMQ_PORT` · `E2E_RABBITMQ_MANAGEMENT_URL` 로 바꾼다.
- 백엔드 · 런타임이 같이 보는 파일 폴더와 로그는 `%TEMP%/abc-e2e-runtime/` 에 있다(`backend.log`, `runtime-*.log`). 실패하면 리포트에도 붙는다.

| 도구 | 위치 | 용도 |
| --- | --- | --- |
| `member` | `support/fixtures.ts` | 로그인 + 가입까지 마친 새 지갑. page 와 `api` 가 같은 쿠키를 쓴다 |
| `admin` | `support/runtimeFixtures.ts` | 관리자 API(정산 근거)를 부르는 지갑. 런타임 모드 백엔드만 관리자로 안다 |
| `charge(api, amount)` | `support/credits.ts` | 충전 API 로 ABC 를 채운다(가짜 체인) |
| `startRuntime` · `stopRuntime` | `support/runtime.ts` | 런타임을 띄우고 / 강제로 끝낸다 |
| `takeSubmit` · `deadLetter` · `tapEvents` · `pauseBroker` | `support/broker.ts` | 큐의 SubmitRun 꺼내기, dead letter 만들기, RunEvent 엿보기, 브로커 멈추기 |
| `runAsMock(page)` | `support/executions.ts` | 화면의 "실행하기" 요청에 `mock=true` 한 칸만 더한다 |
