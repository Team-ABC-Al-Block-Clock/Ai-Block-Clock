"""결과 JSON → 서식(HTML). 계약 v2.2 §5.5 · §7.2.

산출물은 셋이다 — 결과 JSON(원본) · 서식을 입힌 HTML · 그 HTML에서 만든 PDF.
서식은 백엔드가 보관하고 실행 요청의 `output.template`에 HTML로 담아 보낸다(Runtime은 stateless).
없으면 플랫폼 기본 서식(`templates/document.html` · `card.html`)을 쓴다.

안전망 3중 (PoC `poc/step3_render.py` 이식)
  ① 사전 검사  서식의 자리표시자와 결과 JSON 의 키를 대조. 빠진 것이 있으면 AI 가 **한 번** 경로를 짝짓는다(값 배치일 뿐 판정이 아니다).
              반복 항목의 속성(`{{ 항목.title }}`)도 항목 키로 짝짓는다. 못 채운 빈칸은 비워 두고 경고한다
  ② StrictUndefined  없는 키를 참조하면 조용히 빈칸이 되는 대신 즉시 예외
  ③ 폴백      그래도 실패하면 기본 서식으로. 렌더 실패는 실행 실패가 아니다 — 노드는 성공했고 정산도 끝났다

서식 규칙 — Jinja2. `{{ 키 }}` 로 결과 JSON 의 키를, `{% for x in 목록 %}` 로 배열을 쓴다. `<script>` · 외부 리소스는 금지.
기본 제공 변수: `title` · `date` · `fields`(키 · 값 HTML 목록) · `text`(글 출력의 본문 HTML).
"""
from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any

import markdown as _markdown
from jinja2 import Environment, StrictUndefined, TemplateError
from markupsafe import Markup, escape

TEMPLATES = Path(__file__).resolve().parents[1] / "templates"
DEFAULTS = {"document": "document.html", "card": "card.html"}
FORBIDDEN = re.compile(r"<\s*(script|iframe|object|embed)\b|\bjavascript:|\bon\w+\s*=|\bsrc\s*=\s*[\"']?https?://", re.I)
BUILTINS = {"title", "date", "fields", "text", "loop", "range", "namespace", "dict", "lipsum", "cycler", "joiner"}
MAX_TEMPLATE_CHARS = 200_000


@dataclass
class Rendered:
    html: str
    applied: str                        # "user" | "default"
    template_name: str
    fallback_reason: str | None = None  # 사용자 서식을 못 써서 기본으로 간 이유
    mapping: dict[str, str] = field(default_factory=dict)   # AI 가 짝지은 자리표시자 → 키
    warnings: list[str] = field(default_factory=list)


class TemplateRejected(ValueError):
    """서식 자체가 쓸 수 없다 — 금지 요소 · 너무 큼 · Jinja 문법 오류."""


# --- 값 → HTML ---------------------------------------------------------------

def md(text: Any) -> Markup:
    """마크다운 → HTML. `_md` 키의 값과 글 출력에 쓴다."""
    return Markup(_markdown.markdown(str(text or ""), extensions=["tables", "fenced_code", "sane_lists"]))


def fmt(value: Any) -> Markup:
    """아무 값이나 읽을 수 있는 HTML 로. 서식에서 `{{ 값 | fmt }}` 로 쓴다."""
    if value is None:
        return Markup("")
    if isinstance(value, dict):
        rows = "".join(f"<li><b>{escape(k)}</b>: {fmt(v)}</li>" for k, v in value.items() if v is not None)
        return Markup(f"<ul>{rows}</ul>")
    if isinstance(value, list):
        return Markup("<ul>" + "".join(f"<li>{fmt(v)}</li>" for v in value) + "</ul>")
    if isinstance(value, bool):
        return Markup("예" if value else "아니오")
    return Markup(escape(str(value)))


