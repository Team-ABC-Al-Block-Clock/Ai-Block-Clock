# 추천 엔진 API 명세 (runtime-recommend)

| 항목 | 내용 |
| --- | --- |
| 담당 | AI 파트 트랙 B |
| 요구사항 | **WRK-017** (자연어 에이전트 추천 · 필수 · MVP) |
| 서비스 | `runtime-recommend` 컨테이너 — 실행 엔진(`runtime-api`)과 **별도 프로세스** |
| 포트 | `8100`. 운영은 `127.0.0.1` 루프백에만 바인딩 (ai-runtime 과 동일) |
| 모델 | `jhgan/ko-sroberta-multitask` 파인튜닝. 응답의 `model_version` 으로 식별 |
| 배포 | **09-18 EC2 배포 완료** — 컨테이너 `ai-block-clock-recommend`. 8절 참고 |
| 상태 | v0.2 — 필드 변경 시 이 문서와 `app/schemas.py` 를 함께 고치고 통지한다. 09-25 트랙 A 주석 1건(§3 스키마 ID 필드의 용도) |

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
        "lexical": 12.4100,            // BM25 어휘 점수. 하이브리드가 꺼져 있으면 null
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
- **`breakdown` 은 진단용이다. 화면에 그대로 뿌리지 말 것.** `semantic` 은 0~1,
  `lexical`(BM25)은 상한이 없어 스케일이 다르다. 사용자에게 보여줄 값은 `score` 뿐이다
- `breakdown.lexical` 은 **09-17 에 추가된 필드**다. nullable 이라 기존 파싱은 깨지지
  않는다. 하이브리드 검색이 꺼져 있으면 `null` 이 온다
- `input_schema_id` · `output_schema_id` 는 **표시용**이다 — 입력은 진입 노드의 사용자 입력 폼
  견본, 출력은 종단 노드의 출력 견본(글이면 `text`). 09-25 Runtime 계약 v2.0에서 연결 호환
  판정이 없어졌으므로 캔버스 판정에는 쓰지 않는다. 추천 목록 화면에 노출할 필요는 없다
- 결과가 0개일 수 있다. 인덱스가 비어 있거나 필터가 과한 경우다.
  빈 목록일 때의 화면을 준비해 둘 것
- **랜딩 문구 주의.** 현 시안의 "엔터를 누르면 에이전트 생성이 시작됩니다"는
  없는 것을 만들어 준다는 약속이라 이 기능과 맞지 않는다.
  **"알맞은 에이전트를 찾아드립니다"** 로 변경 요청 중이다 (가이드 N-17)

### 어떻게 찾는가 — 임베딩 + 어휘 (09-17)

의미 검색(임베딩)에 **BM25 어휘 점수를 0.05 비중으로 얹는다.** 임베딩은 단어가
겹치지 않아도 찾아내는 게 강점인데, 뒤집으면 단어가 정확히 겹치는 경우를 특별히
대우하지 않는다. 그 빈틈을 어휘 점수가 메운다.

| | 골든 쿼리 22개 | holdout 80개 |
| --- | --- | --- |
| 임베딩 단독 | 100% | 82.5% |
| **임베딩 + 어휘** | **100%** | **86.2%** |

근거는 `ai/recommend/training/results/eval_hybrid.json`.
`ABC_HYBRID=off` 로 언제든 임베딩 단독으로 되돌릴 수 있다.

### 성능

- 목표 **1초 이내**. 실측은 에이전트 20개 기준 **20~40ms** (CPU)
- 첫 요청이 느리지 않다. 모델은 프로세스 기동 시 한 번만 로드한다
- BM25 는 파이썬 기본 연산이고 에이전트 20~30개 규모라 지연에 영향이 없다

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

## 8. 배포된 서비스에 붙기 (백엔드용)

09-18 EC2 배포를 마쳤다. 백엔드에서 아래 주소로 바로 부르면 된다.

