# 끝난 설계 — 시작 노드 · 끝 노드

[`../io-nodes.md`](../io-nodes.md) 에서 **구현이 끝나** 걷어낸 장이다. 저장 층(2 · 3장)과 결과 받기(6.4)가
여기 있다. 지금 코드가 왜 그 모양인지 되짚을 때만 본다. **현재 규칙의 원본이 아니다.**

---

## 2. 저장 틀

### 2.1 종류를 늘린다

```java
public enum SourceType { NODE, AGENT, CONTROL, IO }

public enum IoType { START, END }
```

`ControlType` 에 값을 더하지 않는다. 흐름을 제어하는 것이 아니라 경계에 서는 것이라 역할이 다르다.

**분기 노드와 같은 자리에 산다.** `node_definition` 행 없이 `graph_node` 에만 있고, 가리킬 원본이 없다.

### 2.2 시작 노드 — 입력 항목을 든다

```jsonc
{ "nodeKey": "s1", "sourceType": "IO", "ioType": "START",
  "fields": [
    { "key": "brand_name", "label": "브랜드명", "type": "TEXT",   "required": true },
    { "key": "price_tier", "label": "가격대",   "type": "CHOICE", "required": true,
      "choices": ["보급형", "중가", "프리미엄"] }
  ] }
```

| 타입 | 화면 | 값 |
| --- | --- | --- |
| `TEXT` · `LONG_TEXT` | 한 줄 · 여러 줄 입력란 | 문자열 |
| `NUMBER` | 숫자 입력란 | 숫자 |
| `CHOICE` | 선택 상자. `choices` 가 항목 | 문자열 |
| `BOOLEAN` | 체크 상자 | 참 · 거짓 |

| 한계 | |
| --- | --- |
| `key` | 영문 소문자로 시작하고 영문 소문자 · 숫자 · 밑줄, 40자. 한 시작 노드 안에서 겹치지 않는다 |
| `label` | 40자 |
| 항목 수 | 10개 |
| `choices` | `CHOICE` 일 때 1 ~ 10개, 항목마다 40자 |

**폼을 그리는 데 필요한 최소만 둔다.** 계약의 JSON Schema 를 흉내 내지 않는다 — 우리가 해석해 화면을
그리는 값이라 표현력이 늘수록 FE 와 BE 가 같은 해석을 유지해야 한다.

**`fields` 가 비면 자유 입력이다.** 실행 화면이 여러 줄 입력란 하나를 그린다. 폼은 사용자가 무엇을
써야 할지 알려 주려고 두는 것이라, 정의하지 않아도 실행은 된다.

**`comment` 는 `key` 로 쓸 수 없다.** 사용자 코멘트가 이미 별도 파트로 모든 노드에 따라가고 견본
프롬프트도 `{comment}` 를 그 뜻으로 쓴다. 이름이 겹치면 어느 쪽이 들어갈지 정해지지 않는다.

### 2.3 끝 노드 — 결과의 구조와 형식을 든다

```jsonc
{ "nodeKey": "e1", "sourceType": "IO", "ioType": "END",
  "resultShape": "DOCUMENT", "resultFormat": "PDF" }
```

**축이 둘이다.** 하나로 뭉치면 안 된다.

| | 무엇을 정하나 | 어디로 나가나 |
| --- | --- | --- |
| `resultShape` | LLM 이 **무엇을 만들지** — 글 · 문서 · 표 | `output.schema_id` |
| `resultFormat` | 그것을 **무엇으로 렌더할지** — pdf · md · xlsx · csv | `output` 의 형식 칸 (5장 4-1) |

```java
public enum ResultShape {
    TEXT,      // common.text@1      — 제목과 본문 한 덩어리
    DOCUMENT,  // common.document@1  — 제목 · 여러 절 · 요약
    TABLE      // common.table@1     — 열과 행
}

public enum ResultFormat { PDF, MD, XLSX, CSV }
```

