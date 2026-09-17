"""
하이브리드 검색 비교 — 임베딩 단독 vs RRF vs 가중합 (S15P21A403-280)

왜 두 가지 융합 방식을 같이 재는가.
  로드맵은 RRF 를 지정했지만, RRF 에는 이 프로젝트에 불리한 성질이 하나 있다.
  **순위만 쓰고 점수를 버리기 때문에 1·2등 격차가 뭉개진다.** 우리는 09-14
  파인튜닝에서 그 격차를 대표 지표로 삼았고, 에이전트가 늘어도 순위가 안 흔들리는
  여유가 거기서 나온다.

  그래서 점수를 살리는 가중합도 같이 만들어 두고 **측정해서 고른다.**
  둘 다 켜고 끌 수 있으므로 나중에 바꿔도 코드 수정이 없다.

판정 기준 (이 순서로 본다)
  ① 골든 쿼리 22개 1등 정답률이 **100% 를 유지**하는가 — 떨어지면 탈락
  ② holdout 80개 1등 정답률이 **오르는가** — 여기가 개선 여지가 남은 세트다
  ③ 1·2등 격차가 줄지 않는가 — 같은 모드 안에서만 비교한다

  ★ 로드맵 §2.3 에서 하이브리드는 컷 2순위다. **개선이 확인되지 않으면 켜지 않는다.**
    "해봤는데 효과 없어서 안 썼다"도 기록할 가치가 있는 결과다.

실행:
    cd ai/recommend
    python training/eval_hybrid.py
"""

import json
import sys
from pathlib import Path

import numpy as np

BASE = Path(__file__).resolve().parents[1]          # ai/recommend
sys.path.insert(0, str(BASE))                       # app 패키지
sys.path.insert(0, str(BASE / "training"))          # catalog

from app.config import MODEL_PATH, MODEL_VERSION, RRF_K, W_LEXICAL  # noqa: E402
from app.index.lexical import BM25Index, rrf_fuse                   # noqa: E402
from catalog import build_agent_text, load_agents, load_queries, load_schemas  # noqa: E402

HOLDOUT = BASE / "data" / "holdout_pairs.jsonl"
RESULT = Path(__file__).resolve().parent / "results" / "eval_hybrid.json"

# 서비스(app/index/store.py)와 같은 규칙. 스키마 설명은 넣지 않는다 (09-09 결정)
INCLUDE_SCHEMA = False

# (표시 이름, 모드, 어휘 가중치)
# ★ weighted 를 여러 가중치로 훑는다. 09-17 첫 측정에서 W_LEXICAL=0.2 가
#   골든 -1 / holdout +1 로 아슬아슬하게 걸렸다. 어휘 비중이 과한 것인지
#   방식 자체가 안 맞는 것인지는 가중치를 낮춰 봐야 갈린다.
MODES = [("off", "off", 0.0), ("rrf", "rrf", 0.0)] + [
    (f"weighted {w:.2f}", "weighted", w) for w in (0.05, 0.10, 0.15, 0.20)
]


def fuse(mode: str, semantic: list[float], lexical: list[float],
         w_lex: float = W_LEXICAL) -> list[float]:
    """app/index/store.py 의 _fuse_* 와 같은 계산.

    ★ 두 곳의 계산이 어긋나면 "평가에서는 좋았는데 서비스에서는 다른" 상태가 된다.
      한쪽을 고치면 반드시 다른 쪽도 고친다.
    """
    n = len(semantic)
    if mode == "off":
        return semantic
    if mode == "rrf":
        sem_rank = sorted(range(n), key=lambda i: -semantic[i])
        lex_rank = sorted(range(n), key=lambda i: -lexical[i])
        fused = rrf_fuse([sem_rank, lex_rank], k=RRF_K)
        ceiling = 2.0 / (RRF_K + 1)
        return [fused.get(i, 0.0) / ceiling for i in range(n)]
    top = max(lexical) if lexical else 0.0
    lex_norm = [(x / top if top > 0 else 0.0) for x in lexical]
    return [(1 - w_lex) * max(0.0, min(1.0, s)) + w_lex * l
            for s, l in zip(semantic, lex_norm)]


