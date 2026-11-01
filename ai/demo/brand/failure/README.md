# 실패 3케이스 시연 — 누구 잘못인지 플랫폼이 가린다

시연 2단계 워크플로우(판매자 A · B · C 조합)를 그대로 쓰고, **어디서 무엇이 틀렸는지만** 바꿔
세 가지 책임(`supplier` · `consumer` · `platform`)이 정산을 어떻게 가르는지 보여 준다.
판정 기준은 계약 `ai/docs/integration-guide.md` §8.5 · §9.2.

모든 케이스가 **LLM을 부르기 전 또는 해당 노드 직전**에 판정되므로 결과가 항상 같다(실제 AI가
틀리기를 기다리지 않는다). mock으로도 실제 호출과 같은 판정이 나온다.

## 준비

```bash
cd ai/spike/linear-mvp
uv run python ../../demo/brand/failure/make_failure_cases.py   # generated/ 에 참고 파일 2개
```

## 케이스

| # | 책임 | 무엇이 틀렸나 | 실패 지점 | 판매자 정산 | 사용자 환불 |
| --- | --- | --- | --- | --- | --- |
| 1 | **사용자** `consumer` | 입력란의 필수 항목 "브랜드명"(`brand_name`)을 비웠다 — 입력 예시에 있는 키 | 첫 노드 전(`a1.n1`) | 없음 | **전액** — 아무것도 돌지 않았다 |
| 2 | **판매자** `supplier` | 판매자 C가 광고 카피 노드에 Word 파일(.docx)을 참고 자료로 붙였다 | `a3.k1` 광고 카피 | A의 4노드 · B의 캠페인 기획 **유지** | `a3.k1` · `a4.e1` (실패 노드부터) |
| 3 | **플랫폼** `platform` | 통합 실행안 노드에 붙은 참고 자료가 42만 자 — 입력 상한(40만 자) 초과 | `a4.e1` 통합 실행안 | 앞서 성공한 6노드 **유지** | **전액** — 차액은 플랫폼 부담 |

화면에 보이는 문구(`error.message` · `error.hint`):

| # | 문구 |
| --- | --- |
| 1 | 입력한 값이 이 블록이 받는 형식과 맞지 않습니다. / 입력 폼의 필수 항목과 값 형식을 확인한 뒤 다시 실행해 주세요. 사용료는 차감되지 않습니다. |
| 2 | 블록에 붙은 참고 자료를 쓸 수 없습니다. / 형식 또는 크기 제한을 넘었습니다. 판매자가 고쳐야 합니다. |
| 3 | 이 단계에 넘길 앞 결과와 참고 자료가 너무 큽니다. / 플랫폼의 입력 상한을 넘었습니다. 차감분은 전액 환원됩니다. 워크플로우를 나눠 실행해 주세요. |

## 로컬에서 돌리기 (CLI)

```bash
# 1. 사용자 잘못
uv run python -m src.runner --workflow brand_campaign --input ../../demo/brand/brief_bad.json --mock
# 2. 판매자 잘못
uv run python -m src.runner --workflow brand_campaign --input ../../demo/brand/brief_high.json --mock \
    --ref "a3.k1=../../demo/brand/failure/generated/브랜드가이드.docx"
# 3. 플랫폼 잘못
uv run python -m src.runner --workflow brand_campaign --input ../../demo/brand/brief_high.json --mock \
    --ref "a4.e1=../../demo/brand/failure/generated/참고자료_대용량.txt"
```

`--mock`을 빼면 실제 호출이다. 2 · 3은 실패 전까지 앞 노드들이 실제로 돌아 약 1 ~ 2분 · $0.03 ~ 0.04가 든다.

## 서비스로 돌리기 (백엔드 경유)

실행 요청(`POST /v1/runs`)의 폼 필드만 다르다. 에이전트 안 노드는 **접두 id**로 가리킨다.

| # | 다른 점 |
| --- | --- |
| 1 | `input` = `brief_bad.json` 내용(`brand_name` 없음) |
| 2 | `ref_file_a3.k1_0` = `브랜드가이드.docx` — 판매자 C가 노드에 붙여 둔 파일이라는 설정. 백엔드는 저장된 참고 파일을 그대로 보낸다 |
| 3 | `ref_file_a4.e1_0` = `참고자료_대용량.txt` |

응답의 `failure { node_id, error_code, fault, refund_scope { refund_node_ids, settled_node_ids } }`로
백엔드가 환불 · 정산을 처리하고, 프론트는 `error.message` · `error.hint`를 그대로 보여 준다.

## 검증 기록 (09-26, mock)

| # | `error.code` | `fault` | 정산 유지 | 환불 |
| --- | --- | --- | --- | --- |
| 1 | `SCHEMA_VALIDATION_FAILED` | consumer | 없음 | 9노드 전부 |
| 2 | `REF_FILE_REJECTED` | supplier | a1.n1 · a1.n2 · a1.n3a · a1.n4 · a2.c1 | a3.k1 · a4.e1 |
| 3 | `INPUT_TOO_LARGE` | platform | a1.n1 · a1.n2 · a1.n3a · a1.n4 · a2.c1 · a3.k1 | 9노드 전부 |
