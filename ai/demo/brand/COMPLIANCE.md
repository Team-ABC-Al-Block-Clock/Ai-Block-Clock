# 브랜딩 준수율 검사

## 현재 상태 (2026-09-21)

2단계 프롬프트 3개는 기존 커밋에 있다. 검사기는 8개 노드를 지원한다.
로컬 오프라인 회귀 테스트를 추가했다. **실제 LLM 준수율은 아직 측정하지 않았다.**
Runtime `.env`와 프로세스의 API 키 설정이 없으며 `ai/docs/demo-workflow.md`도 없다.
04의 브랜드명 전달은 A 확인 전까지 기존 프롬프트의 임시 가정을 유지한다.
마켓 등록·추천 평가 등 다음 일정의 작업은 이번 검사기 범위에 포함하지 않는다.

## 설치 및 검사기 검증

저장소 루트에서 실행한다. 오프라인 검사는 API 키·Runtime 의존성이 필요 없다.

```powershell
python -m pip install -r ai/demo/requirements-brand.txt
python -m unittest discover -s ai/demo -p test_compliance_brand.py -v
```

테스트는 정상 출력 8종, 잘못된 자료형, 보고서 배열 개수, 발행 정보 주입,
뒤집힌 approach, 4/5 경계 판정, 없는 채널·카피·태그, 카피 미사용,
절대 KPI/상대 표현, 금칙 표현, 10회 안정성, 오류를 포함한 분모,
CLI 종료 코드·리포트 저장·가짜 어댑터 호출 경로를 검증한다.
테스트 통과는 LLM의 준수율 측정 결과가 아니다.

## 실호출

A의 `ai/spike/linear-mvp/src/llm.py`와 `catalog.response_schema()`를 재사용한다.
기존 가정통신문 검사기의 OpenAI 호환 호출 경로는 사용하지 않는다.
`LLM_API_KEY`를 로컬 환경 또는 Runtime `.env`에 설정한다. 키를 채팅이나 커밋에 넣지 않는다.
GMS 경유 `gemini-2.5-flash`만 허용하며 기본값은 Runtime과 같다.

```powershell
cd ai/spike/linear-mvp
uv run --with jsonschema python ../../demo/check_compliance_brand.py --node 01 --brief ../../demo/brand/brief_high.json --runs 10 --expected-level high
uv run --with jsonschema python ../../demo/check_compliance_brand.py --node 01 --brief ../../demo/brand/brief_low.json --runs 10 --expected-level low
```

노드별 호출 횟수는 `--runs`로 명시한다. 출력 payload와 report.json은 Git에서 제외된
`ai/demo/outputs/brand-<UTC시각>/`에 저장한다. 응답 JSON과 토큰 수는 기록하지만
요청 프롬프트와 SDK 오류 원문은 기록하지 않는다.

이는 **노드별 원본 응답 평가**이며 Runtime 교정 2회 후 성공률이나 전체 워크플로우 평가가 아니다.
API 재시도·구조화 출력 fallback은 A 어댑터를 따르며 각 결과에 retries/schema_mode를 남긴다.
API 오류와 잘못된 JSON도 분모에 포함한다. 한 번의 요청이 실패해도 다음 회차를 계속한다.

## 입력 형식

`--input`은 입력 payload JSON 파일, `--file`은 검사할 출력 payload JSON 파일이다.
Runtime 봉투는 지원하지 않는다. 입력 스키마가 틀리면 호출 전에 중단한다.

| 노드 | 입력 JSON |
| --- | --- |
| 01, 02 | `biz.brand_brief@1` (또는 `--brief`) |
| 03a, 03b | `{"market": <시장 조사>, "product": <제품 분석>}` |
| 04 | `{"brand_name": "원본 브랜드명", "strategy_draft": <전략 초안>}` |
| 05, 06 | Runtime 필드까지 채워진 `biz.brand_strategy@1` |
| 07 | `{"campaign": <캠페인 계획>, "copy": <광고 카피>}` |

03a는 high 입력, 03b는 low 입력만 허용한다. 각 브리프에서 01·02를 실행한 뒤
해당 분기(03a 또는 03b)와 04를 순서대로 평가한다. 두 분기를 한 브리프에 강제로 실행하지 않는다.
04는 A의 전달 계약이 확정되기 전까지 브랜드명을 별도로 제공하는 가정 하에서 평가한다.
04 출력 검사 대상은 Runtime 주입 **전** payload다. issued_on/author_org는 생략 또는
빈 문자열을 허용한다. Runtime이 이미 채운 결과는 발행 정보 환각 검사에 사용하면 안 된다.

```powershell
# ai/demo에서: 파일 이름은 사용자가 준비한 실제 경로로 바꾼다.
python check_compliance_brand.py --node 04 --input outputs/report_input.json --file outputs/report_output.json
python check_compliance_brand.py --node 07 --input outputs/execution_input.json --file outputs/execution_output.json
```

## 판정 범위

- `schema_pass_rate`: 카탈로그 JSON Schema 형식 통과율. 목표 ≥95%.
- `auto_pass_rate`: 스키마 및 구현된 자동 내용 검사 통과율. **전체 내용 준수율과 다르다.**
- `competition_stability.pass`: 최소 10회 전체에서 high/low가 동일하고, 지정했다면 기대 분기와도 일치해야 true.
- `manual_review`: 사람이 확인할 목록. 자동 통과여도 `manual_review_complete`는 false다.
- 종료 코드: 자동 기준 충족 0, 미달 1, 입력/설정 오류 2. 단일 파일 검사는 10회 안정성을 요구하지 않는다.

자동 검사는 배열/필드 타입·분기 표식·판정 근거 형식·원본 브랜드명·Runtime 필드·
명시 금칙 표현·완전 동일한 중복·알려진 채널·인용 카피·해시태그 등을 검사한다.
의미 중복, 사실성, 새 전략, 요청 의도 준수, 사전에 없는 채널이나 인용하지 않은 새 카피는
문자열만으로 확정할 수 없다. 숫자 토큰 차이도 순번·기간 등의 오탐이 가능해 검토 대상으로 둔다.
자동 결과만으로 로드맵의 모든 내용 기준을 만족했다고 보고하지 않는다.
