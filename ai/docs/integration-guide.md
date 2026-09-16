# AI Runtime 연동 가이드 — 프론트 · 백엔드용

| 항목 | 내용 |
| --- | --- |
| 버전 | v1.0 |
| 작성일 | 2026-09-16 |
| 작성 | AI 파트 (김정윤) |
| 대상 | **백엔드(Spring), 프론트엔드(React)** |
| 상태 | **EC2 배포 완료 · 동작 확인됨.** 백엔드에서 컨테이너 이름으로 호출 성공 |

> **이 문서 하나만 보고 연동할 수 있게 썼다.** 필드 정의부터 오류 처리, 함정까지 전부 여기 있다.
> 더 깊은 배경이 필요하면 §12의 문서 지도를 본다.

---

## 1. 3분 요약

**AI Runtime은 "워크플로우 실행기"다.** 노드 목록과 사용자 입력을 주면, 노드를 순서대로 실행해 PDF를 만들고, 정산에 필요한 값을 돌려준다.

| 질문 | 답 |
| --- | --- |
| 어디 있나 | EC2 안 도커 컨테이너. 주소 `http://ai-block-clock-ai-runtime:8000` |
| 누가 부르나 | **백엔드만.** 프론트는 AI Runtime을 직접 부르지 않는다 |
| 인증 | 헤더 `X-ABC-Service-Token`. 없으면 401 |
| 외부에서 접근되나 | **안 된다.** 인터넷에 열려 있지 않고 nginx 경로도 없다 |
| 호출 횟수 | **실행 1건에 호출 1번.** 노드마다 왕복하지 않는다 |
| 응답 방식 | 즉시 `202 + run_id`, 그 뒤 2초마다 조회 |
| 얼마나 걸리나 | 노드 1개당 약 25초. 노드 2개면 약 41초 (실측) |
| 상태를 저장하나 | **아니다.** 재시작하면 `run_id` 조회가 404. 이력 원본은 백엔드 DB |

**전체 그림**

```
 브라우저
    │  https://j15a403.p.ssafy.io
    ▼
 nginx ──/──▶ 프론트 정적 파일
    └────/api/──▶ 백엔드 (Spring, 127.0.0.1:8080)
                      │
                      │  같은 도커 네트워크 안에서 컨테이너 이름으로 호출
                      ▼
              AI Runtime (FastAPI, 8000)  ──HTTPS──▶  GMS ──▶ Gemini
```

AI Runtime은 이 그림에서 **백엔드 뒤에 숨어 있다.** 브라우저는 존재를 모른다.

---

## 2. 접속 정보

| 항목 | 값 |
| --- | --- |
| 주소 (백엔드 컨테이너 기준) | `http://ai-block-clock-ai-runtime:8000` |
| 주소 (EC2 호스트에서 디버깅) | `http://127.0.0.1:8000` |
| 인증 헤더 | `X-ABC-Service-Token: <ABC_SERVICE_TOKEN>` |
| 토큰 값 | GitLab → Settings → CI/CD → Variables 의 `ABC_SERVICE_TOKEN` |
| 헬스체크 | `GET /v1/health` (인증 불필요) |
| Swagger | `http://127.0.0.1:8000/docs` (EC2 안에서만) |

### 백엔드가 먼저 해야 할 설정

백엔드 컨테이너에 환경변수 `ABC_SERVICE_TOKEN`을 추가한다. **GitLab Variables의 값과 글자 하나까지 같아야 한다.** 다르면 모든 호출이 401로 떨어진다.

```yaml
# backend compose.yaml
environment:
  ABC_SERVICE_TOKEN: ${ABC_SERVICE_TOKEN:?ABC_SERVICE_TOKEN 이 필요하다}
```

연결 확인은 이렇게 한다. 백엔드 컨테이너 안에서 실행한다.

```bash
curl -s http://ai-block-clock-ai-runtime:8000/v1/health
# {"status":"ok","auth_required":true,"runs_in_memory":0}
```

`auth_required: true`면 Runtime 쪽 토큰이 살아 있다는 뜻이다.

---

## 3. 전체 흐름 — 사용자가 버튼을 누른 순간부터

