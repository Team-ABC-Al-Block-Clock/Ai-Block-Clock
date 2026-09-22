# 화·수요일 시연 준비물 (2026-09-21 선행 작업)

**로컬 준비 완료와 실제 서비스 완료를 구분한다.** 에이전트는 아직 미등록이다.
아래 파일을 실제 공개 레코드로 간주하거나 준비용 ID로 운영 서비스를 색인하면 안 된다.

## 준비한 파일

| 파일 | 용도 |
| --- | --- |
| `market_content.json` | 판매자 A/B/C가 등록할 에이전트 4개 제목·설명·태그·스키마 |
| `seed_values.json` | 합성 시연 실적 원본. DB 미적용 |
| `prepared/seed_values_calculated.json` | A의 등급 수식 v1.0 기준 검산 |
| `prepared/schema_catalog.json` | A의 현재 카탈로그에서 생성한 요약 11종 |
| `prepared/brand_agents_local.json` | 추천 사전 평가 전용 공개 메타데이터. 실제 등록 API payload 아님 |
| `prepared/recommend_local_report.json` | weighted 검색 + 실제 Trust 점수 포함 로컬 평가 |
| `prepared/recommend_local_off_report.json` | 하이브리드를 끈 대조 평가 |
| `prepared/recommend_local_gated_report.json` | Trust를 관련도에 곱한 후보 수식 평가. 서비스 미적용 |
| `prepared/recommend_local_pretrained_report.json` | 사전학습 모델 대조 평가. 서비스 미적용 |
| `output/pdf/brand_strategy_design_a.pdf` | 그린 보고서 디자인, A4 2페이지 |
| `output/pdf/brand_strategy_design_b.pdf` | 네이비 보고서 디자인, A4 2페이지 |

## 등록 인계표

| 준비용 키 | 판매자 | 이름 | 입력 → 출력 | 실제 ID |
| --- | --- | --- | --- | --- |
| ag_brand_01 | A | 브랜딩 전략 수립기 | brand_brief@1 → brand_strategy@1 | 미등록 |
| ag_brand_02 | B | SNS 캠페인 기획기 | brand_strategy@1 → campaign_plan@1 | 미등록 |
| ag_brand_03 | C | 광고 카피 생성기 | brand_strategy@1 → ad_copy@1 | 미등록 |
| ag_brand_04 | B | 통합 마케팅 실행안 작성기 | campaign_plan@1 + ad_copy@1 → execution_plan@1 | 미등록 |

표의 스키마는 모두 `biz.` 접두어를 가진다. 등록 후 `agent_id`, `owner_id`를
실제 값으로 기록하고 공개 설명문을 이 파일과 대조한다.
판매자 계정 이름·ID는 현재 확인되지 않았다. A/B/C는 역할 별칭이다.

**A 확인 2건**

1. 04 보고서의 `brand_name`은 입력 strategy_draft에 없다. 기존 프롬프트가 가정한
   원본 브랜드명 전달 방법을 확정해야 한다.
2. 07은 두 입력이 필요하지만 추천 API의 `input_schema_id`는 단일 값이다.
   입력을 campaign만으로 표기하지 않고 null로 남겼다. `input_ports`는 인계 메타이며
   API 필드가 아니다. A의 `demo-workflow.md`에서 병합 포트·마켓 등록 방식을 확인한다.

## 콜드스타트 시드 값 표

| 에이전트 | 성공 실행 | 공급자 실패 | 평가 수 | 평균 평점 | 성공률 | Score | Tier |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 브랜딩 전략 | 120 | 5 | 25 | 4.6 | 0.96 | 71.015 | 2 Gold |
| SNS 캠페인 | 0 | 0 | 0 | 없음 | 1.00 | 37.5 | 4 Bronze |
| 광고 카피 | 0 | 0 | 0 | 없음 | 1.00 | 37.5 | 4 Bronze |
| 통합 실행안 | 0 | 0 | 0 | 없음 | 1.00 | 37.5 | 4 Bronze |

