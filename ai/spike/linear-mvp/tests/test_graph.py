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


def test_합류하는_경로들의_출력_형식이_달라도_된다():
    wf = runner.load_workflow("brand_agent")
    for node in wf["nodes"]:
        if node["id"] == "n3b":
            node["output_schema_id"] = "biz.campaign_plan@1"
    report = graph.validate(wf)
    assert report["valid"] and not any(e["code"] == "JOIN_SCHEMA_MISMATCH" for e in report["errors"])


def test_옛_필드는_무시하고_경고만_낸다():
    wf = {"nodes": [{"id": "n1", "prompt": "p", "input_schema_id": "biz.brand_brief@1",
                     "output_schema_id": "biz.market_research@1", "unit_price": 4.2}],
          "output": {"schema_id": "biz.market_research@1"}}
    report = graph.validate(wf)
    dep = [w for w in report["warnings"] if w["code"] == "DEPRECATED_FIELD"]
    assert report["valid"] and len(dep) == 2                     # 노드 1 + output.schema_id 1
    assert any("input_schema_id" in w["message"] and "output_schema_id" in w["message"]
               and w["node_id"] == "n1" for w in dep)
    assert any(w["node_id"] is None and "output.schema_id" in w["message"] for w in dep)
    assert graph.validate(runner.load_workflow("brand_agent"))["warnings"] == []   # 견본은 v2.0 형식


def test_분기_노드는_when_true_false_간선이_각각_하나여야_한다():
    wf = runner.load_workflow("brand_agent")
    wf["edges"] = [e for e in wf["edges"] if not (e["from"] == "br1" and e.get("when") is False)]
    codes = [e["code"] for e in graph.validate(wf)["errors"]]
    assert "BRANCH_EDGES_INVALID" in codes


def test_견본에_없는_형식은_거부한다():
    wf = {"nodes": [{"id": "a", "prompt": "p", "output": {"kind": "json", "sample": "biz.없는것@1"}}]}
    assert [e["code"] for e in graph.validate(wf)["errors"]] == ["UNKNOWN_SAMPLE"]
    wf = {"nodes": [{"id": "a", "prompt": "p", "input": {"sample": "biz.없는것@1"},
                     "output": {"kind": "text"}}]}
    assert [e["code"] for e in graph.validate(wf)["errors"]] == ["UNKNOWN_SAMPLE"]


def test_출력_선언이_없거나_틀리면_거부한다():
    assert [e["code"] for e in graph.validate({"nodes": [{"id": "a", "prompt": "p"}]})["errors"]] == ["MISSING_OUTPUT"]
    for bad in ({"kind": "pdf"}, {"kind": "json"}):
        wf = {"nodes": [{"id": "a", "prompt": "p", "output": bad}]}
        assert [e["code"] for e in graph.validate(wf)["errors"]] == ["OUTPUT_KIND_INVALID"], bad


def test_규모_상한을_넘으면_거부한다():
    nodes = [_text_node(f"n{i}") for i in range(9)]
    edges = [{"from": f"n{i}", "to": f"n{i+1}"} for i in range(8)]
    report = graph.validate({"nodes": nodes, "edges": edges})
    assert not report["valid"]
    assert any(e["code"] == "LIMIT_EXCEEDED" for e in report["errors"])


def test_output_schema_id_는_더_이상_검사하지_않고_경고만_낸다():
    wf = runner.load_workflow("brand_agent")
    wf["output"] = {"schema_id": "biz.ad_copy@1"}                # 종단(brand_strategy)과 달라도 오류가 아니다
    report = graph.validate(wf)
    assert report["valid"] and not any(e["code"] == "OUTPUT_SCHEMA_MISMATCH" for e in report["errors"])
    assert any(w["code"] == "DEPRECATED_FIELD" and "output.schema_id" in w["message"] for w in report["warnings"])


