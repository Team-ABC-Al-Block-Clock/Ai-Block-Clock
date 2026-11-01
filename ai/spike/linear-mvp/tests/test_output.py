"""출력 선언 v2.1 — 글 노드 · JSON 예시 노드 · 이종 체인 · 옛 견본 별칭. 계약 v2.1 §5.2 · §5.5.

판매자는 노드마다 `output {kind, example}` 을 선언하고, 다음 노드는 앞 결과 전체를 받는다.
글 노드는 검증 · 교정 없이 `{"text": …}` 봉투를 내고, 글 → JSON · JSON → 글 어느 순서로도 이어진다.
"""
from __future__ import annotations

import json

from fastapi.testclient import TestClient

from src import graph, nodes, runner, shape
from src.api import app
from src.llm import LLM
from tests.messaging_support import submit, submit_message
from tests.test_nodes import FakeLLM

BRIEF = {"brand_name": "초록소반", "category": "간편식", "target": "1인 가구 30대",
         "price_tier": "중가", "differentiator": "국내산 재료"}

PLAN = {"objective": "가을 신제품 인지도", "channels": [{"name": "인스타그램", "role": "도달"}], "posts": ["첫 게시물"]}


def _text_then_json() -> dict:
    """글 노드(법률 메모) → 견본 JSON 노드(캠페인 계획) — 형식이 다른 두 판매자의 연결."""
    return {"id": "mixed", "name": "글 → JSON",
            "nodes": [
                {"id": "memo", "name": "법률 검토 메모", "prompt": "브리프를 읽고 상표 관점의 메모를 쓴다",
                 "input": {"example": {"brand_name": "x", "category": "x"}}, "output": {"kind": "text"}, "owner_id": "u_law"},
                {"id": "plan", "name": "캠페인 기획", "prompt": "앞 메모를 반영해 캠페인을 짠다",
                 "output": {"kind": "json", "example": PLAN}, "owner_id": "u_mkt"},
            ],
            "edges": [{"from": "memo", "to": "plan"}],
            "output": {"type": "pdf", "title": "혼합 체인"}}


def test_글_노드는_검증_없이_text_봉투를_낸다():
    run = nodes.run_text_node(FakeLLM("자유롭게 쓴 메모"), run_id="r", node_id="memo", system="s", message="m")
    assert run.ok and run.repair_count == 0
    assert run.envelope["schema_id"] == "text" and run.payload == {"text": "자유롭게 쓴 메모"}
    assert run.envelope["meta"]["repair_count"] == 0 and run.envelope["meta"]["usage"]["input_tokens"] == 10


def test_mock_글_노드도_돈다():
    run = nodes.run_text_node(LLM(mock=True), run_id="r", node_id="memo", system="s", message="m")
    assert run.ok and run.envelope["meta"]["model"] == "mock" and run.payload["text"].startswith("## [MOCK")


def test_글_다음에_JSON_노드가_이어져_끝까지_돈다():
    wf = _text_then_json()
    report = graph.validate(wf)
    assert report["valid"] and report["warnings"] == []
    run = runner.execute(wf, mock=True, input_payload=BRIEF, comment="가을 신제품")
    assert run["status"] == "succeeded"
    memo, plan = run["nodes"]
    assert memo["output"] == {"kind": "text", "example": None} and plan["output"]["example"] == PLAN
    assert run["result"]["envelope"]["schema_id"] == "json" and run["result"]["envelope"]["payload"] == PLAN
    request = (runner.OUT_ROOT / run["run_id"] / "nodes" / "plan.request.md").read_text(encoding="utf-8")
    assert "### 법률 검토 메모 (memo · 글)" in request and "## [MOCK memo]" in request   # 글은 본문 그대로
    assert {c["owner_id"] for c in run["contribution"]} == {"u_law", "u_mkt"}


def test_JSON_다음에_글_노드가_이어지면_산출물은_글이다():
    wf = runner.load_workflow("brand_agent")
    wf["nodes"].append({"id": "n5", "name": "요약 메모", "prompt": "보고서를 세 줄로 요약한다",
                        "output": {"kind": "text"}, "owner_id": "u_seller_d"})
    wf["edges"].append({"from": "n4", "to": "n5"})
    run = runner.execute(wf, mock=True, input_payload=BRIEF)
    assert run["status"] == "succeeded"
    assert run["result"]["output"] == {"kind": "text", "example": None}
    assert run["result"]["envelope"]["schema_id"] == "text" and run["result"]["pdf"]
    request = (runner.OUT_ROOT / run["run_id"] / "nodes" / "n5.request.md").read_text(encoding="utf-8")
    assert "(n4 · JSON)" in request and '"messages"' in request        # JSON 전체가 넘어갔다


def test_옛_견본_id_는_한_버전_동안_예시로_바꿔_읽힌다():
    for legacy in ({"output_schema_id": "biz.campaign_plan@1"},
                   {"output": {"kind": "json", "sample": "biz.campaign_plan@1"}}):
        wf = _text_then_json()
        node = {k: v for k, v in wf["nodes"][1].items() if k != "output"}
        wf["nodes"][1] = {**node, **legacy}
        report = graph.validate(wf)
        assert report["valid"], report["errors"]
        spec = graph.output_spec_of(wf["nodes"][1])
        assert spec["kind"] == "json" and spec["example"] == shape.LEGACY_EXAMPLES["biz.campaign_plan@1"]
        assert any(w["code"] == "DEPRECATED_FIELD" and "biz.campaign_plan@1" in w["message"] for w in report["warnings"])
    wf = _text_then_json()
    wf["nodes"][1] = {**{k: v for k, v in wf["nodes"][1].items() if k != "output"}, "output_schema_id": "common.text@1"}
    assert graph.output_spec_of(wf["nodes"][1])["kind"] == "text"                # common.text 는 글
    assert runner.execute(wf, mock=True, input_payload=BRIEF)["status"] == "succeeded"


def test_API_응답의_노드에는_선언한_형식이_실린다():
    client = TestClient(app)
    message = submit_message(_text_then_json(), input=BRIEF)
    run_id = message["execution_id"]

    outcome, _ = submit(message)

    assert outcome == "ack"
    body = client.get(f"/v1/runs/{run_id}?include_node_output=true").json()
    assert body["status"] == "succeeded"
    assert body["nodes"][0]["output"] == {"kind": "text", "example": None}
    assert json.loads(body["nodes"][0]["output_payload"])["text"].startswith("## [MOCK")
    assert body["result"]["output"] == {"kind": "json", "example": PLAN}
    artifact = client.get(f"/v1/runs/{run_id}/artifact").json()
    assert artifact["schema_id"] == "json" and artifact["payload"] == PLAN and artifact["a2a_version"] == "2.0"
