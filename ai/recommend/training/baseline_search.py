"""
ABC 추천 엔진 — 임베딩 모델 후보 평가 (AI-10-1 / S15P21A403-199)

한 번 실행하면 CONFIGS 에 적힌 설정을 전부 돌리고 마지막에 비교표를 낸다.
모델은 캐시돼 있으므로 4개 설정 전체가 1~2분이면 끝난다.

지표 (Recall 하나로는 후보가 전부 동점이라 늘렸다)
  Recall@5      정답이 상위 5개 안에 있는 비율. 서비스가 5~8개를 보여주므로 이 K를 쓴다
  1등 정답률    정답이 정확히 1등인 비율
  MRR@10        정답 순위의 역수 평균. 1등=1.0, 2등=0.5, 3등=0.33
  1·2등 격차    1등과 2등 점수 차이. 클수록 정답을 확실히 구분한 것
  단어 불일치   에이전트 이름과 겹치는 단어가 없는 질의만 따로 집계.
                키워드 검색이 명확히 실패하는 구간이라 임베딩의 존재 이유가 여기서 드러난다

실행:  python baseline_search.py
"""

import json
import time
from pathlib import Path

import numpy as np
from sentence_transformers import SentenceTransformer

from catalog import build_agent_text, load_agents, load_queries, load_schemas

# ─────────────────────────────────────────────────────────────
# 설정 — (모델, 스키마 설명 포함 여부). 줄을 지우거나 추가하면 그만큼만 돈다
# ─────────────────────────────────────────────────────────────
CONFIGS = [
    ("jhgan/ko-sroberta-multitask", True),
    ("jhgan/ko-sroberta-multitask", False),   # 128토큰 잘림 회피안 비교군
    ("intfloat/multilingual-e5-base", True),
    ("nlpai-lab/KURE-v1", True),
]

TOP_K = 5      # Recall@5 · 화면에 보여줄 개수와 맞춘다
MRR_K = 10     # MRR@10
VERBOSE = False   # True 로 두면 질의 22개의 상위 3개를 전부 출력한다

RESULT_DIR = Path(__file__).resolve().parent / "results"


def evaluate(model_name: str, include_schema: bool, agents, schemas, queries) -> dict:
    # e5 계열은 학습 시 "query: " / "passage: " 접두어를 붙여 배웠다. 안 붙이면 성능이 떨어진다.
    use_e5_prefix = "e5" in model_name

    t0 = time.time()
    model = SentenceTransformer(model_name)
    load_sec = time.time() - t0

    max_seq = model.max_seq_length
    texts = [build_agent_text(a, schemas, include_schema) for a in agents]
    tok_lens = [len(model.tokenizer.encode(t)) for t in texts]
    truncated = sum(1 for n in tok_lens if n > max_seq)

    if use_e5_prefix:
        texts = [f"passage: {t}" for t in texts]

    # normalize_embeddings=True → 벡터 길이를 1로 맞춘다.
    # 그러면 코사인 유사도가 내적(행렬곱) 한 번으로 계산된다.
    doc_vecs = model.encode(texts, normalize_embeddings=True, show_progress_bar=False)

    rows = []
    for q in queries:
        text = f"query: {q['query']}" if use_e5_prefix else q["query"]

        t0 = time.time()
        q_vec = model.encode(text, normalize_embeddings=True, show_progress_bar=False)
        scores = doc_vecs @ q_vec
        latency_ms = (time.time() - t0) * 1000

        order = np.argsort(-scores)
        margin = float(scores[order[0]] - scores[order[1]])

        # 정답 중 가장 앞선 순위 (1부터)
        ranks = [r for r, i in enumerate(order, 1) if agents[i]["agent_id"] in q["answers"]]
        best_rank = min(ranks) if ranks else None
        hit = bool(best_rank and best_rank <= TOP_K)

        rows.append({
            "id": q["id"], "query": q["query"], "answers": q["answers"],
            "word_overlap": q["word_overlap"], "best_rank": best_rank,
            "hit_at_k": hit, "top1": best_rank == 1,
            "rr": 1 / best_rank if best_rank and best_rank <= MRR_K else 0.0,
            "margin": margin, "latency_ms": latency_ms,
            "top": [{"agent_id": agents[i]["agent_id"], "name": agents[i]["name"],
                     "score": round(float(scores[i]), 4)} for i in order[:TOP_K]],
        })

        if VERBOSE:
            tag = "" if q["word_overlap"] else "  [단어 불일치]"
            print(f"  {q['id']} {q['query']}{tag}  → {best_rank}등, 격차 {margin:.3f}")
            for r, i in enumerate(order[:3], 1):
                mark = " ←정답" if agents[i]["agent_id"] in q["answers"] else ""
                print(f"        {r}. {scores[i]:.3f}  {agents[i]['name']}{mark}")

    return summarize(rows, model_name, include_schema, load_sec,
                     model.get_embedding_dimension(), max_seq,
                     max(tok_lens), truncated, len(agents))


