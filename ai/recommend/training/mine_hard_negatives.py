"""
하드 네거티브 채굴 (AI-10-10 2단계)

네거티브가 무엇인가.
  학습은 "이 질의에는 이 에이전트가 맞다"만으로는 잘 안 된다.
  "이건 아니다"도 같이 줘야 모델이 경계를 배운다. 그 오답이 네거티브다.

왜 아무 오답이나 쓰면 안 되는가 — 하드 네거티브
  '학원 성적 분석' 질의에 '이미지 리사이즈'를 오답으로 주면 모델은 배울 게 없다.
  이미 확실히 구분하고 있기 때문이다. 기울기가 거의 0이라 학습이 안 일어난다.

  대신 **지금 모델이 실제로 헷갈려 하는 것**을 준다. 현재 모델로 검색했을 때
  정답 바로 아래에 올라온 오답들이다. 이게 하드 네거티브고, 우리 데이터에는
  일부러 심어둔 유사 쌍(ag_seed_01 성적 데이터 분석기 ↔ ag_seed_03 학업 성취도
  통계 산출기)이 있어서 이 방식이 잘 맞는다.

  ★ 이번 파인튜닝의 목표 지표가 '1·2등 격차'인 이유가 여기 있다.
    순위는 이미 다 맞히고 있으니(MRR 1.000) 남은 개선 여지는
    "정답과 2등을 얼마나 벌려 놓느냐"다. 하드 네거티브는 정확히 그걸 벌린다.

왜 파인튜닝 전 모델로 채굴하는가.
  학습이 시작되기 전 시점의 "헷갈림"을 기준으로 삼아야 한다.
  학습 도중 계속 다시 캐는 방법(iterative mining)도 있지만 2주 일정에는 과하다.

실행:
    cd ai/recommend/training
    python mine_hard_negatives.py
"""

import json
from pathlib import Path

import numpy as np
from sentence_transformers import SentenceTransformer

from catalog import build_agent_text, load_agents, load_schemas

DATA_DIR = Path(__file__).resolve().parents[1] / "data"
TRAIN_IN = DATA_DIR / "train_pairs.jsonl"
TRIPLET_OUT = DATA_DIR / "train_triplets.jsonl"

BASE_MODEL = "jhgan/ko-sroberta-multitask"

# 질의 하나당 네거티브 몇 개를 뽑을지.
# 에이전트가 20개뿐이라 많이 뽑으면 상관없는 것까지 딸려온다. 2개면 충분하다.
N_NEGATIVES = 2

# ★ 인덱싱과 같은 규칙으로 텍스트를 만든다.
#   app/index/store.py 의 build_agent_text() 는 스키마 설명을 넣지 않는다
#   (09-09 결정: 넣으나 빼나 점수가 같은데 128토큰 잘림만 생겼다).
#   학습 때와 서비스 때 텍스트가 다르면 학습한 것과 다른 것을 검색하게 된다.
INCLUDE_SCHEMA = False


def read_jsonl(path: Path) -> list[dict]:
    with path.open(encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def main() -> None:
    if not TRAIN_IN.exists():
        raise SystemExit(f"{TRAIN_IN.name} 이 없습니다. 먼저 split_holdout.py 를 실행하세요.")

    pairs = read_jsonl(TRAIN_IN)
    agents = load_agents()
    schemas = load_schemas()

    print(f"모델 로딩: {BASE_MODEL}")
    model = SentenceTransformer(BASE_MODEL)

    agent_ids = [a["agent_id"] for a in agents]
    agent_text = {
        a["agent_id"]: build_agent_text(a, schemas, include_schema=INCLUDE_SCHEMA)
        for a in agents
    }
    agent_name = {a["agent_id"]: a["name"] for a in agents}

    # normalize=True → 벡터 길이가 1. 코사인 유사도가 내적 한 번으로 끝난다
    agent_vecs = model.encode(
        [agent_text[i] for i in agent_ids], normalize_embeddings=True, show_progress_bar=True
    )
    query_vecs = model.encode(
        [p["query"] for p in pairs], normalize_embeddings=True, show_progress_bar=True
    )

    sims = query_vecs @ agent_vecs.T            # (질의 수 × 에이전트 수)
    id_to_col = {aid: i for i, aid in enumerate(agent_ids)}

    triplets = []
    confusion: dict[tuple[str, str], int] = {}
    rank1_miss = 0

    for row, pair in zip(sims, pairs):
        pos_id = pair["positive_agent_id"]
        order = np.argsort(-row)                # 점수 높은 순

        if agent_ids[order[0]] != pos_id:
            rank1_miss += 1

        picked = 0
        for col in order:
            cand = agent_ids[col]
            if cand == pos_id:
                continue
            triplets.append({
                "anchor": pair["query"],
                "positive": agent_text[pos_id],
                "negative": agent_text[cand],
            })
            confusion[(pos_id, cand)] = confusion.get((pos_id, cand), 0) + 1
            picked += 1
            if picked >= N_NEGATIVES:
                break

    with TRIPLET_OUT.open("w", encoding="utf-8") as f:
        for t in triplets:
            f.write(json.dumps(t, ensure_ascii=False) + "\n")

    print(f"\n학습 질의 {len(pairs)}개 → 삼중항 {len(triplets)}개 저장 → {TRIPLET_OUT.name}")
    print(f"파인튜닝 전 모델의 학습셋 1등 오답률: {rank1_miss}/{len(pairs)}"
          f" ({rank1_miss / len(pairs):.1%})")

    print("\n가장 자주 헷갈린 쌍 (정답 → 잘못 올라온 것)")
    for (pos, neg), n in sorted(confusion.items(), key=lambda x: -x[1])[:8]:
        print(f"  {n:>3}회   {agent_name[pos]}  →  {agent_name[neg]}")
    print("\n이 목록이 파인튜닝으로 벌려야 할 대상이다. 학습 후 1·2등 격차로 확인한다.")


if __name__ == "__main__":
    main()
