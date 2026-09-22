"""로컬 시연 준비물 생성. 네트워크·DB 변경 없음. 실제 등록 payload와 구분한다."""
import argparse
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parent
AI = ROOT.parent


def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def seed_score(row):
    # 등급 정책 구현은 백엔드 소유. 이 함수는 명세 v1.0 시드 값 검산에만 사용한다.
    u, failed, v, rating = (row[k] for k in ("successful_runs", "supplier_failures", "review_count", "average_rating"))
    if any(not isinstance(x, int) or isinstance(x, bool) or x < 0 for x in (u, failed, v)):
        raise ValueError("실행·평가 횟수는 음수가 아닌 정수여야 합니다")
    if v and (not isinstance(rating, (int, float)) or not 1 <= rating <= 5):
        raise ValueError("평가는 1~5점이어야 합니다")
    success = u / (u + failed) if u + failed else 1.0
    bayes = (v * (rating or 0) + 10 * 3.5) / (v + 10)
    score = (.6 * (bayes - 1) / 4 * 100 + .4 * math.log10(u + 1) / math.log10(1000) * 100) * success ** 2
    tier = 1 if score >= 80 and success >= .98 and v >= 50 else 2 if score >= 65 and success >= .95 and v >= 20 else 3 if score >= 45 and success >= .90 else 4
    return {"success_rate": success, "score": round(score, 4), "tier": tier,
            "premium_rate": {1: .5, 2: .3, 3: .1, 4: 0}[tier]}


def prepare():
    catalog = {}
    for path in sorted((AI / "schemas" / "catalog").glob("*.json")):
        item = read(path)
        if "schema_id" not in item:
            continue
        catalog[item["schema_id"]] = {"summary": item.get("description", item["title"]),
            "fields": ", ".join(f"{key}({value.get('title', key)})" for key, value in item["properties"].items())}
    content = read(ROOT / "brand" / "market_content.json")
    seeds = read(ROOT / "brand" / "seed_values.json")["agents"]
    seed_map = {r["local_key"]: r for r in seeds}
    agents, scores = [], []
    for item in content["agents"]:
        for schema_id in [*item["input_ports"].values(), item["output_schema_id"]]:
            if schema_id not in catalog:
                raise ValueError(f"카탈로그에 없는 ID: {schema_id}")
        row = seed_map[item["local_key"]]
        calculated = seed_score(row)
        if calculated["tier"] != row["expected_tier"]:
            raise ValueError("시드의 기대 Tier와 검산 결과 불일치")
        scores.append({**row, **calculated})
        agents.append({k: item[k] for k in ("name", "description", "tags", "category", "input_schema_id", "output_schema_id")}
                      | {"agent_id": item["local_key"], "owner_id": item["owner_key"], "tier": calculated["tier"],
                         "success_rate": calculated["success_rate"], "is_public": True})
    return {"schema_catalog.json": {"description": "A 카탈로그에서 생성. 현재 서비스는 스키마 설명을 임베딩에 사용하지 않음.", "schemas": catalog},
            "brand_agents_local.json": {"status": "local_evaluation_only_not_registered", "agents": agents},
            "seed_values_calculated.json": {"status": "synthetic_demo_only_not_applied", "agents": scores}}


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--out", type=Path, default=ROOT / "brand" / "prepared")
    args = ap.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    for name, data in prepare().items():
        (args.out / name).write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(args.out / name)


if __name__ == "__main__":
    main()
