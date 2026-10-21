# AI Runtime 연동 계약 — 백엔드 · 프론트용

| 항목 | 내용 |
| --- | --- |
| 상태 | **v2.3** (2026-10-16 — **실행 접수 · 진행 · 완료 · 취소를 RabbitMQ 메시지로**(§4), `POST /v1/runs` · `ENGINE_BUSY` 폐지. v2.2 09-27: 서식(HTML 산출물 · 사용자 서식 · 디자인 → 서식 변환), 사용자 파일 확장. v2.1 09-26: 견본 삭제 · 출력 JSON 예시. v2.0 09-25: 입력 형식 폐지 · ABC 제거 · `mode=test`). 이 파일이 항상 최신 계약이다 |
| 이전 | 확정본 09-19 · 09-22 갱신(검증 응답의 견적, 노드 필드 8종, 결과 URL, 정의 검증 코드, 원가 산출) |
| 작성 | AI 파트 트랙 A (김정윤) |
| 대상 | **백엔드(Spring), 프론트엔드(React)** |
| 범위 | 실행 엔진(8000). 추천 서비스(8100)는 [`recommend-api.md`](recommend-api.md), 등급 수식은 [`grading-formula.md`](grading-formula.md) |

> **이 문서 하나만 보면 된다.** 필드 정의 · 엔드포인트 · 정산 · 오류 · 함정이 전부 여기 있다.
> 서비스 전체 흐름은 [`../../docs/service-flow.md`](../../docs/service-flow.md).

### v2.3에서 바뀐 것 (10-16) — 먼저 읽을 것

결정과 근거는 [`docs/adr/0001-execution-messaging-rabbitmq.md`](../../docs/adr/0001-execution-messaging-rabbitmq.md). 메시지 계약은 §4.

| 바뀐 것 | v2.2 | v2.3 (지금) | 왜 |
| --- | --- | --- | --- |
| **실행 접수** | `POST /v1/runs`(multipart) → 202 `run_id` | 백엔드가 **SubmitRun** 메시지를 `abc.runs` 에 발행한다. `run_id` = `execution_id`(백엔드 실행 번호). `POST /v1/runs` 는 없다 | 접수가 런타임 가용성에 묶이지 않게 |
| **진행 · 결과** | 2초마다 `GET /v1/runs/{id}` | 런타임이 **RunEvent**(`accepted` · `progress` · `succeeded` · `failed` · `rejected`)를 `abc.events` 로 낸다. HTTP 조회는 대조 · 근거 백필에만 | 폴링 지연 · 조회 반복을 없앤다 |
| **파일** | 폼 파트 `file` · `ref_file_<node_id>_<n>` | 공유 볼륨 경로(`files` · `ref_files`, file-store 읽기 전용). 결과물은 `results` 볼륨 | 메시지에는 경로만 싣는다(claim check) |
| **동시 실행 상한** | `MAX_ACTIVE_RUNS` 를 넘으면 503 `ENGINE_BUSY` | prefetch. 넘치는 실행은 큐에서 기다리고, 10분을 넘기면 dead letter → 환불 | 사용자가 "잠시 후" 를 받지 않는다 |
| **정의 · 입력 오류** | 접수 응답 400 | 실행 전 **`rejected`** 이벤트(§9.1). 백엔드는 전액 환불 | 접수 시점에는 런타임이 없다 |
| 멱등 키 | `client_run_id` | 없다. `execution_id` 가 멱등 키다 | |
| `GET /v1/health` | `active_runs` · `max_active_runs` | 둘 다 없어지고 `broker` 가 생겼다 | |

### v2.2에서 바뀐 것 (09-27)

| 바뀐 것 | v2.1 | v2.2 (지금) | 왜 |
| --- | --- | --- | --- |
| **산출물** | JSON(`/artifact`) + PDF(`/pdf`, 기본 서식 하나) | **JSON + 서식을 입힌 HTML(`/html`) + 그 HTML의 PDF**. `result.html_url` · `result.template` 추가 | 화면에 바로 띄우고, 서식은 곧 HTML |
| **사용자 서식** | 없음(`template_id` 무시) | `output.template: {name, html}` — 백엔드가 보관한 Jinja HTML을 실행 요청에 실어 보낸다. 없으면 기본 서식(`document` · `card`) | 캔바 · 미리캔버스처럼 내 서식으로 결과 받기 |
| **자리표시자 ≠ 키** | — | 서식의 빈칸 이름과 결과 JSON 키가 같으면 AI 없이, 다르면 실행 끝에 **AI가 한 번 경로를 짝짓는다**(`messages.core` · `reasons.0.axis` · 반복 항목 속성까지. 값은 JSON에서 그대로). 못 채운 빈칸은 비워 두고 `warnings[]` | 판매자마다 키가 다르다 |
| **디자인 → 서식** | 없음 | **`POST /v1/templates/convert`** — PDF · 사진을 올리면 빈칸 뚫린 HTML 서식을 돌려준다(AI 1회 + 교정 1회). 백엔드가 보관 | 등록 때 한 번, 실행마다 재사용 |
| **`warnings[]`** | 없음 | 실행 응답 최상위. 서식 폴백 · 짝짓기 · PDF 실패처럼 "성공했지만 알릴 것" | 실행 실패로 만들지 않기 위해 |
| PDF 실패 | 실행 실패(`PDF_RENDER_FAILED`) | **실행 실패 아님** — `pdf_url: null` + 경고. 사용자 서식이 PDF 엔진을 넘으면 PDF만 기본 서식으로 | HTML이 원본이므로 |
| 사용자 파일 | `file` 하나(표만) | 여러 개 · 표 · 글 · PDF · 사진, 입력란 값과 함께, 모든 LLM 노드에 (§7.1) | |

### v2.1에서 바뀐 것 (09-26 저녁)

| 바뀐 것 | v2.0 (09-25) | v2.1 (지금) | 왜 |
| --- | --- | --- | --- |
| **JSON 출력의 모양** | 견본 11종 중 하나 `{kind: "json", sample: "biz.x@1"}` | 판매자가 **JSON 예시를 직접** `{kind: "json", example: {…}}` | 플랫폼이 정한 형식 목록 없이 판매자가 자유롭게. n8n "Generate from JSON example" 방식 |
| **사용자 입력 폼** | `input: {sample: "biz.brand_brief@1"}` | `input: {example: {…}}` — 예시의 키가 입력란 | 같음 |
| **분기 필드** | 견본에 선언된 스칼라 필드 | **앞 노드 출력 예시**의 문자 · 숫자 · 참거짓 필드. 문자열 비교는 대소문자 · 앞뒤 공백 무시 | 같음 |
| **선택 항목** | 견본의 `required` | 예시에서 값을 **`null`로 둔 키**는 선택, 나머지는 필수 | 예시 하나로 표현 |
| **선택지 · 개수 제약** | 견본이 강제(예: high/low만, 최대 5개) | **없다.** 프롬프트로 지시한다 | 예시로는 표현할 수 없다 |
| `GET /v1/schemas` | 견본 목록 | **빈 목록**(`deprecated: true`), `/{id}`는 404 | 부르고 있어도 화면이 깨지지 않게 한 버전 유지 |
| 봉투 `schema_id` | 견본 id | `"json"` 또는 `"text"` | 모양은 노드의 `example`에 있다 |
| PDF 제목 | 견본의 한국어 라벨 | **예시의 키 이름**(한글 키면 한글 제목) | |
| 호환 | — | 옛 `output.sample` · `output_schema_id` · `input.sample` · `input_schema_id`의 견본 id는 **한 버전 동안 그 견본의 예시로 바꿔 읽는다**(경고). `common.text@1`은 글 | 백엔드가 바꾸는 동안 실행이 끊기지 않게 |
| **사용자 파일** (09-27) | `file` 하나, 엑셀 · CSV만. 입력란을 채우면 버려짐 | **여러 개**(`file` 반복) · 표 · 글 · **PDF · 사진**. 입력란 값과 **함께** 가고, **모든 LLM 노드**가 본다 | 사진 · 문서를 던지면 블록들이 읽는다 |
| **판매자 참고 파일** (09-27) | pdf · txt · md · csv | **사진(png · jpg · webp) · 엑셀**도 | 같은 규칙 |

### v2.0에서 바뀐 것 (09-25)

| 바뀐 것 | 이전 (09-22) | 지금 | 왜 |
| --- | --- | --- | --- |
| **노드 연결** | 앞 노드 `output_schema_id` = 뒤 노드 `input_schema_id`일 때만 | **어떤 노드든 이어진다.** 뒤 노드는 앞 결과 전체를 자동으로 받는다 | 카테고리가 다른 판매자의 에이전트를 사용자가 자유롭게 엮게 하려고. Google A2A · n8n과 같은 방식 |
| **입력 형식** | `input_schema_id` 필수 검증 | **없다.** 진입 노드에만 사용자 입력 예시(선택. v2.1 `input.example`) | 위와 같음 |
| **출력 형식** | 카탈로그 11종 중 하나 필수 | `output: { kind: "text" }` 또는 JSON(v2.0 견본 → **v2.1 판매자 예시**) — **글 허용** | 판매자가 형식을 고르고, 구조 없는 노드도 마켓에 올릴 수 있게 |
| **분기** | 실행 때 경로가 없으면 실패 | 저장 시 **선언된 출력 필드**만 참조하도록 코드 검사 | 실행 전에 안다 |
| **비용** | `cost_abc` · `unit_price` · 견적 ABC | **토큰 · USD만.** `agents[]` 신설. Runtime은 ABC를 모른다 | 측정(Runtime)과 가격(백엔드)의 분리 |
| **테스트 실행** | 없음 | `POST /v1/runs`에 `mode=test` | 판매자 사전 테스트 · 원가 실측(NOD-007) |
| **조합 오류** | 실패 · 수요자 책임 환불 | **실패가 아니다.** 실행되고 품질은 평점으로 | 입력 검증이 없으니 "조합 오류"를 코드가 판정할 수 없다 |
| 호환 별칭 | — | `output_schema_id`는 한 버전 동안 `output.sample`로 읽는다(경고). `input_schema_id` · `unit_price` · `base_cost` · `premium_rate`는 무시(경고) | 백엔드 반영 중 실행이 끊기지 않게 |
| **참고 파일** (09-26) | 예정 | **동작.** 폼 필드 `ref_file_<node_id>_<n>` — txt · md · csv는 본문, PDF는 inline 첨부. 위반은 `REF_FILE_REJECTED` | §7.1 |
| **기본 모델** (09-26) | `gemini-2.5-flash` | **`gemini-2.5-pro`.** 노드당 15 ~ 35초, 원가 약 4배. 워크플로우 `model`로 Flash 선택 가능 | GMS 크레딧 여유 · §13 |
| **실패 노드 토큰** (09-26) | 교정 중 예외면 `null` | LLM을 부른 뒤 실패하면 **그때까지의 토큰 · USD**가 들어온다 | 백엔드 환불 계산 · §8.5 |

---

## 1. 3분 요약

**AI Runtime은 워크플로우 실행기다.** 노드와 연결선, 사용자 입력을 주면 순서대로 실행해 결과 JSON(과 PDF)을 만들고, 정산의 **근거**(누가 성공했고 무엇이 실행되지 않았고 토큰을 얼마나 썼나)를 돌려준다. **금액은 계산하지 않는다.**

| 질문 | 답 |
| --- | --- |
| 어디 있나 | EC2 안 도커 컨테이너. `http://ai-block-clock-ai-runtime:8000` |
| 누가 부르나 | **백엔드만.** 프론트는 AI Runtime을 직접 부르지 않는다 |
| 인증 | 헤더 `X-ABC-Service-Token`. 없으면 401 |
| 외부 접근 | **안 된다.** 인터넷에 열려 있지 않고 nginx 경로도 없다 |
| 호출 횟수 | **실행 1건에 메시지 1개(SubmitRun).** 노드마다 왕복하지 않는다 |
| 응답 방식 | 메시지. 백엔드가 SubmitRun 을 큐에 넣고, Runtime 이 진행 · 완료를 RunEvent 로 알린다 (§4) |
| 큐를 쓰나 | **쓴다(10-16).** RabbitMQ — 실행 접수 · 진행 · 완료 · 취소만. 검증 · 서식 변환 · 조회는 HTTP (§4) |
| 걸리는 시간 | 노드 1개당 약 15 ~ 35초 (`gemini-2.5-pro`, 09-26 기본). Flash면 10 ~ 20초 |
| 상태를 저장하나 | **아니다.** 재시작하면 `run_id` 조회가 404. 이력 원본은 백엔드 DB. ack 전이던 실행은 큐가 한 번 더 배달한다 |
| 돈을 아나 | **모른다.** 토큰과 USD 원가까지만. ABC · 단가 · 프리미엄 · 정산은 백엔드 |

### 구현 일정 — 무엇을 언제 붙일 수 있나

| 기능 | 상태 |
| --- | --- |
| PDF · `artifact` · `edges` · 분기 · 병합 · 에이전트 전개 · `validate` | **동작 중** (v1.x 형식) |
| v2.0 — 입력 폐지 · 별칭 · `cost_usd` · `agents[]` · `mode=test` · 분기 참조 검사 | **동작 중** (09-26 master) |
| **v2.1 — 견본 삭제 · `output {kind, example}` · `input {example}`** | **09-26 저녁 develop-AI** |
| 노드별 참고 파일 `ref_files` | **동작 중** (09-26) — txt · md · csv는 본문 절, PDF는 inline 첨부 |
| 서식 — 기본 2종 · 사용자 서식 `output.template` · `GET /v1/runs/{id}/html` · `POST /v1/templates/convert` | **동작 중** (09-27) |
| **v2.3 — RabbitMQ 접수 · 이벤트 · 취소 · 공유 볼륨** (`POST /v1/runs` · 2초 폴링 · `ENGINE_BUSY` 폐지) | **구현 완료** (10-16, 운영 배포 전 · 실제 브로커 E2E 전) |

---

## 2. 접속 정보

| 항목 | 값 |
| --- | --- |
| 주소 (백엔드 컨테이너 기준) | `http://ai-block-clock-ai-runtime:8000` |
| 주소 (EC2 호스트에서 디버깅) | `http://127.0.0.1:8000` |
| 실행 메시지 | RabbitMQ `ai-block-clock-rabbitmq:5672` (백엔드 compose 가 띄운다). 접속 변수는 §4.10 |
| 인증 헤더 | `X-ABC-Service-Token: <ABC_SERVICE_TOKEN>` (HTTP 만) |
| 토큰 값 | GitLab → Settings → CI/CD → Variables 의 `ABC_SERVICE_TOKEN` |
| 헬스체크 | `GET /v1/health` (인증 불필요) |
| Swagger | `http://127.0.0.1:8000/docs` (EC2 안에서만) |
| 인코딩 | 요청 · 응답 모두 UTF-8 |

백엔드 컨테이너에 환경변수 `ABC_SERVICE_TOKEN`을 추가한다. **GitLab Variables의 값과 글자 하나까지 같아야 한다.** 다르면 모든 호출이 401이다.

```yaml
# backend compose.yaml
environment:
  ABC_SERVICE_TOKEN: ${ABC_SERVICE_TOKEN:?ABC_SERVICE_TOKEN 이 필요하다}
```

