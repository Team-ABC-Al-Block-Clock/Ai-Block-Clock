"""실행 메시지 처리 — SubmitRun 을 받아 돌리고 RunEvent 를 발행한다(runtime-spec R1 ~ R22, contract.md).

전송(aio-pika)과 떼어 놓은 처리 로직(`src/messaging.py`)을 가짜 발행자로 동기로 부른다.
메시지로 받은 실행은 HTTP 조회(`GET /v1/runs/{execution_id}`)로 그대로 보인다.
진짜 RabbitMQ 로 확인하는 연결 · confirm · ack 는 `test_broker.py` 에 있다.
"""
from __future__ import annotations

import asyncio
import inspect
import json
from pathlib import Path

import pytest
from aio_pika import ExchangeType
from fastapi.testclient import TestClient

from src import api, errors, messaging, runner
from src.api import app
from tests.messaging_support import (BRIEF, FailingLLM, FakePublisher, GatedLLM, LLMCounter, Worker, assert_utc,
                                     cancel, encode, submit, submit_message, two_step_workflow, wait_for)
from tests.test_api_shape import TOP_KEYS


@pytest.fixture()
def client():
    return TestClient(app)


@pytest.fixture()
def llm_counter(monkeypatch):
    return LLMCounter(monkeypatch)


# --- R1 · R3 받기 · 실행 · 이벤트 순서 ------------------------------------------

def test_올바른_SubmitRun_은_accepted_progress_succeeded_를_차례로_발행하고_ack_한다():
    message = submit_message(two_step_workflow(), comment="가을 신제품", future_field="모르는 칸은 무시한다")

    outcome, publisher = submit(message)

    assert outcome == "ack"
    # 실행 시작 1 + 노드 2개 × (시작 · 성공) = progress 5
    assert publisher.types() == ["accepted"] + ["progress"] * 5 + ["succeeded"]


def test_모든_이벤트는_같은_execution_id_mode_attempt_에_seq_가_1부터_빠짐없이_는다():
    message = submit_message(two_step_workflow(), mode="test")
    execution_id = message["execution_id"]

    _, publisher = submit(message)

    assert [event["seq"] for event in publisher.events] == list(range(1, len(publisher.events) + 1))
    for routing_key, event, message_id in publisher.published:
        assert event["execution_id"] == execution_id
        assert event["mode"] == "test"
        assert event["attempt"] == 1
        assert routing_key == f"run.{event['type']}"
        assert message_id == f"{execution_id}:1:{event['seq']}"
        assert_utc(event["occurred_at"])


def test_accepted_와_progress_의_snapshot_은_노드_출력_없는_조회_모양이다():
    message = submit_message(two_step_workflow())

    _, publisher = submit(message)

    for event in publisher.events[:-1]:
        snapshot = event["snapshot"]
        assert set(snapshot) == TOP_KEYS, event["type"]
        assert snapshot["run_id"] == message["execution_id"]
        assert all("output_payload" not in node for node in snapshot["nodes"]), event["type"]
        assert event.get("artifact") is None and event.get("result_files") is None


def test_progress_의_snapshot_은_그_시점의_진행률을_보인다():
    _, publisher = submit(submit_message(two_step_workflow()))

    accepted = publisher.events[0]["snapshot"]
    progress = [event["snapshot"] for event in publisher.events if event["type"] == "progress"]

    assert accepted["progress"]["done"] == 0 and accepted["progress"]["total"] == 2
    assert [(s["progress"]["current_node_id"], s["progress"]["done"]) for s in progress] == [
        (None, 0),          # 실행 시작
        ("a", 0),           # a 시작
        (None, 1),          # a 성공
        ("b", 1),           # b 시작
        (None, 2),          # b 성공
    ]
    assert all(s["status"] == "running" for s in progress)


# --- R2 성공 이벤트 · 결과 볼륨 --------------------------------------------------

def test_succeeded_는_노드_출력_포함_조회와_같은_snapshot_과_artifact_봉투를_싣는다(client):
    message = submit_message(two_step_workflow())
    run_id = message["execution_id"]

    _, publisher = submit(message)

    final = publisher.last()
    assert final["type"] == "succeeded"
    assert final["snapshot"] == client.get(f"/v1/runs/{run_id}?include_node_output=true").json()
    assert final["snapshot"]["status"] == "succeeded"
    assert all("output_payload" in node for node in final["snapshot"]["nodes"])
    assert final["artifact"] == client.get(f"/v1/runs/{run_id}/artifact").json()


