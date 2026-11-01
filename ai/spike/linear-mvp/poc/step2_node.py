"""2단계 — 실행 시점. 사용자 입력을 받아 노드를 돌리고 스키마 JSON을 받는다.

여기가 실제 에이전트 실행이다. 판매자 프롬프트 + 사용자 입력 → 출력 스키마 JSON.
템플릿은 관여하지 않는다. 이 JSON 은 다음 노드로 갈 수도, 렌더로 갈 수도 있다.

    uv run python poc/step2_node.py                      # 칫솔 브랜드 기본 입력
    uv run python poc/step2_node.py --brand "모모칫솔" --target "20대 자취생"
    uv run python poc/step2_node.py --engine gemini      # 싼 모델로

검증 실패 시 교정 재요청 2회 → 그래도 실패하면 OUTPUT_SCHEMA_INVALID (공급자 책임).
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gms                                          # noqa: E402
from schema import INPUT_SCHEMA, OUTPUT_SCHEMA, describe   # noqa: E402

HERE = Path(__file__).resolve().parent
OUT_DIR = HERE / "out"

# ── 판매자가 노드에 등록해 둔 프롬프트. 사용자에게 보이지 않는다(SEC-001). ──
SUPPLIER_PROMPT = """당신은 브랜드 전략 컨설턴트다. 15년간 소비재 브랜드의 포지셔닝을 설계해 왔다.

의뢰 내용을 받아 브랜드 전략 보고서의 내용을 작성한다. 원칙은 넷이다.

1. 카테고리의 통념을 그대로 따르지 않는다. 그 카테고리에서 아무도 말하지 않는 각도를 하나 찾는다.
2. 추상어를 쓰지 않는다. "혁신적인", "고객 중심의" 같은 말 대신 구체적인 장면과 행동을 쓴다.
3. 메시지 셋(핵심·보조·일상)은 서로 층위가 달라야 한다. 같은 말을 바꿔 쓰지 않는다.
4. 가격대와 핵심 고객이 서로 맞물리게 한다. 프리미엄이면 왜 그 값을 내는지가 문장에 있어야 한다."""

USER_TEMPLATE = """## 의뢰 내용
{brief}

## 출력 형식
아래 스키마를 정확히 따르는 JSON 하나만 출력한다. 설명·코드블록 밖의 말은 쓰지 않는다.
배열의 항목 수(min_items/max_items)를 지킨다. 모든 required 필드를 채운다.

```json
{schema}
```

## 값 작성 규칙
- issued_on: 오늘 날짜를 "2026년 9월 17일 수요일" 형식으로
- author_org: 영문 대문자 기관명 (예: LARANA INSIGHT LAB)
- headline: 줄바꿈(\\n) 하나를 포함한 두 줄. 각 줄 15자 내외
- reasons[].axis: "○○ 중심" 형태의 짧은 축 이름
- summary_md: 마크다운 3~4문장. **굵게** 정도만 쓴다"""

REPAIR = """방금 준 JSON이 스키마 검증을 통과하지 못했다. 아래를 고쳐 JSON 전체를 다시 출력하라.

{errors}