연결 확인은 백엔드 컨테이너 안에서 한다.

```bash
curl -s http://ai-block-clock-ai-runtime:8000/v1/health
# {"status":"ok","broker":"connected","auth_required":true,"runs_in_memory":0}
```

`broker` 가 `disconnected` 면 Runtime 이 실행 메시지를 받지 못한다. Runtime 은 브로커가 없어도 뜨고, 5초마다 다시 붙는다.

---

## 3. 서비스 전체에서 AI를 부르는 지점

**세 곳뿐이다.** 나머지는 백엔드와 프론트만으로 끝난다.

| | 언제 | 어느 서비스 | 엔드포인트 |
| --- | --- | --- | --- |
| ① 인덱싱 | 에이전트가 마켓에 공개될 때 | 추천 (8100) | `POST /v1/index/agents` |
| ② 추천 | 사용자가 랜딩에 자연어를 입력할 때 | 추천 (8100) | `POST /v1/recommend` |
| **③ 실행** | 사용자가 실행 버튼을 누를 때 · **판매자가 테스트 실행을 누를 때** | **실행 엔진 (8000)** | **SubmitRun 메시지** (`mode=run` · `mode=test`, §4) |

**이 문서는 ③만 다룬다.** ①②는 [`recommend-api.md`](recommend-api.md)에 있고, 서로 다른 컨테이너라 독립적이다. 추천을 붙이지 않아도 실행 파이프라인은 완전히 동작한다.

### AI를 부르지 않는 지점 — 헷갈리기 쉬운 것

| 항목 | 실제 담당 | 이유 |
| --- | --- | --- |
| 프롬프트 저장 · 조회 | 백엔드 DB | Runtime은 실행 시점에 메시지로 받는다 |
| 노드 소유권 검증 (버전 확정 가능 여부) | 백엔드 | 계정과 소유권 정보는 백엔드에 있다 |
| **실행 단가 · 견적 금액 · ABC 환산 · 등급** | **백엔드** | Runtime은 토큰 · USD · "어느 노드가 도는가"까지만 준다 |
| ABC 차감 · 적립 · 환원 | 백엔드 | 장부는 전부 백엔드 몫이다 |
| 실행 이력 저장 | 백엔드 DB | Runtime은 메모리에만 두고 재시작하면 잊는다 |
| 결과물 보관 · 다운로드 URL 발급 | 백엔드 스토리지 | Runtime은 공유 `results` 볼륨에 써 두기만 한다(§4.5). 보관 기한이 지나면 지운다 |
| 마켓 키워드 검색 · 필터 | 백엔드 DB | 추천(②)과 별개 기능이다 |
| 테스트 실행 횟수 · 크레딧 한도 | 백엔드 접수 단계 | Runtime은 제한하지 않는다 |

---

## 4. 전체 흐름 — 실행은 메시지로 (v2.3)

```
 사용자 · 프론트        백엔드                          RabbitMQ                          AI Runtime
  ① [실행] ─────────▶ ② 선차감 · QUEUED · outbox (한 트랜잭션)
  ◀───── 202 ────────┤
                     ③ 디스패처 ── SubmitRun ──▶ abc.runs.submit ── prefetch ──▶ ④ 검사 → accepted
                        confirm 뒤 run_id 기록                                    ⑤ 노드 실행 ◀──▶ Gemini
  ◀── SSE ── ⑥ 진행 중계 ◀──────── abc.events.backend ◀── accepted · progress ───┤
                                                                                 ⑦ results/<id>/ 에 결과물
             ⑧ 닫기 · 정산 · 환불 ◀── abc.events.backend ◀── succeeded · failed · rejected
  ◀── 결과 URL ── ⑨ results/<id>/ 의 결과물을 옮겨 보관
```

| 단계 | 하는 일 |
| --- | --- |
| ① ~ ② 접수 | 백엔드는 선차감 · `QUEUED` · outbox(보낼 SubmitRun)를 **한 트랜잭션**으로 쓰고 202 를 준다. 엔진도 브로커도 부르지 않는다 — 브로커가 죽어 있어도 접수는 된다 |
| ③ 넘기기 | 디스패처가 커밋 직후 한 번, 그 뒤 5초마다 넘기지 않은 실행을 발행한다. publisher confirm 을 받으면 `run_id`(= 실행 번호)를 적는다. 10분이 지나도록 넘기지 못하면 버리고(`ABC_RUNTIME_UNREACHABLE`, platform) 전액 돌려준다 |
| ④ ~ ⑤ 실행 | Runtime 은 큐에서 꺼내 검사하고(걸리면 `rejected` 하나로 끝난다) `accepted` 를 낸 뒤 돌린다. 동시에 도는 실행 수는 prefetch(`MAX_ACTIVE_RUNS`, 기본 4)다 |
| ⑥ 진행 | 실행 시작 · 노드 시작 · 성공 · 실패마다 `progress` 가 온다. 백엔드는 snapshot 으로 진행 상황을 만들어 SSE 로 중계한다 |
| ⑦ ~ ⑨ 끝 | Runtime 은 결과물을 결과 볼륨에 다 쓴 뒤 `succeeded` 를 낸다. 백엔드는 기존 멱등 닫기로 정산 · 환불하고, 볼륨의 결과물을 자기 스토리지로 옮긴다 |

**상태는 이벤트 `type` 으로 정한다** — `accepted` · `progress` → RUNNING, `succeeded` → SUCCEEDED, `failed` · `rejected` → FAILED. snapshot 은 진행률 · 실패 사유 · 정산 근거로 읽는다.

**감시가 안전망이다.** `accepted` 뒤 마지막 이벤트에서 기한(단계 수 × 60초 + 60초)이 지나도록 이벤트가 없으면 백엔드가 `GET /v1/runs/{id}` 로 한 번 묻는다(§4.9). `accepted` 전의 대기는 묻지 않는다 — TTL 과 dead letter 가 맡는다.

### 왜 메시지 큐인가 — 10-16 결정

> 09-19 에 "HTTP 202 + 폴링, 큐는 쓰지 않는다" 로 정했고, 10-15 에 `client_run_id` · 동시 실행 상한 · 복구 스케줄러로 보강했다. 10-16 에 RabbitMQ 로 옮겼고 구현이 끝났다(운영 배포 전). 그때의 판단과 버린 대안(DB 큐 + 콜백 + 대조)은 [`docs/adr/0001-execution-messaging-rabbitmq.md`](../../docs/adr/0001-execution-messaging-rabbitmq.md) 에 남겼다.

동기로 기다리지 않는 것은 그대로다. 노드가 15 ~ 35초라 몇 개만 이어도 nginx · 로드밸런서 타임아웃을 넘긴다. 바뀐 것은 **접수와 결과 전달의 길**이다.

| 202 + 폴링에 남아 있던 것 | 큐로 바뀐 것 |
| --- | --- |
| 실행 요청이 성공하려면 그 순간 Runtime 이 살아 있고 자리가 있어야 했다(502 · 503 `ENGINE_BUSY`) | 접수는 DB 에만 쓴다. Runtime 이 죽거나 꽉 차도 실행은 큐에서 기다린다 |
| 진행이 최대 2초 늦고, 실행 수만큼 조회가 반복됐다 | 진행이 이벤트로 바로 온다. 조회는 이벤트가 끊긴 실행만 |
| Runtime 이 실행 도중 죽으면 환불(`ABC_RUNTIME_LOST`)로 끝났다 | ack 전이면 다시 배달되어 **한 번 더 돈다.** 두 번째도 끝내지 못하면 dead letter → 환불 |
| 백엔드가 내려간 동안의 결과는 대조가 최대 60초 늦게 메웠다 | 이벤트가 큐에 남는다. 백엔드가 올라오면 그대로 받는다 |

**09-19 에 큐를 쓰지 않은 이유 셋은 이렇게 풀었다.**

1. **재배달로 두 번 돈다** — `execution_id` 가 멱등 키다. 이 프로세스에서 이미 끝났거나 도는 실행은 다시 돌리지 않는다. 다시 도는 것은 ack 전에 Runtime 이 죽었을 때 한 번뿐이고(LLM 비용이 한 번 더 든다), 정산 · 환불은 백엔드 닫기가 멱등이라 두 번 잡히지 않는다.
2. **접수 시점의 동기 검증이 사라진다** — 받아들였다. 정의 · 입력 · 파일 오류는 실행 전 `rejected` 이벤트로 실행 화면의 실패가 된다(§9.1). 정의 오류는 저장 시점 `validate`(§7.5)가 대부분 거른다. 선차감은 접수 때 그대로 하고, `rejected` 면 전액 돌려준다.
3. **얻는 것이 폴링 제거뿐이다** — 재배달 · 동시 실행 제한(prefetch) · 대기 한도(TTL) · 실패 격리(dead letter)를 브로커 기능으로 얻는다. 손으로 짜면 그 코드가 곧 작은 브로커가 된다.

**남는 구멍.** Runtime 의 실행 상태는 여전히 메모리라, 완료 이벤트를 보내기 직전에 죽는 경우는 어떤 전송으로도 막지 못한다. 그래서 감시(§4.9)가 남는다.

**결과물도 Runtime이 백엔드 스토리지에 직접 쓰지 않는다.** 공유 `results` 볼륨에 써 두면 백엔드가 읽어 자기 스토리지로 옮긴다(§4.5).

**메시지 계약은 아래 4.1 ~ 4.10 이다.** 백엔드(Spring AMQP)와 Runtime(aio-pika)이 이것 하나를 같이 본다. 코드 주석의 "contract N장" 은 §4.N 이다.

### 4.1 토폴로지

양쪽이 **둘 다 전부** 선언한다(멱등). 먼저 뜬 쪽이 만들고, 늦게 뜬 쪽은 같은 인자로 확인만 한다. 이름 · 인자가 한 글자라도 다르면 브로커가 `PRECONDITION_FAILED` 로 채널을 닫는다.

| 종류 | 이름 | 타입 / 인자 | 묶임 |
| --- | --- | --- | --- |
| exchange | `abc.runs` | direct, durable | |
| queue | `abc.runs.submit` | durable, `x-queue-type=quorum`, `x-message-ttl=600000`(10분), `x-delivery-limit=1`, `x-dead-letter-exchange=abc.runs.dlx` | `abc.runs` ← routing key `submit` |
| exchange | `abc.runs.dlx` | fanout, durable | |
| queue | `abc.runs.dead` | durable, `x-queue-type=quorum` | `abc.runs.dlx` |
| exchange | `abc.control` | fanout, durable | |
| queue | (Runtime 마다) `amq_<hex>` | exclusive, auto-delete | `abc.control` |
| exchange | `abc.events` | topic, durable | |
| queue | `abc.events.backend` | durable, `x-queue-type=quorum`, `x-single-active-consumer=true`, `x-dead-letter-exchange=abc.events.dlx` | `abc.events` ← `run.#` |
| exchange | `abc.events.dlx` | fanout, durable | |
| queue | `abc.events.backend.dlq` | durable, `x-queue-type=quorum` | `abc.events.dlx` |

- 숫자 인자는 정수(int/long)로 보낸다. 문자열 `"600000"` 은 다른 인자로 취급된다.
- `x-delivery-limit=1` 은 **처음 배달 + 재배달 1번 = 최대 두 번 실행**이다. 두 번째 배달마저 끝내지 못하면 `abc.runs.dlx` 로 간다. 무엇을 배달로 세는지는 §4.7.
- TTL 은 처음 큐에 들어간 시각부터 잰다. 재배달로 돌아온 메시지도 10분을 넘겼으면 곧바로 만료된다.
- **control 큐는 Runtime 만 선언하고, 이름도 Runtime(aio-pika)이 짓는다**(`amq_<hex>`). 브로커가 짓게 두면 연결이 끊겼다 다시 붙을 때 같은 이름으로 되살릴 수 없어서다. exclusive · auto-delete 라 Runtime 이 내려가면 함께 사라진다.
- `abc.events.backend` 는 단일 활성 컨슈머라 이벤트가 보낸 순서대로 온다.

### 4.2 공통 메시지 속성

- 본문은 UTF-8 JSON. `content_type=application/json`, `delivery_mode=2`(persistent)
- `type` 속성: `SubmitRun` · `RunEvent` · `CancelRun`
- `message_id`: SubmitRun 은 `execution_id`, RunEvent 는 `<execution_id>:<attempt>:<seq>`, CancelRun 은 `cancel:<execution_id>`
- 시각은 ISO-8601 UTC 문자열(`2026-10-16T05:00:00Z`). 칸 이름은 snake_case
- 받는 쪽은 모르는 칸을 무시한다

### 4.3 SubmitRun — 백엔드 → `abc.runs` / `submit`

```jsonc
{
  "execution_id": "6f1c2a9e-0b7d-4c1e-9a53-2f8e1d4c7b10",  // 멱등 키 = Runtime 의 run_id. ^[A-Za-z0-9-]{1,64}$
  "mode": "run",                                         // "run" | "test"
  "workflow": { /* §5 워크플로우 객체. 문자열이 아니라 객체 */ },
  "input": { "brand_name": "초록소반", "category": "간편식" },  // 진입 노드 입력. 없으면 null
  "comment": "…",                                        // 없으면 null
  "mock": false,
  "files": ["executions/6f1c2a9e-…/input.pdf"],           // 사용자 파일. 없으면 []
  "ref_files": { "n1": ["nodes/<nodeId>/<refId>.pdf"] },  // 노드별 참고 자료. 없으면 {}
  "submitted_at": "2026-10-16T05:00:00Z"
}
```

| 칸 | 타입 | 설명 |
| --- | --- | --- |
| `execution_id` | string | 실행(`mode=run`)은 백엔드 실행 번호, 판매자 테스트(`mode=test`)는 테스트 실행 번호. 둘 다 UUID 라 겹치지 않는다. **멱등 키이자 Runtime 의 `run_id`** |
| `mode` | string | **`run` 또는 `test`**(대소문자 구분). `test`는 판매자 사전 테스트 — 실행 경로 · 토큰 · USD · 결과물 전부 같고 **`contribution`만 비어 온다.** 횟수 · 크레딧 제한은 백엔드가 건다 |
| `workflow` | object | §5의 워크플로우 정의 |
| `input` | object · null | 진입 노드의 사용자 입력(폼에서 받은 값). 진입 노드에 `input.example`이 있으면 그 모양인지 검사한다(빠진 필수 키 · 다른 타입 → 수요자 책임) |
| `comment` | string · null | 사용자가 입력한 자연어 코멘트 |
| `mock` | boolean | `true`면 LLM을 호출하지 않는다. `mode`와 독립(형식 확인용 조합 가능) |
| `files` | string[] | 사용자가 실행할 때 올린 파일. **표**(`xlsx · xls · csv`) → 마크다운 표, **글**(`txt · md`) → 본문, **PDF · 사진**(`png · jpg · jpeg · webp`) → Gemini inline 첨부. 합계 10MB. **모든 LLM 노드**에 `## 올린 파일` 절(+첨부)로 간다 — 입력란 값과 함께 간다 |
| `ref_files` | { 노드 id: string[] } | **판매자가 노드에 붙여 둔 참고 자료.** 형식 규칙은 `files`와 같다(표 · 글 · PDF · 사진), 노드당 합계 10MB, **그 노드에만** 간다. 전개된 에이전트 안 노드는 `a1.n1`처럼 접두 id로 |
| `submitted_at` | string | 백엔드가 SubmitRun 을 만든 시각 |

