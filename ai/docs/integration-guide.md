# AI Runtime 연동 계약 — 백엔드 · 프론트용

| 항목 | 내용 |
| --- | --- |
| 상태 | **확정본** (2026-09-19 · **09-22 갱신** — 검증 응답의 견적, 노드 필드 8종, 결과 URL, 정의 검증 코드, 원가 산출). 버전 번호를 붙이지 않는다. 이 파일이 항상 최신 계약이다 |
| 작성 | AI 파트 트랙 A (김정윤) |
| 대상 | **백엔드(Spring), 프론트엔드(React)** |
| 범위 | 실행 엔진(8000). 추천 서비스(8100)는 [`recommend-api.md`](recommend-api.md), 등급 수식은 [`grading-formula.md`](grading-formula.md) |

> **이 문서 하나만 보면 된다.** 필드 정의 · 엔드포인트 · 정산 · 오류 · 함정이 전부 여기 있다.
> 이전에 나뉘어 있던 `ai-runtime-api.md`(계약)와 `pipeline-flow.md`(내부 동작)는 이 문서로 합쳤다.
> 서비스 전체 흐름은 [`../../docs/service-flow.md`](../../docs/service-flow.md).

---

## 1. 3분 요약

**AI Runtime은 워크플로우 실행기다.** 노드와 연결선, 사용자 입력을 주면 순서대로 실행해 결과 JSON(과 PDF)을 만들고, 정산에 필요한 값을 돌려준다.

| 질문 | 답 |
| --- | --- |
| 어디 있나 | EC2 안 도커 컨테이너. `http://ai-block-clock-ai-runtime:8000` |
| 누가 부르나 | **백엔드만.** 프론트는 AI Runtime을 직접 부르지 않는다 |
| 인증 | 헤더 `X-ABC-Service-Token`. 없으면 401 |
| 외부 접근 | **안 된다.** 인터넷에 열려 있지 않고 nginx 경로도 없다 |
| 호출 횟수 | **실행 1건에 호출 1번.** 노드마다 왕복하지 않는다 |
| 응답 방식 | 즉시 `202 + run_id`, 그 뒤 2초마다 조회 |
| 큐를 쓰나 | **쓰지 않는다.** HTTP + 폴링이 확정이다 (§4) |
| 걸리는 시간 | 노드 1개당 약 25초 |
| 상태를 저장하나 | **아니다.** 재시작하면 `run_id` 조회가 404. 이력 원본은 백엔드 DB |

### 구현 일정 — 무엇을 언제 붙일 수 있나

계약은 아래 전부를 정의한다. 다만 Runtime 구현은 순차로 들어간다. **형식을 먼저 맞춰 두고, 붙는 날 `mock=true`로 확인**하면 된다.

| 기능 | 상태 |
| --- | --- |
| `POST /v1/runs` · 폴링 · PDF · 정산 필드 | **동작 중** |
| 카탈로그 스키마 JSON 입출력 · `GET /v1/schemas` | 09-19 |
| `edges` · 분할 · 병합 · 조건 분기 · 합류 · `POST /v1/workflows/validate` | 09-19 |
| 에이전트 노드 전개 · `GET /v1/runs/{id}/artifact` | 09-20 |
| 노드별 참고 파일 `ref_files` | 09-21 |
| 템플릿 `output.template_id` | 09-22 ~ 23 |

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
# {"status":"ok","auth_required":true,"runs_in_memory":0}
```

---

## 3. 서비스 전체에서 AI를 부르는 지점

**세 곳뿐이다.** 나머지는 백엔드와 프론트만으로 끝난다.

| | 언제 | 어느 서비스 | 엔드포인트 |
| --- | --- | --- | --- |
| ① 인덱싱 | 에이전트가 마켓에 공개될 때 | 추천 (8100) | `POST /v1/index/agents` |
| ② 추천 | 사용자가 랜딩에 자연어를 입력할 때 | 추천 (8100) | `POST /v1/recommend` |
| **③ 실행** | 사용자가 실행 버튼을 누를 때 | **실행 엔진 (8000)** | **`POST /v1/runs`** |

**이 문서는 ③만 다룬다.** ①②는 [`recommend-api.md`](recommend-api.md)에 있고, 서로 다른 컨테이너라 독립적이다. 추천을 붙이지 않아도 실행 파이프라인은 완전히 동작한다.

### AI를 부르지 않는 지점 — 헷갈리기 쉬운 것

| 항목 | 실제 담당 | 이유 |
| --- | --- | --- |
| 프롬프트 저장 · 조회 | 백엔드 DB | Runtime은 실행 시점에 요청으로 받는다 |
| 노드 소유권 검증 (버전 확정 가능 여부) | 백엔드 | 계정과 소유권 정보는 백엔드에 있다 |
| 실행 단가 계산 · 등급 산정 | 백엔드 배치 | AI는 수식(`grading-formula.md`)만 제공한다 |
| ABC 차감 · 적립 · 환원 | 백엔드 | 장부는 전부 백엔드 몫이다 |
| 실행 이력 저장 | 백엔드 DB | Runtime은 메모리에만 두고 재시작하면 잊는다 |
| 결과물 보관 · 다운로드 URL 발급 | 백엔드 스토리지 | Runtime의 `out/`은 임시 폴더다 |
| 마켓 키워드 검색 · 필터 | 백엔드 DB | 추천(②)과 별개 기능이다 |

---

## 4. 전체 흐름

```
 사용자        프론트           백엔드                      AI Runtime          Gemini
  │  입력 폼 · 파일 │                │                          │                  │
  │  [실행] ──────▶│                │                          │                  │
  │               │ ① 실행 요청 ───▶│                          │                  │
  │               │                │ ② 워크플로우 DB 조회       │                  │
  │               │                │ ③ 선차감                  │                  │
  │               │                │ ④ POST /v1/runs ────────▶│                  │
  │               │                │◀── 202 {run_id} 즉시 ────┤                  │
  │               │◀── run_id ─────┤                          │ 백그라운드 시작   │
  │◀ 진행 화면 ────┤                │                          │ ⑤ 입력 검증       │
  │               │                │ ⑥ GET /v1/runs/{id}      │ ⑥ 노드 실행 ────▶│
  │               │  진행 표시      ├─── 2초마다 폴링 ────────▶│    출력 검증 ◀───┤
  │◀ 1/2 완료 ─────┤◀── SSE/폴링 ───┤◀ {progress, nodes} ──────┤ ⑦ 다음 노드 ────▶│
  │               │                │ ⑧ 노드 성공 시 적립       │                  │
  │               │                │ ⑨ GET .../artifact ─────▶│ ⑨ 봉투 조립       │
  │               │                │ ⑩ GET .../pdf ──────────▶│ ⑩ 템플릿 → PDF   │
  │◀ 다운로드 ─────┤◀── 결과 URL ───┤ 스토리지 저장             │                  │
