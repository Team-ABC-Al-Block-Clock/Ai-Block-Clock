"""메시지 처리 테스트의 공용 도구 — 가짜 발행자 · SubmitRun 만들기 · 멈춰 세우는 가짜 모델.

`messaging.handle_submit` 을 전송(aio-pika) 없이 동기로 부르고, 발행된 RunEvent 를 목록으로 받아 본다.
실행 조회는 지금처럼 HTTP(`GET /v1/runs/{execution_id}`)로 한다.
"""
from __future__ import annotations

import asyncio
import json
import threading
import time
import uuid
from datetime import datetime, timedelta

from src import messaging, runner
from src.llm import LLMResult

BRIEF = {"brand_name": "초록소반", "category": "간편식", "target": "1인 가구 30대",
         "price_tier": "중가", "differentiator": "국내산 재료"}


class PublishFailed(ConnectionError):
    """브로커가 nack 했거나 연결이 끊긴 것처럼 던진다."""


class FakePublisher:
    """`EventPublisher` 가짜. publish 호출을 쌓고, `fail_types` 에 든 type 의 이벤트는 confirm 실패처럼 예외를 던진다.

    받은 message 는 JSON 으로 한 번 직렬화했다 되살려 쌓는다 — 진짜 발행자도 그 순간 바이트로 만들고,
    그래야 실행 상태가 나중에 바뀌어도 기록이 발행 시점 그대로 남는다. 직렬화가 안 되면 여기서 깨진다.
    """

    def __init__(self, fail_types=(), on_publish=None):
        self.fail_types = set(fail_types)
        self.on_publish = on_publish
        self.attempts: list[tuple[str, dict, str]] = []     # 시도한 것 전부
        self.published: list[tuple[str, dict, str]] = []    # confirm 된 것만

    def publish(self, routing_key: str, message: dict, message_id: str) -> None:
        record = (routing_key, json.loads(json.dumps(message, ensure_ascii=False)), message_id)
        self.attempts.append(record)
        if self.on_publish:
            self.on_publish(*record)
        if message.get("type") in self.fail_types:
            raise PublishFailed(f"confirm 실패: {routing_key} {message_id}")
        self.published.append(record)

    @property
    def events(self) -> list[dict]:
        return [message for _, message, _ in self.published]

    def types(self) -> list[str]:
        return [event["type"] for event in self.events]

    def last(self) -> dict:
        assert self.published, "발행된 이벤트가 없다"
        return self.events[-1]


def new_execution_id() -> str:
    return str(uuid.uuid4())


def submit_message(workflow, *, execution_id: str | None = None, mode="run", input=None, comment=None,
                   mock=True, files=None, ref_files=None, **extra) -> dict:
    """contract 3장 SubmitRun. 기본은 mock 실행이다."""
    return {"execution_id": execution_id or new_execution_id(), "mode": mode, "workflow": workflow,
            "input": input, "comment": comment, "mock": mock, "files": files or [],
            "ref_files": ref_files or {}, "submitted_at": "2026-10-16T05:00:00Z", **extra}


def encode(message) -> bytes:
    return json.dumps(message, ensure_ascii=False).encode("utf-8")


def submit(message: dict, *, delivery_count: int = 0,
           publisher: FakePublisher | None = None) -> tuple[str, FakePublisher]:
    publisher = publisher or FakePublisher()
    outcome = messaging.handle_submit(encode(message), delivery_count, publisher)
    return outcome, publisher


def cancel(execution_id: str) -> None:
    """contract 6장 CancelRun."""
    messaging.handle_cancel(encode({"execution_id": execution_id, "requested_at": "2026-10-16T05:10:00Z"}))


def two_step_workflow() -> dict:
    """글 노드 두 개(a → b). 진입 노드에 입력 예시가 없어 input 이 null 이어도 돈다."""
    return {"id": "two", "name": "두 단계",
            "nodes": [{"id": "a", "name": "요약", "prompt": "요약한다", "owner_id": "u_a", "output": {"kind": "text"}},
                      {"id": "b", "name": "카피", "prompt": "카피를 쓴다", "owner_id": "u_b", "output": {"kind": "text"}}],
            "edges": [{"from": "a", "to": "b"}],
            "output": {"type": "pdf", "title": "두 단계"}}


def assert_utc(value: str) -> None:
    """ISO-8601 UTC 문자열인가 — `Z` 와 `+00:00` 둘 다 받는다."""
    parsed = datetime.fromisoformat(value)
    assert parsed.utcoffset() == timedelta(0), value


def wait_for(predicate, timeout: float = 10.0, interval: float = 0.01) -> bool:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if predicate():
            return True
        time.sleep(interval)
    return predicate()


class FailingLLM:
    """모든 호출이 모델 API 오류로 끝나는 가짜 모델. `runner.LLM` 자리에 그대로 꽂는다."""

    mock = False

    def __init__(self, **_):
        pass

    def generate(self, *args, **kwargs):
        raise RuntimeError("모델 API 503")


class LLMCounter:
    """`runner.LLM` 을 감싸 실행이 모델을 몇 번 만들었는지 센다. 실행 하나가 한 번 만든다."""

    def __init__(self, monkeypatch):
        real = runner.LLM
        self.created = 0

        def factory(*args, **kwargs):
            self.created += 1
            return real(*args, **kwargs)

        monkeypatch.setattr(runner, "LLM", factory)


class GatedLLM:
    """generate 에 들어오면 `entered` 를 켜고 `gate` 가 열릴 때까지 멈춘다 — '도는 중' 을 만든다.

    `runner.LLM` 자리에는 `factory` 를 꽂는다. 글 노드 전용이다.
    """

    mock = False

    def __init__(self):
        self.entered = threading.Event()
        self.gate = threading.Event()
        self.calls = 0
        self.on_event_loop: list[bool] = []      # 호출된 스레드에 돌고 있는 asyncio 루프가 있었나
        self._lock = threading.Lock()

    def factory(self, **_):
        return self

    def generate(self, system, user, label="", response_schema=None, mock_json=None, attachments=None):
        with self._lock:
            self.calls += 1
        self.on_event_loop.append(_has_running_loop())
        self.entered.set()
        if not self.gate.wait(30):
            raise TimeoutError("gate 가 열리지 않았다")
        return LLMResult(text=f"{label} 결과", model="fake", input_tokens=10, output_tokens=5, duration_ms=1)


def _has_running_loop() -> bool:
    try:
        asyncio.get_running_loop()
    except RuntimeError:
        return False
    return True


class Worker(threading.Thread):
    """함수를 따로 돌린다. 결과와 예외를 담고, `after` 가 있으면 함수가 돌아온 직후 그 자리에서 부른다."""

    def __init__(self, fn, after=None):
        super().__init__(daemon=True)
        self.fn = fn
        self.after = after
        self.result = None
        self.after_result = None
        self.error: BaseException | None = None

    def run(self) -> None:
        try:
            self.result = self.fn()
            if self.after:
                self.after_result = self.after()
        except BaseException as e:          # 테스트 스레드에서 단언으로 드러낸다
            self.error = e
