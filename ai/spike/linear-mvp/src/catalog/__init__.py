"""스키마 카탈로그 — 플랫폼이 미리 정해 둔 노드 입출력 형식 목록.

판매자는 노드를 만들 때 이 목록에서 입력 형식 · 출력 형식을 고른다(NOD-003 · 004).
카탈로그가 있어서 연결 판정(WRK-005) · 입력 폼 · 엑셀 열 이름 · 조건 분기 ·
실패 책임 판정(EXE-009) · 템플릿 바인딩(EXE-014)이 전부 성립한다.

**원본은 Pydantic 이다.** `ai/schemas/catalog/*.json` 은 여기서 export 한 산출물이고
손으로 고치지 않는다(`ai/CLAUDE.md` §3-3).

    python -m src.catalog.export      # JSON Schema 를 ai/schemas/catalog/ 에 다시 쓴다
"""
from __future__ import annotations

import copy
from dataclasses import dataclass
from typing import Any

from pydantic import BaseModel, ValidationError

from . import biz, common
from .sample import sample_payload

CATALOG_VERSION = "1.0"


@dataclass(frozen=True)
class Entry:
    """카탈로그 한 줄. 프론트 드롭다운에 그대로 쓰인다."""

    schema_id: str
    title: str          # 한국어 이름. 화면에 보이는 것
    category: str       # common | biz
    model: type[BaseModel]


_ENTRIES: tuple[Entry, ...] = (
    Entry("common.text@1", "단순 텍스트", "common", common.Text),
    Entry("common.document@1", "문서", "common", common.Document),
    Entry("common.table@1", "표", "common", common.Table),
    Entry("biz.brand_brief@1", "브랜드 기획 요청서", "biz", biz.BrandBrief),
    Entry("biz.market_research@1", "시장 조사 결과", "biz", biz.MarketResearch),
    Entry("biz.product_analysis@1", "제품 분석 결과", "biz", biz.ProductAnalysis),
    Entry("biz.strategy_draft@1", "전략 초안", "biz", biz.StrategyDraft),
    Entry("biz.brand_strategy@1", "브랜드 전략 보고서", "biz", biz.BrandStrategy),
    Entry("biz.campaign_plan@1", "SNS 캠페인 계획", "biz", biz.CampaignPlan),
    Entry("biz.ad_copy@1", "광고 카피", "biz", biz.AdCopy),
    Entry("biz.execution_plan@1", "통합 실행안", "biz", biz.ExecutionPlan),
)

CATALOG: dict[str, Entry] = {e.schema_id: e for e in _ENTRIES}


class UnknownSchema(KeyError):
    """카탈로그에 없는 schema_id."""


def get(schema_id: str) -> Entry:
    try:
        return CATALOG[schema_id]
    except KeyError:
        raise UnknownSchema(schema_id) from None


def list_entries() -> list[dict[str, str]]:
    """`GET /v1/schemas` 가 돌려줄 목록."""
    return [{"schema_id": e.schema_id, "title": e.title, "category": e.category} for e in _ENTRIES]


def json_schema(schema_id: str) -> dict[str, Any]:
    """표준 JSON Schema. 프론트(Zod 변환) · 백엔드 저장 · `GET /v1/schemas/{id}` 용."""
    e = get(schema_id)
    return {
        "schema_id": e.schema_id,
        "catalog_version": CATALOG_VERSION,
        "category": e.category,
        **e.model.model_json_schema(),
        "title": e.title,        # 모델 클래스명 대신 한국어 이름을 쓴다
    }


def runtime_fields(schema_id: str) -> set[str]:
    """LLM 이 아니라 Runtime 이 채우는 필드(`x-source: runtime`)."""
    props = get(schema_id).model.model_json_schema().get("properties", {})
    return {k for k, v in props.items() if v.get("x-source") == "runtime"}


# 연결 호환 판정(`is_compatible`)은 v2.0 에서 없앴다 — 입력 형식이 폐지되어 어떤 노드든 이어진다.
# 다음 노드는 앞 결과 전체를 받고 프롬프트가 필요한 것만 읽는다(계약 v2.0 §5.3 · §5.4).


SCALAR_KINDS = ("enum", "string", "number", "boolean")


def field_kind(schema_id: str, path: str) -> str | None:
    """견본 필드의 종류 — enum · string · number · boolean · array · object. 경로가 없으면 None.

    분기 조건 `left` 가 **선언된 스칼라 필드**를 가리키는지 저장 시점에 검사하는 데 쓴다
    (계약 v2.0 §5.4). 경로는 점 표기이며 배열 인덱스(`competitors.0.name`)도 따라간다.
    """
    node: dict[str, Any] = _inline_defs(get(schema_id).model.model_json_schema())
    for key in [p for p in path.split(".") if p]:
        node = _unwrap_nullable(node)
        props = node.get("properties")
        if isinstance(props, dict):
            if key not in props:
                return None
            node = props[key]
        elif node.get("type") == "array" and key.isdigit():
            node = node.get("items") or {}
        else:
            return None
    node = _unwrap_nullable(node)
    if "enum" in node:
        return "enum"
    kind = node.get("type")
    if kind in ("integer", "number"):
        return "number"
    if kind in ("string", "boolean", "array", "object"):
        return kind
    return "object" if "properties" in node else None


