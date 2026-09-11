# 추천 엔진 API 명세 (runtime-recommend)

| 항목 | 내용 |
| --- | --- |
| 담당 | AI 파트 트랙 B |
| 요구사항 | **WRK-017** (자연어 에이전트 추천 · 필수 · MVP) |
| 서비스 | `runtime-recommend` 컨테이너 — 실행 엔진(`runtime-api`)과 **별도 프로세스** |
| 기본 포트 | 개발 `8100` (배포 시 compose 에서 조정) |
| 모델 | `jhgan/ko-sroberta-multitask` 파인튜닝. 응답의 `model_version` 으로 식별 |
| 상태 | 초안 v0.1 — 필드 변경 시 이 문서와 `app/schemas.py` 를 함께 고치고 통지한다 |

---

## 1. 무엇을 하는 서비스인가

사용자가 랜딩 화면에 **하고 싶은 업무를 자기 말로 입력**하면, 마켓에 공개된
에이전트 중 가장 알맞은 것을 찾아 돌려준다.

```
프론트  ──REST──▶  백엔드(Spring)  ──REST──▶  runtime-recommend
 랜딩 입력           중계 · 인증              임베딩 검색 + 랭킹
```

- 키워드 검색(MKT-002)과 다르다. **단어가 겹치지 않아도 찾는다.**
  "부모님께 보낼 안내문" → `가정통신문 작성기`
- LLM 을 호출하지 않는다. 임베딩 모델이 우리 서버 메모리에서 돌고 외부 호출은 0회다.
  덕분에 응답이 수십 ms 이고 검색마다 비용이 들지 않는다.

## 2. 인증

내부 호출이지만 인증이 필요하다. 인덱스를 누구나 조작할 수 있으면
추천 결과를 조작해 특정 에이전트의 정산금을 늘릴 수 있다.

```http
X-ABC-Service-Token: <백엔드와 공유하는 시크릿>
```

- 환경변수 `ABC_SERVICE_TOKEN` 으로 주입한다. 코드·이미지에 넣지 않는다 (SEC-003)
- 값이 비어 있으면 검사하지 않는다 — 로컬 개발용이며 배포 시에는 반드시 설정한다

---

## 3. `POST /v1/recommend` — 추천

### 요청

```jsonc
{
  "query": "학원 성적을 분석해서 학부모에게 보낼 리포트를 만들고 싶어",  // 필수
  "user_id": "u_1001",                 // 선택 (현재 미사용, 로깅용)
  "selected_agent_ids": [],            // 선택. 이미 캔버스에 올린 것은 추천에서 제외
  "top_k": 8,                          // 선택. 기본 8, 최대 20
  "filters": { "category": null }      // 선택. 카테고리 하드 필터
}
```

### 응답

```jsonc
{
  "recommendations": [
    {
      "agent_id": "ag_seed_01",
      "name": "성적 데이터 분석기",
      "owner_id": "u_2001",
      "score": 0.8123,                 // 최종 점수 (정렬 기준)
      "breakdown": {
        "semantic": 0.8412,            // 임베딩 코사인 유사도
        "trust": 0.7300,               // 등급 · 성공률
        "schema_fit": null             // v2
      },
      "tier": 2,
      "success_rate": 0.96,
      "category": "교육",
      "input_schema_id": "edu.roster@1",
      "output_schema_id": "edu.analysis@1",
      "reason": "학생별 점수 표를 받아 이번 기간과 지난 기간을 비교합니다. …"
    }
  ],
  "model_version": "abc-agent-retriever-v1",
  "latency_ms": 31
}
```

### 프론트가 알아야 할 것

- **`reason` 을 목록의 설명 줄로 쓰면 된다.** 에이전트의 공개 설명문이다
- `score` 순으로 이미 정렬돼 있다. 프론트에서 다시 정렬하지 않아도 된다
- `input_schema_id` · `output_schema_id` 는 **캔버스에 배치한 뒤 연결 가능 여부를
  판정**할 때 쓴다 (WRK-005). 추천 목록 화면에 노출할 필요는 없다
- 결과가 0개일 수 있다. 인덱스가 비어 있거나 필터가 과한 경우다.
  빈 목록일 때의 화면을 준비해 둘 것
- **랜딩 문구 주의.** 현 시안의 "엔터를 누르면 에이전트 생성이 시작됩니다"는
  없는 것을 만들어 준다는 약속이라 이 기능과 맞지 않는다.
  **"알맞은 에이전트를 찾아드립니다"** 로 변경 요청 중이다 (가이드 N-17)

### 성능

