# 시연 워크플로우 명세 — 브랜딩 전략

| 항목 | 내용 |
| --- | --- |
| 목적 | 1단계(판매자 한 사람의 워크플로우)가 **나뉘고 · 합쳐지고 · 갈라지고 · 다시 만나는** 것을 한 실행으로 보여 준다 |
| 정의 파일 | `ai/spike/linear-mvp/workflows/brand_agent.json` |
| 입력 | **시연** `ai/demo/brand/brief_high.json`(칫솔 → high) · `brief_low.json`(클라이밍 테이프 → low). 테스트용 `ai/spike/linear-mvp/data/brand_brief.json`(간편식 → low) |
| 참고 파일 | `ai/demo/brand/ref/` — 시장 조사 체크리스트(md) · 보고서 작성 가이드(PDF) · 광고 카피 금지 표현(txt) |
| 실패 시연 | `ai/demo/brand/failure/README.md` — 사용자 · 판매자 · 플랫폼 책임 3케이스 |
| 계약 | `integration-guide.md` **v2.0** §5(워크플로우 정의) · §6(견본 · 봉투) |
| 버전 | v2.0 · 09-26 (계약 v2.0 반영 — 입력 형식 없음, 출력 선언, ABC 없음, 기본 모델 Pro) |

## 그림

```
[브리프 biz.brand_brief@1]
   ├──▶ n1 시장 조사 ─────▶ biz.market_research@1 (competition_level: high | low)  ┐ ① 분할
   └──▶ n2 제품 분석 ─────▶ biz.product_analysis@1                                 ┘
                 │ ② 병합 — br1 이 { market: n1, product: n2 } 를 받는다
   → br1 분기: n1.competition_level == "high" ?                                       ③ 조건 분기
        ├ 예    → n3a 차별화 전략 ──▶ biz.strategy_draft@1 ┐  (받은 객체를 그대로 넘긴다)
        └ 아니오 → n3b 대중 공략 전략 ─▶ biz.strategy_draft@1 ┘  도착한 하나가 합류된다(형식이 달라도 된다)
                 │ ④ 합류 — 도착한 하나
   → n4 브랜드 전략 보고서 ──▶ biz.brand_strategy@1
   → [출력 → /artifact JSON · /pdf 기본 문서형 PDF]
```

## 노드

| ID | 이름 | 받는 것 | 출력 선언 `output` | 비고 |
| --- | --- | --- | --- | --- |
| `n1` | 시장 조사 | 사용자 입력(폼 견본 `input.sample = biz.brand_brief@1`) | `{kind: json, sample: biz.market_research@1}` | `competition_level` 이 분기 기준값. 참고 파일: 시장 조사 체크리스트 |
| `n2` | 제품 분석 | 사용자 입력 | `{kind: json, sample: biz.product_analysis@1}` | n1 과 병렬(MVP 는 순차 실행) |
| `br1` | 경쟁 강도 분기 | 병합 `{market, product}` | — | `type: "branch"`. LLM 을 부르지 않고 과금하지 않는다. 받은 것을 그대로 넘긴다 |
| `n3a` | 차별화 전략 | 병합 `{market, product}` | `{kind: json, sample: biz.strategy_draft@1}` | `when: true` |
| `n3b` | 대중 공략 전략 | 병합 `{market, product}` | `{kind: json, sample: biz.strategy_draft@1}` | `when: false` |
| `n4` | 브랜드 전략 보고서 | n3a 또는 n3b 결과 전체 | `{kind: json, sample: biz.brand_strategy@1}` | 종단 = 에이전트의 출력. 발행일 · 작성 기관은 Runtime 이 주입. 참고 파일: 보고서 작성 가이드(PDF) |

v2.0에는 **입력 형식이 없다.** 각 노드는 앞 노드(들)의 결과 전체를 `## 입력` 아래 `### 노드 이름 (id · 견본 이름)` 절로 받는다(계약 §5.4). 분기 조건 `n1.competition_level` 은 저장 시 n1 이 견본 노드이고 그 필드가 enum 인지 코드로 검사한다.

## 확인 방법

```bash
cd ai/spike/linear-mvp

# mock — high 경로(카탈로그 샘플 값) · low 경로(값 지정)
uv run python -m src.runner --workflow brand_agent --input ../../demo/brand/brief_high.json --mock
uv run python -m src.runner --workflow brand_agent --input ../../demo/brand/brief_high.json --mock \
    --mock-set "biz.market_research@1.competition_level=low"

# 실호출 (GMS 크레딧 · 약 90초 · $0.03) — 참고 파일 포함
uv run python -m src.runner --workflow brand_agent --input ../../demo/brand/brief_low.json \
    --ref "n1=../../demo/brand/ref/시장조사_체크리스트.md" --ref "n4=../../demo/brand/ref/보고서_작성가이드.pdf"

# 정의만 검증 (실행하지 않는다)
uv run python -c "import json;from src import graph,runner;print(graph.validate(runner.load_workflow('brand_agent')))"
```

## 기대 결과