def _unwrap_nullable(node: dict[str, Any]) -> dict[str, Any]:
    """`str | None` 은 anyOf[..., null] 로 나온다 → 실제 타입 쪽만 남긴다."""
    if "anyOf" in node:
        branches = [b for b in node["anyOf"] if b.get("type") != "null"]
        if len(branches) == 1:
            return {**branches[0], **{k: v for k, v in node.items() if k != "anyOf"}}
    return node


def sample(schema_id: str, overrides: dict[str, Any] | None = None) -> dict[str, Any]:
    """형식을 만족하는 샘플 payload. mock 실행 · 템플릿 등록 검증에 쓴다.

    `overrides` 로 특정 필드를 지정할 수 있다. mock 으로 분기 양쪽을 다 돌려 보려면
    `{"competition_level": "low"}` 처럼 분기 기준값을 바꿔 주면 된다.
    """
    payload = sample_payload(get(schema_id).model)
    payload.update(overrides or {})
    return payload


def parse(schema_id: str, payload: dict[str, Any]) -> BaseModel:
    """payload 를 형식에 맞춰 검증한다. 실패하면 ValidationError 가 올라간다.

    입력에서 실패하면 수요자 책임, 노드 출력에서 실패하면 공급자 책임이다(PAY-011).
    그 판정은 호출하는 쪽에서 한다.
    """
    return get(schema_id).model.model_validate(payload)


def response_schema(schema_id: str) -> dict[str, Any]:
    """Gemini 구조화 출력(`response_schema`)에 넣을 형태.

    표준 JSON Schema 를 그대로 주면 안 된다. 세 가지를 손본다.
      1) `$ref` · `$defs` 인라인 — OpenAPI 부분집합은 참조를 받지 않는다
      2) Runtime 이 채우는 필드 제거 — LLM 에게 발행일 · 기관명을 묻지 않는다
      3) `title`(한국어 이름)을 `description` 앞에 붙임 — 라벨을 프롬프트 밖에서 전달
    """
    e = get(schema_id)
    schema = _inline_defs(e.model.model_json_schema())
    for name in runtime_fields(schema_id):
        schema.get("properties", {}).pop(name, None)
        if name in schema.get("required", []):
            schema["required"].remove(name)
    return _to_openapi(schema)


def _inline_defs(schema: dict[str, Any]) -> dict[str, Any]:
    """`#/$defs/X` 참조를 실제 정의로 펼친다. 카탈로그 모델에 재귀 참조는 없다."""
    defs = schema.pop("$defs", {})

    def walk(node: Any) -> Any:
        if isinstance(node, dict):
            ref = node.get("$ref")
            if isinstance(ref, str) and ref.startswith("#/$defs/"):
                target = copy.deepcopy(defs[ref.split("/")[-1]])
                target.update({k: v for k, v in node.items() if k != "$ref"})
                return walk(target)
            return {k: walk(v) for k, v in node.items()}
        if isinstance(node, list):
            return [walk(v) for v in node]
        return node

    return walk(schema)


_OPENAPI_KEYS = {
    "type", "format", "description", "nullable", "enum", "items", "properties",
    "required", "minItems", "maxItems", "propertyOrdering",
}


def _to_openapi(node: Any) -> Any:
    """JSON Schema 를 Gemini 가 받는 OpenAPI 부분집합으로 좁힌다."""
    if isinstance(node, list):
        return [_to_openapi(v) for v in node]
    if not isinstance(node, dict):
        return node

    node = dict(node)

    # `str | None` 은 anyOf[..., null] 로 나온다 → nullable 로 바꾼다
    if "anyOf" in node:
        branches = [b for b in node.pop("anyOf") if b.get("type") != "null"]
        if len(branches) == 1:
            merged = dict(branches[0])
            merged.update(node)
            merged["nullable"] = True
            node = merged

    # 한국어 라벨을 설명 앞에 붙여 살린다
    label, desc = node.pop("title", None), node.get("description")
    if label and not _is_class_name(label):
        node["description"] = f"{label} — {desc}" if desc else label

    # 리스트 길이 제약 키 이름이 다르다
    for src, dst in (("minLength", "minItems"), ("maxLength", "maxItems")):
        if node.get("type") == "array" and src in node:
            node[dst] = node.pop(src)

    out: dict[str, Any] = {}
    for key, value in node.items():
        if key not in _OPENAPI_KEYS:
            continue
        if key == "properties":
            # 여기의 키는 스키마 키워드가 아니라 필드 이름이다. 값만 변환한다
            out[key] = {name: _to_openapi(sub) for name, sub in value.items()}
        elif key in ("required", "enum"):
            out[key] = list(value)
        else:
            out[key] = _to_openapi(value)
    if node.get("type") == "object" and "properties" in out:
        out["propertyOrdering"] = list(out["properties"].keys())
    return out


def _is_class_name(title: str) -> bool:
    """Pydantic 이 모델 클래스명을 title 로 넣은 경우(예: `Competitor`)는 라벨이 아니다."""
    return title.isascii() and title[:1].isupper()
