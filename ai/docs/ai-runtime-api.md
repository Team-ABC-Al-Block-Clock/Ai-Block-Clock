# AI Runtime API — 백엔드 · 프론트 연동 계약

| 항목 | 내용 |
| --- | --- |
| 버전 | **v0.2** (선형 워크플로우 스파이크 + 정산 입력값) |
| 작성일 | 2026-09-09 |
| 작성 | AI 파트 (김정윤) |
| 대상 | 백엔드(Spring), 프론트엔드(React) |
| 구현 위치 | `ai/spike/linear-mvp/src/api.py` |
| 대응 Jira | S15P21A403-171 |
| 상태 | **구현 완료 · 동작 확인됨.** 실제 Gemini 호출 5회, 정산 필드는 성공 · 실패 케이스로 검증 |

### 개정 이력

| 버전 | 변경 |
| --- | --- |
| v0.1 | 최초. 실행 요청 · 진행 조회 · PDF 다운로드 |
| **v0.2** | 정산 입력값 추가. 요청에 `owner_id` · `unit_price`, 응답에 `contribution` · `not_executed` · `failure.fault`. 파일명을 버전 없는 이름으로 변경 |

> 이 문서는 규격(A2A · H2A)과 분기를 도입하지 않기로 한 팀 결정(09-08)에 따른 **최소 계약**이다. 노드를 순서대로 실행해 마지막 산출물을 PDF로 렌더링하고, 정산에 필요한 값을 돌려주는 것까지 다룬다. 스키마 검증과 분기는 이 버전에 없다.

---

## 1. 전체 구조

```
 [프론트]                [백엔드 Spring]                  [AI Runtime FastAPI]         [Gemini]
    │                          │                                  │                       │
    │ ① 실행 요청 (파일+코멘트) │                                  │                       │
    ├─────────────────────────▶│                                  │                       │
    │                          │ ② POST /v1/runs (multipart)      │                       │
    │                          ├─────────────────────────────────▶│                       │
    │                          │◀── 202 { run_id } 즉시 ──────────┤                       │
    │◀── 실행 시작 (run_id) ────┤                                  │ 백그라운드 실행 시작   │
    │                          │                                  │   노드1 ─────────────▶│
    │                          │ ③ GET /v1/runs/{id}  (2초 간격)   │   노드1 ◀─────────────┤
    │  진행 상황 표시           ├─────────────────────────────────▶│   노드2 ─────────────▶│
    │◀── SSE 또는 폴링 ─────────┤◀── { status, progress, nodes } ──┤   노드2 ◀─────────────┤
    │                          │                                  │   마크다운 → PDF       │
    │                          │ ④ GET /v1/runs/{id}/pdf          │                       │
    │                          ├─────────────────────────────────▶│                       │
    │◀── 결과 전달 ─────────────┤◀── application/pdf ──────────────┤                       │
```

**중요한 두 가지**

1. **프론트는 AI Runtime을 직접 호출하지 않는다.** 프론트는 백엔드만 부르고, 백엔드가 AI Runtime을 부른다.
2. **실행 한 번에 호출 한 번이다.** 노드마다 왕복하지 않는다. 노드 사이의 데이터는 AI Runtime 프로세스 안에서만 넘어간다.

**왜 202 + 조회 방식인가.** 노드 하나가 평균 25초 걸린다. 노드 3개면 75초로 nginx와 ALB의 기본 타임아웃 60초를 넘긴다. 동기 호출은 연결이 끊기면 이미 지불한 API 비용과 실행이 통째로 사라진다. 202로 즉시 응답하고 조회로 진행 상황을 받으면 타임아웃이 없고, 프론트의 실행 진행 화면에 "2단계 중 1단계 진행 중"을 그대로 표시할 수 있다.

---

## 2. 공통 사항