def prepare(payload: dict, title: str | None) -> dict[str, Any]:
    """서식에 넘길 변수. 결과 JSON 의 키 그대로 + 기본 변수(title · date · fields · text)."""
    ctx: dict[str, Any] = dict(payload)
    ctx["title"] = title or next((str(v) for k, v in payload.items() if isinstance(v, str) and len(v) < 60), "결과")
    ctx["date"] = datetime.now().strftime("%Y-%m-%d")
    if set(payload) == {"text"}:                          # 글 출력
        ctx["text"] = md(payload["text"])
        ctx["fields"] = []
    else:
        ctx["text"] = Markup("")
        ctx["fields"] = [{"key": str(k), "html": md(v) if str(k).endswith("_md") and isinstance(v, str) else fmt(v)}
                         for k, v in payload.items() if v is not None]
    return ctx


# --- 서식 -------------------------------------------------------------------

def placeholders(html: str) -> set[str]:
    """서식이 쓰는 최상위 변수 이름. `{{ a.b }}` · `{{ a[0] }}` · `{% for x in a %}` 의 a."""
    used = {m.split(".")[0].split("[")[0].split("|")[0].strip()
            for m in re.findall(r"\{\{\s*([^\s}|]+)", html)}
    used |= set(re.findall(r"\{%\s*for\s+[\w, ]+\s+in\s+([A-Za-z_가-힣][\w가-힣]*)", html))
    used |= set(re.findall(r"\{%\s*if\s+([A-Za-z_가-힣][\w가-힣]*)", html))
    loop_vars = set(re.findall(r"\{%\s*for\s+([\w가-힣]+)\s+in", html))
    return {u for u in used if u and u not in BUILTINS and u not in loop_vars and not u.startswith("'") and not u.startswith('"')}


def check_template(html: str) -> None:
    if not isinstance(html, str) or not html.strip():
        raise TemplateRejected("서식 HTML 이 비어 있습니다")
    if len(html) > MAX_TEMPLATE_CHARS:
        raise TemplateRejected(f"서식이 너무 큽니다({len(html):,}자 > {MAX_TEMPLATE_CHARS:,})")
    hit = FORBIDDEN.search(html)
    if hit:
        raise TemplateRejected(f"서식에 허용하지 않는 요소가 있습니다: {hit.group()[:40]!r}")
    try:
        _env().parse(html)
    except TemplateError as e:
        raise TemplateRejected(f"서식 문법 오류: {e}") from e


def _env() -> Environment:
    env = Environment(autoescape=True, undefined=StrictUndefined)
    env.filters["md"] = md
    env.filters["fmt"] = fmt
    return env


def render_html(html: str, ctx: dict[str, Any]) -> str:
    return _env().from_string(html).render(**ctx)


def default_html(name: str = "document") -> str:
    return (TEMPLATES / DEFAULTS.get(name, DEFAULTS["document"])).read_text(encoding="utf-8")


# --- AI 짝짓기 (판정이 아니라 배치) -------------------------------------------

_MAP_SYSTEM = ("문서 서식의 빈칸(자리표시자)에 결과 데이터의 어느 값을 넣을지 짝짓는다. "
               "판단하거나 새 내용을 만들지 않는다 — 있는 값을 자리에 배치만 한다. JSON 객체 하나만 출력한다.")


def loop_attrs(html: str) -> dict[str, set[str]]:
    """`{% for 항목 in 목록 %} … {{ 항목.title }}` → {"목록": {"title", …}}. 반복 항목 안에서 쓰는 속성 이름."""
    out: dict[str, set[str]] = {}
    for var, lst in re.findall(r"\{%\s*for\s+([\w\uac00-\ud7a3]+)\s+in\s+([A-Za-z_\uac00-\ud7a3][\w\uac00-\ud7a3]*)", html):
        attrs = set(re.findall(r"\{\{\s*" + re.escape(var) + r"\.([\w\uac00-\ud7a3]+)", html))
        out.setdefault(lst, set()).update(attrs)
    return out