def test_succeeded_를_발행하기_전에_결과_볼륨에_봉투와_보고서를_쓴다(volumes, client):
    written_before_publish = {}

    def on_publish(routing_key, event, message_id):
        if event["type"] == "succeeded":
            folder = volumes.results / event["execution_id"]
            written_before_publish.update(
                {name: (folder / name).exists() for name in ("artifact.json", "report.pdf", "report.html")})

    message = submit_message(two_step_workflow())
    run_id = message["execution_id"]
    folder = volumes.results / run_id

    _, publisher = submit(message, publisher=FakePublisher(on_publish=on_publish))

    final = publisher.last()
    assert written_before_publish == {"artifact.json": True, "report.pdf": True, "report.html": True}
    assert json.loads((folder / "artifact.json").read_text(encoding="utf-8")) == final["artifact"]
    assert (folder / "report.pdf").read_bytes().startswith(b"%PDF")
    assert (folder / "report.html").read_text(encoding="utf-8") == client.get(f"/v1/runs/{run_id}/html").text
    assert final["result_files"] == {"json": f"{run_id}/artifact.json",
                                     "pdf": f"{run_id}/report.pdf",
                                     "html": f"{run_id}/report.html"}


def test_PDF_를_못_만들었으면_result_files_에_pdf_가_없다(volumes, monkeypatch):
    def broken_pdf(*args, **kwargs):
        raise RuntimeError("PDF 엔진 고장")

    monkeypatch.setattr(runner, "html_to_pdf", broken_pdf)
    message = submit_message(two_step_workflow())
    run_id = message["execution_id"]

    outcome, publisher = submit(message)

    assert outcome == "ack" and publisher.last()["type"] == "succeeded"     # PDF 실패는 실행 실패가 아니다
    assert publisher.last()["result_files"] == {"json": f"{run_id}/artifact.json", "html": f"{run_id}/report.html"}
    assert not (volumes.results / run_id / "report.pdf").exists()


# --- R4 실패로 끝남 ----------------------------------------------------------

def test_실행이_실패로_끝나면_마지막_이벤트는_failed_이고_실패_사유가_채워져_있다(monkeypatch, client):
    monkeypatch.setattr(runner, "LLM", FailingLLM)
    message = submit_message(two_step_workflow(), mock=False)
    run_id = message["execution_id"]

    outcome, publisher = submit(message)

    final = publisher.last()
    assert outcome == "ack"
    assert publisher.types()[0] == "accepted" and final["type"] == "failed"
    assert set(publisher.types()[1:-1]) == {"progress"}
    assert final["snapshot"]["status"] == "failed"
    assert final["snapshot"]["error"]["code"] == "MODEL_API_ERROR"
    assert final["snapshot"]["failure"]["error_code"] == "MODEL_API_ERROR"
    assert final["snapshot"]["failure"]["node_id"] == "a"
    assert final["snapshot"] == client.get(f"/v1/runs/{run_id}?include_node_output=true").json()
    assert final.get("artifact") is None and final.get("result_files") is None


# --- R5 조회 ----------------------------------------------------------------

def test_메시지로_받은_실행은_execution_id_로_조회한다(client):
    message = submit_message(two_step_workflow())
    run_id = message["execution_id"]

    submit(message)

    assert client.get(f"/v1/runs/{run_id}").json()["run_id"] == run_id
    for suffix in ("", "?include_node_output=true", "/artifact", "/html", "/pdf"):
        assert client.get(f"/v1/runs/{run_id}{suffix}").status_code == 200, suffix


# --- R6 attempt ---------------------------------------------------------------

def test_재배달로_처음_받은_실행은_attempt_2_이고_seq_를_1부터_다시_센다():
    message = submit_message(two_step_workflow())
    execution_id = message["execution_id"]

    outcome, publisher = submit(message, delivery_count=1)

    count = len(publisher.events)
    assert outcome == "ack" and publisher.types()[-1] == "succeeded"
    assert [event["attempt"] for event in publisher.events] == [2] * count
    assert [event["seq"] for event in publisher.events] == list(range(1, count + 1))
    assert [message_id for _, _, message_id in publisher.published] == [
        f"{execution_id}:2:{seq}" for seq in range(1, count + 1)]


