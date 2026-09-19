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
# 재기동 가속용 스냅샷 (git 제외). 백엔드 데이터로 언제든 재생성되는 파생 캐시다.
#
# ★ 경로를 환경변수로 뺀 이유 (09-18 배포 준비).
#   컨테이너 안에 두면 컨테이너를 내릴 때 같이 사라진다. 그러면 재기동할 때마다
#   백엔드에 전체 재색인을 요청하게 되고, 에이전트가 늘수록 기동이 느려진다.
#   볼륨으로 마운트한 경로를 가리키게 해서 재기동을 넘겨 살아남게 한다.
SNAPSHOT_PATH = Path(os.environ.get("ABC_SNAPSHOT_PATH", BASE_DIR / "index_snapshot.npz"))

# ── 랭킹 가중치 (가이드 §12.6) ────────────────────────────────
W_SEMANTIC = 0.7
W_TRUST = 0.3

# ── 하이브리드 검색 (S15P21A403-280) ──────────────────────────
#   off       임베딩 단독
#   rrf       임베딩 · BM25 순위를 Reciprocal Rank Fusion 으로 합친다
#   weighted  임베딩 점수에 BM25(질의별 최댓값으로 정규화)를 가중합한다
#
# 09-17 측정 (training/results/eval_hybrid.json, model=abc-agent-retriever-v1)
#
#   모드              골든 22개        holdout 80개
#   off               100.0%           82.5%
#   rrf                86.4%           75.0%   ← 크게 악화
#   weighted 0.05     100.0%           86.2%   ← 채택
#   weighted 0.10      95.5%           87.5%
#   weighted 0.20      95.5%           83.8%
#
# ★ rrf 가 무너진 이유. RRF 는 점수를 버리고 순위만 쓴다. BM25 단독 성능이
#   임베딩보다 15%p 낮은데(holdout 67.5% vs 82.5%) 두 랭킹을 동등하게 취급하니
#   나쁜 쪽이 좋은 쪽을 끌어내린다. 성능 차가 큰 랭킹끼리는 RRF 로 합치면 안 된다.
#
# ★ W_LEXICAL 이 0.05 인 이유. 어휘 신호는 조금만 얹으면 이득이고 많이 얹으면
#   손해다. 0.10 은 holdout 이 더 오르지만(+4개) 골든 쿼리를 하나 잃는다.
#   사람이 쓴 골든 쿼리 100% 유지가 우선이라 0.05 를 골랐다.
#
# 시연 중 문제가 생기면 ABC_HYBRID=off 로 즉시 되돌린다.
HYBRID_MODE = os.environ.get("ABC_HYBRID", "weighted")   # off | rrf | weighted
RRF_K = 60              # 원 논문(Cormack 2009) 관례값
W_LEXICAL = 0.05        # weighted 모드에서 relevance 안의 어휘 비중

# ── API ──────────────────────────────────────────────────────
TOP_K_DEFAULT = 8
TOP_K_MAX = 20

# 백엔드 → Runtime 인증 (가이드 §8.1). 비어 있으면 검사하지 않는다(로컬 개발용).
SERVICE_TOKEN = os.environ.get("ABC_SERVICE_TOKEN", "")