```

### 왜 202 + 폴링인가 — 그리고 왜 큐를 쓰지 않는가

노드 하나가 평균 25초다. 노드 3개면 75초가 되어 nginx와 로드밸런서 기본 타임아웃 60초를 넘는다. 동기로 기다리면 연결이 끊기는 순간 이미 지불한 LLM 비용과 실행이 통째로 날아간다. **그래서 접수 즉시 `run_id`를 주고 진행 상황은 조회로 가져간다.**

메시지 큐로 바꾸지 않는 이유는 셋이다.

1. **정산이 중복된다.** 큐는 재배달이 기본이라 컨슈머가 죽으면 같은 작업이 다시 오고, 같은 실행이 두 번 돌아 LLM 비용과 정산이 두 번 잡힌다(REL-005). 막으려면 실행 단위 멱등 키가 필요한데 Runtime 상태는 메모리라 둘 곳이 없다.
2. **접수 시점의 동기 검증이 사라진다.** 지금은 `POST /v1/runs`가 `INVALID_WORKFLOW` · `FILE_TOO_LARGE` · 401을 즉시 돌려준다. 큐에 넣으면 전부 비동기 오류가 되어 백엔드의 접수 응답과 선차감 시점을 다시 설계해야 한다.
3. **얻는 것이 폴링 루프 제거 하나뿐이다.** 노드가 25초라 2초 폴링이 더하는 지연은 8%다.

**결과물도 Runtime이 스토리지에 직접 쓰지 않는다.** 백엔드가 `/artifact` · `/pdf`로 받아 자기 스토리지에 저장한다.

---

## 5. 워크플로우 JSON — 확정 형식

백엔드가 실행 요청에 담아 보내는 JSON이다. **프론트 캔버스가 만들어 백엔드 DB에 저장할 형식이기도 하다.**

### 5.1 전체 예시 — 판매자 한 명의 에이전트 (분할 · 병합 · 분기 · 합류)

```jsonc
{
  "id": "brand_agent",                     // 선택. 로그 식별용
  "name": "브랜딩 전략",                     // 선택. 화면 표시용
  "model": null,                           // 선택. null 이면 서버 기본값(gemini-2.5-flash)

  "nodes": [
    { "id": "n1", "type": "llm", "name": "시장 조사",
      "prompt": "당신은 시장 조사 분석가입니다. ...",
      "input_schema_id":  "biz.brand_brief@1",
      "output_schema_id": "biz.market_research@1",
      "owner_id": "u_2001" },

    { "id": "n2", "type": "llm", "name": "제품 분석",
      "prompt": "당신은 제품 분석가입니다. ...",
      "input_schema_id":  "biz.brand_brief@1",
      "output_schema_id": "biz.product_analysis@1",
      "owner_id": "u_2001" },

    { "id": "br1", "type": "branch",                       // LLM 호출 아님. 과금 0
      "condition": { "left": "n1.competition_level", "op": "==", "right": "high" } },

    { "id": "n3a", "type": "llm", "name": "차별화 전략",
      "prompt": "...", "output_schema_id": "biz.strategy_draft@1", "owner_id": "u_2001" },
    { "id": "n3b", "type": "llm", "name": "대중 공략 전략",
      "prompt": "...", "output_schema_id": "biz.strategy_draft@1", "owner_id": "u_2001" },

    { "id": "n4", "type": "llm", "name": "브랜드 전략 보고서",
      "prompt": "...", "output_schema_id": "biz.brand_strategy@1", "owner_id": "u_2001" }
  ],

  "edges": [
    { "from": "n1", "to": "br1", "port": "market"  },      // 병합 — port가 입력 객체의 키
    { "from": "n2", "to": "br1", "port": "product" },
    { "from": "br1", "to": "n3a", "when": true  },         // 분기 — 선택된 경로
    { "from": "br1", "to": "n3b", "when": false },
    { "from": "n3a", "to": "n4" },                         // 합류 — 도착한 하나
    { "from": "n3b", "to": "n4" }
  ],

  "output": { "schema_id": "biz.brand_strategy@1", "template_id": null }
}
```

### 5.2 노드 — `type`으로 종류를 구분한다

| `type` | 하는 일 | `prompt` | 과금 |
| --- | --- | --- | --- |
| `llm` (기본값) | 프롬프트를 실행해 JSON payload를 만든다 | **필수** | 있음 |
| `branch` | 조건을 평가해 경로를 고른다 | 없음 | **0** |
| `agent` | 남의 에이전트를 통째로 — Runtime이 안을 펼쳐 실행한다 | 없음 | 내부 노드별 |

**`type`을 생략하면 `llm`이다.** 저장 스키마에 `type` 칼럼만 두면 나중에 종류가 늘어도 마이그레이션이 없다. 웹 검색 · 계산 같은 **도구 노드는 MVP에 없다.**

입출력 노드(H2A · A2A2H)는 **캔버스의 노드로 그리지 않는다.** 입력은 첫 노드의 `input_schema_id`로 만든 폼이고, 출력은 `output`이다.

#### `llm` 노드의 필드

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `id` | 필수 | 워크플로우 안에서 유일 |
| `type` | 선택 | 생략 시 `llm` |
| `name` | 선택 | 진행 화면에 표시되는 이름 |
| `prompt` | 필수 | 판매자가 쓴 프롬프트 원문 |
| `input_schema_id` | 선택 | 받을 payload의 카탈로그 스키마. 없으면 검증하지 않는다 |
| `output_schema_id` | **필수** | 낼 payload의 카탈로그 스키마 |
| `ref_files` | 선택 | 판매자가 노드에 붙여 둔 참고 자료 (§7.1) |
| `owner_id` | 권고 | 이 노드의 원작자. 정산 귀속 키 (§8.2) |
| `agent_id` | 선택 | 에이전트 단위로 묶어 돌려받고 싶을 때 |
| `unit_price` · `base_cost` · `premium_rate` | 선택 | 백엔드 가격표를 그대로 되돌려받고 싶을 때만 (§8.2) |

#### `agent` 노드 — Runtime이 전개한다

백엔드는 저장된 에이전트 정의를 **그대로 끼워** 보낸다. Runtime이 ID에 접두를 붙여 평탄화하고, 들어오는 엣지는 내부 진입 노드로, 나가는 엣지는 실행된 내부 종단 노드로 잇는다.

```jsonc
{ "id": "a1", "type": "agent",
  "agent": {
    "agent_id": "ag_31",
    "owner_id": "u_2001",
    "input_schema_id":  "biz.brand_brief@1",
    "output_schema_id": "biz.brand_strategy@1",
    "nodes": [ /* 5.1과 같은 형식 */ ],
    "edges": [ /* 5.1과 같은 형식 */ ]
  } }
