"""PoC 전용 출력 스키마 — biz.brand_strategy@1

정식 카탈로그는 트랙 A(사용자)가 ai/schemas/catalog/ 에 정의한다.
이 파일은 "판매자 커스텀 템플릿" 흐름을 실증하기 위한 임시 정의다.
필드명 옆의 title 이 폼 라벨 · 엑셀 열 이름 · 템플릿 바인딩 이름이 된다.
"""
from __future__ import annotations

from typing import Any

SCHEMA_ID = "biz.brand_strategy@1"

# 판매자가 노드를 만들 때 고르는 "출력 형식"의 정의.
# 정식 버전에서는 Pydantic 모델에서 model_json_schema() 로 뽑는다.
OUTPUT_SCHEMA: dict[str, Any] = {
    "schema_id": SCHEMA_ID,
    "title": "브랜드 전략 보고서",
    "fields": {
        "brand_name":   {"type": "string", "title": "브랜드명", "required": True},
        "issued_on":    {"type": "string", "title": "발행일", "required": True,
                         "description": "예: 2026년 1월 5일 월요일"},
        "author_org":   {"type": "string", "title": "작성 기관", "required": True},
        "headline":     {"type": "string", "title": "대제목", "required": True,
                         "description": "보고서 표지의 한 문장. 20자 내외 두 줄"},
        "purpose": {
            "type": "array", "title": "브랜드 목적", "required": True,
            "min_items": 3, "max_items": 3,
            "items": {"label": {"type": "string", "title": "항목"}},
        },
        "positioning": {
            "type": "array", "title": "브랜드 포지셔닝", "required": True,
            "min_items": 3, "max_items": 3,
            "items": {"label": {"type": "string", "title": "항목"}},
        },
        "reasons": {
            "type": "array", "title": "선택의 이유", "required": True,
            "min_items": 3, "max_items": 3,
            "items": {
                "axis": {"type": "string", "title": "축",
                         "description": "예: 기능 중심 / 메시지 중심 / 제품 중심"},
                "detail": {"type": "string", "title": "설명"},
            },
        },
        "core_strategy": {
            "type": "array", "title": "핵심 전략", "required": True,
            "min_items": 3, "max_items": 5,
            "items": {"label": {"type": "string", "title": "항목"}},
        },
        "messages": {
            "type": "object", "title": "메시지 체계", "required": True,
            "fields": {
                "core":  {"type": "string", "title": "핵심 메시지"},
                "sub":   {"type": "string", "title": "보조 메시지"},
                "daily": {"type": "string", "title": "일상 메시지"},
            },
        },
        "summary_md": {
            "type": "string", "title": "총평", "required": False,
            "format": "markdown",
            "description": "_md 접미사 = 마크다운 허용 필드. 렌더러가 마크다운으로 해석한다",
        },
    },
}

# 첫 노드가 사용자에게서 받는 입력 형식. 프론트는 이걸로 폼을 그린다.
INPUT_SCHEMA: dict[str, Any] = {
    "schema_id": "biz.brand_brief@1",
    "title": "브랜드 기획 요청서",
    "fields": {
        "brand_name":  {"type": "string", "title": "브랜드명", "required": True},
        "category":    {"type": "string", "title": "제품 카테고리", "required": True},
        "target":      {"type": "string", "title": "핵심 고객", "required": True},
        "price_tier":  {"type": "enum", "title": "가격대", "required": True,
                        "enum": ["보급형", "중가", "프리미엄"]},
        "differentiator": {"type": "string", "title": "차별점", "required": True},
        "comment":     {"type": "string", "title": "추가 요청", "required": False},
    },
}


def sample_payload() -> dict[str, Any]:
    """템플릿 검증용 더미 데이터. 등록 시점 관문에서 이걸로 렌더해 본다."""
    return {
        "brand_name": "샘플 브랜드",
        "issued_on": "2026년 1월 1일 목요일",
        "author_org": "SAMPLE INSIGHT LAB",
        "headline": "샘플 대제목이\n두 줄로 들어갑니다",
        "purpose": [{"label": f"목적 항목 {i}"} for i in (1, 2, 3)],
        "positioning": [{"label": f"포지셔닝 항목 {i}"} for i in (1, 2, 3)],
        "reasons": [{"axis": f"축 {i}", "detail": f"설명 {i}"} for i in (1, 2, 3)],
        "core_strategy": [{"label": f"전략 {i}"} for i in (1, 2, 3)],
        "messages": {"core": "핵심 메시지", "sub": "보조 메시지", "daily": "일상 메시지"},
        "summary_md": "총평 **본문**입니다.",
    }


def describe(schema: dict[str, Any]) -> str:
    """LLM 프롬프트에 넣을 사람이 읽는 스키마 설명."""
    import json
    return json.dumps(schema, ensure_ascii=False, indent=2)