```
사용자      프론트           백엔드                    AI Runtime          GMS/Gemini
  │           │                │                          │                  │
  │ 코멘트 입력 │                │                          │                  │
  │ 엑셀 선택  │                │                          │                  │
  │ [실행] ──▶│                │                          │                  │
  │           │ ① POST 실행요청 │                          │                  │
  │           ├───────────────▶│                          │                  │
  │           │                │ ② 워크플로우 정의 DB 조회  │                  │
  │           │                │ ③ 예상 차감액 계산·선차감  │                  │
  │           │                │ ④ POST /v1/runs ────────▶│                  │
  │           │                │◀── 202 {run_id} 즉시 ────┤                  │
  │           │◀── run_id ─────┤                          │ 백그라운드 시작   │
  │◀ 진행화면 ─┤                │                          │                  │
  │           │                │                          │ ⑤ 엑셀→표 텍스트  │
  │           │                │ ⑥ GET /v1/runs/{id}      │ ⑥ 노드1 ───────▶│
  │           │  진행 표시      ├─── 2초마다 폴링 ────────▶│    노드1 ◀───────┤
  │◀ 1/2 완료 ─┤◀── SSE/폴링 ───┤◀ {progress, nodes} ──────┤ ⑦ 노드2 ───────▶│
  │           │                │ ⑧ 노드 성공시 정산 적립   │    노드2 ◀───────┤
  │           │                │                          │ ⑨ 마크다운→PDF   │
  │           │                │ ⑩ GET /v1/runs/{id}/pdf ▶│                  │
  │           │                │◀── application/pdf ──────┤                  │
  │◀ 다운로드 ─┤◀── 결과 URL ───┤ 스토리지 저장             │                  │
```

### 왜 202 + 폴링인가

노드 하나가 평균 25초다. 노드 3개면 75초가 되어 nginx와 로드밸런서의 기본 타임아웃 60초를 넘는다. 동기로 기다리면 연결이 끊기는 순간 이미 지불한 LLM 비용과 실행이 통째로 날아간다. 그래서 요청을 접수하면 즉시 `run_id`를 주고, 진행 상황은 조회로 가져간다.

---

## 4. 백엔드가 할 일

### 4.1 단계 요약

| # | 할 일 | 엔드포인트 |
| --- | --- | --- |
| 1 | 실행 요청을 보낸다 | `POST /v1/runs` |
| 2 | 2초마다 진행 상황을 조회한다 | `GET /v1/runs/{run_id}` |
| 3 | 노드가 성공할 때마다 정산금을 적립한다 | (2번 응답의 `nodes[].status`) |
| 4 | 끝나면 PDF를 받아 스토리지에 저장한다 | `GET /v1/runs/{run_id}/pdf` |
| 5 | 실패면 원인을 기록하고 환원한다 | (2번 응답의 `failure`) |

### 4.2 실행 요청 — `POST /v1/runs`

**형식은 `multipart/form-data`다.** JSON 본문이 아니다. 파일을 함께 보내야 하기 때문이다.

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `workflow` | string | **필수** | 워크플로우 정의를 **JSON 문자열로** 넣는다 (§6 형식) |
| `comment` | string | 선택 | 사용자가 입력한 자연어 코멘트 |
| `file` | file | 선택 | 성적 엑셀 · CSV. `.xlsx` `.xls` `.csv`, 최대 10MB |
| `mock` | boolean | 선택 | `true`면 LLM을 호출하지 않는다. 기본 `false` |

**응답 `202 Accepted`**

```json
{
  "run_id": "20260916-151933-543",
  "status": "queued",
  "node_count": 2,
  "poll_url": "/v1/runs/20260916-151933-543",
  "poll_interval_ms": 2000
}
```

**연동 첫 단계는 반드시 `mock=true`로 한다.** LLM을 부르지 않아 1초 안에 끝나고, GMS 팀 크레딧을 쓰지 않으며, 응답 구조는 실제와 완전히 같다. 형식을 맞추는 단계에서는 실호출이 필요 없다.

