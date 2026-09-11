"""추천 서비스 (runtime-recommend) — FastAPI 앱.

실행:
    cd ai/recommend
    uvicorn app.main:app --reload --port 8100

★ 모델은 프로세스 기동 시 1회만 로드한다 (lifespan).
  09-08 실측에서 모델 로딩 6~8초 vs 질의 22ms 로 약 300배 차이가 났다.
  요청마다 로드하면 사용자가 매번 그 시간을 기다리게 된다 (가이드 §12.7).

★ 실행 워커(runtime-api)와 프로세스를 분리한다.
  임베딩 모델이 메모리를 1GB 쯤 쓰는데, 실행 워커에 같이 올리면
  워커 수 × 1GB 가 낭비된다 (가이드 N-21 · §14.3).
"""

from contextlib import asynccontextmanager

import numpy as np
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sentence_transformers import SentenceTransformer

from app.api.routes import router
from app.config import MODEL_PATH, MODEL_VERSION, SNAPSHOT_PATH
from app.index.store import AgentIndex


@asynccontextmanager
async def lifespan(app: FastAPI):
    print(f"[recommend] 모델 로딩: {MODEL_PATH}")
    model = SentenceTransformer(MODEL_PATH)

    def encode(texts: list[str]) -> np.ndarray:
        # normalize_embeddings=True → 벡터 길이를 1로. 코사인 유사도가 내적 한 번이 된다
        return model.encode(texts, normalize_embeddings=True, show_progress_bar=False)

    index = AgentIndex(encode_fn=encode, model_version=MODEL_VERSION)
    if index.load_snapshot(SNAPSHOT_PATH):
        print(f"[recommend] 스냅샷 복원 — 에이전트 {len(index)}개")
    else:
        print("[recommend] 스냅샷 없음 — 백엔드의 rebuild 요청을 기다립니다")

    app.state.model = model
    app.state.index = index
    app.state.model_version = MODEL_VERSION
    app.state.snapshot_path = SNAPSHOT_PATH
    print(f"[recommend] 준비 완료 · model_version={MODEL_VERSION}")

    yield

    index.save_snapshot(SNAPSHOT_PATH)
    print("[recommend] 종료 — 스냅샷 저장")


app = FastAPI(
    title="ABC 추천 엔진",
    description="자연어로 마켓의 에이전트를 찾아주는 서비스 (WRK-017)",
    version="0.1.0",
    lifespan=lifespan,
)

# 개발 중 프론트가 직접 호출해 볼 수 있게 열어 둔다.
# 운영에서는 백엔드만 호출하므로 배포 전에 좁힌다.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"], allow_methods=["*"], allow_headers=["*"],
)

app.include_router(router)