```

- **종단 payload가 `output_schema_id`를 만족하는지 Runtime이 검사한다**(경계 검증). 어기면 `AGENT_BOUNDARY_VIOLATION` · 공급자 책임이다.
- 정산은 내부 노드의 `owner_id`로 귀속된다.
- **중첩은 깊이 1까지다.** 에이전트 안에 에이전트를 넣을 수 없다.
- 내부에 분기 · 분할 · 병합이 있을 수 있어서 **평평한 프롬프트 배열로는 펼 수 없다.** 백엔드가 직접 펼쳐 보내려면 `nodes` + `edges` 형태여야 하고, 그 경우 경계 검증은 적용되지 않는다.

### 5.3 `edges` — 연결선이 실행 순서를 정한다

```jsonc
{ "from": "n1", "to": "br1", "port": "market", "when": true }
```

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `from` · `to` | 필수 | 노드 ID |
| `port` | 선택 | **병합할 때 입력 객체의 키.** 없으면 `from` 노드 ID를 키로 쓴다 |
| `when` | 분기에서만 | `branch` 노드에서 나가는 간선의 선택 경로. `true` · `false` |

**`edges`가 없으면 `nodes` 배열 순서대로 직선 실행한다.** 기존 선형 워크플로우는 그대로 동작한다.

### 5.4 실행 규칙 — 규칙 하나로 분할 · 병합 · 분기 · 합류가 다 된다

> **노드는 "실제로 실행된" 상위 노드들의 결과를 받는다. 하나면 그 payload 그대로, 여럿이면 `port` 이름으로 묶은 객체로. 분기 노드는 받은 것을 그대로 선택된 경로에 넘긴다.**

| 상황 | 실행된 상위 | 노드가 받는 것 |
| --- | --- | --- |
| 직선 | 1 | payload 그대로 |
| 분할 (한 노드 → 여러 노드) | 1 | 하위 노드들이 각각 같은 payload를 받는다. MVP는 순차 실행 |
| 병합 (여러 노드 → 한 노드) | 2 이상 | `{ "market": {...}, "product": {...} }` — 키는 `port` |
| 조건 분기 뒤 합류 | 1 | 도착한 payload. 안 간 쪽은 `not_executed` |

**첫 노드**는 `edges`의 `to`에 없는 노드다. 사용자 입력(§7.1)을 받는다.

#### 분기 조건

```jsonc
"condition": { "left": "n1.competition_level", "op": "==", "right": "high" }
```

| | |
| --- | --- |
| `left` | `"<노드ID>.<payload 경로>"`. **실행된 임의 상위 노드**를 가리킬 수 있다. 바로 앞 노드로 제한하지 않는다 |
| `op` | `==` `!=` `>` `>=` `<` `<=` `in` |
| `right` | 리터럴 (숫자 · 문자열 · enum · 불리언, `in`은 배열) |

**평가는 코드가 한다. LLM을 쓰지 않는다.** 같은 입력이면 항상 같은 경로가 나온다. `left`가 가리키는 경로가 없으면 `BRANCH_CONDITION_INVALID` · 수요자 책임이다.

**합류 지점에서 만나는 경로들의 `output_schema_id`는 같아야 한다.** 프론트는 캔버스에서 이것으로 연결 가부를 판정한다.

### 5.5 `output` — 무엇을 내놓을 것인가

```jsonc
"output": { "schema_id": "biz.brand_strategy@1", "template_id": null }
```

| 필드 | 설명 |
| --- | --- |
| `schema_id` | 최종 산출물의 스키마. 종단 노드의 `output_schema_id`와 같아야 한다 |
| `template_id` | 선택. 있으면 그 템플릿으로 PDF를 렌더한다. 없으면 스키마별 기본 템플릿 |

**템플릿 렌더 실패는 실행 실패가 아니다.** JSON은 정상 반환하고 `TEMPLATE_RENDER_FAILED`를 경고로 남긴다.

### 5.6 그래프 규모 상한

| 상한 | 기준 | 값 |
| --- | --- | --- |
| 캔버스 노드 수 | 사용자가 놓은 개수 (에이전트 1개 = 1) | **8** |
| 전개 후 노드 수 | `nodes` 배열 전체 길이, 에이전트를 펼친 뒤 | **20** |
| 최장 경로 길이 | 그래프에서 **가능한** 가장 긴 경로 (실제로 도는 경로가 아니다) | **8** |
| 에이전트 중첩 깊이 | | **1** |

넷 다 워크플로우 정의만 보면 정해지는 **정적인 값**이라, 저장 시점(백엔드 · 프론트)과 실행 시점(Runtime)의 판정이 갈리지 않는다. **분기가 있어도 마찬가지다** — 실제로 도는 경로가 아니라 가능한 최댓값으로 세기 때문이다.

실행 시간 5분(PER-003)은 상한으로 쓰지 않는다. 입력에 따라 달라져 저장 시점에 판정할 수 없어서, 견적의 경고로만 쓴다.

**이 규칙을 백엔드가 복제하지 않아도 된다.** `POST /v1/workflows/validate`(§7.5)를 저장 버튼에서 부르면 된다.

### 5.7 요청 시점에 검사하는 것

어기면 `400 INVALID_WORKFLOW`가 나고 `errors` 배열에 어느 노드의 무엇이 문제인지 담긴다.

- `nodes`는 1개 이상의 배열, `id`는 비어 있지 않고 중복되지 않음
- `type: "llm"`인 노드는 `prompt`가 비어 있지 않음
- `output_schema_id`가 카탈로그에 있는 값
- `edges`의 `from` · `to`가 실재하는 노드, **순환이 없음**
- `branch` 노드는 `condition`이 있고 나가는 간선이 `when: true` · `when: false` 각각 하나
- 합류 지점에서 만나는 경로들의 `output_schema_id`가 같음
- §5.6 상한을 넘지 않음

---

## 6. 스키마 카탈로그와 봉투

### 6.1 카탈로그 — 노드가 주고받는 형식의 목록

노드 출력은 자유 텍스트가 아니라 **카탈로그에 등록된 스키마를 따르는 JSON**이다. 그래야 연결 가부를 판정하고, 분기 조건이 값을 읽고, 조합 오류의 책임을 가릴 수 있다.

| 카테고리 | 스키마 |
| --- | --- |
| `common` | `common.text@1` 단순 텍스트 · `common.document@1` 문서 · `common.table@1` 표 |
| `biz` | `biz.brand_brief@1` 브랜드 기획 요청서 · `biz.market_research@1` 시장 조사 결과 · `biz.product_analysis@1` 제품 분석 결과 · `biz.strategy_draft@1` 전략 초안 · `biz.brand_strategy@1` 브랜드 전략 보고서 · `biz.campaign_plan@1` SNS 캠페인 계획 · `biz.ad_copy@1` 광고 카피 · `biz.execution_plan@1` 통합 실행안 |

목록과 JSON Schema 전문은 `GET /v1/schemas`(§7.6)로 받는다. 원본은 Pydantic이고 `ai/schemas/catalog/`에 export되어 있다. **프론트는 이 JSON Schema로 입력 폼을 그리고 연결 가부를 판정한다.**

### 6.2 봉투 — Runtime이 씌운다

노드 결과는 봉투에 담긴다. **LLM은 `payload`만 만들고 봉투는 Runtime이 씌운다.** 다음 노드에는 payload(+ 사용자 코멘트)만 넘어간다.

```jsonc
{
  "a2a_version": "1.0",
  "run_id": "20260919-100451-688",
  "node_id": "n1",
  "produced_at": "2026-09-19T01:04:51Z",
  "schema_id": "biz.market_research@1",
  "payload": { "competition_level": "high", "price_band": "중가", "...": "..." },
  "meta": { "model": "gemini-2.5-flash",
            "usage": { "input_tokens": 1281, "output_tokens": 1349 },
            "cost_abc": 0.12, "duration_ms": 18100, "retry_count": 0, "repair_count": 1 },
  "error": null
}
```

`repair_count`는 형식이 틀려 다시 요청한 횟수다. **교정은 2회까지** 하고, 넘으면 `SCHEMA_VALIDATION_FAILED` · 공급자 책임이다.

> **A2A는 자체 정의 용어이며 Google A2A 프로토콜과 무관하다.**

---

## 7. 엔드포인트

### 7.1 `POST /v1/runs` — 실행 요청

**형식은 `multipart/form-data`다.** JSON 본문이 아니다. 파일을 함께 보내기 때문이다.

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `workflow` | string | **필수** | §5의 워크플로우 정의를 **JSON 문자열로** |
| `input` | string | 선택 | 첫 노드의 입력 payload를 JSON 문자열로 (폼에서 받은 값) |
| `comment` | string | 선택 | 사용자가 입력한 자연어 코멘트 |
| `file` | file | 선택 | 사용자가 실행할 때 올리는 파일. `.xlsx` `.xls` `.csv`, 최대 10MB |
| `ref_file_<node_id>_<n>` | file | 선택 | **판매자가 노드에 붙여 둔 참고 자료.** 노드 정의의 `ref_files` 순서와 맞춘다 |
| `mock` | boolean | 선택 | `true`면 LLM을 호출하지 않는다. 기본 `false` |

**참고 자료(`ref_files`)와 사용자 파일(`file`)은 다르다.** 참고 자료는 공급자가 노드를 만들 때 붙여 둔 채점 기준표 · 양식이고, 백엔드가 원본을 보관했다가 실행 때마다 함께 보낸다. mime는 `pdf · txt · md · csv · png · jpg`, **노드당 합계 10MB**. 어기면 `REF_FILE_REJECTED` · 공급자 책임이다.

**응답 `202 Accepted`**

```jsonc
{
  "run_id": "20260919-100451-688",
  "status": "queued",
  "node_count": 6,
  "poll_url": "/v1/runs/20260919-100451-688",
  "poll_interval_ms": 2000
}
```

```bash
TOKEN="<ABC_SERVICE_TOKEN>"
BASE="http://ai-block-clock-ai-runtime:8000"

