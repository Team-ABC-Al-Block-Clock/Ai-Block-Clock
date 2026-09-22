"""에이전트 전개 테스트 — 2단계 관문.

사용자가 서로 다른 판매자의 에이전트를 엮은 워크플로우가 끝까지 돌고, 한 실행에
판매자마다 정산이 쌓인다. 사수 확인 항목 "한 실행에 여러 계정 정산"이 여기서 성립한다.
"""
from __future__ import annotations

import json
from collections import defaultdict

import pytest
from fastapi.testclient import TestClient

from src import expand, graph, runner
from src.api import app

BRIEF = {
    "brand_name": "초록소반",
    "category": "간편식",
    "target": "1인 가구 30대",
    "price_tier": "중가",
    "differentiator": "국내산 재료",
}


def _wf() -> dict:
    return runner.load_workflow("brand_campaign")


def _run(**kw) -> dict:
    return runner.execute(_wf(), comment="테스트", mock=True, input_payload=BRIEF, **kw)


# --- 전개 --------------------------------------------------------------------

def test_에이전트_4개가_노드_9개로_펼쳐진다():
    flat, stats = expand.expand(_wf())
    assert stats == {"canvas_node_count": 4, "expanded_node_count": 9, "agent_depth": 1}
    assert [n["id"] for n in flat["nodes"]] == [
        "a1.n1", "a1.n2", "a1.br1", "a1.n3a", "a1.n3b", "a1.n4", "a2.c1", "a3.k1", "a4.e1"]


def test_들어오는_엣지는_내부_진입_노드로_나가는_엣지는_종단_노드로_붙는다():
    flat, _ = expand.expand(_wf())
    pairs = {(e["from"], e["to"]) for e in flat["edges"]}
    assert ("a1.n4", "a2.c1") in pairs          # a1 → a2 가 종단 → 진입으로
    assert ("a1.n4", "a3.k1") in pairs
    assert ("a2.c1", "a4.e1") in pairs
    ports = {(e["from"], e.get("port")) for e in flat["edges"] if e["to"] == "a4.e1"}
    assert ports == {("a2.c1", "campaign"), ("a3.k1", "copy")}


def test_내부_노드는_에이전트_소유자와_agent_id_를_물려받는다():
    flat, _ = expand.expand(_wf())
    owners = {n["id"]: (n["owner_id"], n["agent_id"]) for n in flat["nodes"]}
    assert owners["a1.n1"] == ("u_seller_a", "ag_brand_strategy")
    assert owners["a2.c1"] == ("u_seller_b", "ag_sns_campaign")
    assert owners["a3.k1"] == ("u_seller_c", "ag_ad_copy")


def test_내부_분기_조건도_접두를_따라간다():
    flat, _ = expand.expand(_wf())
    branch = next(n for n in flat["nodes"] if n["id"] == "a1.br1")
    assert branch["condition"]["left"] == "a1.n1.competition_level"


def test_종단_노드에_경계_표시가_남는다():
    flat, _ = expand.expand(_wf())
    boundary = expand.boundary_of(next(n for n in flat["nodes"] if n["id"] == "a1.n4"))
    assert boundary["schema_id"] == "biz.brand_strategy@1"
    assert expand.boundary_of(next(n for n in flat["nodes"] if n["id"] == "a1.n1")) is None


def test_에이전트_안의_에이전트는_거부한다():
    wf = _wf()
    wf["nodes"][0]["agent"]["nodes"].append({"id": "x", "type": "agent", "agent": {}})
    with pytest.raises(expand.ExpandError) as e:
        expand.expand(wf)
    assert e.value.code == "AGENT_NESTING_TOO_DEEP"


# --- 검증 · 견적 --------------------------------------------------------------

def test_상한은_캔버스_기준_전개_기준을_따로_센다():
    report = graph.validate(_wf())
    assert report["valid"]
    assert report["stats"] == {"node_count": 4, "expanded_node_count": 9,
                               "longest_path": 6, "agent_depth": 1}


def test_공개한_출력_형식과_내부_종단이_다르면_경계_위반이다():
    wf = _wf()
    wf["nodes"][1]["agent"]["output_schema_id"] = "biz.ad_copy@1"   # a2 가 거짓 공개
    codes = [e["code"] for e in graph.validate(wf)["errors"]]
    assert "AGENT_BOUNDARY_VIOLATION" in codes


def test_견적은_전개_후_노드로_계산한다():
    quote = graph.estimate(_wf())
    assert quote["max_charge_abc"] == 31.1
    assert "a1.n3b" not in quote["worst_case_nodes"]


# --- 실행: 2단계 관문 ---------------------------------------------------------

def test_세_판매자의_에이전트가_한_실행으로_돈다():
    run = _run()
    assert run["status"] == "succeeded"
    assert run["result"]["envelope"]["schema_id"] == "biz.execution_plan@1"
    assert run["result"]["pdf"]


def test_한_실행에_계정_세_개로_정산이_쌓인다():
    run = _run()
    per = defaultdict(float)
    for c in run["contribution"]:
        per[c["owner_id"]] += c["charged_abc"]
    assert dict(per) == {"u_seller_a": 17.5, "u_seller_b": 10.0, "u_seller_c": 3.6}
    assert round(sum(per.values()), 2) == 31.1        # 견적과 같다


def test_에이전트_안의_분기도_그대로_동작한다():
    high = {n["id"]: n["status"] for n in _run()["nodes"]}
    assert high["a1.n3a"] == "succeeded" and high["a1.n3b"] == "skipped"

    low = {n["id"]: n["status"] for n in
           _run(mock_overrides={"biz.market_research@1": {"competition_level": "low"}})["nodes"]}
    assert low["a1.n3b"] == "succeeded" and low["a1.n3a"] == "skipped"


def test_안_간_내부_노드도_환원_대상이다():
    run = _run()
    assert run["not_executed"] == [
        {"node_id": "a1.n3b", "reason": "branch_not_taken", "refund_abc": 4.5}]


# --- /artifact ---------------------------------------------------------------

def test_결과물은_봉투_JSON_으로_받는다():
    client = TestClient(app)
    res = client.post("/v1/runs", data={"workflow": json.dumps(_wf(), ensure_ascii=False),
                                        "input": json.dumps(BRIEF, ensure_ascii=False),
                                        "mock": "true"})
    run_id = res.json()["run_id"]
    artifact = client.get(f"/v1/runs/{run_id}/artifact")
    assert artifact.status_code == 200
    body = artifact.json()
    assert body["schema_id"] == "biz.execution_plan@1"
    assert body["a2a_version"] == "1.0" and body["error"] is None
    assert body["payload"]["phases"]


def test_없는_실행의_결과물은_404_다():
    assert TestClient(app).get("/v1/runs/없는것/artifact").status_code == 404


def test_실패한_실행의_결과물은_409_다():
    client = TestClient(app)
    res = client.post("/v1/runs", data={"workflow": json.dumps(_wf(), ensure_ascii=False),
                                        "mock": "true"})          # input 없음 → 수요자 책임 실패
    run_id = res.json()["run_id"]
    assert client.get(f"/v1/runs/{run_id}/artifact").status_code == 409
