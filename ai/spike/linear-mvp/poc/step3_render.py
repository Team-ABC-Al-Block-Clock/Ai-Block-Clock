"""3단계 — 사용자가 고른 템플릿에 결과 JSON 을 부어 결과물을 만든다. LLM 을 부르지 않는다.

    uv run --with jinja2 python poc/step3_render.py --list          # 고를 수 있는 서식
    uv run --with jinja2 python poc/step3_render.py                 # 기본값으로
    uv run --with jinja2 python poc/step3_render.py --template 2    # 목록의 2번으로
    uv run --with jinja2 python poc/step3_render.py --sample --pdf  # 더미 데이터 + PDF

## 불일치 안전망 3중

  ① 사전검사   템플릿이 쓰는 필드와 JSON 의 필드를 대조. 부족하면 경고
  ② StrictUndefined  없는 필드를 참조하면 조용히 빈칸이 되는 대신 즉시 예외
  ③ 폴백       그래도 실패하면 플랫폼 기본 템플릿으로 다시 렌더

렌더 실패는 실행을 실패시키지 않는다. 노드는 이미 성공했고 정산도 끝났다.
서식 때문에 전부 환불하는 것은 말이 안 된다. 최악의 경우 기본 서식으로 나간다.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import library                                      # noqa: E402
from schema import OUTPUT_SCHEMA, sample_payload    # noqa: E402

HERE = Path(__file__).resolve().parent
OUT_DIR = HERE / "out"
SCHEMA_ID = OUTPUT_SCHEMA["schema_id"]


def render_markdown_fields(payload: dict) -> dict:
    """_md 로 끝나는 필드만 마크다운으로 해석한다. 나머지는 값 그대로."""
    try:
        import markdown as md
    except ImportError:
        return payload
    out = dict(payload)
    for name, spec in OUTPUT_SCHEMA["fields"].items():
        if spec.get("format") == "markdown" and isinstance(out.get(name), str):
            out[name] = md.markdown(out[name], extensions=["tables"])
    return out


def preflight(tpl: library.Template, payload: dict) -> list[str]:
    """① 사전검사 — 템플릿이 요구하는 것과 JSON 이 가진 것을 맞춰 본다."""
    warnings: list[str] = []
    used, have = tpl.fields_used(), set(payload)

    for name in sorted(used - have):
        required = OUTPUT_SCHEMA["fields"].get(name, {}).get("required")
        warnings.append(f"템플릿이 쓰는 {name} 가 결과에 없다"
                        + (" (필수 필드!)" if required else " (선택 필드 — {% if %} 로 처리되어야 함)"))

    for name in sorted(have - used):
        if OUTPUT_SCHEMA["fields"].get(name, {}).get("required"):
            warnings.append(f"결과의 {name} 를 템플릿이 쓰지 않는다 — 내용이 버려진다")

    for name, spec in OUTPUT_SCHEMA["fields"].items():
        val = payload.get(name)
        if spec["type"] == "array" and isinstance(val, list):
            lo, hi = spec.get("min_items", 0), spec.get("max_items", 99)
            if not lo <= len(val) <= hi:
                warnings.append(f"{name} 가 {len(val)}개다 (스키마는 {lo}~{hi}개) — 레이아웃이 넘칠 수 있다")
        if spec["type"] == "string" and isinstance(val, str) and len(val) > 300:
            warnings.append(f"{name} 가 {len(val)}자로 길다 — 레이아웃이 넘칠 수 있다")
    return warnings


def render(html: str, payload: dict) -> str:
    """② StrictUndefined — 없는 필드는 조용히 넘어가지 않고 예외를 낸다."""
    from jinja2 import Environment, StrictUndefined
    return Environment(autoescape=True, undefined=StrictUndefined) \
        .from_string(html).render(**render_markdown_fields(payload))


def to_pdf(html_path: Path, pdf_path: Path) -> bool:
    try:
        from playwright.sync_api import sync_playwright
    except ImportError:
        print("  (Playwright 가 없어 PDF 는 건너뜁니다. HTML 을 브라우저에서 Ctrl+P 로 확인하세요)")
        return False
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page()
        page.goto(html_path.resolve().as_uri())
        page.pdf(path=str(pdf_path), format="A4", print_background=True,
                 margin={"top": "0", "bottom": "0", "left": "0", "right": "0"})
        browser.close()
    return True


def pick(arg: str | None) -> library.Template | None:
    """실행 화면의 '서식 고르기'. 목록은 스키마로 걸러져 있다."""
    rows = library.list_templates(SCHEMA_ID)
    if not rows:
        return None
    if not arg:
        return rows[0]
    if arg.isdigit() and 1 <= int(arg) <= len(rows):
        return rows[int(arg) - 1]
    return library.get(arg)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--list", action="store_true", help="고를 수 있는 서식 목록")
    ap.add_argument("--template", default=None, help="목록 번호 또는 template_id")
    ap.add_argument("--payload", default=str(OUT_DIR / "payload.json"))
    ap.add_argument("--sample", action="store_true", help="더미 데이터로 서식만 확인")
    ap.add_argument("--pdf", action="store_true")
    ap.add_argument("--no-fallback", action="store_true", help="폴백 없이 실패를 그대로 본다")
    args = ap.parse_args()

    if args.list:
        print(f"'{OUTPUT_SCHEMA['title']}'({SCHEMA_ID}) 로 쓸 수 있는 서식\n")
        library.print_table(SCHEMA_ID)
        return 0

    tpl = pick(args.template)
    if tpl is None:
        print(f"{SCHEMA_ID} 용 템플릿이 보관함에 없습니다.\n"
              "  먼저: uv run --with jinja2 python poc/step1_template.py --name '내 양식'")
        return 1

    if args.sample:
        payload, label = sample_payload(), "샘플 데이터"
    else:
        pay_path = Path(args.payload)
        if not pay_path.exists():
            print(f"결과 JSON 이 없습니다: {pay_path}\n  먼저: uv run python poc/step2_node.py")
            return 1
        payload, label = json.loads(pay_path.read_text(encoding="utf-8")), pay_path.name

    print(f"서식     {tpl.name}  ({tpl.template_id} · {'플랫폼' if tpl.owner == 'platform' else tpl.owner})")
    print(f"데이터   {label}")

    for w in preflight(tpl, payload):
        print(f"  ⚠ {w}")

    missing_required = [n for n, s in OUTPUT_SCHEMA["fields"].items()
                        if s.get("required") and n not in payload]

    OUT_DIR.mkdir(exist_ok=True)
    used_fallback = False
    start = time.perf_counter()
    try:
        html = render(tpl.html(), payload)
    except Exception as e:
        print(f"\n렌더 실패: {type(e).__name__}: {e}")

        # 필수 필드가 없으면 어떤 서식으로 바꿔도 못 살린다. 폴백은 "서식이 이상할 때"의 대비다.
        if missing_required:
            print(f"원인은 서식이 아니라 데이터다. 필수 필드 누락: {missing_required}")
            print("이 경우는 노드 출력 검증(step2)에서 OUTPUT_SCHEMA_INVALID 로 먼저 걸린다."
                  " 렌더까지 오면 안 되는 상태다.")
            return 1

        fb = library.default_for(SCHEMA_ID)
        if args.no_fallback or fb is None or fb.template_id == tpl.template_id:
            print("폴백 없음 — 실행을 실패시키지 않으려면 기본 서식이 보관함에 있어야 합니다.")
            return 1
        print(f"→ 기본 서식 '{fb.name}' 으로 폴백합니다. (실행은 성공으로 유지)")
        try:
            html, tpl, used_fallback = render(fb.html(), payload), fb, True
        except Exception as e2:
            print(f"기본 서식도 실패: {type(e2).__name__}: {e2}")
            print("RENDER_FAILED — 플랫폼 책임. 노드 정산은 유지하고 결과물만 다시 만든다.")
            return 1
    elapsed = (time.perf_counter() - start) * 1000

    out_html = OUT_DIR / "result.html"
    out_html.write_text(html, encoding="utf-8")
    print(f"렌더     {elapsed:.0f}ms · 0원 · LLM 호출 없음" + ("  (폴백됨)" if used_fallback else ""))
    print(f"저장     {out_html}")

    leftover = re.findall(r"\{\{[^}]*\}\}|\{%[^%]*%\}", html)
    if leftover:
        print(f"경고     치환되지 않은 자리표시자 {len(leftover)}개: {leftover[:3]}")

    if args.pdf and to_pdf(out_html, OUT_DIR / "result.pdf"):
        print(f"PDF      {OUT_DIR / 'result.pdf'}")

    print(f"\n브라우저로 열어 확인하세요:\n  start {out_html}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