# --- R7 ~ R11 거절 ------------------------------------------------------------

def _assert_rejected(outcome: str, publisher: FakePublisher, message: dict, code: str) -> None:
    execution_id = message["execution_id"]
    assert outcome == "ack"
    assert publisher.types() == ["rejected"]                         # accepted 없이 하나만
    routing_key, event, message_id = publisher.published[0]
    assert routing_key == "run.rejected"
    assert message_id == f"{execution_id}:1:1"
    assert (event["execution_id"], event["attempt"], event["seq"]) == (execution_id, 1, 1)
    snapshot = event["snapshot"]
    assert snapshot["status"] == "failed"
    assert snapshot["nodes"] == []
    assert snapshot["error"]["code"] == code
    failure = snapshot["failure"]
    assert {key: failure.get(key) for key in ("node_id", "error_code", "fault", "affects_success_rate")} == {
        "node_id": None, "error_code": code, "fault": errors.describe(code)["fault"], "affects_success_rate": False}
    assert event.get("artifact") is None and event.get("result_files") is None


def test_그래프_검증에_걸린_워크플로우는_rejected_INVALID_WORKFLOW(llm_counter):
    wf = runner.load_workflow("brand_agent")
    wf["edges"].append({"from": "n4", "to": "n1"})                  # 순환
    message = submit_message(wf, input=BRIEF)

    outcome, publisher = submit(message)

    _assert_rejected(outcome, publisher, message, "INVALID_WORKFLOW")
    assert llm_counter.created == 0                                  # 실행 전에 걸렸다


def test_출력_선언이_없는_노드가_있어도_rejected_INVALID_WORKFLOW():
    wf = two_step_workflow()
    wf["nodes"][1].pop("output")
    message = submit_message(wf)

    outcome, publisher = submit(message)

    _assert_rejected(outcome, publisher, message, "INVALID_WORKFLOW")


def test_workflow_가_객체가_아니면_rejected_INVALID_WORKFLOW():
    message = submit_message(json.dumps(two_step_workflow(), ensure_ascii=False))   # 예전 HTTP 처럼 문자열

    outcome, publisher = submit(message)

    _assert_rejected(outcome, publisher, message, "INVALID_WORKFLOW")


@pytest.mark.parametrize("mode", ["dry", "RUN", None])
def test_mode_가_run_test_가_아니면_rejected_INVALID_MODE(mode, llm_counter):
    message = submit_message(two_step_workflow(), mode=mode)

    outcome, publisher = submit(message)

    _assert_rejected(outcome, publisher, message, "INVALID_MODE")
    assert llm_counter.created == 0


@pytest.mark.parametrize("value", ["{not json", [BRIEF], 3, True])
def test_input_이_객체도_null_도_아니면_rejected_INVALID_INPUT_JSON(value):
    message = submit_message(two_step_workflow(), input=value)

    outcome, publisher = submit(message)

    _assert_rejected(outcome, publisher, message, "INVALID_INPUT_JSON")


def test_input_이_null_이면_거절하지_않는다():
    outcome, publisher = submit(submit_message(two_step_workflow(), input=None))

    assert outcome == "ack" and publisher.types()[0] == "accepted"


@pytest.fixture()
def store_files(volumes):
    """볼륨 안 파일 하나와 볼륨 바로 밖(같은 부모) 파일 하나 — 거절이 '파일 없음' 때문이 아니게 둘 다 실제로 둔다."""
    inside = volumes.store / "executions" / "e1" / "메모.md"
    inside.parent.mkdir(parents=True)
    inside.write_text("안쪽 파일", encoding="utf-8")
    outside = volumes.store.parent / "outside.txt"
    outside.write_text("바깥 파일", encoding="utf-8")
    return inside, outside


