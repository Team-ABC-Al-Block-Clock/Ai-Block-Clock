"""그래프 실행 테스트 — 위상 정렬 · 분할 · 병합 · 조건 분기 · 합류 · 정의 검증.

여기가 09-19(당초 09-20) 관문이다: UC1 5노드가 mock 으로 완주하고 경쟁 강도 high/low가
서로 다른 경로로 간다. 분기 판정은 코드가 하므로 같은 입력이면 항상 같은 경로가 나온다.
"""
from __future__ import annotations

import json

import pytest
from fastapi.testclient import TestClient

from src import graph, runner
from src.api import app

BRIEF = {
    "brand_name": "초록소반",
    "category": "간편식",
    "target": "1인 가구 30대",
    "price_tier": "중가",
    "differentiator": "국내산 재료",
}


def _text_node(node_id: str, schema: str = "common.text@1") -> dict:
    return {"id": node_id, "prompt": "p", "output_schema_id": schema}


def _uc1(**kw) -> dict:
    return runner.execute(runner.load_workflow("brand_agent"), comment="테스트", mock=True,
                          input_payload=BRIEF, **kw)


LOW = {"biz.market_research@1": {"competition_level": "low"}}


# --- 위상 정렬 · 순환 --------------------------------------------------------

def test_edges_가_실행_순서를_정한다():
    wf = {"nodes": [_text_node("c"), _text_node("a"), _text_node("b")],
          "edges": [{"from": "a", "to": "b"}, {"from": "b", "to": "c"}]}
    assert graph.build(wf).order == ["a", "b", "c"]


def test_edges_가_없으면_배열_순서대로_직선이다():
    wf = {"nodes": [_text_node("a"), _text_node("b")]}
    g = graph.build(wf)
    assert g.order == ["a", "b"] and g.entries == ["a"]


def test_순환은_실행_전에_걸린다():
    wf = {"nodes": [_text_node("a"), _text_node("b")],
          "edges": [{"from": "a", "to": "b"}, {"from": "b", "to": "a"}]}
    with pytest.raises(graph.GraphError):
        graph.build(wf)


def test_최장_경로는_실제로_도는_경로가_아니라_가능한_최댓값이다():
    g = graph.build(runner.load_workflow("brand_agent"))
    # n1 -> br1 -> n3a -> n4 : 분기로 한쪽만 돌지만 길이는 4로 센다
    assert graph.longest_path(g) == 4


# --- 분할 · 병합 · 합류 ------------------------------------------------------

def test_병합은_port_이름으로_묶어_받는다():
    g = graph.build(runner.load_workflow("brand_agent"))
    payloads = {"n1": {"competition_level": "high"}, "n2": {"strengths": []}}
    received = graph.fan_in(g, "br1", payloads)
    assert received == {"market": payloads["n1"], "product": payloads["n2"]}


def test_상위가_하나면_payload_를_그대로_받는다():
    g = graph.build(runner.load_workflow("brand_agent"))
    payloads = {"br1": {"any": 1}, "n3a": {"approach": "차별화"}}
    assert graph.fan_in(g, "n4", payloads, {"br1": True}) == {"approach": "차별화"}


def test_합류에서는_도착한_쪽만_살아_있다():
    g = graph.build(runner.load_workflow("brand_agent"))
    payloads = {"br1": {}, "n3a": {"a": 1}, "n3b": {"b": 2}}
    alive = graph.live_edges(g, "n4", payloads, {"br1": True})
    assert [e["from"] for e in alive] == ["n3a", "n3b"]      # 둘 다 실행됐다면 둘 다 온다


def test_분기_미선택_경로는_살아_있지_않다():
    g = graph.build(runner.load_workflow("brand_agent"))
    assert graph.live_edges(g, "n3b", {"br1": {}}, {"br1": True}) == []
    assert [e["from"] for e in graph.live_edges(g, "n3a", {"br1": {}}, {"br1": True})] == ["br1"]


# --- 조건 평가 ---------------------------------------------------------------

def test_조건은_임의_상위_노드의_경로를_읽는다():
    payloads = {"n1": {"competition_level": "high", "nested": {"score": 7}}}
    assert graph.evaluate({"left": "n1.competition_level", "op": "==", "right": "high"}, payloads)
    assert graph.evaluate({"left": "n1.nested.score", "op": ">", "right": 5}, payloads)
    assert not graph.evaluate({"left": "n1.competition_level", "op": "!=", "right": "high"}, payloads)
    assert graph.evaluate({"left": "n1.competition_level", "op": "in", "right": ["high", "mid"]}, payloads)


def test_조건이_가리키는_경로가_없으면_수요자_책임_오류다():
    with pytest.raises(graph.ConditionInvalid):
        graph.evaluate({"left": "n1.없는필드", "op": "==", "right": "x"}, {"n1": {"a": 1}})
    with pytest.raises(graph.ConditionInvalid):
        graph.evaluate({"left": "n9.field", "op": "==", "right": "x"}, {"n1": {}})


