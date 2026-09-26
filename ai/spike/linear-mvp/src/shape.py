"""출력 모양 — 판매자가 노드마다 준 **JSON 예시**로 AI 답을 강제하고 검사한다. 계약 v2.1 §5.5.

09-26 플랫폼 견본(카탈로그 11종)을 없앴다. 판매자는 n8n 의 "Generate from JSON example" 처럼
원하는 출력 JSON 을 하나 적는다. 이 모듈이 그 예시 하나로 다섯 가지를 한다.

    schema_of(example)        예시 → 모양(타입 트리). 모든 키가 필수, 배열은 첫 원소가 원소 모양
    response_schema(example)  Gemini 구조화 출력(`response_schema`)에 넣을 형태
    problems(value, example)  답이 모양을 지켰는가 — 어긋난 곳 목록(최대 5). 판정은 코드
    field_kind(example, path) 분기 조건이 가리키는 필드의 종류 — 저장 시 검사
    mock(example, overrides)  LLM 없이 돌릴 때의 답 = 예시 그대로(+ 값 지정)
    to_markdown(payload)      결과 JSON → PDF 용 문서. 키 이름이 제목이다(한글 키면 한글 제목)

규칙은 일부러 좁다. 타입은 string · number · boolean · array · object · null 여섯 가지이고,
**예시에서 값을 null 로 둔 키는 선택 항목**이다(없어도 되고, 있으면 문자열이어야 한다). 나머지 키는 필수다.
선택지(enum) · 개수 상한은 없다 — 필요하면 프롬프트로 지시한다. 분기의 문자열 비교는 대소문자 ·
앞뒤 공백을 무시한다(graph.evaluate).
"""
from __future__ import annotations

import copy
import json
from pathlib import Path
from typing import Any

MAX_DEPTH = 5                 # 예시의 중첩 깊이 상한
MAX_KEYS = 60                 # 예시 전체 키 수 상한
SCALAR_KINDS = ("string", "number", "boolean")

# 옛 견본 id → 예시. 백엔드가 v2.0 형식(`output.sample` · `output_schema_id` · `input.sample`)을
# 보내도 한 버전 동안 실행되게 한다(경고). 견본 11종을 없애기 직전에 뽑아 둔 값이다.
LEGACY_EXAMPLES: dict[str, dict] = json.loads(
    (Path(__file__).with_name("legacy_examples.json")).read_text(encoding="utf-8"))
LEGACY_TEXT_IDS = {"common.text@1"}          # 이 id 는 글 출력으로 읽는다


class ExampleInvalid(ValueError):
    """예시가 JSON 객체가 아니거나 너무 깊다 · 크다 · 빈 객체를 담았다."""


def check_example(example: Any) -> str | None:
    """예시 자체가 쓸 만한가. 문제가 있으면 사람이 읽는 사유, 없으면 None."""
    if not isinstance(example, dict) or not example:
        return "example 은 키가 하나 이상인 JSON 객체여야 합니다"
    count = 0

    def walk(v: Any, depth: int, where: str) -> str | None:
        nonlocal count
        if depth > MAX_DEPTH:
            return f"{where}: 중첩은 {MAX_DEPTH}단계까지입니다"
        if isinstance(v, dict):
            if not v:
                return f"{where or '(최상위)'}: 빈 객체는 모양을 알 수 없습니다 — 키를 하나 이상 적어 주세요"
            for k, sub in v.items():
                count += 1
                if count > MAX_KEYS:
                    return f"키는 {MAX_KEYS}개까지입니다"
                found = walk(sub, depth + 1, f"{where}.{k}" if where else k)
                if found:
                    return found
        elif isinstance(v, list) and v:
            return walk(v[0], depth + 1, f"{where}[0]")
        return None

    return walk(example, 1, "")


def kind_of(value: Any) -> str:
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "boolean"
    if isinstance(value, (int, float)):
        return "number"
    if isinstance(value, str):
        return "string"
    if isinstance(value, list):
        return "array"
    return "object"


def schema_of(example: Any) -> dict[str, Any]:
    """예시 → 모양. `{"type": ..., "properties"?, "required"?, "items"?}`."""
    kind = kind_of(example)
    if kind == "object":
        return {"type": "object",
                "properties": {k: schema_of(v) for k, v in example.items()},
                "required": [k for k, v in example.items() if v is not None]}   # null = 선택 항목
    if kind == "array":
        return {"type": "array", "items": schema_of(example[0]) if example else {"type": "string"}}
    if kind == "null":
        return {"type": "string", "nullable": True}
    return {"type": kind}


