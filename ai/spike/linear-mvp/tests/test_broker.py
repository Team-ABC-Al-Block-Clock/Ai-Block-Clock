"""브로커 연결 — 접속 설정 · 기동 · 다시 붙기 · confirm · ack · 스레드 넘김 (runtime-spec R23 · R24).

앞의 두 테스트는 브로커 없이 돈다. 연결을 받자마자 끊는 가짜 TCP 포트로 런타임이 어디에 · 몇 번 붙으려 하는지만 본다.
`broker` 표시가 붙은 테스트는 진짜 RabbitMQ 가 있어야 한다 — 환경 변수 RABBITMQ_TEST_URL
(`amqp://user:pass@host:port/vhost`, 예: `amqp://guest:guest@localhost:5672/%2F`)이 없으면 건너뛴다.
    RABBITMQ_TEST_URL=amqp://guest:guest@localhost:5672/%2F uv run pytest -m broker

런타임은 FastAPI lifespan 에서 브로커에 붙으므로 `with TestClient(app)` 로 띄운다. 접속 설정은 기동할 때 환경 변수에서 읽는다.
"""
from __future__ import annotations

import asyncio
import json
import os
import socket
import threading
import time
import uuid
from urllib.parse import unquote, urlparse

import aio_pika
import pytest
from fastapi.testclient import TestClient

from src import messaging, runner
from src.api import app
from tests.messaging_support import GatedLLM, Worker, encode, submit_message, two_step_workflow, wait_for

BROKER_URL = os.getenv("RABBITMQ_TEST_URL")
needs_broker = pytest.mark.skipif(not BROKER_URL, reason="RABBITMQ_TEST_URL 이 없다 — 진짜 RabbitMQ 가 필요하다")
FINAL = {"succeeded", "failed", "rejected"}


def _broker_env(monkeypatch, *, port: int, host: str = "127.0.0.1", username: str = "guest",
                password: str = "guest", vhost: str = "/", enabled: str = "true", max_active: int = 4) -> None:
    monkeypatch.setenv("BROKER_ENABLED", enabled)
    monkeypatch.setenv("RABBITMQ_HOST", host)
    monkeypatch.setenv("RABBITMQ_PORT", str(port))
    monkeypatch.setenv("RABBITMQ_USERNAME", username)
    monkeypatch.setenv("RABBITMQ_PASSWORD", password)
    monkeypatch.setenv("RABBITMQ_VHOST", vhost)
    monkeypatch.setenv("MAX_ACTIVE_RUNS", str(max_active))


def _run_app(check, timeout: float = 30.0):
    """lifespan 을 돌린 TestClient 로 `check(client)` 를 부른다. 기동 · 종료가 막히면 실패한다.

    돌려주는 값은 `(기동에 걸린 초, check 의 결과)`.
    """
    def body():
        started = time.monotonic()
        with TestClient(app) as client:
            startup_seconds = time.monotonic() - started
            return startup_seconds, check(client)

    worker = Worker(body)
    worker.start()
    worker.join(timeout)
    assert not worker.is_alive(), f"lifespan 기동 또는 종료가 {timeout}초 안에 끝나지 않았다"
    if worker.error is not None:
        raise worker.error
    return worker.result


class FlakyBrokerPort:
    """연결을 받는 즉시 끊는 TCP 포트 — '브로커가 아직 없다' 를 흉내 내고, 연결 시도를 센다."""

    def __init__(self):
        self._server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._server.bind(("127.0.0.1", 0))
        self._server.listen(16)
        self._server.settimeout(0.1)
        self.port = self._server.getsockname()[1]
        self.attempts = 0
        self._stop = threading.Event()
        self._thread = threading.Thread(target=self._serve, daemon=True)
        self._thread.start()

    def _serve(self) -> None:
        while not self._stop.is_set():
            try:
                conn, _ = self._server.accept()
            except socket.timeout:
                continue
            except OSError:
                return
            self.attempts += 1
            conn.close()

    def close(self) -> None:
        self._stop.set()
        self._thread.join(2)
        self._server.close()


@pytest.fixture()
def flaky_broker():
    port = FlakyBrokerPort()
    yield port
    port.close()


# --- R23 브로커 없이 -----------------------------------------------------------

