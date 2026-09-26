# ai/ — ABC AI Runtime · 추천 엔진

ABC(AI Block Clock)의 AI 파트 코드가 이 폴더 아래에 모인다. 프론트(`develop-FE`) · 백엔드(`develop-BE`)와 같은 저장소를 쓰되 브랜치는 `develop-AI`로 분리하며, `feat/*`는 `develop-AI`에서 분기해 `develop-AI`로 병합한다. 브랜치 · 커밋 · MR 규칙은 루트의 [`git-workflow.md`](../git-workflow.md)를 따른다.

설계 문서(AI 파트 가이드, 4주 로드맵)는 팀 문서 폴더에 있고, 이 폴더의 `docs/`에는 다른 파트에 배포하는 **규격 · 명세 산출물**만 둔다.

## 무엇을 만드나

| 서비스 | 역할 | 배포 |
| --- | --- | --- |
| **runtime** | 워크플로우 실행 엔진. 그래프 해석 → 노드 순차 실행(앞 결과 전달 → LLM 호출 → 선언한 출력 형식 검증) → 분기 · 에이전트 전개 → 실패 책임 판정 · 기여 노드 · 토큰 · USD 원가 반환(ABC 환산은 백엔드). H2A(엑셀 파싱) · A2A2H(PDF 렌더링) 포함 | EC2 컨테이너 `runtime-api` |
| **recommend** | 자연어 에이전트 추천(WRK-017). 허깅페이스 한국어 임베딩 모델을 파인튜닝해 EC2 CPU에서 직접 추론. 인메모리 벡터 인덱스 | EC2 컨테이너 `runtime-recommend` (모델은 이 프로세스에만 로드) |

두 서비스 모두 **stateless**다. 실행 이력 · 노드 출력값 · 정산은 백엔드(Spring + MySQL)가 저장하고, Runtime은 요청에 담긴 것만으로 실행해 결과를 돌려준다. 추천 인덱스는 백엔드 데이터로 언제든 재구성되는 캐시로 취급한다.

## 폴더 구조

```
ai/
├── runtime/                  실행 엔진 (Python 3.11 + FastAPI + Pydantic v2)
│   ├── app/
│   │   ├── api/              라우터 — /v1/validate · /v1/estimate · /v1/executions · /v1/nodes/test · /v1/agents/verify
│   │   ├── core/             설정 · 서비스 토큰 인증 · 로깅 · 프롬프트 마스킹 필터(단일 출구)
│   │   ├── schemas/          Pydantic 모델 — A2A 봉투 · 견본 카탈로그(출력 선언 · 사용자 입력 폼용) · 노드 매니페스트 · 실행 이벤트 · Runtime 계약 (JSON Schema의 원본)
│   │   ├── engine/           오케스트레이터 — 그래프 정규화 · 위상 정렬 · 순환/규모 검사 · 분기 평가 · 에이전트 전개
│   │   ├── nodes/            노드 실행기 — 모델 어댑터 · 레지스트리 · 프롬프트 조립 · 구조화 출력 · 검증 루프 · 타임아웃 · 재시도
│   │   ├── io/               H2A(엑셀 · CSV 파서, 파일 검증) · A2A2H(HTML 템플릿 → PDF, 차트)
│   │   ├── settlement/       실패 책임 판정(supplier / consumer / platform) · 기여 노드 목록 · 에이전트별 토큰 · USD 집계
│   │   └── events/           실행 이벤트 발행 — 백엔드 콜백 · seq · HMAC 서명 · 재시도
│   └── tests/
├── recommend/                추천 엔진 (Python 3.11 + FastAPI + sentence-transformers)
│   ├── app/
│   │   ├── api/              /v1/recommend · /v1/index/agents
│   │   ├── index/            인메모리 numpy 인덱스 · 스냅샷(.npz) · 재색인
│   │   └── ranking/          Semantic · Trust · (여유 시) SchemaFit · BM25 + RRF
│   ├── training/             합성 질의 생성(GPL) · 하드 네거티브 채굴 · 파인튜닝 · 전/후 평가 스크립트 (GPU 개발 서버에서 실행)
│   └── data/                 골든 쿼리 20개 · 시드 에이전트 카탈로그 시트 · 합성 질의 JSONL  ※ 모델 가중치는 커밋하지 않음
├── schemas/                  다른 파트에 배포하는 JSON Schema 산출물 (Pydantic에서 export). 프론트 Zod · 백엔드가 이 파일을 원본으로 삼는다
│   ├── envelope/  ├── catalog/  ├── manifest/  └── events/
├── prompts/                  시연 에이전트 프롬프트 초안 (등록 전 작업용). 등록 후에는 백엔드 DB가 원본
└── docs/                     A2A 규격 명세 · Runtime API 명세 · 실행 이벤트 명세 · 등급 산정 수식 명세 (배포용)
```

