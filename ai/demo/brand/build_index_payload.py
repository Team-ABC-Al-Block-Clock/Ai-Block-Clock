"""추천 색인 payload 만들기 — 시드 20 + 시연 에이전트 4를 **실제 등록 ID**로.

09-26 새벽 EC2 추천 서비스에 준비용 ID(`ag_brand_01 ~ 04`)로 24건을 넣었다. 백엔드가 시연 에이전트를
등록하면 추천 결과를 눌렀을 때 백엔드가 아는 ID여야 하므로, 실제 ID로 다시 `rebuild` 한다.

    # 1) 백엔드가 준 실제 ID를 registered_ids.json 에 적는다 (registered_ids.example.json 복사)
    # 2) payload 생성
    cd ai/spike/linear-mvp
    uv run python ../../demo/brand/build_index_payload.py --ids ../../demo/brand/registered_ids.json --out /tmp/index_payload.json
    # 3) EC2 에서 (토큰은 EC2 셸 안에서만 쓴다 — 출력하지 않는다)
    #    T=$(sudo docker exec ai-block-clock-recommend printenv ABC_SERVICE_TOKEN)
    #    curl -s -X POST http://127.0.0.1:8100/v1/index/agents -H "X-ABC-Service-Token: $T" \
    #         -H "Content-Type: application/json" --data-binary @/tmp/index_payload.json

프롬프트 · 내부 노드는 넣지 않는다 — recommend-api.md §4 의 공개 필드만(SEC-001).
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

BRAND = Path(__file__).resolve().parent
AI = BRAND.parents[1]
FIELDS = ("agent_id", "name", "description", "tags", "category", "input_schema_id", "output_schema_id",
          "owner_id", "tier", "success_rate", "is_public")


def main() -> None:
    ap = argparse.ArgumentParser(description="추천 색인 rebuild payload")
    ap.add_argument("--ids", type=Path, help="준비용 키 → {agent_id, owner_id} 매핑 JSON. 없으면 준비용 ID 그대로")
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()

    seeds = json.loads((AI / "recommend" / "data" / "seed_agents.json").read_text(encoding="utf-8"))["agents"]
    market = json.loads((BRAND / "market_content.json").read_text(encoding="utf-8"))["agents"]
    stats = {a["local_key"]: a for a in json.loads(
        (BRAND / "prepared" / "seed_values_calculated.json").read_text(encoding="utf-8"))["agents"]}
    agents_dir = BRAND / "agents"
    ids = json.loads(args.ids.read_text(encoding="utf-8")) if args.ids else {}

    brand = []
    for m in market:
        key = m["local_key"]
        mapped = ids.get(key) or {}
        io_path = agents_dir / f"{key}.json"
        io = json.loads(io_path.read_text(encoding="utf-8"))["public_io"] if io_path.exists() else {}
        out = (io.get("output") or {})
        brand.append({
            "agent_id": mapped.get("agent_id") or key,
            "name": m["name"], "description": m["description"], "tags": m["tags"], "category": m["category"],
            # v2.1: 견본이 없다. 표시용으로 입력은 예시가 있으면 "json"(없으면 자유 = None), 출력은 "json" · "text"
            "input_schema_id": "json" if io.get("input") else None,
            "output_schema_id": out.get("kind") or "json",
            "owner_id": mapped.get("owner_id"),
            "tier": stats[key]["tier"], "success_rate": stats[key]["success_rate"], "is_public": True,
        })
    missing = [a["agent_id"] for a in brand if a["agent_id"].startswith("ag_brand_")]
    payload = {"op": "rebuild", "agents": [{k: a.get(k) for k in FIELDS} for a in seeds + brand]}
    args.out.write_text(json.dumps(payload, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{args.out}  {len(payload['agents'])}건 (시드 {len(seeds)} + 시연 {len(brand)})")
    if missing:
        print(f"주의: 실제 ID가 없어 준비용 ID로 남은 것 {missing} — 백엔드 등록 뒤 --ids 로 다시 만든다")


if __name__ == "__main__":
    main()
