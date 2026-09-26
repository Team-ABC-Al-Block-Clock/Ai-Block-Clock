"""구조화 출력 · 검사 · 교정 · 봉투 테스트 — 판매자의 출력 JSON 예시 기준(계약 v2.1).

여기서 갈리는 것이 정산이다 — 교정 2회를 넘기면 공급자 책임, 사용자 입력이 입력 예시와 다르면
수요자 책임이다(계약 §9.2, PAY-011). 판정 코드를 고치면 이 파일도 함께 고친다.
"""
from __future__ import annotations

import json

from src import nodes, shape
from src.llm import LLM, LLMResult

# 판매자가 "시장 조사" 노드에 준 출력 예시 — 이 모양이 곧 강제 규격이다
MARKET_EXAMPLE = {
    "market_overview_md": "시장 개요",
    "competitors": [{"name": "경쟁사", "note": "특징"}],
    "competition_level": "high",
    "level_reason": "전국 유통 브랜드 5개를 확인했다.",
}
BRIEF_EXAMPLE = {"brand_name": "초록소반", "category": "간편식", "price_tier": "중가"}


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
        "competitors": [{"name": f"경쟁사 {i}", "note": "특징"} for i in (1, 2)],
        "competition_level": "high",
        "level_reason": "전국 유통 브랜드 2개를 확인했다.",
    }
    payload.update(over)
    return payload


def _json(payload: dict) -> str:
    return json.dumps(payload, ensure_ascii=False)


def _run(llm, example=None, **kw) -> nodes.NodeRun:
    return nodes.run_llm_node(llm, run_id="r1", node_id="n1", example=example or MARKET_EXAMPLE,
                              system="프롬프트 원문", message="브리프", **kw)


# --- 정상 ------------------------------------------------------------------

def test_예시_모양이면_한_번에_통과하고_봉투가_씌워진다():
    llm = FakeLLM(_json(_market()))
    run = _run(llm)
    assert run.ok and run.repair_count == 0
    env = run.envelope
    assert list(env) == ["a2a_version", "run_id", "node_id", "produced_at",
                         "schema_id", "payload", "meta", "error"]
    assert env["a2a_version"] == "2.0" and env["error"] is None
    assert env["schema_id"] == "json"
    assert env["payload"]["competition_level"] == "high"
    assert env["meta"]["repair_count"] == 0 and env["meta"]["usage"]["input_tokens"] == 10
    # 예시에서 만든 응답 스키마가 모델에 간다
    sent = llm.calls[0]["response_schema"]
    assert sent["type"] == "object" and sent["propertyOrdering"] == list(MARKET_EXAMPLE)
    assert sent["properties"]["competitors"]["items"]["properties"]["name"] == {"type": "string"}


def test_예시에_없는_키가_더_와도_통과한다():
    assert _run(FakeLLM(_json(_market(extra="덤")))).ok


def test_코드블록으로_감싸_와도_받아준다():
    llm = FakeLLM("설명을 덧붙이면 ```json\n" + _json(_market()) + "\n``` 입니다")
    assert _run(llm).ok


def test_mock_모드는_예시를_그대로_돌려준다():
    run = _run(LLM(mock=True))
    assert run.ok and run.envelope["meta"]["model"] == "mock"
    assert run.payload == MARKET_EXAMPLE


def test_mock_은_경로로_값을_덮을_수_있다():
    run = _run(LLM(mock=True), mock_overrides={"competition_level": "low"})
    assert run.payload["competition_level"] == "low" and run.payload["level_reason"] == MARKET_EXAMPLE["level_reason"]


# --- 교정 ------------------------------------------------------------------

def test_키가_빠지면_한_번_교정해_통과한다():
    bad = _market()
    del bad["competition_level"]
    llm = FakeLLM(_json(bad), _json(_market()))
    run = _run(llm)
    assert run.ok and run.repair_count == 1
    assert "competition_level" in llm.calls[1]["user"]           # 교정 요청에 어긋난 곳이 들어간다


def test_타입이_다르면_교정_대상이다():
    llm = FakeLLM(_json(_market(competitors="경쟁사 여럿")), _json(_market()))
    run = _run(llm)
    assert run.ok and run.repair_count == 1
    assert "competitors" in run.attempts[0] and "array" in run.attempts[0]


def test_JSON_이_아니어도_교정_대상이다():
    llm = FakeLLM("죄송합니다, 표로 정리했습니다. | 항목 | 값 |", _json(_market()))
    run = _run(llm)
    assert run.ok and run.repair_count == 1
    assert "JSON" in run.attempts[0]


def test_교정_2회를_넘기면_공급자_책임으로_실패한다():
    bad = _json(_market(competition_level=3))
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
    llm = FakeLLM(_json(_market(competition_level=3)), _json(_market()))
    _run(llm)
    assert "프롬프트 원문" not in llm.calls[1]["user"]


# --- 입력 검사 (수요자 책임) -------------------------------------------------

def test_입력이_입력_예시_모양이면_None():
    assert nodes.check_input(BRIEF_EXAMPLE, {**BRIEF_EXAMPLE, "comment": "덤"}) is None


def test_입력에_예시의_키가_빠지면_사유를_돌려준다():
    reason = nodes.check_input(BRIEF_EXAMPLE, {"category": "간편식", "price_tier": "중가"})
    assert reason and "brand_name" in reason


def test_입력_예시가_없으면_검사하지_않는다():
    assert nodes.check_input(None, {"아무거나": 1}) is None


def test_옛_견본_변환표는_11종이다():
    assert len(shape.LEGACY_EXAMPLES) == 11 and "biz.market_research@1" in shape.LEGACY_EXAMPLES