A의 제안 수식 `ai/docs/grading-formula.md` v1.0을 그대로 검산했다.
브랜딩 1개만 시드 주입하는 시나리오이며 나머지는 신규 상태다.
성공률 분모는 성공 120 + 공급자 실패 5 = 125다. 플랫폼·수요자 실패는 제외한다.
실제 등급 배치·DB 주입은 백엔드가 수행해야 하며 완료하지 않았다.

## 추천 사전 평가 결과

로컬 파인튜닝 모델 `abc-agent-retriever-v1`, CPU, 기존 시드 20개에 준비용 4개를 추가했다.
실제 서비스의 `AgentIndex`와 `final_score`를 사용했다. 서버 호출·DB 변경·가중치 다운로드 없음.
기존 도메인 평가의 브랜딩 질의 9개를 유지했으며, 그중 예산 질의 2개는 현재 설명문의
서비스 범위와 차이가 있다. 결과를 좋게 만들기 위해 질의를 삭제하지 않았다.

| 모드 | 브랜딩 Top1 / Top8 | 기존 골든 Top1 (추가 전 → 후) | holdout Top1 | holdout Top8 |
| --- | --- | --- | --- | --- |
| 파인튜닝 weighted + Trust | 8/9 · 9/9 | 22/22 → 22/22 | 70/80 → 68/80 | 80/80 → 80/80 |
| 파인튜닝 off + Trust | 8/9 · 9/9 | 22/22 → 22/22 | 66/80 → 66/80 | 80/80 → 80/80 |
| 파인튜닝 weighted + gated Trust 후보 | 8/9 · 9/9 | 22/22 → 22/22 | 70/80 → 68/80 | 80/80 → 80/80 |
| 사전학습 weighted + Trust | 7/9 · 9/9 | 21/22 → 21/22 | 58/80 → 58/80 | 79/80 → 78/80 |

브랜딩 상위 8과 기존 질의 상위 8 관문은 파인튜닝 두 모드에서 통과했다.
기존 에이전트 공개 메타데이터에 `학부모 대화`·`상담 문서`, `PR`·`풀 리퀘스트`를
보강하자 이전 골든 Top1 1건과 holdout Top8 1건 회귀가 해소됐다.

그러나 weighted에서 브랜딩 에이전트를 추가하면 holdout Top1이 70→68로 내려간다.
`PR 올리기 전에 한번 봐줘`, `feat인지 fix인지 정해줘`를 파인튜닝 모델이 브랜딩 설명과
가깝게 보는 것이 원인이다. Trust를 관련도에 곱하는 후보 수식으로도 결과가 같아
랭킹 수식만의 문제는 아니다. 사전학습 모델은 전체 절대 성능이 더 낮다.
off 모드는 추가 전후 회귀가 없지만 추가 후 절대 성능이 66/80으로 weighted의 68/80보다 낮다.
따라서 **weighted·파인튜닝 설정을 유지**하고, 모델 재학습 또는 평가 기준 합의 전까지
추천 최종 평가를 완료로 표시하지 않는다. 서비스 코드와 설정은 바꾸지 않았다.
실등록 설명문으로 재측정하고 관련 실패 질의를 검토한 뒤 프롬프트/시연 동결을 결정한다.

`evaluate_brand_recommend.py`의 종료 코드는 브랜딩 Top8 및 기존 Top1 비하락 조건이다.
off 모드 종료 코드가 0이어도 위 holdout Top8 하락과 수동 검토 항목이 없어지는 것은 아니다.

## 재현 명령

저장소 루트 기준. 평가에는 로컬 `sentence-transformers` 의존성과 파인튜닝 가중치가 필요하다.

