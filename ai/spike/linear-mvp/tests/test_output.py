"""출력 선언 v2.0 — 글 노드 · 견본 노드 · 이종 체인 · 별칭. 계약 v2.0 §5.2 · §5.5.

판매자는 노드마다 `output {kind, sample}` 을 선언하고, 다음 노드는 앞 결과 전체를 받는다.
글 노드는 검증 · 교정 없이 `{"text": …}` 봉투를 내고, 글 → JSON · JSON → 글 어느 순서로도 이어진다.
"""
from __future__ import annotations

import json

from fastapi.testclient import TestClient

from src import graph, nodes, runner
from src.api import app
from src.llm import LLM
from tests.test_nodes import FakeLLM

BRIEF = {"brand_name": "초록소반", "category": "간편식", "target": "1인 가구 30대",
         "price_tier": "중가", "differentiator": "국내산 재료"}


def _text_then_json() -> dict:
    """글 노드(법률 메모) → 견본 JSON 노드(캠페인 계획) — 형식이 다른 두 판매자의 연결."""
    return {"id": "mixed", "name": "글 → JSON",
            "nodes": [
                {"id": "memo", "name": "법률 검토 메모", "prompt": "브리프를 읽고 상표 관점의 메모를 쓴다",
                 "input": {"sample": "biz.brand_brief@1"}, "output": {"kind": "text"}, "owner_id": "u_law"},
                {"id": "plan", "name": "캠페인 기획", "prompt": "앞 메모를 반영해 캠페인을 짠다",
                 "output": {"kind": "json", "sample": "biz.campaign_plan@1"}, "owner_id": "u_mkt"},
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
    assert memo["output"] == {"kind": "text", "sample": None} and plan["output"]["sample"] == "biz.campaign_plan@1"
    assert run["result"]["envelope"]["schema_id"] == "biz.campaign_plan@1"
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
    assert run["result"]["output"] == {"kind": "text", "sample": None}
    assert run["result"]["envelope"]["schema_id"] == "text" and run["result"]["pdf"]
    request = (runner.OUT_ROOT / run["run_id"] / "nodes" / "n5.request.md").read_text(encoding="utf-8")
    assert "(n4 · 브랜드 전략 보고서)" in request and '"messages"' in request        # JSON 전체가 넘어갔다


def test_별칭_output_schema_id_는_한_버전_동안_읽힌다():
    wf = _text_then_json()
    wf["nodes"][1] = {**wf["nodes"][1], "output_schema_id": "biz.campaign_plan@1"}
    del wf["nodes"][1]["output"]
    report = graph.validate(wf)
    assert report["valid"]
    assert graph.output_spec_of(wf["nodes"][1]) == {"kind": "json", "sample": "biz.campaign_plan@1"}
    assert any(w["code"] == "DEPRECATED_FIELD" and "output_schema_id" in w["message"] for w in report["warnings"])
    assert runner.execute(wf, mock=True, input_payload=BRIEF)["status"] == "succeeded"


def test_API_응답의_노드에는_선언한_형식이_실린다():
    client = TestClient(app)
    res = client.post("/v1/runs", data={"workflow": json.dumps(_text_then_json(), ensure_ascii=False),
                                        "input": json.dumps(BRIEF, ensure_ascii=False), "mock": "true"})
    body = client.get(f"/v1/runs/{res.json()['run_id']}?include_node_output=true").json()
    assert body["status"] == "succeeded"
    assert body["nodes"][0]["output"] == {"kind": "text", "sample": None}
    assert json.loads(body["nodes"][0]["output_payload"])["text"].startswith("## [MOCK")
    assert body["result"]["output"] == {"kind": "json", "sample": "biz.campaign_plan@1"}
    artifact = client.get(f"/v1/runs/{res.json()['run_id']}/artifact").json()
    assert artifact["schema_id"] == "biz.campaign_plan@1" and artifact["a2a_version"] == "2.0"
