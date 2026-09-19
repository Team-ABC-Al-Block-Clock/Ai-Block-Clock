"""1단계 — 디자인(PDF·이미지)을 HTML 템플릿으로 바꿔 보관함에 등록한다.

※ MVP 마지막 항목(09-22 ~ 23). 지금은 실행하지 않는다 — Claude 비전을 2~3회 반복 호출해 GMS 토큰을 많이 쓴다.
   이식할 때 --model 을 gemini 계열로 바꾸고 gms.gemini 에 이미지 입력을 추가한다. 09-17 fork 기록에서 복구한 파일.

**실행 시점이 아니다.** 올리는 그 순간 한 번만 돈다. 노드가 아니므로 과금·정산 대상이 아니다.

    uv run --with jinja2 python poc/step1_template.py --list
    uv run --with jinja2 python poc/step1_template.py --design poc/template/sample_template.pdf --name "파란 모던"
    uv run --with jinja2 python poc/step1_template.py --design 내양식.png --rounds 3 --model claude-opus-4-6

## 어떻게 원본을 닮게 만드나 — 보고 고친다

  0) 원본을 PNG 로 렌더한다 (PDF 는 텍스트로만 읽히면 레이아웃을 짐작하게 된다)
  1) 원본에 적힌 내용을 스키마 JSON 으로 추출한다 → 이게 비교용 샘플이 된다
  2) 이미지를 보고 HTML 을 만든다
  3) 만든 HTML 을 실제로 렌더해 스크린샷을 찍는다
  4) [원본, 내 결과] 두 장을 나란히 주고 "차이를 고쳐라" → 2~3회 반복
  5) 관문 통과분만 보관함에 등록

1번이 중요하다. 원본과 같은 내용으로 렌더해야 픽셀 단위 비교가 성립한다.
다른 내용으로 그리면 "글자가 달라서 다른 건지 레이아웃이 틀린 건지" 구분이 안 된다.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gms                      # noqa: E402
import library                  # noqa: E402
from schema import OUTPUT_SCHEMA, describe, sample_payload   # noqa: E402

HERE = Path(__file__).resolve().parent
OUT = HERE / "out"
FORBIDDEN = re.compile(r"<script|<iframe|<object|<embed|javascript:|@import|url\(\s*https?:", re.I)

EXTRACT = """첨부한 이미지는 어떤 보고서 양식이다. **이 이미지에 실제로 적혀 있는 글자**를
아래 스키마에 맞춰 JSON 으로 옮겨라. 새로 지어내지 말고, 안 보이는 필드는 짧게 채워라.

```json
{schema}
```

JSON 하나만 출력한다."""

BUILD = """첨부한 이미지는 판매자가 쓰던 보고서 양식이다. **이 이미지와 똑같아 보이는**
Jinja2 HTML 템플릿을 만들어라. 목표는 픽셀 단위로 닮게 만드는 것이다.

## 최우선 — 시각적 재현

이미지를 위에서 아래로 훑으며 **모든 시각 요소**를 옮겨라. 빠뜨리면 실패다.

- 색: 배경색, 글자색, 테두리색을 이미지에서 직접 읽어 그대로 쓴다
- 도형: 대각선 띠, 색 블록, 화살표, 테두리 박스, 구분선 — 전부 재현한다
  대각선은 `clip-path: polygon(...)` 또는 `transform: skewX()` 로,
  화살표는 인라인 SVG 나 CSS 삼각형으로 그린다
- 크기 대비: 큰 글자와 작은 글자의 배율을 이미지에서 재서 맞춘다
- 위치: 좌우 정렬, 가운데 정렬, 여백 비율을 맞춘다
- 빈 공간도 디자인이다. 원본이 여백을 넓게 쓰면 그대로 넓게 쓴다

## 변수 규칙

1. **변수는 아래 스키마의 필드만 쓴다.** 새 이름을 지어내지 마라.
```json
{schema}
```

2. **고정 문구는 그대로 둔다.** 이미지의 `Brand Purpose`, `핵심 전략`, `2026 VISION`,
   `2026 Vision & Branding Strategy` 같은 제목·라벨은 디자인의 일부다. 변수로 바꾸지 마라.
   **내용**(항목 문장, 메시지, 날짜, 기관명)만 변수로 바꾼다.

3. 배열은 `{{% for %}}` 로 감싼다. 원본에 3개가 그려져 있어도 개수는 달라질 수 있다.
   **`reasons[3]` 같은 인덱스 접근은 절대 금지.** 개수가 다르면 그 자리에서 터진다.
   원본이 2×2 배치라면 `{{% for %}}` + CSS Grid 로 개수에 무관하게 채워라.

4. required 가 false 인 필드는 `{{% if 필드 %}}` 로 감싼다.

5. `_md` 로 끝나는 필드는 `{{{{ 필드 | safe }}}}` 로 출력한다.

## 안 깨지게