def response_schema(example: dict) -> dict[str, Any]:
    """Gemini 가 받는 OpenAPI 부분집합. 키 순서를 예시대로 고정한다(`propertyOrdering`)."""
    def walk(node: dict) -> dict:
        out = {k: v for k, v in node.items() if k in ("type", "nullable", "required")}
        if "properties" in node:
            out["properties"] = {k: walk(v) for k, v in node["properties"].items()}
            out["propertyOrdering"] = list(node["properties"])
        if "items" in node:
            out["items"] = walk(node["items"])
        return out
    return walk(schema_of(example))


def problems(value: Any, example: Any, limit: int = 5) -> list[str]:
    """답이 예시의 모양을 지켰는가. **없는 키 · 타입이 다른 값**을 찾는다. 남는 키는 허용한다.

    값은 싣지 않는다 — 산출물 · 프롬프트가 오류 문구로 새지 않게(SEC-001).
    """
    found: list[str] = []

    def walk(v: Any, schema: dict, where: str) -> None:
        if len(found) >= limit:
            return
        want = schema["type"]
        if v is None:
            if not schema.get("nullable"):
                found.append(f"{where or '(최상위)'}: 값이 비었습니다 ({want} 이어야 합니다)")
            return
        got = kind_of(v)
        if got != want:
            found.append(f"{where or '(최상위)'}: {want} 이어야 하는데 {got} 입니다")
            return
        if want == "object":
            for key in schema["required"]:
                if key not in v:
                    found.append(f"{where + '.' if where else ''}{key}: 없습니다")
                    if len(found) >= limit:
                        return
            for key, sub in schema["properties"].items():
                if key in v:
                    walk(v[key], sub, f"{where}.{key}" if where else key)
        elif want == "array":
            for i, item in enumerate(v):
                walk(item, schema["items"], f"{where}.{i}")

    walk(value, schema_of(example), "")
    return found


def field_kind(example: Any, path: str) -> str | None:
    """예시에서 점 경로가 가리키는 필드의 종류. 없으면 None. 배열 인덱스(`competitors.0.name`)도 따라간다."""
    node = example
    for key in [p for p in path.split(".") if p]:
        if isinstance(node, dict) and key in node:
            node = node[key]
        elif isinstance(node, list) and key.isdigit():
            if not node:
                return None
            node = node[0]
        else:
            return None
    return kind_of(node) if node is not None else "string"


def mock(example: dict, overrides: dict[str, Any] | None = None) -> dict:
    """LLM 없이 돌릴 때의 답. 예시 그대로에 `{"점.경로": 값}` 을 덮는다 — 분기 양쪽을 돌려 볼 때."""
    out = copy.deepcopy(example)
    for path, value in (overrides or {}).items():
        keys = [p for p in path.split(".") if p]
        cur = out
        for key in keys[:-1]:
            cur = cur[int(key)] if isinstance(cur, list) else cur.setdefault(key, {})
        if keys:
            cur[keys[-1]] = value
    return out


def to_markdown(payload: dict, title: str | None = None) -> str:
    """결과 JSON → 사람이 읽는 문서. 키가 제목이다. 템플릿이 붙기 전의 기본 문서형 렌더."""
    lines = [f"# {title}", ""] if title else []
    for key, value in payload.items():
        if value is None:
            continue                                  # 비어 있는 선택 항목은 문서에 싣지 않는다
        lines += _block(str(key), value, 2)
    return "\n".join(lines).strip() + "\n"


def _block(label: str, value: Any, level: int) -> list[str]:
    head = [f"{'#' * min(level, 6)} {label}", ""]
    if isinstance(value, dict):
        return head + [f"- **{k}**: {_inline(v)}" for k, v in value.items()] + [""]
    if isinstance(value, list):
        return head + [f"- {_inline(v)}" for v in value] + [""]
    return head + [str(value), ""]


def _inline(value: Any) -> str:
    if isinstance(value, dict):
        return " · ".join(f"{k}: {_inline(v)}" for k, v in value.items() if v is not None)
    if isinstance(value, list):
        return ", ".join(_inline(v) for v in value)
    return "" if value is None else str(value)