백엔드는 칸을 늘 싣는다 — 값이 없으면 `input` · `comment` 는 `null`, `files` 는 `[]`, `ref_files` 는 `{}`.

**파일은 경로로만 오간다(claim check).** `files` · `ref_files` 는 **file-store 볼륨 루트 기준 상대 경로**다. 백엔드가 이 볼륨에 쓰고, Runtime 은 같은 볼륨을 읽기 전용으로 마운트한다. 두 쪽의 마운트 위치는 달라도 된다(각자 `FILE_STORE_ROOT`). Runtime 은 파일을 복사하지 않고, 실행이 끝나도 지우지 않는다. 절대 경로 · `..` 가 든 경로(풀어서 안쪽이어도) · 마운트 밖으로 풀리는 경로 · **없거나 일반 파일이 아닌 경로**는 실행 전에 `rejected`(`FILE_PATH_REJECTED`, platform)다.

**참고 자료(`ref_files`)와 사용자 파일(`files`)은 다르다.** 참고 자료는 공급자가 노드를 만들 때 붙여 둔 채점 기준표 · 양식이고, 백엔드가 원본을 보관했다가 실행 때마다 함께 보낸다. 형식은 확장자로 판정한다 — `pdf · png · jpg · jpeg · webp · txt · md · csv · xlsx · xls`, **노드당 합계 10MB**. 어기면 그 노드가 `REF_FILE_REJECTED` · 공급자 책임으로 실패한다. 사용자 파일이 규칙을 어기면 실행 전에 `rejected`(`UNSUPPORTED_FILE_TYPE` · `FILE_TOO_LARGE`)이고, 실행 중 읽지 못하면 `FILE_PARSE_FAILED` · 수요자 책임이다.

Runtime은 `txt · md`를 본문 그대로, `xlsx · xls · csv`는 **마크다운 표**로 `## 참고 자료`(사용자 파일은 `## 올린 파일`) 절 아래 `### 파일명`으로 넣고, **PDF · 사진은 Gemini에 inline 첨부**로 함께 보낸다(본문에는 "PDF 첨부 NKB" · "사진 첨부 NKB"만 적는다). 파일 크기는 입력 상한(§5.4)에 포함된다 — 텍스트는 글자 수 그대로, PDF는 바이트 × 0.25자, 사진은 장당 1,000자로 추정. 프롬프트에서 참고 자료를 가리키려면 "참고 자료의 체크리스트를 기준으로"처럼 절 이름으로 부르면 된다.

### 4.4 RunEvent — Runtime → `abc.events` / `run.<type>`

```jsonc
{
  "execution_id": "6f1c2a9e-…",
  "mode": "run",                 // SubmitRun 의 mode 그대로. 백엔드가 실행 · 판매자 테스트로 가른다
  "attempt": 1,                  // 몇 번째 배달인가. x-delivery-count 헤더(없으면 0) + 1
  "seq": 3,                      // 같은 (execution_id, attempt) 안에서 1 부터 1씩 는다
  "type": "progress",            // accepted | progress | succeeded | failed | rejected
  "occurred_at": "2026-10-16T05:00:03Z",
  "snapshot": { /* GET /v1/runs/{id} 응답과 같은 모양(§7.2). succeeded · failed 는 ?include_node_output=true 모양 */ },
  "artifact": null,              // succeeded 에만. GET /v1/runs/{id}/artifact 의 봉투(§6.2)와 같은 객체
  "result_files": null           // succeeded 에만. 결과 볼륨 루트 기준 상대 경로(§4.5)
}
```

| type | 언제 | 백엔드가 보는 상태 |
| --- | --- | --- |
| `accepted` | Runtime 이 SubmitRun 을 검사하고 실행을 시작하기로 했을 때. 그 배달의 첫 이벤트(seq=1) | RUNNING |
| `progress` | 실행 시작, 노드 시작 · 성공 · 실패마다 | RUNNING |
| `succeeded` | 실행이 성공으로 끝났고 결과물 파일을 다 쓴 뒤 | SUCCEEDED |
| `failed` | 실행이 실패로 끝났을 때(취소 포함) | FAILED |
| `rejected` | 실행 전에 거절했을 때 — 정의 · 입력 · 경로 · 형식 오류(§9.1). `accepted` 없이 이것 하나만 온다 | FAILED |

- 백엔드는 상태를 **`type` 으로 정한다.** snapshot 은 진행률 · 단계 · 실패 사유 · 정산 근거로 읽는다. 완료 이벤트의 snapshot 원문이 그대로 정산 근거로 남는다.
- `rejected` 의 snapshot 은 `status:"failed"`, `nodes:[]`, `error:{code, …}`(정의 오류면 `error.errors[]` 에 §7.5 코드), `failure:{node_id:null, error_code:<code>, fault:<§9.1 의 fault>, affects_success_rate:false}` 를 갖는다.
- **`seq` 는 같은 배달 안에서 이어 센다.** 발행에 실패한 것도 센다(confirm 만 못 받고 나갔을 수 있다). 같은 배달(attempt)에서 마지막 이벤트를 다시 낼 때도 그 배달의 seq 에 이어 붙인다 — 1 로 되돌리면 아래 필터가 버린다. 새 배달(attempt 가 오름)이면 1 부터다.
- 백엔드는 `(attempt, seq)` 가 이미 적용한 것보다 크지 않은 이벤트를 버린다(사전식 비교 — attempt 가 크면 seq 가 작아도 새것). 이미 끝난 실행 · 모르는 `execution_id` 의 이벤트는 아무것도 바꾸지 않고 ack 한다.

### 4.5 결과 볼륨

Runtime 은 `succeeded` 를 보내기 **전에** 결과 볼륨 루트(`RESULTS_ROOT`) 아래에 쓴다.

```
<RESULTS_ROOT>/<execution_id>/artifact.json   봉투(= artifact 칸과 같은 내용). 성공이면 항상
<RESULTS_ROOT>/<execution_id>/report.pdf      PDF 를 만들었을 때만
<RESULTS_ROOT>/<execution_id>/report.html     HTML 을 만들었을 때만
```

`result_files` 는 `{"json": "<id>/artifact.json", "pdf": "<id>/report.pdf", "html": "<id>/report.html"}` 에서 **있는 파일만** 싣는다.
백엔드는 이벤트의 경로를 믿지 않고 자기 실행 번호로 위 고정 위치를 읽는다(경로 조작 여지가 없다). 감시가 HTTP 응답으로 닫을 때도 같은 위치를 읽는다.
결과 볼륨에 쓰지 못하면 Runtime 은 `failed`(`RUNTIME_INTERNAL`, platform)로 끝낸다. 보관 기한(`RUN_RETENTION_HOURS`, 기본 24시간)이 지나면 Runtime 이 `<execution_id>/` 를 지운다.

### 4.6 CancelRun — 백엔드 → `abc.control` (fanout)

```json
{ "execution_id": "6f1c2a9e-…", "requested_at": "2026-10-16T05:10:00Z" }
```

Runtime 마다 자기 control 큐로 받는다. 돌고 있으면 다음 노드부터 돌지 않고 `failed`(`CANCELLED`, platform)로 끝낸다. 지금 부르는 LLM 호출은 끝까지 간다.
아직 받지 않은 실행이면 번호를 기억해 두었다가 그 SubmitRun 이 오면 **돌리지 않고 이벤트 없이 ack** 한다. 기억은 실행 보관 기한만큼이다.
백엔드는 감시가 실행을 끊을 때(STALLED)와 넘기지 못한 실행을 버릴 때 보낸다.

### 4.7 ack 규칙

| 누가 | 무엇을 | 언제 ack |
| --- | --- | --- |
| Runtime | SubmitRun | 마지막 이벤트(`succeeded` · `failed` · `rejected`)가 브로커에 **confirm 된 뒤**. 발행이 실패하면 **1초 쉬고** `nack(requeue=true)` — 다시 오면 기록해 둔 마지막 이벤트만 다시 낸다 |
| Runtime | SubmitRun — 같은 실행이 이 프로세스에서 이미 끝남 | 다시 돌리지 않고(모델을 부르지 않음) 마지막 이벤트를 이번 배달의 attempt 로 다시 발행(confirm)한 뒤 ack |
| Runtime | SubmitRun — 같은 실행이 이 프로세스에서 아직 도는 중 | 두 번째로 돌리지 않는다. 도는 실행이 끝날 때 새 배달도 함께 ack |
| Runtime | SubmitRun — 받기 전에 취소된 실행 | 돌리지 않고 이벤트 없이 ack |
| Runtime | SubmitRun — JSON 이 아니거나 `execution_id` 가 없거나 형식이 틀림 | 이벤트 없이 `reject(requeue=false)` → dead letter |
| 백엔드 | RunEvent | DB 커밋 뒤. 처리에 실패하면 **처음 1번 + 재시도 3번 = 4번** 처리한 뒤 거절 → `abc.events.backend.dlq`. 읽을 수 없는 메시지(JSON 이 아님 · `execution_id` · `type` · `attempt` · `seq` 없음)는 재시도 없이 곧바로 DLQ |
| 백엔드 | `abc.runs.dead` | 실행을 닫은 뒤. 본문이 SubmitRun 으로 읽히지 않으면 경고만 남기고 ack |

**무엇을 배달로 세나.** RabbitMQ 4.x 는 `nack(requeue=true)` 를 `x-delivery-limit` 에 세지 않는다. ack 없이 채널이 닫힐 때(= Runtime 프로세스가 죽을 때)만 센다. 그래서

- Runtime 이 실행 도중 죽으면 다시 배달되어(attempt 2, seq 1 부터) **처음부터 한 번 더 돈다.** 두 번째도 끝내지 못하면 dead letter(`delivery_limit`)다.
- 마지막 이벤트 발행만 계속 실패하면 한도가 아니라 **TTL(10분)** 에 걸려 dead letter(`expired`)로 가고 환불된다. nack 로 돌아온 배달은 attempt 가 그대로라 seq 를 이어 센다(§4.4).

`abc.events.backend.dlq` 는 사람이 확인한다. 그 실행은 감시(§4.9)가 닫는다.

### 4.8 dead letter → 실패 코드 (백엔드)

`abc.runs.dead` 메시지는 본문이 SubmitRun 그대로이고, 브로커가 `x-first-death-reason` 헤더를 붙인다.

| `x-first-death-reason` | 실패 코드 | 책임 |
| --- | --- | --- |
| `expired` — 큐에서 10분을 넘겨 기다렸다 | `ABC_QUEUE_EXPIRED` | platform |
| `delivery_limit` — 두 번 배달하고도 끝내지 못했다 | `ABC_RUNTIME_RETRY_EXHAUSTED` | platform |
| 그 밖 · 없음 (Runtime 이 읽지 못해 거절한 메시지 등) | `ABC_RUNTIME_DEAD_LETTERED` | platform |

어느 것이든 플랫폼 탓이라 사용자는 예치를 전액 돌려받는다(§8.5 `platform`). 이미 끝난 실행이면 아무것도 하지 않는다. 판매자 테스트(`mode=test`)도 같은 코드로 닫는다.

### 4.9 HTTP 에 남는 것 — 감시 · 대조 · 근거

`POST /v1/runs` 와 503 `ENGINE_BUSY` 는 없어졌다. 아래는 남는다.

| HTTP | 누가 쓰나 |
| --- | --- |
| `GET /v1/runs/{execution_id}` (`?include_node_output=true`) — `run_id` 가 곧 `execution_id` | 백엔드 감시의 대조 · 정산 근거 백필 |
| `GET /v1/runs/{id}/artifact` · `/html` · `/pdf` | 판매자 테스트의 기한 넘긴 대조 · 운영 |
| `POST /v1/runs/{id}/cancel` | 운영 도구. 백엔드는 CancelRun(§4.6)을 쓴다 |
| `GET /v1/health` · `GET /v1/workflows*` · `POST /v1/workflows/validate` · `POST /v1/templates/convert` | 그대로 |

**감시(백엔드).** RUNNING 실행마다 마지막 이벤트 시각을 들고, 10초마다 기한(단계 수 × `per-step` 60초 + `grace` 60초)이 지난 실행만 `GET /v1/runs/{id}` 로 한 번 묻는다.

| 엔진 답 | 백엔드 처리 |
| --- | --- |
| 끝났다(`succeeded` · `failed`) | 그 응답으로 닫고, 결과 볼륨에서 결과물을 옮긴다 |
| 아직 돌거나 기다린다 | CancelRun 을 걸고 `ABC_RUNTIME_STALLED`(platform)로 닫는다 |
| 404 `RUN_NOT_FOUND` · 닿지 않음 | **곧바로 닫지 않는다.** 넘긴 시각에서 큐 대기 한도(10분) + grace 가 지나기 전이면 기한을 다시 건다 — 재배달 · TTL · dead letter 가 닫을 몫이다. 지났으면 `RUN_NOT_FOUND` 는 `ABC_RUNTIME_LOST`, 닿지 않음은 `ABC_RUNTIME_STALLED`(취소도 건다)로 닫는다 |

`QUEUED`(아직 `accepted` 전) 실행은 묻지 않는다. 판매자 테스트는 판매자가 조회할 때 접수에서 기한(한 단계)이 지났으면 한 번 묻는다.

### 4.10 접속 설정 (환경 변수, 양쪽 같은 이름)

| 변수 | 기본값 | 쓰는 쪽 | 운영 (compose) |
| --- | --- | --- | --- |
| `RABBITMQ_HOST` | `localhost` | 둘 다 | `ai-block-clock-rabbitmq` |
| `RABBITMQ_PORT` | `5672` | 둘 다 | `5672` |
| `RABBITMQ_USERNAME` · `RABBITMQ_PASSWORD` | `guest` · `guest` | 둘 다 · 브로커 계정 | GitLab CI/CD Variables. **양쪽이 같은 값** |
| `RABBITMQ_VHOST` | `/` | 둘 다 | `/` |
| `FILE_STORE_ROOT` | 백엔드 `/var/lib/ai-block-clock/files`, Runtime `<프로젝트>/file-store` | 둘 다 | `/var/lib/ai-block-clock/files` (Runtime 은 읽기 전용) |
| `RESULTS_ROOT` | 백엔드 `/var/lib/ai-block-clock/results`, Runtime `<프로젝트>/results` | 둘 다 | `/var/lib/ai-block-clock/results` |
| `MAX_ACTIVE_RUNS` | `4` | Runtime | prefetch = 동시에 도는 실행 수 |
| `BROKER_ENABLED` | `true` | Runtime | `false` 면 브로커에 붙지 않는다(HTTP 조회만 쓰는 로컬 개발 · 테스트) |
| `AI_MESSAGING_CONFIRM_TIMEOUT` | `5s` | 백엔드 | 발행 뒤 confirm 을 기다리는 한도. 넘기면 발행 실패로 보고 다음 차례에 다시 보낸다 |

두 볼륨은 백엔드 compose 가 이름을 고정해 만든다(`ai-block-clock_file-store` · `ai-block-clock_results`). Runtime compose 는 둘을 `external` 로 마운트하므로 **백엔드를 먼저 배포한다.**

---

