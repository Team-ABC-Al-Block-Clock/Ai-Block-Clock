"""
가정통신문 노드 스키마 준수율 측정 (AI-06-7)

로드맵 W3 월 목표 **90%**, W4 월 최종 **95%**. 미만이면 프롬프트를 고친다.

무엇을 재는가.
  "JSON 형식이 맞나"는 구조화 출력 강제(AI-02-3)가 이미 보장한다.
  여기서 보는 것은 **형식은 맞는데 내용이 틀린** 경우다. 이쪽이 훨씬 자주 터진다.

    ① letters 개수 != 학생 수   — 가장 흔하다. 20명 중 몇 명이 조용히 빠진다
    ② 이름 불일치              — 없는 학생을 만들거나 이름을 바꾼다
    ③ tone 과 trend 불일치     — 오른 학생에게 경고문이 가면 시연이 무너진다
    ④ 금칙어                   — 학부모에게 나가는 글이다
    ⑤ 숫자 근거 없음           — "열심히 하고 있습니다" 같은 빈 문장

사용법 두 가지.

  # ⓐ 이미 받아둔 출력 파일을 검사한다 (API 키 불필요)
  python check_compliance.py --file outputs/letter_run1.json

  # ⓑ 직접 10회 호출해서 준수율을 낸다 (.env 필요)
  #   변수 이름은 ai/spike/linear-mvp/.env.example 과 같다
  export LLM_API_KEY=...
  export LLM_BASE_URL=...        # ★ GMS 주소. Google 직접 주소로는 키가 안 먹는다
  export LLM_MODEL=...
  python check_compliance.py --runs 10 --branch down

  ★ 키는 트랙 A가 관리한다. 코드·커밋·채팅에 넣지 않는다. .env 는 각자 로컬.
"""

import argparse
import json
import os
import re
import sys
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = BASE_DIR / "data"
PROMPT_DIR = BASE_DIR.parent / "prompts"

# 학부모에게 나가는 글에 있으면 안 되는 표현.
# 부분 문자열로 검사하므로 어미 변화를 어느 정도 흡수한다.
FORBIDDEN = [
    "가망", "소질이 없", "머리가", "포기",
    "산만", "주의력", "집중력 장애", "ADHD",
    "반드시 오릅", "보장", "확실히 올라",
    "수강료", "환불", "결제",
    "등수", "석차", "꼴찌", "1등",
]

# 숫자 근거가 들어갔는지 — 점수·변화폭·과목명 중 하나는 있어야 한다
HAS_NUMBER = re.compile(r"\d")


def load_roster(branch: str) -> dict:
    return json.loads((DATA_DIR / f"demo_roster_{branch}.json").read_text(encoding="utf-8"))


def load_analysis(branch: str) -> dict:
    path = DATA_DIR / f"demo_analysis_{branch}.json"
    if not path.exists():
        raise SystemExit(f"{path.name} 이 없습니다. 먼저 analysis_reference.py 를 실행하세요.")
    return json.loads(path.read_text(encoding="utf-8"))


def check(output: dict, analysis: dict, node_tone: str) -> list[str]:
    """출력 한 벌을 검사한다. 문제 목록을 돌려준다. 빈 목록이면 통과."""
    problems = []

    letters = output.get("letters")
    if not isinstance(letters, list):
        return ["letters 배열이 없음"]

    expected = {s["name"]: s for s in analysis["students"]}

    # ① 개수
    if len(letters) != len(expected):
        problems.append(f"편지 {len(letters)}통 / 학생 {len(expected)}명")

    seen = set()
    for i, letter in enumerate(letters):
        name = letter.get("student_name")
        body = letter.get("body") or ""
        tone = letter.get("tone")

        # ② 이름
        if name not in expected:
            problems.append(f"[{i}] 명단에 없는 이름: {name!r}")
            continue
        if name in seen:
            problems.append(f"[{i}] 이름 중복: {name}")
        seen.add(name)

        # ③ 톤 — 오른 학생은 노드 톤과 무관하게 격려형이어야 한다
        want = "encourage" if expected[name]["trend"] == "up" else node_tone
        if tone != want:
            problems.append(f"{name} tone={tone} (trend={expected[name]['trend']} → {want} 기대)")

        # ④ 금칙어
        for word in FORBIDDEN:
            if word in body:
                problems.append(f"{name} 금칙어 '{word}'")

        # ⑤ 숫자 근거 · 길이
        if not HAS_NUMBER.search(body):
            problems.append(f"{name} 숫자 근거 없음")
        sentences = [x for x in re.split(r"[.!?]\s*", body) if x.strip()]
        if not 2 <= len(sentences) <= 5:
            problems.append(f"{name} 문장 {len(sentences)}개 (3~4문장 목표)")

    missing = set(expected) - seen
    if missing:
        problems.append(f"빠진 학생 {len(missing)}명: {sorted(missing)[:5]}")

    overall = output.get("overall_comment") or ""
    if not overall.strip():
        problems.append("overall_comment 비어 있음")
    else:
        for name in expected:
            if name in overall:
                problems.append(f"overall_comment 에 개별 학생 이름({name}) 포함")
                break

    return problems