```powershell
python ai/demo/prepare_brand_demo.py
python -m unittest discover -s ai/demo -p 'test_*.py' -v

# 기본 서비스 설정(weighted)
$env:ABC_HYBRID='weighted'
& ai/recommend/venv/Scripts/python.exe ai/demo/evaluate_brand_recommend.py

# 서비스 미적용 후보 수식·사전학습 모델 대조
& ai/recommend/venv/Scripts/python.exe ai/demo/evaluate_brand_recommend.py --rank-mode gated --out ai/demo/brand/prepared/recommend_local_gated_report.json
& ai/recommend/venv/Scripts/python.exe ai/demo/evaluate_brand_recommend.py --model pretrained --out ai/demo/brand/prepared/recommend_local_pretrained_report.json

# 비교 실행 (현재 터미널 환경만 변경)
$env:ABC_HYBRID='off'
& ai/recommend/venv/Scripts/python.exe ai/demo/evaluate_brand_recommend.py --out ai/demo/brand/prepared/recommend_local_off_report.json
$env:ABC_HYBRID='weighted'
```

기존 `ai/recommend/data/schema_catalog.json`에는 과거 교육 UC 스키마가 포함돼 있으므로
이번 생성 파일로 덮어쓰지 않았다. 현 서비스는 스키마 설명을 임베딩에 넣지 않는다.
역사적 평가에 사용한 `brand_agents_draft.json`도 이번 실측과 구분해 보존했다.

## 디자인 샘플

외부 이미지·로고·템플릿을 복제하지 않은 자체 레이아웃이다. 시스템 한글 글꼴을 사용했다.
두 PDF의 페이지별 PNG 미리보기를 함께 제공한다. A4 2페이지, 한글·줄바꿈·잘림을 확인했다.
합성 브랜드의 사람이 작성한 예시이며 LLM 실측 결과나 동적 바인딩이 끝난 완성 템플릿은 아니다.
Runtime의 템플릿 추출·필드 매핑은 A 영역이므로 디자인 참고 자료로 전달한다.

1쪽은 `brand_name`, `headline`, `issued_on`, `author_org`, `purpose` 3개,
`messages` 3종, `positioning` 3개를 보여 준다. 2쪽은 `core_strategy` 3개,
`reasons` 3개와 `summary_md`를 보여 준다. `biz.brand_strategy@1`의 표시 필드를
빠짐없이 확인할 수 있지만 동적 바인딩은 구현하지 않은 정적 디자인 샘플이다.

## 실제 등록 후 화요일 확인 순서

1. 판매자 3개 계정에서 4개 에이전트를 등록하고 실제 ID·공개 설명문·버전을 기록한다.
2. 백엔드에서 `POST /v1/index/agents`를 `op=upsert`로 호출한다.
   준비용 ID를 전송하지 않는다. 인증은 환경변수 `ABC_SERVICE_TOKEN`으로 주입한다.
   전체 인덱스를 지우는 rebuild는 이 4개만 가진 상태에서 사용하지 않는다.
3. 백엔드 컨테이너에서 추천 컨테이너 `/health`의 status와 model_version을 확인한다.
4. 실제 등록 ID로 정답 라벨을 바꾸고, 동일 브랜딩·골든·holdout 질의를 평가한다.
5. 성공률·등급은 백엔드 실적과 일치하는지 대조한다. 합성 시드는 시연 환경임을 표시한다.

## 수요일 리허설 체크리스트

- [ ] high·low 브리프 각각으로 01 반복 10회 분기 안정성 확인
- [ ] 1단계 스키마 준수율 ≥95% 및 사람 검토 완료
- [ ] 04 브랜드명 전달, 07 두 입력 합류를 실제 워크플로우에서 확인
- [ ] 사용자 브리프 → 브랜드 전략 → SNS·카피 → 실행안까지 시연
- [ ] 실행안의 채널·헤드라인이 원본 입력과 일치하는지 확인
- [ ] 공개 설명문·실제 ID로 추천 최종 평가 및 회귀 점검
- [ ] 실제 템플릿 출력에서 디자인 샘플의 한글·배치·칸 수 확인
- [ ] 통과한 프롬프트 커밋 ID와 평가 리포트 경로 기록 후 동결

현재 테스트 20개 통과. 위 체크리스트는 외부 연동 및 실호출이 필요해 미완료로 남겨 둔다.