def test_글_출력_워크플로우는_경고_없이_통과한다():
    wf = runner.load_workflow("report")
    report = graph.validate(wf)
    assert report["valid"] and report["warnings"] == [] and report["errors"] == []
    assert all(graph.output_spec_of(n) == {"kind": "text", "sample": None} for n in wf["nodes"])


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
        {"node_id": "n3b", "reason": "branch_not_taken"}]          # 금액 없음(계약 v2.0)
    charged = {c["node_id"] for c in run["contribution"]}
    assert charged == {"n1", "n2", "n3a", "n4"}          # 분기 노드 br1 은 과금하지 않는다


def test_분기_조건이_없는_필드를_가리키면_저장_시점에_걸린다():
    wf = runner.load_workflow("brand_agent")
    for node in wf["nodes"]:
        if node["id"] == "br1":
            node["condition"] = {"left": "n1.없는필드", "op": "==", "right": "high"}
    report = graph.validate(wf)
    assert [e["code"] for e in report["errors"]] == ["BRANCH_REF_UNKNOWN_FIELD"]
    run = runner.execute(wf, mock=True, input_payload=BRIEF)
    assert run["status"] == "failed" and run["error"]["code"] == "INVALID_WORKFLOW"


def _with_condition(condition: dict, extra_nodes: list | None = None, extra_edges: list | None = None) -> dict:
    wf = runner.load_workflow("brand_agent")
    for node in wf["nodes"]:
        if node["id"] == "br1":
            node["condition"] = condition
    wf["nodes"] += extra_nodes or []
    wf["edges"] += extra_edges or []
    return wf


def test_분기는_선언된_스칼라_필드만_참조한다():
    ok = graph.validate(_with_condition({"left": "n1.competition_level", "op": "in", "right": ["high"]}))
    assert ok["valid"]
    for cond in ({"left": "n1.competitors", "op": "==", "right": 3},          # 배열
                 {"left": "n1.trends.0", "op": "==", "right": "x"},           # 배열 항목(객체)
                 {"left": "n4.headline", "op": "==", "right": "x"},           # 하위 노드 — 상위가 아니다
                 {"left": "br1.market.competition_level", "op": "==", "right": "high"},   # 분기 노드 직접
                 {"left": "n9.x", "op": "==", "right": 1}):                    # 없는 노드
        codes = [e["code"] for e in graph.validate(_with_condition(cond))["errors"]]
        assert codes == ["BRANCH_REF_UNKNOWN_FIELD"], cond


def test_글_출력_노드는_분기_기준이_될_수_없다():
    wf = _with_condition({"left": "memo.text", "op": "==", "right": "x"},
                         extra_nodes=[{"id": "memo", "prompt": "p", "output": {"kind": "text"}}],
                         extra_edges=[{"from": "memo", "to": "br1", "port": "memo"}])
    assert [e["code"] for e in graph.validate(wf)["errors"]] == ["BRANCH_ON_TEXT_OUTPUT"]


def test_필드_종류에_맞지_않는_연산자는_거부한다():
    bad = graph.validate(_with_condition({"left": "n1.competition_level", "op": ">", "right": "high"}))
    assert [e["code"] for e in bad["errors"]] == ["BRANCH_OP_TYPE_MISMATCH"]
    from src import catalog
    assert catalog.field_kind("biz.market_research@1", "competition_level") == "enum"
    assert catalog.field_kind("biz.market_research@1", "level_reason") == "string"
    assert catalog.field_kind("biz.market_research@1", "competitors") == "array"
    assert catalog.field_kind("biz.market_research@1", "competitors.0.name") == "string"
    assert catalog.field_kind("biz.market_research@1", "없는필드") is None


def test_전개된_에이전트_안의_분기도_접두_경로로_검사된다():
    report = graph.validate(runner.load_workflow("brand_campaign"))
    assert report["valid"] and report["errors"] == []


def test_형식이_다른_노드도_이어지고_끝까지_돈다():
    wf = runner.load_workflow("brand_agent")
    wf["nodes"].append({"id": "n5", "name": "법률 검토", "prompt": "앞 결과의 브랜드 전략을 검토한다",
                        "input_schema_id": "biz.ad_copy@1",             # 옛 필드 — 형식이 달라도 무시된다
                        "output_schema_id": "biz.campaign_plan@1", "owner_id": "u_seller_d"})
    wf["edges"].append({"from": "n4", "to": "n5"})
    report = graph.validate(wf)
    assert report["valid"], report["errors"]
    assert any(w["code"] == "DEPRECATED_FIELD" and w["node_id"] == "n5" for w in report["warnings"])
    run = runner.execute(wf, mock=True, input_payload=BRIEF)
    assert run["status"] == "succeeded"
    assert run["result"]["envelope"]["schema_id"] == "biz.campaign_plan@1"
    assert {c["owner_id"] for c in run["contribution"]} == {"u_seller_a", "u_seller_d"}