curl -X POST $BASE/v1/runs \
  -H "X-ABC-Service-Token: $TOKEN" \
  -F "workflow=$(cat workflow.json)" \
  -F "input=$(cat brief.json)" \
  -F "mock=true"
```

### 7.2 `GET /v1/runs/{run_id}` — 진행 상황 · 결과 조회

**2초 간격으로 호출한다.** `status`가 `succeeded` 또는 `failed`가 되면 멈춘다.
쿼리 `include_node_output=true`를 붙이면 노드별 payload 전문(`nodes[].output`)이 함께 온다. 기본은 제외다(응답이 커진다).

```jsonc
{
  "run_id": "20260919-100451-688",
  "status": "running",                      // queued | running | succeeded | failed
  "workflow": { "id": "brand_agent", "name": "브랜딩 전략 에이전트", "node_count": 6 },
  "mock": false,

  "progress": {
    "done": 2, "total": 4, "percent": 50,   // total = 돌아야 하는 LLM 노드 수. 분기 노드 · 건너뛴 노드는 세지 않는다
    "current_index": 3,
    "current_node_id": "n3a",
    "current_node_name": "차별화 전략"          // ← 진행 화면에 그대로 표시하면 된다
  },

  "nodes": [
    { "id": "n1", "name": "시장 조사", "type": "llm", "status": "succeeded",
      "schema_id": "biz.market_research@1", "agent_id": null, "owner_id": "u_2001",
      "started_at": "2026-09-19T01:04:52+00:00", "finished_at": "2026-09-19T01:05:10+00:00",
      "duration_ms": 18100, "input_tokens": 1281, "output_tokens": 1349,
      "retries": 0, "repair_count": 1, "cost_abc": 3.76, "unit_price": 4.2,
      "skip_reason": null, "branch_taken": null },
    { "id": "br1", "name": "경쟁 강도 분기", "type": "branch", "status": "succeeded",
      "schema_id": null, "duration_ms": 0, "input_tokens": 0, "output_tokens": 0,
      "repair_count": 0, "cost_abc": 0.0, "unit_price": null,
      "skip_reason": null, "branch_taken": true },                          // 코드가 판정했다. LLM 호출 · 과금 없음
    { "id": "n3a", "name": "차별화 전략", "type": "llm", "status": "running",
      "started_at": "2026-09-19T01:05:11+00:00", "finished_at": null,
      "duration_ms": null, "input_tokens": null, "output_tokens": null,
      "retries": 0, "repair_count": 0, "cost_abc": null, "unit_price": 4.5 },
    { "id": "n3b", "name": "대중 공략 전략", "type": "llm", "status": "skipped",
      "skip_reason": "branch_not_taken", "unit_price": 4.5, "cost_abc": null }   // 안 간 쪽. §8.4 에 환원으로 잡힌다
  ],

  "totals": { "input_tokens": 1281, "output_tokens": 1349, "cost_abc": 3.76 },
  "started_at": "2026-09-19T01:04:51+00:00",
  "finished_at": null,
  "total_duration_ms": null,

  "contribution": [                          // 성공한 노드가 늘어날 때마다 즉시 커진다
    { "node_id": "n1", "owner_id": "u_2001", "agent_id": null, "charged_abc": 4.2 }
  ],
  "not_executed": [                          // §8.4
    { "node_id": "n3b", "reason": "branch_not_taken", "refund_abc": 4.5 }
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
| `schema_id` | 이 노드가 낸 결과의 카탈로그 형식. 분기 노드는 `null` | 프론트 — 노드 결과 미리보기 |
| `agent_id` | 어느 에이전트 안에서 돈 노드인가. 캔버스의 에이전트 블록과 매핑 | 백엔드 — 에이전트 단위로 정산 묶기 · 화면 표시 |
| `owner_id` | 정산이 귀속되는 판매자 계정 | 백엔드 — 적립 |
| `started_at` · `finished_at` · `duration_ms` | 노드 실행 시각 · 소요 | 진행 화면 · 실측 |
| `input_tokens` · `output_tokens` | 쓴 토큰. **교정 재요청분까지 합산** | 원가 검산 |
| `retries` | 모델 API 오류(429 · 5xx)로 다시 부른 횟수 | 운영 |
| `repair_count` | **형식이 틀려 다시 요청한 횟수(0 ~ 2).** 2를 넘기면 `SCHEMA_VALIDATION_FAILED` · 공급자 책임 | 판매자 품질 지표 |
| `cost_abc` | **실측 원가**(§8.1 수식). 판매가 `unit_price`와 다르다 — 그 차이가 마진이다 | 백엔드 — Base Cost 보정 · 마진 |
| `unit_price` | 요청에 실어 보낸 값 그대로. 안 보냈으면 `null` | 백엔드 |
| `skip_reason` | `skipped`일 때만. `branch_not_taken`(분기로 안 감) · `upstream_failed`(앞이 실패) | 정산은 이것 말고 `not_executed[]`를 본다 |
| `branch_taken` | 분기 노드가 고른 쪽. `true`면 `when: true` 간선으로 갔다 | 진행 화면 "예 / 아니오" |

**노드 상태값** `pending → running → succeeded` 또는 `failed`. 한 방향뿐이고 되돌아가지 않는다.
분기로 선택되지 않은 경로의 노드는 `skipped`가 되고 `skip_reason`이 붙는다. 같은 내용이 `not_executed[]`에도 담기므로 **정산은 그쪽만 보면 된다.**
에이전트를 펼친 노드의 `id`는 `a1.n1`처럼 **에이전트 id가 접두**로 붙는다(§5.2).

**완료 시 추가되는 부분**

```jsonc
{
  "status": "succeeded",
  "finished_at": "2026-09-19T01:07:02+00:00",
  "total_duration_ms": 131000,
  "result": {
    "type": "pdf",
    "schema_id": "biz.brand_strategy@1",
    "artifact_url": "/v1/runs/20260919-100451-688/artifact",   // 결과 JSON (봉투) — 항상 있다
    "pdf_url": "/v1/runs/20260919-100451-688/pdf"              // PDF. 렌더 실패면 null (실행 실패 아님)
  }
}
```

### 7.3 `GET /v1/runs/{run_id}/artifact` — 결과 JSON

종단 노드의 **봉투**(§6.2)를 그대로 돌려준다. `status`가 `succeeded`일 때만. 아직이면 409 `ARTIFACT_NOT_READY`.

**백엔드는 이것도 스토리지에 저장한다.** 템플릿이 붙기 전까지는 이 JSON이 최종 산출물이고, 붙은 뒤에도 원본 데이터로 남긴다.

### 7.4 `GET /v1/runs/{run_id}/pdf` — 결과 PDF

`application/pdf`. 아직이면 409 `PDF_NOT_READY`.
Runtime의 `out/`은 임시 저장소이고 컨테이너를 다시 올리면 사라진다. **백엔드 스토리지에 저장하고 프론트에는 백엔드가 발급한 URL을 준다.**

### 7.5 `POST /v1/workflows/validate` — 저장 시점 검증 + 견적

워크플로우 JSON만 받아 **실행하지 않고** 검증 결과와 **견적**을 돌려준다. 저장 버튼에서 한 번 부르면 상한 · 순환 · 스키마 호환 규칙을 백엔드가 복제하지 않아도 되고, 실행 버튼 옆에 **최대 청구액**을 보여줄 수 있다. 과금은 없다.

```jsonc
// 요청 (application/json)
{ "workflow": { /* §5 형식. 에이전트 노드는 정의를 그대로 끼워 보낸다 */ } }

// 응답 200 — UC1(5노드 + 분기) 실제 응답
{ "valid": true,
  "errors": [],
  "warnings": [],
  "stats": { "node_count": 6,              // 캔버스에 놓인 노드 수 (에이전트 1개 = 1)
             "expanded_node_count": 6,     // 에이전트를 펼친 뒤 노드 수
             "longest_path": 4,            // 가능한 가장 긴 경로 (실제로 도는 경로가 아니다)
             "agent_depth": 0 },
  "estimate": { "max_charge_abc": 17.5,    // 분기 조합 중 가장 비싼 경우의 unit_price 합
                "worst_case_nodes": ["n1", "n2", "n3a", "n4"],   // 그때 실제로 도는 노드
                "unpriced_nodes": [] } }   // 단가가 없어 0 으로 센 노드. 비어 있지 않으면 견적이 실제보다 낮다

// 응답 200 — 틀린 정의
{ "valid": false,
  "errors": [ { "code": "SCHEMA_INCOMPATIBLE", "node_id": "n4",
                "message": "n3a의 출력(biz.strategy_draft@1)이 n4의 입력(biz.campaign_plan@1)과 맞지 않습니다" } ],
  "warnings": [ { "code": "LONG_PATH", "node_id": null, "message": "최장 경로 7단계. 5분을 넘길 수 있습니다" } ],
  "stats": { "...": "..." }, "estimate": { "...": "..." } }
```

**견적은 최장 경로가 아니다.** 분할 · 병합이 있으면 여러 노드가 함께 돌기 때문에, 분기 조합마다 "그 경우 실제로 도는 노드의 `unit_price` 합"을 구해 최댓값을 잡는다. 분기 노드는 과금하지 않는다. `unit_price`를 안 보낸 노드는 0으로 세고 `unpriced_nodes`에 남긴다 — 화면에는 "최대 17.5 ABC" 대신 "단가 미정 노드 있음"으로 보여야 한다.

**`errors[].code` — 하나라도 있으면 `valid: false`, 실행 요청은 400 `INVALID_WORKFLOW`**

| code | 뜻 |
| --- | --- |
| `NO_NODES` | `nodes`가 비어 있음 |
| `INVALID_GRAPH` | id 누락 · 중복, 없는 노드를 가리키는 엣지, **순환** |
| `MISSING_PROMPT` | `llm` 노드의 `prompt`가 비어 있음 |
| `MISSING_OUTPUT_SCHEMA` | `llm` 노드에 `output_schema_id`가 없음 |
| `UNKNOWN_SCHEMA` | 카탈로그에 없는 `schema_id` |
| `SCHEMA_INCOMPATIBLE` | 앞 노드 출력 형식 ≠ 뒤 노드 입력 형식 (완전 일치만 허용) |
| `JOIN_SCHEMA_MISMATCH` | 분기 뒤 합류 지점에서 만나는 경로들의 출력 형식이 다름 |
| `BRANCH_CONDITION_MISSING` · `BRANCH_EDGES_INVALID` | 분기 노드에 `condition`이 없음 · `when: true/false` 간선이 각각 하나가 아님 |
| `OUTPUT_SCHEMA_MISMATCH` | `output.schema_id`가 종단 노드의 출력 형식과 다름 |
| `LIMIT_EXCEEDED` | §5.6 상한 초과 (캔버스 8 · 전개 20 · 경로 8) |
| `AGENT_BOUNDARY_VIOLATION` | 에이전트가 공개한 `output_schema_id`와 내부 종단 노드의 출력이 다름 |
| `AGENT_NESTING_TOO_DEEP` · `AGENT_DEFINITION_MISSING` · `AGENT_EDGE_UNKNOWN_NODE` | 에이전트 안에 에이전트 · `agent.nodes` 없음 · 내부 엣지가 없는 노드를 가리킴 |

**`warnings[].code` — 실행은 된다. 화면에 안내만**

| code | 뜻 |
| --- | --- |
| `LONG_PATH` | 최장 경로 7 이상. 5분(PER-003)을 넘길 수 있다 |
| `UNPRICED_NODES` | 단가 없는 노드가 있어 견적이 실제보다 낮다 |
| `MERGE_INPUT_UNCHECKED` | 병합 노드는 입력이 `port`로 묶인 객체라 `input_schema_id`로 검증하지 않는다 |
| `LEGACY_TEXT_NODE` | 출력 형식이 없는 자유 텍스트 노드(09-19 이전 정의). 새 워크플로우에서는 나오지 않아야 한다 |

### 7.6 `GET /v1/schemas` · `GET /v1/schemas/{schema_id}`

카탈로그 목록과 JSON Schema 전문. **프론트가 노드 편집 화면의 "입출력 형식" 선택지와 첫 노드 입력 폼을 이것으로 그린다.** 백엔드는 그대로 중계하면 된다.

### 7.7 보조

| 엔드포인트 | 용도 |
| --- | --- |
| `GET /v1/health` | 상태 확인. `{ status, auth_required, runs_in_memory }` |
| `GET /v1/workflows` · `/{id}` | 견본 워크플로우. 복사해 테스트용으로 쓴다 |

---

## 8. 정산 연동

### 8.1 금액은 백엔드가 정한다 — Runtime은 계산하지 않는다

| 값 | 정하는 곳 |
| --- | --- |
| Base Cost (원가) | AI 파트가 산출 (NOD-007) |
| Premium Rate · 등급 | **백엔드** 배치 — `grading-formula.md` |
| 실행 단가 · 선차감액 · 정산 | **백엔드** |
| 실행된 노드 · 성공 여부 · 실패 책임 · 미실행 구간 | **Runtime** ← 이 문서가 주는 것 |

**실측 원가(`cost_abc`)는 Runtime이 계산해 노드마다 · 실행 합계로 준다** (NOD-007 · EXE-013).

```
cost_abc = (입력 토큰 × 입력 단가 + 출력 토큰 × 출력 단가) / 1,000,000 × ABC 환산율
```

- 교정 재요청분 토큰까지 합산한다. 실패한 노드도 쓴 토큰은 기록한다(환원 여부는 §8.5).
- 단가는 모델 공시가(`gemini-2.5-flash` 입력 0.30 · 출력 2.50 USD / 1M 토큰), 환산율은 **1 ABC = 0.001 USD(제안값 🔶)**. 셋 다 환경변수 `LLM_PRICE_IN` · `LLM_PRICE_OUT` · `ABC_PER_USD`로 덮어쓴다. 기획이 환산율을 확정하면 코드 수정 없이 바뀐다.
- 실측 기준 노드 1회 약 3 ~ 5 ABC. `unit_price`(판매가)와의 차이가 판매자 마진이다.
- mock 실행은 모델을 부르지 않으므로 `cost_abc: 0.0`이다.

### 8.2 요청에 담는 정산 필드 — 전부 선택이다

| 필드 | 필수 | 왜 |
| --- | --- | --- |
| `owner_id` 또는 `agent_id` | **권고** | 없으면 `contribution[]`의 귀속이 비어서, 한 실행에 여러 판매자 계정으로 정산이 쌓이는 것을 Runtime 응답 하나로 보여줄 수 없다 |
| `unit_price` · `base_cost` · `premium_rate` | 선택 | 보내면 실행된 노드에 그대로 붙여 돌려준다. 안 보내면 `null` |

**`0`으로 채워 보내지 않는다.** `0`과 "값 없음"을 구분할 수 없다. 생략하거나 `null`로 보낸다.

### 8.3 돈이 움직이는 네 지점

| 시점 | 무슨 일 | AI가 주는 근거 |
| --- | --- | --- |
| 실행 시작 | 선차감 | 없음. 백엔드가 이미 단가를 안다 |
| 노드 성공 | 원작자에게 적립 | `nodes[].status` 변화 + `contribution[]` |
| 노드 실패 | 책임에 따라 보류 · 환원 | `failure.fault` + `refund_scope` |
| 실행 종료 | 미실행 구간 환원 · 정산 확정 | `not_executed[]` |

**멱등성은 백엔드가 지킨다.** 폴링은 같은 상태를 여러 번 본다. 이미 적립한 `(run_id, node_id)`를 기록해 두 번 적립하지 않게 한다.

### 8.4 `not_executed` — 실행되지 않은 노드

```jsonc
"not_executed": [
  { "node_id": "n3b", "reason": "branch_not_taken" },   // 분기로 안 감
  { "node_id": "n4",  "reason": "upstream_failed"  }    // 앞이 실패해 도달 못 함
]
```

**요구사항 PAY-010** — 분기로 실행되지 않은 노드는 과금 · 정산 대상에서 제외하고 선차감분을 실행자에게 반환한다. `branch` 노드 자체는 LLM 호출이 아니라 `contribution`에도 `not_executed`에도 들어가지 않는다.

### 8.5 실패했을 때 — `failure`

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

---

## 9. 오류 처리

모든 4xx · 5xx는 아래 형태다.

```jsonc
{ "detail": { "code": "INVALID_WORKFLOW", "message": "워크플로우 정의 오류",
              "errors": [ { "code": "MISSING_PROMPT", "node_id": "n1", "message": "prompt 가 비어 있습니다" } ] } }
```

`errors[]`의 코드는 §7.5 표와 같다 — 저장 시점 `validate`와 실행 요청이 **같은 검사기**를 쓴다.

### 9.1 요청 단계

| HTTP | code | 의미 | 백엔드 처리 |
| --- | --- | --- | --- |
| 400 | `INVALID_WORKFLOW_JSON` | `workflow` 필드가 JSON이 아님 | 재시도 무의미 |
| 400 | `INVALID_WORKFLOW` | 노드 · 엣지 정의 오류. `errors[]`에 §7.5 코드 | 사용자에게 안내 |
| 400 | `INVALID_INPUT_JSON` | `input` 필드가 JSON 객체가 아님 | 폼 직렬화 확인 |
| 400 | `UNSUPPORTED_FILE_TYPE` | 허용 확장자 아님 | 사용자에게 안내 |
| 400 | `FILE_TOO_LARGE` | 10MB 초과 | 사용자에게 안내 |
| 401 | `UNAUTHORIZED` | 서비스 토큰 불일치 | 설정 확인 |
| 404 | `RUN_NOT_FOUND` | `run_id` 없음. 재배포 시에도 발생 | DB 이력으로 답한다 |
| 409 | `PDF_NOT_READY` · `ARTIFACT_NOT_READY` | 아직 실행 중이거나 실패 | `status` 확인 후 재시도 |

### 9.2 실행 단계 (`failure.error_code`)

| code | 의미 | `fault` | 성공률 | 재시도 |
| --- | --- | --- | --- | --- |
| `SCHEMA_VALIDATION_FAILED` | 입력 payload가 `input_schema_id`와 안 맞음 | `consumer` | 미반영 | 무의미 |
| `SCHEMA_VALIDATION_FAILED` | 출력이 스키마와 안 맞음 (교정 2회 초과) | `supplier` | 반영 | 가능 |
| `BRANCH_CONDITION_INVALID` | 조건이 가리키는 payload 경로가 없음 | `consumer` | 미반영 | 무의미 |
| `AGENT_BOUNDARY_VIOLATION` | 에이전트 종단 payload가 선언된 출력 스키마와 다름 | `supplier` | 반영 | 가능 |
| `REF_FILE_REJECTED` | 참고 파일 mime · 크기 위반 | `supplier` | 미반영 | 무의미 |
| `PROMPT_TEMPLATE_INVALID` | 판매자 프롬프트의 입력 틀(`{…}`)이 요구하는 값을 이 자리에서 받을 수 없음 | `supplier` | 미반영 | 무의미 |
| `MODEL_TIMEOUT` | 노드 60초 초과 | `supplier` | 반영 | 가능 |
| `MODEL_API_ERROR` | 모델 API 5xx 또는 기타 | `supplier` | 반영 | 가능 |
| `FILE_PARSE_FAILED` | 업로드 파일을 읽지 못함 | `consumer` | 미반영 | 무의미 |
| `RATE_LIMITED` | 요청 상한. 10초 간격 2회 재시도 후 실패 | `platform` | 미반영 | 잠시 후 |
| `TEMPLATE_RENDER_FAILED` | 템플릿 렌더 실패. **실행 실패 아님** — JSON은 정상 반환 | `platform` | 미반영 | 무의미 |
| `PDF_RENDER_FAILED` | PDF 생성 실패 | `platform` | 미반영 | 무의미 |
| `LLM_INIT_FAILED` | API 키 없음 등 설정 오류 | `platform` | 미반영 | 무의미 |
| `RUNTIME_INTERNAL` | Runtime 내부 오류 | `platform` | 미반영 | AI 파트에 알릴 것 |

**오류 코드가 늘면 이 표를 갱신하고 공유한다.** 백엔드는 자기 봉투로 번역해 쓰면 된다.

---

## 10. 파트별 체크리스트 — 붙이는 순서

### 백엔드 — 이 순서로 붙이면 된다

| # | 단계 | 부르는 것 | 응답에서 읽는 것 |
| --- | --- | --- | --- |
| 1 | 노드 저장 | — | `type` · `input_schema_id` · `output_schema_id` · `ref_files` · `owner_id` · `unit_price`를 그대로 저장 |
| 2 | 워크플로우 저장 | **`POST /v1/workflows/validate`** | `valid`가 `false`면 `errors[]`를 사용자에게. `estimate.max_charge_abc`를 저장해 실행 화면에 표시 |
| 3 | 에이전트 저장 | — | `agent.nodes` + `agent.edges`를 **그대로** 임베드. 평평하게 펴지 않는다 |
| 4 | 실행 요청 | **`POST /v1/runs`** multipart — `workflow`(JSON 문자열) · `input`(폼 값 JSON 문자열) · `comment` · `file` · `ref_file_*` | 202 `run_id`. 선차감액은 `estimate.max_charge_abc` |
| 5 | 폴링 | **`GET /v1/runs/{id}`** 2초 간격 | `progress`를 프론트에 중계. `nodes[].status`가 `succeeded`로 바뀐 노드는 `contribution[]`에 나타난다 → `(run_id, node_id)` 멱등 키로 **즉시 적립** |
| 6 | 종료 | `status`가 `succeeded` · `failed` | `not_executed[]`의 `refund_abc` 환원, `failure.refund_scope`로 실패 구간 환원(§8.5) |
| 7 | 결과 저장 | **`GET /v1/runs/{id}/artifact`** · `/pdf` | 봉투 JSON과 PDF를 스토리지에 저장해 URL 발급. Runtime은 재시작하면 잊는다 |
| 8 | 중계 | `GET /v1/schemas` · `/{id}` | 프론트에 그대로 |

**폴링 중단 조건을 반드시 둔다.** `succeeded` · `failed`면 멈추고, 추가로 `노드 수 × 40초 + 60초`를 넘도록 상태가 안 바뀌면 실패로 처리한다.

**정산에서 보는 필드는 셋뿐이다** — `contribution[]`(적립) · `not_executed[]`(미실행 환원) · `failure.refund_scope`(실패 환원). `nodes[].skip_reason`은 화면용이고 정산 근거로 쓰지 않는다.

### 프론트 — 이 순서로 붙이면 된다

| # | 단계 | 쓰는 것 | 화면 |
| --- | --- | --- | --- |
| 1 | 노드 편집 | `GET /v1/schemas` 목록 | 입출력 형식 드롭다운(`title`이 한국어 이름). 참고 파일 업로드 |
| 2 | 캔버스 연결 | 두 노드의 `output_schema_id` · `input_schema_id` | **완전히 같을 때만** 선이 그어진다. 분할(한 출력 → 여러 노드) · 병합(여러 입력 → `port` 이름) · 분기 노드(`condition` · `when: true/false` 두 간선) |
| 3 | 저장 | (백엔드 경유) `validate` 응답 | `errors[]`는 해당 `node_id`에 빨간 표시. `warnings[]`는 노란 안내. `estimate.max_charge_abc`를 **"최대 N ABC"** 로, `unpriced_nodes`가 있으면 "단가 미정" |
| 4 | 실행 폼 | 첫 노드 `input_schema_id`의 JSON Schema(`GET /v1/schemas/{id}`) | `properties`의 `title`이 입력란 이름, `enum`이 선택지, `required`가 필수 표시 |
| 5 | 진행 | 폴링 응답 `progress` · `nodes[]` | `percent` · `current_node_name`. `type: branch`는 "판정 → 예/아니오"(`branch_taken`), `status: skipped`는 "건너뜀 — 분기로 선택되지 않음" |
| 6 | 결과 | `result.artifact_url` · `pdf_url` | JSON 보기 · 다운로드, PDF 다운로드. `pdf_url`이 `null`이면 JSON만 |
| 7 | 실패 | `error.message` · `error.hint` | 그대로 표시하면 된다. 한국어 문구 · 다음 행동 안내가 들어 있다(§9.2). `error.detail`은 표시하지 않는다 |

**프론트는 AI Runtime을 직접 부르지 않는다.** 백엔드가 중계한다. 프론트 코드의 `/api/v1/executions/{id}/events`(SSE)는 백엔드가 폴링 결과를 중계하는 것이라 그대로 쓰면 된다.

### 확인용 요청 — 서버에 붙이기 전에 손으로 한 번

```bash
# 1) 형식 목록
curl -s -H "X-ABC-Service-Token: $TOKEN" http://127.0.0.1:8000/v1/schemas | jq '.schemas[] | .schema_id'

# 2) 저장 시점 검증 + 견적 (견본 워크플로우로)
curl -s -H "X-ABC-Service-Token: $TOKEN" http://127.0.0.1:8000/v1/workflows/brand_agent \
  | jq '{workflow: .}' \
  | curl -s -H "X-ABC-Service-Token: $TOKEN" -H "Content-Type: application/json" -d @- \
         http://127.0.0.1:8000/v1/workflows/validate | jq '.valid, .estimate'

# 3) 실행 (mock) → 폴링 → 결과
RUN=$(curl -s -H "X-ABC-Service-Token: $TOKEN" -F "workflow=$(curl -s -H "X-ABC-Service-Token: $TOKEN" http://127.0.0.1:8000/v1/workflows/brand_agent)" \
  -F 'input={"brand_name":"초록소반","category":"간편식","target":"1인 가구 30대","price_tier":"중가","differentiator":"국내산 재료"}' \
  -F "mock=true" http://127.0.0.1:8000/v1/runs | jq -r .run_id)
curl -s -H "X-ABC-Service-Token: $TOKEN" http://127.0.0.1:8000/v1/runs/$RUN | jq '.status, .progress, .contribution, .not_executed'
curl -s -H "X-ABC-Service-Token: $TOKEN" http://127.0.0.1:8000/v1/runs/$RUN/artifact | jq '.schema_id, .meta'
```

## 11. 노드 사이에서 실제로 일어나는 일

백엔드가 알 필요는 없지만 디버깅할 때 도움이 된다.

```
입력 payload (JSON)
   │
   ├─ ① 입력 검증 — input_schema_id 로 Pydantic 검증. 실패 → consumer 책임
   ├─ ② 프롬프트 조립 — 프롬프트 원문 + 입력 payload + 사용자 코멘트 + 참고 파일(inline)
   ├─ ③ LLM 호출 — 구조화 출력(response_schema) 1차, 지시 + 검증 fallback
   ├─ ④ 출력 검증 — output_schema_id 로 검증. 틀리면 "형식 고쳐서 다시" 최대 2회
   ├─ ⑤ 봉투 조립 — Runtime이 a2a_version · run_id · meta 를 씌운다
   └─ ⑥ 다음 노드로 — payload(+ 코멘트)만. 봉투는 넘기지 않는다
```

**LLM은 payload만 만든다.** 봉투 · 토큰 집계 · 원가 환산 · 마스킹은 전부 Runtime이 한다.
**프롬프트 원문은 응답 · 로그 · 예외 어디에도 남기지 않는다**(SEC-001).

---

## 12. 함정 — 미리 알아야 할 것

| 함정 | 내용 | 대응 |
| --- | --- | --- |
| **실행 상태가 메모리에만 있다** | 재배포하면 진행 중이던 `run_id` 조회가 404 | 이력 원본은 백엔드 DB. 배포는 실행이 없는 시점에 |
| **단일 프로세스다** | 워커를 늘리면 조회가 다른 워커로 가서 실패 | 워커 1개로 고정 운영 |
| **동시 실행 제한이 없다** | Runtime은 들어오는 대로 받는다 | 사용자별 제한은 백엔드가 접수 단계에서 |
| **폴링은 같은 상태를 여러 번 본다** | 그대로 적립하면 중복 정산 | `(run_id, node_id)` 멱등 키 필수 |
| **`branch` 노드는 `progress.total`에 안 들어간다** | 실행 대상이 아니다 | 진행률은 응답의 `total`을 그대로 쓴다 |
| **분기로 안 간 노드도 환원 대상이다** | `not_executed[].reason` 확인 | PAY-010 |
| **LLM은 매번 다른 글을 쓴다** | 같은 입력이라도 문장이 달라진다 | 정상이다. 형식은 스키마로 고정된다 |
| **GMS 크레딧은 팀 공용이다** | 개발 중 반복 실행이 크레딧을 소모 | 형식 맞추기 단계는 반드시 `mock=true` |

---

## 13. 실측값 (참고)

노드 2개, `gemini-2.5-flash`, 자유 텍스트 시절 기준이다. JSON 출력으로 바뀐 뒤 재측정은 09-22.

| 항목 | 값 |
| --- | --- |
| 노드 1개 평균 | 약 25초 |
| 노드 2개 전체 | 약 41초 |
| 실행당 토큰 | 입력 약 2,900 / 출력 약 2,900 |
| 결과 PDF 크기 | 약 79KB |
| `mock=true` | 1초 미만 |

---

## 14. 막히면

| 증상 | 먼저 확인할 것 |
| --- | --- |
| 401 | 백엔드 컨테이너의 `ABC_SERVICE_TOKEN`이 GitLab Variables 값과 같은지 |
| 연결 안 됨 | 같은 도커 네트워크(`ai-block-clock_ai-block-clock-network`)에 있는지 |
| 404 `RUN_NOT_FOUND` | Runtime이 재배포됐는지. 이전 `run_id`는 사라진다 |
| 400 `INVALID_WORKFLOW` | 응답 `errors` 배열에 어느 노드의 무엇이 문제인지 있다 |
| 저장은 되는데 실행에서 튕김 | `POST /v1/workflows/validate`를 저장 시점에 부르고 있는지 |
| 응답이 느림 | 정상이다. 노드당 25초. `mock=true`로 확인 |
| 그 외 | `RUNTIME_INTERNAL`이 보이면 AI 파트에 알린다 |