@pytest.mark.parametrize("path", [
    "../outside.txt",                                   # 볼륨 밖
    "executions/../../outside.txt",                     # 풀면 볼륨 밖
    "executions/../executions/e1/메모.md",               # 풀면 안쪽이어도 .. 를 품었다
    "/etc/passwd",                                      # 절대 경로
])
def test_files_경로가_절대_경로이거나_dotdot_를_품거나_볼륨_밖이면_rejected_FILE_PATH_REJECTED(path, store_files,
                                                                                         llm_counter):
    message = submit_message(two_step_workflow(), files=[path])

    outcome, publisher = submit(message)

    _assert_rejected(outcome, publisher, message, "FILE_PATH_REJECTED")
    assert llm_counter.created == 0


def test_볼륨_안을_가리켜도_절대_경로면_rejected_FILE_PATH_REJECTED(store_files):
    inside, _ = store_files
    message = submit_message(two_step_workflow(), files=[str(inside.resolve())])

    outcome, publisher = submit(message)

    _assert_rejected(outcome, publisher, message, "FILE_PATH_REJECTED")


def test_볼륨_밖을_가리키는_심볼릭_링크는_rejected_FILE_PATH_REJECTED(volumes, store_files):
    _, outside = store_files
    link = volumes.store / "executions" / "e1" / "link.txt"
    try:
        link.symlink_to(outside)
    except (OSError, NotImplementedError):
        pytest.skip("이 환경에서는 심볼릭 링크를 만들 수 없다")
    message = submit_message(two_step_workflow(), files=["executions/e1/link.txt"])

    outcome, publisher = submit(message)

    _assert_rejected(outcome, publisher, message, "FILE_PATH_REJECTED")


@pytest.mark.parametrize("path", ["../outside.txt", "/etc/passwd"])
def test_ref_files_경로도_같은_규칙으로_rejected_FILE_PATH_REJECTED(path, store_files):
    message = submit_message(two_step_workflow(), ref_files={"b": ["executions/e1/메모.md", path]})

    outcome, publisher = submit(message)

    _assert_rejected(outcome, publisher, message, "FILE_PATH_REJECTED")


def test_FILE_PATH_REJECTED_는_플랫폼_책임이다():
    described = errors.describe("FILE_PATH_REJECTED")

    assert described["fault"] == "platform"                        # 경로는 백엔드가 만들었다
    assert described["affects_success_rate"] is False
    assert "FILE_PATH_REJECTED" in errors.CODES


def test_허용하지_않는_확장자의_사용자_파일은_rejected_UNSUPPORTED_FILE_TYPE(volumes, llm_counter):
    doc = volumes.store / "executions" / "e1" / "기획.docx"
    doc.parent.mkdir(parents=True)
    doc.write_bytes(b"x")
    message = submit_message(two_step_workflow(), files=["executions/e1/기획.docx"])

    outcome, publisher = submit(message)

    _assert_rejected(outcome, publisher, message, "UNSUPPORTED_FILE_TYPE")
    assert llm_counter.created == 0


def test_사용자_파일_합계가_10MB_를_넘으면_rejected_FILE_TOO_LARGE(volumes):
    folder = volumes.store / "executions" / "e1"
    folder.mkdir(parents=True)
    for name in ("a.txt", "b.txt"):                                  # 하나씩은 10MB 아래, 합계는 넘는다
        (folder / name).write_bytes(b"x" * (6 * 1024 * 1024))
    message = submit_message(two_step_workflow(), files=["executions/e1/a.txt", "executions/e1/b.txt"])

    outcome, publisher = submit(message)

    _assert_rejected(outcome, publisher, message, "FILE_TOO_LARGE")


# --- R12 받을 수 없는 메시지 ----------------------------------------------------

def _body(value) -> bytes:
    return value if isinstance(value, bytes) else json.dumps(value, ensure_ascii=False).encode("utf-8")


@pytest.mark.parametrize("value", [
    b"not json",
    b"\xff\xfe\x00\x01",
    b"[]",
    b"null",
    {"mode": "run", "workflow": {}},                    # execution_id 없음
    {"execution_id": None},
    {"execution_id": 123},
    {"execution_id": ""},
    {"execution_id": "has space"},
    {"execution_id": "a" * 65},
    {"execution_id": "../etc"},
    {"execution_id": "exec_1"},                         # 밑줄은 허용 문자가 아니다
    {"execution_id": "실행-1"},
], ids=lambda v: repr(v)[:40])
def test_JSON_이_아니거나_execution_id_가_없거나_형식이_틀리면_이벤트_없이_reject(value):
    publisher = FakePublisher()

    outcome = messaging.handle_submit(_body(value), 0, publisher)

    assert outcome == "reject"
    assert publisher.attempts == []