| 항목 | 값 |
| --- | --- |
| Base URL (로컬 개발) | `http://localhost:8000` |
| Base URL (EC2) | 배포 후 확정. 외부 노출 없이 내부 포트로 두고 백엔드만 접근 |
| 인증 | 헤더 `X-ABC-Service-Token: <공유 시크릿>`. 환경변수 `ABC_SERVICE_TOKEN`이 비어 있으면 검사를 건너뛴다(로컬 개발 기본값) |
| 인코딩 | 요청 · 응답 모두 UTF-8 |
| Swagger | `http://localhost:8000/docs` 에서 직접 호출해 볼 수 있다 |

**오류 응답 형식.** 모든 4xx · 5xx는 아래 형태다.

```jsonc
{ "detail": { "code": "INVALID_WORKFLOW", "message": "워크플로우 정의 오류", "errors": ["nodes[0].prompt 가 비어 있습니다"] } }
```

| HTTP | code | 의미 | 백엔드 처리 |
| --- | --- | --- | --- |
| 400 | `INVALID_WORKFLOW_JSON` | workflow 필드가 JSON이 아님 | 요청 오류. 재시도 무의미 |
| 400 | `INVALID_WORKFLOW` | 노드 정의 오류. `errors` 배열에 상세 | 사용자에게 안내 |
| 400 | `UNSUPPORTED_FILE_TYPE` | 허용 확장자 아님 (`.xlsx` `.xls` `.csv`) | 사용자에게 안내 |
| 400 | `FILE_TOO_LARGE` | 10MB 초과 | 사용자에게 안내 |
| 401 | `UNAUTHORIZED` | 서비스 토큰 불일치 | 설정 확인 |
| 404 | `RUN_NOT_FOUND` | run_id 없음. 서버 재시작 시에도 발생 | 실행 이력을 DB에서 조회 |
| 409 | `PDF_NOT_READY` | 아직 실행 중이거나 실패함 | status 확인 후 재시도 |

---

## 3. 워크플로우 정의 형식

백엔드가 실행 요청에 담아 보내는 JSON이다. **프론트 캔버스가 만들어 백엔드 DB에 저장할 형식이기도 하다.**

```jsonc
{
  "id": "report",                          // 선택. 로그 식별용
  "name": "학원 성적 리포트",                // 선택
  "model": null,                           // 선택. null 이면 서버 기본값(gemini-2.5-flash)
  "nodes": [
    { "id": "analyze",                     // 필수. 워크플로우 안에서 유일해야 한다
      "name": "성적 분석",                  // 선택. 진행 화면에 표시되는 이름
      "prompt": "당신은 학원 성적 데이터 분석가입니다. ...",   // 필수. 판매자가 작성한 프롬프트 원문
      "context": "user",                   // 선택. 기본값 "prev"

      // ── 정산 입력값 (v0.2). 없으면 응답의 정산 필드가 null 로 채워진다 ──
      "owner_id": "u_2001",                // 이 노드의 원작자. 정산금을 받을 계정
      "unit_price": 3.9,                   // 백엔드가 등급을 반영해 확정한 실행 단가(ABC)
      "base_cost": 3.0,                    // 참고용
      "premium_rate": 0.30 },              // 참고용
    { "id": "letter",
      "name": "가정통신문 작성",
      "prompt": "당신은 교육 심리 전문가입니다. ...",
      "context": "prev",
      "owner_id": "u_3001", "unit_price": 4.0, "base_cost": 4.0, "premium_rate": 0.0 }
  ],
  "output": { "type": "pdf", "title": "3월 성적 리포트" }   // type: "pdf" | "markdown"
}
```

**등급과 단가는 AI Runtime이 계산하지 않는다.** 백엔드의 등급 산정 배치가 확정한 값을 요청에 실어 보내면, Runtime은 **실제로 실행된 노드에만 그 값을 붙여 되돌려준다.** 정산 계산과 장부 기록은 백엔드 몫이다.

**프롬프트 전달 방식은 미결이다.** 지금은 원문을 그대로 받는다. 남의 에이전트를 조합한 워크플로우에서는 백엔드 로그와 DB 덤프에 타인의 프롬프트가 남아 SEC-001(프롬프트 비노출)에 걸린다. 마켓 조합을 실제로 열기 전에 `prompt_ref`(참조 ID)만 넘기고 Runtime이 직접 조회하는 방식으로 바꿔야 한다.