## 5. 워크플로우 JSON — v2.1 형식

백엔드가 SubmitRun 의 `workflow` 칸(§4.3)에 담아 보내는 JSON이다. **프론트 캔버스가 만들어 백엔드 DB에 저장할 형식이기도 하다.**

### 5.1 전체 예시 — 판매자 한 명의 에이전트 (분할 · 병합 · 분기 · 합류)

```jsonc
{
  "id": "brand_agent",                     // 선택. 로그 식별용
  "name": "브랜딩 전략",                     // 선택. 화면 표시용
  "model": null,                           // 선택. null 이면 서버 기본값(gemini-2.5-pro · 09-26). "gemini-2.5-flash" 지정 가능

  "nodes": [
    { "id": "n1", "type": "llm", "name": "시장 조사",
      "prompt": "당신은 시장 조사 분석가입니다. ...",
      "input":  { "example": { "brand_name": "…", "category": "…", "price_tier": "…", "comment": null } },  // 진입 노드만 — 입력란. null = 선택 항목
      "output": { "kind": "json",                                        // 판매자가 원하는 출력 모양을 예시로
                  "example": { "market_overview_md": "…", "competitors": [ { "name": "…", "note": "…" } ], "competition_level": "high", "level_reason": "…" } },
      "owner_id": "u_2001" },

    { "id": "n2", "type": "llm", "name": "제품 분석",
      "prompt": "당신은 제품 분석가입니다. ...",
      "input":  { "example": { "brand_name": "…", "category": "…", "price_tier": "…", "comment": null } },
      "output": { "kind": "json", "example": { "strengths": ["…"], "weaknesses": ["…"], "opportunities": ["…"] } },
      "owner_id": "u_2001" },

    { "id": "br1", "type": "branch",                       // LLM 호출 아님
      "condition": { "left": "n1.competition_level", "op": "==", "right": "high" } },

    { "id": "n3a", "type": "llm", "name": "차별화 전략",
      "prompt": "...", "output": { "kind": "json", "example": { "approach": "…", "positioning": "…", "core_strategies": ["…"] } }, "owner_id": "u_2001" },
    { "id": "n3b", "type": "llm", "name": "대중 공략 전략",
      "prompt": "...", "output": { "kind": "json", "example": { "approach": "…", "positioning": "…", "core_strategies": ["…"] } }, "owner_id": "u_2001" },

    { "id": "n4", "type": "llm", "name": "브랜드 전략 보고서",
      "prompt": "...", "output": { "kind": "json", "example": { "headline": "…", "positioning": ["…"], "messages": { "core": "…" }, "summary_md": null } }, "owner_id": "u_2001" },

    { "id": "n5", "type": "llm", "name": "법률 검토 메모",              // ← 형식이 다른 노드도 그냥 이어진다
      "prompt": "당신은 상표 전문 변호사입니다. 앞 단계의 브랜드 전략을 읽고 ...",
      "output": { "kind": "text" }, "owner_id": "u_2002" }
  ],

  "edges": [
    { "from": "n1", "to": "br1", "port": "market"  },      // 병합 — port가 입력 객체의 키
    { "from": "n2", "to": "br1", "port": "product" },
    { "from": "br1", "to": "n3a", "when": true  },         // 분기 — 선택된 경로
    { "from": "br1", "to": "n3b", "when": false },
    { "from": "n3a", "to": "n4" },                         // 합류 — 도착한 하나
    { "from": "n3b", "to": "n4" },
    { "from": "n4",  "to": "n5" }                          // 브랜드 전략 → 법률 검토. 형식 검사 없음
  ],

  "output": { "title": "브랜드 전략 보고서", "template": null }
}
```

### 5.2 노드 — `type`으로 종류를 구분한다

| `type` | 하는 일 | `prompt` | 과금 |
| --- | --- | --- | --- |
| `llm` (기본값) | 프롬프트를 실행해 결과(글 또는 JSON)를 만든다 | **필수** | 있음 |
| `branch` | 조건을 평가해 경로를 고른다 | 없음 | **0** |
| `agent` | 남의 에이전트를 통째로 — Runtime이 안을 펼쳐 실행한다 | 없음 | 내부 노드별 |

**`type`을 생략하면 `llm`이다.** 웹 검색 · 계산 같은 **도구 노드는 MVP에 없다.**

입출력 노드(H2A · A2A2H)는 **캔버스의 노드로 그리지 않는다.** 입력은 진입 노드의 `input`으로 만든 폼이고, 출력은 종단 노드의 결과다.

#### `llm` 노드의 필드

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `id` | 필수 | 워크플로우 안에서 유일 |
| `type` | 선택 | 생략 시 `llm` |
| `name` | 선택 | 진행 화면 · 다음 노드 입력의 절 제목에 쓰인다 |
| `prompt` | 필수 | 판매자가 쓴 프롬프트 원문 |
| **`output`** | **필수** | 이 노드가 내는 형식. `{ "kind": "text" }` 또는 `{ "kind": "json", "example": { … } }` (§5.5) |
| **`input`** | 선택 · **진입 노드만** | `{ "example": { … } }`. 사용자 입력 예시 — 키가 입력란, 값을 `null`로 두면 선택 항목. 없으면 자유 입력(코멘트 + 파일) |
| `ref_files` | 선택 | 판매자가 노드에 붙여 둔 참고 자료의 **파일명 목록**(표시용). 실물은 SubmitRun 의 `ref_files` 경로로 보낸다(§4.3). 전개된 에이전트 안 노드는 `a1.n1`처럼 **접두 id**로 |
| `owner_id` | 권고 | 이 노드의 원작자. 정산 귀속 키 (§8.2) |
| `agent_id` | 선택 | 에이전트 단위로 묶어 돌려받고 싶을 때 (에이전트 전개 시 Runtime이 채운다) |

**없어진 필드** — `output.sample` · `output_schema_id` · `input.sample` · `input_schema_id`(견본 id → 한 버전 동안 그 견본의 예시로 읽는다) · `unit_price` · `base_cost` · `premium_rate`(무시). 보내면 `DEPRECATED_FIELD` 경고가 `validate`에 뜬다. **다음 버전에서 별칭도 사라진다.**

#### `agent` 노드 — Runtime이 전개한다

백엔드는 저장된 에이전트 정의를 **그대로 끼워** 보낸다. Runtime이 ID에 접두를 붙여 평탄화하고, 들어오는 엣지는 내부 진입 노드로, 나가는 엣지는 실행된 내부 종단 노드로 잇는다.

```jsonc
{ "id": "a1", "type": "agent",
  "agent": {
    "agent_id": "ag_31",
    "owner_id": "u_2001",
    "nodes": [ /* 5.1과 같은 형식 */ ],
    "edges": [ /* 5.1과 같은 형식 */ ]
  } }
```

- **에이전트의 공개 입력 = 내부 진입 노드의 `input`, 공개 출력 = 내부 종단 노드의 `output`.** 따로 선언하지 않는다(보내면 무시). 마켓 카드는 이 둘을 그대로 표시한다 — "글" 또는 "JSON(예시의 키 목록)", 입력은 예시의 키 또는 "자유".
- 정산은 내부 노드의 `owner_id`로 귀속된다. 없으면 `agent.owner_id`로 채운다.
- **중첩은 깊이 1까지다.** 에이전트 안에 에이전트를 넣을 수 없다.
- 내부에 분기 · 분할 · 병합이 있을 수 있어서 **평평한 프롬프트 배열로는 펼 수 없다.** `nodes` + `edges` 그대로 보낸다.

### 5.3 `edges` — 연결선이 실행 순서를 정한다

```jsonc
{ "from": "n1", "to": "br1", "port": "market", "when": true }
```

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `from` · `to` | 필수 | 노드 ID |
| `port` | 선택 | **병합할 때 입력 객체의 키.** 없으면 `from` 노드 ID를 키로 쓴다 |
| `when` | 분기에서만 | `branch` 노드에서 나가는 간선의 선택 경로. `true` · `false` |

**어떤 노드 사이에도 선을 그을 수 있다.** 형식 호환 검사는 없다. `edges`가 없으면 `nodes` 배열 순서대로 직선 실행한다.

### 5.4 실행 규칙 — 규칙 하나로 분할 · 병합 · 분기 · 합류가 다 된다

> **노드는 "실제로 실행된" 상위 노드들의 결과 전체를 받는다. 하나면 그 결과 그대로, 여럿이면 `port` 이름으로 묶은 객체로. 분기 노드는 받은 것을 그대로 선택된 경로에 넘긴다.**

| 상황 | 실행된 상위 | 노드가 받는 것 |
| --- | --- | --- |
| 직선 | 1 | 앞 결과 그대로 (JSON이면 그 객체, 글이면 `{ "text": "..." }`) |
| 분할 (한 노드 → 여러 노드) | 1 | 하위 노드들이 각각 같은 결과를 받는다. MVP는 순차 실행 |
| 병합 (여러 노드 → 한 노드) | 2 이상 | `{ "market": {...}, "product": {...} }` — 키는 `port` |
| 조건 분기 뒤 합류 | 1 | 도착한 결과. 안 간 쪽은 `not_executed`. **양쪽 형식이 달라도 된다** |

**받은 것을 LLM에게 어떻게 보여주나.** 프롬프트 원문 뒤에 아래 블록이 붙는다. 판매자는 프롬프트에서 필요한 것만 읽으면 된다.

```
## 입력
### 시장 조사 (n1 · 시장 조사 결과)           ← 병합이면 port마다 절, 단일이면 절 하나
```json
{ "competition_level": "high", ... }
```
### 법률 검토 메모 (n5 · 글)                   ← 글 출력은 본문 그대로
...

## 사용자 코멘트
...
```

**입력 상한.** 앞 결과 직렬화 + 참고 파일 텍스트 추정치가 `MAX_INPUT_CHARS`(기본 400,000자 ≈ 100k 토큰)를 넘으면 그 노드는 `INPUT_TOO_LARGE`로 실패한다 — **platform 책임** · 전액 환원. 요약이나 잘라내기는 하지 않는다. 실측 노드당 약 6k 토큰이라 8노드 체인에서도 여유가 크다.

**첫 노드**는 `edges`의 `to`에 없는 노드다. 사용자 입력(§4.3 `input` · `files`)을 받는다.

#### 분기 조건

```jsonc
"condition": { "left": "n1.competition_level", "op": "==", "right": "high" }
```

| | |
| --- | --- |
| `left` | `"<노드ID>.<필드 경로>"`. **실행된 임의 상위 노드**를 가리킬 수 있다. 바로 앞 노드로 제한하지 않는다 |
| `op` | `==` `!=` `>` `>=` `<` `<=` `in` |
| `right` | 리터럴 (숫자 · 문자열 · 불리언, `in`은 배열). **문자열은 대소문자 · 앞뒤 공백을 무시하고 비교한다** — `"High "`도 `high` |

**저장 시 코드가 검사한다(`validate`).** `left`의 노드는 `output.kind`가 `json`이어야 하고(글 노드는 `BRANCH_ON_TEXT_OUTPUT`), 필드 경로가 그 노드의 **출력 예시**에 있어야 하며(`BRANCH_REF_UNKNOWN_FIELD`), 필드 타입에 맞는 연산자여야 한다(`BRANCH_OP_TYPE_MISMATCH`).

| 필드 타입 | 허용 연산자 |
| --- | --- |
| `string` | `==` `!=` `in` |
| `number` (integer 포함) | 전부 |
| `boolean` | `==` `!=` |
| 배열 · 객체 | **분기 불가.** 개수 비교가 필요하면 출력 예시에 숫자 필드(예: `competitor_count`)를 둔다 |

**평가는 코드가 한다. LLM을 쓰지 않는다.** 같은 입력이면 항상 같은 경로가 나온다. 실행 때 경로가 없으면(선택 필드가 `null`) `BRANCH_CONDITION_INVALID` · 수요자 책임이다.

### 5.5 `output` — 노드가 내는 형식과 워크플로우의 산출물

**노드의 `output`**

| `kind` | 뜻 | 봉투 `payload` | 검증 · 교정 |
| --- | --- | --- | --- |
| `text` | 자유 텍스트 | `{ "text": "..." }` | 없음 |
| `json` + `example` | 판매자가 준 **예시와 같은 모양**의 JSON | 그 모양의 객체 | 예시에서 만든 스키마로 구조화 출력 강제 → 키 · 타입 검사 → 교정 2회 → 초과 시 `SCHEMA_VALIDATION_FAILED` · 공급자 책임 |

**예시 규칙** — 예시는 키가 하나 이상인 JSON 객체다. 값의 타입이 곧 규격이다(`"…"` 문자 · `1` 숫자 · `true` 참거짓 · `[…]` 배열(첫 원소가 원소 모양) · `{…}` 객체). **값을 `null`로 둔 키는 선택 항목**이고 나머지는 필수다. 예시에 없는 키가 더 와도 통과한다. 선택지(high/low만) · 개수(최대 5개) 같은 제약은 표현하지 않는다 — 프롬프트로 지시한다. 빈 객체 `{}` · 중첩 6단계 이상 · 키 60개 초과는 `OUTPUT_EXAMPLE_INVALID`. mock 실행은 이 예시를 그대로 결과로 돌려준다.

**워크플로우 최상위 `output`**

```jsonc
"output": {
  "title": "브랜드 전략 보고서",                       // 선택. 서식의 {{ title }}
  "template": {                                    // 선택. 없으면 기본 서식
    "name": "그린 보고서",                            // 화면 표시용
    "html": "<!DOCTYPE html>… {{ 헤드라인 }} … {% for p in 포지셔닝 %}…{% endfor %} …",   // 백엔드가 보관한 서식 HTML
    "default": "document"                          // 사용자 서식이 없거나 실패했을 때 쓸 기본 서식: document | card
  }
}
```

| 필드 | 설명 |
| --- | --- |
| `title` | 선택. 서식의 `{{ title }}`. 없으면 결과 JSON의 짧은 문자열 값 하나 |
| `template.html` | 선택. **Jinja2 HTML 서식.** `{{ 키 }}`로 결과 JSON 키를, `{% for x in 목록 %}`로 배열을 쓴다. `<script>` · 외부 URL · 이벤트 속성 금지, 200,000자 이하. 기본 제공 변수 `title` · `date` · `fields`(키 · 값 HTML 목록) · `text`(글 출력 본문). 필터 `md`(마크다운) · `fmt`(아무 값이나 HTML로) |
| `template.name` | 선택. 응답 `result.template.name` |
| `template.default` | 선택. `document`(문서형, 기본) · `card`(카드형) |

**서식 적용 순서(실행 끝, 정산과 무관).** ① 서식이 쓰는 빈칸 이름과 결과 JSON 키를 대조한다 → 빠진 빈칸이 있으면 **AI가 한 번** 어느 **경로**를 넣을지 짝짓는다(`headline` · `messages.core` · `reasons.0.axis` · 반복 항목의 `목록[].title`→`label`. 경로만 받으므로 내용을 지어낼 수 없다. `result.template.mapping`에 기록, `warnings[]`에 `TEMPLATE_MAPPED`) → 결과에 없는 빈칸(디자인의 고정 칸 등)은 **비워 두고** `TEMPLATE_BLANKS` → ② 붓는다 → ③ 서식 자체가 깨졌으면(문법 · 금지 요소) **기본 서식**으로 만들고 `TEMPLATE_RENDER_FAILED`. 어느 경우든 실행은 성공이고 `/artifact` · `/html`은 있다.