`biz.*` 11종은 브랜드 시연 전용이라(`biz.brand_brief@1` 설명이 "사용자가 실행 화면에서 채우는 첫
입력(H2A 폼)" 이다) 일반 사용자가 고를 목록이 아니다. **우리 enum 으로 두고 카탈로그 ID 를 저장하지
않는다** — 사용자에게는 "글 · 문서 · 표" 셋이고, 어느 ID 로 나가는지는 실행할 때 정하면 된다.

**형식도 저장한다. 결과 화면에서 고르게 하지 않는다.** 렌더는 실행의 일부다 — 완료 응답이 `pdf_url` 을
이미 들고 있으니(계약 §7.2) 실행이 끝나는 시점에 이미 만들어져 있고, 그러려면 **무엇으로 렌더할지가
요청에 실려 있어야 한다.** 지금 PDF 하나뿐이라 물어볼 필요가 없었을 뿐이다.

**결과 JSON 은 형식과 무관하게 늘 보관한다.** `artifact_url` 은 항상 있다고 계약이 적는다.

### 2.4 `graph_node` 에 칸 넷

```sql
alter table graph_node
    add column io_type varchar(20) null after branch_condition;

alter table graph_node
    add column io_fields text null after io_type;

alter table graph_node
    add column result_shape varchar(20) null after io_fields;

alter table graph_node
    add column result_format varchar(20) null after result_shape;
```

`io_fields` 는 2.2 의 배열을 JSON 문자열로 담는다. **`branch_condition` 이 이미 같은 방식이다** — 모양이
바뀔 수 있는 값을 칼럼으로 펴지 않고 한 칸에 둔다. `reference_id` 는 V16 에서 이미 null 을 받게 열어
두었다.

들어간 번호는 **V20** 이다.

### 2.5 저장할 때 보는 것

| 보는 것 | 어기면 |
| --- | --- |
| 시작 · 끝은 각각 0개 또는 1개 | `WORKFLOW_IO_NODE_DUPLICATED` |
| 시작으로 들어오는 선이 없다 | `WORKFLOW_IO_NODE_EDGE_INVALID` |
| 끝에서 나가는 선이 없다 | `WORKFLOW_IO_NODE_EDGE_INVALID` |
| 나가는 선이 둘 이상이다 | `WORKFLOW_GRAPH_NOT_TREE` — 분기 노드가 아니면 모든 노드에 같은 규칙이다 |
| `fields` 가 2.2 의 한계를 지킨다 | `COMMON_001` — 요청 검증에서 막는다. 전용 코드는 두지 않았다 |
| 끝 노드는 `resultShape` · `resultFormat` 이 있고, 시작 노드는 없다 | `COMMON_001` |
| 시작 · 끝은 `sourceId` · `nodeType` · `controlType` · `condition` 을 싣지 않는다 | `COMMON_001` |

**구조와 형식의 조합은 막지 않는다.** 표를 마크다운으로 받는 것이 말이 되는지 같은 판정은 5장 4-2 의
답이 와야 표가 선다. 짐작으로 막으면 멀쩡한 저장을 거부하게 된다. 답이 오면 그때 한 줄 더한다.

**캔버스 노드 상한(`GraphLimits.nodeCount`)에는 든다.** 원가와 프롬프트 수에서 빠지는지는 아직 물을 일이 없다 —
시작 · 끝 노드가 놓인 워크플로우는 견적과 실행 조립이 `EXECUTION_IO_NODE_NOT_SUPPORTED`(`EXECUTION_005`)로 막는다.

---

## 3. FE 와 맞춘 것

| 언제 | 부르는 곳 | 주고받는 것 |
| --- | --- | --- |
| 워크플로우 저장 | `POST /api/v1/workflows` · `PUT …/{id}` | 배치에 `sourceType: "IO"` · `ioType` · `fields` · `resultShape` · `resultFormat` |
| 워크플로우 열기 | `GET /api/v1/workflows/{id}` | 응답의 노드에 같은 값이 실린다 |
| 실행 폼 그리기 | **없다** | 위 응답만으로 그린다. `fields` 가 비면 자유 입력란 하나 |

**추가 요청이 없다.** 시작 노드 정의가 워크플로우 상세에 실려 오므로 편집 화면에서도 실행 화면에서도
스키마 카탈로그를 불러오지 않는다.

**배치 가능 목록에는 싣지 않는다.** 그 목록은 `node_definition` 과 에이전트를 나열하는 자리이고, 시작 · 끝은
가리킬 원본이 없다. 분기와 같은 취급이다 — FE 가 고정 항목으로 들고 있는다.

실행 요청(`POST /api/v1/runs`)에 `input` 파트를 더하는 것은 **번역 방식이 정해진 뒤다.** 폼 값을 어떤
모양으로 묶어 보낼지가 갈래 A 와 B 에서 다르다.

---

## 6. 결과

### 6.4 결과를 내려주는 쪽 — 받고 보관하고 내려주기

번역 방식과 무관하게 필요하다. `GET /v1/runs/{id}/pdf` 를 클라이언트에 더하고, 실행이 `succeeded` 가
되는 것을 본 자리에서 결과를 받아 보관한다. **Runtime 의 `out/` 은 임시 저장소라 컨테이너를 다시
올리면 사라진다**(계약 §7.4).

저장은 [`file-upload.md`](file-upload.md) 가 세운 `FileStore` 에 얹었다.

**형식 변환은 AI 쪽 일이다. 우리는 골라 실어 보내고, 만들어진 것을 받아 보관한다.**

렌더는 이미 Runtime 이 한다 — `pdf_url` 을 그쪽이 만들고, 결과 봉투도 `"type": "pdf"` 로 **형식 이름을
칸으로 들고 있다.** 값이 늘어날 자리로 보인다. 같은 파이프라인에 형식을 더하는 것이, 우리가 payload 를
읽어 두 번째 렌더러를 세우는 것보다 싸다. **Apache POI 를 들이지 않는다.**

우리가 하는 일은 셋이다.

| | |
| --- | --- |
| 실을 때 | 끝 노드의 `resultFormat` 을 `output` 의 형식 칸에 넣어 보낸다 — **아직 남았다** ([`../io-nodes.md`](../io-nodes.md)) |
| 받을 때 | `ExecutionResultHarvester` 가 `/v1/runs/{id}/artifact` 와 `/pdf` 를 불러 보관한다. 완료 응답의 URL 을 읽지 않고 고정 주소를 부른다 |
| 내려줄 때 | `GET /api/v1/runs/{executionId}/result[?format=]` |

**보관 구조를 형식 하나로 굳히지 않는다.** `result_pdf_key` 한 칸이 아니라 형식별 행으로 둔다 —
워크플로우마다 고른 형식이 다르고, Runtime 이 형식을 늘리면 받는 자리만 늘어야 한다. 결과 JSON 은
형식과 무관하게 늘 보관한다.

들어간 것: V21 `execution_result`(형식별 행, `(execution_id, format)` 유일), `ExecutionResultFormat { JSON, PDF }`,
`ExecutionMonitor` 와 조회 경로가 같은 수확기를 부른다. 끝 노드의 `ResultFormat` 과 다른 enum 이다 —
하나는 사용자가 고른 값이고 하나는 실제로 받아 둔 것이다.
