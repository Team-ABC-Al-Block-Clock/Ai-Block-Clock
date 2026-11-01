"""사용자 디자인(PDF · 사진) → 빈칸 뚫린 HTML 서식. 계약 v2.2 §7.8 `POST /v1/templates/convert`.

캔바 · 미리캔버스처럼 "내 디자인으로 결과물을 받는다"를 위한 변환이다. 올릴 때 **한 번**만 AI 를 부르고,
결과 HTML 은 백엔드가 보관해 실행마다 재사용한다(실행 때는 AI 없이 값만 붓는다 — `src/render.py`).

PoC `poc/step1_template.py`(Claude 비전 반복 호출) 를 Gemini 이미지 · PDF 입력으로 옮겼다. 교정은 1회.
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field

from . import render

_SYSTEM = """당신은 문서 디자인을 HTML 서식으로 옮기는 퍼블리셔다. 첨부된 디자인(PDF 또는 이미지)을 보고,
같은 레이아웃 · 색 · 글자 크기 비율 · 고정 문구를 가진 **Jinja2 HTML 서식** 하나를 만든다.

규칙
- 디자인에서 '내용이 들어갈 자리'는 {{ 자리표시자 }} 로 뚫는다. 자리표시자 이름은 그 자리의 뜻을 담은 한국어 또는 영어 식별자
  (예: {{ 제목 }} {{ 브랜드명 }} {{ headline }}). 공백 · 특수문자 없이, 같은 뜻이면 같은 이름.
- 반복되는 항목(목록 · 카드 여러 장)은 {% for 항목 in 목록이름 %} … {% endfor %} 로 만들고 안에서 {{ 항목 }} 또는 {{ 항목.필드 }} 를 쓴다.
  항목 필드 이름은 그 자리의 뜻(예: 제목 · 설명 · title · detail)으로 짓는다. 실행 때 결과 데이터의 키와 자동으로 짝지어진다.
- 디자인에 고정으로 찍힌 문구(회사명 · 섹션 제목 · 안내문)는 그대로 글자로 둔다. 자리표시자로 만들지 않는다.
- 값이 없을 수도 있는 자리는 {% if 이름 %} … {% endif %} 로 감싼다.
- 전체를 <!DOCTYPE html> 부터 </html> 까지 완결된 문서로. CSS 는 <style> 안에 인라인. A4 한 장 기준(@page { size: A4 }).
- <script> · 외부 URL 리소스 · 이벤트 속성은 절대 쓰지 않는다. 이미지는 넣지 않는다(색 블록 · 선 · 글자로만 재현).
- 글꼴은 font-family: "KR", sans-serif 로 둔다.
- 마지막에 사용한 자리표시자 이름 목록을 HTML 주석으로 적는다: <!-- placeholders: 제목, 브랜드명, 항목목록 -->

출력은 ```html 코드블록 하나만. 설명은 쓰지 않는다."""

_REPAIR = """방금 만든 서식에 문제가 있다. 고쳐서 전체 HTML 을 ```html 코드블록 하나로 다시 출력한다.

문제:
{problems}
"""

_FENCE = re.compile(r"```(?:html)?\s*(.+?)\s*```", re.S)


@dataclass
class Converted:
    html: str
    placeholders: list[str]
    repaired: bool = False
    problems: list[str] = field(default_factory=list)   # 남은 문제(있으면 백엔드가 사용자에게 보여 준다)
    usage: dict = field(default_factory=dict)


def _extract(text: str) -> str:
    m = _FENCE.search(text or "")
    return (m.group(1) if m else text or "").strip()


def _problems(html: str) -> list[str]:
    out: list[str] = []
    if not html.lstrip().lower().startswith("<!doctype"):
        out.append("<!DOCTYPE html> 로 시작하지 않는다")
    try:
        render.check_template(html)
    except render.TemplateRejected as e:
        out.append(str(e))
        return out
    names = render.placeholders(html)
    if not names:
        out.append("자리표시자({{ … }})가 하나도 없다 — 내용이 들어갈 자리를 뚫어야 한다")
    dummy = {n: [{"항목": "예시"}] if re.search(r"\{%\s*for\s+[\w가-힣]+\s+in\s+" + re.escape(n), html) else "예시" for n in names}
    try:
        render.render_html(html, {**render.prepare({"_": "x"}, None), **dummy})
    except Exception as e:
        out.append(f"예시 값으로 렌더할 때 오류: {type(e).__name__}: {str(e)[:120]}")
    return out


def convert(llm, files: list[tuple[bytes, str, str]], hint: str = "") -> Converted:
    """디자인 파일(들) → 서식. AI 1회 + 문제가 있으면 교정 1회."""
    user = "첨부한 디자인을 HTML 서식으로 옮겨 주세요." + (f"\n\n참고: {hint.strip()}" if hint.strip() else "")
    if getattr(llm, "mock", False):
        html = render.default_html("card").replace("{{ title }}", "{{ 제목 }}", 1)
        return Converted(html=html, placeholders=sorted(render.placeholders(html)), usage={"mock": True})

    res = llm.generate(system=_SYSTEM, user=user, label="template-convert", attachments=files)
    html = _extract(res.text)
    usage = {"input_tokens": res.input_tokens, "output_tokens": res.output_tokens, "model": res.model}
    problems = _problems(html)
    repaired = False
    if problems:
        res2 = llm.generate(system=_SYSTEM, user=user + "\n\n" + _REPAIR.format(problems="\n".join("- " + p for p in problems))
                            + "\n\n## 직전 결과\n```html\n" + html[:60_000] + "\n```", label="template-repair", attachments=files)
        html2 = _extract(res2.text)
        usage["input_tokens"] += res2.input_tokens
        usage["output_tokens"] += res2.output_tokens
        p2 = _problems(html2)
        if len(p2) <= len(problems):
            html, problems, repaired = html2, p2, True
    return Converted(html=html, placeholders=sorted(render.placeholders(html)), repaired=repaired,
                     problems=problems, usage=usage)
