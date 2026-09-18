"""로컬 확인용 — 시드 에이전트를 인덱싱하고 추천을 한 번 불러본다.

실제 서비스에서는 백엔드가 에이전트 공개 시점에 /v1/index/agents 를 호출한다.
이 스크립트는 백엔드 없이 추천 API 가 도는지 확인하기 위한 개발용 도구다.

사용법:
    # 터미널 1 — 서버
    cd ai/recommend
    uvicorn app.main:app --reload --port 8100

    # 터미널 2 — 시드 투입 + 확인
    cd ai/recommend
    python seed_index.py
"""

import json
import os
import sys
from pathlib import Path

import httpx

BASE = "http://127.0.0.1:8100"
DATA = Path(__file__).resolve().parent / "data" / "seed_agents.json"

DEMO_QUERIES = [
    "학원 애들 점수가 얼마나 올랐는지 보고 싶은데",
    "부모님께 보낼 안내문 좀 써줘",
    "우리 팀이 짠 코드 좀 봐줄 사람 없나",
    "같은 내용을 여러 사람에게 한꺼번에 전달해야 하는데",
]


def main():
    try:
        health = httpx.get(f"{BASE}/health", timeout=10).json()
    except httpx.ConnectError:
        sys.exit(f"서버에 연결할 수 없습니다. 먼저 실행하세요:\n"
                 f"  cd ai/recommend && uvicorn app.main:app --port 8100")

    print(f"서버 상태: {health['status']} · model_version={health['model_version']}")
    print(f"하이브리드: ABC_HYBRID={os.environ.get('ABC_HYBRID', '(기본값)')}"
          f"   — lexical 이 숫자면 켜진 것, — 이면 꺼진 것\n")

    # ── 인덱싱 ──
    payload = json.loads(DATA.read_text(encoding="utf-8"))
    payload["op"] = "rebuild"        # 통째로 다시 만든다
    r = httpx.post(f"{BASE}/v1/index/agents", json=payload, timeout=120)
    r.raise_for_status()
    print("인덱싱:", r.json(), "\n")

    # ── 추천 ──
    for q in DEMO_QUERIES:
        r = httpx.post(f"{BASE}/v1/recommend", json={"query": q, "top_k": 3}, timeout=30)
        r.raise_for_status()
        data = r.json()
        print(f'"{q}"   ({data["latency_ms"]}ms)')
        for i, rec in enumerate(data["recommendations"], 1):
            b = rec["breakdown"]
            # lexical 은 하이브리드 검색이 꺼져 있으면 null 이다 (09-17 추가)
            lex = f" · lexical {b['lexical']:6.2f}" if b.get("lexical") is not None else " · lexical  —   "
            print(f"  {i}. {rec['score']:.3f}  {rec['name']:<16}"
                  f"   (semantic {b['semantic']:.3f}{lex} · trust {b['trust']:.3f} · Tier {rec['tier']})")
        print()


if __name__ == "__main__":
    main()
