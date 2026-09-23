# AI Runtime 계약과 우리 코드의 차이

**실행은 돌아가지만, 저장 구조가 계약보다 한 세대 앞의 모델에 머물러 있다.** 우리는 노드를
"프롬프트 + 모델 + 원가" 로 들고 있고, 계약은 노드를 "입출력 형식을 가진 부품" 으로 본다.
지금 실행이 되는 것은 계약이 선택 필드를 넓게 열어 두었고, 필수인 `output_schema_id` 를 보내는 순간에
만들어 채우기 때문이다.

| | |
| --- | --- |
| 계약 | `origin/master:ai/docs/integration-guide.md` (09-22 갱신본, `develop-AI` 와 같은 커밋 `d7afa62`) |
| 스키마 카탈로그 | `origin/master:ai/schemas/catalog/` 11종 |
| 견본 | `ai/docs/demo-workflow.md` · `ai/prompts/brand/*.md` |
| 비교한 우리 코드 | `feat/file-upload` 작업 트리 — `client/ai` · `domain/execution` · `domain/node` · `domain/agent` · `storage` |
| 실행 엔진 코드 | **저장소에 없다.** `ai/runtime/app/*` 는 `.gitkeep` 뿐이라 계약 문서로만 대조했다 |

---

## 1. 한눈에

| 갈래 | 항목 | 계약 | 우리 |
| --- | --- | --- | --- |
| 같다 | 프롬프트 원문 | 원문을 실어 보낸다 | 원문을 저장하고 그대로 보낸다 |
| 같다 | 참고 자료 | 노드마다, `ref_file_<node_id>_<n>` | 노드마다 저장하고 버전에 고정한다 |
| 같다 | 사용자 파일 · 코멘트 · mock | `file` · `comment` · `mock` | 보낸다 |
| 같다 | 전송 방식 | HTTP + 2초 폴링 | 같다 |
| 단위가 다르다 | 모델 | 실행 한 건에 하나 | 노드마다 저장한다 |
| 단위가 다르다 | 단가 | 노드마다 `unit_price` | 에이전트 버전 단위로 매긴다 |
| 없다 | 노드 입출력 스키마 | 노드마다 저장한다. `output_schema_id` 는 필수 | 칸이 없다. 보낼 때 위치로 상수를 채운다 |
| 없다 | 에이전트 입출력 스키마 | 에이전트마다 둔다. 경계 검증 기준 | 칸이 없다 |
| 없다 | 첫 노드 입력 `input` | 첫 노드 입력 스키마로 만든 폼 값 | 보내지 않는다 |
| 일부만 있다 | 분기 · 병합 | `branch` 노드, 엣지 `when` · `port` | 분기는 저장 · 조회까지 된다. 실행에 싣지 않고, 병합 · 합류는 받지 않는다 |
| 없다 | 저장 시점 검증 · 견적 | `POST /v1/workflows/validate` | 부르지 않는다 |
| 없다 | 정산 귀속 | 노드마다 `owner_id` | 주인을 저장하지만 보내지 않는다 |
| 방향이 반대다 | 에이전트 전개 | Runtime 이 펼친다 | 우리가 펼쳐 일직선 `llm` 노드로 보낸다 |
| 이름이 틀리다 | 실패 응답 | `error_code` · `fault` · `refund_scope` | `code` · `message` 로 받는다 |
| 빠졌다 | 결과 PDF | `GET /v1/runs/{id}/pdf` | 클라이언트에 없다 |
| 빠졌다 | 폴링 중단 기준 | 노드 수 × 40초 + 60초 | 없다 |

---

## 2. 실행 요청 파트 — `POST /v1/runs`

**여섯 파트 중 `input` 하나만 비어 있다.** 조립은 `AiRuntimeClient.start` 가 한다.