def test_BROKER_ENABLED_false_면_브로커에_붙지_않고_HTTP_조회만_한다(flaky_broker, monkeypatch):
    _broker_env(monkeypatch, port=flaky_broker.port, enabled="false")

    def check(client):
        time.sleep(1.0)
        return client.get("/v1/health").json()

    _, health = _run_app(check)

    assert health["status"] == "ok"
    assert health.get("broker") == "disconnected"
    assert flaky_broker.attempts == 0


def test_브로커가_아직_없어도_기동은_막히지_않고_RABBITMQ_HOST_PORT_로_다시_붙으려_한다(flaky_broker, monkeypatch):
    _broker_env(monkeypatch, port=flaky_broker.port, enabled="true")

    def check(client):
        attempted = wait_for(lambda: flaky_broker.attempts >= 1, timeout=5)
        retried = attempted and wait_for(lambda: flaky_broker.attempts >= 2, timeout=15)
        return retried, client.get("/v1/health").json()

    startup_seconds, (retried, health) = _run_app(check, timeout=40)

    assert startup_seconds < 5, f"브로커를 기다리느라 기동이 {startup_seconds:.1f}초 막혔다"
    assert flaky_broker.attempts >= 1, "RABBITMQ_HOST · RABBITMQ_PORT 로 붙으려 하지 않았다"
    assert retried, f"끊긴 뒤 다시 붙으려 하지 않았다(시도 {flaky_broker.attempts}번)"
    assert health["status"] == "ok" and health.get("broker") == "disconnected"


# --- R23 · R24 진짜 RabbitMQ -----------------------------------------------------

def _broker_env_from_url(monkeypatch, *, max_active: int = 4) -> None:
    url = urlparse(BROKER_URL)
    _broker_env(monkeypatch, host=url.hostname or "localhost", port=url.port or 5672,
                username=unquote(url.username or "guest"), password=unquote(url.password or "guest"),
                vhost=unquote(url.path[1:]) or "/", max_active=max_active)


async def _open():
    connection = await aio_pika.connect_robust(BROKER_URL)
    channel = await connection.channel(publisher_confirms=True)
    await messaging.declare_topology(channel)
    return connection, channel


async def _purge(*names: str) -> None:
    connection, channel = await _open()
    async with connection:
        for name in names:
            queue = await channel.declare_queue(name, passive=True)
            await queue.purge()


async def _message_count(name: str) -> int:
    connection, channel = await _open()
    async with connection:
        queue = await channel.declare_queue(name, passive=True)
        return queue.declaration_result.message_count


async def _capture_queue(channel):
    """abc.events 의 run.# 을 몰래 받아 보는 임시 큐. abc.events.backend 는 건드리지 않는다."""
    queue = await channel.declare_queue(None, exclusive=True, auto_delete=True)
    await queue.bind("abc.events", routing_key="run.#")
    return queue


async def _publish_submit(channel, message: dict) -> None:
    exchange = await channel.get_exchange("abc.runs")
    await exchange.publish(
        aio_pika.Message(encode(message), content_type="application/json",
                         delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
                         message_id=message["execution_id"], type="SubmitRun"),
        routing_key="submit")


async def _publish_cancel(channel, execution_id: str) -> None:
    exchange = await channel.get_exchange("abc.control")
    await exchange.publish(
        aio_pika.Message(encode({"execution_id": execution_id, "requested_at": "2026-10-16T05:10:00Z"}),
                         content_type="application/json", delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
                         message_id=f"cancel:{execution_id}", type="CancelRun"),
        routing_key="")


async def _collect(queue, events: list[dict], until, timeout: float = 60.0) -> list[dict]:
    """until(events) 가 참이 될 때까지 받은 이벤트를 `events` 에 쌓는다. 메시지 속성도 함께 담는다."""
    deadline = time.monotonic() + timeout
    while not until(events):
        if time.monotonic() > deadline:
            raise AssertionError(f"기다린 이벤트가 오지 않았다: {[(e['execution_id'][:8], e['type']) for e in events]}")
        incoming = await queue.get(no_ack=True, fail=False)
        if incoming is None:
            await asyncio.sleep(0.05)
            continue
        event = json.loads(incoming.body)
        event["_props"] = {"routing_key": incoming.routing_key, "message_id": incoming.message_id,
                           "type": incoming.type, "content_type": incoming.content_type,
                           "delivery_mode": int(incoming.delivery_mode or 0)}
        events.append(event)
    return events