def test_지원하지_않는_연산자는_거부한다():
    with pytest.raises(graph.ConditionInvalid):
        graph.evaluate({"left": "n1.a", "op": "~=", "right": 1}, {"n1": {"a": 1}})


# --- 정의 검증 (계약 §5.7) ---------------------------------------------------

def test_UC1_정의는_검증을_통과한다():
    report = graph.validate(runner.load_workflow("brand_agent"))
    assert report["valid"] and report["stats"] == {
        "node_count": 6, "expanded_node_count": 6, "longest_path": 4, "agent_depth": 0}


def test_합류하는_경로들의_출력_형식이_다르면_거부한다():
    wf = runner.load_workflow("brand_agent")
    for node in wf["nodes"]:
        if node["id"] == "n3b":
            node["output_schema_id"] = "biz.campaign_plan@1"
    codes = [e["code"] for e in graph.validate(wf)["errors"]]
    assert "JOIN_SCHEMA_MISMATCH" in codes


def test_분기_노드는_when_true_false_간선이_각각_하나여야_한다():
    wf = runner.load_workflow("brand_agent")
    wf["edges"] = [e for e in wf["edges"] if not (e["from"] == "br1" and e.get("when") is False)]
    codes = [e["code"] for e in graph.validate(wf)["errors"]]
    assert "BRANCH_EDGES_INVALID" in codes


def test_카탈로그에_없는_형식은_거부한다():
    wf = {"nodes": [{"id": "a", "prompt": "p", "output_schema_id": "biz.없는것@1"}]}
    assert [e["code"] for e in graph.validate(wf)["errors"]] == ["UNKNOWN_SCHEMA"]


def test_규모_상한을_넘으면_거부한다():
    nodes = [_text_node(f"n{i}") for i in range(9)]
    edges = [{"from": f"n{i}", "to": f"n{i+1}"} for i in range(8)]
    report = graph.validate({"nodes": nodes, "edges": edges})
    assert not report["valid"]
    assert any(e["code"] == "LIMIT_EXCEEDED" for e in report["errors"])


def test_종단_출력과_output_schema_id_가_다르면_거부한다():
    wf = runner.load_workflow("brand_agent")
    wf["output"] = {"schema_id": "biz.ad_copy@1"}
    assert any(e["code"] == "OUTPUT_SCHEMA_MISMATCH" for e in graph.validate(wf)["errors"])


def test_자유_텍스트_워크플로우는_경고만_받는다():
    report = graph.validate(runner.load_workflow("report"))
    assert report["valid"]
    assert [w["code"] for w in report["warnings"]] == ["LEGACY_TEXT_NODE", "LEGACY_TEXT_NODE"]


# --- UC1 실행: 1단계 관문 ----------------------------------------------------

def test_경쟁_강도가_high_면_차별화_전략으로_간다():
    run = _uc1()
    status = {n["id"]: n["status"] for n in run["nodes"]}
    assert run["status"] == "succeeded"
    assert status["n3a"] == "succeeded" and status["n3b"] == "skipped"
    assert run["result"]["envelope"]["schema_id"] == "biz.brand_strategy@1"
    assert run["result"]["pdf"]


def test_경쟁_강도가_low_면_대중_공략_전략으로_간다():
    run = _uc1(mock_overrides=LOW)
    status = {n["id"]: n["status"] for n in run["nodes"]}
    assert run["status"] == "succeeded"
    assert status["n3b"] == "succeeded" and status["n3a"] == "skipped"


def test_안_간_경로는_돈을_받지_않는다():
    run = _uc1()
    assert run["not_executed"] == [
        {"node_id": "n3b", "reason": "branch_not_taken", "refund_abc": 4.5}]
    charged = {c["node_id"] for c in run["contribution"]}
    assert charged == {"n1", "n2", "n3a", "n4"}          # 분기 노드 br1 은 과금하지 않는다


def test_분기_조건이_가리키는_값이_없으면_실행이_멈춘다():
    wf = runner.load_workflow("brand_agent")
    for node in wf["nodes"]:
        if node["id"] == "br1":
            node["condition"] = {"left": "n1.없는필드", "op": "==", "right": "high"}
    run = runner.execute(wf, mock=True, input_payload=BRIEF)
    assert run["status"] == "failed"
    assert run["error"]["code"] == "BRANCH_CONDITION_INVALID"
    assert run["error"]["fault"] == "consumer"


def test_정의가_틀린_워크플로우는_실행하지_않는다():
    wf = runner.load_workflow("brand_agent")
    wf["nodes"].append({"id": "n5", "prompt": "p"})       # output_schema_id 없음
    wf["edges"].append({"from": "n4", "to": "n5"})
    run = runner.execute(wf, mock=True, input_payload=BRIEF)
    assert run["status"] == "failed"
    assert run["error"]["code"] == "INVALID_WORKFLOW"
    assert run["totals"]["input_tokens"] == 0             # 모델을 부르지 않았다