| 파트 | 필수 | 계약 | 우리 |
| --- | --- | --- | --- |
| 헤더 `X-ABC-Service-Token` | 필수 | 없으면 401 | 값이 있을 때만 붙인다 |
| `workflow` | 필수 | 정의 JSON 문자열 | `RuntimeWorkflow.of` 가 만든다 |
| `input` | 선택 | 첫 노드 `input_schema_id` 폼 값 JSON | **항상 `null`** (`ExecutionService.accept`) |
| `comment` | 선택 | 자연어 코멘트. 모든 노드에 따라간다 | 보낸다 |
| `file` | 선택 | `xlsx` · `xls` · `csv`, 10MB | 보낸다. 같은 규칙으로 먼저 검사한다 |
| `ref_file_<node_id>_<n>` | 선택 | 노드 `ref_files` 순서와 맞춘다 | `ref_file_n1_0` 처럼 0부터 보낸다 |
| `mock` | 선택 | 기본 `false` | 보낸다 |

---

## 3. 워크플로우 JSON

### 3.1 지금 보내는 모양

캔버스가 `[노드 A] → [에이전트 B (b1 → b2)] → [노드 C]` 일 때 이렇게 나간다.

```jsonc
{
  "id": "<워크플로우 UUID>", "name": "...",
  "nodes": [
    { "id": "n1", "type": "llm", "name": "A",  "prompt": "...", "output_schema_id": "common.text@1" },
    { "id": "n2", "type": "llm", "name": "b1", "prompt": "...", "output_schema_id": "common.text@1" },
    { "id": "n3", "type": "llm", "name": "b2", "prompt": "...", "output_schema_id": "common.text@1" },
    { "id": "n4", "type": "llm", "name": "C",  "prompt": "...", "output_schema_id": "common.document@1" }
  ],
  "edges": [ {"from":"n1","to":"n2"}, {"from":"n2","to":"n3"}, {"from":"n3","to":"n4"} ],
  "output": { "schema_id": "common.document@1" }
}
```

- Runtime 은 에이전트 B 가 있었다는 것을 모른다. 평범한 노드 넷으로 보인다
- 스키마는 노드가 가진 값이 아니라 **마지막이냐 아니냐**로 정해진다
- `input_schema_id` · `owner_id` · `unit_price` 가 하나도 없다

### 3.2 계약이 원하는 모양

```jsonc
{
  "model": null,
  "nodes": [
    { "id": "n1", "type": "llm", "name": "A", "prompt": "...",
      "input_schema_id": "biz.brand_brief@1", "output_schema_id": "biz.market_research@1",
      "owner_id": "u_1", "unit_price": 4.2 },
    { "id": "a1", "type": "agent",
      "agent": {
        "agent_id": "ag_B", "owner_id": "u_2",
        "input_schema_id": "biz.market_research@1", "output_schema_id": "biz.strategy_draft@1",
        "nodes": [
          { "id": "b1", "type": "llm", "prompt": "...", "input_schema_id": "biz.market_research@1",
            "output_schema_id": "biz.product_analysis@1", "owner_id": "u_2" },
          { "id": "b2", "type": "llm", "prompt": "...",
            "output_schema_id": "biz.strategy_draft@1", "owner_id": "u_2" }
        ],
        "edges": [ { "from": "b1", "to": "b2" } ]
      } },
    { "id": "n2", "type": "llm", "name": "C", "prompt": "...",
      "input_schema_id": "biz.strategy_draft@1", "output_schema_id": "biz.brand_strategy@1" }
  ],
  "edges": [ { "from": "n1", "to": "a1" }, { "from": "a1", "to": "n2" } ],
  "output": { "schema_id": "biz.brand_strategy@1", "template_id": null }
}
```

### 3.3 필드별

**최상위**

| 필드 | 필수 | 계약 | 우리 |
| --- | --- | --- | --- |
| `id` · `name` | 선택 | 로그 · 화면용 | 보낸다 |
| `model` | 선택 | 실행 한 건에 하나. `null` 이면 `gemini-2.5-flash` | 보내지 않는다 |
| `nodes` | 필수 | 1개 이상 | 보낸다 |
| `edges` | 선택 | 없으면 배열 순서대로 직선 | 일직선만 보낸다 |
| `output.schema_id` | 필수 | 종단 노드 출력과 같아야 한다 | `common.document@1` 고정 |
| `output.template_id` | 선택 | PDF 템플릿 | 보내지 않는다 |

**`llm` 노드**