```
http://ai-block-clock-recommend:8100
```

### 왜 IP 가 아니라 이름인가

두 컨테이너가 같은 Docker 네트워크(`ai-block-clock_ai-block-clock-network`)에 있어서
**컨테이너 이름이 그대로 호스트명으로 해석된다.** IP 는 컨테이너가 재기동할 때마다
바뀌므로 쓰면 안 된다. ai-runtime 을 `http://ai-block-clock-ai-runtime:8000` 으로
부르는 것과 같은 방식이다.

### 외부에서는 닿지 않는다 — 의도한 것

`127.0.0.1:8100` 루프백에만 바인딩했다. 브라우저·외부에서 직접 못 부른다.

- **Nginx 설정이 필요 없다.** 프론트는 백엔드를 거쳐서만 추천을 받는다
- 열어 두면 인증 없이 인덱스를 조작할 통로가 생긴다. 추천 순위를 조작하면
  특정 에이전트의 정산금을 늘릴 수 있다 (2절과 같은 이유)

서버에 SSH 로 들어가 확인할 때만 이렇게 쓴다.

```bash
curl -s http://127.0.0.1:8100/health
```

### 붙이기 전 확인 순서

```bash
# ① 서비스가 떠 있는가
curl -s http://127.0.0.1:8100/health

# ② 백엔드와 같은 네트워크에서 이름으로 닿는가
docker run --rm --network ai-block-clock_ai-block-clock-network \
  curlimages/curl -s http://ai-block-clock-recommend:8100/health
```

둘 다 같은 JSON 이 나와야 한다. ②가 실패하면 네트워크 이름이 달라진 것이니
`docker network ls` 로 실제 이름을 확인한다.

### ⚠️ 지금 상태 (09-18 기준)

| 항목 | 현재 | 백엔드가 할 일 |
| --- | --- | --- |
| `indexed_agents` | **0** | `POST /v1/index/agents` 에 `op: "rebuild"` 로 공개 에이전트 전체를 보내야 추천이 나온다 |
| `model_version` | `abc-agent-retriever-v1` | 이 값이 바뀌면 다시 `rebuild` (4절 참고) |
| `X-ABC-Service-Token` | **미설정** | CI Variables 의 `ABC_SERVICE_TOKEN` 주입 후 켠다. 그때까지는 헤더 없이도 통과한다 |

**색인을 넣기 전에는 `recommendations` 가 빈 배열로 나온다.** 오류가 아니다.
`/health` 의 `indexed_agents` 를 먼저 확인하면 헛걸음을 줄일 수 있다.

### 모델 가중치는 이미지에 없다

파인튜닝 가중치(423MB)는 EC2 호스트의 `/opt/abc/models` 에 두고 볼륨으로 넣는다.
이미지에 구우면 모델을 바꿀 때마다 전체를 다시 빌드·전송해야 하기 때문이다.

**서버를 새로 만들거나 이 디렉터리가 비면 컨테이너가 기동 단계에서 죽는다.**
사전학습 모델로 조용히 대체되지 않게 일부러 그렇게 뒀다 — 벡터 공간이 다른 모델로
돌면 추천이 미묘하게 어긋나는데, 그건 시연 중에 원인을 찾을 수 없다.

---

## 9. 아직 없는 것 (v2 또는 이후)

| 항목 | 상태 |
| --- | --- |
| SchemaFit 랭킹 항 — 이미 고른 에이전트와 연결 가능한지 | W3 여유 시 |
| ~~하이브리드 검색 (BM25 + 임베딩)~~ | **09-17 도입 완료** — 3절 참고. RRF 는 측정 결과 탈락, 가중합 채택 |
| `suggested_chain` — 스키마가 이어지는 조합 제안 | v2 |
| 기본 노드 추천 (현재는 마켓 공개 에이전트만) | v2 |
| 자연어 → 워크플로우 자동 생성 (R2 · WRK-018 신설 요청) | v2 |
