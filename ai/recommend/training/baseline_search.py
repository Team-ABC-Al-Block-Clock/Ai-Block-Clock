"""
ABC 추천 엔진 — 임베딩 모델 후보 평가 (AI-10-1 / S15P21A403-199)

09-08 baseline(에이전트 6개 · 질의 5개)에서 확장한 판이다.
이제 시드 카탈로그 20개와 골든 쿼리 20개를 파일에서 읽어 측정한다.

지표 (09-08에 Recall 하나로는 후보가 전부 동점이라 판정이 안 돼서 늘렸다)
  Recall@5      정답이 상위 5개 안에 있는 비율. 서비스가 5~8개를 보여주므로 이 K를 쓴다
  1등 정답률    정답이 정확히 1등인 비율
  MRR@10        정답 순위의 역수 평균. 1등=1.0, 2등=0.5, 3등=0.33
  1·2등 격차    1등과 2등 점수 차이. 클수록 정답을 확실히 구분한 것
  단어 불일치   에이전트 이름과 겹치는 단어가 없는 질의만 따로 집계.
                키워드 검색이 명확히 실패하는 구간이라 임베딩의 존재 이유가 여기서 드러난다

실행:  python baseline_search.py
       MODEL_NAME 을 바꿔가며 세 번 돌리면 마지막에 비교표가 자동으로 나온다.
"""

import json
import time
from pathlib import Path

import numpy as np
from sentence_transformers import SentenceTransformer

from catalog import build_agent_text, load_agents, load_queries, load_schemas

# ─────────────────────────────────────────────────────────────
# 설정 — 여기만 바꿔가며 후보를 비교한다
# ─────────────────────────────────────────────────────────────
# MODEL_NAME = "jhgan/ko-sroberta-multitask"
# MODEL_NAME = "intfloat/multilingual-e5-base"
MODEL_NAME = "nlpai-lab/KURE-v1"

# 임베딩 텍스트에 입출력 스키마 설명을 넣을지.
# ko-sroberta(128토큰)에서 잘림이 나면 False 로 두고 점수가 유지되는지 본다.
INCLUDE_SCHEMA_TEXT = True

# e5 계열은 학습 시 "query: " / "passage: " 접두어를 붙여 배웠다. 안 붙이면 성능이 떨어진다.
USE_E5_PREFIX = "e5" in MODEL_NAME

TOP_K = 5     # Recall@5 · 화면에 보여줄 개수와 맞춘다
MRR_K = 10    # MRR@10

RESULT_DIR = Path(__file__).resolve().parent / "results"


def evaluate():
    agents = load_agents()
    schemas = load_schemas()
    queries = load_queries()

    # ── 모델 로딩 ──
    print(f"[1] 모델 로딩: {MODEL_NAME}")
    t0 = time.time()
    model = SentenceTransformer(MODEL_NAME)
    load_sec = time.time() - t0

    dim = model.get_embedding_dimension()
    max_seq = model.max_seq_length
    print(f"    로딩 완료 — {load_sec:.1f}초 | {dim}차원 | 최대 입력 {max_seq} 토큰")
    print(f"    스키마 설명 포함: {INCLUDE_SCHEMA_TEXT}")

    # ── 문서 임베딩 (인덱싱에 해당) ──
    texts = [build_agent_text(a, schemas, INCLUDE_SCHEMA_TEXT) for a in agents]

    tok_lens = [len(model.tokenizer.encode(t)) for t in texts]
    over = sum(1 for n in tok_lens if n > max_seq)
    print(f"    에이전트 텍스트 토큰: 최대 {max(tok_lens)} / 평균 {np.mean(tok_lens):.0f}", end="")
    print(f"  ⚠️ {over}건 잘림" if over else "  ✅ 잘림 없음")

    if USE_E5_PREFIX:
        texts = [f"passage: {t}" for t in texts]

    # normalize_embeddings=True → 벡터 길이를 1로 맞춘다.
    # 그러면 코사인 유사도가 내적(행렬곱) 한 번으로 계산된다.
    doc_vecs = model.encode(texts, normalize_embeddings=True, show_progress_bar=False)
    print(f"\n[2] 에이전트 {len(agents)}개 인덱싱 완료 — 행렬 {doc_vecs.shape}\n")

    # ── 질의 검색 ──
    rows = []
    for q in queries:
        text = f"query: {q['query']}" if USE_E5_PREFIX else q["query"]

        t0 = time.time()
        q_vec = model.encode(text, normalize_embeddings=True, show_progress_bar=False)
        scores = doc_vecs @ q_vec
        latency_ms = (time.time() - t0) * 1000

        order = np.argsort(-scores)                    # 점수 높은 순 전체 정렬
        margin = float(scores[order[0]] - scores[order[1]])

        # 정답 중 가장 앞선 순위 (1부터)
        ranks = [r for r, i in enumerate(order, 1)
                 if agents[i]["agent_id"] in q["answers"]]
        best_rank = min(ranks) if ranks else None

        rows.append({
            "id": q["id"],
            "query": q["query"],
            "answers": q["answers"],
            "word_overlap": q["word_overlap"],
            "best_rank": best_rank,
            "hit_at_k": bool(best_rank and best_rank <= TOP_K),
            "top1": best_rank == 1,
            "rr": 1 / best_rank if best_rank and best_rank <= MRR_K else 0.0,
            "margin": margin,
            "latency_ms": latency_ms,
            "top": [
                {"agent_id": agents[i]["agent_id"],
                 "name": agents[i]["name"],
                 "score": round(float(scores[i]), 4)}
                for i in order[:TOP_K]
            ],
        })

        # ── 질의별 출력 (상위 3개만 — 전체 순위는 결과 JSON에 남는다) ──
        if best_rank == 1:
            verdict = "✅ 1등"
        elif rows[-1]["hit_at_k"]:
            verdict = f"⚠️ {best_rank}등"
        elif best_rank:
            verdict = f"❌ {best_rank}등 — 상위 {TOP_K} 밖"
        else:
            verdict = "❌ 정답 없음"

        tag = "" if q["word_overlap"] else "  [단어 불일치]"
        print(f"{q['id']}  {q['query']}{tag}")
        print(f"      {verdict}   격차 {margin:.3f}")
        for r, i in enumerate(order[:3], 1):
            mark = " ←정답" if agents[i]["agent_id"] in q["answers"] else ""
            print(f"      {r}. {scores[i]:.3f}  {agents[i]['name']}{mark}")
        print()

    return summarize(rows, load_sec, dim, max_seq, max(tok_lens), over, len(agents))


