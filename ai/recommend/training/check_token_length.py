"""
에이전트 텍스트 토큰 길이 확인 (log.md 09-08 TODO / AI-10-1 보조)

왜 필요한가.
  임베딩 모델은 입력을 '토큰'(단어보다 작은 조각) 단위로 자른 뒤 처리하는데,
  모델마다 한 번에 받을 수 있는 최대 토큰 수가 정해져 있다.
  그 길이를 넘으면 오류가 나지 않고 **뒤가 조용히 잘린다.**
  ko-sroberta-multitask는 최대 128토큰뿐이라, 에이전트 설명 + 스키마 필드 설명을
  붙였을 때 뒷부분(출력 스키마 설명)이 통째로 사라질 수 있다.
  그러면 검색 품질이 떨어지는데 원인이 안 보인다. 선정 전에 재는 것이 맞다.

실행:  python check_token_length.py
"""

import json
from pathlib import Path

from transformers import AutoTokenizer

DATA = Path(__file__).resolve().parents[1] / "data" / "seed_agents.json"

# 후보 3개의 최대 입력 길이 (09-08 baseline 측정값)
CANDIDATES = {
    "jhgan/ko-sroberta-multitask": 128,
    "intfloat/multilingual-e5-base": 512,
    "nlpai-lab/KURE-v1": 8192,
}


def build_text(agent: dict, schemas: dict) -> str:
    """임베딩에 넣을 문서 텍스트를 조립한다. (가이드 §12.5)

    이름 / 설명 / 태그 / 카테고리 / 입력 스키마 설명 / 출력 스키마 설명.
    전부 공개 메타데이터다 — 프롬프트 원문은 절대 들어가지 않는다 (SEC-001).
    """
    src = schemas.get(agent["input_schema_id"], {})
    dst = schemas.get(agent["output_schema_id"], {})
    return "\n".join([
        agent["name"],
        agent["description"],
        " ".join(agent["tags"]),
        agent["category"],
        f"입력: {src.get('summary', '')} — {src.get('fields', '')}",
        f"출력: {dst.get('summary', '')} — {dst.get('fields', '')}",
    ])


def main():
    data = json.loads(DATA.read_text(encoding="utf-8"))
    agents, schemas = data["agents"], data["schemas"]

    texts = {a["id"]: build_text(a, schemas) for a in agents}
    print(f"에이전트 {len(agents)}개 · 임베딩 텍스트 조립 완료\n")
    print("─" * 70)
    print(texts[agents[0]["id"]])          # 조립 결과 한 건 눈으로 확인
    print("─" * 70, "\n")

    for name, limit in CANDIDATES.items():
        tok = AutoTokenizer.from_pretrained(name)
        lens = {aid: len(tok.encode(t)) for aid, t in texts.items()}

        over = {aid: n for aid, n in lens.items() if n > limit}
        mx = max(lens.values())
        avg = sum(lens.values()) / len(lens)

        mark = "❌ 잘림 발생" if over else "✅ 여유"
        print(f"{name}")
        print(f"  최대 입력 {limit} 토큰 | 실제 최대 {mx} / 평균 {avg:.0f}  →  {mark}")
        if over:
            print(f"  초과 {len(over)}건 — 뒤가 잘리는 에이전트:")
            for aid, n in sorted(over.items(), key=lambda x: -x[1])[:5]:
                nm = next(a["name"] for a in agents if a["id"] == aid)
                print(f"    {n:4d} 토큰  {aid}  {nm}")
            print(f"  ※ 잘린 만큼이 곧 '출력 스키마 설명이 검색에 안 쓰인다'는 뜻")
        print()

    print("판정 기준")
    print("  잘림이 없으면       → 그 모델은 이 카탈로그로 쓸 수 있다")
    print("  ko-sroberta만 잘리면 → ① 스키마 필드 설명을 빼고 재측정")
    print("                        ② 그래도 잘리면 e5-base 재검토 (격차 0.033 약점 감수)")


if __name__ == "__main__":
    main()
