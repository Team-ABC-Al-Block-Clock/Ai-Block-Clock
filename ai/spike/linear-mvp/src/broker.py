"""RabbitMQ 연결 — SubmitRun · CancelRun 을 받아 메시지 처리(src/messaging.py)에 넘기고 ack 한다(ADR-0001, ai/docs/integration-guide.md §4).

- 접속 설정은 기동할 때 환경 변수에서 읽는다(ai/docs/integration-guide.md §4.10). `BROKER_ENABLED=false` 면 붙지 않는다(HTTP 조회만 쓰는 로컬 개발).
- 브로커가 아직 없어도 기동을 막지 않는다. 뒤에서 RECONNECT_SECONDS 마다 다시 붙고, 한 번 붙은 뒤의 끊김은
  robust connection 이 다시 잇는다(토폴로지 · QoS · 컨슈머까지 되살린다).
- 동시 실행 상한은 prefetch(`MAX_ACTIVE_RUNS`)다. 넘치는 실행은 큐에서 기다린다.
- 실행은 스레드에서 돈다. 스레드의 발행은 이벤트 루프로 넘겨 publisher confirm 을 기다리고, ack · nack 는 이벤트 루프에서 한다.
  긴 실행 중에도 루프가 막히지 않아 하트비트가 끊기지 않는다.
"""
from __future__ import annotations

import asyncio
import json
import logging
import os
from collections.abc import AsyncIterator
from concurrent.futures import ThreadPoolExecutor
from contextlib import asynccontextmanager
from dataclasses import dataclass

import aio_pika
from aio_pika.abc import AbstractIncomingMessage
from aio_pika.connection import make_url

from . import messaging

log = logging.getLogger(__name__)

RECONNECT_SECONDS = 5
CONNECT_TIMEOUT_SECONDS = 10
PUBLISH_TIMEOUT_SECONDS = 30
REQUEUE_DELAY_SECONDS = 1           # 발행 실패로 돌려보낸 메시지가 곧바로 다시 와 헛도는 것을 늦춘다


@dataclass(frozen=True)
class Settings:
    enabled: bool
    host: str
    port: int
    username: str
    password: str
    vhost: str
    prefetch: int

    @classmethod
    def from_env(cls) -> Settings:
        return cls(
            enabled=os.getenv("BROKER_ENABLED", "true").strip().lower() not in ("false", "0", "no", "off"),
            host=os.getenv("RABBITMQ_HOST", "localhost"),
            port=int(os.getenv("RABBITMQ_PORT", "5672")),
            username=os.getenv("RABBITMQ_USERNAME", "guest"),
            password=os.getenv("RABBITMQ_PASSWORD", "guest"),
            vhost=os.getenv("RABBITMQ_VHOST", "/"),
            prefetch=max(1, int(os.getenv("MAX_ACTIVE_RUNS", "4"))),
        )


