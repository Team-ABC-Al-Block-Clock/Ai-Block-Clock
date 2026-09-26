"""판매자 사전 테스트 `mode=test` — 계약 v2.0 §7.1 · §8.3.

실행 경로 · 토큰 · USD · 결과물은 실제와 완전히 같고, 정산 입력(`contribution`)만 비어 온다.
"""
from __future__ import annotations

import json

from fastapi.testclient import TestClient

from src import runner
from src.api import app

BRIEF = {"brand_name": "초록소반", "category": "간편식", "target": "1인 가구 30대",
         "price_tier": "중가", "differentiator": "국내산 재료"}


def test_test_모드는_결과는_같고_정산_입력만_비운다():
    wf = runner.load_workflow("brand_agent")
    real = runner.execute(wf, mock=True, input_payload=BRIEF)
    test = runner.execute(wf, mock=True, input_payload=BRIEF, mode="test")
    assert real["status"] == test["status"] == "succeeded"
    assert real["mode"] == "run" and test["mode"] == "test"
    assert test["contribution"] == [] and len(real["contribution"]) == 4
    assert test["totals"] == real["totals"] and test["agents"] == real["agents"]     # 토큰 · USD 는 같다
    assert test["not_executed"] == real["not_executed"]
    assert test["result"]["envelope"]["schema_id"] == real["result"]["envelope"]["schema_id"]
    assert test["result"]["pdf"]


def test_API_는_mode_를_받고_되돌려준다():
    client = TestClient(app)
    res = client.post("/v1/runs", data={"workflow": json.dumps(runner.load_workflow("brand_agent"), ensure_ascii=False),
                                        "input": json.dumps(BRIEF, ensure_ascii=False),
                                        "mode": "test", "mock": "true"})
    assert res.status_code == 202 and res.json()["mode"] == "test"
    body = client.get(f"/v1/runs/{res.json()['run_id']}").json()
    assert body["status"] == "succeeded" and body["mode"] == "test"
    assert body["contribution"] == [] and body["totals"]["input_tokens"] > 0


def test_기본은_run_이고_잘못된_mode_는_400_이다():
    client = TestClient(app)
    wf = json.dumps(runner.load_workflow("brand_agent"), ensure_ascii=False)
    ok = client.post("/v1/runs", data={"workflow": wf, "input": json.dumps(BRIEF), "mock": "true"})
    assert ok.status_code == 202 and ok.json()["mode"] == "run"
    bad = client.post("/v1/runs", data={"workflow": wf, "input": json.dumps(BRIEF), "mode": "dry", "mock": "true"})
    assert bad.status_code == 400 and bad.json()["detail"]["code"] == "INVALID_MODE"
