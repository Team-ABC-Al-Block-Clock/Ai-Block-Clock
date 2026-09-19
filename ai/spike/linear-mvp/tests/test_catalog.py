"""카탈로그 판정 로직 테스트.

연결 호환(WRK-005) · 형식 검증(EXE-009) · 구조화 출력 스키마 변환을 다룬다.
판정이 바뀌면 정산 · 실패 책임이 바뀌므로 테스트 없이 고치지 않는다(`ai/CLAUDE.md` §3-7).

    uv run pytest -q
"""
from __future__ import annotations

import json

import pytest
from pydantic import ValidationError

from src import catalog
from src.catalog import export


def _brand_strategy_payload(**overrides) -> dict:
    """LLM 이 낸 payload + Runtime 이 채운 두 필드까지 갖춘 완전한 형태."""
    payload = {
        "brand_name": "샘플 브랜드",
        "issued_on": "2026년 9월 18일 금요일",
        "author_org": "SAMPLE INSIGHT LAB",
        "headline": "샘플 대제목",
        "purpose": [{"label": f"목적 {i}"} for i in (1, 2, 3)],
        "positioning": [{"label": f"포지셔닝 {i}"} for i in (1, 2, 3)],
        "reasons": [{"axis": f"축 {i}", "detail": f"설명 {i}"} for i in (1, 2, 3)],
        "core_strategy": [{"label": f"전략 {i}"} for i in (1, 2, 3)],
        "messages": {"core": "핵심", "sub": "보조", "daily": "일상"},
        "summary_md": "총평 **본문**",
    }
    payload.update(overrides)
    return payload


def _market_research_payload(**overrides) -> dict:
    payload = {
        "market_overview_md": "시장 개요",
        "competitors": [{"name": f"경쟁사 {i}", "positioning": "포지션", "note": "특징"} for i in (1, 2)],
        "trends": [{"label": f"트렌드 {i}"} for i in (1, 2)],
        "competition_level": "high",
        "level_reason": "상위 3사 점유율 합계가 70%를 넘는다",
    }
    payload.update(overrides)
    return payload


# --- 목록 ------------------------------------------------------------------

def test_카탈로그는_공통_3종과_비즈니스_8종이다():
    entries = catalog.list_entries()
    assert len(entries) == 11
    assert sum(e["category"] == "common" for e in entries) == 3
    assert sum(e["category"] == "biz" for e in entries) == 8


def test_모든_항목이_한국어_이름과_버전을_갖는다():
    for entry in catalog.list_entries():
        assert entry["schema_id"].endswith("@1")
        assert entry["schema_id"].split(".")[0] in ("common", "biz")
        assert entry["title"] and entry["title"].isascii() is False   # 화면에 보이는 한국어 이름


def test_없는_형식을_찾으면_UnknownSchema():
    with pytest.raises(catalog.UnknownSchema):
        catalog.get("biz.없는형식@1")


# --- 연결 호환 판정 (WRK-005) ----------------------------------------------

def test_같은_형식끼리만_연결된다():
    assert catalog.is_compatible("biz.market_research@1", "biz.market_research@1")
    assert not catalog.is_compatible("biz.market_research@1", "biz.product_analysis@1")
    # MVP 는 느슨한 변환을 허용하지 않는다. biz.* → common.document 도 막힌다
    assert not catalog.is_compatible("biz.brand_strategy@1", "common.document@1")


def test_합류하는_두_노드는_같은_출력_형식을_쓴다():
    # 차별화 전략(n3a)과 대중 공략 전략(n3b)이 같은 형식이라 n4 가 받을 수 있다
    assert catalog.is_compatible("biz.strategy_draft@1", "biz.strategy_draft@1")


# --- 형식 검증 (EXE-009) ---------------------------------------------------

def test_올바른_payload_는_통과한다():
    parsed = catalog.parse("biz.brand_strategy@1", _brand_strategy_payload())
    assert parsed.messages.core == "핵심"


def test_보고서의_배열은_3개_고정이라_2개면_실패한다():
    payload = _brand_strategy_payload(purpose=[{"label": "목적 1"}, {"label": "목적 2"}])
    with pytest.raises(ValidationError):
        catalog.parse("biz.brand_strategy@1", payload)


def test_분기_기준값은_enum_을_벗어나면_실패한다():
    with pytest.raises(ValidationError):
        catalog.parse("biz.market_research@1", _market_research_payload(competition_level="medium"))


def test_필수_필드가_빠지면_실패한다():
    payload = _market_research_payload()
    del payload["level_reason"]
    with pytest.raises(ValidationError):
        catalog.parse("biz.market_research@1", payload)


def test_runtime_이_채우기_전에는_보고서가_검증을_통과하지_못한다():
    llm_output = _brand_strategy_payload()
    del llm_output["issued_on"], llm_output["author_org"]
    with pytest.raises(ValidationError):
        catalog.parse("biz.brand_strategy@1", llm_output)

    llm_output["issued_on"] = "2026년 9월 18일 금요일"
    llm_output["author_org"] = "SAMPLE INSIGHT LAB"
    assert catalog.parse("biz.brand_strategy@1", llm_output)


# --- 구조화 출력 스키마 ----------------------------------------------------

def test_응답_스키마에는_참조가_남지_않는다():
    for schema_id in catalog.CATALOG:
        dumped = json.dumps(catalog.response_schema(schema_id))
        assert "$ref" not in dumped and "$defs" not in dumped


def test_응답_스키마는_runtime_주입_필드를_LLM_에게_묻지_않는다():
    schema = catalog.response_schema("biz.brand_strategy@1")
    assert catalog.runtime_fields("biz.brand_strategy@1") == {"issued_on", "author_org"}
    for name in ("issued_on", "author_org"):
        assert name not in schema["properties"]
        assert name not in schema["required"]


def test_응답_스키마가_한국어_라벨을_설명으로_전달한다():
    schema = catalog.response_schema("biz.market_research@1")
    assert schema["properties"]["competition_level"]["description"].startswith("경쟁 강도")
    assert schema["properties"]["competitors"]["minItems"] == 2


def test_선택_필드는_nullable_로_바뀐다():
    schema = catalog.response_schema("biz.brand_strategy@1")
    assert schema["properties"]["summary_md"]["nullable"] is True
    assert schema["properties"]["summary_md"]["type"] == "string"


# --- 산출물 ----------------------------------------------------------------

def test_export_산출물이_모델과_일치한다():
    for name, content in export.build().items():
        path = export.OUT_DIR / name
        assert path.exists(), f"{name} 이 없습니다. uv run python -m src.catalog.export 를 돌리세요"
        assert path.read_text(encoding="utf-8") == content, f"{name} 이 모델과 다릅니다"