async def _drain(queue, events: list[dict], seconds: float) -> None:
    """`seconds` 동안 오는 이벤트를 다 받아 쌓는다 — '그 사이 아무것도 안 왔다' 를 보려고."""
    try:
        await _collect(queue, events, lambda _: False, timeout=seconds)
    except AssertionError:
        pass


def _final_for(*execution_ids: str):
    return lambda events: all(any(e["execution_id"] == i and e["type"] in FINAL for e in events) for i in execution_ids)


def _with_runtime(monkeypatch, scenario, *, max_active: int = 4, timeout: float = 120.0):
    """런타임 lifespan 을 띄우고 브로커에 붙은 뒤 `scenario(client)` 코루틴을 돌린다."""
    _broker_env_from_url(monkeypatch, max_active=max_active)

    def check(client):
        connected = wait_for(lambda: client.get("/v1/health").json().get("broker") == "connected",
                             timeout=20, interval=0.2)
        assert connected, "런타임이 브로커에 붙지 못했다"
        return asyncio.run(scenario(client))

    _, result = _run_app(check, timeout=timeout)
    return result


@pytest.mark.broker
@needs_broker
def test_SubmitRun_을_받아_돌리고_완료_이벤트가_confirm_된_뒤_ack_한다(monkeypatch):
    asyncio.run(_purge("abc.runs.submit"))
    message = submit_message(two_step_workflow())
    execution_id = message["execution_id"]

    async def scenario(client):
        connection, channel = await _open()
        async with connection:
            queue = await _capture_queue(channel)
            await _publish_submit(channel, message)
            events = await _collect(queue, [], _final_for(execution_id))
        await asyncio.sleep(1.0)                    # ack 는 confirm 뒤에 온다 — 런타임을 내리기 전에 틈을 준다
        return [e for e in events if e["execution_id"] == execution_id]

    events = _with_runtime(monkeypatch, scenario)

    types = [e["type"] for e in events]
    assert types[0] == "accepted" and types[-1] == "succeeded" and set(types[1:-1]) == {"progress"}
    for event in events:
        assert event["_props"] == {"routing_key": f"run.{event['type']}",
                                   "message_id": f"{execution_id}:1:{event['seq']}",
                                   "type": "RunEvent", "content_type": "application/json", "delivery_mode": 2}
    # 런타임을 내린 뒤에도 submit 큐가 비어 있으면 ack 된 것이다. ack 하지 않았으면 연결이 닫힐 때 큐로 돌아온다
    assert asyncio.run(_message_count("abc.runs.submit")) == 0


@pytest.mark.broker
@needs_broker
def test_prefetch_는_MAX_ACTIVE_RUNS_이고_넘치는_실행은_큐에서_기다린다(monkeypatch):
    asyncio.run(_purge("abc.runs.submit"))
    llm = GatedLLM()
    monkeypatch.setattr(runner, "LLM", llm.factory)
    first = submit_message(two_step_workflow(), mock=False)
    second = submit_message(two_step_workflow(), mock=False)
    ids = {first["execution_id"], second["execution_id"]}

    def accepted(events):
        return {e["execution_id"] for e in events if e["type"] == "accepted" and e["execution_id"] in ids}

    async def scenario(client):
        connection, channel = await _open()
        async with connection:
            queue = await _capture_queue(channel)
            events: list[dict] = []
            try:
                await _publish_submit(channel, first)
                await _publish_submit(channel, second)
                await _collect(queue, events, lambda es: len(accepted(es)) >= 1)
                await _drain(queue, events, 2.0)                    # 첫 실행이 묶인 동안 두 번째가 시작하나
                while_blocked = accepted(events)
            finally:
                llm.gate.set()
            await _collect(queue, events, _final_for(*ids))
        return while_blocked, events

    while_blocked, events = _with_runtime(monkeypatch, scenario, max_active=1)

    assert len(while_blocked) == 1, f"prefetch 1 인데 두 실행이 함께 시작했다: {while_blocked}"
    assert accepted(events) == ids
    assert llm.calls == 4                                       # 두 실행 × 노드 2개, 한 번씩