def describe_paths(payload: dict, limit: int = 80) -> dict[str, str]:
    """결과 JSON 의 값 경로와 앞부분 — AI 가 고를 후보. `a.b` · `목록[]: {키들}` · `목록[]: string`."""
    out: dict[str, str] = {}

    def walk(v: Any, path: str) -> None:
        if len(out) >= limit:
            return
        if isinstance(v, dict):
            for k, sub in v.items():
                walk(sub, f"{path}.{k}" if path else str(k))
        elif isinstance(v, list):
            if v and isinstance(v[0], dict):
                out[path + "[]"] = "목록 " + str(len(v)) + "개, 항목 키: " + ", ".join(map(str, v[0].keys()))
            else:
                out[path + "[]"] = "목록 " + str(len(v)) + "개: " + "; ".join(str(x)[:40] for x in v[:3])
        elif v is not None:
            out[path] = str(v)[:100]

    walk(payload, "")
    return out


def _get(payload: dict, path: str) -> Any:
    cur: Any = payload
    for key in [k for k in path.replace("[]", "").split(".") if k]:
        if isinstance(cur, dict) and key in cur:
            cur = cur[key]
        elif isinstance(cur, list) and key.isdigit() and int(key) < len(cur):
            cur = cur[int(key)]
        else:
            return None
    return cur


def ai_mapping(llm, missing: set[str], item_slots: set[str], payload: dict) -> dict[str, str]:
    """빠진 빈칸마다 결과 JSON 의 어느 **경로**를 넣을지 AI 에게 한 번 묻는다. 답은 `{빈칸: 경로 | null}`.

    값이 아니라 경로만 받는다 — 내용은 결과 JSON 에서 그대로 가져오므로 AI 가 지어낼 수 없다.
    `목록[].속성` 같은 반복 항목 슬롯은 항목 안의 키 이름(문자열 목록이면 ".")으로 답한다. `date` · `title` 도 고를 수 있다.
    """
    asks = sorted(missing | item_slots)
    user = ("## 서식의 빈칸(자리표시자)\n" + json.dumps(asks, ensure_ascii=False)
            + "\n\n## 결과 데이터의 경로와 값 앞부분\n" + json.dumps(describe_paths(payload), ensure_ascii=False, indent=1)
            + "\n\n각 빈칸에 가장 맞는 **경로**를 고른다(예: \"messages.core\", \"positioning[]\"). "
              "`목록[].속성` 형태의 빈칸은 그 목록 항목 안의 키 이름만 답한다(항목이 문자열이면 \".\"). "
              "서식에 같은 종류의 고정 칸이 여러 개면(예: 포지셔닝_고객 · 포지셔닝_제품) 목록의 원소를 순서대로 배치한다 — "
              "\"positioning.0\", \"positioning.1\", 항목이 객체면 \"reasons.0.axis\"처럼 경로를 끝까지 적는다. 목록 전체(`positioning[]`)는 반복문 빈칸에만 준다. "
              "오늘 날짜는 \"date\", 문서 제목은 \"title\". 맞는 것이 없으면 null. "
              "출력 예: {\"헤드라인\": \"headline\", \"메시지_핵심\": \"messages.core\", \"목적목록[].title\": \"label\", \"발행일\": \"date\"}")
    schema = {"type": "object", "properties": {m: {"type": "string", "nullable": True} for m in asks},
              "required": asks}
    res = llm.generate(system=_MAP_SYSTEM, user=user, label="template-map", response_schema=schema)
    text = res.text or ""
    start, end = text.find("{"), text.rfind("}")
    try:
        answer = json.loads(text[start:end + 1]) if start >= 0 else {}
    except json.JSONDecodeError:
        answer = {}
    return {ph: path for ph, path in answer.items() if isinstance(path, str) and path.strip() and ph in asks}


