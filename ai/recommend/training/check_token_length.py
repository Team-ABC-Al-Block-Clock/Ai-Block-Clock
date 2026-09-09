"""
에이전트 텍스트 토큰 길이 확인 (AI-10-1 보조)

왜 필요한가.
  임베딩 모델은 입력을 '토큰'(단어보다 작은 조각) 단위로 자른 뒤 처리하는데,
  모델마다 한 번에 받을 수 있는 최대 토큰 수가 정해져 있다.
  그 길이를 넘으면 오류가 나지 않고 **뒤가 조용히 잘린다.**
  ko-sroberta-multitask는 최대 128토큰뿐이라, 에이전트 설명 + 스키마 필드 설명을
  붙였을 때 뒷부분(출력 스키마 설명)이 통째로 사라질 수 있다.
  그러면 검색 품질이 떨어지는데 원인이 안 보인다. 선정 전에 재는 것이 맞다.

실행:  python check_token_length.py
"""

from transformers import AutoTokenizer

from catalog import build_agent_text, load_agents, load_schemas

# 후보 3개의 최대 입력 길이 (09-08 baseline 측정값)
CANDIDATES = {
    "jhgan/ko-sroberta-multitask": 128,
    "intfloat/multilingual-e5-base": 512,
    "nlpai-lab/KURE-v1": 8192,
}


def main():
    agents = load_agents()
    schemas = load_schemas()

    # 스키마 설명 포함 / 제외 두 가지를 함께 잰다.
    # 128토큰에 걸리면 '제외' 쪽이 대안이 되므로 미리 비교해 둔다.
    variants = {
        "스키마 설명 포함": {a["agent_id"]: build_agent_text(a, schemas, True) for a in agents},
        "스키마 설명 제외": {a["agent_id"]: build_agent_text(a, schemas, False) for a in agents},
    }

    print(f"에이전트 {len(agents)}개 · 임베딩 텍스트 조립 완료\n")
    print("─" * 70)
    print(variants["스키마 설명 포함"][agents[0]["agent_id"]])   # 조립 결과 한 건 눈으로 확인
    print("─" * 70, "\n")

    for name, limit in CANDIDATES.items():
        tok = AutoTokenizer.from_pretrained(name)
        print(f"{name}  (최대 {limit} 토큰)")

        for label, texts in variants.items():
            lens = {aid: len(tok.encode(t)) for aid, t in texts.items()}
            over = {aid: n for aid, n in lens.items() if n > limit}
            mx = max(lens.values())
            avg = sum(lens.values()) / len(lens)

            mark = "❌ 잘림 발생" if over else "✅ 여유"
            print(f"  [{label}] 실제 최대 {mx} / 평균 {avg:.0f}  →  {mark}")

            if over:
                print(f"    초과 {len(over)}건 — 뒤가 잘리는 에이전트:")
                for aid, n in sorted(over.items(), key=lambda x: -x[1])[:5]:
                    nm = next(a["name"] for a in agents if a["agent_id"] == aid)
                    print(f"      {n:4d} 토큰  {aid}  {nm}")
                print("    ※ 잘린 만큼이 곧 '출력 스키마 설명이 검색에 안 쓰인다'는 뜻")
        print()

    print("판정 기준")
    print("  잘림이 없으면            → 그 모델은 이 카탈로그로 쓸 수 있다")
    print("  '포함'만 잘리면          → baseline_search.py 의 INCLUDE_SCHEMA_TEXT=False 로")
    print("                             재측정해 검색 점수가 유지되는지 확인")
    print("  '제외'까지 잘리면        → 그 모델은 후보에서 제외")


if __name__ == "__main__":
    main()