산출물은 셋이다 — **종단 노드의 봉투**(`/artifact`, JSON 원본) · **서식을 입힌 HTML**(`/html`) · **그 HTML의 PDF**(`/pdf`). 기본 서식은 **키 이름을 제목**으로 절을 자동 배치하고(한글 키면 한글 제목, 비어 있는 선택 항목은 뺀다), 글 출력이면 본문을 그대로 문서로. `output.schema_id` · `template_id`는 없어졌다(보내면 무시).

### 5.6 그래프 규모 상한

| 상한 | 기준 | 값 |
| --- | --- | --- |
| 캔버스 노드 수 | 사용자가 놓은 개수 (에이전트 1개 = 1) | **8** |
| 전개 후 노드 수 | `nodes` 배열 전체 길이, 에이전트를 펼친 뒤 | **20** |
| 최장 경로 길이 | 그래프에서 **가능한** 가장 긴 경로 (실제로 도는 경로가 아니다) | **8** |
| 에이전트 중첩 깊이 | | **1** |

넷 다 워크플로우 정의만 보면 정해지는 **정적인 값**이라, 저장 시점(백엔드 · 프론트)과 실행 시점(Runtime)의 판정이 갈리지 않는다. 실행 시간 5분(PER-003)은 상한으로 쓰지 않는다. 입력에 따라 달라져 저장 시점에 판정할 수 없어서, 경고로만 쓴다.

**이 규칙을 백엔드가 복제하지 않아도 된다.** `POST /v1/workflows/validate`(§7.5)를 저장 버튼에서 부르면 된다.

### 5.7 실행 전에 검사하는 것

어기면 실행하지 않고 `rejected`(`INVALID_WORKFLOW`) 하나로 끝난다. snapshot 의 `error.errors` 배열에 어느 노드의 무엇이 문제인지 담긴다(§4.4). 저장 시점 `validate`(§7.5)와 같은 검사다.

- `nodes`는 1개 이상의 배열, `id`는 비어 있지 않고 중복되지 않음
- `type: "llm"`인 노드는 `prompt`가 비어 있지 않음
- `llm` 노드에 `output`이 있고 `kind`가 `text` · `json`, `json`이면 `example`이 쓸 만한 JSON 객체
- 진입 노드의 `input.example`(있다면)이 쓸 만한 JSON 객체
- `edges`의 `from` · `to`가 실재하는 노드, **순환이 없음**
- `branch` 노드는 `condition`이 있고 나가는 간선이 `when: true` · `when: false` 각각 하나
- 분기 `condition.left`가 **글이 아닌 상위 노드의 출력 예시 필드**를 가리키고 연산자가 타입에 맞음
- §5.6 상한을 넘지 않음

---

## 6. 출력 예시와 봉투

### 6.1 출력 예시 — 플랫폼 견본 목록은 없다 (v2.1)

v2.0까지 있던 견본 11종(`common.*` · `biz.*`)은 09-26에 없앴다. 판매자는 노드마다 원하는 출력 JSON을 **예시 하나**로 적는다(§5.5 예시 규칙). 예시가 곧 강제 규격이고, 분기 조건의 필드 · 사용자 입력란 · 기본 문서 렌더가 모두 예시에서 나온다.

**프론트가 그릴 것** — 노드 편집의 "출력 형식"은 **글 / JSON** 둘, JSON이면 예시를 적는 입력칸 하나(n8n처럼). 분기 조건의 필드 드롭다운은 기준 노드 `output.example`의 키(문자 · 숫자 · 참거짓만), 실행 폼은 진입 노드 `input.example`의 키. 별도 API 호출이 필요 없다 — 노드 정의 안에 다 있다.

### 6.2 봉투 — Runtime이 씌운다

노드 결과는 봉투에 담긴다. **LLM은 `payload`만 만들고 봉투는 Runtime이 씌운다.** 다음 노드에는 payload(+ 사용자 코멘트)만 넘어간다.

```jsonc
{
  "a2a_version": "2.0",
  "run_id": "6f1c2a9e-0b7d-4c1e-9a53-2f8e1d4c7b10",
  "node_id": "n1",
  "produced_at": "2026-09-25T01:04:51Z",
  "schema_id": "json",                           // "json" 또는 "text". 모양은 노드의 output.example
  "payload": { "competition_level": "high", "price_band": "중가", "...": "..." },
  "meta": { "model": "gemini-2.5-pro",            // 09-26부터 기본. 아래 숫자는 Flash 시절 예시
            "usage": { "input_tokens": 1281, "output_tokens": 1349 },
            "cost_usd": 0.003757, "duration_ms": 18100, "retry_count": 0, "repair_count": 1 },
  "error": null
}
```

`repair_count`는 형식이 틀려 다시 요청한 횟수다. **교정은 2회까지** 하고, 넘으면 `SCHEMA_VALIDATION_FAILED` · 공급자 책임이다. 글 출력은 항상 0이다.

> **A2A는 자체 정의 용어이며 Google A2A 프로토콜과 무관하다.**

---

## 7. 엔드포인트

### 7.1 실행 요청 — 메시지로 옮겼다 (v2.3)

`POST /v1/runs` 는 없다. 실행은 백엔드가 **SubmitRun 메시지**로 맡긴다 — 칸 · 파일 규칙 · 예시는 §4.3. 폼 파트(`file` · `ref_file_<node_id>_<n>`)는 file-store 경로(`files` · `ref_files`)로, 멱등 키 `client_run_id` 는 `execution_id` 로 바뀌었다. 202 응답의 `poll_url` · `poll_interval_ms` 도 없다 — 진행은 RunEvent(§4.4)로 온다.

### 7.2 `GET /v1/runs/{run_id}` — 진행 상황 · 결과 조회

**RunEvent 의 `snapshot` 이 이 응답과 같은 모양이다.** 진행은 이벤트로 오므로 백엔드는 이 API 를 주기적으로 부르지 않는다 — 이벤트가 끊긴 실행의 대조(§4.9) · 정산 근거 백필 · 운영 확인에만 쓴다. `run_id` 는 SubmitRun 의 `execution_id` 다.
쿼리 `include_node_output=true`를 붙이면 노드별 결과 전문(`nodes[].output_payload`)이 함께 온다. 기본은 제외다(응답이 커진다). 완료 이벤트(`succeeded` · `failed`)의 snapshot 은 이것을 붙인 모양이다.

```jsonc
{
  "run_id": "6f1c2a9e-0b7d-4c1e-9a53-2f8e1d4c7b10",
  "status": "running",                      // queued | running | succeeded | failed
  "mode": "run",                            // run | test
  "workflow": { "id": "brand_agent", "name": "브랜딩 전략 에이전트", "node_count": 7 },
  "mock": false,

  "progress": {
    "done": 2, "total": 5, "percent": 40,   // total = 돌아야 하는 LLM 노드 수. 분기 노드 · 건너뛴 노드는 세지 않는다
    "current_index": 3,
    "current_node_id": "n3a",
    "current_node_name": "차별화 전략"
  },

  "nodes": [
    { "id": "n1", "name": "시장 조사", "type": "llm", "status": "succeeded",
      "output": { "kind": "json", "example": { "competition_level": "high", "...": "..." } },
      "agent_id": null, "owner_id": "u_2001",
      "started_at": "2026-09-25T01:04:52+00:00", "finished_at": "2026-09-25T01:05:10+00:00",
      "duration_ms": 18100, "model": "gemini-2.5-pro",   // 숫자는 예시
      "input_tokens": 1281, "output_tokens": 1349, "cost_usd": 0.003757,
      "retries": 0, "repair_count": 1,
      "skip_reason": null, "branch_taken": null },
    { "id": "br1", "name": "경쟁 강도 분기", "type": "branch", "status": "succeeded",
      "output": null, "duration_ms": 0, "model": null, "input_tokens": 0, "output_tokens": 0, "cost_usd": 0.0,
      "repair_count": 0, "skip_reason": null, "branch_taken": true },     // 코드가 판정했다. LLM 호출 · 과금 없음
    { "id": "n3a", "name": "차별화 전략", "type": "llm", "status": "running",
      "output": { "kind": "json", "example": { "approach": "…", "positioning": "…", "core_strategies": ["…"] } },
      "started_at": "2026-09-25T01:05:11+00:00", "finished_at": null,
      "duration_ms": null, "model": null, "input_tokens": null, "output_tokens": null, "cost_usd": null,
      "retries": 0, "repair_count": 0 },
    { "id": "n3b", "name": "대중 공략 전략", "type": "llm", "status": "skipped",
      "output": { "kind": "json", "example": { "approach": "…", "positioning": "…", "core_strategies": ["…"] } },
      "skip_reason": "branch_not_taken", "cost_usd": null }              // 안 간 쪽. §8.4 에 잡힌다
  ],

  "agents": [                                // 에이전트(판매자)별 사용량 합계. 에이전트 밖 노드는 agent_id null 로 한 묶음
    { "agent_id": null, "owner_id": "u_2001", "node_ids": ["n1", "n3a"],
      "input_tokens": 1281, "output_tokens": 1349, "cost_usd": 0.003757 }
  ],
  "totals": { "input_tokens": 1281, "output_tokens": 1349, "cost_usd": 0.003757 },
  "started_at": "2026-09-25T01:04:51+00:00",
  "finished_at": null,
  "total_duration_ms": null,

  "contribution": [                          // 성공한 노드가 늘어날 때마다 즉시 커진다. mode=test 면 항상 []
    { "node_id": "n1", "owner_id": "u_2001", "agent_id": null }
  ],
  "not_executed": [                          // §8.4
    { "node_id": "n3b", "reason": "branch_not_taken" }
  ],
  "failure": null,                           // 실패 시에만
  "result": null,                            // 완료 시에만
  "error": null
}
```

**`nodes[]` 필드 — 무엇을 뜻하고 누가 쓰나**

| 필드 | 뜻 | 주로 쓰는 곳 |
| --- | --- | --- |
| `type` | `llm` 또는 `branch`. 분기 노드는 LLM을 부르지 않고 과금도 없다 | 프론트 — 분기 노드는 진행 표시를 다르게 |
| `status` | `pending → running → succeeded` · `failed` · **`skipped`** | 진행 화면 |
| `output` | 이 노드가 선언한 형식 `{kind, example}`. 분기 노드는 `null` | 프론트 — 결과 미리보기 · 마켓 카드 |
| `agent_id` | 어느 에이전트 안에서 돈 노드인가. 캔버스의 에이전트 블록과 매핑 | 백엔드 — 에이전트 단위로 정산 묶기 |
| `owner_id` | 정산이 귀속되는 판매자 계정 | 백엔드 — 적립 |
| `started_at` · `finished_at` · `duration_ms` | 노드 실행 시각 · 소요 | 진행 화면 · 실측 |
| `model` | 실제로 부른 모델. mock이면 `"mock"` | 원가 검산 |
| `input_tokens` · `output_tokens` | 쓴 토큰. **교정 재요청분까지 합산** | 원가 검산 · Base Cost |
| **`cost_usd`** | 토큰 × 모델 공시 단가(§8.1). **ABC가 아니다.** mock이면 0 | 백엔드 — ABC 환산 · Base Cost 보정 |
| `retries` | 모델 API 오류(429 · 5xx)로 다시 부른 횟수 | 운영 |
| `repair_count` | **형식이 틀려 다시 요청한 횟수(0 ~ 2).** 2를 넘기면 `SCHEMA_VALIDATION_FAILED` · 공급자 책임. 글 출력은 0 | 판매자 품질 지표 |
| `skip_reason` | `skipped`일 때만. `branch_not_taken`(분기로 안 감) · `upstream_failed`(앞이 실패) | 정산은 이것 말고 `not_executed[]`를 본다 |
| `branch_taken` | 분기 노드가 고른 쪽. `true`면 `when: true` 간선으로 갔다 | 진행 화면 "예 / 아니오" |

**노드 상태값** `pending → running → succeeded` 또는 `failed`. 한 방향뿐이고 되돌아가지 않는다.
에이전트를 펼친 노드의 `id`는 `a1.n1`처럼 **에이전트 id가 접두**로 붙는다(§5.2).

**`agents[]`** — `agent_id`(에이전트 밖 노드는 `null`)별로 `owner_id` · `node_ids` · 토큰 합 · `cost_usd` 합. 한 실행에 판매자 여럿의 사용량을 백엔드가 다시 더하지 않아도 되게 하려는 것이다.

**완료 시 추가되는 부분**

```jsonc
{
  "status": "succeeded",
  "finished_at": "2026-09-25T01:07:02+00:00",
  "total_duration_ms": 131000,
  "warnings": [ { "code": "TEMPLATE_MAPPED", "message": "서식 빈칸 헤드라인←headline 을(를) 짝지어 채웠습니다" } ],   // 성공했지만 알릴 것 (v2.2)
  "result": {
    "type": "pdf",
    "output": { "kind": "text" },                                          // 종단 노드의 형식
    "artifact_url": "/v1/runs/6f1c2a9e-…/artifact",   // 결과 JSON (봉투) — 항상 있다
    "html_url": "/v1/runs/6f1c2a9e-…/html",           // 서식을 입힌 HTML (v2.2) — 항상 있다
    "pdf_url": "/v1/runs/6f1c2a9e-…/pdf",             // 그 HTML 의 PDF. 실패면 null (실행 실패 아님)
    "template": { "applied": "user", "name": "그린 보고서", "fallback_reason": null,   // user | default
                  "mapping": { "헤드라인": "headline" } }
  }
}
```

### 7.3 `GET /v1/runs/{run_id}/artifact` — 결과 JSON

종단 노드의 **봉투**(§6.2)를 그대로 돌려준다. `status`가 `succeeded`일 때만. 아직이면 409 `ARTIFACT_NOT_READY`.

같은 내용이 `succeeded` 이벤트의 `artifact` 칸과 결과 볼륨의 `<execution_id>/artifact.json`(§4.5)으로도 온다. **백엔드는 볼륨에서 읽어 자기 스토리지에 저장한다.** 글 출력이면 `payload.text`가 본문이다.

### 7.4 `GET /v1/runs/{run_id}/html` · `/pdf` — 서식을 입힌 결과

`/html`은 `text/html`(서식이 입혀진 완결 HTML 문서 — 그대로 화면에 띄우거나 저장), `/pdf`는 `application/pdf`(같은 HTML을 A4로). 아직이면 409 `HTML_NOT_READY` · `PDF_NOT_READY`. PDF는 사용자 서식의 CSS를 다 재현하지 못할 수 있다(단순 HTML→PDF 엔진). 화면 표시는 HTML을, 인쇄 · 다운로드는 PDF를 쓴다.
Runtime은 `succeeded` 전에 같은 파일을 결과 볼륨(`report.html` · `report.pdf`, §4.5)에 써 둔다. 보관 기한이 지나면 지운다. **백엔드는 볼륨에서 읽어 자기 스토리지에 저장하고(지금은 JSON · PDF) 프론트에는 백엔드가 발급한 URL을 준다.**

### 7.5 `POST /v1/workflows/validate` — 저장 시점 검증 + 도는 노드 목록

