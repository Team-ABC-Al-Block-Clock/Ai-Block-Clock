"""
합성 질의 생성 (GPL / doc2query) — AI-10-9 / S15P21A403-201

문제.  파인튜닝에는 (사용자 질의, 정답 에이전트) 쌍이 필요한데 서비스가 없어 실데이터가 0건이다.
해결.  에이전트 설명을 거꾸로 돌려 "이 에이전트를 찾으려는 사람이 칠 법한 말"을 생성한다.
       학습 데이터가 없을 때 쓰는 정보 검색 분야의 표준 기법이다. (가이드 §12.5)

★ 평가 세트 오염 방지
  생성된 질의가 골든 쿼리 20개와 겹치면, 학습에 쓴 문장으로 성능을 재는 꼴이 된다.
  그러면 파인튜닝 후 점수가 올라도 실력인지 암기인지 구분할 수 없다.
  이 스크립트는 골든 쿼리와 같은 문장을 자동으로 걸러낸다.

★ API 키
  ai/recommend/.env 파일에 아래 한 줄. 루트 .gitignore 가 .env 를 막고 있다 (SEC-003).
      ANTHROPIC_API_KEY=sk-ant-...

준비:  python -m pip install anthropic python-dotenv
실행:  python generate_synthetic_queries.py
"""

import json
import os
import re
import time
from pathlib import Path

from anthropic import Anthropic
from dotenv import load_dotenv

from catalog import load_agents, load_queries

# ─────────────────────────────────────────────────────────────
# 설정
# ─────────────────────────────────────────────────────────────
MODEL = "claude-haiku-4-5-20251001"   # 단순 생성 작업이라 가장 싸고 빠른 모델로 충분
N_PER_AGENT = 22                      # 에이전트당 질의 수 → 20개 × 22 ≈ 440쌍
LIMIT = None                          # 처음엔 2 로 두고 시험 실행할 것. 확인 후 None

DATA_DIR = Path(__file__).resolve().parents[1] / "data"
OUT_PATH = DATA_DIR / "synthetic_queries.jsonl"

PROMPT = """당신은 AI 에이전트 마켓플레이스의 검색 로그를 만드는 일을 합니다.

아래는 마켓에 등록된 에이전트 하나의 공개 정보입니다.

이름: {name}
설명: {description}
태그: {tags}
카테고리: {category}

이 에이전트를 찾고 싶은 사람이 **랜딩 화면 검색창에 실제로 칠 법한 문장**을 {n}개 만드세요.

반드시 지킬 것
1. 에이전트 이름에 들어간 단어를 그대로 쓰지 마세요. 사용자는 에이전트 이름을 모릅니다.
   자기 상황을 자기 말로 설명합니다.
2. 말투를 섞으세요. 존댓말과 반말, 완성된 문장과 짧은 명령형을 골고루.
   ("~해줘", "~하고 싶은데", "~할 수 있나요", "~좀 만들어 주세요", 명사로 끝나는 짧은 검색어)
3. 상황을 다양하게 하세요. 같은 요청을 조사만 바꿔 반복하지 마세요.
   업무 맥락, 급한 상황, 막연한 요청, 구체적인 요청을 섞습니다.
4. 한국어로만 쓰세요.
5. 한 줄에 하나씩, 번호나 따옴표나 설명 없이 문장만 출력하세요.

출력 예시 형식:
부모님께 보낼 안내문 좀 써줘
학부모한테 뭐라고 전달해야 할지 모르겠어
가정 안내 문구 작성"""


def generate_for_agent(client: Anthropic, agent: dict) -> list[str]:
    """에이전트 하나에 대한 질의 목록을 받아온다."""
    msg = client.messages.create(
        model=MODEL,
        max_tokens=2000,
        messages=[{
            "role": "user",
            "content": PROMPT.format(
                name=agent["name"],
                description=agent["description"],
                tags=" ".join(agent["tags"]),
                category=agent["category"],
                n=N_PER_AGENT,
            ),
        }],
    )
    text = msg.content[0].text

    lines = []
    for raw in text.splitlines():
        s = raw.strip()
        s = re.sub(r'^[\-\*\d]+[\.\)]?\s*', '', s)   # 앞의 번호·불릿 제거
        s = s.strip('"\'' + '「」“”')
        if 3 <= len(s) <= 60:
            lines.append(s)
    return lines


def main():
    load_dotenv(Path(__file__).resolve().parents[1] / ".env")
    key = os.environ.get("ANTHROPIC_API_KEY")
    if not key:
        raise SystemExit(
            "ANTHROPIC_API_KEY 가 없습니다.\n"
            "ai/recommend/.env 에 다음 한 줄을 넣으세요:\n"
            "  ANTHROPIC_API_KEY=sk-ant-..."
        )

    client = Anthropic(api_key=key)
    agents = load_agents()[:LIMIT]

    # 평가 세트 오염 방지 — 골든 쿼리와 같은 문장은 버린다
    golden = {q["query"].strip() for q in load_queries()}

    # 이어서 실행 가능하게 — 이미 만든 에이전트는 건너뛴다
    done, rows = set(), []
    if OUT_PATH.exists():
        for line in OUT_PATH.read_text(encoding="utf-8").splitlines():
            if line.strip():
                r = json.loads(line)
                rows.append(r)
                done.add(r["positive_agent_id"])
        print(f"기존 {len(rows)}쌍 발견 — 에이전트 {len(done)}개는 건너뜁니다\n")

    seen = {r["query"] for r in rows}
    dropped_golden = 0

    for i, agent in enumerate(agents, 1):
        aid = agent["agent_id"]
        if aid in done:
            continue

        print(f"[{i}/{len(agents)}] {agent['name']} ... ", end="", flush=True)
        try:
            queries = generate_for_agent(client, agent)
        except Exception as e:
            print(f"실패 ({type(e).__name__}) — 건너뜁니다")
            continue

        added = 0
        for q in queries:
            if q in golden:            # ★ 평가 세트와 겹치면 버린다
                dropped_golden += 1
                continue
            if q in seen:              # 다른 에이전트에서 이미 나온 문장
                continue
            seen.add(q)
            rows.append({"query": q, "positive_agent_id": aid})
            added += 1

        print(f"{added}개")

        # 매 에이전트마다 저장 — 중간에 끊겨도 앞부분이 남는다
        with OUT_PATH.open("w", encoding="utf-8") as f:
            for r in rows:
                f.write(json.dumps(r, ensure_ascii=False) + "\n")

        time.sleep(0.5)   # 레이트 리밋 여유

    print()
    print("─" * 58)
    print(f"총 {len(rows)}쌍 생성 → {OUT_PATH.name}")
    print(f"에이전트당 평균 {len(rows) / max(len(set(r['positive_agent_id'] for r in rows)), 1):.1f}개")
    if dropped_golden:
        print(f"골든 쿼리와 중복되어 제외: {dropped_golden}개  (평가 세트 오염 방지)")
    print("─" * 58)
    print("\n샘플 5개")
    for r in rows[:5]:
        print(f"  {r['query']}   → {r['positive_agent_id']}")


if __name__ == "__main__":
    main()