**`nodes` 배열의 순서가 곧 실행 순서다.** 엣지나 그래프 개념은 이 버전에 없다.

### `context` — 이 노드가 무엇을 입력으로 받는가

| 값 | 받는 것 | 쓰는 자리 |
| --- | --- | --- |
| `"user"` | 사용자 입력(코멘트 + 업로드 파일을 표로 변환한 텍스트) | 첫 노드 |
| `"prev"` (기본) | **직전 노드의 출력 텍스트만** | 두 번째 이후 노드 |
| `"prev+user"` | 직전 노드 출력 + 원본 사용자 입력 | 원본 데이터를 다시 봐야 하는 노드 |

첫 노드는 `context` 값과 무관하게 사용자 입력을 받는다(앞 노드가 없으므로).

### 검증 규칙

요청 시점에 아래를 검사하고 위반하면 400을 반환한다.

- `nodes`는 1개 이상의 배열
- 각 노드의 `id`는 비어 있지 않고 중복되지 않음
- 각 노드의 `prompt`는 비어 있지 않음
- `context`는 `user` · `prev` · `prev+user` 중 하나
- `output.type`은 `pdf` 또는 `markdown`

---

## 4. 엔드포인트

### 4.1 `POST /v1/runs` — 실행 요청

**요청.** `multipart/form-data`

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `workflow` | string | 필수 | 3장의 워크플로우 정의를 JSON **문자열**로 |
| `comment` | string | 선택 | 사용자가 입력한 자연어 코멘트 |
| `file` | file | 선택 | 성적 엑셀 또는 CSV. `.xlsx` `.xls` `.csv`, 최대 10MB |
| `mock` | boolean | 선택 | `true`면 LLM을 호출하지 않고 파이프라인만 실행. 기본 `false` |

**응답.** `202 Accepted`

```jsonc
{
  "run_id": "20260909-100451-688",
  "status": "queued",
  "node_count": 2,
  "poll_url": "/v1/runs/20260909-100451-688",
  "poll_interval_ms": 2000
}
```

**curl 예시**

```bash
curl -X POST http://localhost:8000/v1/runs \
  -F "workflow=$(cat workflow.json)" \
  -F "comment=이번 달은 전반적으로 시험 난이도가 높았음" \
  -F "file=@성적표.xlsx"
```

> 개발 초기에는 `GET /v1/workflows/report`로 견본 워크플로우를 받아 그대로 `workflow` 필드에 넣으면 바로 테스트할 수 있다.

### 4.2 `GET /v1/runs/{run_id}` — 진행 상황 · 결과 조회

백엔드가 **2초 간격**으로 호출한다. `status`가 `succeeded` 또는 `failed`가 되면 중단한다.

**쿼리 파라미터.** `include_node_output=true`를 붙이면 각 노드의 출력 전문이 함께 온다. 기본은 제외(응답 크기 절약).

**응답 예시 (실행 중)**

```jsonc
{
  "run_id": "20260909-100451-688",
  "status": "running",                     // queued | running | succeeded | failed
  "workflow": { "id": "report", "name": "학원 성적 리포트", "node_count": 2 },
  "mock": false,
  "progress": {
    "done": 1,                             // 완료된 노드 수
    "total": 2,
    "percent": 50,
    "current_index": 1,                    // 지금 도는 노드의 0-based 인덱스. 없으면 null
    "current_node_id": "letter",
    "current_node_name": "가정통신문 작성"    // ← 프론트 진행 화면에 그대로 표시
  },
  "nodes": [
    { "id": "analyze", "name": "성적 분석", "status": "succeeded", "context": "user",
      "duration_ms": 28900, "input_tokens": 1288, "output_tokens": 1344, "retries": 0,
      "owner_id": "u_2001", "unit_price": 3.9 },
    { "id": "letter", "name": "가정통신문 작성", "status": "running", "context": "prev",
      "duration_ms": null, "input_tokens": null, "output_tokens": null, "retries": 0,
      "owner_id": "u_3001", "unit_price": 4.0 }
  ],
  "totals": { "input_tokens": 1288, "output_tokens": 1344 },
  "started_at": "2026-09-09T01:04:51+00:00",
  "finished_at": null,
  "total_duration_ms": null,
  "contribution": [ { "node_id": "analyze", "owner_id": "u_2001", "charged_abc": 3.9 } ],
  "not_executed": [],
  "failure": null,
  "result": null,
  "error": null
}
```