워크플로우 JSON만 받아 **실행하지 않고** 검증 결과와 **최악 분기 조합에서 도는 노드 목록**을 돌려준다. 저장 버튼에서 한 번 부르면 상한 · 순환 · 분기 참조 규칙을 백엔드가 복제하지 않아도 된다. 과금은 없다. **금액은 없다** — 백엔드가 `worst_case_nodes`의 단가를 합산해 최대 청구액을 만든다.

```jsonc
// 요청 (application/json)
{ "workflow": { /* §5 형식. 에이전트 노드는 정의를 그대로 끼워 보낸다 */ } }

// 응답 200
{ "valid": true,
  "errors": [],
  "warnings": [ { "code": "DEPRECATED_FIELD", "node_id": "n1",
                  "message": "견본 id biz.market_research@1(출력 — 예시로 바꿔 읽음) — 견본 · 옛 필드는 v2.1 에서 쓰지 않습니다. 다음 버전에서 사라집니다" } ],
  "stats": { "node_count": 7,              // 캔버스에 놓인 노드 수 (에이전트 1개 = 1)
             "expanded_node_count": 7,     // 에이전트를 펼친 뒤 노드 수
             "longest_path": 5,            // 가능한 가장 긴 경로 (실제로 도는 경로가 아니다)
             "agent_depth": 0 },
  "estimate": { "worst_case_nodes": ["n1", "n2", "n3a", "n4", "n5"],   // 그때 실제로 도는 LLM 노드 (분기 노드 제외)
                "worst_case_node_count": 5 } }

// 응답 200 — 틀린 정의
{ "valid": false,
  "errors": [ { "code": "BRANCH_REF_UNKNOWN_FIELD", "node_id": "br1",
                "message": "n1 의 출력 예시에 competition 필드가 없습니다" } ],
  "warnings": [ { "code": "LONG_PATH", "node_id": null, "message": "최장 경로 7단계. 5분을 넘길 수 있습니다" } ],
  "stats": { "...": "..." }, "estimate": { "...": "..." } }
```

**`worst_case_nodes`는 최장 경로가 아니다.** 분할 · 병합이 있으면 여러 노드가 함께 돌기 때문에, 분기 조합마다 "그 경우 실제로 도는 노드"를 구해 **개수가 가장 많은 조합**을 잡는다. 백엔드는 이 목록의 단가 합으로 선차감액을 정한다.

**`errors[].code` — 하나라도 있으면 `valid: false`, 실행은 `rejected` · `INVALID_WORKFLOW`(§9.1)**

| code | 뜻 |
| --- | --- |
| `NO_NODES` | `nodes`가 비어 있음 |
| `INVALID_GRAPH` | id 누락 · 중복, 없는 노드를 가리키는 엣지, **순환** |
| `MISSING_PROMPT` | `llm` 노드의 `prompt`가 비어 있음 |
| **`MISSING_OUTPUT`** | `llm` 노드에 `output`이 없음 (별칭 `output_schema_id`도 없음) |
| **`OUTPUT_KIND_INVALID`** | `output.kind`가 `text` · `json`이 아님 |
| **`OUTPUT_EXAMPLE_INVALID`** | `json`인데 `output.example`이 없거나 쓸 수 없음(객체 아님 · 빈 객체 · 너무 깊음 · 키 60개 초과). `input.example`도 같다 |
| **`UNKNOWN_SAMPLE`** | 옛 견본 id(`output.sample` · `output_schema_id` · `input.sample` · `input_schema_id`)가 11종에 없음 — 예시로 바꿀 수 없다 |
| `BRANCH_CONDITION_MISSING` · `BRANCH_EDGES_INVALID` | 분기 노드에 `condition`이 없음 · `when: true/false` 간선이 각각 하나가 아님 |
| **`BRANCH_ON_TEXT_OUTPUT`** | 분기 `left`가 글(`text`) 출력 노드를 가리킴 |
| **`BRANCH_REF_UNKNOWN_FIELD`** | `left`의 필드 경로가 그 노드의 출력 예시에 없음 (또는 배열 · 객체 필드) |
| **`BRANCH_OP_TYPE_MISMATCH`** | 필드 타입에 허용되지 않는 연산자 (§5.4 표) |
| `LIMIT_EXCEEDED` | §5.6 상한 초과 (캔버스 8 · 전개 20 · 경로 8) |
| `AGENT_NESTING_TOO_DEEP` · `AGENT_DEFINITION_MISSING` · `AGENT_EDGE_UNKNOWN_NODE` | 에이전트 안에 에이전트 · `agent.nodes` 없음 · 내부 엣지가 없는 노드를 가리킴 |

**v2.0에서 사라진 코드** — `SCHEMA_INCOMPATIBLE` · `JOIN_SCHEMA_MISMATCH` · `OUTPUT_SCHEMA_MISMATCH` · `AGENT_BOUNDARY_VIOLATION` · `MISSING_OUTPUT_SCHEMA` · `UNKNOWN_SCHEMA`.

**`warnings[].code` — 실행은 된다. 화면에 안내만**

| code | 뜻 |
| --- | --- |
| `LONG_PATH` | 최장 경로 7 이상. 5분(PER-003)을 넘길 수 있다 |
| **`DEPRECATED_FIELD`** | 옛 견본 id(`output.sample` · `output_schema_id` · `input.sample` · `input_schema_id`) · `unit_price` · `base_cost` · `premium_rate` · `agent.input_schema_id` · `agent.output_schema_id` · `output.schema_id`가 왔다. 견본 id는 예시로 바꿔 읽었고 나머지는 무시했다 |

### 7.6 `GET /v1/schemas` — 없어짐 (v2.1)

견본이 없어져 **빈 목록**을 돌려준다: `{ "deprecated": true, "schemas": [], "message": "…" }`. `GET /v1/schemas/{id}`는 404 `SCHEMA_NOT_FOUND`. 부르는 코드가 있어도 깨지지 않게 한 버전 유지하고 다음 버전에서 지운다. **프론트는 드롭다운 · 폼을 노드의 `output.example` · `input.example`로 그린다**(§6.1).

### 7.8 `POST /v1/templates/convert` — 디자인(PDF · 사진) → 빈칸 뚫린 HTML 서식 (v2.2)

캔바 · 미리캔버스처럼 사용자(또는 판매자)가 **자기 디자인**을 올리면 그 레이아웃 · 색 · 고정 문구를 재현한 Jinja HTML 서식을 돌려준다. **올릴 때 한 번** AI를 부르고(교정 1회 포함 최대 2회), 결과 HTML은 **백엔드가 보관**해 실행 때 `output.template.html`로 보낸다. Runtime은 보관하지 않는다. 실행이 아니므로 과금 대상이 아니다.

```jsonc
// 요청 multipart/form-data
//   design: PDF · png · jpg · webp (여러 장이면 페이지 순서대로, 각 10MB)   hint: 서식 설명(선택)   mock: true 면 AI 없이 기본 서식
// 응답 200
{ "html": "<!DOCTYPE html>…{{ 제목 }}…",
  "placeholders": ["제목", "브랜드명", "포지셔닝"],   // 서식이 쓰는 빈칸 — 프론트가 미리보기 · 안내에 쓴다
  "repaired": false,                                  // 검사에 걸려 한 번 고쳤는가
  "problems": [],                                     // 남은 문제. 비어 있지 않으면 사용자에게 보여 주고 다시 올리게 한다
  "usage": { "input_tokens": 3120, "output_tokens": 2400, "model": "gemini-2.5-pro" } }
```

400 `UNSUPPORTED_FILE_TYPE` · `FILE_TOO_LARGE`, 502 모델 오류. 서식은 `<script>` · 외부 URL 없이 인라인 CSS만이라 그대로 화면에 띄워도 된다.

### 7.7 보조

| 엔드포인트 | 용도 |
| --- | --- |
| `GET /v1/health` | 상태 확인. `{ status, broker, auth_required, runs_in_memory }`. `broker` 는 `connected` · `disconnected` |
| `POST /v1/runs/{run_id}/cancel` | **운영 도구.** 다음 노드부터 돌지 않고 `failed` · `CANCELLED`(platform)로 끝난다. 지금 부르는 LLM 호출은 끝까지 간다. 끝난 실행이면 `cancel_requested: false`. 백엔드는 이 API 대신 CancelRun 메시지(§4.6)를 쓴다 |
| `GET /v1/workflows` · `/{id}` | 예시 워크플로우(v2.1 형식). 복사해 테스트용으로 쓴다 |

---

## 8. 정산 연동

### 8.1 Runtime은 돈을 모른다 — 토큰과 USD 원가까지만

| 값 | 정하는 곳 |
| --- | --- |
| 실행된 노드 · 성공 여부 · 실패 책임 · 미실행 구간 · **노드별 · 에이전트별 · 전체 토큰과 USD 원가** | **Runtime** ← 이 문서가 주는 것 |
| Base Cost · ABC 환산율 · Premium Rate · 등급 · 실행 단가 · 선차감액 · 견적 금액 · 정산 | **백엔드** |

```
cost_usd = (입력 토큰 × 입력 단가 + 출력 토큰 × 출력 단가) / 1,000,000
```

- 단가는 모델 공시가(`gemini-2.5-pro` 입력 1.25 · 출력 10.00, `gemini-2.5-flash` 입력 0.30 · 출력 2.50 USD / 1M 토큰. 모르는 모델은 Pro 단가). 환경변수 `LLM_PRICE_IN` · `LLM_PRICE_OUT`으로 덮어쓴다. **ABC 환산율은 Runtime에 없다.**
- 교정 재요청분 토큰까지 합산한다. 실패한 노드도 쓴 토큰은 기록한다(환원 여부는 §8.5).
- 실측 기준 노드 1회 약 0.001 ~ 0.002 USD, 실행 1회(4노드) 약 0.007 USD. mock 실행은 0이다.
- **`mode=test`의 토큰 · USD가 Base Cost 실측(NOD-007)의 입력이다.** 몇 번 돌려 평균 낼지는 백엔드가 정한다.

### 8.2 요청에 담는 정산 필드 — `owner_id`뿐이다

| 필드 | 필수 | 왜 |
| --- | --- | --- |
| `owner_id` (노드) 또는 `agent.owner_id` | **권고** | 없으면 `contribution[]` · `agents[]`의 귀속이 비어서, 한 실행에 여러 판매자 계정으로 정산이 쌓이는 것을 Runtime 응답 하나로 보여줄 수 없다 |

`unit_price` · `base_cost` · `premium_rate`는 **더 이상 받지 않는다.** 보내면 무시하고 `DEPRECATED_FIELD` 경고를 낸다.

### 8.3 돈이 움직이는 네 지점

| 시점 | 무슨 일 | AI가 주는 근거 |
| --- | --- | --- |
| 실행 시작 | 선차감 (`mode=run`만) | `validate`의 `worst_case_nodes` × 백엔드 단가 |
| 노드 성공 | 원작자에게 적립 | `nodes[].status` 변화 + `contribution[]`(`node_id · owner_id · agent_id`) |
| 노드 실패 | 책임에 따라 보류 · 환원 | `failure.fault` + `refund_scope` |
| 실행 종료 | 미실행 구간 환원 · 정산 확정 · 실측 원가 보정 | `not_executed[]` · `agents[]` · `totals` |

**멱등성은 백엔드가 지킨다.** 같은 상태가 여러 번 온다 — `progress` 마다 스냅샷 전체가 오고, 재배달 · 재발행으로 마지막 이벤트가 다시 올 수 있다. 이미 적립한 `(run_id, node_id)`를 기록해 두 번 적립하지 않게 한다. **`mode=test`면 `contribution`이 항상 비어 있으므로 적립할 것이 없다.**

### 8.4 `not_executed` — 실행되지 않은 노드

```jsonc
"not_executed": [
  { "node_id": "n3b", "reason": "branch_not_taken" },   // 분기로 안 감
  { "node_id": "n4",  "reason": "upstream_failed"  }    // 앞이 실패해 도달 못 함
]
```

**요구사항 PAY-010** — 분기로 실행되지 않은 노드는 과금 · 정산 대상에서 제외하고 선차감분을 실행자에게 반환한다. 금액은 백엔드가 자기 단가로 계산한다. `branch` 노드 자체는 LLM 호출이 아니라 `contribution`에도 `not_executed`에도 들어가지 않는다.

### 8.5 실패했을 때 — `failure`

**실패 · 미실행 노드의 토큰 필드.** LLM을 부른 뒤 실패한 노드(형식 위반 `SCHEMA_VALIDATION_FAILED`, 교정 호출 중 `MODEL_TIMEOUT` · `MODEL_API_ERROR`)는 **그때까지 쓴** `input_tokens · output_tokens · cost_usd`가 들어오고 `totals` · `agents[]`에도 합산된다. 부르기 전에 실패한 노드(`PROMPT_TEMPLATE_INVALID` · `INPUT_TOO_LARGE` · `REF_FILE_REJECTED`, 첫 호출 자체의 예외)와 건너뛴 노드는 `null`, 분기 노드는 `0`이다. `null`은 "쓴 것이 없다"가 아니라 "잰 것이 없다"이니 0으로 읽되 정산에서는 `failure.fault`를 따른다.

```jsonc
"failure": {
  "node_id": "n4",
  "error_code": "MODEL_API_ERROR",
  "fault": "supplier",                        // supplier | consumer | platform
  "affects_success_rate": true,
  "refund_scope": {
    "refund_node_ids":  ["n4"],               // 수요자에게 환원
    "settled_node_ids": ["n1","n2","n3a"]     // 정산 유지
  }
}
```

| `fault` | 환원 대상 | 정산 유지 | 공급자 등급 |
| --- | --- | --- | --- |
| `supplier` | 실패 노드 + 이후 미실행 구간 | 앞서 성공한 노드 | 성공률에 실패로 집계 |
| `consumer` | 실패 노드 + 이후 미실행 구간 | 앞서 성공한 노드 | 영향 없음 |
| `platform` | **전체 노드** | 앞서 성공한 노드 | 영향 없음 |

`platform`일 때만 환원액과 정산액의 합이 차감액을 넘는다. 플랫폼 과실로 공급자가 손해 보지 않게 하려는 것이고, 차액은 플랫폼이 부담한다.

**조합 오류는 실패가 아니다.** 사용자가 문맥이 안 맞는 블록을 이어도 실행은 되고, 결과 품질이 낮으면 그것은 평점 · 리뷰의 영역이다. 환불 대상이 아니고 판매자 성공률에도 들어가지 않는다. (v1.x의 "입력 형식 위반 = 수요자 책임"은 사용자 입력 폼 위반으로 범위가 줄었다.)

---

## 9. 오류 처리

HTTP 4xx · 5xx는 아래 형태다. 실행 전 거절은 HTTP 가 아니라 `rejected` 이벤트의 `snapshot.error`(`code` · `message` · `hint` · `fault`, 정의 오류면 `errors[]`)로 온다(§9.1).

```jsonc
{ "detail": { "code": "INVALID_REQUEST", "message": "workflow 객체가 필요합니다" } }
```

`errors[]`의 코드는 §7.5 표와 같다 — 저장 시점 `validate`와 실행 전 검사가 **같은 검사기**를 쓴다.

### 9.1 실행 전 — `rejected` 이벤트 · HTTP 오류

