"""구조화 출력 · 검증 · 교정 · 봉투 테스트.

여기서 갈리는 것이 정산이다 — 교정 2회를 넘기면 공급자 책임, 입력이 틀리면 수요자
책임이다(계약 §9.2, PAY-011). 판정 코드를 고치면 이 파일도 함께 고친다.
"""
from __future__ import annotations

import json

from src import catalog, nodes
from src.llm import LLM, LLMResult


class FakeLLM:
    """정해진 응답을 순서대로 돌려주는 가짜 모델."""

    mock = False

    def __init__(self, *replies: str):
        self.replies = list(replies)
        self.calls: list[dict] = []

    def generate(self, system: str, user: str, label: str = "",
                 response_schema: dict | None = None, mock_json: dict | None = None) -> LLMResult:
        self.calls.append({"system": system, "user": user, "response_schema": response_schema})
        return LLMResult(text=self.replies.pop(0), model="fake", input_tokens=10,
                         output_tokens=20, duration_ms=5, schema_mode="response_schema")


def _market(**over) -> dict:
    payload = {
        "market_overview_md": "시장 개요",
        "competitors": [{"name": f"경쟁사 {i}", "positioning": "포지션", "note": "특징"} for i in (1, 2)],
        "trends": [{"label": f"트렌드 {i}"} for i in (1, 2)],
        "competition_level": "high",
        "level_reason": "상위 3사 점유율 합계 70%",
    }
    payload.update(over)
    return payload


def _json(payload: dict) -> str:
    return json.dumps(payload, ensure_ascii=False)


def _run(llm, schema_id="biz.market_research@1", **kw) -> nodes.NodeRun:
    return nodes.run_llm_node(llm, run_id="r1", node_id="n1", schema_id=schema_id,
                              system="프롬프트 원문", message="브리프", **kw)


# --- 정상 ------------------------------------------------------------------

def test_형식에_맞으면_한_번에_통과하고_봉투가_씌워진다():
    run = _run(FakeLLM(_json(_market())))
    assert run.ok and run.repair_count == 0
    env = run.envelope
    assert list(env) == ["a2a_version", "run_id", "node_id", "produced_at",
                         "schema_id", "payload", "meta", "error"]
    assert env["a2a_version"] == "2.0" and env["error"] is None
    assert env["schema_id"] == "biz.market_research@1"
    assert env["payload"]["competition_level"] == "high"
    assert env["meta"]["repair_count"] == 0 and env["meta"]["usage"]["input_tokens"] == 10


def test_코드블록으로_감싸_와도_받아준다():
    llm = FakeLLM("설명을 덧붙이면 ```json\n" + _json(_market()) + "\n``` 입니다")
    assert _run(llm).ok


def test_mock_모드는_LLM_없이_스키마를_만족한다():
    run = _run(LLM(mock=True))
    assert run.ok and run.envelope["meta"]["model"] == "mock"
    catalog.parse("biz.market_research@1", run.envelope["payload"])


# --- 교정 ------------------------------------------------------------------

def test_형식이_틀리면_한_번_교정해_통과한다():
    bad = _json(_market(competition_level="medium"))     # enum 위반
    llm = FakeLLM(bad, _json(_market()))
    run = _run(llm)
    assert run.ok and run.repair_count == 1
    assert run.envelope["meta"]["repair_count"] == 1
    # 교정 요청에 어긋난 곳이 들어간다
    assert "competition_level" in llm.calls[1]["user"]


def test_JSON_이_아니어도_교정_대상이다():
    llm = FakeLLM("죄송합니다, 표로 정리했습니다. | 항목 | 값 |", _json(_market()))
    run = _run(llm)
    assert run.ok and run.repair_count == 1
    assert "JSON" in run.attempts[0]


def test_교정_2회를_넘기면_공급자_책임으로_실패한다():
    bad = _json(_market(competition_level="medium"))
    llm = FakeLLM(bad, bad, bad)
    run = _run(llm)
    assert not run.ok
    assert len(llm.calls) == 3                       # 최초 1 + 교정 2
    assert run.envelope["payload"] is None
    assert run.error["code"] == "SCHEMA_VALIDATION_FAILED"
    assert run.error["fault"] == "supplier"
    assert run.envelope["meta"]["repair_count"] == 2


def test_교정_요청에_시스템_프롬프트를_싣지_않는다():
    """SEC-001 — 판매자 프롬프트 원문이 사용자 메시지 쪽으로 새지 않아야 한다."""
    llm = FakeLLM(_json(_market(competition_level="medium")), _json(_market()))
    _run(llm)
    assert "프롬프트 원문" not in llm.calls[1]["user"]


# --- Runtime 주입 -----------------------------------------------------------

def test_보고서의_발행일과_기관은_Runtime_이_채운다():
    payload = catalog.sample("biz.brand_strategy@1")
    for name in catalog.runtime_fields("biz.brand_strategy@1"):
        payload.pop(name)                            # LLM은 이 둘을 만들지 않는다
    llm = FakeLLM(_json(payload))
    run = _run(llm, schema_id="biz.brand_strategy@1",
               runtime_values={"issued_on": "2026년 9월 19일 토요일", "author_org": "ABC LAB"})
    assert run.ok and run.repair_count == 0
    assert run.envelope["payload"]["issued_on"] == "2026년 9월 19일 토요일"
    assert run.envelope["payload"]["author_org"] == "ABC LAB"


def test_주입값이_없으면_보고서는_검증에_걸린다():
    payload = catalog.sample("biz.brand_strategy@1")
    for name in catalog.runtime_fields("biz.brand_strategy@1"):
        payload.pop(name)
    llm = FakeLLM(*[_json(payload)] * 3)
    run = _run(llm, schema_id="biz.brand_strategy@1")
    assert not run.ok and run.error["fault"] == "supplier"


# --- 입력 검증 (수요자 책임) -------------------------------------------------

def test_입력_검증은_통과하면_None_을_돌려준다():
    assert nodes.check_input("biz.brand_brief@1", catalog.sample("biz.brand_brief@1")) is None


def test_입력이_형식과_다르면_사유를_돌려준다():
    payload = catalog.sample("biz.brand_brief@1")
    payload["price_tier"] = "초고가"
    reason = nodes.check_input("biz.brand_brief@1", payload)
    assert reason and "price_tier" in reason


def test_input_schema_id_가_없으면_검증하지_않는다():
    assert nodes.check_input(None, {"아무거나": 1}) is None