```bash
# 견본 워크플로우를 받아서 그대로 넣어 보는 예시
TOKEN="<ABC_SERVICE_TOKEN>"
BASE="http://ai-block-clock-ai-runtime:8000"

WF=$(curl -s -H "X-ABC-Service-Token: $TOKEN" $BASE/v1/workflows/report)

curl -X POST $BASE/v1/runs \
  -H "X-ABC-Service-Token: $TOKEN" \
  -F "workflow=$WF" \
  -F "comment=이번 달은 전반적으로 시험 난이도가 높았음" \
  -F "mock=true" \
  -F "file=@성적표.xlsx"
```

### 4.3 진행 조회 — `GET /v1/runs/{run_id}`

**2초 간격으로 호출한다.** `status`가 `succeeded` 또는 `failed`가 되면 멈춘다.

쿼리 파라미터 `include_node_output=true`를 붙이면 각 노드의 출력 전문이 함께 온다. 기본은 제외다(응답이 커진다).

**실행 중 응답 예시**

```jsonc
{
  "run_id": "20260916-151933-543",
  "status": "running",                      // queued | running | succeeded | failed
  "workflow": { "id": "report", "name": "학원 성적 리포트", "node_count": 2 },
  "mock": false,

  "progress": {
    "done": 1,                              // 완료된 노드 수
    "total": 2,                             // 전체 노드 수
    "percent": 50,
    "current_index": 1,                     // 지금 도는 노드의 0부터 시작하는 번호. 없으면 null
    "current_node_id": "letter",
    "current_node_name": "가정통신문 작성"     // ← 진행 화면에 그대로 표시하면 된다
  },

  "nodes": [
    { "id": "analyze", "name": "성적 분석", "status": "succeeded", "context": "user",
      "duration_ms": 18100, "input_tokens": 1281, "output_tokens": 1349, "retries": 0,
      "owner_id": "u_2001", "unit_price": 3.9 },
    { "id": "letter", "name": "가정통신문 작성", "status": "running", "context": "prev",
      "duration_ms": null, "input_tokens": null, "output_tokens": null, "retries": 0,
      "owner_id": "u_3001", "unit_price": 4.0 }
  ],

  "totals": { "input_tokens": 1281, "output_tokens": 1349 },
  "started_at": "2026-09-16T15:19:33+00:00",
  "finished_at": null,
  "total_duration_ms": null,

  "contribution": [                          // 정산 대상. 노드가 성공할 때마다 늘어난다
    { "node_id": "analyze", "owner_id": "u_2001", "charged_abc": 3.9 }
  ],
  "not_executed": [],                        // 실행되지 않은 노드 (환원 대상)
  "failure": null,                           // 실패 시에만 채워진다
  "result": null,                            // 완료 시에만 채워진다
  "error": null
}
```

**완료 시 추가되는 부분**

```jsonc
{
  "status": "succeeded",
  "progress": { "done": 2, "total": 2, "percent": 100,
                "current_index": null, "current_node_id": null, "current_node_name": null },
  "finished_at": "2026-09-16T15:20:14+00:00",
  "total_duration_ms": 41000,
  "totals": { "input_tokens": 2919, "output_tokens": 2890 },
  "result": {
    "type": "pdf",
    "markdown": "# 3월 학습 안내\n\n학부모님께, ...",   // 최종 산출물 원문
    "pdf_url": "/v1/runs/20260916-151933-543/pdf"
  }
}
```

### 4.4 노드 상태값

| 값 | 의미 |
| --- | --- |
| `pending` | 아직 시작 안 함 |
| `running` | 실행 중 |
| `succeeded` | 성공 |
| `failed` | 실패 |

전이는 `pending → running → succeeded` 또는 `failed` 한 방향뿐이다. 되돌아가지 않는다.

### 4.5 PDF 받기 — `GET /v1/runs/{run_id}/pdf`

`status`가 `succeeded`이고 `output.type`이 `pdf`일 때 `application/pdf`를 돌려준다. 아직 준비되지 않았으면 `409`다.

**받은 파일은 백엔드 스토리지에 저장한다.** Runtime의 `out/` 디렉터리는 임시 저장소이고, 컨테이너를 다시 올리면 사라진다. 프론트에는 백엔드가 발급한 URL을 준다.