`contribution`은 **성공한 노드가 늘어날 때마다 즉시 늘어난다.** 실행이 끝나기를 기다리지 않는다.

**응답 예시 (완료)**

```jsonc
{
  "status": "succeeded",
  "progress": { "done": 2, "total": 2, "percent": 100, "current_index": null,
                "current_node_id": null, "current_node_name": null },
  "finished_at": "2026-09-09T01:05:38+00:00",
  "total_duration_ms": 46500,
  "totals": { "input_tokens": 3632, "output_tokens": 2268 },
  "result": {
    "type": "pdf",
    "markdown": "# 3월 학습 안내\n\n학부모님께, ...",     // 최종 산출물 원문
    "pdf_url": "/v1/runs/20260909-100451-688/pdf"
  },
  "error": null
}
```

**응답 예시 (실패)**

```jsonc
{
  "status": "failed",
  "nodes": [ { "id": "analyze", "status": "succeeded", "...": "..." },
             { "id": "letter",  "status": "failed",    "...": "..." } ],
  "contribution": [ { "node_id": "analyze", "owner_id": "u_2001", "charged_abc": 3.9 } ],
  "not_executed": [],
  "failure": {
    "node_id": "letter",
    "error_code": "MODEL_API_ERROR",
    "fault": "supplier",                   // supplier | consumer | platform
    "affects_success_rate": true,          // 이 노드 공급자의 성공률에 실패로 집계할지
    "refund_scope": {
      "refund_node_ids":  ["letter"],      // 수요자에게 환원할 노드
      "settled_node_ids": ["analyze"]      // 정산을 유지할 노드
    }
  },
  "result": null,
  "error": { "code": "MODEL_API_ERROR", "message": "…", "node_id": "letter" }
}
```

**노드 상태값.** `pending` → `running` → `succeeded` 또는 `failed`

**실행 오류 코드**

| code | 의미 | `fault` | 성공률 반영 | 재시도 |
| --- | --- | --- | --- | --- |
| `MODEL_TIMEOUT` | 노드 60초 초과 | `supplier` | 반영 | 가능 |
| `MODEL_API_ERROR` | 모델 API 5xx 또는 기타 오류 | `supplier` | 반영 | 가능 |
| `FILE_PARSE_FAILED` | 업로드 파일을 읽지 못함 | `consumer` | 미반영 | 무의미 |
| `RATE_LIMITED` | 무료 등급 요청 상한. 10초 간격 2회 재시도 후 최종 실패 | `platform` | 미반영 | 잠시 후 |
| `PDF_RENDER_FAILED` | 마크다운을 PDF로 그리는 중 실패 | `platform` | 미반영 | 무의미 |
| `LLM_INIT_FAILED` | API 키 없음 등 설정 오류 | `platform` | 미반영 | 무의미 |
| `RUNTIME_INTERNAL` | Runtime 내부 오류 | `platform` | 미반영 | 무의미. AI 파트에 알릴 것 |

### 4.3 `GET /v1/runs/{run_id}/pdf` — 결과 PDF

`status`가 `succeeded`이고 `output.type`이 `pdf`일 때 `application/pdf`를 반환한다. 아직 준비되지 않았으면 409.

백엔드는 이 파일을 받아 자체 스토리지에 저장하고, 프론트에는 백엔드가 발급한 URL을 준다. AI Runtime의 `out/` 디렉터리는 임시 저장소이므로 장기 보관용으로 쓰지 않는다.

