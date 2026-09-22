# 시연 워크플로우 명세 — 브랜딩 전략

| 항목 | 내용 |
| --- | --- |
| 목적 | 1단계(판매자 한 사람의 워크플로우)가 **나뉘고 · 합쳐지고 · 갈라지고 · 다시 만나는** 것을 한 실행으로 보여 준다 |
| 정의 파일 | `ai/spike/linear-mvp/workflows/brand_agent.json` (2노드 축소판은 `brand_2node.json`) |
| 입력 예시 | `ai/spike/linear-mvp/data/brand_brief.json` |
| 계약 | `integration-guide.md` §5(워크플로우 정의) · §6(카탈로그 · 봉투) |

## 그림

```
[브리프 biz.brand_brief@1]
   ├──▶ n1 시장 조사 ─────▶ biz.market_research@1 (competition_level: high | low)  ┐ ① 분할
   └──▶ n2 제품 분석 ─────▶ biz.product_analysis@1                                 ┘
                 │ ② 병합 — br1 이 { market: n1, product: n2 } 를 받는다
   → br1 분기: n1.competition_level == "high" ?                                       ③ 조건 분기
        ├ 예    → n3a 차별화 전략 ──▶ biz.strategy_draft@1 ┐  (받은 객체를 그대로 넘긴다)
        └ 아니오 → n3b 대중 공략 전략 ─▶ biz.strategy_draft@1 ┘  같은 출력 형식이라 합류된다
                 │ ④ 합류 — 도착한 하나
   → n4 브랜드 전략 보고서 ──▶ biz.brand_strategy@1
   → [출력 → /artifact JSON, 템플릿이 붙으면 PDF]
```

## 노드

| ID | 이름 | 입력 형식 | 출력 형식 | 비고 |
| --- | --- | --- | --- | --- |
| `n1` | 시장 조사 | `biz.brand_brief@1` | `biz.market_research@1` | `competition_level` 이 분기 기준값 |
| `n2` | 제품 분석 | `biz.brand_brief@1` | `biz.product_analysis@1` | n1 과 병렬(MVP 는 순차 실행) |
| `br1` | 경쟁 강도 분기 | — | — | `type: "branch"`. LLM 을 부르지 않고 과금하지 않는다 |
| `n3a` | 차별화 전략 | (병합 객체) | `biz.strategy_draft@1` | `when: true` |
| `n3b` | 대중 공략 전략 | (병합 객체) | `biz.strategy_draft@1` | `when: false`. n3a 와 **같은 출력 형식** |
| `n4` | 브랜드 전략 보고서 | `biz.strategy_draft@1` | `biz.brand_strategy@1` | 종단. 발행일 · 작성 기관은 Runtime 이 주입 |

`n3a` · `n3b` 는 병합 객체(`{ market, product }`)를 받으므로 `input_schema_id` 를 두지 않는다.
병합 입력은 카탈로그 스키마 하나로 표현되지 않아 MVP 에서는 검증하지 않는다(경고 `MERGE_INPUT_UNCHECKED`).

## 확인 방법

```bash
cd ai/spike/linear-mvp

# 경쟁 강도 high → n3a 차별화 전략
uv run python -m src.runner --workflow brand_agent --input data/brand_brief.json --mock

# 경쟁 강도 low → n3b 대중 공략 전략 (mock 값을 지정해 반대 경로를 확인한다)
uv run python -m src.runner --workflow brand_agent --input data/brand_brief.json --mock \
    --mock-set "biz.market_research@1.competition_level=low"

# 정의만 검증 (실행하지 않는다)
uv run python -c "import json;from src import graph,runner;print(graph.validate(runner.load_workflow('brand_agent')))"
```

## 기대 결과

| | high 경로 | low 경로 |
| --- | --- | --- |
| 실행되는 노드 | n1 · n2 · br1 · **n3a** · n4 | n1 · n2 · br1 · **n3b** · n4 |
| `not_executed` | `n3b` / `branch_not_taken` | `n3a` / `branch_not_taken` |
| `contribution` | n1 · n2 · n3a · n4 (br1 제외) | n1 · n2 · n3b · n4 |
| 종단 봉투 | `biz.brand_strategy@1` | 같음 |

분기 판정은 코드가 한다. **같은 입력이면 항상 같은 경로가 나온다** — 정산과 견적의 근거이기 때문이다.

## 프롬프트

`ai/spike/linear-mvp/prompts/brand/` 에 있다. **본문은 트랙 B(조유진) 담당**이고 현재는 형식
관통 확인용 자리표시자다. 교체 시 출력 형식(카탈로그)은 바뀌지 않으므로 워크플로우 정의는 그대로 둔다.

---

# 2단계 — 판매자 3명의 에이전트 조합

| 항목 | 내용 |
| --- | --- |
| 정의 파일 | `ai/spike/linear-mvp/workflows/brand_campaign.json` |
| 캔버스 노드 4개 → **전개 후 9개** · 최장 경로 6 · 중첩 깊이 1 | 상한은 캔버스 8 · 전개 20 · 경로 8 (계약 §5.6) |

```
[브리프]
 → a1 [브랜딩 전략 에이전트 · 판매자 A]      (안에서 5노드 + 분기가 전개된다) ──▶ biz.brand_strategy@1
      ├──▶ a2 [SNS 캠페인 기획 · 판매자 B] ──▶ biz.campaign_plan@1 ┐ 분할
      └──▶ a3 [광고 카피 · 판매자 C]      ──▶ biz.ad_copy@1       ┘
                                                                  │ 병합 { campaign, copy }
 → a4 [통합 실행안 · 판매자 B] ──▶ biz.execution_plan@1
 → [출력]
```

**사용자에게는 블록 네 개로 보이지만 실제로는 아홉 개가 돈다.** 에이전트 정의는 백엔드가 그대로
끼워 보내고, 펼치는 것도 경계를 검사하는 것도 정산을 귀속하는 것도 Runtime 이 한다(계약 §5.2).

- 내부 노드 id 에 `a1.` 접두가 붙는다. 내부 분기 조건(`n1.competition_level`)도 함께 바뀐다
- 에이전트의 종단 payload 가 공개된 `output_schema_id` 를 만족하지 않으면 `AGENT_BOUNDARY_VIOLATION` · 공급자 책임
- 정산은 내부 노드의 `owner_id` 로 귀속된다 — 에이전트 소유자를 물려받는다

## 확인 방법

```bash
uv run python -m src.runner --workflow brand_campaign --input data/brand_brief.json --mock
```

## 기대 결과 (mock)

| | 값 |
| --- | --- |
| 실행 노드 | `a1.n1` · `a1.n2` · `a1.br1` · `a1.n3a` · `a1.n4` · `a2.c1` · `a3.k1` · `a4.e1` |
| 미실행 | `a1.n3b` / `branch_not_taken` (환원 4.5) |
| 정산 | **판매자 A 17.5 · B 10.0 · C 3.6 ABC — 계정 3개** |
| 견적 | 31.1 ABC (실제 과금 합계와 일치) |
| 종단 | `biz.execution_plan@1` 봉투 → `GET /v1/runs/{id}/artifact` |