def test_execution_id_는_64자까지_받는다():
    message = submit_message(two_step_workflow(), execution_id="A1-" + "b" * 61)

    outcome, publisher = submit(message)

    assert outcome == "ack" and publisher.types()[0] == "accepted"


# --- R13 · R14 멱등 -----------------------------------------------------------

def test_이미_끝난_실행이_다시_오면_돌리지_않고_마지막_이벤트를_이번_attempt_로_다시_발행한다(llm_counter):
    message = submit_message(two_step_workflow())
    execution_id = message["execution_id"]
    _, first = submit(message)
    created = llm_counter.created

    outcome, again = submit(message, delivery_count=1)

    assert outcome == "ack"
    assert llm_counter.created == created                            # 모델을 다시 부르지 않았다
    assert again.types() == ["succeeded"]
    routing_key, event, message_id = again.published[0]
    original = first.last()
    assert routing_key == "run.succeeded"
    assert (event["attempt"], event["seq"]) == (2, 1)
    assert message_id == f"{execution_id}:2:1"
    assert (event["execution_id"], event["mode"]) == (execution_id, "run")
    assert event["snapshot"] == original["snapshot"]
    assert event["artifact"] == original["artifact"]
    assert event["result_files"] == original["result_files"]


def test_실패로_끝난_실행이_다시_와도_다시_돌리지_않고_failed_를_다시_발행한다(monkeypatch):
    created = []

    class CountingFailingLLM(FailingLLM):
        def __init__(self, **kwargs):
            created.append(kwargs)

    monkeypatch.setattr(runner, "LLM", CountingFailingLLM)
    message = submit_message(two_step_workflow(), mock=False)
    _, first = submit(message)

    outcome, again = submit(message, delivery_count=1)

    assert outcome == "ack"
    assert len(created) == 1
    assert again.types() == ["failed"]
    assert again.last()["snapshot"] == first.last()["snapshot"]
    assert again.last()["attempt"] == 2


def test_같은_SubmitRun_이_새_메시지로_한_번_더_와도_다시_돌리지_않는다(llm_counter):
    """백엔드 디스패처가 confirm 직전에 죽어 같은 실행을 두 번 보낸 경우 — 배달 횟수는 둘 다 0 이다."""
    message = submit_message(two_step_workflow())
    submit(message)
    created = llm_counter.created

    outcome, again = submit(message, delivery_count=0)

    assert outcome == "ack"
    assert llm_counter.created == created
    assert again.types() == ["succeeded"]
    assert again.last()["attempt"] == 1


def test_도는_실행의_SubmitRun_이_다시_오면_두_번째로_돌리지_않고_도는_실행이_끝난_뒤_ack(monkeypatch):
    llm = GatedLLM()
    monkeypatch.setattr(runner, "LLM", llm.factory)
    message = submit_message(two_step_workflow(), mock=False)
    execution_id = message["execution_id"]
    first_publisher, second_publisher = FakePublisher(), FakePublisher()
    first = Worker(lambda: messaging.handle_submit(encode(message), 0, first_publisher))
    second = Worker(lambda: messaging.handle_submit(encode(message), 1, second_publisher),
                    after=lambda: TestClient(app).get(f"/v1/runs/{execution_id}").json().get("status"))

    first.start()
    try:
        wait_for(lambda: llm.entered.is_set() or not first.is_alive())
        assert llm.entered.is_set(), f"첫 배달이 실행을 시작하지 않았다: {first.result!r} {first.error!r}"
        second.start()
        second.join(0.5)
        assert second.is_alive(), f"도는 실행이 끝나기 전에 돌려주었다: {second.result!r} {second.error!r}"
    finally:
        llm.gate.set()
    first.join(20)
    second.join(20)

    assert (first.error, second.error) == (None, None)
    assert (first.result, second.result) == ("ack", "ack")
    assert llm.calls == 2                                            # 노드 2개를 한 번씩 — 두 번째 실행은 없었다
    assert "accepted" not in second_publisher.types() and "progress" not in second_publisher.types()
    assert second.after_result in ("succeeded", "failed")           # 돌려준 때 실행은 끝나 있었다
    assert first_publisher.types()[-1] == "succeeded"