def evaluate(mode, w_lex, ids, agent_vecs, bm25, model, queries) -> dict:
    """queries: (질의문, 정답 agent_id 집합) 목록."""
    q_vecs = model.encode([q for q, _ in queries],
                          normalize_embeddings=True, show_progress_bar=False)
    hits, rr, margins, misses = 0, [], [], []

    for q_vec, (text, answers) in zip(q_vecs, queries):
        semantic = (agent_vecs @ q_vec).tolist()
        lexical = bm25.scores(text) if mode != "off" else []
        rel = fuse(mode, semantic, lexical, w_lex)

        order = sorted(range(len(ids)), key=lambda i: -rel[i])
        ranked = [ids[i] for i in order]

        if ranked[0] in answers:
            hits += 1
            # 1등이 정답일 때만 격차를 잰다 — 틀렸을 때의 격차는 클수록 나쁘다
            margins.append(rel[order[0]] - rel[order[1]])
        else:
            misses.append(text)

        rank = next((i + 1 for i, a in enumerate(ranked) if a in answers), None)
        rr.append(1.0 / rank if rank else 0.0)

    n = len(queries)
    return {"n": n, "top1": hits / n, "mrr": float(np.mean(rr)),
            "margin": float(np.mean(margins)) if margins else 0.0, "misses": misses}


def main() -> None:
    if not HOLDOUT.exists():
        raise SystemExit(f"{HOLDOUT.name} 이 없습니다. training/split_holdout.py 를 먼저 실행하세요.")

    from sentence_transformers import SentenceTransformer

    agents, schemas = load_agents(), load_schemas()
    ids = [a["agent_id"] for a in agents]
    texts = [build_agent_text(a, schemas, include_schema=INCLUDE_SCHEMA) for a in agents]

    print(f"모델 로딩: {MODEL_PATH}")
    model = SentenceTransformer(MODEL_PATH)
    agent_vecs = model.encode(texts, normalize_embeddings=True, show_progress_bar=False)

    bm25 = BM25Index()
    bm25.build(texts)

    golden = [(q["query"], set(q["answers"])) for q in load_queries()]
    holdout = [(json.loads(l)["query"], {json.loads(l)["positive_agent_id"]})
               for l in HOLDOUT.read_text(encoding="utf-8").splitlines() if l.strip()]
    sets = {"골든 22개": golden, "holdout 80개": holdout}

    results = {}
    for label, mode, w_lex in MODES:
        results[label] = {k: evaluate(mode, w_lex, ids, agent_vecs, bm25, model, v)
                          for k, v in sets.items()}

    # ── 표 ──
    print(f"\nmodel_version={MODEL_VERSION}")
    for label in sets:
        print(f"\n── {label} ──")
        print(f"  {'모드':14} {'1등 정답률':>10} {'MRR':>8} {'1·2등 격차':>11}")
        for name, _, _ in MODES:
            r = results[name][label]
            print(f"  {name:14} {r['top1']:>9.1%} {r['mrr']:>8.3f} {r['margin']:>11.4f}")

    # ── 판정 ──
    base_g, base_h = results["off"]["골든 22개"], results["off"]["holdout 80개"]
    print("\n" + "=" * 62)
    winner = None
    for name, mode, w_lex in MODES:
        if mode == "off":
            continue
        g, h = results[name]["골든 22개"], results[name]["holdout 80개"]
        dg = round((g["top1"] - base_g["top1"]) * g["n"])
        dh = round((h["top1"] - base_h["top1"]) * h["n"])
        verdict = "탈락 — 골든 쿼리가 떨어졌다" if dg < 0 else (
            "후보" if dh > 0 else "개선 없음")
        print(f"{name:14} 골든 {dg:+d}개 · holdout {dh:+d}개 → {verdict}")
        if dg >= 0 and dh > 0 and (winner is None or dh > winner[1]):
            winner = ((mode, w_lex), dh)

    print("-" * 62)
    if winner:
        (mode, w_lex), dh = winner
        print(f"채택: ABC_HYBRID={mode} · W_LEXICAL={w_lex:.2f}   (holdout {dh:+d}개)")
        print(f"  환경변수로 켠다:  $env:ABC_HYBRID=\"{mode}\"")
        print(f"  app/config.py 의 W_LEXICAL 을 {w_lex:.2f} 로 맞춘다.")
        print("  시연 중 문제가 생기면 off 로 되돌리면 된다.")
    else:
        print("채택하지 않는다 — 개선이 확인되지 않았다. ABC_HYBRID=off 유지.")
        print("  로드맵 §2.3 컷 2순위 항목이다. 효과가 없으면 켜지 않는 게 맞다.")
    print("=" * 62)

    RESULT.parent.mkdir(parents=True, exist_ok=True)
    RESULT.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n결과 저장 → results/{RESULT.name}")


if __name__ == "__main__":
    main()