## 규칙

- **판정은 코드, 제안은 모델.** 스키마 검증 · 실패 책임 판정 · 분기 평가 · 견적에 LLM을 쓰지 않는다. 돈 계산의 근거는 결정적이어야 한다.
- **입력 형식은 없다. 출력은 판매자가 선언한다.** 노드는 앞 노드의 결과 전체를 받고, 출력은 글(`text`) 또는 견본 JSON(`sample`)이다. 연결 호환 판정을 다시 넣지 않는다(계약 v2.0 · `docs/integration-guide.md` §5).
- **Runtime은 토큰 · USD까지만 센다.** ABC · 단가 · 환불액은 백엔드가 계산한다.
- **프롬프트 원문은 어디에도 남기지 않는다.** 응답 · 로그 · 예외 · 임베딩 텍스트에 프롬프트가 섞이면 SEC-001 위반이다. 마스킹은 `runtime/app/core`의 단일 출구에서 한다. 백엔드는 `prompt_ref`만 넘기고 원문은 Runtime이 조회한다.
- **스키마의 원본은 Pydantic.** `runtime/app/schemas`에서 `model_json_schema()`로 뽑은 파일을 `ai/schemas/`에 커밋하고 프론트 · 백엔드에 통지한다. 수작업 이중 관리 금지.
- **비밀은 코드 · 이미지에 넣지 않는다.** 외부 LLM API 키, 백엔드 ↔ Runtime 공유 시크릿은 EC2 환경변수로 주입한다. `.env` 파일은 커밋하지 않는다.
- **모델 가중치 · 학습 산출물은 커밋하지 않는다.** `recommend/data`에는 텍스트 데이터(골든 쿼리 · 카탈로그 · 합성 질의)만 둔다.
- **W3 수요일 직선(H2A → 분석 → 텍스트 → PDF)은 항상 돌아야 한다.** 새 기능을 붙일 때마다 확인한다.

## 통신 요약

```
프론트 ──REST──▶ 백엔드(Spring) ──REST──▶ runtime-api ──HTTPS──▶ 외부 LLM API
   ▲                 │  ▲                     │
   └──── SSE ────────┘  └── 실행 이벤트 콜백 ───┘
                       └──REST──▶ runtime-recommend (임베딩 모델 상주)
```

- 백엔드 → Runtime: `X-ABC-Service-Token` 헤더
- Runtime → 백엔드 콜백: `X-ABC-Signature`(HMAC-SHA256) + `seq` 단조 증가
- 실행 트리거는 REST + 백그라운드 실행으로 시작하고, 여유가 있을 때 Redis Streams 컨슈머로 전환한다

## 로컬 개발 (예정)

- Python 3.11, `uv`로 의존성 관리. 각 서비스 폴더에 `pyproject.toml`과 `Dockerfile`을 둔다
- 루트 `docker-compose`에 `runtime-api` · `runtime-recommend`를 추가한다 (백엔드 compose와 조율)
- 파인튜닝 · 후보 모델 비교는 GPU 개발 서버(Jupyter)에서 하고, 가중치 파일만 EC2로 복사해 서빙한다

## 상태

- [x] 폴더 구조 · README
- [ ] A2A 규격 v1 + 노드 매니페스트 예시 4개 → `schemas/`, `docs/`
- [ ] Runtime 계약(요청 · 응답 · 이벤트) 명세 → `docs/`
- [ ] FastAPI 스캐폴딩 · Dockerfile
- [ ] 노드 실행기 · `/v1/nodes/test`
- [ ] 임베딩 모델 선정 · 골든 쿼리 · 파인튜닝 1차
- [ ] 추천 API