### 4.6 폴링할 때 반드시 지킬 것

| 항목 | 내용 |
| --- | --- |
| **멱등성** | 폴링은 같은 상태를 여러 번 본다. 이미 적립한 `(run_id, node_id)`를 기록해 **두 번 적립하지 않게** 한다 |
| **중단 조건** | `succeeded` · `failed`면 멈춘다. 추가로 `노드 수 × 40초 + 60초`를 넘도록 상태가 안 바뀌면 실패로 처리한다 |
| **404 처리** | Runtime을 재배포하면 이전 `run_id`가 사라진다. 404는 "없어진 실행"으로 보고 DB 이력으로 답한다 |
| **409 처리** | PDF가 아직 준비 안 됨. `status`를 다시 확인한다 |

---

## 5. 프론트가 할 일

**프론트는 AI Runtime과 직접 주고받는 것이 없다.** 백엔드만 부른다. 다만 두 가지 형식을 맞춰야 한다.

### 5.1 캔버스가 만드는 워크플로우 JSON

사용자가 캔버스에서 조립한 결과가 §6의 JSON이 된다. 이것을 백엔드에 저장하고, 백엔드가 실행할 때 Runtime으로 넘긴다.

**가장 중요한 규칙: `nodes` 배열의 순서가 곧 실행 순서다.** 엣지나 그래프 개념이 없다. 캔버스에서 위에서 아래로 연결한 순서를 그대로 배열에 담으면 된다.

**노드 수 상한은 8개다.** 노드 하나가 약 25초이므로 8개면 200초이고, 워크플로우 총 실행 시간 5분(PER-003) 제한에서 역산한 값이다. 배치 단계에서 막아 주면 사용자가 실행 버튼을 누른 뒤에 거절당하지 않는다. Runtime도 요청 시점에 다시 검사한다.

### 5.2 진행 화면에 표시할 값

백엔드가 전달하는 값으로 게이지를 그린다.

| 화면 요소 | 쓸 필드 |
| --- | --- |
| 진행률 바 | `progress.percent` |
| "2단계 중 1단계" | `progress.done` / `progress.total` |
| 현재 무엇을 하는지 | `progress.current_node_name` |
| 단계별 체크 표시 | `nodes[].status` (`pending` · `running` · `succeeded` · `failed`) |
| 실패 위치 강조 | `failure.node_id` |
| 실패 안내 문구 | `error.message` |

### 5.3 SSE에 대하여

프론트 코드에 `/api/v1/executions/{id}/events`(SSE)가 정의되어 있는데, **AI Runtime에는 SSE가 없다.** 백엔드가 Runtime을 2초마다 폴링한 결과를 SSE로 중계하는 구조다. 프론트 입장에서는 기존 설계 그대로 쓰면 되고, 바꿀 것이 없다.

---

## 6. 워크플로우 JSON 형식

```jsonc
{
  "id": "report",                          // 선택. 로그 식별용
  "name": "학원 성적 리포트",                // 선택. 화면 표시용
  "model": null,                           // 선택. null 이면 서버 기본 모델

  "nodes": [
    {
      "id": "analyze",                     // 필수. 워크플로우 안에서 유일해야 한다
      "name": "성적 분석",                  // 선택. 진행 화면에 보이는 이름
      "prompt": "당신은 학원 성적 데이터 분석가입니다. ...",   // 필수. 판매자가 쓴 프롬프트 원문
      "context": "user",                   // 선택. 기본값 "prev"

      // ── 정산 입력값. 백엔드가 채워 보낸다 ──
      "owner_id": "u_2001",                // 이 노드의 원작자. 정산금을 받을 계정
      "unit_price": 3.9,                   // 실행 단가(ABC). 백엔드가 등급을 반영해 확정한 값
      "base_cost": 3.0,                    // 참고용
      "premium_rate": 0.30                 // 참고용
    },
    {
      "id": "letter",
      "name": "가정통신문 작성",
      "prompt": "당신은 교육 심리 전문가입니다. ...",
      "context": "prev",
      "owner_id": "u_3001", "unit_price": 4.0, "base_cost": 4.0, "premium_rate": 0.0
    }
  ],

  "output": { "type": "pdf", "title": "3월 성적 리포트" }   // type: "pdf" | "markdown"
}
```