| 필드 | 필수 | 계약 | 우리 |
| --- | --- | --- | --- |
| `id` | 필수 | 워크플로우 안에서 유일 | `n1` · `n2` … 로 새로 매긴다 |
| `type` | 선택 | 생략하면 `llm` | `llm` |
| `name` | 선택 | 진행 화면 표시 | 보낸다 |
| `prompt` | 필수 | 원문. 블록 마커와 자리표시자를 Runtime 이 해석한다 (4.3) | 보낸다 |
| `input_schema_id` | 선택 | 없으면 입력을 검증하지 않는다 | 보내지 않는다 |
| `output_schema_id` | **필수** | 카탈로그 ID | 위치로 정한 상수 |
| `ref_files` | 선택 | 값의 모양이 계약에 없다 | 원래 이름의 배열 |
| `owner_id` | 권고 | 정산 귀속 | 보내지 않는다 |
| `agent_id` | 선택 | 에이전트 단위로 묶어 받을 때 | 보내지 않는다 |
| `unit_price` · `base_cost` · `premium_rate` | 선택 | 보내면 그대로 되돌려 준다. `0` 으로 채우지 않는다 | 보내지 않는다 |

**`branch` 노드** — 우리는 하나도 보내지 않는다

| 필드 | 필수 | 계약 |
| --- | --- | --- |
| `condition.left` | 필수 | `<노드ID>.<payload 경로>`. 실행된 어느 상위 노드든 가리킬 수 있다 |
| `condition.op` | 필수 | `==` `!=` `>` `>=` `<` `<=` `in` |
| `condition.right` | 필수 | 리터럴. `in` 이면 배열 |

**`agent` 노드** — 우리는 보내지 않고 펼친다 (6장)

| 필드 | 필수 | 계약 |
| --- | --- | --- |
| `agent.agent_id` | 선택 | 에이전트 ID |
| `agent.owner_id` | 권고 | 에이전트 판매자 |
| `agent.input_schema_id` | 선택 | 에이전트가 받는 형식 |
| `agent.output_schema_id` | 필수 | 에이전트가 내는 형식. 경계 검증 기준 |
| `agent.nodes` · `agent.edges` | 필수 | 내부 정의 그대로 |

**`edges`**

| 필드 | 필수 | 계약 | 우리 |
| --- | --- | --- | --- |
| `from` · `to` | 필수 | 노드 ID | 보낸다 |
| `port` | 선택 | 병합할 때 입력 객체의 키 | 보내지 않는다 |
| `when` | 분기에서만 | `true` · `false` | 보내지 않는다 |

---

## 4. 스키마

### 4.1 계약은 노드마다 형식을 둔다

노드 출력은 자유 텍스트가 아니라 **카탈로그 스키마를 따르는 JSON payload** 다. 값은 카탈로그 ID
문자열이고, 스키마 본문은 Runtime 이 들고 있다.

| 카탈로그 | 스키마 |
| --- | --- |
| `common` | `text@1` `{title?, body_md}` · `document@1` `{title, sections[1..10], summary_md?}` · `table@1` `{title?, columns, rows}` — 엑셀 업로드가 도착하는 자리 |
| `biz` | `brand_brief` · `market_research` · `product_analysis` · `strategy_draft` · `brand_strategy` · `campaign_plan` · `ad_copy` · `execution_plan` — 브랜드 시연용 |

스키마가 쓰이는 곳은 다섯이다.

| 쓰는 곳 | 계약 | 우리가 받는 영향 |
| --- | --- | --- |
| 출력 강제 | LLM 에 형식을 걸고, 틀리면 2회까지 교정. 넘으면 `SCHEMA_VALIDATION_FAILED` · 공급자 책임 | 중간 출력이 전부 `body_md` 한 덩어리다 |
| 입력 검증 | `input_schema_id` 가 있을 때만. 틀리면 수요자 책임 | 돌지 않는다 |
| 연결 가부 | 앞 출력 = 뒤 입력일 때만 잇는다. 합류하는 경로들의 출력도 같아야 한다 | 돌지 않는다 |
| 분기 조건 | payload 의 필드를 읽는다 (`n1.competition_level`) | 읽을 필드가 없다 |
| 실행 입력 폼 | 첫 노드 입력 스키마가 폼이 된다 | 폼이 없어 `input` 이 비어 있다 |

### 4.2 계약은 저장할 때 받아 두라고 한다

가이드 §10 백엔드 체크리스트.