class Broker:
    """연결 하나 · 채널 하나. 이 객체가 곧 스레드에서 쓰는 `messaging.EventPublisher` 다."""

    def __init__(self, settings: Settings):
        self.settings = settings
        self._loop: asyncio.AbstractEventLoop | None = None
        self._connection: aio_pika.RobustConnection | None = None
        self._events: aio_pika.abc.AbstractExchange | None = None
        self._ready = False
        self._closing = False
        self._task: asyncio.Task | None = None
        # prefetch 만큼 메시지가 동시에 오므로 같은 수의 스레드면 된다
        self._executor = ThreadPoolExecutor(max_workers=settings.prefetch, thread_name_prefix="abc-run")

    @property
    def connected(self) -> bool:
        connection = self._connection
        return (self._ready and connection is not None and not connection.is_closed
                and connection.connected.is_set())

    async def start(self) -> None:
        """기다리지 않는다 — 붙는 일은 뒤에서 한다."""
        self._loop = asyncio.get_running_loop()

        if not self.settings.enabled:
            log.info("BROKER_ENABLED=false — 브로커에 붙지 않는다")
            return

        self._task = asyncio.create_task(self._connect())

    async def stop(self) -> None:
        self._closing = True
        self._ready = False

        if self._task is not None:
            self._task.cancel()
            await asyncio.gather(self._task, return_exceptions=True)

        if self._connection is not None:
            await _close_quietly(self._connection)

        self._executor.shutdown(wait=False, cancel_futures=True)

    async def _connect(self) -> None:
        settings = self.settings
        url = make_url(host=settings.host, port=settings.port, login=settings.username,
                       password=settings.password, virtualhost=settings.vhost)

        while not self._closing:
            connection = aio_pika.RobustConnection(url, reconnect_interval=RECONNECT_SECONDS)

            try:
                await connection.connect(timeout=CONNECT_TIMEOUT_SECONDS)
                await self._setup(connection)
            except asyncio.CancelledError:
                await _close_quietly(connection)
                raise
            except Exception as e:
                log.warning("브로커 %s:%s 에 붙지 못했다(%s). %s초 뒤 다시 붙는다",
                            settings.host, settings.port, e, RECONNECT_SECONDS)
                await _close_quietly(connection)
                await asyncio.sleep(RECONNECT_SECONDS)
                continue

            self._connection = connection
            self._ready = True
            log.info("브로커 %s:%s 에 붙었다(prefetch %s)", settings.host, settings.port, settings.prefetch)
            return

    async def _setup(self, connection: aio_pika.RobustConnection) -> None:
        channel = await connection.channel(publisher_confirms=True)
        await channel.set_qos(prefetch_count=self.settings.prefetch)
        await messaging.declare_topology(channel)
        self._events = await channel.get_exchange(messaging.EVENTS_EXCHANGE)

        # 런타임마다 하나씩 받는 취소 큐. 런타임이 내려가면 함께 사라진다(ai/docs/integration-guide.md §4.1).
        # 이름은 aio-pika 가 지어 두고 다시 붙을 때 같은 이름으로 되살린다
        control = await channel.declare_queue(None, exclusive=True, auto_delete=True)
        await control.bind(messaging.CONTROL_EXCHANGE, routing_key="")
        await control.consume(self._on_cancel, no_ack=True)

        submit = await channel.get_queue(messaging.SUBMIT_QUEUE)
        await submit.consume(self._on_submit)

    async def _on_submit(self, message: AbstractIncomingMessage) -> None:
        delivery_count = _delivery_count(message)

        try:
            outcome = await self._loop.run_in_executor(
                self._executor, messaging.handle_submit, message.body, delivery_count, self)
        except RuntimeError:        # 내려가는 중이라 스레드를 더 받지 않는다. ack 하지 않으면 브로커가 되돌린다
            log.warning("내려가는 중이라 SubmitRun 을 처리하지 않았다: %s", message.message_id)
            return

        try:
            if outcome == "ack":
                await message.ack()
            elif outcome == "requeue":
                await asyncio.sleep(REQUEUE_DELAY_SECONDS)
                await message.nack(requeue=True)
            else:
                await message.reject(requeue=False)     # dead letter → abc.runs.dead
        except Exception as e:      # 그 사이 채널이 바뀌었다. 브로커가 이미 되돌렸고 다시 오면 R13 · R14 로 처리된다
            log.warning("SubmitRun %s 를 %s 하지 못했다: %s", message.message_id, outcome, e)

    async def _on_cancel(self, message: AbstractIncomingMessage) -> None:
        await self._loop.run_in_executor(None, messaging.handle_cancel, message.body)

    def publish(self, routing_key: str, message: dict, message_id: str) -> None:
        """스레드에서 부른다. 이벤트 루프에서 발행하고 confirm 까지 기다린다. 실패면 예외."""
        if self._closing or not self.connected:
            raise ConnectionError("브로커에 붙어 있지 않다")

        body = json.dumps(message, ensure_ascii=False).encode("utf-8")      # 지금 모양으로 굳힌다
        future = asyncio.run_coroutine_threadsafe(self._publish(routing_key, body, message_id), self._loop)

        try:
            future.result(PUBLISH_TIMEOUT_SECONDS)
        except TimeoutError:
            future.cancel()         # 늦게라도 나가지 않게
            raise

    async def _publish(self, routing_key: str, body: bytes, message_id: str) -> None:
        await self._events.publish(
            aio_pika.Message(body, content_type="application/json", delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
                             message_id=message_id, type="RunEvent"),
            routing_key=routing_key, timeout=PUBLISH_TIMEOUT_SECONDS)


def _delivery_count(message: AbstractIncomingMessage) -> int:
    """quorum 큐가 붙이는 x-delivery-count(없으면 0). attempt = 이 값 + 1."""
    value = (message.headers or {}).get("x-delivery-count")

    try:
        return int(value or 0)
    except (TypeError, ValueError):
        return 0


async def _close_quietly(connection: aio_pika.RobustConnection) -> None:
    try:
        await connection.close()
    except Exception as e:
        log.debug("연결을 닫다가: %s", e)


_current: Broker | None = None


def status() -> str:
    """`/v1/health` 의 broker 칸. 받을 준비까지 끝났으면 connected."""
    return "connected" if _current is not None and _current.connected else "disconnected"


@asynccontextmanager
async def running() -> AsyncIterator[Broker]:
    """FastAPI lifespan 에서 쓴다. 설정은 이때 환경 변수에서 읽는다."""
    global _current
    broker = Broker(Settings.from_env())
    await broker.start()
    _current = broker

    try:
        yield broker
    finally:
        _current = None
        await broker.stop()