def test_입력_블록은_절마다_출처와_형식을_단다():
    g = graph.build(runner.load_workflow("brand_agent"))
    merged = {"market": {"competition_level": "high"}, "product": {"strengths": []}}
    payloads = {"n1": merged["market"], "n2": merged["product"], "br1": merged}
    sources = graph.fan_in_sources(g, "n3a", payloads, {"br1": True})
    assert sources == [("n1", "market"), ("n2", "product")]           # 분기를 통과해 원천이 보인다
    msg = runner.build_input_message(runner.input_sections(g, sources, merged, None, ""), "코멘트")
    assert msg.startswith("## 입력\n### market — ") and "(n1 · 시장 조사 결과)" in msg
    assert "### product — " in msg and "(n2 · 제품 분석 결과)" in msg
    assert '"competition_level": "high"' in msg and msg.endswith("## 사용자 코멘트\n코멘트")

    single = graph.fan_in_sources(g, "n4", {"n3a": {"approach": "차별화"}}, {"br1": True})
    assert single == [("n3a", None)]
    one = runner.build_input_message(runner.input_sections(g, single, {"approach": "차별화"}, None, ""), "")
    assert one.count("### ") == 1 and "(n3a · 전략 초안)" in one
    assert runner.build_input_message([("메모", {"text": "본문 그대로"})], "") == "## 입력\n### 메모\n본문 그대로"


def test_앞_결과가_상한을_넘으면_platform_책임으로_멈춘다(monkeypatch):
    monkeypatch.setattr(runner, "MAX_INPUT_CHARS", 50)
    run = _uc1()
    assert run["status"] == "failed" and run["error"]["code"] == "INPUT_TOO_LARGE"
    assert run["error"]["fault"] == "platform" and run["error"]["affects_success_rate"] is False
    assert set(run["failure"]["refund_scope"]["refund_node_ids"]) == {n["id"] for n in run["nodes"]}


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


# --- 견적 (분기 조합 중 노드가 가장 많은 경우 · 금액 없음, 계약 v2.0 §7.5) ---------

def test_견적은_가장_많은_노드가_도는_분기_조합의_목록이다():
    quote = graph.estimate(runner.load_workflow("brand_agent"))
    assert quote == {"worst_case_nodes": ["n1", "n2", "n3a", "n4"], "worst_case_node_count": 4}
    assert "br1" not in quote["worst_case_nodes"]                 # 분기 노드는 돈 노드가 아니다
    assert "max_charge_abc" not in quote and "unpriced_nodes" not in quote


def test_노드가_더_많이_도는_쪽_분기를_고른다():
    wf = runner.load_workflow("brand_agent")
    wf["nodes"].append({"id": "n3c", "prompt": "p", "output": {"kind": "text"}})   # 대중 공략 뒤에 한 단계 더
    wf["edges"] = [e for e in wf["edges"] if e != {"from": "n3b", "to": "n4"}]
    wf["edges"] += [{"from": "n3b", "to": "n3c"}, {"from": "n3c", "to": "n4"}]
    quote = graph.estimate(wf)
    assert "n3b" in quote["worst_case_nodes"] and "n3c" in quote["worst_case_nodes"]
    assert quote["worst_case_node_count"] == 5


def test_검증_응답에_도는_노드_목록이_실린다():
    client = TestClient(app)
    body = client.post("/v1/workflows/validate",
                       json={"workflow": runner.load_workflow("brand_agent")}).json()
    assert body["estimate"] == {"worst_case_nodes": ["n1", "n2", "n3a", "n4"], "worst_case_node_count": 4}
    assert not any(w["code"] == "UNPRICED_NODES" for w in body["warnings"])


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