### `context` — 이 노드가 무엇을 입력으로 받는가

| 값 | 받는 것 | 쓰는 자리 |
| --- | --- | --- |
| `"user"` | 사용자 입력 (코멘트 + 엑셀을 표로 바꾼 텍스트) | 첫 노드 |
| `"prev"` (기본) | **직전 노드의 출력 텍스트만** | 두 번째 이후 노드 |
| `"prev+user"` | 직전 노드 출력 + 원본 사용자 입력 | 원본 데이터를 다시 봐야 하는 노드 |

첫 노드는 `context` 값과 무관하게 사용자 입력을 받는다. 앞 노드가 없기 때문이다.

### 요청 시점에 검사하는 것

아래를 어기면 `400 INVALID_WORKFLOW`가 나고, `errors` 배열에 어느 부분이 문제인지 담긴다.

- `nodes`는 1개 이상의 배열
- 각 노드의 `id`가 비어 있지 않고 중복되지 않음
- 각 노드의 `prompt`가 비어 있지 않음
- `context`는 `user` · `prev` · `prev+user` 중 하나
- `output.type`은 `pdf` 또는 `markdown`

---

## 7. 정산 연동

### 7.1 금액은 누가 정하나 — **AI Runtime은 계산하지 않는다**

이 부분이 자주 오해된다. 역할이 이렇게 나뉜다.

| 값 | 정하는 곳 | 지금 상태 |
| --- | --- | --- |
| Base Cost (원가) | AI 파트가 산출 (NOD-007) | **MVP는 노드별 고정값.** 분석 3.0 · 통신문 4.0 ABC |
| Premium Rate | **백엔드** — 등급에서 결정 | `ai/docs/grading-formula.md` 참고 |
| `unit_price` = Base × (1 + Premium) | **백엔드** | 백엔드가 계산해 워크플로우 JSON에 실어 보낸다 |
| 실제 적립 · 환원 · 장부 | **백엔드** | |

**Runtime이 하는 일은 하나다.** 백엔드가 보낸 `unit_price`를, **실제로 실행된 노드에만** 붙여서 `contribution`으로 돌려준다. 실행되지 않은 노드는 `not_executed`에 넣는다. 계산도 판단도 하지 않는다.

> 등급 산정 수식(베이지안 보정 · 로그 정규화 · Tier 구간 · Premium Rate)은 `ai/docs/grading-formula.md`에 있다. 백엔드 구현 기준 문서다.

### 7.2 돈이 움직이는 네 지점

| 시점 | 무슨 일 | 누가 | AI가 주는 근거 |
| --- | --- | --- | --- |
| 실행 시작 | 노드 단가 합계를 선차감(예치) | 백엔드 | 없음. 백엔드가 이미 단가를 안다 |
| 노드 성공 | 그 노드 원작자에게 정산금 즉시 적립 | 백엔드 | `nodes[i].status` 변화 + `contribution` |
| 노드 실패 | 책임에 따라 적립 보류, ABC 환원 | 백엔드 | `failure.fault` + `refund_scope` |
| 실행 종료 | 미실행 구간 환원, 정산 확정 | 백엔드 | `not_executed` |

MVP는 플랫폼 수수료 0%다. 차감한 ABC 전액이 각 노드 원작자에게 간다.

### 7.3 노드 단위 즉시 적립을 폴링으로 하는 법

기획서는 "노드가 성공할 때마다 실시간 적립"을 요구한다. 콜백 없이 폴링만으로 된다.

1. 2초마다 `GET /v1/runs/{run_id}`를 조회한다
2. `nodes[i].status`가 `succeeded`로 **새로 바뀐** 노드를 찾는다
3. 그 노드의 `owner_id`와 `unit_price`로 정산금을 적립한다
4. `(run_id, node_id)`를 기록해 다음 폴링에서 다시 적립하지 않는다