def _apply_mapping(ctx: dict[str, Any], payload: dict, missing: set[str], loops: dict[str, set[str]],
                   mapping: dict[str, str]) -> tuple[list[str], list[str]]:
    """짝짓기 결과를 ctx 에 반영한다. 돌려주는 것: (채운 빈칸 설명, 못 채운 빈칸)."""
    filled: list[str] = []
    for ph in sorted(missing):
        path = mapping.get(ph)
        value = None
        if path in ("date", "title"):
            value = ctx[path]
        elif path:
            value = _get(payload, path)
        if value is None:
            ctx[ph] = [] if ph in loops else ""
            mapping.pop(ph, None)                  # 경로가 결과에 없었다 — 빈칸으로 세고 경고에 올린다
            continue
        # 반복 항목의 속성 별칭 — {{ 항목.title }} 이 결과 항목의 label 을 가리키게
        if ph in loops and isinstance(value, list):
            aliased = []
            for item in value:
                base = dict(item) if isinstance(item, dict) else {}
                for attr in loops[ph]:
                    key = mapping.get(f"{ph}[].{attr}")
                    if key == "." or (not isinstance(item, dict) and key is None):
                        base[attr] = item
                    elif isinstance(item, dict):
                        base[attr] = item.get(key) if key else (item.get(attr) if attr in item else "")
                    else:
                        base[attr] = item
                aliased.append(base)
            value = aliased
        ctx[ph] = value
        filled.append(f"{ph}←{path}")
    empty = [ph for ph in sorted(missing) if not mapping.get(ph)]
    return filled, empty


# --- 적용 -------------------------------------------------------------------

def apply(template: dict | None, payload: dict, *, title: str | None = None, llm=None) -> Rendered:
    """서식을 입힌다. 사용자 서식이 있으면 시도하고, 못 쓰면 기본 서식으로 폴백한다. 예외를 던지지 않는다."""
    ctx = prepare(payload, title)
    template = template or {}
    user_html = template.get("html")
    default_name = template.get("default") or "document"

    if user_html:
        name = template.get("name") or "사용자 서식"
        try:
            check_template(user_html)
            loops = loop_attrs(user_html)
            missing = placeholders(user_html) - set(ctx)
            item_slots = {f"{lst}[].{a}" for lst, attrs in loops.items() for a in attrs
                          if lst in missing or lst in payload}
            mapping: dict[str, str] = {}
            warnings: list[str] = []
            if missing or item_slots:
                if llm is not None and not getattr(llm, "mock", False):
                    mapping = ai_mapping(llm, missing, item_slots, payload)
                elif llm is not None:                                   # mock — 키를 순서대로 배치한다
                    keys = list(payload)
                    mapping = {ph: keys[i % len(keys)] for i, ph in enumerate(sorted(missing))} if keys else {}
                filled, empty = _apply_mapping(ctx, payload, missing, loops, mapping)
                # 결과에 이미 있는 목록에 서식이 다른 속성 이름을 쓰면 별칭만 붙인다
                for lst in loops:
                    if lst in payload and lst not in missing and isinstance(ctx.get(lst), list):
                        _apply_mapping(ctx, payload, {lst}, loops, {**mapping, lst: lst})
                if filled:
                    warnings.append("서식 빈칸 " + ", ".join(filled) + " 을(를) 짝지어 채웠습니다")
                if empty:
                    warnings.append("서식 빈칸 " + ", ".join(empty) + " 에 넣을 값이 없어 비워 두었습니다")
            html = render_html(user_html, ctx)
            return Rendered(html=html, applied="user", template_name=name, mapping=mapping, warnings=warnings)
        except (TemplateRejected, TemplateError, Exception) as e:      # ③ 폴백
            reason = f"{type(e).__name__}: {str(e)[:200]}"
            r = _default(default_name, ctx)
            r.fallback_reason = reason
            r.warnings.append(f"사용자 서식 '{name}' 을(를) 적용하지 못해 기본 서식으로 만들었습니다 — {reason}")
            return r
    return _default(default_name, ctx)


def _default(name: str, ctx: dict[str, Any]) -> Rendered:
    html = render_html(default_html(name), ctx)
    return Rendered(html=html, applied="default", template_name=name)
