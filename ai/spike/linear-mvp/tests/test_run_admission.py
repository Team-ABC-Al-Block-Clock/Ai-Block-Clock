"""취소 · 보관 — 운영 도구로 남은 HTTP 취소가 메시지로 받은 실행에도 듣는지, 끝난 실행을 비우는지.

접수는 HTTP 가 아니라 SubmitRun 메시지다(ADR-0001). 같은 실행을 두 번 돌리지 않는 멱등 · 동시 실행 상한은
`client_run_id` · 503 `ENGINE_BUSY` 대신 `test_messaging.py`(R13 · R14 · R20)가 확인한다.
"""
from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from src import api, messaging, runner
from tests.messaging_support import (BRIEF, FakePublisher, GatedLLM, Worker, encode, submit, submit_message,
                                     two_step_workflow, wait_for)


@pytest.fixture()
def client():
    return TestClient(api.app)


def test_끝난_실행은_execution_id_로_찾고_취소할_것이_없다(client):
    message = submit_message(runner.load_workflow("brand_agent"), input=BRIEF)
    run_id = message["execution_id"]
    submit(message)

    res = client.post(f"/v1/runs/{run_id}/cancel")

    assert res.status_code == 200
    assert res.json() == {"run_id": run_id, "status": "succeeded", "cancel_requested": False}


def test_모르는_실행은_취소할_수_없다(client):
    assert client.post("/v1/runs/nope/cancel").status_code == 404


def test_HTTP_취소도_메시지로_받은_도는_실행을_다음_노드부터_멈춘다(client, monkeypatch):
    llm = GatedLLM()
    monkeypatch.setattr(runner, "LLM", llm.factory)
    message = submit_message(two_step_workflow(), mock=False)
    run_id = message["execution_id"]
    publisher = FakePublisher()
    worker = Worker(lambda: messaging.handle_submit(encode(message), 0, publisher))

    worker.start()
    try:
        wait_for(lambda: llm.entered.is_set() or not worker.is_alive())
        assert llm.entered.is_set(), f"실행이 시작되지 않았다: {worker.result!r} {worker.error!r}"
        res = client.post(f"/v1/runs/{run_id}/cancel")
    finally:
        llm.gate.set()
    worker.join(20)

    assert res.status_code == 200 and res.json()["cancel_requested"] is True
    assert worker.error is None and worker.result == "ack"
    assert publisher.last()["type"] == "failed"
    assert publisher.last()["snapshot"]["error"]["code"] == "CANCELLED"
    assert llm.calls == 1


def test_취소_표시가_있으면_다음_노드부터_돌지_않고_플랫폼_탓으로_끝난다():
    state = {"cancel_requested": True}

    run = runner.execute(runner.load_workflow("brand_agent"), mock=True, input_payload=BRIEF, state=state)

    assert run["status"] == "failed"
    assert run["error"]["code"] == "CANCELLED"
    assert run["failure"]["fault"] == "platform"
    assert run["contribution"] == []                     # 아무 노드도 돌지 않았다
    assert all(n["status"] == "pending" for n in run["nodes"])


def test_보관_기한이_지난_끝난_실행은_지운다(client, monkeypatch, volumes):
    message = submit_message(runner.load_workflow("brand_agent"), input=BRIEF)
    run_id = message["execution_id"]
    submit(message)
    monkeypatch.setattr(api, "RUN_RETENTION_SECONDS", -1)

    assert client.get(f"/v1/runs/{run_id}").status_code == 404
    assert not (volumes.results / run_id).exists()