`contribution` 배열을 그대로 써도 되는데, 이 배열도 성공한 노드가 늘어날 때마다 커지므로 같은 주의가 필요하다.

### 7.4 실패했을 때 — `failure` 블록

```jsonc
"status": "failed",
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
"error": { "code": "MODEL_API_ERROR", "message": "…", "node_id": "letter" }
```

**환원 범위 규칙**

| `fault` | 뜻 | 환원 대상 | 정산 유지 | 공급자 등급 |
| --- | --- | --- | --- | --- |
| `supplier` | 에이전트 자체 결함 | 실패 노드 + 이후 미실행 구간 | 앞서 성공한 노드 | 성공률에 실패로 집계 |
| `consumer` | 사용자 입력 · 조합 오류 | 실패 노드 + 이후 미실행 구간 | 앞서 성공한 노드 | 영향 없음 |
| `platform` | 우리 쪽 장애 | **전체 노드** | 앞서 성공한 노드 | 영향 없음 |

`platform`일 때만 환원액과 정산액의 합이 차감액을 넘는다. 플랫폼 과실로 공급자가 손해를 보지 않게 하려는 것이고, 차액은 플랫폼이 부담한다는 뜻이다.

---

## 8. 오류 처리

### 8.1 오류 응답 형식

모든 4xx · 5xx는 같은 모양이다.

```jsonc
{ "detail": { "code": "INVALID_WORKFLOW",
              "message": "워크플로우 정의 오류",
              "errors": ["nodes[0].prompt 가 비어 있습니다"] } }
```

### 8.2 요청 단계 오류 (`POST /v1/runs`)

| HTTP | code | 의미 | 백엔드 처리 |
| --- | --- | --- | --- |
| 400 | `INVALID_WORKFLOW_JSON` | `workflow` 필드가 JSON이 아님 | 재시도 무의미. 코드 버그 |
| 400 | `INVALID_WORKFLOW` | 노드 정의 오류. `errors`에 상세 | 사용자에게 안내 |
| 400 | `UNSUPPORTED_FILE_TYPE` | 허용 확장자 아님 | 사용자에게 안내 |
| 400 | `FILE_TOO_LARGE` | 10MB 초과 | 사용자에게 안내 |
| 401 | `UNAUTHORIZED` | 서비스 토큰 불일치 | 설정 확인 |
| 404 | `RUN_NOT_FOUND` | `run_id` 없음. 재배포 후에도 발생 | DB 이력으로 답한다 |
| 409 | `PDF_NOT_READY` | 아직 실행 중이거나 실패함 | `status` 확인 후 재시도 |

### 8.3 실행 중 오류 (`failure.error_code`)

| code | 의미 | `fault` | 성공률 반영 | 재시도 |
| --- | --- | --- | --- | --- |
| `MODEL_TIMEOUT` | 노드가 60초를 넘김 | `supplier` | 반영 | 가능 |
| `MODEL_API_ERROR` | 모델 API 오류 | `supplier` | 반영 | 가능 |
| `FILE_PARSE_FAILED` | 업로드 파일을 읽지 못함 | `consumer` | 미반영 | 무의미 |
| `RATE_LIMITED` | 요청 상한. 10초 간격 2회 재시도 후 최종 실패 | `platform` | 미반영 | 잠시 후 |
| `PDF_RENDER_FAILED` | PDF 렌더링 실패 | `platform` | 미반영 | 무의미 |
| `LLM_INIT_FAILED` | API 키 없음 등 설정 오류 | `platform` | 미반영 | 무의미 |
| `RUNTIME_INTERNAL` | Runtime 내부 오류 | `platform` | 미반영 | AI 파트에 알릴 것 |

---

## 9. 함정 — 미리 알아야 할 것

