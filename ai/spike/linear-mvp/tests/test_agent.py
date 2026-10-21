"""에이전트 전개 테스트 — 2단계 관문.

사용자가 서로 다른 판매자의 에이전트를 엮은 워크플로우가 끝까지 돌고, 한 실행에
판매자마다 정산이 쌓인다. 사수 확인 항목 "한 실행에 여러 계정 정산"이 여기서 성립한다.
"""
from __future__ import annotations

from collections import defaultdict

import pytest
from fastapi.testclient import TestClient

from src import expand, graph, runner, shape
from src.api import app
from tests.messaging_support import submit, submit_message

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


def test_에이전트_공개_입출력은_내부_진입_종단_노드에서_파생된다():
    a1, a2, a4 = _wf()["nodes"][0], _wf()["nodes"][1], _wf()["nodes"][3]
    assert expand.public_io(a1) == {"input": {"example": shape.LEGACY_EXAMPLES["biz.brand_brief@1"]},
                                    "output": {"kind": "json", "example": shape.LEGACY_EXAMPLES["biz.brand_strategy@1"]}}
    assert expand.public_io(a2) == {"input": None,                       # 진입 노드에 입력 예시가 없다 = 자유 입력
                                    "output": {"kind": "json", "example": shape.LEGACY_EXAMPLES["biz.campaign_plan@1"]}}
    assert expand.public_io(a4)["output"] == {"kind": "json", "example": shape.LEGACY_EXAMPLES["biz.execution_plan@1"]}


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


def test_에이전트에_옛_공개_형식_필드가_오면_무시하고_경고만_낸다():
    wf = _wf()
    wf["nodes"][1]["agent"]["output_schema_id"] = "biz.ad_copy@1"   # v1.x 필드 — 파생값과 달라도 오류가 아니다
    report = graph.validate(wf)
    assert report["valid"] and not any(e["code"] == "AGENT_BOUNDARY_VIOLATION" for e in report["errors"])
    assert any(w["code"] == "DEPRECATED_FIELD" and w["node_id"] == "a2" for w in report["warnings"])


def test_견적은_전개_후_노드로_계산한다():
    quote = graph.estimate(_wf())
    assert quote["worst_case_node_count"] == 7                       # 9 노드 − 분기 1 − 안 간 쪽 1
    assert "a1.n3b" not in quote["worst_case_nodes"] and "a1.br1" not in quote["worst_case_nodes"]
    assert quote["worst_case_nodes"][-1] == "a4.e1"


# --- 실행: 2단계 관문 ---------------------------------------------------------

def test_세_판매자의_에이전트가_한_실행으로_돈다():
    run = _run()
    assert run["status"] == "succeeded"
    assert run["result"]["envelope"]["schema_id"] == "json"
    assert run["result"]["pdf"]


def test_한_실행에_계정_세_개로_정산이_쌓인다():
    run = _run()
    per = defaultdict(int)
    for c in run["contribution"]:
        per[c["owner_id"]] += 1                        # 금액은 없다 — 성공 노드의 귀속만(계약 v2.0 §8.2)
        assert set(c) == {"node_id", "owner_id", "agent_id"}
    assert dict(per) == {"u_seller_a": 4, "u_seller_b": 2, "u_seller_c": 1}
    owners = {a["owner_id"] for a in run["agents"]}
    assert owners == {"u_seller_a", "u_seller_b", "u_seller_c"}   # 에이전트별 사용량도 세 계정


def test_에이전트_안의_분기도_그대로_동작한다():
    high = {n["id"]: n["status"] for n in _run()["nodes"]}
    assert high["a1.n3a"] == "succeeded" and high["a1.n3b"] == "skipped"

    low = {n["id"]: n["status"] for n in
           _run(mock_overrides={"a1.n1.competition_level": "low"})["nodes"]}
    assert low["a1.n3b"] == "succeeded" and low["a1.n3a"] == "skipped"


def test_안_간_내부_노드도_환원_대상이다():
    run = _run()
    assert run["not_executed"] == [
        {"node_id": "a1.n3b", "reason": "branch_not_taken"}]


# --- /artifact ---------------------------------------------------------------

def test_결과물은_봉투_JSON_으로_받는다():
    client = TestClient(app)
    message = submit_message(_wf(), input=BRIEF)
    run_id = message["execution_id"]

    outcome, _ = submit(message)

    assert outcome == "ack"
    artifact = client.get(f"/v1/runs/{run_id}/artifact")
    assert artifact.status_code == 200
    body = artifact.json()
    assert body["schema_id"] == "json"
    assert body["a2a_version"] == "2.0" and body["error"] is None
    assert body["payload"]["phases"]


def test_없는_실행의_결과물은_404_다():
    assert TestClient(app).get("/v1/runs/없는것/artifact").status_code == 404


def test_실패한_실행의_결과물은_409_다():
    client = TestClient(app)
    message = submit_message(_wf())                                  # input 없음 → 수요자 책임 실패
    run_id = message["execution_id"]

    outcome, publisher = submit(message)

    assert outcome == "ack" and publisher.types()[-1] == "failed"
    assert client.get(f"/v1/runs/{run_id}/artifact").status_code == 409
