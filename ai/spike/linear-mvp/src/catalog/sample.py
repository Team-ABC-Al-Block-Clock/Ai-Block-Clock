"""카탈로그 스키마로부터 유효한 샘플 payload를 만든다.

쓰는 곳 둘.
  1) `--mock` 실행 — LLM 없이도 스키마를 만족하는 payload가 나와야 전 구간이 돈다(CI가 이 경로).
  2) 템플릿 등록 검증 — 09-23에 사용자 템플릿을 이 값으로 한 번 렌더해 본다.

값은 "샘플 <한국어 라벨>" 꼴이라 PDF로 뽑아 보면 어느 칸이 어느 필드인지 바로 보인다.
"""
from __future__ import annotations

import types as _types
from typing import Any, Literal, Union, get_args, get_origin

from pydantic import BaseModel


def sample_payload(model: type[BaseModel]) -> dict[str, Any]:
    """모델을 만족하는 샘플 dict. 선택 필드도 채운다."""
    out: dict[str, Any] = {}
    for name, field in model.model_fields.items():
        label = field.title or name
        out[name] = _value(field.annotation, label, field)
    return out


def _min_items(field: Any) -> int:
    for m in getattr(field, "metadata", []) or []:
        v = getattr(m, "min_length", None)
        if isinstance(v, int) and v > 0:
            return v
    return 1


def _value(ann: Any, label: str, field: Any = None) -> Any:
    origin = get_origin(ann)

    if origin is Union or origin is getattr(_types, "UnionType", None):
        inner = [a for a in get_args(ann) if a is not type(None)]
        return _value(inner[0], label) if inner else None
    if origin is Literal:
        return get_args(ann)[0]
    if origin is list:
        (inner,) = get_args(ann) or (str,)
        n = _min_items(field)
        return [_value(inner, f"{label} {i + 1}") for i in range(n)]
    if origin is dict:
        return {"col_1": "샘플 값"}
    if isinstance(ann, type) and issubclass(ann, BaseModel):
        return sample_payload(ann)
    if ann is str:
        return f"샘플 {label}"
    if ann in (int, float):
        return 1
    if ann is bool:
        return True
    return None
