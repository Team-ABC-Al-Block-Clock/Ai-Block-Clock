"""JSON 경로 통합 테스트 — UC1 이 봉투로 완주하는지, 실패 책임이 갈리는지.

09-19 관문(2노드 JSON 완주)을 09-22 에 UC1 5노드로 옮겼다 — 트랙 B 프롬프트는 5노드 사슬을
전제로 쓰여서(04 는 전략 초안을 받는다) 2노드 축소판이 성립하지 않기 때문이다.
기존 텍스트 경로 회귀도 함께 지킨다.
"""
from __future__ import annotations

import json

from fastapi.testclient import TestClient

from src import errors, runner
from src.api import app

BRIEF = {
    "brand_name": "초록소반",
    "category": "간편식",
    "target": "1인 가구 30대",
    "price_tier": "중가",
    "differentiator": "국내산 재료",
}


def _run(input_payload=BRIEF, workflow="brand_agent"):
    return runner.execute(runner.load_workflow(workflow), comment="테스트",
                          mock=True, input_payload=input_payload)


# --- JSON 경로 --------------------------------------------------------------

def test_UC1_이_JSON_으로_완주하고_봉투가_나온다():
    run = _run()
    assert run["status"] == "succeeded"
    assert [n["schema_id"] for n in run["nodes"]] == [
        "biz.market_research@1", "biz.product_analysis@1", None,
        "biz.strategy_draft@1", "biz.strategy_draft@1", "biz.brand_strategy@1"]
    env = run["result"]["envelope"]
    assert env["a2a_version"] == "1.0"
    assert env["schema_id"] == "biz.brand_strategy@1"
    assert env["error"] is None and env["meta"]["repair_count"] == 0


def test_발행일과_작성기관은_Runtime_이_채운다():
    payload = _run()["result"]["envelope"]["payload"]
    assert payload["author_org"] == "김전략 브랜딩"      # 노드의 owner_name
    assert payload["issued_on"].startswith("2026년")


def test_실행된_노드마다_정산_기여가_쌓인다():
    run = _run()
    assert [c["node_id"] for c in run["contribution"]] == ["n1", "n2", "n3a", "n4"]
    assert all(c["owner_id"] == "u_seller_a" for c in run["contribution"])


def test_결과_PDF_가_만들어진다():
    assert _run()["result"]["pdf"]


# --- 실패 책임 --------------------------------------------------------------

def test_입력이_형식과_다르면_수요자_책임으로_멈춘다():
    run = _run({**BRIEF, "price_tier": "초고가"})
    assert run["status"] == "failed"
    assert run["error"]["code"] == "SCHEMA_VALIDATION_FAILED"
    assert run["error"]["fault"] == "consumer"
    assert run["error"]["affects_success_rate"] is False
    assert "price_tier" in run["error"]["detail"]          # 사유는 detail 에만
    assert run["failure"]["refund_scope"]["settled_node_ids"] == []


def test_입력값이_아예_없으면_실행하지_않는다():
    run = _run(None)
    assert run["status"] == "failed" and run["error"]["fault"] == "consumer"
    assert run["totals"]["input_tokens"] == 0             # 모델을 부르지 않았다


def test_오류_문구는_같은_코드라도_책임에_따라_다르다():
    consumer = errors.describe("SCHEMA_VALIDATION_FAILED", fault="consumer")
    supplier = errors.describe("SCHEMA_VALIDATION_FAILED")
    assert consumer["message"] != supplier["message"]
    assert consumer["affects_success_rate"] is False and supplier["affects_success_rate"] is True


# --- 회귀: 기존 텍스트 경로 --------------------------------------------------

def test_스키마가_없는_워크플로우는_기존_텍스트_경로로_돈다():
    run = runner.execute(runner.load_workflow("report"), comment="회귀", mock=True)
    assert run["status"] == "succeeded"
    assert run["result"].get("envelope") is None
    assert all(n["schema_id"] is None for n in run["nodes"])


# --- 스키마 API -------------------------------------------------------------

def test_스키마_목록과_전문을_API_로_받는다():
    client = TestClient(app)
    listing = client.get("/v1/schemas")
    assert listing.status_code == 200 and len(listing.json()["schemas"]) == 11
    one = client.get("/v1/schemas/biz.brand_brief@1")
    assert one.status_code == 200 and one.json()["title"] == "브랜드 기획 요청서"
    assert client.get("/v1/schemas/biz.없는것@1").status_code == 404


# --- 실측 원가 (NOD-007 · EXE-013) -------------------------------------------

def test_실측_원가는_토큰과_단가에서_결정적으로_나온다():
    from src import pricing
    # 가이드 §11.5 — Σ(입력 토큰 × 입력 단가 + 출력 토큰 × 출력 단가) × ABC 환산율
    assert pricing.cost_usd("gemini-2.5-flash", 1_000_000, 0) == 0.30
    assert pricing.cost_usd("gemini-2.5-flash", 0, 1_000_000) == 2.50
    assert pricing.cost_abc("gemini-2.5-flash", 1_000_000, 0) == 300.0
    assert pricing.cost_abc("mock", 9999, 9999) == 0.0        # 모델을 부르지 않았다


def test_봉투와_합계에_원가_자리가_채워진다():
    run = _run()
    assert run["totals"]["cost_abc"] == 0.0                   # mock 이라 0
    assert all(n["cost_abc"] == 0.0 for n in run["nodes"] if n["status"] == "succeeded")
    assert run["result"]["envelope"]["meta"]["cost_abc"] == 0.0


def test_교정_재요청분_토큰도_원가에_들어간다():
    import json as _json

    from src import catalog, nodes
    from tests.test_nodes import FakeLLM, _market

    bad = _json.dumps(_market(competition_level="medium"), ensure_ascii=False)
    good = _json.dumps(_market(), ensure_ascii=False)
    run = nodes.run_llm_node(FakeLLM(bad, good), run_id="r", node_id="n1",
                             schema_id="biz.market_research@1", system="s", message="m")
    assert run.ok and run.repair_count == 1
    meta = run.envelope["meta"]
    assert meta["usage"]["input_tokens"] == 20 and meta["usage"]["output_tokens"] == 40   # 2회 합산
    assert catalog.CATALOG_VERSION
