"""추천 서비스 설정 (runtime-recommend 컨테이너)."""

import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parents[1]      # ai/recommend
DATA_DIR = BASE_DIR / "data"
MODEL_DIR = BASE_DIR / "models"                     # 파인튜닝 가중치 (git 제외)

# ── 모델 선택 ────────────────────────────────────────────────
# 파인튜닝 가중치가 있으면 그것을, 없으면 선정한 사전학습 모델을 쓴다.
# 09-11 파인튜닝이 끝나면 models/ 에 가중치만 넣으면 코드 수정 없이 교체된다.
PRETRAINED = "jhgan/ko-sroberta-multitask"          # 09-09 선정 (log.md 참고)
FINETUNED_DIR = MODEL_DIR / "abc-agent-retriever-v1"

if FINETUNED_DIR.exists():
    MODEL_PATH = str(FINETUNED_DIR)
    MODEL_VERSION = "abc-agent-retriever-v1"
else:
    MODEL_PATH = PRETRAINED
    MODEL_VERSION = "ko-sroberta-multitask-pretrained"

# 가중치가 바뀌면 벡터 공간이 바뀌므로 전체 재색인이 필요하다 (가이드 N-22).
# 인덱스 레코드에 MODEL_VERSION 을 기록하고, 불일치하면 재색인을 요청한다.

# ── 인덱스 ───────────────────────────────────────────────────
SNAPSHOT_PATH = BASE_DIR / "index_snapshot.npz"     # 재기동 가속용 (git 제외)

# ── 랭킹 가중치 (가이드 §12.6) ────────────────────────────────
W_SEMANTIC = 0.7
W_TRUST = 0.3

# ── API ──────────────────────────────────────────────────────
TOP_K_DEFAULT = 8
TOP_K_MAX = 20

# 백엔드 → Runtime 인증 (가이드 §8.1). 비어 있으면 검사하지 않는다(로컬 개발용).
SERVICE_TOKEN = os.environ.get("ABC_SERVICE_TOKEN", "")
