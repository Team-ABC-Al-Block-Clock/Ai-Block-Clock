"""폴링 응답이 계약 §7.2 와 같은 모양인지 — 백엔드가 문서만 보고 붙일 수 있어야 한다.

문서에 있는 필드가 응답에 없으면 백엔드는 없는 키를 기다린다. 필드를 더하거나 빼면
이 테스트와 `ai/docs/integration-guide.md` §7.2 를 같은 MR 에서 고친다.
"""
from __future__ import annotations

from fastapi.testclient import TestClient

from src import runner, shape
from src.api import app
from tests.messaging_support import submit, submit_message

BRIEF = {"brand_name": "초록소반", "category": "간편식", "target": "1인 가구 30대",
         "price_tier": "중가", "differentiator": "국내산 재료"}

NODE_KEYS = {"id", "name", "type", "status", "output", "agent_id", "owner_id",
             "started_at", "finished_at", "duration_ms", "input_tokens", "output_tokens",
             "retries", "repair_count", "model", "cost_usd", "skip_reason", "branch_taken"}
TOP_KEYS = {"run_id", "status", "mode", "workflow", "mock", "progress", "nodes", "agents", "totals", "started_at",
            "finished_at", "total_duration_ms", "contribution", "not_executed", "failure",
            "result", "warnings", "error"}


def _run(workflow: str = "brand_agent") -> dict:
    """SubmitRun 메시지로 실행을 만들고 HTTP 로 조회한다 — 조회 응답 모양은 메시지 경로에서도 같다."""
    message = submit_message(runner.load_workflow(workflow), input=BRIEF)
    outcome, _ = submit(message)
    assert outcome == "ack"
    return TestClient(app).get(f"/v1/runs/{message['execution_id']}").json()


def test_최상위_키가_계약과_같다():
    assert set(_run()) == TOP_KEYS


def test_노드마다_계약_필드가_전부_있다():
    body = _run()
    for node in body["nodes"]:
        assert NODE_KEYS <= set(node), f"{node['id']} 에 빠진 키: {NODE_KEYS - set(node)}"
        assert "output_payload" not in node    # 전문은 include_node_output=true 일 때만
        assert "prompt" not in node            # 프롬프트 원문은 어디에도 내지 않는다(SEC-001)


def test_분기_노드는_type_branch_에_판정_결과가_실린다():
    body = _run()
    branch = next(n for n in body["nodes"] if n["id"] == "br1")
    assert branch["type"] == "branch" and branch["branch_taken"] is True
    assert branch["cost_usd"] == 0.0 and branch["model"] is None


def test_건너뛴_노드는_skipped_와_이유가_실린다():
    body = _run()
    skipped = next(n for n in body["nodes"] if n["id"] == "n3b")
    assert skipped["status"] == "skipped" and skipped["skip_reason"] == "branch_not_taken"
    assert body["not_executed"] == [{"node_id": "n3b", "reason": "branch_not_taken"}]   # 금액 없음(v2.0)


def test_진행률은_돌아야_하는_LLM_노드_기준이다():
    body = _run()
    # UC1 6노드 중 분기 1 · 건너뜀 1 을 빼면 4개가 돌았다
    assert body["progress"] == {"done": 4, "total": 4, "percent": 100,
                                "current_index": None, "current_node_id": None, "current_node_name": None}


def test_완료_결과는_URL_로_안내한다():
    body = _run()
    assert body["result"] == {"type": "pdf", "output": {"kind": "json", "example": shape.LEGACY_EXAMPLES["biz.brand_strategy@1"]},
                              "artifact_url": f"/v1/runs/{body['run_id']}/artifact",
                              "html_url": f"/v1/runs/{body['run_id']}/html",
                              "pdf_url": f"/v1/runs/{body['run_id']}/pdf",
                              "template": {"applied": "default", "name": "document", "fallback_reason": None, "mapping": {}}}


def test_totals_에_원가_합계가_있다():
    assert set(_run()["totals"]) == {"input_tokens", "output_tokens", "cost_usd"}


def test_agents_는_판매자별_토큰과_USD_합계다():
    body = _run("brand_campaign")
    agents = {a["agent_id"]: a for a in body["agents"]}
    assert set(agents) == {"ag_brand_strategy", "ag_sns_campaign", "ag_ad_copy", "ag_execution_plan"}
    assert agents["ag_brand_strategy"]["owner_id"] == "u_seller_a"
    assert agents["ag_brand_strategy"]["node_ids"] == ["a1.n1", "a1.n2", "a1.n3a", "a1.n3b", "a1.n4"]   # 분기 노드(a1.br1) 제외
    assert set(agents["ag_ad_copy"]) == {"agent_id", "owner_id", "node_ids", "input_tokens", "output_tokens", "cost_usd"}
    assert sum(a["cost_usd"] for a in body["agents"]) == body["totals"]["cost_usd"]
    assert "cost_abc" not in body["totals"] and all("charged_abc" not in c for c in body["contribution"])


def test_에이전트_노드는_agent_id_로_묶인다():
    body = _run("brand_campaign")
    ids = {n["agent_id"] for n in body["nodes"]}
    assert ids == {"ag_brand_strategy", "ag_sns_campaign", "ag_ad_copy", "ag_execution_plan"}
    assert all(c["agent_id"] for c in body["contribution"])
