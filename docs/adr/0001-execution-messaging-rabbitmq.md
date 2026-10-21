# ADR-0001: AI 실행 접수 · 결과 전달을 RabbitMQ 로 옮긴다

**Status:** Accepted
**Date:** 2026-10-16 (결정) · 2026-10-21 (구현 코드 · E2E 와 대조해 갱신 — [변경 이력](#변경-이력))
**Deciders:** 백엔드 · AI 파트
**Supersedes:** `ai/docs/integration-guide.md` §4 「왜 큐를 쓰지 않는가」(2026-09-19)

---

## Context

### 결정 전 구조

```
사용자 ──POST /runs──▶ 백엔드 ──① 선차감──▶ ② POST /v1/runs (동기 HTTP) ──▶ 런타임 ──▶ Gemini
                          │                       ◀── 202 run_id
                          └─③ execution-poll 스레드 4개가 2초마다 GET /v1/runs/{id}
                               끝나면 닫기 · 환불 · 정산 → GET /artifact · /pdf
사용자 화면 ◀── SSE (백엔드가 폴링 결과를 중계)
```

- 백엔드 → 런타임 접수는 **사용자 요청 안의 동기 HTTP**였다(`ExecutionService.dispatch`). 런타임이 죽어 있거나 꽉 차 있으면 사용자가 곧바로 502 · 503 을 받았다.
- 결과는 **2초 폴링(pull)** 이었다(`ExecutionMonitor`). 런타임은 백엔드에 아무것도 알리지 않았다.
- 런타임의 실행 상태는 **프로세스 메모리**(`_runs` dict)에만 있다. 재배포하면 도는 실행과 끝난 실행이 모두 사라진다.
- 운영은 EC2 한 대에 백엔드 1 · 런타임 1(uvicorn 단일 프로세스) · MySQL 1. 실행은 분당 몇 건, 노드 하나 15 ~ 35초, 실행 하나 1 ~ 3분.

### 큐 없이 먼저 막은 것 (2026-10-15)

같은 문제를 큐 없이 먼저 고쳤다. 결정할 때는 이것들이 어느 선택지에서도 그대로 남는다고 봤지만, 구현하며 셋이 바뀌었다(오른쪽 칸).

| 구멍 | 10-15 에 막은 방법 | 이 결정 뒤 |
| --- | --- | --- |
| 접수 응답이 끊기면 환불하는데 런타임은 계속 돌아 LLM 비용만 나감 | 멱등 키 `client_run_id` + 같은 키로 1회 재시도 + 취소 API | 멱등 키가 `execution_id`(메시지 `message_id`)로 바뀌었다. 접수는 outbox 가 다시 보내고 런타임이 중복을 거른다 |
| 백엔드 재기동 시 도는 실행을 아무도 안 따라가 선차감이 묶임 | 복구 스케줄러(`ExecutionRecoveryScheduler`, 기동 시 + 60초) | 남는다. 다만 엔진에 묻지 않고, 도는 실행을 감시에 다시 붙이기만 한다 |
| 런타임이 잊은 실행(404)이 영영 닫히지 않음 | `RUN_NOT_FOUND` 면 곧바로 `ABC_RUNTIME_LOST` 로 닫고 환불 | 곧바로 닫지 않는다. 넘긴 지 10분 + 유예가 지나도록 모르면 그때 닫는다(규칙 6) |
| 동시 실행 무제한으로 GMS 할당량에 그대로 부딪힘 | 런타임 동시 실행 상한 + 503 `ENGINE_BUSY` | prefetch 로 바뀌고 503 은 없어졌다(규칙 3) |
| 정산 다툼에 꺼낼 근거가 없음 | `execution_step` · `execution_run_record`. 닫기 커밋 뒤 비동기로 적고 백필로 메움 | 남는다. 이벤트로 닫힌 실행은 완료 이벤트에서 근거를 얻고, 감시가 닫은 실행만 엔진에 다시 묻는다 |

### 남아 있던 결합

1. **접수가 런타임 가용성에 묶여 있다.** 사용자의 실행 요청이 성공하려면 그 순간 런타임이 살아 있고 자리가 있어야 한다.
2. **결과 전달이 폴링이다.** 진행 상황이 최대 2초 늦고, 실행 수만큼 조회가 반복된다. 폴링 스레드가 엔진 호출에 묶인다.
3. **백엔드가 내려간 동안의 결과는 대조로만 메워진다.** 유실은 없지만 최대 60초 늦게 닫힌다.

### 제약 · 힘

- **돈이 걸려 있다.** 같은 실행을 두 번 정산하면 안 되고, 선차감이 영영 묶여도 안 된다. 닫기(`ExecutionReservationManager.finish`)는 이미 멱등이다(행 잠금 + 상태 확인 + 장부 멱등 키 + `(execution_id, step_id)` 유니크).
- **런타임 상태는 메모리다.** 보내는 쪽에 내구성이 없으므로, 어떤 전송을 쓰든 "보내기 직전에 죽는" 구멍은 남는다 → 대조는 어느 선택지에서도 없앨 수 없다.
- **규모가 작다.** 처리량 때문에 브로커가 필요한 상황은 아니다.
- **프로젝트는 끝났다(2026-09-28).** 운영 부담보다 구조를 바로 세우고 배우는 것의 비중이 크다.

---

## Decision

**실행 접수 · 진행 · 완료 · 취소를 RabbitMQ 로 옮긴다.** 추천 · 워크플로우 검증 · 서식 변환처럼 사용자가 그 자리에서 답을 기다리는 동기 요청은 HTTP 로 둔다. 대조(끝나지 않은 실행을 HTTP `GET /v1/runs/{id}` 로 한 번 묻기)는 안전망으로 남긴다.

### 토폴로지

```
[백엔드]                                              [런타임]
 접수 ──한 트랜잭션──▶ execution(QUEUED) + execution_outbox
 디스패처 ──publish──▶ abc.runs (direct)
 (커밋 직후 + 5초마다)   └▶ abc.runs.submit  ──consume(prefetch=MAX_ACTIVE_RUNS, 기본 4)──▶ 실행
                            (quorum, TTL 10분, x-delivery-limit=1 → 배달 최대 2번)
                              │ 만료 · 한도 초과 · 읽을 수 없음
                              ▼
                        abc.runs.dlx (fanout) ─▶ abc.runs.dead ──▶ 백엔드: 플랫폼 탓으로 닫고 전액 환불

 취소 ─────publish──▶ abc.control (fanout) ──▶ 런타임마다 전용 큐(exclusive · auto-delete) ──▶ cancel 표시

 리스너 ◀──consume── abc.events.backend ◀── abc.events (topic, run.#) ◀──publish── 진행 · 완료
                     (quorum, 단일 활성 컨슈머 → 실행별 순서 보장)
                       │ 처리 4번 실패(처음 + 재시도 3)
                       ▼
                     abc.events.dlx (fanout) ─▶ abc.events.backend.dlq (사람이 확인. 실행은 감시가 닫는다)
```

토폴로지는 백엔드(`RunTopology`)와 런타임(`messaging.py`)이 같은 인자로 각자 멱등 선언한다. 어느 쪽이 먼저 떠도 된다.

### 메시지

| 메시지 | 주요 칸 |
| --- | --- |
| **SubmitRun** | `execution_id`(멱등 키 = 런타임의 run_id = 메시지 `message_id`), `mode`, `workflow`, `input`, `comment`, `mock`, `files[]` · `ref_files{}`(공유 볼륨 경로) |
| **RunEvent** | `execution_id`, `attempt`(= `x-delivery-count` + 1), `seq`, `type`(`accepted` · `progress` · `succeeded` · `failed` · `rejected`), `snapshot` — `GET /v1/runs/{id}` 응답과 **같은 모양**(같은 함수로 만든다). 완료 이벤트는 노드 출력 · 봉투 JSON · 결과물 경로를 싣는다 |
| **CancelRun** | `execution_id` |

`rejected` 는 정의 오류만이 아니다. `INVALID_MODE` · `INVALID_INPUT_JSON` · `FILE_PATH_REJECTED` · `UNSUPPORTED_FILE_TYPE` · `FILE_TOO_LARGE` 도 실행 전에 `rejected` 로 끝난다.

### 핵심 규칙

1. **접수는 DB 를 outbox 로 쓴다.** 사용자 요청은 선차감 + `QUEUED` + `execution_outbox` 행을 한 트랜잭션에 쓰고 202 를 준다(엔진을 부르지 않는다).
   디스패처가 커밋 직후 한 번 발행하고, 놓친 것은 5초마다 다시 발행한다. publisher confirm(기한 5초)을 받은 뒤에만 `run_id` 를 적는다.
   **접수 후 10분이 지나도록 넘기지 못하면** 취소를 걸고 전액 환불한다(`ABC_RUNTIME_UNREACHABLE`).
   두 번 나가도 런타임이 `execution_id` 로 거른다 — 단, 이 거르기는 **런타임 프로세스 메모리**(24시간 보관)라 런타임 재시작 · 런타임 여러 대에서는 거르지 못한다.
2. **런타임은 완료 이벤트가 브로커에 confirm 된 뒤 submit 메시지를 ack 한다.** 실행은 스레드에서 돌고, confirm 대기와 ack · nack 은 이벤트 루프로 되돌린다(하트비트 유지).
   실행 도중 죽으면 다시 배달되어 재실행된다(실행은 최대 2번 — 처음 + 재실행 1번). 두 번 다 실패하면 dead letter → 환불.
3. **동시 실행 제한은 prefetch 로 한다.** prefetch = `MAX_ACTIVE_RUNS`(기본 4) = 실행 스레드 수. 503 `ENGINE_BUSY` 는 없어졌다. 넘치는 실행은 큐에서 기다리고, 10분을 넘기면 TTL 로 dead letter → 환불.
4. **백엔드 리스너는 DB 커밋 뒤 ack 한다.** 완료면 기존 멱등 `finish` 로 닫는다. `(attempt, seq)` 가 거꾸로 온 스냅샷은 버린다. 이벤트가 confirm 기록보다 먼저 오면 넘긴 것으로 표시한다.
   처리에 실패하면 1초 간격으로 세 번 더 시도하고, 그래도 안 되면 `abc.events.dlx` 로 보낸다. 읽을 수 없는 메시지는 다시 시도하지 않는다.
5. **파일은 claim check.** 메시지에는 경로만 싣는다. 백엔드 `file-store` 볼륨을 런타임에 읽기 전용으로 마운트하고, 런타임이 경로를 검사한다(절대 경로 · `..` · 볼륨 밖 심볼릭 링크 · 없는 파일 거절).
   결과는 런타임이 공유 `results/{execution_id}/` 볼륨에 쓰고, 백엔드는 이벤트에 실린 경로가 아니라 `execution_id` 로 정해진 경로에서 `artifact.json` · `report.pdf` 를 읽는다.
6. **감시.** `accepted` 이후, **마지막 이벤트부터** 기한(단계 수 × 60초 + 60초)을 재고 이벤트가 올 때마다 다시 잰다. 10초마다 기한을 점검하고, 넘기면 HTTP 로 한 번 묻는다.

   | 엔진의 답 | 처리 |
   | --- | --- |
   | 끝났다 | 그 결과로 닫는다 |
   | 모른다 · 닿지 않는다 (넘긴 지 10분 + 유예 전) | 기한을 다시 건다 |
   | 모른다 (그 뒤) | `ABC_RUNTIME_LOST` 로 닫고 환불 |
   | 닿지 않는다 (그 뒤) | `ABC_RUNTIME_STALLED` 로 닫고 환불 |
   | 아직 돈다 | 취소를 보내고 `ABC_RUNTIME_STALLED` 로 닫는다 |

   `accepted` 이전(`QUEUED`) 대기는 감시하지 않고 TTL 이 맡는다. 복구 스케줄러(기동 시 + 60초)는 도는 실행을 감시에 다시 붙이기만 한다.
7. **dead letter 는 사유별로 닫는다.** 모두 플랫폼 탓이고 사용자는 전액 환불받는다. 이미 성공한 단계의 공급자 몫은 플랫폼이 낸다.

   | dead letter 사유 | 실패 코드 |
   | --- | --- |
   | `expired` (큐 대기 10분 초과) | `ABC_QUEUE_EXPIRED` |
   | `delivery_limit` (런타임이 두 번 죽음) | `ABC_RUNTIME_RETRY_EXHAUSTED` |
   | 그 밖 (읽을 수 없는 메시지 등) | `ABC_RUNTIME_DEAD_LETTERED` |

---

## Options Considered

### Option A: RabbitMQ (선택)

| Dimension | Assessment |
| --- | --- |
| Complexity | **Med** — 브로커 · 토폴로지 · Java/Python 양쪽 컨슈머 · outbox 디스패처 · DLQ |
| Cost | 브로커 컨테이너 1개(메모리 수백 MB). 새 단일 실패점 1개 |
| Scalability | 런타임을 여러 대로 늘리면 경쟁 컨슈머로 바로 나뉜다(런타임 상태 외부화는 별도) |
| Team familiarity | Spring AMQP(`@RabbitListener`)는 익숙한 축. Python 쪽(`aio-pika`)은 새로 익혀야 함 |

**Pros**
- 백엔드가 내려가 있어도 결과 이벤트가 큐에 남는다 — 대조를 기다리지 않는다.
- ack 전에 런타임이 죽으면 **자동 재실행**된다. 결정 전에는 환불(LOST)로 끝났다.
- 동시 실행 제한(prefetch) · 재시도 · 실패 메시지 격리(DLX · TTL · delivery-limit)를 브로커가 기본으로 준다.
- 접수가 런타임 가용성에서 떨어진다. 런타임이 잠깐 죽어도 실행은 큐에서 기다린다.
- 런타임 수평 확장의 길이 열린다.

**Cons**
- 브로커가 새 인프라 · 새 단일 실패점이다. 한 대로 띄우면 클러스터의 이점은 없다.
- 백엔드 → 브로커는 DB 커밋과 publish 의 **이중 쓰기**라 outbox 가 어차피 필요하다.
- 접수 시 동기 검증(`INVALID_WORKFLOW` 400)이 사라지고, 실행 화면의 "실패"로 늦게 보인다.
- Python `aio-pika` 에서 긴 작업 중 하트비트를 지키려면 실행을 스레드로 빼고 ack 를 이벤트 루프로 되돌려야 한다.
- 재실행은 LLM 비용을 한 번 더 쓴다.

### Option B: DB 큐 + 콜백 + 대조

`execution` 테이블의 `QUEUED`(아직 넘기지 않음) 행을 큐로 쓰고, 디스패처가 HTTP 202 로 접수, 런타임은 진행 · 완료를 HTTP 콜백으로 알리고, 놓친 콜백은 복구 스케줄러가 메운다.

| Dimension | Assessment |
| --- | --- |
| Complexity | **Low** — 디스패처 클래스 1개 + 콜백 엔드포인트 1개. 멱등 키 · 복구 스케줄러는 이미 있다 |
| Cost | 추가 인프라 없음(MySQL 재사용). 새 단일 실패점 없음 |
| Scalability | 지금 규모엔 충분. 런타임 여러 대면 디스패처가 라우팅을 떠안는다 |
| Team familiarity | 높음 — 이미 쓰는 Spring · JPA · 스케줄러 패턴 그대로 |

**Pros**
- 선차감과 큐 항목이 **같은 트랜잭션**이다(자연스러운 outbox). 이중 쓰기가 없다.
- 런타임 변경이 거의 없다(접수는 HTTP 202, 콜백은 이미 있는 `on_event` 훅).
- 운영할 것이 늘지 않는다.

**Cons**
- 콜백은 내구성이 없다. 백엔드가 내려간 동안의 알림은 사라지고 대조가 최대 60초 뒤 메운다.
- 런타임이 죽은 실행의 재실행 · 재시도 간격 · 실패 격리를 전부 직접 구현해야 한다.
- 동시 실행 제한은 런타임 상한 + 503 으로 둬야 한다.
- 런타임이 여러 대가 되면 "어느 런타임에 줄지"를 디스패처가 정해야 한다.

### 검토 후 제외

- **Kafka** — 대용량 이벤트 스트림 · 재생용 도구다. 이 규모에 과하고(브로커 메모리 GB 단위), 1 ~ 3분짜리 작업을 컨슈머에서 처리하려면 `max.poll.interval` · 재조정 · 커밋 순서를 다뤄야 한다. 작업 분배 모양의 이 문제에는 RabbitMQ 가 맞다.
- **현행 유지(HTTP 202 + 2초 폴링)** — 10-15 의 보강으로 돈이 묶이는 구멍은 막혔지만, 위 「남아 있던 결합」 1 ~ 3 이 그대로다.

---

## Trade-off Analysis

| 기준 | RabbitMQ | DB 큐 + 콜백 + 대조 |
| --- | --- | --- |
| 새 인프라 · 단일 실패점 | 생김 | 없음 |
| 선차감과 원자적 접수 | ❌ outbox 필요 | ✅ 같은 트랜잭션 |
| 백엔드 다운 중 결과 | ✅ 큐에 남음 | 대조가 메움(≤ 60초) |
| 런타임이 보내기 직전 죽음 | 대조 필요 | 대조 필요 |
| 런타임 사망 후 재실행 | ✅ 재배달 | 직접 구현 |
| 동시 실행 제한 | ✅ prefetch | 런타임 상한 + 503 |
| 재시도 · 실패 격리 | ✅ DLX · TTL | 직접 구현 |
| 접수 시 동기 검증 | 사라짐 | 사라짐 |
| 구현량 | 중 | 소 |
| 런타임 확장 | ✅ 경쟁 컨슈머 | 라우팅 직접 |

**운영 비용만 보면 B 가 맞다.** 지금 규모에서 필요한 것은 B 로 다 되고, 어느 쪽이든 대조는 남는다(런타임 상태가 메모리라서).

**그래도 A 를 고른 이유.**
1. 재실행 · 동시 실행 제한 · 재시도 · 실패 격리를 **직접 만들지 않고 검증된 브로커 기능으로** 얻는다. B 로 가면 이것들을 디스패처에 하나씩 손으로 짜게 되고, 그 코드가 곧 작은 메시지 브로커가 된다.
2. 백엔드 다운 · 재배포 중 결과가 **지연 없이 보존된다.** 대조는 안전망으로만 남는다.
3. 런타임을 여러 대로 늘릴 때 구조를 다시 바꾸지 않아도 된다.
4. 프로젝트가 끝나 운영 부담이 작고, 메시지 기반 구조를 제대로 세워 보는 가치가 크다.

A 의 최대 약점인 이중 쓰기는 **B 의 장점(DB 를 outbox 로)을 A 에 그대로 가져와** 푼다 — 사용자 요청은 DB 에만 쓰고, 브로커로 넘기는 일은 디스패처가 한다. 결과적으로 A 는 "B 의 접수 방식 + 브로커의 전달 · 재시도 · 격리"다.

---

## Consequences

**쉬워지는 것**
- 런타임이 잠깐 죽거나 꽉 차도 실행 요청이 실패하지 않는다. 브로커가 죽어도 실행 요청은 202 를 받고 outbox 에 쌓인다.
- 진행 상황이 폴링 간격 없이 바로 화면에 간다. 2초 폴링(`ExecutionMonitor` · `ExecutionProgressScheduler`)은 지우고 `ExecutionTracker` + `ExecutionWatchScheduler` 로 바꿨다.
- 런타임이 실행 도중 죽어도 사용자는 결과를 받는다(재실행).
- 런타임 수평 확장(단, 규칙 1 의 중복 거르기는 런타임마다 따로다).
- 이벤트로 닫힌 실행은 정산 근거 원본을 완료 이벤트에서 바로 얻는다.

**어려워지는 것**
- 브로커 운영(계정 · 볼륨 · 헬스체크 · 디스크 경보). 브로커 장애 시 접수는 DB 에 쌓이고 결과는 감시로만 닫힌다.
- **사용자가 가장 오래 기다리는 경우는 약 20분이다** — 브로커로 못 넘긴 채 10분(발행 포기) 또는 큐에서 10분(TTL). 두 한도는 따로 걸리므로 넘긴 직후 큐에 막히면 합쳐서 20분 가까이 된다.
- 노드 테스트는 요청 안에서 confirm 을 기다리므로, 브로커가 죽어 있으면 5초 안에 502 `EXECUTION_002` 를 받는다.
- 디버깅이 비동기로 흩어진다 — 큐 · DLQ · 메시지 추적(관리 UI)을 봐야 한다.
- 정의 오류가 접수 시점이 아니라 실행 화면에서 보인다.
- `(attempt, seq)` 필터와 감시 목록은 백엔드 메모리에만 있다. 재기동하면 복구 스케줄러가 감시를 다시 붙이지만 필터는 비어서 시작한다.
- 이벤트 처리 재시도(1초 × 3)는 리스너 스레드에서 돌아, 그동안 단일 활성 컨슈머인 이벤트 큐 전체가 멈춘다.
- 테스트: 진짜 브로커 E2E 에는 Docker 가 필요하다. 엔진 HTTP 모킹 테스트를 발행자 모킹으로 다시 썼다.
- 백엔드와 런타임이 볼륨(`file-store` · `results`)을 공유해 배포가 묶인다.
- HTTP 가 완전히 사라지지는 않는다. 감시의 상태 조회 · 노드 테스트 기한 초과 시 결과물 수확 · 감시가 닫은 실행의 근거 기록이 HTTP 를 쓴다. 런타임의 `/artifact` · `/html` · `/pdf` · `/cancel` 도 남아 있다.

**다시 볼 것**
- 백엔드를 여러 대로 늘리면 SSE · 진행 상황 저장소가 인스턴스마다 따로라, 진행 이벤트는 인스턴스별 fanout 으로, 완료 처리는 작업 큐로 갈라야 한다.
- 런타임 상태 외부화(재배포 중 실행 보존 · 런타임 간 중복 거르기) — 큐로도 풀리지 않는 남은 한계.
- 브로커 단일 노드 → 필요해지면 quorum 큐 클러스터(3노드).
- 감시 점검(10초) · 감시 기한 · 발행 포기(10분) · 큐 대기 한도(10분) · 실행 횟수(최대 2번) 실측 후 조정.

---

## 세부 결정

결정할 때 남긴 다섯 가지는 모두 권장안대로 구현했다.

| # | 결정 | 확정 | 버린 대안 |
| --- | --- | --- | --- |
| 1 | 파일 전달 | 공유 볼륨(claim check) | 메시지에 base64(16MB 한도) · 런타임이 백엔드에서 HTTP 로 받기 |
| 2 | 런타임 사망 시 | 자동 재실행 1회(`x-delivery-limit=1` — 배달 최대 2번) | 재실행 없이 환불 |
| 3 | HTTP 접수 경로 | 완전 교체(`POST /v1/runs` 제거) | 설정 스위치로 HTTP · 큐 병행 |
| 4 | 큐 대기 한도 | 10분 후 환불 | 다른 값 |
| 5 | 정의 오류 노출 | 실행 화면의 실패(`rejected`)로 보임. 저장 시 검증으로 대부분 걸러짐 | 접수 전에 백엔드가 `POST /v1/workflows/validate` 를 동기로 한 번 부름 |

---

## Action Items

순서대로 처리한다. 각 단계는 앞 단계 위에 선다.

**1. 인프라**
1. [x] `backend/ai-block-clock/compose.yaml` 에 `rabbitmq:4.3.6-management`(데이터 볼륨 · 헬스체크 · 계정 환경 변수) 추가
2. [x] 런타임 compose 에 외부 볼륨 `file-store`(읽기 전용) · `results` 마운트, RabbitMQ 접속 정보 주입
3. [x] 토폴로지 선언 — 양쪽이 같은 인자로 멱등 선언(`abc.runs` · `abc.runs.submit` · `abc.runs.dlx` · `abc.runs.dead` · `abc.control` · `abc.events` · `abc.events.backend` · `abc.events.dlx` · `abc.events.backend.dlq`)

**2. 런타임 (Python)**
4. [x] `aio-pika` 추가, FastAPI lifespan 에서 연결 · 컨슈머 시작(prefetch = 동시 실행 상한)
5. [x] SubmitRun 처리 — `execution_id` 중복 거르기, 공유 볼륨 경로 검증, 실행을 스레드로 넘기고 완료 이벤트 confirm 뒤 ack
6. [x] `on_event` 훅에서 RunEvent 발행(`attempt` · `seq` · 스냅샷), 완료 이벤트에 노드 출력 · 봉투 · 결과물 경로
7. [x] 정의 · 입력 오류 → `rejected` 이벤트, 취소 fanout 컨슈머
8. [x] 결과 PDF · HTML 을 `results/{execution_id}/` 에 쓰기
9. [x] 메시지 처리 로직을 전송 계층과 분리해 가짜 채널로 테스트

**3. 백엔드 (Spring)**
10. [x] `client:ai` 에 `spring-boot-starter-amqp`, `RunQueuePublisher`(publisher confirm), 메시지 DTO
11. [x] `ExecutionDispatcher` + `execution_outbox`(V9) — 아직 넘기지 않은 실행을 publish, confirm 뒤 `run_id` 기록, 10분 넘으면 포기 · 환불. `ExecutionService.start` 에서 엔진 호출 제거
12. [x] `RunEventListener`(`@RabbitListener`, 커밋 뒤 ack, 재시도 3번 후 DLQ) — 스냅샷 → 진행 상황 · SSE, 완료 → `finish` · 결과물 옮기기 · 근거
13. [x] `abc.runs.dead` 컨슈머 — 사유별 실패 코드로 플랫폼 탓으로 닫고 환불
14. [x] `ExecutionMonitor` · `ExecutionProgressScheduler` 삭제, `ExecutionTracker` + `ExecutionWatchScheduler`(이벤트 적용 + 감시)로 교체. 2초 폴링 제거
15. [x] 노드 테스트 실행을 이벤트로 닫기
16. [x] 취소를 `abc.control` 발행으로

**4. 테스트 · 정리**
17. [x] 단위 테스트: 디스패처 · 이벤트 적용 · DLQ 처리 · 감시 · `seq` 역전
18. [x] 기존 컨트롤러 · 통합 테스트를 발행자 모킹으로 재작성
19. [ ] Docker 위 진짜 RabbitMQ E2E (`e2e/tests/runtime`, `E2E_RUNTIME=1`) — **일부 완료.** 2026-10-21 최신 실행 7/7 통과

    | 시나리오 | 상태 |
    | --- | --- |
    | 접수 → 대기열 → 진행 → PDF · JSON 결과 → 예치 반환 | ✅ |
    | 런타임 사망 → attempt 2 로 재실행해 끝남 | ✅ |
    | 런타임이 내려간 동안 접수 → 뜨면 이어서 끝남 | ✅ |
    | 브로커 멈춤 중 접수 → 다시 넘겨 한 번만 정산 | ✅ |
    | 브로커 멈춤 중 노드 테스트 → 5초 안에 거절 | ✅ |
    | 노드 테스트(`mode=test`) | ✅ |
    | 큐 만료 → `ABC_QUEUE_EXPIRED` 환불 | ✅ 단, TTL 을 기다리지 않고 메시지에 `expired` 사유를 붙여 DLX 로 넣어 흉내 낸다 |
    | 백엔드 재기동 중 완료 | ❌ 없음 — Playwright 가 띄운 백엔드는 도중에 내렸다 올릴 수 없고 H2 가 인메모리다 |
    | 런타임이 두 번 죽음 → `ABC_RUNTIME_RETRY_EXHAUSTED` | ❌ 없음 |
    | 실제 TTL 10분 만료 | ❌ 없음 |

    10-16 실행에서 나온 버그 둘(dead letter 로 닫힌 실행을 다시 읽으면 사유가 `ABC_RUNTIME_UNREPORTED` 로 바뀜 · 브로커가 멈추면 채널 열기에서 발행이 묶임)은 고쳤다.
20. [x] `ai/docs/integration-guide.md` §4 · §7 · §9 · §12 를 새 계약으로 갱신, 런타임 `MAX_ACTIVE_RUNS` 503 경로 제거

**5. 구현과 대조하며 나온 후속 (2026-10-21)**
21. [ ] **선차감이 묶일 수 있는 구멍.** `abc.runs.dead` 처리가 재시도까지 모두 실패하면 메시지가 사라진다(dead 큐에 DLX 가 없다). 그 실행은 `QUEUED` 로 남고 감시 · 복구 대상이 아니다 → dead 큐에도 DLX 를 달거나, 오래된 `QUEUED` 를 복구 대상에 넣는다
22. [ ] **compose 기동 조건이 원칙과 어긋난다.** 백엔드가 `rabbitmq: service_healthy` 를 기다려, 브로커가 없으면 백엔드가 뜨지 않는다. "브로커가 죽어도 접수는 outbox 에 쌓인다"(구현 메모)와 맞추려면 `service_started` 로 낮출지 정한다
23. [ ] `nack(requeue=true)` 가 delivery-limit 에 세어지는지 실측한다 — 구현 메모와 `e2e/tests/support/broker.ts` 주석이 반대로 적혀 있다
24. [ ] 진짜 브로커 pytest(`-m broker`) 5개의 통과 기록을 남긴다. 지금은 RED 단계 기록뿐이다
25. [ ] 테스트 공백: 같은 배달 안에서 `seq` 이어 세기, 마지막 이벤트 발행 실패 → 1초 뒤 nack
26. [ ] `ai/docs/integration-guide.md` 의 "실제 브로커 E2E 전" 문구를 갱신한다
27. [ ] 실패 판정이 없는 옛 실행을 조회할 때마다 "엔진이 끝난 실행을 잊었다" 로그가 찍힌다 — 한 번만 남기거나 판정을 채운다

---

## 구현 메모

결정은 그대로다. 메시지 계약(토폴로지 · 칸 · ack 규칙)은 [`ai/docs/integration-guide.md`](../../ai/docs/integration-guide.md) §4 에 있다. 구현하며 더 정한 것만 적는다.

**양쪽 공통**
- **백엔드 `/actuator/health` 에는 RabbitMQ 를 넣지 않는다.** 브로커가 죽어도 접수는 outbox 에 쌓이므로 백엔드는 UP 이다. 브로커는 자기 컨테이너 헬스체크로 본다. 런타임 `/v1/health` 는 `broker`(`connected` · `disconnected`)를 더했다.
- 새 실패 코드(`ABC_QUEUE_EXPIRED` · `ABC_RUNTIME_RETRY_EXHAUSTED` · `ABC_RUNTIME_DEAD_LETTERED` · `ABC_RUNTIME_UNREACHABLE` · `ABC_RUNTIME_STALLED`)는 사용자 응답의 `ErrorCode` 가 아니라 실행 실패 사유 문자열이다.
- 운영 compose 는 5672 를 밖으로 열지 않고 관리 UI 는 루프백에만 묶는다(`backend/ai-block-clock/compose.prod.yaml`). CI 배포 잡이 볼륨과 RabbitMQ 자격 증명이 있는지 먼저 검사한다.

**백엔드**
- 디스패처는 커밋 직후 비동기 발행(스레드 2, 대기열 1000)과 5초 주기 재발행을 함께 쓴다. 한 번에 보내는 개수 한도는 없다.
- confirm 기한과 채널 RPC · 연결 기한을 모두 5초로 맞췄다. 라이브러리 기본값(채널 RPC 10분 · 연결 60초)이면 브로커가 연결만 받고 답하지 않을 때 발행이 그만큼 묶인다.

**런타임**
- **control 큐 이름은 런타임(aio-pika)이 짓는다**(`amq_<hex>`). 브로커가 지으면 연결이 끊겼다 다시 붙을 때 같은 이름으로 되살릴 수 없다. exclusive · auto-delete 는 그대로다.
- control 컨슈머는 `no_ack` 이고 fanout 이라, **런타임이 내려간 동안 보낸 취소는 사라진다**(코드에서 추론). 취소해도 지금 부르는 LLM 호출은 끝까지 가고, 실행은 `CANCELLED`(플랫폼 탓)로 끝난다.
- **같은 배달 안에서 마지막 이벤트를 다시 낼 때 `seq` 는 그 배달에서 이어 센다.** 1 로 되돌리면 백엔드의 `(attempt, seq)` 필터가 버린다. 새 배달(attempt 가 오름)이면 1 부터다.
- **런타임은 마지막 이벤트 발행에 실패하면 1초 쉬고 `nack(requeue=true)` 한다.** 이 nack 이 delivery-limit 에 세어지지 않는다고 보고 설계했지만 **실측하지 않았다**(후속 23). 발행이 계속 실패하면 TTL 10분 뒤 dead letter(`expired`) → 환불이다.
- 읽을 수 없는 메시지는 `reject(requeue=false)` 로 dead letter(사유 `rejected`)가 된다.
- `BROKER_ENABLED=false` 면 브로커에 붙지 않는다. 브로커가 없어도 기동을 막지 않고 5초마다 다시 붙는다.
- 런타임은 백엔드 쪽 큐(`abc.events.backend` · `abc.events.dlx` 등)까지 토폴로지 전체를 선언한다.
- 결과 볼륨에 쓰지 못하면 `failed`(`RUNTIME_INTERNAL`)로 끝낸다. 24시간이 지난 `results/{id}` 와 메모리 기록은 지운다.
- 이미 도는 실행이 다시 배달되면, 그 배달은 끝나기를 기다리는 동안 prefetch 자리 하나와 스레드 하나를 차지한다.

---

## 변경 이력

| 날짜 | 내용 |
| --- | --- |
| 2026-10-16 | 결정 (Accepted) |
| 2026-10-16 | 구현 후 할 일 체크 · 구현 메모 추가 |
| 2026-10-21 | 구현 코드 · E2E 와 대조해 갱신. 결정은 그대로이고 사실관계만 고쳤다 — ① 10-15 보강 중 셋이 바뀐 것을 표에 적음 ② 토폴로지에 빠진 `abc.events.dlx` 추가, 이벤트 처리 "3회" → "4번(처음 + 재시도 3)" ③ outbox 를 별도 테이블 · 발행 포기 10분으로 구체화, 런타임 중복 거르기가 메모리 기준임을 명시 ④ 감시 기한이 마지막 이벤트 기준이고 엔진 답에 따라 갈리는 것을 표로 ⑤ dead letter 사유별 실패 코드 ⑥ 결과 볼륨에서 백엔드가 읽는 파일(`artifact.json` · `report.pdf`) 정정 ⑦ #19 를 시나리오별 현황으로 ⑧ 대조에서 찾은 후속 21 ~ 27 ⑨ nack 메모를 "실측 안 됨" 으로 |