| | high 경로 | low 경로 |
| --- | --- | --- |
| 실행되는 노드 | n1 · n2 · br1 · **n3a** · n4 | n1 · n2 · br1 · **n3b** · n4 |
| `not_executed` | `n3b` / `branch_not_taken` | `n3a` / `branch_not_taken` |
| `contribution` | n1 · n2 · n3a · n4 (br1 제외) — 금액 없음, 백엔드가 단가를 붙인다 | n1 · n2 · n3b · n4 |
| 종단 봉투 | `biz.brand_strategy@1` | 같음 |
| 결과물 | `/artifact` JSON · `/pdf` 기본 문서형 PDF | 같음 |

분기 판정은 코드가 한다. **같은 입력이면 항상 같은 경로가 나온다** — 정산과 견적의 근거이기 때문이다.

## 실호출 결과

**09-26 `gemini-2.5-pro`(기본 모델) — 판정 대상을 오프라인 일반 매장으로 좁힌 프롬프트 01 기준**

| 브리프 | 기대 | 결과 | 경쟁사 수 |
| --- | --- | --- | --- |
| 칫솔(`brief_high.json`) | high | **high 3/3** ✓ → n3a | 5 · 5 · 5 |
| 클라이밍 테이프(`brief_low.json`) | low | **low 3/3** ✓ → n3b | 2 · 3 · 2 |

수정 전 Pro는 클라이밍 테이프를 3회 중 2회 high로 냈다(해외 클라이밍 전문 브랜드로 5칸을 채움). 경위는 `ai/prompts/brand/01_market_research.md` "09-26 수정".
브랜딩 에이전트 1회 실행: 약 87 ~ 88초 · 약 $0.029 · 교정 0회(계약 §13).

**09-22 `gemini-2.5-flash` (참고 — v1.x 시절)**

| 브리프 | 의도 | 모델 판정 | 경로 | 시간 | 원가 |
| --- | --- | --- | --- | --- | --- |
| `ai/demo/brand/brief_high.json` 소소칫솔(칫솔) | high | **high** ✓ | n3a 차별화 | 48.6s | $0.0070 |
| (옛 low 브리프) 밤길동무(LED 하네스) | low | **high** ✗ | n3a 차별화 | 53.3s | $0.0066 |

교정 재요청 0회, 발행일 · 작성 기관 주입 정상, 브랜드명 보존, 정산 4노드 · 환원 1노드 모두 계약대로.
**분기 자체는 코드가 값을 읽어 정확히 갈랐다**(mock 에서 양쪽 경로 검증). low 브리프가 high 로 판정된 것은
프롬프트 `01` 의 high/low 기준 또는 브리프 선택의 문제로, 트랙 B 와 조정한다(리허설 전 low 경로 실호출 재확인).

## 프롬프트

`ai/prompts/brand/01 ~ 07*.md` — **트랙 B(조유진) 작성, 09-26부터 트랙 A(김정윤)가 관리.** 마커 3블록(`system` · `user` · `fewshot`)과
자리표시자를 Runtime 이 해석한다(`src/prompts.py`). 견본 워크플로우는 이 파일을 `../../prompts/brand/…` 로 가리키며,
컨테이너에서는 같은 상대 경로가 `/prompts/brand/…` 로 풀린다.

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
끼워 보내고, 펼치는 것도 정산을 귀속하는 것도 Runtime 이 한다(계약 §5.2). 등록용 정의는 `ai/demo/brand/agents/`.

- 내부 노드 id 에 `a1.` 접두가 붙는다. 내부 분기 조건(`n1.competition_level`)도 함께 바뀐다
- 에이전트의 공개 입출력은 따로 선언하지 않는다 — 내부 진입 노드의 `input.sample` · 종단 노드의 `output`이 곧 에이전트의 입출력이다(v2.0에서 경계 검증 폐지)
- 정산은 내부 노드의 `owner_id` 로 귀속된다 — 에이전트 소유자를 물려받는다

## 확인 방법

```bash
uv run python -m src.runner --workflow brand_campaign --input ../../demo/brand/brief_high.json --mock
```

## 기대 결과 (mock)

| | 값 |
| --- | --- |
| 실행 노드 | `a1.n1` · `a1.n2` · `a1.br1` · `a1.n3a` · `a1.n4` · `a2.c1` · `a3.k1` · `a4.e1` |
| 미실행 | `a1.n3b` / `branch_not_taken` |
| 정산 입력 | `contribution` 7건 — **판매자 A · B · C 계정 3개**. 금액은 백엔드 단가 |
| 사용량 | `agents[]` 4건(에이전트별 토큰 · USD) |
| 견적 | `validate` → `estimate.worst_case_nodes` **7개** id — LLM 노드만, 최악 분기 조합의 한쪽(분기 노드 · 안 가는 쪽 제외). 금액은 백엔드가 단가를 합산. EC2 배포본에서 확인(09-26) |
| 실호출 (09-26 · Flash · 참고 PDF 첨부) | 107.2초 · $0.013157 · 교정 0. Pro 기준으로는 약 3분 · $0.055 예상 |
| 종단 | `biz.execution_plan@1` 봉투 → `GET /v1/runs/{id}/artifact` |