# --- 검증 엔드포인트 · 엑셀 입력 ---------------------------------------------

def test_검증_엔드포인트는_실행하지_않고_판정만_돌려준다():
    client = TestClient(app)
    wf = runner.load_workflow("brand_agent")
    ok = client.post("/v1/workflows/validate", json={"workflow": wf})
    assert ok.status_code == 200 and ok.json()["valid"] is True
    assert ok.json()["stats"]["longest_path"] == 4

    wf["edges"].append({"from": "n4", "to": "n1"})        # 순환
    bad = client.post("/v1/workflows/validate", json={"workflow": wf})
    assert bad.status_code == 200 and bad.json()["valid"] is False
    assert client.post("/v1/workflows/validate", json={}).status_code == 400


def test_엑셀은_common_table_payload_로_들어온다():
    from src import catalog
    from src.excel_to_text import excel_to_common_table

    table = excel_to_common_table("data/demo_scores.xlsx")
    catalog.parse("common.table@1", table)
    assert [c["label"] for c in table["columns"]][:1] == ["이름"]
    assert all(c["key"].startswith("col_") for c in table["columns"])
    assert len(table["rows"]) == 20
    assert json.dumps(table, ensure_ascii=False)          # 직렬화 가능


# --- 견적 (분기 조합 중 최댓값) ----------------------------------------------

def test_견적은_가장_비싼_분기_조합의_합이다():
    quote = graph.estimate(runner.load_workflow("brand_agent"))
    # n1 4.2 + n2 3.8 + n3a 4.5 + n4 5.0 = 17.5 (분기 노드 br1 은 과금하지 않는다)
    assert quote["max_charge_abc"] == 17.5
    assert "br1" not in quote["worst_case_nodes"]
    assert len(quote["worst_case_nodes"]) == 4
    assert quote["unpriced_nodes"] == []


def test_비싼_쪽_분기를_고른다():
    wf = runner.load_workflow("brand_agent")
    for node in wf["nodes"]:
        if node["id"] == "n3b":
            node["unit_price"] = 9.9          # 대중 공략 쪽이 비싸지면 견적도 그쪽
    quote = graph.estimate(wf)
    assert "n3b" in quote["worst_case_nodes"] and quote["max_charge_abc"] == 22.9


def test_단가가_없으면_견적에_표시된다():
    wf = runner.load_workflow("brand_agent")
    for node in wf["nodes"]:
        node.pop("unit_price", None)
    report = graph.validate(wf)
    assert report["estimate"]["max_charge_abc"] == 0.0
    assert set(report["estimate"]["unpriced_nodes"]) == {"n1", "n2", "n3a", "n4"}
    assert any(w["code"] == "UNPRICED_NODES" for w in report["warnings"])


def test_검증_응답에_견적이_실린다():
    client = TestClient(app)
    body = client.post("/v1/workflows/validate",
                       json={"workflow": runner.load_workflow("brand_agent")}).json()
    assert body["estimate"]["max_charge_abc"] == 17.5


# --- POST /v1/runs 가 입력 payload 를 받는다 (계약 §7.1) ----------------------

def test_실행_요청에_input_payload_를_실어_보낸다():
    client = TestClient(app)
    wf = runner.load_workflow("brand_agent")
    res = client.post("/v1/runs", data={"workflow": json.dumps(wf, ensure_ascii=False),
                                        "input": json.dumps(BRIEF, ensure_ascii=False),
                                        "comment": "API 경유", "mock": "true"})
    assert res.status_code == 202
    run_id = res.json()["run_id"]
    got = client.get(f"/v1/runs/{run_id}").json()
    assert got["status"] == "succeeded"
    status = {n["id"]: n["status"] for n in got["nodes"]}
    assert status["n3a"] == "succeeded" and status["n3b"] == "skipped"


def test_input_이_없으면_수요자_책임으로_실패한다():
    client = TestClient(app)
    wf = runner.load_workflow("brand_agent")
    res = client.post("/v1/runs", data={"workflow": json.dumps(wf, ensure_ascii=False), "mock": "true"})
    assert res.status_code == 202
    got = client.get(f"/v1/runs/{res.json()['run_id']}").json()
    assert got["status"] == "failed" and got["error"]["fault"] == "consumer"


def test_망가진_input_JSON_은_400_이다():
    client = TestClient(app)
    res = client.post("/v1/runs", data={"workflow": json.dumps(runner.load_workflow("brand_agent")),
                                        "input": "{not json", "mock": "true"})
    assert res.status_code == 400 and res.json()["detail"]["code"] == "INVALID_INPUT_JSON"


def test_정의가_틀리면_요청_시점에_400_이다():
    client = TestClient(app)
    wf = runner.load_workflow("brand_agent")
    wf["edges"].append({"from": "n4", "to": "n1"})       # 순환
    res = client.post("/v1/runs", data={"workflow": json.dumps(wf), "mock": "true"})
    assert res.status_code == 400 and res.json()["detail"]["code"] == "INVALID_WORKFLOW"