### 4.4 보조 엔드포인트

| 엔드포인트 | 용도 |
| --- | --- |
| `GET /v1/health` | 상태 확인. `{ status, auth_required, runs_in_memory }` |
| `GET /v1/workflows` | 서버에 있는 견본 워크플로우 목록 |
| `GET /v1/workflows/{id}` | 견본 워크플로우 정의 전문. 백엔드가 복사해 테스트용으로 쓴다 |

---

## 5. 정산 연동

### 돈이 움직이는 네 지점

| 시점 | 무슨 일 | 누가 실행 | AI가 주는 근거 |
| --- | --- | --- | --- |
| 실행 시작 | 노드 단가 합계를 선차감(예치) | 백엔드 | 없음. 백엔드가 이미 단가를 안다 |
| 노드 성공 | 그 노드 원작자에게 정산금 즉시 적립 | 백엔드 | `nodes[i].status` 변화 + `contribution` |
| 노드 실패 | 책임에 따라 적립 보류, ABC 환원 | 백엔드 | `failure.fault` + `refund_scope` |
| 실행 종료 | 미실행 구간 환원, 정산 확정 | 백엔드 | `not_executed` |

MVP는 플랫폼 수수료 0%라, 차감한 ABC 전액이 그대로 각 노드 원작자에게 간다.

### 노드 단위 즉시 적립을 폴링으로 처리하는 법

기획서는 "노드가 성공할 때마다 실시간 적립"을 요구한다. 콜백 없이 폴링만으로 된다.

1. 2초마다 `GET /v1/runs/{run_id}`를 조회한다
2. `nodes[i].status`가 `succeeded`로 바뀐 노드를 찾는다
3. 그 노드의 `owner_id`와 `unit_price`로 정산금을 적립한다

**멱등성은 백엔드가 지켜야 한다.** 폴링은 같은 상태를 여러 번 본다. 이미 적립한 `(run_id, node_id)`를 기록해 두 번 적립하지 않게 한다. `contribution` 배열을 그대로 써도 되는데, 이 배열도 성공한 노드가 늘어날 때마다 커지므로 같은 주의가 필요하다.

### 환원 범위 규칙

| `fault` | 환원 대상 | 정산 유지 | 공급자 등급 |
| --- | --- | --- | --- |
| `supplier` | 실패 노드 + 이후 미실행 구간 | 앞서 성공한 노드 | 성공률에 실패로 집계 |
| `consumer` | 실패 노드 + 이후 미실행 구간 | 앞서 성공한 노드 | 영향 없음 |
| `platform` | **전체 노드** | 앞서 성공한 노드 | 영향 없음 |

`platform`일 때만 환원액과 정산액의 합이 차감액을 넘는다. 플랫폼 과실로 공급자가 손해를 보지 않게 하려는 것이고, 차액은 플랫폼이 부담한다는 뜻이다.

### ⚠️ 이 버전에서 판정할 수 없는 것

기획서 6-③은 책임 판정을 **A2A 스키마 검증 결과**로 하도록 정의한다. 이 버전에는 스키마가 없어서 아래가 성립하지 않는다.

> **조합 오류(수요자 책임)를 탐지할 수 없다.** 앞 노드의 출력이 뒤 노드에 맞지 않아도, 자유 텍스트에서는 뒤 노드가 입력을 거부하지 않는다. 그냥 품질이 낮은 결과를 내고 성공으로 끝난다. 실패로 잡히지 않으니 환원할 근거도, 수요자 책임이라 판정할 근거도 없다.

따라서 지금 실제로 나오는 `fault`는 `supplier`와 `platform` 두 가지뿐이고, `consumer`는 파일 파싱 실패에서만 나온다. 발표에서 "실패 책임 자동 판정"을 다룰 때 이 범위를 정확히 말해야 한다. 스키마를 도입하면 판정 로직만 정교해지고 이 계약의 필드 구조는 그대로 간다.

### 실측 원가