# --- R15 · R16 취소 -----------------------------------------------------------

def test_도는_실행을_취소하면_다음_노드부터_돌지_않고_CANCELLED_로_끝난다(monkeypatch):
    llm = GatedLLM()
    monkeypatch.setattr(runner, "LLM", llm.factory)
    message = submit_message(two_step_workflow(), mock=False)
    publisher = FakePublisher()
    worker = Worker(lambda: messaging.handle_submit(encode(message), 0, publisher))

    worker.start()
    try:
        wait_for(lambda: llm.entered.is_set() or not worker.is_alive())
        assert llm.entered.is_set(), f"실행이 시작되지 않았다: {worker.result!r} {worker.error!r}"
        cancel(message["execution_id"])
    finally:
        llm.gate.set()
    worker.join(20)

    final = publisher.last()
    statuses = {node["id"]: node["status"] for node in final["snapshot"]["nodes"]}
    assert worker.error is None and worker.result == "ack"
    assert final["type"] == "failed"
    assert final["snapshot"]["error"]["code"] == "CANCELLED"
    assert final["snapshot"]["error"]["fault"] == "platform"
    assert final["snapshot"]["failure"]["fault"] == "platform"
    assert llm.calls == 1                                            # 두 번째 노드는 모델을 부르지 않았다
    assert statuses["a"] == "succeeded" and statuses["b"] != "succeeded"


def test_받기_전에_취소된_실행은_돌리지_않고_이벤트_없이_ack(llm_counter):
    message = submit_message(two_step_workflow())
    cancel(message["execution_id"])

    outcome, publisher = submit(message)

    assert outcome == "ack"
    assert publisher.attempts == []
    assert llm_counter.created == 0


# --- R17 · R18 발행 실패 -------------------------------------------------------

@pytest.mark.parametrize("final_type", ["succeeded", "failed", "rejected"])
def test_마지막_이벤트_발행이_실패하면_requeue(final_type, monkeypatch):
    if final_type == "failed":
        monkeypatch.setattr(runner, "LLM", FailingLLM)
    message = submit_message(two_step_workflow(), mock=final_type != "failed",
                             mode="dry" if final_type == "rejected" else "run")
    publisher = FakePublisher(fail_types={final_type})

    outcome = messaging.handle_submit(encode(message), 0, publisher)

    assert outcome == "requeue"
    assert publisher.attempts[-1][1]["type"] == final_type


def test_requeue_로_다시_온_끝난_실행은_다시_돌리지_않고_마지막_이벤트만_다시_발행해_ack(llm_counter):
    message = submit_message(two_step_workflow())
    assert messaging.handle_submit(encode(message), 0, FakePublisher(fail_types={"succeeded"})) == "requeue"

    outcome, again = submit(message, delivery_count=1)

    assert outcome == "ack"
    assert llm_counter.created == 1
    assert again.types() == ["succeeded"]
    assert again.last()["attempt"] == 2


def test_accepted_progress_발행이_실패해도_실행은_계속되고_마지막_이벤트가_되면_ack(client):
    message = submit_message(two_step_workflow())
    publisher = FakePublisher(fail_types={"accepted", "progress"})

    outcome = messaging.handle_submit(encode(message), 0, publisher)

    assert outcome == "ack"
    assert publisher.attempts[0][1]["type"] == "accepted"
    assert publisher.types() == ["succeeded"]
    assert client.get(f"/v1/runs/{message['execution_id']}").json()["status"] == "succeeded"


# --- R19 파일 -----------------------------------------------------------------

