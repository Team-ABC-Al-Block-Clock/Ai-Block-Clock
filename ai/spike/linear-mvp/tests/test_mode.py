"""판매자 사전 테스트 `mode=test` — 계약 v2.0 §7.1 · §8.3.

실행 경로 · 토큰 · USD · 결과물은 실제와 완전히 같고, 정산 입력(`contribution`)만 비어 온다.
"""
from __future__ import annotations

from fastapi.testclient import TestClient

from src import runner
from src.api import app
from tests.messaging_support import submit, submit_message

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


def test_메시지의_mode_를_이벤트와_조회에_그대로_되돌려준다():
    client = TestClient(app)
    message = submit_message(runner.load_workflow("brand_agent"), input=BRIEF, mode="test")

    outcome, publisher = submit(message)

    assert outcome == "ack"
    assert {event["mode"] for event in publisher.events} == {"test"}
    body = client.get(f"/v1/runs/{message['execution_id']}").json()
    assert body["status"] == "succeeded" and body["mode"] == "test"
    assert body["contribution"] == [] and body["totals"]["input_tokens"] > 0


def test_mode_run_은_run_으로_돌고_잘못된_mode_는_rejected_INVALID_MODE_다():
    client = TestClient(app)
    wf = runner.load_workflow("brand_agent")
    ok = submit_message(wf, input=BRIEF, mode="run")
    bad = submit_message(wf, input=BRIEF, mode="dry")

    ok_outcome, ok_publisher = submit(ok)
    bad_outcome, bad_publisher = submit(bad)

    assert ok_outcome == "ack" and {event["mode"] for event in ok_publisher.events} == {"run"}
    assert client.get(f"/v1/runs/{ok['execution_id']}").json()["mode"] == "run"
    assert bad_outcome == "ack" and bad_publisher.types() == ["rejected"]
    assert bad_publisher.last()["snapshot"]["error"]["code"] == "INVALID_MODE"
