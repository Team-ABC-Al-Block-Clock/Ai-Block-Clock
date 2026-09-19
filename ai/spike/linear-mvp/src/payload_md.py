"""payload(JSON) → 사람이 읽는 마크다운.

템플릿(09-22 ~ 23)이 붙기 전까지 결과 PDF를 만드는 임시 경로다. 카탈로그의 한국어
라벨(`title`)을 제목으로 쓰기 때문에, 템플릿이 붙어도 같은 라벨이 그대로 쓰인다.
"""
from __future__ import annotations

from typing import Any

from pydantic import BaseModel

from . import catalog


def payload_to_markdown(schema_id: str, payload: dict[str, Any]) -> str:
    entry = catalog.get(schema_id)
    lines = [f"# {entry.title}", ""]
    lines += _fields(entry.model, payload, level=2)
    return "\n".join(lines).strip() + "\n"


def _fields(model: type[BaseModel], payload: dict[str, Any], level: int) -> list[str]:
    out: list[str] = []
    for name, field in model.model_fields.items():
        if name not in payload or payload[name] is None:
            continue
        out += _one(field.title or name, payload[name], level)
    return out


def _one(label: str, value: Any, level: int) -> list[str]:
    if isinstance(value, dict):
        return [f"{'#' * level} {label}", ""] + [f"- **{k}**: {v}" for k, v in value.items()] + [""]
    if isinstance(value, list):
        out = [f"{'#' * level} {label}", ""]
        for item in value:
            if isinstance(item, dict):
                out.append("- " + " · ".join(f"{v}" for v in item.values() if v is not None))
            else:
                out.append(f"- {item}")
        return out + [""]
    return [f"{'#' * level} {label}", "", str(value), ""]