**SubmitRun 이 실행 전에 걸리면 `accepted` 없이 `rejected` 하나로 끝난다(§4.4).** 어느 노드도 돌지 않았으므로 백엔드는 선차감을 **전액 돌려준다.** `affects_success_rate` 는 모두 `false` 다.

| code | 의미 | `fault` | 백엔드 처리 |
| --- | --- | --- | --- |
| `INVALID_WORKFLOW` | 노드 · 엣지 정의 오류, `workflow` 가 객체가 아님. `error.errors[]`에 §7.5 코드 | `consumer` | 전액 환불 · 사용자에게 안내 |
| `INVALID_MODE` | `mode`가 `run` · `test`가 아님(대소문자 구분) | `platform` | 전액 환불 · 설정 확인 |
| `INVALID_INPUT_JSON` | `input`이 객체도 `null`도 아님 | `platform` | 전액 환불 · 직렬화 확인 |
| `UNSUPPORTED_FILE_TYPE` | 사용자 파일 확장자가 허용 목록 밖(표 · 글 · PDF · 사진만) | `consumer` | 전액 환불 · 사용자에게 안내 |
| `FILE_TOO_LARGE` | 사용자 파일 합계 10MB 초과 | `consumer` | 전액 환불 · 사용자에게 안내 |
| **`FILE_PATH_REJECTED`** | `files` · `ref_files` 경로가 절대 경로 · `..` 포함 · file-store 밖이거나, 없거나 일반 파일이 아님 | `platform` | 전액 환불 · 볼륨 마운트 확인 |

본문이 JSON 이 아니거나 `execution_id` 가 없거나 형식이 틀린 SubmitRun 은 이벤트 없이 dead letter 로 간다 → `ABC_RUNTIME_DEAD_LETTERED`(§4.8).

**HTTP 오류** — 조회 · 검증 · 서식 변환에만 남았다.

| HTTP | code | 의미 | 백엔드 처리 |
| --- | --- | --- | --- |
| 401 | `UNAUTHORIZED` | 서비스 토큰 불일치 | 설정 확인 |
| 404 | `RUN_NOT_FOUND` | `run_id` 없음. 아직 받지 않았거나(큐에서 대기 · 재배달 전), 재배포 · 재시작 · 보관 기한(`RUN_RETENTION_HOURS`, 기본 24시간) 경과 | **곧바로 닫지 않는다.** 넘긴 시각에서 큐 대기 한도(10분) + grace 안이면 기다리고, 지나면 플랫폼 탓 실패(`ABC_RUNTIME_LOST`)로 닫고 환불한다(§4.9) |
| 409 | `PDF_NOT_READY` · `HTML_NOT_READY` · `ARTIFACT_NOT_READY` | 아직 실행 중이거나 실패 | `status` 확인 후 재시도 |
| 400 | `INVALID_REQUEST` | `POST /v1/workflows/validate` 본문이 워크플로우 객체가 아님 | 요청 직렬화 확인 |
| 400 | `UNSUPPORTED_FILE_TYPE` · `FILE_TOO_LARGE` | `POST /v1/templates/convert` 디자인 파일의 형식 · 크기 위반 | 사용자에게 안내 |
| 404 | `SCHEMA_NOT_FOUND` | `GET /v1/schemas/{id}` — v2.1에서 견본이 없어져 항상 404 | 부르지 않는다(§7.6) |
| 404 | `WORKFLOW_NOT_FOUND` | `GET /v1/workflows/{id}`에 없는 예시 워크플로우 | 개발용 보조 API |

### 9.2 실행 단계 (`failure.error_code`)

| code | 의미 | `fault` | 성공률 | 재시도 |
| --- | --- | --- | --- | --- |
| `SCHEMA_VALIDATION_FAILED` | **사용자 입력**이 진입 노드 `input.example`의 모양과 안 맞음(필수 키 누락 · 타입) | `consumer` | 미반영 | 무의미 |
| `SCHEMA_VALIDATION_FAILED` | 노드 출력이 `output.example`의 모양과 안 맞음 (교정 2회 초과) | `supplier` | 반영 | 가능 |
| `BRANCH_CONDITION_INVALID` | 조건이 가리키는 값이 실행 결과에 없음(선택 필드가 비어 옴) | `consumer` | 미반영 | 무의미 |
| **`INPUT_TOO_LARGE`** | 앞 결과 + 참고 파일 합이 입력 상한 초과 (§5.4) | **`platform`** | 미반영 | 무의미 |
| `REF_FILE_REJECTED` | 참고 파일 mime · 크기 위반 | `supplier` | 미반영 | 무의미 |
| `PROMPT_TEMPLATE_INVALID` | 판매자 프롬프트의 입력 틀(`{…}`)이 요구하는 값을 이 자리에서 받을 수 없음 | `supplier` | 미반영 | 무의미 |
| `MODEL_TIMEOUT` | 노드 60초 초과 | `supplier` | 반영 | 가능 |
| `MODEL_API_ERROR` | 모델 API 5xx 또는 기타 | `supplier` | 반영 | 가능 |
| `FILE_PARSE_FAILED` | 업로드 파일을 읽지 못함 | `consumer` | 미반영 | 무의미 |
| `RATE_LIMITED` | 요청 상한. 10초 간격 2회 재시도 후 실패 | `platform` | 미반영 | 잠시 후 |
| `TEMPLATE_RENDER_FAILED` | (v2.2부터 **`warnings[]`**로만) 사용자 서식을 못 써 기본 서식으로 만듦. 실행은 성공 | — | — | — |
| `PDF_RENDER_FAILED` | (v2.2부터 **`warnings[]`**로만) PDF 실패. `pdf_url: null`, HTML · JSON은 있다 | — | — | — |
| `TEMPLATE_MAPPED` · `TEMPLATE_BLANKS` | (`warnings[]`) 서식 빈칸을 AI가 짝지어 채웠다 · 결과에 없는 빈칸을 비워 두었다 — 정보 | — | — | — |
| `LLM_INIT_FAILED` | API 키 없음 등 설정 오류 | `platform` | 미반영 | 무의미 |
| `CANCELLED` | CancelRun(§4.6) · HTTP 취소로 다음 노드부터 멈춤 | `platform` | 미반영 | — |
| `RUNTIME_INTERNAL` | Runtime 내부 오류. 결과 볼륨에 쓰지 못한 경우 포함(§4.5) | `platform` | 미반영 | AI 파트에 알릴 것 |

**v2.0에서 사라진 실행 오류** — `AGENT_BOUNDARY_VIOLATION`(종단 노드 자체 검증과 같아짐), "입력 payload가 `input_schema_id`와 안 맞음"(입력 형식 폐지).

**오류 코드가 늘면 이 표를 갱신하고 공유한다.** 백엔드는 자기 봉투로 번역해 쓰면 된다.

---

## 10. 파트별 체크리스트 — 붙이는 순서

### 백엔드 — 이 순서로 붙이면 된다

| # | 단계 | 부르는 것 | 응답에서 읽는 것 |
| --- | --- | --- | --- |
| 1 | 노드 저장 | — | `type` · **`output {kind, example}`** · **진입 노드 `input {example}`** · `ref_files` · `owner_id`를 그대로 저장. 예시는 판매자가 적은 JSON 그대로. `common.*` 고정 조립을 **판매자가 적은 값 전달**로 바꾼다 |
| 2 | 워크플로우 저장 | **`POST /v1/workflows/validate`** | `valid`가 `false`면 `errors[]`를 사용자에게. **`estimate.worst_case_nodes`의 단가 합**을 최대 청구액으로 저장 |
| 3 | 에이전트 저장 | — | `agent.nodes` + `agent.edges`를 **그대로** 임베드. 공개 입출력은 진입 · 종단 노드에서 읽어 마켓 카드에 |
| 4 | 실행 요청 | **SubmitRun 발행** (`abc.runs` / `submit`, §4.3) — 접수 트랜잭션에 outbox 로 적고 디스패처가 보낸다 | confirm 을 받으면 `run_id` = `execution_id` 를 적는다. 선차감액은 2번의 값. `mode=test`면 선차감 없음 |
| 5 | 진행 | **`abc.events.backend` 소비** (RunEvent, §4.4) — DB 커밋 뒤 ack | `(attempt, seq)` 가 앞선 것만 적용. snapshot 의 `progress`를 프론트에 중계. `contribution[]`에 나타난 노드는 `(run_id, node_id)` 멱등 키로 **즉시 적립**(금액은 자기 단가) |
| 6 | 종료 | `succeeded` · `failed` · `rejected` 이벤트 | `not_executed[]` 환원, `failure.refund_scope`로 실패 구간 환원(§8.5). **`agents[]` · `totals`의 토큰 · USD로 Base Cost 보정.** 사용액을 실측으로 계산한다면 `nodes[].cost_usd` × 환산율 × 등급 배율(v1.x의 `cost_abc` 자리. `cost_abc`는 이제 오지 않아 그대로 두면 사용액 0이 된다) |
| 7 | 결과 저장 | **결과 볼륨** `<execution_id>/artifact.json` · `report.pdf` (§4.5) | 봉투 JSON과 PDF를 스토리지에 저장해 URL 발급. 이벤트의 경로는 믿지 않는다 |
| 8 | (없어짐) | `GET /v1/schemas` | v2.1에서 필요 없다 — 부르던 코드는 지워도 된다 |
| 9 | 테스트 실행 | SubmitRun + `mode=test` | 횟수 · 크레딧 한도는 접수에서. 발행이 confirm 되지 않으면 접수를 지운다. 결과의 토큰 · USD를 NOD-007 실측으로 |
| 10 | dead letter | **`abc.runs.dead` 소비** (§4.8) | `x-first-death-reason` 으로 실패 코드를 정해 플랫폼 탓으로 닫고 전액 환불 |

**감시를 반드시 둔다(§4.9).** `accepted` 뒤 마지막 이벤트에서 `노드 수 × 60초 + 60초`를 넘도록 이벤트가 없으면 `GET /v1/runs/{id}` 로 한 번 묻는다. `RUN_NOT_FOUND` 를 곧바로 실패로 닫지 않는다 — 큐 대기 한도(10분) + grace 안이면 다시 배달될 수 있다.

**정산에서 보는 필드는 셋뿐이다** — `contribution[]`(적립) · `not_executed[]`(미실행 환원) · `failure.refund_scope`(실패 환원). 금액은 전부 백엔드 단가표에서 나온다.

### 프론트 — 이 순서로 붙이면 된다

| # | 단계 | 쓰는 것 | 화면 |
| --- | --- | --- | --- |
| 1 | 노드 편집 | — | **출력 형식: "글" 또는 "JSON"**. JSON이면 **예시 JSON 입력칸**(키 · 값 타입이 규격, 값 `null` = 선택 항목). 진입 노드에만 "사용자 입력 예시" 입력칸. 참고 파일 업로드 |
| 2 | 캔버스 연결 | — | **어떤 노드 사이든 선이 그어진다.** 분할(한 출력 → 여러 노드) · 병합(여러 입력 → `port` 이름) · 분기 노드(`condition` · `when: true/false` 두 간선) |
| 3 | 분기 조건 | 상위 노드의 `output.example` | 기준 노드 목록에서 **글 출력 노드는 제외**, 필드 드롭다운은 예시의 문자 · 숫자 · 참거짓 필드(점 경로)만, 타입별 연산자 (§5.4 표) |
| 4 | 저장 | (백엔드 경유) `validate` 응답 | `errors[]`는 해당 `node_id`에 빨간 표시. `warnings[]`는 노란 안내. 최대 청구액은 **백엔드가 계산한 값**을 표시 |
| 5 | 실행 폼 | 진입 노드 `input.example` | 예시의 키가 입력란 이름, 값 타입이 입력 종류(문자 · 숫자 · 참거짓), 값이 `null`인 키는 선택 표시. 없으면 코멘트 + 파일만 |
| 6 | 진행 | (백엔드 SSE) RunEvent snapshot 의 `progress` · `nodes[]` | `percent` · `current_node_name`. `type: branch`는 "판정 → 예/아니오"(`branch_taken`), `status: skipped`는 "건너뜀 — 분기로 선택되지 않음". 판매자별 사용량은 `agents[]` |
| 7 | 결과 | `result.html_url` · `artifact_url` · `pdf_url` · `template` · `warnings[]` | **HTML을 화면에 띄운다**(iframe 또는 새 창). PDF 다운로드. `template.applied`가 `default`면 "기본 서식으로 만들었습니다" 안내, `warnings[]` 문구 표시 |
| 7-1 | 서식 등록 | `POST /v1/templates/convert` (백엔드 경유) | 디자인 업로드 → 돌아온 `html`을 미리보기(iframe) → `placeholders` 안내 → 저장. 실행 화면의 "서식 고르기"(플랫폼 기본 2종 · 판매자 제공 · 내 서식) |
| 8 | 실패 | `error.message` · `error.hint` | 그대로 표시하면 된다. `error.detail`은 표시하지 않는다 |
| 9 | 마켓 카드 | 에이전트의 진입 노드 `input` · 종단 노드 `output` | "입력: 브랜드 기획 요청서 / 출력: 브랜드 전략 보고서" 또는 "입력: 자유 / 출력: 글" |

**프론트는 AI Runtime을 직접 부르지 않는다.** 백엔드가 중계한다. 프론트 코드의 `/api/v1/executions/{id}/events`(SSE)는 백엔드가 RunEvent 를 받아 중계하는 것이라 그대로 쓰면 된다. 정의 · 입력 오류도 접수 응답이 아니라 실행 화면의 실패(`rejected`)로 보인다.

### 확인용 요청 — 서버에 붙이기 전에 손으로 한 번

```bash
# 1) 예시 워크플로우 목록
curl -s -H "X-ABC-Service-Token: $TOKEN" http://127.0.0.1:8000/v1/workflows | jq '.workflows[] | .id'

# 2) 저장 시점 검증 + 도는 노드 목록 (예시 워크플로우로)
curl -s -H "X-ABC-Service-Token: $TOKEN" http://127.0.0.1:8000/v1/workflows/brand_agent \
  | jq '{workflow: .}' \
  | curl -s -H "X-ABC-Service-Token: $TOKEN" -H "Content-Type: application/json" -d @- \
         http://127.0.0.1:8000/v1/workflows/validate | jq '.valid, .estimate'

# 3) 테스트 실행 (mock) — 관리 API 로 SubmitRun 을 직접 넣고 결과를 HTTP 로 본다
#    백엔드는 모르는 execution_id 의 이벤트를 경고 로그만 남기고 버린다
RUN=$(cat /proc/sys/kernel/random/uuid)
WF=$(curl -s -H "X-ABC-Service-Token: $TOKEN" http://127.0.0.1:8000/v1/workflows/brand_agent)
jq -n --arg id "$RUN" --argjson wf "$WF" '{
    properties: {content_type: "application/json", delivery_mode: 2, type: "SubmitRun", message_id: $id},
    routing_key: "submit", payload_encoding: "string",
    payload: ({execution_id: $id, mode: "test", workflow: $wf, mock: true, comment: null, files: [], ref_files: {},
               input: {brand_name: "초록소반", category: "간편식", target: "1인 가구 30대", price_tier: "중가", differentiator: "국내산 재료"},
               submitted_at: (now | todate)} | tojson)}' \
  | curl -s -u "$RABBITMQ_USERNAME:$RABBITMQ_PASSWORD" -H "Content-Type: application/json" -d @- \
         http://127.0.0.1:15672/api/exchanges/%2F/abc.runs/publish        # {"routed":true}
curl -s -H "X-ABC-Service-Token: $TOKEN" http://127.0.0.1:8000/v1/runs/$RUN | jq '.status, .mode, .agents, .totals, .contribution'
curl -s -H "X-ABC-Service-Token: $TOKEN" http://127.0.0.1:8000/v1/runs/$RUN/artifact | jq '.schema_id, .meta'
```

