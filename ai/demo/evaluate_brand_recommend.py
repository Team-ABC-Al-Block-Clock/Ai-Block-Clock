"""실제 검색/랭킹 코드를 사용하는 로컬 사전 평가. 서버 및 DB를 변경하지 않는다."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent
RECOMMEND = ROOT.parent / "recommend"
QUERIES = [
    ("신제품 내놓는데 어떤 컨셉으로 갈지 방향 좀 잡아줘", "ag_brand_01"),
    ("우리 제품 시장에서 어떻게 자리잡을지 전략 세워줘", "ag_brand_01"),
    ("브랜드 전략 만들어줘", "ag_brand_01"),
    ("인스타랑 유튜브에 뭘 언제 올릴지 계획 짜줘", "ag_brand_02"),
    ("소셜미디어 캠페인 기획해줘", "ag_brand_02"),
    ("광고에 쓸 문구랑 해시태그 뽑아줘", "ag_brand_03"),
    ("한 줄로 각인되는 홍보 문장 만들어줘", "ag_brand_03"),
    ("앞에서 나온 캠페인이랑 문구를 일정이랑 예산으로 묶어줘", "ag_brand_04"),
    ("채널별로 언제 뭘 집행할지 예산까지 정리해줘", "ag_brand_04"),
]


def metrics(rows):
    if not rows:
        raise ValueError("평가 세트가 비어 있습니다")
    return {"n": len(rows), "top1": sum(r["rank"] == 1 for r in rows),
            "top8": sum(r["rank"] is not None and r["rank"] <= 8 for r in rows),
            "mrr": sum(1 / r["rank"] if r["rank"] else 0 for r in rows) / len(rows), "rows": rows}


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--agents", type=Path, default=ROOT / "brand/prepared/brand_agents_local.json")
    ap.add_argument("--out", type=Path, default=ROOT / "brand/prepared/recommend_local_report.json")
    args = ap.parse_args()
    # 로컬 가중치만 사용: 평가 중 다운로드나 모델 대체가 일어나지 않게 한다.
    os.environ["HF_HUB_OFFLINE"] = "1"
    os.environ["TRANSFORMERS_OFFLINE"] = "1"
    sys.path.insert(0, str(RECOMMEND))
    from sentence_transformers import SentenceTransformer
    from app.config import FINETUNED_DIR, HYBRID_MODE
    from app.index.store import AgentIndex
    from app.ranking.score import final_score
    from app.schemas import AgentRecord
    if not FINETUNED_DIR.is_dir():
        raise SystemExit("로컬 파인튜닝 가중치가 없습니다")
    read = lambda p: json.loads(p.read_text(encoding="utf-8"))
    seed = read(RECOMMEND / "data/seed_agents.json")["agents"]
    brand = read(args.agents)["agents"]
    golden = [(q["query"], set(q["answers"])) for q in read(RECOMMEND / "data/golden_queries.json")["queries"]]
    holdout = [(r["query"], {r["positive_agent_id"]}) for r in
               (json.loads(line) for line in (RECOMMEND / "data/holdout_pairs.jsonl").read_text(encoding="utf-8").splitlines() if line.strip())]
    model = SentenceTransformer(str(FINETUNED_DIR), local_files_only=True, device="cpu")
    def encode(texts):
        return model.encode(texts, normalize_embeddings=True, show_progress_bar=False)
    index = AgentIndex(encode, "abc-agent-retriever-v1")
    def evaluate(pairs):
        rows = []
        for query, answers in pairs:
            hits = sorted(index.search(query), key=lambda h: -final_score(h.relevance, h.agent)[0])
            ids = [h.agent.agent_id for h in hits]
            rank = next((i for i, aid in enumerate(ids, 1) if aid in answers), None)
            rows.append({"query": query, "answers": sorted(answers), "rank": rank, "top8": ids[:8]})
        return metrics(rows)
    index.upsert([AgentRecord(**a) for a in seed])
    before = {"golden": evaluate(golden), "holdout": evaluate(holdout)}
    index.upsert([AgentRecord(**a) for a in brand])
    after = {"brand": evaluate([(q, {aid}) for q, aid in QUERIES]),
             "golden": evaluate(golden), "holdout": evaluate(holdout)}
    report = {"status": "local_pre_registration_not_final", "model": "abc-agent-retriever-v1",
              "hybrid_mode": HYBRID_MODE, "trust_included": True, "indexed_agents": len(index),
              "content_sha256": hashlib.sha256(args.agents.read_bytes()).hexdigest(),
              "note": "기존 사전 평가의 브랜딩 9질의를 유지. 예산 질의는 현 콘텐츠 범위와 차이가 있어 해석 시 주의.",
              "before": before, "after": after,
              "brand_top8_gate": after["brand"]["top8"] == after["brand"]["n"],
              "regression_top1_non_decreasing": all(after[k]["top1"] >= before[k]["top1"] for k in before)}
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"before": {k: {m: v[m] for m in ("n", "top1", "top8")} for k, v in before.items()},
                      "after": {k: {m: v[m] for m in ("n", "top1", "top8")} for k, v in after.items()}}, ensure_ascii=False))
    return 0 if report["brand_top8_gate"] and report["regression_top1_non_decreasing"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