def test_files_와_ref_files_는_볼륨_기준으로_풀어_원래_자리_그대로_러너에_넘긴다(volumes, monkeypatch):
    user_file = volumes.store / "executions" / "e1" / "메모.md"
    ref_file = volumes.store / "nodes" / "b" / "r1" / "가이드.md"
    for path, text in ((user_file, "손잡이는 대나무"), (ref_file, "색은 초록")):
        path.parent.mkdir(parents=True)
        path.write_text(text, encoding="utf-8")
    before = sorted(volumes.store.rglob("*"))
    seen: dict = {}
    real_execute = runner.execute

    def spy(*args, **kwargs):
        seen.update(inspect.signature(real_execute).bind(*args, **kwargs).arguments)
        return real_execute(*args, **kwargs)

    monkeypatch.setattr(runner, "execute", spy)
    message = submit_message(two_step_workflow(), files=["executions/e1/메모.md"],
                             ref_files={"b": ["nodes/b/r1/가이드.md"]})

    outcome, publisher = submit(message)

    user_files = ([seen["file_path"]] if seen.get("file_path") else []) + list(seen.get("files") or [])
    request = (runner.OUT_ROOT / message["execution_id"] / "nodes" / "b.request.md").read_text(encoding="utf-8")
    assert outcome == "ack" and publisher.last()["type"] == "succeeded"
    assert [Path(p).resolve() for p in user_files] == [user_file.resolve()]          # 임시 폴더로 복사하지 않았다
    assert {node: [Path(p).resolve() for p in paths] for node, paths in seen["ref_files"].items()} == {
        "b": [ref_file.resolve()]}
    assert "### 메모.md\n손잡이는 대나무" in request and "### 가이드.md\n색은 초록" in request
    assert sorted(volumes.store.rglob("*")) == before                              # 지우지도 더하지도 않았다
    assert user_file.read_text(encoding="utf-8") == "손잡이는 대나무"


# --- R20 HTTP 정리 ------------------------------------------------------------

def test_POST_v1_runs_는_없다(client):
    res = client.post("/v1/runs", data={"workflow": json.dumps(two_step_workflow(), ensure_ascii=False),
                                        "mock": "true"})

    assert res.status_code in (404, 405)


def test_ENGINE_BUSY_로_거절하는_경로가_없다():
    for module in (api, messaging):
        assert "ENGINE_BUSY" not in Path(module.__file__).read_text(encoding="utf-8"), module.__name__


def test_동시_실행_상한은_메시지_처리를_막지_않는다(monkeypatch):
    """상한은 prefetch 가 건다. 메시지를 받았으면 자리 계산 없이 돈다."""
    monkeypatch.setattr(api, "MAX_ACTIVE_RUNS", 0, raising=False)
    monkeypatch.setattr(api, "_active", 10 ** 6, raising=False)

    outcome, publisher = submit(submit_message(two_step_workflow()))

    assert outcome == "ack"
    assert publisher.types()[0] == "accepted" and publisher.types()[-1] == "succeeded"


def test_health_는_status_ok_에_broker_상태를_더한다(client):
    body = client.get("/v1/health").json()

    assert body["status"] == "ok"
    assert body.get("broker") == "disconnected"         # lifespan 을 돌리지 않은 TestClient — 붙은 적이 없다


# --- R21 보관 -----------------------------------------------------------------

def test_보관_기한이_지난_실행을_지울_때_결과_볼륨_폴더도_지운다(volumes, monkeypatch, client):
    message = submit_message(two_step_workflow())
    run_id = message["execution_id"]
    submit(message)
    folder = volumes.results / run_id
    assert (folder / "artifact.json").exists()
    monkeypatch.setattr(api, "RUN_RETENTION_SECONDS", -1)

    assert client.get(f"/v1/runs/{run_id}").status_code == 404     # 조회가 보관 기한 정리를 돌린다

    assert not folder.exists()


def test_보관_기한이_지나면_취소_기억도_지운다(monkeypatch, client):
    message = submit_message(two_step_workflow())
    cancel(message["execution_id"])
    monkeypatch.setattr(api, "RUN_RETENTION_SECONDS", -1)
    client.get("/v1/runs/unknown-run")                               # 조회가 보관 기한 정리를 돌린다
    monkeypatch.setattr(api, "RUN_RETENTION_SECONDS", 24 * 3600)

    outcome, publisher = submit(message)

    assert outcome == "ack"
    assert publisher.types()[0] == "accepted" and publisher.types()[-1] == "succeeded"