def summarize(rows, load_sec, dim, max_seq, tok_max, tok_over, n_agents):
    def mean(key, subset=None):
        data = subset if subset is not None else rows
        return float(np.mean([r[key] for r in data])) if data else 0.0

    overlap = [r for r in rows if r["word_overlap"]]
    no_overlap = [r for r in rows if not r["word_overlap"]]

    result = {
        "model": MODEL_NAME,
        "include_schema_text": INCLUDE_SCHEMA_TEXT,
        "n_agents": n_agents,
        "n_queries": len(rows),
        "top_k": TOP_K,
        "mrr_k": MRR_K,
        "dim": dim,
        "max_seq_length": max_seq,
        "agent_token_max": tok_max,
        "agent_token_truncated": tok_over,
        "load_sec": round(load_sec, 1),
        f"recall_at_{TOP_K}": round(mean("hit_at_k"), 3),
        "top1_acc": round(mean("top1"), 3),
        f"mrr_at_{MRR_K}": round(mean("rr"), 3),
        "avg_margin": round(mean("margin"), 3),
        "avg_latency_ms": round(mean("latency_ms"), 1),
        "recall_word_overlap": round(mean("hit_at_k", overlap), 3),
        "recall_no_word_overlap": round(mean("hit_at_k", no_overlap), 3),
        "top1_no_word_overlap": round(mean("top1", no_overlap), 3),
        "misses": [
            {"id": r["id"], "query": r["query"], "rank": r["best_rank"],
             "got": r["top"][0]["name"]}
            for r in rows if not r["top1"]
        ],
        "per_query": rows,
    }

    print("─" * 62)
    print(f"모델              : {MODEL_NAME}")
    print(f"스키마 설명 포함  : {INCLUDE_SCHEMA_TEXT}")
    print(f"Recall@{TOP_K}          : {result[f'recall_at_{TOP_K}']:.2f}")
    print(f"1등 정답률        : {result['top1_acc']:.2f}")
    print(f"MRR@{MRR_K}           : {result[f'mrr_at_{MRR_K}']:.3f}")
    print(f"평균 1·2등 격차   : {result['avg_margin']:.3f}")
    print(f"평균 질의 지연    : {result['avg_latency_ms']:.0f} ms")
    print(f"모델 로딩         : {result['load_sec']:.1f}초")
    print("-" * 62)
    print(f"단어 겹침 질의    : Recall {result['recall_word_overlap']:.2f}  ({len(overlap)}건)")
    print(f"단어 불일치 질의  : Recall {result['recall_no_word_overlap']:.2f}"
          f" / 1등 {result['top1_no_word_overlap']:.2f}  ({len(no_overlap)}건)")
    print("─" * 62)

    if result["misses"]:
        print(f"\n1등을 놓친 질의 {len(result['misses'])}건")
        for m in result["misses"]:
            rank = f"{m['rank']}등" if m["rank"] else "순위 밖"
            print(f"  {m['id']} {m['query']}")
            print(f"       → {rank}. 1등은 '{m['got']}'")

    return result


def save_and_compare(result):
    RESULT_DIR.mkdir(exist_ok=True)
    slug = MODEL_NAME.replace("/", "__")
    if not INCLUDE_SCHEMA_TEXT:
        slug += "__noschema"
    path = RESULT_DIR / f"eval_{slug}.json"
    path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n결과 저장: {path.name}")

    # 지금까지 저장된 결과 전부를 한 표로 — 손으로 옮겨 적지 않게
    files = sorted(RESULT_DIR.glob("eval_*.json"))
    if len(files) < 2:
        return

    saved = [json.loads(f.read_text(encoding="utf-8")) for f in files]
    print("\n" + "=" * 96)
    print("후보 비교  (골든 쿼리 20개 기준)")
    print("=" * 96)
    head = f"{'모델':<34}{'Recall@5':>9}{'1등':>7}{'MRR@10':>9}{'격차':>8}{'불일치1등':>10}{'지연ms':>8}{'토큰':>7}"
    print(head)
    print("-" * 96)
    for s in saved:
        name = s["model"].split("/")[-1][:26]
        if not s["include_schema_text"]:
            name += "(스키마X)"
        trunc = "잘림" if s["agent_token_truncated"] else "OK"
        print(f"{name:<34}{s['recall_at_5']:>9.2f}{s['top1_acc']:>7.2f}"
              f"{s['mrr_at_10']:>9.3f}{s['avg_margin']:>8.3f}"
              f"{s['top1_no_word_overlap']:>10.2f}{s['avg_latency_ms']:>8.0f}{trunc:>7}")
    print("=" * 96)
    print("선정 기준 (가이드 §12.3): ① Recall@5 · MRR  ② CPU 지연 200ms 이내")
    print("                          ③ 서빙 메모리 1.5GB 이내  ④ 상업 이용 가능 라이선스")


if __name__ == "__main__":
    save_and_compare(evaluate())
