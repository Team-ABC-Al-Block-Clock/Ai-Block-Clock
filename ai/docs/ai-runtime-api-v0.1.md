# AI Runtime API v0.1 — 백엔드 · 프론트 연동 계약

| 항목 | 내용 |
| --- | --- |
| 버전 | v0.1 (선형 워크플로우 스파이크) |
| 작성일 | 2026-09-09 |
| 작성 | AI 파트 (김정윤) |
| 대상 | 백엔드(Spring), 프론트엔드(React) |
| 구현 위치 | `ai/spike/linear-mvp/src/api.py` |
| 대응 Jira | S15P21A403-171 |
| 상태 | **구현 완료 · 동작 확인됨.** 실제 Gemini 호출 5회로 검증 |

> 이 문서는 규격(A2A · H2A)과 분기를 도입하지 않기로 한 팀 결정(09-08)에 따른 **최소 계약**이다. 노드를 순서대로 실행해 마지막 산출물을 PDF로 렌더링하는 것까지만 다룬다. 스키마 검증 · 분기 · 정산 입력값은 이 버전에 없다.

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
      "context": "user" },                 // 선택. 기본값 "prev"
    { "id": "letter",
      "name": "가정통신문 작성",
      "prompt": "당신은 교육 심리 전문가입니다. ...",
      "context": "prev" }
  ],
  "output": { "type": "pdf", "title": "3월 성적 리포트" }   // type: "pdf" | "markdown"
}
```

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
      "duration_ms": 28900, "input_tokens": 1288, "output_tokens": 1344, "retries": 0 },
    { "id": "letter", "name": "가정통신문 작성", "status": "running", "context": "prev",
      "duration_ms": null, "input_tokens": null, "output_tokens": null, "retries": 0 }
  ],
  "totals": { "input_tokens": 1288, "output_tokens": 1344 },
  "started_at": "2026-09-09T01:04:51+00:00",
  "finished_at": null,
  "total_duration_ms": null,
  "result": null,
  "error": null
}
```

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
  "result": null,
  "error": { "code": "RATE_LIMITED", "message": "429 …", "node_id": "letter" }
}
```

**노드 상태값.** `pending` → `running` → `succeeded` 또는 `failed`

**실행 오류 코드**

| code | 의미 | 재시도 |
| --- | --- | --- |
| `RATE_LIMITED` | Gemini 무료 등급 요청 상한. Runtime이 이미 10초 간격 2회 재시도한 뒤의 최종 실패 | 잠시 후 |
| `MODEL_API_ERROR` | 모델 API 5xx 또는 기타 오류 | 가능 |
| `MODEL_TIMEOUT` | 노드 60초 초과 | 가능 |
| `PDF_RENDER_FAILED` | 마크다운을 PDF로 그리는 중 실패 | 무의미 |
| `LLM_INIT_FAILED` | API 키 없음 등 설정 오류 | 무의미 |
| `RUNTIME_INTERNAL` | Runtime 내부 오류 | 무의미. AI 파트에 알릴 것 |

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

## 5. 파트별 할 일

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

## 6. 이 버전의 한계

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

## 7. 실측값 (참고)

학생 20명 · 3과목 · 2개월 데이터, 노드 2개, `gemini-2.5-flash`, 실호출 5회 기준.

| 항목 | 값 |
| --- | --- |
| 노드 1개 평균 | 약 25초 |
| 전체 평균 | 약 60초 (42 ~ 97초) |
| 실행당 토큰 | 입력 약 3,300 / 출력 약 2,700 |
| 결과 PDF 크기 | 약 79KB |

백엔드가 조회 타임아웃을 잡을 때 **노드 수 × 40초 + 60초** 정도를 상한으로 두면 충분하다.

---

## 8. 로컬 실행

```bash
cd ai/spike/linear-mvp
uv sync
uv run uvicorn src.api:app --host 0.0.0.0 --port 8000
```

`.env`에 `GEMINI_API_KEY`가 필요하다. 키 없이 붙여 보려면 요청에 `mock=true`를 넣으면 LLM 호출 없이 같은 응답 구조가 나온다. **백엔드 연동 테스트는 `mock=true`로 하는 것을 권한다.** 무료 등급 요청 상한을 아끼고 응답이 1초 안에 온다.