@pytest.mark.broker
@needs_broker
def test_실행은_이벤트_루프_밖_스레드에서_돌고_도는_중에도_발행과_조회가_막히지_않는다(monkeypatch):
    asyncio.run(_purge("abc.runs.submit"))
    llm = GatedLLM()
    monkeypatch.setattr(runner, "LLM", llm.factory)
    message = submit_message(two_step_workflow(), mock=False)
    execution_id = message["execution_id"]

    def node_a_started(events):
        return any(e["execution_id"] == execution_id and e["type"] == "progress"
                   and e["snapshot"]["progress"]["current_node_id"] == "a" for e in events)

    async def scenario(client):
        connection, channel = await _open()
        async with connection:
            queue = await _capture_queue(channel)
            events: list[dict] = []
            try:
                await _publish_submit(channel, message)
                await _collect(queue, events, node_a_started)          # 모델 호출에 묶인 동안에도 진행 이벤트가 나간다
                blocked = llm.entered.is_set() and not llm.gate.is_set()
                health = await asyncio.wait_for(asyncio.to_thread(client.get, "/v1/health"), timeout=5)
            finally:
                llm.gate.set()
            await _collect(queue, events, _final_for(execution_id))
        return blocked, health.status_code, events

    blocked, health_status, events = _with_runtime(monkeypatch, scenario)

    assert blocked, "진행 이벤트를 받을 때 실행이 모델 호출에 멈춰 있지 않았다"
    assert health_status == 200                                 # 이벤트 루프가 실행에 묶이지 않았다
    assert llm.on_event_loop == [False, False]                  # 모델 호출은 asyncio 루프가 없는 스레드에서
    assert [e["type"] for e in events if e["execution_id"] == execution_id][-1] == "succeeded"


@pytest.mark.broker
@needs_broker
def test_CancelRun_은_abc_control_로_받아_도는_실행을_다음_노드부터_멈춘다(monkeypatch):
    asyncio.run(_purge("abc.runs.submit"))
    llm = GatedLLM()
    monkeypatch.setattr(runner, "LLM", llm.factory)
    message = submit_message(two_step_workflow(), mock=False)
    execution_id = message["execution_id"]

    async def scenario(client):
        connection, channel = await _open()
        async with connection:
            queue = await _capture_queue(channel)
            events: list[dict] = []
            try:
                await _publish_submit(channel, message)
                entered = await asyncio.to_thread(llm.entered.wait, 30)
                assert entered, "실행이 시작되지 않았다"
                await _publish_cancel(channel, execution_id)
                await asyncio.sleep(1.5)                                # 런타임 전용 control 큐로 건너갈 틈
            finally:
                llm.gate.set()
            await _collect(queue, events, _final_for(execution_id))
        return [e for e in events if e["execution_id"] == execution_id]

    events = _with_runtime(monkeypatch, scenario)

    final = events[-1]
    assert final["type"] == "failed"
    assert final["snapshot"]["error"]["code"] == "CANCELLED"
    assert llm.calls == 1


@pytest.mark.broker
@needs_broker
def test_받을_수_없는_메시지는_다시_넣지_않고_버려_abc_runs_dead_로_간다(monkeypatch):
    asyncio.run(_purge("abc.runs.submit", "abc.runs.dead"))
    marker = f"garbage-{uuid.uuid4()}"

    async def scenario(client):
        connection, channel = await _open()
        async with connection:
            exchange = await channel.get_exchange("abc.runs")
            await exchange.publish(aio_pika.Message(b"not json", message_id=marker, type="SubmitRun",
                                                    delivery_mode=aio_pika.DeliveryMode.PERSISTENT),
                                   routing_key="submit")
            dead = await channel.declare_queue("abc.runs.dead", passive=True)
            deadline = time.monotonic() + 20
            while time.monotonic() < deadline:
                incoming = await dead.get(no_ack=True, fail=False)
                if incoming is not None and incoming.message_id == marker:
                    reason = (incoming.headers or {}).get("x-first-death-reason")
                    return reason.decode() if isinstance(reason, bytes) else reason
                await asyncio.sleep(0.1)
        return None

    reason = _with_runtime(monkeypatch, scenario)

    assert reason == "rejected"                                 # nack(requeue=false) — 한도 초과 · 만료가 아니다