## 11. 노드 사이에서 실제로 일어나는 일

백엔드가 알 필요는 없지만 디버깅할 때 도움이 된다.

```
실행된 상위 노드들의 결과 (하나 또는 {port: 결과})
   │
   ├─ ① 입력 상한 검사 — 직렬화 길이 > MAX_INPUT_CHARS 면 INPUT_TOO_LARGE (platform)
   ├─ ② 프롬프트 조립 — 프롬프트 원문 + "## 입력" 블록(앞 결과 전체, 절마다 노드 이름 · 형식) + 코멘트 + 참고 파일(inline)
   ├─ ③ LLM 호출 — json 이면 구조화 출력(response_schema) 1차, 지시 + 검증 fallback. text 면 그냥 호출
   ├─ ④ 출력 검사 — json 이면 output.example 의 키 · 타입으로 검사. 틀리면 "형식 고쳐서 다시" 최대 2회. text 는 검사 없음
   ├─ ⑤ 봉투 조립 — Runtime이 a2a_version · run_id · meta(토큰 · USD) 를 씌운다
   └─ ⑥ 다음 노드로 — payload(+ 코멘트)만. 봉투는 넘기지 않는다
```

**LLM은 payload만 만든다.** 봉투 · 토큰 집계 · USD 환산 · 마스킹은 전부 Runtime이 한다. **입력 검증은 없다** — 다음 노드의 프롬프트가 앞 결과를 읽는 것이 곧 "변환"이다.
**프롬프트 원문은 응답 · 로그 · 예외 어디에도 남기지 않는다**(SEC-001).

---

## 12. 함정 — 미리 알아야 할 것

| 함정 | 내용 | 대응 |
| --- | --- | --- |
| **실행 상태가 메모리에만 있다** | 재배포하면 진행 중이던 `run_id` 조회가 404. ack 전이던 실행은 큐가 다시 배달해 **처음부터 한 번 더 돈다** | 이력 원본은 백엔드 DB. 배포는 실행이 없는 시점에 — 다시 돌면 LLM 비용이 한 번 더 든다 |
| **단일 프로세스다** | 워커를 늘리면 조회가 다른 워커로 가서 실패 | 워커 1개로 고정 운영 |
| **동시 실행 수는 prefetch 다** | `MAX_ACTIVE_RUNS`(기본 4)를 넘는 실행은 큐에서 기다린다 | 10분을 넘기면 dead letter → `ABC_QUEUE_EXPIRED` 로 전액 환불. 사용자별 · 테스트 횟수 제한은 여전히 백엔드가 |
| **같은 상태 · 같은 이벤트가 여러 번 온다** | `progress` 마다 스냅샷 전체, 재배달 · 재발행. 그대로 적립하면 중복 정산 | `(attempt, seq)` 로 앞선 것만 적용하고 `(run_id, node_id)` 멱등 키로 적립 |
| **두 compose 가 볼륨을 나눠 쓴다** | Runtime 은 백엔드가 만든 `file-store`(읽기 전용) · `results` 를 `external` 로 마운트한다 | 백엔드를 먼저 배포한다. 마운트가 어긋나면 `rejected` `FILE_PATH_REJECTED` |
| **`branch` 노드는 `progress.total`에 안 들어간다** | 실행 대상이 아니다 | 진행률은 응답의 `total`을 그대로 쓴다 |
| **분기로 안 간 노드도 환원 대상이다** | `not_executed[].reason` 확인 | PAY-010 |
| **어떤 노드든 이어진다** | 문맥이 안 맞는 조합도 실행된다. 실패가 아니다 | 품질은 평점 · 리뷰. 사용자 안내는 프론트 몫 |
| **글 출력 노드는 분기 기준이 될 수 없다** | 비교할 필드가 없다 | `validate`가 `BRANCH_ON_TEXT_OUTPUT`으로 막는다 |
| **`cost_usd`는 ABC가 아니다** | 화면에 그대로 띄우면 안 된다 | 백엔드가 환산한 값만 표시 |
| **LLM은 매번 다른 글을 쓴다** | 같은 입력이라도 문장이 달라진다 | 정상이다. 모양은 출력 예시로 고정된다 |
| **예시는 선택지 · 개수를 강제하지 못한다** | `competition_level`에 `"medium"`이 오거나 목록이 6개일 수 있다 | 프롬프트로 지시한다. 분기는 대소문자 · 공백만 무시한다 |
| **GMS 크레딧은 팀 공용이다** | 개발 중 반복 실행이 크레딧을 소모 | 형식 맞추기 단계는 반드시 `mock=true` |

---

## 13. 실측값 (참고)

GMS 경유 · JSON 구조화 출력. 아래 09-22 · 09-25 · 2단계 표는 **`gemini-2.5-flash`** 실측이고, **09-26부터 기본 모델은 `gemini-2.5-pro`** — Pro 실측은 맨 끝 표. USD는 공시 단가 환산값이다.
자유 텍스트 시절(09-09, 노드당 약 25초 · 실행당 토큰 약 5,800)보다 노드당 시간 · 토큰이 절반으로 줄었다 — 형식이 정해지니 모델이 짧게 답한다.

| 항목 | 값 (2회 평균) |
| --- | --- |
| 실행 전체 | **약 51초** (48.6 · 53.3) |
| 노드 1개 | 9 ~ 16초 (평균 12.6초) |
| 실행당 토큰 | 입력 약 4,700 / 출력 약 2,200 |
| 실행당 원가 `totals.cost_usd` | **약 0.0068 USD** (0.0070 · 0.0066) |
| 노드당 원가 | 0.0013 ~ 0.0021 USD |
| 교정 재요청 `repair_count` | 8노드 실행 중 **0회** |
| `mock=true` | 0.3초, 원가 0 |

노드별 (소소칫솔 · 밤길동무 순):

| 노드 | 시간 | 입력 / 출력 토큰 | 원가 USD |
| --- | --- | --- | --- |
| n1 시장 조사 | 13.6s · 16.3s | 867 / 633 · 872 / 564 | 0.00184 · 0.00167 |
| n2 제품 분석 | 10.9s · 8.9s | 695 / 496 · 700 / 417 | 0.00145 · 0.00125 |
| br1 분기 | 0s | 0 | 0 (코드 판정) |
| n3a 차별화 전략 | 14.4s · 15.2s | 1,620 / 514 · 1,494 / 482 | 0.00177 · 0.00165 |
| n4 보고서 | 9.3s · 12.5s | 1,607 / 584 · 1,575 / 635 | 0.00194 · 0.00206 |

감시 기한(§4.9 · §10, 옛 폴링 중단 기준)의 "노드 수 × 60초"는 이 수치의 약 3배 여유다. 5분(PER-003)은 최장 경로 8노드에서도 넉넉하다.

**v2.0 재측정 — 09-25 · 앞 결과 전체 전달 · 1회(소소칫솔 · high 경로)**

| 항목 | 값 |
| --- | --- |
| 실행 전체 | **52.9초** · `status: succeeded` · `mode: run` |
| 실행당 토큰 | 입력 4,670 / 출력 2,129 |
| `totals.cost_usd` | **0.006725** |
| `agents[]` | 1건 — `u_seller_a` · `node_ids` 5 · 0.006725 USD |
| 교정 `repair_count` | 0회 |
| 노드별 | n1 15.2s · 849/618 · 0.0018 — n2 10.0s · 677/357 · 0.001096 — br1 0s — n3a 16.0s · 1,515/536 · 0.001795 — n4 11.5s · 1,629/618 · 0.002034 |

09-22와 같다. 앞 결과 전체를 넘겨도 입력 토큰이 늘지 않은 것은 v1.x도 payload 전체를 넘기고 있었기 때문이고, v2.0에서 바뀐 것은 입력 블록의 라벨 절(`## 입력` · `### 이름 (id · 견본)`)뿐이다. 최대 노드 입력 1,629 토큰으로 `MAX_INPUT_CHARS`(≈100k 토큰)의 2% 수준.

**2단계 실측 — 09-26 · `brand_campaign`(에이전트 4 · 판매자 3 · LLM 노드 7 실행 + 분기 1) · 1회 · 참고 PDF 1개(63KB) 첨부**

| 항목 | 값 |
| --- | --- |
| 실행 전체 | **107.2초** · `succeeded` · 교정 0회 |
| 실행당 토큰 | 입력 9,947 / 출력 4,069 |
| `totals.cost_usd` | **0.013157** |
| `agents[]` | 4건 — `ag_brand_strategy`(u_seller_a · 노드 5 · $0.007051) · `ag_sns_campaign`(u_seller_b · $0.002) · `ag_ad_copy`(u_seller_c · $0.001328) · `ag_execution_plan`(u_seller_b · $0.002778) |
| `contribution[]` | 판매자 3계정. `not_executed` 1(`a1.n3a` · branch_not_taken) |
| 노드별 | a1.n1 19.7s · 1,610/623 — a1.n2 10.3s · 681/462 — a1.n3b 15.6s · 1,644/457 — a1.n4 12.5s · 1,550/620 — a2.c1 17.5s · 1,342/639 — a3.k1 13.1s · 1,460/356 — a4.e1 18.2s · 1,660/912 |
| 참고 PDF inline | `a1.n1`에 63KB PDF 첨부 → 입력 1,610 토큰(첨부 없던 09-25 849 대비 **약 760 토큰**). GMS 프록시 통과 확인 |

노드당 10 ~ 20초 · $0.0013 ~ 0.0028. 9노드 실행이 2분 안이라 5분(PER-003)은 전개 상한 20노드에서도 여유가 있다. 참고 PDF는 크기에 비례해 입력 토큰을 늘리므로(63KB ≈ 760 토큰) 노드당 10MB 상한은 토큰 기준으로도 안전하다.

**`gemini-2.5-pro` 실측 — 09-26 · `brand_agent` · 트랙 B 시연 브리프(소소칫솔) · 같은 브리프를 Flash와 나란히**

| 항목 | Flash | **Pro (기본)** |
| --- | --- | --- |
| 실행 전체 | 50.4초 | **88.1초** (1.75배) |
| 노드 | n1 12.2s · n2 10.2s · n3 14.2s · n4 13.4s | n1 32.0s · n2 16.0s · n3 19.2s · n4 20.7s |
| 토큰 | 4,940 / 2,140 | 4,973 / 2,242 |
| `totals.cost_usd` | 0.006832 | **0.028636** (4.2배) |
| 교정 | 0 | 0 |
| 분기 판정 | 경쟁사 4 → **low** | 경쟁사 5 → **high** |
| 추가 (09-26 오후, 프롬프트 01 판정 대상을 오프라인 일반 매장으로 좁힌 뒤) | — | 클라이밍 테이프 브리프 **low** · 참고 파일 md + PDF 첨부 1회 86.7초 · $0.029 / 노드 01 단독 high 3/3 · low 3/3 |
| **v2.1 (09-26 저녁, 견본 삭제 · 출력 JSON 예시)** | — | 칫솔 **high**(경쟁사 5) 79.7초 · $0.0278 / 클라이밍 테이프 **low**(경쟁사 3) 79.7초 · $0.0248 — **둘 다 교정 0회.** 견본의 선택지 · 개수 제약 없이도 예시 모양 강제와 프롬프트 지시만으로 분기가 기대대로 갈렸다 |
| **v2.2 서식 (09-27)** | — | 디자인 PDF(2쪽) → 서식 변환 **105초 · 5,142/6,212 토큰 · 약 $0.07**(교정 1회 포함, 빈칸 24개) / 실행 끝 짝짓기 **20 ~ 25초 · 약 $0.01**, 24개 중 21개를 경로로 배치(3개는 결과에 없는 디자인 고정 칸 → 빈칸). 짝짓기 시간은 실행 시간에 더해진다 |
| **v2.2 E2E — API 경로 그대로 (09-27)** | — | 사용자 캔바 디자인(1쪽 PDF) → `/templates/convert` **69초**(교정 1회, 빈칸 10) → `POST /v1/runs` 1단계 + 칫솔 사진 + 참고 파일 3 + 그 서식: **105초 · $0.031**, 빈칸 **10/10** 배치, 제품 분석 · 광고 카피가 사진의 색(초록 손잡이 · 분홍 칫솔모)을 근거로 씀 → 2단계 판매자 3: **161초 · $0.057** |

Pro는 노드당 최대 32초라 폴링 중단 기준(지금은 감시 기한, §4.9)을 **노드 수 × 60초**로 올렸다(§10). 2단계 9노드는 약 3분으로 예상되며 5분(PER-003) 안이다. 같은 칫솔 브리프에서 Flash가 low, Pro가 high를 낸 것은 `competitors` 개수(4 vs 5)의 차이 — 시연 전 Pro로 `--runs 10` 준수율을 다시 잰다(트랙 B).

---

## 14. 막히면

| 증상 | 먼저 확인할 것 |
| --- | --- |
| 401 | 백엔드 컨테이너의 `ABC_SERVICE_TOKEN`이 GitLab Variables 값과 같은지 |
| 연결 안 됨 | 같은 도커 네트워크(`ai-block-clock_ai-block-clock-network`)에 있는지 |
| 실행이 `QUEUED` 에서 움직이지 않음 | Runtime `/v1/health` 의 `broker` 가 `connected` 인지, 관리 UI 에서 `abc.runs.submit` 에 컨슈머가 붙어 있는지. 양쪽 `RABBITMQ_USERNAME` · `RABBITMQ_PASSWORD` 가 같은지 |
| 404 `RUN_NOT_FOUND` | Runtime이 재배포됐는지(이전 `run_id`는 사라진다), 아직 큐에서 기다리는 중인지 |
| `rejected` `INVALID_WORKFLOW` | snapshot `error.errors` 배열에 어느 노드의 무엇이 문제인지 있다 |
| `rejected` `FILE_PATH_REJECTED` | Runtime 의 file-store 마운트가 백엔드와 같은 볼륨(`ai-block-clock_file-store`)인지 |
| `abc.events.backend.dlq` 에 메시지가 쌓임 | 백엔드가 네 번 처리하고도 실패한 이벤트다. 백엔드 로그를 본다. 그 실행은 감시가 닫는다 |
| `DEPRECATED_FIELD` 경고가 계속 뜸 | 노드 저장 형식을 v2.1(`output {kind, example}`)로 바꿨는지 |
| 저장은 되는데 실행에서 튕김 | `POST /v1/workflows/validate`를 저장 시점에 부르고 있는지 |
| `contribution`이 비어 옴 | `mode=test`로 보냈는지 |
| 응답이 느림 | 정상이다. 노드당 15 ~ 35초(Pro). `mock=true`로 확인 |
| 그 외 | `RUNTIME_INTERNAL`이 보이면 AI 파트에 알린다 |