`nodes[i].input_tokens`와 `output_tokens`가 그 노드의 실측 사용량이다. Base Cost를 주기적으로 보정하는 근거로 쓴다(NOD-007). **토큰을 ABC로 환산하는 비율은 아직 정하지 않았다.** 모델별 단가표를 확정하면 Runtime이 `cost_abc`를 함께 반환하도록 추가한다.

---

## 6. 파트별 할 일

### 백엔드

1. 실행 요청을 받으면 DB의 워크플로우 정의를 3장 형식의 JSON으로 만들어 `POST /v1/runs`에 multipart로 보낸다
2. 받은 `run_id`를 실행 이력에 저장하고 프론트에 돌려준다
3. 2초 간격으로 `GET /v1/runs/{run_id}`를 조회한다. `progress`와 `nodes`를 프론트에 전달한다(SSE 또는 프론트 폴링, 백엔드 재량)
4. `succeeded`가 되면 `GET /v1/runs/{run_id}/pdf`로 파일을 받아 스토리지에 저장한다
5. `failed`면 `error.code`와 `error.node_id`를 기록하고 사용자에게 안내한다

조회 중단 조건을 반드시 두어야 한다. 상태가 10분 이상 바뀌지 않으면 실패로 처리할 것을 권한다.

### 프론트엔드

프론트가 AI Runtime과 직접 주고받는 것은 없다. 아래 두 가지 형식만 맞추면 된다.

1. **캔버스가 만드는 워크플로우 JSON** — 3장의 형식. 노드 배열 순서가 실행 순서이고, 각 노드는 `id` · `name` · `prompt` · `context`를 갖는다
2. **진행 화면에 표시할 값** — 백엔드가 전달하는 `progress.done` / `progress.total` / `progress.percent` / `progress.current_node_name`, 그리고 `nodes[].status`로 단계별 체크 표시

---

## 7. 이 버전의 한계

| 한계 | 내용 |
| --- | --- |
| 실행 상태가 메모리에만 있다 | Runtime을 재시작하면 `run_id` 조회가 404가 된다. 실행 이력의 원본은 백엔드 DB여야 한다 |
| 단일 프로세스 | 워커 여러 개로 늘리면 조회가 다른 워커로 가서 실패한다. 스파이크는 단일 프로세스로 운영 |
| 동시 실행 제한 없음 | 사용자별 제한은 백엔드가 접수 단계에서 건다 |
| 스키마 검증 없음 | 노드 출력은 자유 텍스트다. 다음 노드가 필요한 정보를 받았는지 보증하지 않는다 |
| 분기 없음 | 조건 분기는 이 버전에 없다 |
| 정산 입력값 없음 | 기여 노드 목록, 실패 책임 판정, 원가 산출은 제공하지 않는다 |
| 무료 등급 | Gemini 무료 등급이라 분당 · 일일 요청 상한이 있다. 부하 테스트 대상이 아니다 |

---

## 8. 실측값 (참고)

학생 20명 · 3과목 · 2개월 데이터, 노드 2개, `gemini-2.5-flash`, 실호출 5회 기준.

| 항목 | 값 |
| --- | --- |
| 노드 1개 평균 | 약 25초 |
| 전체 평균 | 약 60초 (42 ~ 97초) |
| 실행당 토큰 | 입력 약 3,300 / 출력 약 2,700 |
| 결과 PDF 크기 | 약 79KB |

백엔드가 조회 타임아웃을 잡을 때 **노드 수 × 40초 + 60초** 정도를 상한으로 두면 충분하다.

---

## 9. 로컬 실행

```bash
cd ai/spike/linear-mvp
uv sync
uv run uvicorn src.api:app --host 0.0.0.0 --port 8000
```

`.env`에 `GEMINI_API_KEY`가 필요하다. 키 없이 붙여 보려면 요청에 `mock=true`를 넣으면 LLM 호출 없이 같은 응답 구조가 나온다. **백엔드 연동 테스트는 `mock=true`로 하는 것을 권한다.** 무료 등급 요청 상한을 아끼고 응답이 1초 안에 온다.