# --- R22 토폴로지 ----------------------------------------------------------------

class FakeExchange:
    def __init__(self, name: str):
        self.name = name


class FakeQueue:
    def __init__(self, channel: "FakeChannel", name: str | None):
        self.channel = channel
        self.name = name

    async def bind(self, exchange, routing_key=None, *, arguments=None, timeout=None):
        """aio-pika `Queue.bind` 와 같은 모양 — exchange 는 객체나 이름."""
        self.channel.bindings.append((self.name, getattr(exchange, "name", exchange), routing_key))


class FakeChannel:
    """aio-pika `Channel` 의 선언 호출을 기록한다. 시그니처는 aio-pika 10 그대로다."""

    def __init__(self):
        self.exchanges: dict[str, dict] = {}
        self.queues: dict[str | None, dict] = {}
        self.bindings: list[tuple] = []

    async def declare_exchange(self, name, type=ExchangeType.DIRECT, *, durable=False, auto_delete=False,
                               internal=False, passive=False, arguments=None, timeout=None):
        self.exchanges[name] = {"type": ExchangeType(type).value, "durable": durable, "auto_delete": auto_delete,
                                "internal": internal, "passive": passive, "arguments": arguments or {}}
        return FakeExchange(name)

    async def declare_queue(self, name=None, *, durable=False, exclusive=False, passive=False, auto_delete=False,
                            arguments=None, timeout=None):
        self.queues[name] = {"durable": durable, "exclusive": exclusive, "passive": passive,
                             "auto_delete": auto_delete, "arguments": arguments or {}}
        return FakeQueue(self, name)


def _declared() -> FakeChannel:
    channel = FakeChannel()
    asyncio.run(messaging.declare_topology(channel))
    return channel


def test_exchange_는_contract_1장의_이름과_타입으로_durable_하게_선언한다():
    declared = _declared()

    assert {name: (spec["type"], spec["durable"]) for name, spec in declared.exchanges.items()} == {
        "abc.runs": ("direct", True),
        "abc.runs.dlx": ("fanout", True),
        "abc.control": ("fanout", True),
        "abc.events": ("topic", True),
        "abc.events.dlx": ("fanout", True),
    }
    for name, spec in declared.exchanges.items():
        assert (spec["auto_delete"], spec["internal"], spec["passive"], spec["arguments"]) == (False, False, False, {}), name


def test_queue_는_contract_1장의_인자로_선언하고_런타임_전용_control_큐는_여기서_만들지_않는다():
    declared = _declared()

    assert {name: spec["arguments"] for name, spec in declared.queues.items()} == {
        "abc.runs.submit": {"x-queue-type": "quorum", "x-message-ttl": 600000, "x-delivery-limit": 1,
                            "x-dead-letter-exchange": "abc.runs.dlx"},
        "abc.runs.dead": {"x-queue-type": "quorum"},
        "abc.events.backend": {"x-queue-type": "quorum", "x-single-active-consumer": True,
                               "x-dead-letter-exchange": "abc.events.dlx"},
        "abc.events.backend.dlq": {"x-queue-type": "quorum"},
    }
    for name, spec in declared.queues.items():
        assert (spec["durable"], spec["exclusive"], spec["auto_delete"], spec["passive"]) == (True, False, False, False), name


def test_숫자_인자는_문자열이_아니라_정수로_보낸다():
    declared = _declared()

    submit_args = declared.queues["abc.runs.submit"]["arguments"]
    backend_args = declared.queues["abc.events.backend"]["arguments"]

    assert type(submit_args["x-message-ttl"]) is int
    assert type(submit_args["x-delivery-limit"]) is int
    assert backend_args["x-single-active-consumer"] is True


def test_묶임은_contract_1장_그대로다():
    declared = _declared()

    routes = {(queue, exchange): routing_key for queue, exchange, routing_key in declared.bindings}

    assert set(routes) == {("abc.runs.submit", "abc.runs"), ("abc.runs.dead", "abc.runs.dlx"),
                           ("abc.events.backend", "abc.events"), ("abc.events.backend.dlq", "abc.events.dlx")}
    assert routes[("abc.runs.submit", "abc.runs")] == "submit"
    assert routes[("abc.events.backend", "abc.events")] == "run.#"
