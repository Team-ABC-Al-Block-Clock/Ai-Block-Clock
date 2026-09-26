"""시연 에이전트 4개의 등록용 정의를 만든다 — 백엔드가 DB에 넣을 v2.0 형식 한 파일씩.

원본은 셋이다. 이 스크립트는 합치기만 하고 내용을 만들지 않는다.
    ai/spike/linear-mvp/workflows/brand_campaign.json   에이전트 내부 노드 · 연결 · 출력 선언 (트랙 A)
    ai/prompts/brand/*.md                                프롬프트 원문 (트랙 B 작성, 09-26부터 A 관리)
    ai/demo/brand/market_content.json · prepared/seed_values_calculated.json   마켓 문구 · 시드 실적

    cd ai/spike/linear-mvp
    uv run python ../../demo/brand/build_registration.py

결과 `ai/demo/brand/agents/ag_brand_0N.json` 은 **생성물이다. 직접 고치지 말고 원본을 고친 뒤 다시 돌린다.**
프롬프트 원문이 들어 있다 — 백엔드 DB에 저장하는 값이며 사용자 화면 · 로그 · 추천 색인으로 내보내면 안 된다(SEC-001).
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

BRAND = Path(__file__).resolve().parent
AI = BRAND.parents[1]
RUNTIME = AI / "spike" / "linear-mvp"
sys.path.insert(0, str(RUNTIME))

from src import expand, graph, runner  # noqa: E402

# 준비용 키 ↔ 2단계 워크플로우의 캔버스 노드 (PREPARATION.md 등록 인계표와 같은 순서)
CANVAS = {"ag_brand_01": "a1", "ag_brand_02": "a2", "ag_brand_03": "a3", "ag_brand_04": "a4"}

# 판매자가 노드에 붙여 두는 참고 파일 (계약 §7.1). 경로는 이 폴더 기준
REF_FILES = {
    "ag_brand_01": {"n1": ["ref/시장조사_체크리스트.md"], "n4": ["ref/보고서_작성가이드.pdf"]},
    "ag_brand_03": {"k1": ["ref/광고카피_금지표현.txt"]},
}


def main() -> None:
    campaign = runner.resolve_prompts(runner.load_workflow("brand_campaign"))
    canvas = {n["id"]: n for n in campaign["nodes"]}
    market = {a["local_key"]: a for a in json.loads((BRAND / "market_content.json").read_text(encoding="utf-8"))["agents"]}
    seeds = {a["local_key"]: a for a in json.loads(
        (BRAND / "prepared" / "seed_values_calculated.json").read_text(encoding="utf-8"))["agents"]}

    out_dir = BRAND / "agents"
    out_dir.mkdir(exist_ok=True)
    for key, cid in CANVAS.items():
        node = canvas[cid]
        agent = node["agent"]
        refs = REF_FILES.get(key, {})
        for inner in agent["nodes"]:
            inner.pop("ref_files", None)
            if inner["id"] in refs:
                inner["ref_files"] = [Path(r).name for r in refs[inner["id"]]]   # 노드에는 파일명만(표시용)
        for r in (p for paths in refs.values() for p in paths):
            assert (BRAND / r).exists(), f"참고 파일 없음: {r}"

        # 사용자 워크플로우에 단독으로 놓였을 때 v2.0 규칙을 통과하는지 확인한다
        report = graph.validate({"nodes": [{"id": "x", "type": "agent", "agent": agent}], "edges": [],
                                 "output": {"type": "pdf"}})
        assert report["valid"], (key, report["errors"])
        assert not report["warnings"], (key, report["warnings"])

        m, s = market[key], seeds[key]
        doc = {
            "_note": "생성물 — ai/demo/brand/build_registration.py. 프롬프트 원문 포함(SEC-001: DB 저장용, 외부 노출 금지)",
            "local_key": key,
            "owner": {"key": m["owner_key"], "runtime_owner_id": agent["owner_id"], "name": node.get("owner_name")},
            "market": {"name": m["name"], "description": m["description"], "tags": m["tags"], "category": m["category"]},
            "public_io": expand.public_io(node),          # 마켓 카드 — 입력 폼 견본 · 출력 선언
            "agent": {"nodes": agent["nodes"], "edges": agent["edges"]},   # 실행 때 type:agent 로 그대로 끼워 보낸다
            "ref_files": {nid: paths for nid, paths in refs.items()},     # 노드 id → 이 폴더 기준 파일 경로
            "seed": {k: s[k] for k in ("successful_runs", "supplier_failures", "review_count",
                                       "average_rating", "success_rate", "score", "tier", "premium_rate")},
            "estimate_node_count": graph.validate({"nodes": [{"id": "x", "type": "agent", "agent": agent}],
                                                   "edges": [], "output": {"type": "pdf"}})["estimate"]["worst_case_node_count"],
        }
        path = out_dir / f"{key}.json"
        path.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
        io = doc["public_io"]
        print(f"{path.name}  {m['name']:<14} 판매자 {m['owner_key']}  노드 {len(agent['nodes'])}  "
              f"입력 {(io['input'] or {}).get('sample') or '자유'} → 출력 {io['output']['sample'] or io['output']['kind']}  "
              f"참고 파일 {sum(len(v) for v in refs.values())}")


if __name__ == "__main__":
    main()
