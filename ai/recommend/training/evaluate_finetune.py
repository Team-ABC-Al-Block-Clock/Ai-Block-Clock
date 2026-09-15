"""
파인튜닝 전 / 후 비교 (AI-10-10 4단계)

이 스크립트가 대답하려는 질문은 하나다 — **파인튜닝이 효과가 있었는가.**

지표를 왜 이렇게 골랐는가.
  09-08 모델 선정 때 이미 겪은 문제다. Recall@5 만 보면 후보 모델이 전부
  1.00 으로 동점이라 아무것도 못 고른다. 지표가 천장에 닿으면 정보가 사라진다.
  골든 쿼리 22개는 지금 MRR 1.000 이다. 여기서는 "더 좋아졌다"를 보여줄 수 없다.

  그래서 두 축으로 잰다.

  ① holdout 80개 — 학습에서 한 번도 안 본 질의. 순위 지표(Top-1 · MRR)가
     아직 천장이 아니므로 개선/악화가 숫자로 보인다.

  ② 1·2등 격차 (margin) — **이번 실험의 대표 지표**.
     정답 점수에서 2등 점수를 뺀 값. 순위가 이미 다 맞는 상태에서
     "얼마나 확신을 갖고 맞히는가"를 재는 유일한 축이다.
     격차가 크면 에이전트가 늘어 후보가 빽빽해져도 순위가 덜 흔들린다.
     베이스라인(골든 쿼리 기준) 0.115 가 출발점이다.

  ③ 골든 쿼리 22개 — 안전망. 1.000 을 유지하는지만 본다.
     떨어졌다면 과적합이다(20개 카탈로그를 외워 일반화를 잃은 것).

실행:
    cd ai/recommend/training
    python evaluate_finetune.py
"""

import json
from pathlib import Path

import numpy as np
from sentence_transformers import SentenceTransformer

from catalog import build_agent_text, load_agents, load_queries, load_schemas

BASE_DIR = Path(__file__).resolve().parents[1]
DATA_DIR = BASE_DIR / "data"
RESULT_DIR = Path(__file__).resolve().parent / "results"

HOLDOUT = DATA_DIR / "holdout_pairs.jsonl"
FINETUNED = BASE_DIR / "models" / "abc-agent-retriever-v1"
BASE_MODEL = "jhgan/ko-sroberta-multitask"

INCLUDE_SCHEMA = False        # 서비스(app/index/store.py)와 같은 규칙


def encode_catalog(model, agents, schemas):
    texts = [build_agent_text(a, schemas, include_schema=INCLUDE_SCHEMA) for a in agents]
    return model.encode(texts, normalize_embeddings=True, show_progress_bar=False)


def evaluate(model, agents, schemas, queries: list[tuple[str, set[str]]]) -> dict:
    """queries: (질의문, 정답 agent_id 집합) 목록."""
    agent_ids = [a["agent_id"] for a in agents]
    agent_vecs = encode_catalog(model, agents, schemas)
    query_vecs = model.encode([q for q, _ in queries],
                              normalize_embeddings=True, show_progress_bar=False)
    sims = query_vecs @ agent_vecs.T

    top1_hits, rr_list, margins = 0, [], []
    misses = []

    for row, (text, answers) in zip(sims, queries):
        order = np.argsort(-row)
        ranked = [agent_ids[i] for i in order]

        if ranked[0] in answers:
            top1_hits += 1
            # 1등이 정답일 때만 격차가 의미 있다. 틀렸을 때의 격차는 "확신 있게 틀림"이라
            # 크면 클수록 나쁘므로 같이 평균 내면 지표가 뒤집힌다.
            margins.append(float(row[order[0]] - row[order[1]]))
        else:
            misses.append(text)

        rank = next((i + 1 for i, aid in enumerate(ranked) if aid in answers), None)
        rr_list.append(1.0 / rank if rank else 0.0)

    n = len(queries)
    return {
        "n": n,
        "top1": top1_hits / n,
        "mrr": float(np.mean(rr_list)),
        "margin": float(np.mean(margins)) if margins else 0.0,
        "misses": misses,
    }