- 목표 **1초 이내**. 실측은 에이전트 20개 기준 **20~40ms** (CPU)
- 첫 요청이 느리지 않다. 모델은 프로세스 기동 시 한 번만 로드한다

---

## 4. `POST /v1/index/agents` — 인덱싱

백엔드가 호출한다. 추천 대상 목록을 최신으로 유지하는 유일한 경로다.

### 요청

```jsonc
{
  "op": "upsert",                      // upsert | remove | rebuild
  "agents": [
    {
      "agent_id": "ag_31",
      "name": "성적 데이터 분석기",
      "description": "학생별 점수 표를 받아 …",
      "tags": ["교육", "통계"],
      "category": "교육",
      "input_schema_id": "edu.roster@1",
      "output_schema_id": "edu.analysis@1",
      "owner_id": "u_2001",
      "tier": 2,
      "success_rate": 0.96,
      "is_public": true
    }
  ],
  "agent_ids": []                      // op="remove" 일 때 지울 ID 목록
}
```

### 응답

```jsonc
{ "op": "upsert", "indexed": 1, "total": 21, "model_version": "abc-agent-retriever-v1" }
```

### 언제 호출하나 (가이드 §12.8)

| 시점 | `op` |
| --- | --- |
| 에이전트 등록 검증 통과 · 마켓 공개 (AGT-008) | `upsert` |
| 메타데이터 수정 (AGT-003) | `upsert` |
| 비공개 전환 (AGT-008 · OI-07) | `remove` |
| 신규 버전 등록 (AGT-009) | `upsert` (구버전 교체) |
| 등급 배치 완료 (PAY-022) | `upsert` — `tier` · `success_rate` 갱신 |
| **Runtime 재기동 · 모델 버전 변경** | `rebuild` (공개 에이전트 전체) |

### ⚠️ 백엔드가 반드시 지켜야 할 것

**프롬프트 원문과 에이전트 내부 노드 구성을 절대 보내지 마세요.**

임베딩 벡터에서 원문이 어느 정도 복원될 수 있다는 연구가 있습니다. 프롬프트가
인덱스에 들어가면 "어떤 경로로도 노출되지 않아야 한다"(SEC-001)를 정면으로 어깁니다.
위 요청 예시에 있는 필드만 보내주세요 — 전부 마켓에 공개되는 정보입니다.

**재기동 시 `rebuild` 를 보내주세요.** 이 인덱스는 상태가 아니라 파생 캐시이고,
원본은 백엔드 MySQL 입니다. 서버가 내려갔다 올라오면 인덱스가 비어 있을 수 있습니다.
(스냅샷 파일로 복원을 시도하지만, 모델 버전이 바뀌면 버립니다.)

**`model_version` 이 바뀌면 전체 재색인이 필요합니다.** 파인튜닝으로 가중치가 바뀌면
벡터 공간 자체가 달라져서 기존 벡터와 새 질의를 비교할 수 없습니다. 응답의
`model_version` 이 이전과 다르면 `rebuild` 를 보내주세요.

---

## 5. `GET /health`

```jsonc
{ "status": "ok", "model_version": "abc-agent-retriever-v1", "indexed_agents": 20 }
```

- `status: "loading"` 이면 모델 로딩 중이다. 컨테이너 헬스체크에 쓸 수 있다
- `indexed_agents: 0` 이면 인덱스가 비어 있다 → `rebuild` 필요

---

## 6. 오류

| 상태 | 의미 |
| --- | --- |
| `401` | `X-ABC-Service-Token` 불일치 |
| `422` | 요청 본문이 스키마를 만족하지 않음 (FastAPI 기본 형식) |
| `500` | 서버 내부 오류 |

---

## 7. 로컬에서 확인하기

```bash
cd ai/recommend
python -m pip install -U sentence-transformers fastapi uvicorn httpx

# 터미널 1 — 서버
uvicorn app.main:app --reload --port 8100

# 터미널 2 — 시드 투입 + 추천 확인
python seed_index.py
```

`http://127.0.0.1:8100/docs` 에서 Swagger UI 로 직접 호출해 볼 수도 있다.

---

## 8. 아직 없는 것 (v2 또는 이후)

| 항목 | 상태 |
| --- | --- |
| SchemaFit 랭킹 항 — 이미 고른 에이전트와 연결 가능한지 | W3 여유 시 |
| 하이브리드 검색 (BM25 + 임베딩, RRF) | W3 여유 시 |
| `suggested_chain` — 스키마가 이어지는 조합 제안 | v2 |
| 기본 노드 추천 (현재는 마켓 공개 에이전트만) | v2 |
| 자연어 → 워크플로우 자동 생성 (R2 · WRK-018 신설 요청) | v2 |