| 함정 | 내용 | 대응 |
| --- | --- | --- |
| **실행 상태가 메모리에만 있다** | Runtime을 재배포하면 진행 중이던 `run_id` 조회가 404가 된다 | 이력 원본은 백엔드 DB. 배포는 실행이 없는 시점에 |
| **단일 프로세스다** | 워커를 여러 개로 늘리면 조회가 다른 워커로 가서 실패한다 | 현재 워커 1개로 고정 운영 |
| **동시 실행 제한이 없다** | Runtime은 들어오는 대로 받는다 | 사용자별 제한은 백엔드가 접수 단계에서 건다 |
| **폴링은 같은 상태를 여러 번 본다** | 그대로 적립하면 중복 정산된다 | `(run_id, node_id)` 멱등 키 필수 |
| **`markdown` 필드가 크다** | 완료 응답에 최종 산출물 원문이 통째로 들어간다 | 프론트로 그대로 넘기지 말고 필요한 것만 |
| **LLM은 매번 다른 글을 쓴다** | 같은 입력이라도 결과 문장이 달라진다 | 정상이다. 형식은 프롬프트로 고정한다 |
| **GMS 크레딧은 팀 공용이다** | 개발 중 반복 실행이 크레딧을 소모한다 | 형식 맞추기 단계는 반드시 `mock=true` |

---

## 10. 지금 없는 것 (MVP 범위)

오해를 막기 위해 명시한다. 아래는 **현재 구현되어 있지 않다.**

| 항목 | 상태 |
| --- | --- |
| 노드 간 데이터 형식 검증 (A2A 스키마) | 없음. 노드 출력은 자유 텍스트다 |
| 분기 노드 (조건에 따라 경로 갈라짐) | 없음. 노드는 순서대로만 실행된다 |
| 에이전트 노드 중첩 | 없음. 기본 노드만 |
| 부분 재실행 (실패 지점부터) | 없음. 전체 재실행만 |
| 실행 이벤트 콜백 · SSE · 큐 | 없음. 폴링이 전부다 |
| 노드 단독 테스트 API | 없음 |

**이것들은 파이프라인이 안정되면 그 위에 올린다.** 올리더라도 이 문서의 전송 방식(202 + 폴링, multipart, PDF 다운로드)은 바뀌지 않는다. 워크플로우 JSON에 필드가 **추가**될 뿐이고, 기존 필드는 그대로 동작한다.

---

## 11. 실측값 (참고)

학생 20명 · 3과목 · 2개월 데이터, 노드 2개, `gemini-2.5-flash` 기준.

| 항목 | 값 |
| --- | --- |
| 노드 1개 평균 | 약 25초 |
| 전체 평균 | 약 41초 |
| 실행당 토큰 | 입력 약 2,900 / 출력 약 2,900 |
| 결과 PDF 크기 | 약 79KB |
| mock 모드 | 1초 미만 |

백엔드가 조회 타임아웃을 잡을 때 **노드 수 × 40초 + 60초**를 상한으로 두면 충분하다.

---

## 12. 문서 지도

| 문서 | 언제 보나 |
| --- | --- |
| **이 문서** (`integration-guide.md`) | 연동을 시작할 때. 대부분 여기서 끝난다 |
| `ai-runtime-api.md` | 필드 하나하나의 정의가 더 필요할 때 |
| `pipeline-flow.md` | 노드 사이에서 실제로 무슨 일이 일어나는지, A2A 도입 후 무엇이 바뀌는지 |
| `grading-formula.md` | 백엔드가 등급 · 단가를 계산할 때 |
| `recommend-api.md` | 자연어 에이전트 추천 API (별도 서비스) |

---

## 13. 막히면

| 증상 | 먼저 확인할 것 |
| --- | --- |
| 401이 난다 | 백엔드 컨테이너의 `ABC_SERVICE_TOKEN`이 GitLab Variables 값과 같은지 |
| 연결이 안 된다 | 백엔드가 같은 도커 네트워크(`ai-block-clock_ai-block-clock-network`)에 있는지 |
| 404 `RUN_NOT_FOUND` | Runtime이 재배포됐는지. 이전 `run_id`는 사라진다 |
| 400 `INVALID_WORKFLOW` | 응답의 `errors` 배열에 어느 노드의 무엇이 문제인지 적혀 있다 |
| 응답이 느리다 | 정상이다. 노드당 25초. `mock=true`로 확인해 본다 |
| 그 외 | `RUNTIME_INTERNAL`이 보이면 AI 파트에 알린다. Runtime 로그를 봐야 한다 |