def load_holdout() -> list[tuple[str, set[str]]]:
    rows = [json.loads(l) for l in HOLDOUT.read_text(encoding="utf-8").splitlines() if l.strip()]
    return [(r["query"], {r["positive_agent_id"]}) for r in rows]


def load_golden() -> list[tuple[str, set[str]]]:
    return [(q["query"], set(q["answers"])) for q in load_queries()]


def fmt(before: float, after: float, pct: bool = False) -> str:
    d = after - before
    arrow = "▲" if d > 1e-6 else ("▼" if d < -1e-6 else "—")
    if pct:
        return f"{before:.1%}  →  {after:.1%}   {arrow} {abs(d):.1%}"
    return f"{before:.4f}  →  {after:.4f}   {arrow} {abs(d):.4f}"


def main() -> None:
    if not HOLDOUT.exists():
        raise SystemExit(f"{HOLDOUT.name} 이 없습니다. split_holdout.py 를 먼저 실행하세요.")
    if not FINETUNED.exists():
        raise SystemExit(f"파인튜닝 결과가 없습니다: {FINETUNED}\n먼저 finetune.py 를 실행하세요.")

    agents, schemas = load_agents(), load_schemas()
    sets = {"holdout": load_holdout(), "golden": load_golden()}

    print(f"파인튜닝 전 모델 로딩: {BASE_MODEL}")
    before_model = SentenceTransformer(BASE_MODEL)
    before = {k: evaluate(before_model, agents, schemas, v) for k, v in sets.items()}
    del before_model

    print(f"파인튜닝 후 모델 로딩: {FINETUNED.name}")
    after_model = SentenceTransformer(str(FINETUNED))
    after = {k: evaluate(after_model, agents, schemas, v) for k, v in sets.items()}

    for key, label in [("holdout", "holdout 80개 (학습에서 안 본 질의)"),
                       ("golden", "골든 쿼리 22개 (안전망 · 사람이 쓴 구어체)")]:
        b, a = before[key], after[key]
        print(f"\n── {label} ──")
        print(f"  1등 정답률   {fmt(b['top1'], a['top1'], pct=True)}")
        print(f"  MRR          {fmt(b['mrr'], a['mrr'])}")
        print(f"  1·2등 격차   {fmt(b['margin'], a['margin'])}   ← 대표 지표")
        if a["misses"]:
            print(f"  아직 틀리는 질의 {len(a['misses'])}개:")
            for m in a["misses"][:5]:
                print(f"    · {m}")

    # ── 판정 ──
    print("\n" + "=" * 62)
    gold_drop = after["golden"]["top1"] < before["golden"]["top1"] - 1e-9
    hold_up = after["holdout"]["top1"] >= before["holdout"]["top1"] - 1e-9
    margin_up = after["holdout"]["margin"] > before["holdout"]["margin"]

    if gold_drop:
        print("과적합 의심 — 골든 쿼리 정답률이 떨어졌다.")
        print("  20개 카탈로그를 외워 일반화를 잃은 상태다.")
        print("  EPOCHS 를 5 → 2~3 으로 줄이거나 LEARNING_RATE 를 1e-5 로 낮춰 다시 학습한다.")
    elif hold_up and margin_up:
        print("파인튜닝 성공 — 순위를 유지하면서 정답과 2등의 간격을 벌렸다.")
        print("  ai/recommend/models/abc-agent-retriever-v1/ 을 로컬로 복사하면")
        print("  app/config.py 가 자동 감지한다. 백엔드에 rebuild 요청을 잊지 말 것.")
    else:
        print("개선이 확인되지 않았다. 베이스라인 모델을 그대로 쓰는 것도 정당한 결론이다.")
        print("  '해봤는데 효과 없어서 안 썼다'는 log.md 에 남길 가치가 있는 결과다.")
    print("=" * 62)

    RESULT_DIR.mkdir(parents=True, exist_ok=True)
    out = RESULT_DIR / "eval_finetune.json"
    out.write_text(
        json.dumps({"before": before, "after": after}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"\n결과 저장 → results/{out.name}")


if __name__ == "__main__":
    main()