def summarize(rows, model_name, include_schema, load_sec, dim, max_seq,
              tok_max, tok_over, n_agents) -> dict:
    def mean(key, subset=None):
        data = rows if subset is None else subset
        return float(np.mean([r[key] for r in data])) if data else 0.0

    overlap = [r for r in rows if r["word_overlap"]]
    no_overlap = [r for r in rows if not r["word_overlap"]]

    return {
        "model": model_name, "include_schema_text": include_schema,
        "n_agents": n_agents, "n_queries": len(rows),
        "top_k": TOP_K, "mrr_k": MRR_K,
        "dim": dim, "max_seq_length": max_seq,
        "agent_token_max": tok_max, "agent_token_truncated": tok_over,
        "load_sec": round(load_sec, 1),
        f"recall_at_{TOP_K}": round(mean("hit_at_k"), 3),
        "top1_acc": round(mean("top1"), 3),
        f"mrr_at_{MRR_K}": round(mean("rr"), 3),
        "avg_margin": round(mean("margin"), 3),
        "avg_latency_ms": round(mean("latency_ms"), 1),
        "recall_word_overlap": round(mean("hit_at_k", overlap), 3),
        "recall_no_word_overlap": round(mean("hit_at_k", no_overlap), 3),
        "top1_no_word_overlap": round(mean("top1", no_overlap), 3),
        "misses": [{"id": r["id"], "query": r["query"], "rank": r["best_rank"],
                    "got": r["top"][0]["name"]} for r in rows if not r["top1"]],
        "per_query": rows,
    }


def save(result: dict) -> Path:
    RESULT_DIR.mkdir(exist_ok=True)
    slug = result["model"].replace("/", "__")
    if not result["include_schema_text"]:
        slug += "__noschema"
    path = RESULT_DIR / f"eval_{slug}.json"
    path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    return path


def print_table(saved: list[dict]):
    head = saved[0]
    print("\n" + "=" * 96)
    print(f"후보 비교  (골든 쿼리 {head['n_queries']}개 · 에이전트 {head['n_agents']}개 기준)")
    print("=" * 96)
    print(f"{'모델':<34}{'Recall@5':>9}{'1등':>7}{'MRR@10':>9}{'격차':>8}"
          f"{'불일치1등':>10}{'지연ms':>8}{'토큰':>7}")
    print("-" * 96)
    for s in saved:
        name = s["model"].split("/")[-1][:26]
        if not s["include_schema_text"]:
            name += "(스키마X)"
        rec = s.get(f"recall_at_{s['top_k']}", 0.0)
        mrr = s.get(f"mrr_at_{s['mrr_k']}", 0.0)
        trunc = "잘림" if s["agent_token_truncated"] else "OK"
        print(f"{name:<34}{rec:>9.2f}{s['top1_acc']:>7.2f}{mrr:>9.3f}"
              f"{s['avg_margin']:>8.3f}{s['top1_no_word_overlap']:>10.2f}"
              f"{s['avg_latency_ms']:>8.0f}{trunc:>7}")
    print("=" * 96)
    print("선정 기준 (가이드 §12.3): ① Recall@5 · MRR  ② CPU 지연 200ms 이내")
    print("                          ③ 서빙 메모리 1.5GB 이내  ④ 상업 이용 가능 라이선스")

    # 여러 설정이 똑같이 틀린 질의 = 모델 문제가 아니라 카탈로그·질의 문제
    counts = {}
    for s in saved:
        for m in s["misses"]:
            counts.setdefault(m["id"], []).append(m)
    common = {qid: ms for qid, ms in counts.items() if len(ms) == len(saved)}
    if common:
        print(f"\n⚠️ 모든 설정({len(saved)}개)이 똑같이 1등을 놓친 질의 — 모델이 아니라 데이터 문제 신호")
        for qid, ms in common.items():
            got = {m["got"] for m in ms}
            print(f"   {qid} {ms[0]['query']}")
            print(f"        1등으로 올라온 것: {', '.join(sorted(got))}")


def main():
    agents, schemas, queries = load_agents(), load_schemas(), load_queries()
    print(f"에이전트 {len(agents)}개 · 골든 쿼리 {len(queries)}개 · 설정 {len(CONFIGS)}개\n")

    saved = []
    for i, (model_name, include_schema) in enumerate(CONFIGS, 1):
        tag = "스키마 포함" if include_schema else "스키마 제외"
        print(f"[{i}/{len(CONFIGS)}] {model_name} · {tag}")

        result = evaluate(model_name, include_schema, agents, schemas, queries)
        path = save(result)
        saved.append(result)

        trunc = f" · ⚠️{result['agent_token_truncated']}건 잘림" if result["agent_token_truncated"] else ""
        print(f"        Recall@{TOP_K} {result[f'recall_at_{TOP_K}']:.2f} · "
              f"1등 {result['top1_acc']:.2f} · MRR {result[f'mrr_at_{MRR_K}']:.3f} · "
              f"격차 {result['avg_margin']:.3f} · {result['avg_latency_ms']:.0f}ms{trunc}")
        for m in result["misses"]:
            print(f"        놓침 {m['id']} {m['query'][:26]} → {m['rank']}등, 1등='{m['got']}'")
        print(f"        → {path.name}\n")

    print_table(saved)


if __name__ == "__main__":
    main()