| 단계 | 계약 | 우리 |
| --- | --- | --- |
| 노드 저장 | `type` · `input_schema_id` · `output_schema_id` · `ref_files` · `owner_id` · `unit_price` 를 그대로 저장 | 이름 · 종류 · 프롬프트 · 모델 · 원가 · 참고 자료 |
| 워크플로우 저장 | `validate` 를 부르고 `estimate.max_charge_abc` 를 저장 | 부르지 않는다. `estimatedCost` 가 `"0"` |
| 에이전트 저장 | `agent.nodes` + `agent.edges` 를 그대로 넣어 둔다. **평평하게 펴지 않는다** | 그래프를 복사해 두지만 보낼 때 편다 |
| 실행 | 저장한 것을 그대로 싣는다 | 보낼 때 스키마를 만들어 채운다 |

**우리 저장소에 스키마 칸은 한 곳도 없다.** `node_definition` · `agent_version_node` · `agent_version` ·
`graph_node` 어디에도 없고, 스키마가 코드에 나오는 곳은 `RuntimeWorkflow` 의 상수 둘뿐이다.

### 4.3 프롬프트 안에도 구조가 있다

견본 프롬프트는 문자열 하나가 아니다. Runtime 이 해석하는 구조를 품는다.

| | |
| --- | --- |
| 블록 마커 | `<!-- prompt:system -->` · `<!-- prompt:user -->` · `<!-- prompt:fewshot -->` |
| 자리표시자 | `{brand_name}` · `{comment}` — **입력 payload 의 필드 이름** |
| 어기면 | 이 자리에서 받을 수 없는 필드를 가리키면 `PROMPT_TEMPLATE_INVALID` · 공급자 책임 |

우리 노드의 입력은 늘 `common.text@1` 이라 받을 수 있는 필드가 `title` · `body_md` 뿐이다.

---

## 5. 모델

**계약에서 모델은 워크플로우 최상위에만 있다.** `llm` 노드에도 `agent` 블록에도 모델 칸이 없다.
판매자가 에이전트 단위로 모델을 정할 수도 없고, 여러 에이전트를 섞은 실행 한 건이 모델 하나로 돈다.

우리는 `node_definition.model` · `agent_version_node.model` 로 노드마다 저장하고, 실행 요청에는 싣지 않는다.
`AiModel` 이 `GEMINI_2_5_FLASH` 하나뿐이고 계약 기본값도 같은 모델이라 **지금은 결과가 달라지지 않는다.**
모델이 둘이 되는 순간 저장한 값을 보낼 자리가 없다.

---

## 6. 에이전트 전개

| | 우리 | 계약 |
| --- | --- | --- |
| 누가 펴나 | `ExecutionUnitAssembler` 가 굳힌 버전의 프롬프트를 이어 붙여 편다 | Runtime 이 `type: "agent"` 를 받아 편다 |
| 내부 ID | `n1` · `n2` … 로 다시 매긴다 | `a1.b1` 처럼 접두를 붙인다 |
| 내부 모양 | 일직선 프롬프트 배열 | `nodes` + `edges`. 분기 · 분할 · 병합이 있을 수 있다 |
| 경계 검증 | 없다 | 종단 payload 가 `agent.output_schema_id` 를 지키는지 본다. 어기면 `AGENT_BOUNDARY_VIOLATION` · 공급자 책임 |
| 정산 귀속 | 없다 | 내부 노드의 `owner_id` |
| 중첩 | 깊이 1 로 정했다 | 깊이 1 |

계약은 "안에 분기가 있으면 평평한 배열로는 펼 수 없다" 고 적는다. 백엔드가 직접 펴려면 `nodes` + `edges`
형태여야 하고, 그 경우에도 경계 검증은 적용되지 않는다.

---

## 7. 제어 노드 — 저장 · 조회까지 있다

**분기 노드를 캔버스에 놓고 저장 · 복원할 수 있다.** `SourceType.CONTROL` · `ControlNode` 가 생겼고,
`graph_node` 에 `control_type` · `branch_condition`, `graph_edge` 에 `branch_when` 이 붙었다 (V16).
**실행에는 싣지 않는다.** 분기가 있으면 실행 조립이 `EXECUTION_005` 로 막는다. 조건이 읽을 payload 필드가
노드 스키마 없이는 없기 때문이다 (4장).