def load_prompt_blocks() -> dict[str, str]:
    """letter_node.md 의 `<!-- prompt:NAME -->` 바로 뒤 코드 블록을 읽어온다.

    ★ 명세와 실행이 같은 원본을 보게 한다. 여기에 프롬프트를 복붙해두면
      .md 를 고쳐도 측정은 옛날 프롬프트로 도는 일이 생긴다.
      블록 순서로 집으면 문서에 코드 블록을 하나만 추가해도 조용히 어긋나므로
      이름표를 붙여서 찾는다.
    """
    spec = (PROMPT_DIR / "letter_node.md").read_text(encoding="utf-8")
    blocks = dict(re.findall(r"<!-- prompt:(\w+) -->\s*```\w*\n(.*?)```", spec, re.S))

    required = {"common", "encourage", "warn"}
    missing = required - blocks.keys()
    if missing:
        raise SystemExit(
            f"letter_node.md 에서 프롬프트 블록을 찾지 못했습니다: {sorted(missing)}\n"
            f"`<!-- prompt:이름 -->` 주석이 코드 블록 바로 앞에 있어야 합니다."
        )
    return blocks


def build_messages(branch: str) -> list[dict]:
    blocks = load_prompt_blocks()
    tone_block = blocks["warn"] if branch == "down" else blocks["encourage"]

    roster = load_roster(branch)
    analysis = load_analysis(branch)
    user = (f"[원장 요청 사항]\n{roster['context']['comment']}\n\n"
            f"[학생별 집계]\n{json.dumps(analysis, ensure_ascii=False)}")
    if "fewshot" in blocks:
        # 안내 문장은 블록 안에 이미 들어 있다. 여기서 또 붙이지 않는다
        user += "\n\n" + blocks["fewshot"].strip()

    return [
        {"role": "system", "content": blocks["common"] + "\n" + tone_block},
        {"role": "user", "content": user},
    ]


def call_llm(messages: list[dict]) -> dict:
    try:
        from openai import OpenAI
    except ImportError:
        raise SystemExit("python -m pip install openai")

    key = os.environ.get("LLM_API_KEY")
    base_url = os.environ.get("LLM_BASE_URL")
    model = os.environ.get("LLM_MODEL")

    missing = [n for n, v in (("LLM_API_KEY", key), ("LLM_BASE_URL", base_url),
                              ("LLM_MODEL", model)) if not v]
    if missing:
        raise SystemExit(
            f"{' · '.join(missing)} 가 설정되지 않았습니다.\n"
            f"ai/spike/linear-mvp/.env.example 의 변수 이름을 그대로 쓴다. 키 값은 트랙 A에게 받는다."
        )

    # ★ base_url 을 비워두면 라이브러리가 Google 직접 주소로 붙는데, 우리 키는 GMS 경유라
    #   거기서는 먹지 않는다. 그래서 기본값을 두지 않고 반드시 받도록 했다.
    client = OpenAI(api_key=key, base_url=base_url)
    resp = client.chat.completions.create(
        model=model,
        messages=messages,
        temperature=0.3,
        response_format={"type": "json_object"},
    )
    return json.loads(resp.choices[0].message.content)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--file", help="검사할 출력 JSON (API 호출 없이)")
    ap.add_argument("--runs", type=int, default=0, help="LLM 호출 횟수")
    ap.add_argument("--branch", default="down", choices=["down", "up"])
    args = ap.parse_args()

    node_tone = "warn" if args.branch == "down" else "encourage"
    analysis = load_analysis(args.branch)

    if args.file:
        output = json.loads(Path(args.file).read_text(encoding="utf-8"))
        problems = check(output, analysis, node_tone)
        if problems:
            print(f"실패 — {len(problems)}건")
            for p in problems[:20]:
                print(f"  · {p}")
            sys.exit(1)
        print("통과")
        return

    if not args.runs:
        ap.error("--file 또는 --runs 중 하나가 필요합니다")

    messages = build_messages(args.branch)
    out_dir = BASE_DIR / "outputs"
    out_dir.mkdir(exist_ok=True)

    passed, all_problems = 0, []
    for i in range(1, args.runs + 1):
        try:
            output = call_llm(messages)
        except Exception as e:                       # 호출 자체 실패도 준수율에 센다
            print(f"{i:>3}회  호출 실패: {type(e).__name__}")
            all_problems.append(f"호출 실패: {e}")
            continue

        (out_dir / f"letter_{args.branch}_{i:02d}.json").write_text(
            json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")

        problems = check(output, analysis, node_tone)
        if problems:
            print(f"{i:>3}회  실패 {len(problems)}건 — {problems[0]}")
            all_problems += problems
        else:
            print(f"{i:>3}회  통과")
            passed += 1

    rate = passed / args.runs
    print(f"\n준수율  {passed}/{args.runs} = {rate:.0%}   (W3 목표 90% · W4 목표 95%)")

    if all_problems:
        counts = {}
        for p in all_problems:
            kind = re.sub(r"^\S+\s", "", p).split("(")[0].strip()[:40]
            counts[kind] = counts.get(kind, 0) + 1
        print("\n자주 나온 문제")
        for kind, n in sorted(counts.items(), key=lambda x: -x[1])[:6]:
            print(f"  {n:>3}회  {kind}")
        print("\n가장 많이 나온 것부터 프롬프트를 고친다. 한 번에 하나씩 고치고 다시 잰다.")

    sys.exit(0 if rate >= 0.9 else 1)


if __name__ == "__main__":
    main()