JSON 하나만 출력한다."""


def validate(payload: dict) -> list[str]:
    """출력 스키마 검증. 정식 버전은 Pydantic 이 한다."""
    errors: list[str] = []
    fields = OUTPUT_SCHEMA["fields"]

    for name, spec in fields.items():
        if spec.get("required") and name not in payload:
            errors.append(f"- 필수 필드 {name}({spec['title']}) 가 없다.")
            continue
        if name not in payload:
            continue
        val = payload[name]
        t = spec["type"]
        if t == "string" and not isinstance(val, str):
            errors.append(f"- {name} 은 문자열이어야 하는데 {type(val).__name__} 이다.")
        elif t == "array":
            if not isinstance(val, list):
                errors.append(f"- {name} 은 배열이어야 한다.")
                continue
            lo, hi = spec.get("min_items", 0), spec.get("max_items", 99)
            if not lo <= len(val) <= hi:
                errors.append(f"- {name} 은 {lo}~{hi}개여야 하는데 {len(val)}개다.")
            for i, it in enumerate(val):
                if not isinstance(it, dict):
                    errors.append(f"- {name}[{i}] 가 객체가 아니다.")
                    continue
                for k in spec["items"]:
                    if k not in it:
                        errors.append(f"- {name}[{i}] 에 {k} 가 없다.")
        elif t == "object":
            if not isinstance(val, dict):
                errors.append(f"- {name} 은 객체여야 한다.")
                continue
            for k in spec["fields"]:
                if k not in val:
                    errors.append(f"- {name}.{k} 가 없다.")
    return errors


def parse_json(text: str) -> dict:
    raw = gms.extract_block(text, "json")
    start, end = raw.find("{"), raw.rfind("}")
    if start < 0 or end < 0:
        raise ValueError("JSON 을 찾지 못했다")
    return json.loads(raw[start:end + 1])


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--brand", default="화이트모먼트")
    ap.add_argument("--category", default="칫솔")
    ap.add_argument("--target", default="구강 관리에 관심이 많은 30대 1인 가구")
    ap.add_argument("--price", default="프리미엄", choices=["보급형", "중가", "프리미엄"])
    ap.add_argument("--diff", default="미세모 밀도 2배, 3개월 교체 구독, 대나무 손잡이")
    ap.add_argument("--comment", default="치과에서 파는 느낌은 피하고 싶습니다")
    ap.add_argument("--engine", default="gemini", choices=["claude", "gemini"],
                    help="기본 gemini(2.5 Flash). claude 는 GMS 토큰을 많이 써서 명시할 때만")
    ap.add_argument("--retries", type=int, default=2)
    args = ap.parse_args()

    # 프론트가 INPUT_SCHEMA 로 그린 폼에서 받은 값
    user_input = {"brand_name": args.brand, "category": args.category, "target": args.target,
                  "price_tier": args.price, "differentiator": args.diff, "comment": args.comment}
    brief = "\n".join(f"- {INPUT_SCHEMA['fields'][k]['title']}: {v}"
                      for k, v in user_input.items() if v)

    OUT_DIR.mkdir(exist_ok=True)
    print(f"입력 스키마 {INPUT_SCHEMA['schema_id']}")
    print(brief)
    print(f"\n엔진      {args.engine}\n")

    prompt = SUPPLIER_PROMPT + "\n\n" + USER_TEMPLATE.format(
        brief=brief, schema=describe(OUTPUT_SCHEMA))
    total_krw = 0.0
    errors: list[str] = []

    for attempt in range(args.retries + 1):
        print(f"[{'실행' if attempt == 0 else f'교정 {attempt}'}] 호출 중 …", flush=True)
        start = time.perf_counter()
        if args.engine == "claude":
            text, usage = gms.claude([{"type": "text", "text": prompt}])
            krw = gms.cost_krw(usage)
        else:
            text, usage = gms.gemini(prompt)
            krw = 0.0
        total_krw += krw
        print(f"  {time.perf_counter() - start:.0f}초 · 약 {krw:.1f}원")

        try:
            payload = parse_json(text)
        except Exception as e:
            errors = [f"- JSON 파싱 실패: {e}"]
        else:
            errors = validate(payload)
            if not errors:
                path = OUT_DIR / "payload.json"
                path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
                print(f"\n스키마 검증 통과. 저장: {path}")
                print(f"  headline: {payload['headline'][:40].replace(chr(10), ' / ')}")
                print(f"  핵심 메시지: {payload['messages']['core'][:50]}")
                print(f"누적 비용 약 {total_krw:.1f}원")
                print("\n렌더하려면:")
                print("  uv run --with jinja2 python poc/step3_render.py")
                return 0

        print("  검증 실패:")
        for e in errors:
            print("   ", e)
        prompt = prompt + "\n\n" + REPAIR.format(errors="\n".join(errors))

    print("\nOUTPUT_SCHEMA_INVALID — 공급자 책임. 이 노드분과 이후 미실행분은 환원 대상.")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