| | 우리 | 계약 |
| --- | --- | --- |
| 분기 자리 | 제어 노드. `node_definition` 행이 없다 | `type: "branch"` 노드 |
| 조건 | `graph_node.branch_condition` `{left, operator, right}` | `condition {left, op, right}` |
| 비교 방법 | `EQ` `NE` `GT` `GE` `LT` `LE` `IN` | `==` `!=` `>` `>=` `<` `<=` `in` — 실어 보낼 때 바꾼다 |
| 갈래 | `graph_edge.branch_when` (`true` · `false`) | `when: true / false` |
| 그래프 모양 | **트리.** 들어오는 선은 늘 하나 | 분기 뒤 합류 · 병합을 받는다 |
| 병합 입력 | 없다 (설계는 `inputOrder`) | 엣지의 `port`. **순서가 아니라 이름**으로 묶는다 |
| 셀 때 | 원가 · 프롬프트 수에서 빠지고, 캔버스 상한 · 최장 경로에는 든다 | 과금 0, `progress.total` 에서 빠진다 |

병합에서 이름이 필요한 이유는, 뒤 노드의 프롬프트 자리표시자와 분기 조건이 `market` · `product` 같은 키로
payload 를 찾기 때문이다.

---

## 8. 정산과 가격

| | 계약 | 우리 |
| --- | --- | --- |
| 단가 단위 | 노드마다 `unit_price`. 견적 `max_charge_abc` 는 분기 조합별 노드 단가 합의 최댓값 | 에이전트 버전 단위 (`agent-pipeline.md` 7장) |
| 귀속 | 노드 `owner_id` → `contribution[]` | 보내지 않는다. 돌아오는 귀속이 비어 있다 |
| 원가 | Runtime 이 토큰으로 실측해 `cost_abc` 로 준다 | `BaseCostCalculator` 고정값을 노드와 버전에 박는다 |
| 미실행 환원 | `not_executed[].refund_abc` — 보낸 `unit_price` 를 되돌려 준다 | 단가를 안 보내므로 비어서 온다 |
| 실패 환원 | `failure.refund_scope` | 받지 못한다 (9장) |

`unit_price` 를 보내지 않으면 `validate` 견적은 그 노드를 0 으로 세고 `unpriced_nodes` 에 남긴다.

---

## 9. 응답을 받는 쪽

| 항목 | 계약 | 우리 |
| --- | --- | --- |
| 실패 | `failure {node_id, error_code, fault, affects_success_rate, refund_scope}` | `RunStatus.Failure(code, message, node_id)`. `code` 는 늘 `null`, `fault` · `refund_scope` 는 버려진다 |
| 결과 PDF | `GET /v1/runs/{id}/pdf`, 없으면 409 `PDF_NOT_READY` | 클라이언트에 없다 |
| 결과 JSON | `GET /v1/runs/{id}/artifact` — 종단 봉투 | 문자열로 받는다 |
| 노드 출력 전문 | `?include_node_output=true` | 쓰지 않는다 |
| 폴링 중단 | `succeeded` · `failed`, 또는 노드 수 × 40초 + 60초 동안 변화 없음 | 앞의 것만 |
| 오류 봉투 | `{"detail": {code, message, errors}}` | `AiClientException` 하나로 뭉친다 |
| 진행 상태 | `queued` · `running` · `succeeded` · `failed` | 같다. 모르는 값은 실패로 본다 |

---

## 10. 계약에 적히지 않은 것

| | 우리 가정 | 어디에 물었나 |
| --- | --- | --- |
| `ref_files` 값의 모양 | 원래 이름의 배열 | [`ai-runtime-questions.md`](ai-runtime-questions.md) 1-1 |
| `ref_file_*` 의 `<n>` 시작 | 0 | 1-2 |
| 한글 파일 이름 | 파트에는 `{자료 ID}.확장자`, 원래 이름은 `ref_files` 에만 | 1-3 |
| 에이전트 단위 참고 자료 | 없다 | 묻지 않았다. 계약에 자리가 없다 |
| 노드 · 에이전트 단위 모델 | 없다 | 묻지 않았다. 계약은 실행 한 건에 하나다 |