- **A4 한 장**: `@page {{ size: A4; margin: 0 }}`, 컨테이너 `width:210mm; height:297mm; overflow:hidden`
- 글자는 원본보다 길 수 있다. 고정 높이 영역에 `overflow:hidden`, 긴 문장에 넉넉한 `line-height`
- 한글 폰트: `font-family: 'Malgun Gothic','Nanum Gothic','Noto Sans KR',sans-serif`
- 금지: `<script>`, `<iframe>`, 외부 URL, 이미지 파일 참조. 도형은 CSS·인라인 SVG 로 그린다

설명 없이 ```html 코드블록 하나만. `<!DOCTYPE html>` 부터 `</html>` 까지."""

COMPARE = """두 장을 첨부했다.

- **첫 번째 = 목표(원본 디자인)**
- **두 번째 = 네가 만든 HTML 을 실제로 렌더한 결과**

둘 다 같은 내용으로 채워져 있다. 그러니 **차이는 전부 레이아웃·색·도형의 문제**다.

두 이미지를 위에서 아래로 훑으며 다른 점을 찾아 고쳐라. 특히 이런 것들:
- 원본에 있는데 내 결과에 없는 도형·띠·화살표·색 블록
- 글자 크기 비율이 다른 곳 (원본의 큰 제목이 작아졌다든지)
- 영역의 위치·너비·높이 비율
- 배경색·글자색이 다른 곳
- 원본에 있는 고정 문구가 빠졌거나, 반대로 내용이 고정 문구 자리를 차지한 곳
- 내용이 넘쳐서 잘리거나 겹친 곳

{extra}

전체 HTML 을 다시 출력한다. 설명 없이 ```html 코드블록 하나만."""


def validate(html: str) -> list[str]:
    errors: list[str] = []
    fields = OUTPUT_SCHEMA["fields"]

    if not html.lstrip().lower().startswith("<!doctype"):
        errors.append("- <!DOCTYPE html> 로 시작하지 않는다.")
    hit = FORBIDDEN.search(html)
    if hit:
        errors.append(f"- 금지된 요소가 있다: {hit.group()!r}. 스크립트·외부 리소스를 제거하라.")

    loop_vars = set(re.findall(r"\{%\s*for\s+(\w+)\s+in", html))
    used = {m.split(".")[0].split("[")[0]
            for m in re.findall(r"\{\{\s*([A-Za-z_][\w.\[\]]*)", html)}
    used |= set(re.findall(r"\{%\s*for\s+\w+\s+in\s+([A-Za-z_]\w*)", html))
    JINJA_BUILTINS = {"loop", "range", "namespace", "dict", "lipsum", "cycler", "joiner"}
    unknown = used - set(fields) - loop_vars - JINJA_BUILTINS
    if unknown:
        errors.append(f"- 스키마에 없는 변수를 쓴다: {sorted(unknown)}. 쓸 수 있는 필드: {sorted(fields)}")

    for name, spec in fields.items():
        if spec["type"] == "array" and name in used and f"in {name}" not in html:
            errors.append(f"- 배열 {name} 을 {{% for %}} 로 돌지 않는다.")
        if not spec.get("required") and name in used and \
                not re.search(r"\{%\s*if\s+" + name + r"\s*%\}", html):
            errors.append(f"- 선택 필드 {name} 을 {{% if {name} %}} 로 감싸지 않았다.")

    try:
        render(html, sample_payload())
    except Exception as e:
        errors.append(f"- 샘플 데이터로 렌더할 때 오류: {type(e).__name__}: {e}")
    return errors


def render(html: str, payload: dict) -> str:
    from jinja2 import Environment, StrictUndefined
    return Environment(autoescape=True, undefined=StrictUndefined).from_string(html).render(**payload)


def extract_sample(png: Path, model: str) -> tuple[dict, float]:
    """원본에 적힌 내용을 스키마 JSON 으로. 비교용 샘플이 된다."""
    text, usage = gms.claude(
        [gms.image_block(png), {"type": "text", "text": EXTRACT.format(schema=describe(OUTPUT_SCHEMA))}],
        model=model, max_tokens=4000)
    raw = gms.extract_block(text, "json")
    data = json.loads(raw[raw.find("{"):raw.rfind("}") + 1])
    if "fields" in data and isinstance(data["fields"], dict):
        data = data["fields"]        # 스키마 껍데기를 씌워 주는 경우가 있다
    (OUT / "_extracted.json").write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    base = sample_payload()
    taken = {k: v for k, v in data.items() if v and k in base}
    base.update(taken)
    print(f"    추출된 필드 {len(taken)}/{len(base)}개")
    return base, gms.cost_krw(usage, model)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--design", default=str(HERE / "template" / "sample_template.pdf"))
    ap.add_argument("--name", default="")
    ap.add_argument("--owner", default="u_1001")
    ap.add_argument("--model", default="claude-sonnet-4-6")
    ap.add_argument("--rounds", type=int, default=2, help="원본과 비교하며 고치는 횟수")
    ap.add_argument("--keep-shots", action="store_true", help="라운드별 스크린샷을 남긴다")
    args = ap.parse_args()

    if args.list:
        library.print_table()
        return 0

    design = Path(args.design)
    if not design.exists():
        print(f"양식 파일이 없습니다: {design}")
        return 1
    OUT.mkdir(exist_ok=True)

    print(f"양식      {design.name} ({design.stat().st_size:,} bytes)")
    print(f"스키마    {OUTPUT_SCHEMA['schema_id']} · 모델 {args.model} · 교정 {args.rounds}회\n")

    origin = gms.design_to_png(design, OUT / "original.png")
    print(f"[0] 원본을 이미지로 렌더 → {origin.name}")

    print("[1] 원본 내용 추출 …", flush=True)
    start = time.perf_counter()
    try:
        sample, krw = extract_sample(origin, args.model)
        print(f"    {time.perf_counter() - start:.0f}초 · 약 {krw:.0f}원 · "
              f"예: {str(sample.get('headline', ''))[:30]!r}")
    except Exception as e:
        print(f"    추출 실패({e}) — 기본 샘플로 진행합니다.")
        sample, krw = sample_payload(), 0.0
    total = krw

    blocks = [gms.image_block(origin),
              {"type": "text", "text": BUILD.format(schema=describe(OUTPUT_SCHEMA))}]
    html = ""
    last_good = ""

    for rnd in range(args.rounds + 1):
        label = "생성" if rnd == 0 else f"비교 교정 {rnd}"
        print(f"[{2 + rnd}] {label} …", flush=True)
        start = time.perf_counter()
        text, usage = gms.claude(blocks, model=args.model)
        krw = gms.cost_krw(usage, args.model)
        total += krw
        print(f"    {time.perf_counter() - start:.0f}초 · "
              f"in {usage.get('input_tokens', 0):,} / out {usage.get('output_tokens', 0):,} · 약 {krw:.0f}원")

        html = gms.extract_block(text, "html")
        errors = validate(html)
        if errors:
            print("    관문 실패:")
            for e in errors:
                print("     ", e)
            blocks = blocks + [
                {"type": "text", "text": "(직전 응답)\n```html\n" + html + "\n```"},
                {"type": "text", "text": "아래 문제를 고쳐 전체 HTML 을 다시 출력하라.\n"
                                         + "\n".join(errors) + "\n\n```html 코드블록 하나만."}]
            continue

        last_good = html
        if rnd == args.rounds:
            break

        # 원본과 같은 내용으로 렌더해 스크린샷 → 나란히 비교시킨다
        preview = OUT / "_preview.html"
        preview.write_text(render(html, sample), encoding="utf-8")
        shot = gms.screenshot_html(preview, OUT / f"round{rnd}.png")
        if shot is None:
            print("    (Playwright 가 없어 비교 교정을 건너뜁니다. "
                  "uv run --with playwright python -m playwright install chromium)")
            break
        print(f"    렌더 → {shot.name} · 원본과 비교시킵니다")

        leftover = re.findall(r"\{\{[^}]*\}\}", render(html, sample))
        extra = f"- 치환되지 않은 자리표시자가 남아 있다: {leftover[:3]}" if leftover else ""
        blocks = [gms.image_block(origin), gms.image_block(shot),
                  {"type": "text", "text": "(네가 만든 HTML)\n```html\n" + html + "\n```"},
                  {"type": "text", "text": COMPARE.format(extra=extra)}]

    if validate(html) and last_good:
        print("\n마지막 교정이 관문에 걸려 직전 통과본을 씁니다.")
        html = last_good
    errors = validate(html)
    if errors:
        print("\n관문을 통과하지 못했습니다. 보관함에 넣지 않습니다.")
        (OUT / "template_failed.html").write_text(html, encoding="utf-8")
        return 1

    tpl = library.register(html, name=args.name or design.stem,
                           schema_id=OUTPUT_SCHEMA["schema_id"],
                           owner=args.owner, source=design.name)
    (OUT / f"{tpl.template_id}_sample.json").write_text(
        json.dumps(sample, ensure_ascii=False, indent=2), encoding="utf-8")
    if not args.keep_shots:
        for f in OUT.glob("round*.png"):
            f.unlink(missing_ok=True)
        (OUT / "_preview.html").unlink(missing_ok=True)

    print(f"\n등록 완료: {tpl.template_id} ({len(html):,}자) · 누적 약 {total:.0f}원\n")
    library.print_table(OUTPUT_SCHEMA["schema_id"])
    print("\n확인:")
    print(f"  uv run --with jinja2 python poc/step3_render.py --template {tpl.template_id} "
          f"--payload poc/out/{tpl.template_id}_sample.json")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
