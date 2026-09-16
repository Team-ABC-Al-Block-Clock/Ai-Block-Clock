"""
합성 질의 420개를 학습용 / 평가용으로 나눈다 (AI-10-10 1단계)

왜 나누는가.
  파인튜닝은 "학습에 쓴 데이터"에서는 항상 좋아 보인다. 외운 것을 다시 물었기
  때문이다. 성능이 정말 올랐는지 말하려면 **학습에서 한 번도 안 본 질의**로
  재야 한다. 그게 holdout 이다.

  09-08 에 만든 골든 쿼리 22개는 이미 MRR 1.000 이라 개선 폭을 보여줄 수 없다.
  (천장에 닿아 있다.) 그래서 합성 질의에서 80개를 따로 떼어 두 번째 평가 세트로
  쓴다. 골든 쿼리는 "떨어지지 않았는지" 확인하는 안전망 역할만 한다.

나누는 방식 — 에이전트별 균등(stratified)
  단순 무작위로 80개를 뽑으면 어떤 에이전트는 평가 질의가 0개가 될 수 있다.
  그러면 그 에이전트에 대해서는 아무것도 측정하지 못한다.
  에이전트 20개 × 4개씩 = 80개로 고정해 모든 에이전트를 똑같이 재게 한다.

  seed 를 고정해 몇 번을 돌려도 같은 분할이 나오게 한다. 분할이 달라지면
  before / after 비교가 무의미해진다.

실행:
    cd ai/recommend/training
    python split_holdout.py
"""

import json
import random
from collections import defaultdict
from pathlib import Path

from catalog import load_agents, load_queries

DATA_DIR = Path(__file__).resolve().parents[1] / "data"
SRC = DATA_DIR / "synthetic_queries.jsonl"
TRAIN_OUT = DATA_DIR / "train_pairs.jsonl"
HOLDOUT_OUT = DATA_DIR / "holdout_pairs.jsonl"

SEED = 42
PER_AGENT_HOLDOUT = 4


def read_jsonl(path: Path) -> list[dict]:
    with path.open(encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def write_jsonl(path: Path, rows: list[dict]) -> None:
    with path.open("w", encoding="utf-8") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")


def main() -> None:
    pairs = read_jsonl(SRC)
    agent_ids = {a["agent_id"] for a in load_agents()}
    print(f"합성 질의 {len(pairs)}개 · 에이전트 {len(agent_ids)}개")

    # ── 검증 1. 라벨이 실제 에이전트를 가리키는가 ──
    bad = [p for p in pairs if p["positive_agent_id"] not in agent_ids]
    if bad:
        raise SystemExit(f"존재하지 않는 agent_id 를 가리키는 질의 {len(bad)}개: {bad[:3]}")

    # ── 검증 2. 골든 쿼리가 섞여 있지 않은가 (평가 세트 오염) ──
    golden = {q["query"].strip() for q in load_queries()}
    leaked = [p for p in pairs if p["query"].strip() in golden]
    if leaked:
        raise SystemExit(
            f"골든 쿼리 {len(leaked)}개가 합성 질의에 섞여 있습니다. "
            f"학습에 쓰면 평가가 오염됩니다: {[p['query'] for p in leaked[:3]]}"
        )

    # ── 에이전트별로 모아서 균등하게 뽑는다 ──
    by_agent: dict[str, list[dict]] = defaultdict(list)
    for p in pairs:
        by_agent[p["positive_agent_id"]].append(p)

    rng = random.Random(SEED)
    train, holdout = [], []
    for agent_id in sorted(by_agent):                    # sorted → 순서도 재현 가능
        group = by_agent[agent_id][:]
        rng.shuffle(group)
        if len(group) <= PER_AGENT_HOLDOUT:
            raise SystemExit(f"{agent_id} 의 질의가 {len(group)}개뿐이라 분할할 수 없습니다")
        holdout += group[:PER_AGENT_HOLDOUT]
        train += group[PER_AGENT_HOLDOUT:]

    rng.shuffle(train)

    # ── 검증 3. 두 세트에 같은 문장이 들어가지 않았는가 ──
    train_texts = {p["query"].strip() for p in train}
    overlap = [p for p in holdout if p["query"].strip() in train_texts]
    if overlap:
        raise SystemExit(
            f"학습·평가 세트에 같은 문장 {len(overlap)}개가 겹칩니다: "
            f"{[p['query'] for p in overlap[:3]]}"
        )

    write_jsonl(TRAIN_OUT, train)
    write_jsonl(HOLDOUT_OUT, holdout)

    print(f"\n학습용  {len(train):>3}개 → {TRAIN_OUT.name}")
    print(f"평가용  {len(holdout):>3}개 → {HOLDOUT_OUT.name}  (에이전트당 {PER_AGENT_HOLDOUT}개)")
    print(f"\n검증 통과 — 라벨 정상 · 골든 쿼리 미포함 · 학습/평가 문장 중복 0건")


if __name__ == "__main__":
    main()
