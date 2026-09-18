"""
시연 첫 화면 검증 — 랜딩 질의로 시연 에이전트 2개가 추천되는가 (S15P21A403-275 준비)

무엇을 확인하는가.
  시연의 첫 칸은 **랜딩에 자연어를 입력하면 추천 목록이 뜨는 것**이다.
  여기가 막히면 뒤가 전부 무의미하다. 사용자가 입력한 한 문장에
  **시연 에이전트 2개가 둘 다 상위에 올라와야** 캔버스에 올릴 수 있다.

  (워크플로우 구성과 실행 방식은 트랙 A 영역이다. 이 스크립트는
   추천이 무엇을 내놓는가만 본다 — 추천 품질은 B 영역이다.)

      ag_seed_01  성적 데이터 분석기    edu.roster@1   → edu.analysis@1
      ag_seed_02  가정통신문 작성기     edu.analysis@1 → edu.letter@1

  두 에이전트의 스키마가 이어지므로, 둘을 나란히 배치하면 워크플로우가 된다.

왜 질의문을 측정해서 고르는가.
  질의 하나로 **성격이 다른 두 에이전트**를 동시에 상위로 올리는 건 쉽지 않다.
  게다가 카탈로그에는 `ag_seed_03 학업 성취도 통계 산출기` 가 있어서
  `ag_seed_01` 자리를 뺏을 수 있다 (판별 테스트용으로 일부러 심은 유사 쌍이다).

  로드맵 W4 화요일에 **"당일 즉흥 입력 금지"** 가 적혀 있다.
  시연 질의는 정해두는 것이고, 정하려면 재야 한다.

실행:
    # 터미널 1 — 추천 서버
    cd ai/recommend
    uvicorn app.main:app --port 8100

    # 터미널 2
    cd ai/demo
    python check_demo_scenario.py
"""

import json
import sys
from pathlib import Path

import httpx

BASE = "http://127.0.0.1:8100"
SEED = Path(__file__).resolve().parents[1] / "recommend" / "data" / "seed_agents.json"

DEMO_AGENTS = ["ag_seed_01", "ag_seed_02"]
TOP_K = 5

# 시연 질의 후보. 학원 원장이 실제로 칠 법한 말투로 쓴다.
# ★ 에이전트 이름을 그대로 치면 당연히 잘 나온다. 그건 시연 가치가 없다.
#   "가정통신문", "성적 분석" 같은 이름 단어를 피한 문장을 섞어 둔다.
CANDIDATES = [
    "학원 성적 정리해서 학부모님께 안내문 보내야 해요",
    "이번 달 점수 분석하고 집에 보낼 편지까지 만들고 싶어",
    "애들 성적 어떻게 변했는지 보고 부모님께 알려드려야 해",
    "시험 결과 정리해서 보호자한테 전달할 글 써줘",
    "학생별로 점수 변화 확인하고 가정통신문 쓰려고",
]


def rank_of(recs: list[dict], agent_id: str) -> int | None:
    for i, r in enumerate(recs, 1):
        if r["agent_id"] == agent_id:
            return i
    return None


def main() -> None:
    agents = {a["agent_id"]: a for a in json.loads(SEED.read_text(encoding="utf-8"))["agents"]}

    # ── 스키마가 이어지는지 ──
    a1, a2 = agents[DEMO_AGENTS[0]], agents[DEMO_AGENTS[1]]
    chained = a1["output_schema_id"] == a2["input_schema_id"]
    print(f"스키마 연결   {a1['name']} [{a1['output_schema_id']}]"
          f" → {a2['name']} [{a2['input_schema_id']}]   {'OK' if chained else '끊김'}")
    if not chained:
        sys.exit("두 에이전트를 나란히 배치할 수 없습니다. 스키마를 맞춰야 합니다.")

    # ── 등급 확인 (S15P21A403-281) ──
    for a in (a1, a2):
        warn = "  ⚠ Tier 4 는 신규·미검증 등급이다. 콜드스타트 시드로 올려야 한다" \
            if a["tier"] == 4 else ""
        owner = a["owner_id"] or "(미지정)"
        print(f"등급·소유자   {a['name']:16} Tier {a['tier']} · 성공률 {a['success_rate']}"
              f" · owner {owner}{warn}")
    if a1["owner_id"] == a2["owner_id"]:
        print("              ⚠ 두 에이전트의 owner_id 가 같거나 둘 다 비어 있다.")
        print("                한 실행으로 서로 다른 두 계정에 정산이 적립되어야 한다 (금요일 관문 ④).")

    try:
        health = httpx.get(f"{BASE}/health", timeout=10).json()
    except httpx.ConnectError:
        sys.exit(f"\n추천 서버에 연결할 수 없습니다. 먼저 실행하세요:\n"
                 f"  cd ai/recommend && uvicorn app.main:app --port 8100")
    print(f"\n추천 서버     {health['status']} · {health['model_version']}"
          f" · 색인 {health['indexed_agents']}개")
    if health["indexed_agents"] == 0:
        sys.exit("인덱스가 비어 있습니다. 먼저 ai/recommend/seed_index.py 를 실행하세요.")

    # ── 질의 후보 측정 ──
    print(f"\n{'':2}{'질의':44} {'분석':>6} {'통신문':>6}  상위 3개")
    print("-" * 100)

    results = []
    for q in CANDIDATES:
        r = httpx.post(f"{BASE}/v1/recommend", json={"query": q, "top_k": TOP_K}, timeout=30)
        r.raise_for_status()
        recs = r.json()["recommendations"]
        r1, r2 = rank_of(recs, DEMO_AGENTS[0]), rank_of(recs, DEMO_AGENTS[1])
        top3 = " · ".join(x["name"] for x in recs[:3])
        results.append((q, r1, r2))
        f1 = f"{r1}위" if r1 else "없음"
        f2 = f"{r2}위" if r2 else "없음"
        print(f"{'':2}{q:44} {f1:>6} {f2:>6}  {top3}")

    # ── 판정 ──
    print("\n" + "=" * 100)
    usable = [(q, r1, r2) for q, r1, r2 in results if r1 and r2]
    if not usable:
        print("쓸 수 있는 질의가 없다 — 두 에이전트가 함께 상위에 오르는 문장이 없다.")
        print("  질의문을 바꾸거나, 두 에이전트를 각각 따로 검색해 배치하는 시연으로 바꾼다.")
        sys.exit(1)

    best = min(usable, key=lambda x: x[1] + x[2])
    print(f"추천 시연 질의:  \"{best[0]}\"")
    print(f"  {a1['name']} {best[1]}위 · {a2['name']} {best[2]}위 — 둘 다 상위 {TOP_K}개 안")
    print("\n이 문장을 시연 시나리오 스크립트(AI-09-6)에 고정한다. 당일 즉흥 입력 금지.")
    print("=" * 100)


if __name__ == "__main__":
    main()
