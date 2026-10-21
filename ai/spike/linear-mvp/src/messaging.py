"""실행 메시지 처리 — SubmitRun 을 받아 실행하고 RunEvent 를 발행한다(ADR-0001, ai/docs/integration-guide.md §4).

전송(aio-pika)과 떼어 놓은 처리 로직이다. 브로커 모듈(src/broker.py)이 받은 메시지를 스레드에서 이 함수들에 넘기고,
돌려받은 "ack" · "requeue" · "reject" 대로 이벤트 루프에서 ack · nack 한다. 테스트는 가짜 발행자로 동기로 부른다.

한 배달(delivery)의 이벤트는 accepted(seq=1) → progress … → 마지막 이벤트(succeeded · failed · rejected) 순서다.
마지막 이벤트가 confirm 되어야 "ack" 다. 같은 실행이 다시 오면 다시 돌리지 않는다 — 끝났으면 마지막 이벤트만 다시 내고,
도는 중이면 끝나기를 기다린다. 기록은 이 프로세스 메모리에만 있다(run_store 와 같은 보관 기한).
"""
from __future__ import annotations

import json
import logging
import os
import re
import shutil
import threading
import time
from dataclasses import dataclass, field
from datetime import UTC, datetime
from pathlib import Path, PureWindowsPath
from typing import Any, Protocol

from aio_pika import ExchangeType

from . import errors, graph, run_store, runner

log = logging.getLogger(__name__)

ROOT = Path(__file__).resolve().parents[1]

# 백엔드 file-store 볼륨을 읽기 전용으로 마운트한 곳. SubmitRun 의 files · ref_files 는 이 기준 상대 경로다
FILE_STORE_ROOT: Path = Path(os.getenv("FILE_STORE_ROOT") or ROOT / "file-store")
# 결과 볼륨. <RESULTS_ROOT>/<execution_id>/artifact.json · report.pdf · report.html
RESULTS_ROOT: Path = Path(os.getenv("RESULTS_ROOT") or ROOT / "results")

# ai/docs/integration-guide.md §4.1 토폴로지
RUNS_EXCHANGE = "abc.runs"
RUNS_DLX = "abc.runs.dlx"
CONTROL_EXCHANGE = "abc.control"
EVENTS_EXCHANGE = "abc.events"
EVENTS_DLX = "abc.events.dlx"
SUBMIT_QUEUE = "abc.runs.submit"
DEAD_QUEUE = "abc.runs.dead"
EVENTS_QUEUE = "abc.events.backend"
EVENTS_DLQ = "abc.events.backend.dlq"

EXECUTION_ID = re.compile(r"[A-Za-z0-9-]{1,64}")
MODES = ("run", "test")
PROGRESS_EVENTS = ("run.started", "node.started", "node.succeeded", "node.failed")


class EventPublisher(Protocol):
    def publish(self, routing_key: str, message: dict, message_id: str) -> None:
        """브로커 confirm 까지 기다린다. 실패(nack · 끊김)면 예외를 던진다."""
        ...


# 아래 기록은 모두 run_store.lock 안에서만 읽고 쓴다
_finals: dict[str, dict] = {}                 # execution_id → {"event": 마지막 이벤트 본문, "confirmed": bool}
_last_seq: dict[str, dict[int, int]] = {}     # execution_id → attempt → 마지막으로 쓴 seq
_inflight: dict[str, threading.Event] = {}    # 이 프로세스에서 도는 실행 → 끝나면 켜지는 표시
_cancelled: dict[str, float] = {}             # 받기 전에 취소된 실행 → 취소 받은 시각(epoch)


class _Rejected(Exception):
    """실행 전에 거절한다 — `rejected` 이벤트 하나로 끝난다(R7 ~ R11)."""

    def __init__(self, code: str, detail: str, errors_: list | None = None):
        super().__init__(detail)
        self.code = code
        self.detail = detail
        self.errors = errors_


@dataclass
class _Plan:
    workflow: dict
    mode: str
    mock: bool
    input_payload: dict | None
    comment: str
    files: list[str] = field(default_factory=list)
    ref_files: dict[str, list[str]] = field(default_factory=dict)


class _Delivery:
    """SubmitRun 한 배달. attempt 는 배달 횟수 + 1 이고, seq 는 같은 (execution_id, attempt) 안에서 1 부터 는다.

    seq 는 발행에 실패해도 쓴 것으로 친다 — confirm 만 못 받고 실제로는 나갔을 수 있어서다.
    같은 attempt 로 다시 온 배달(nack 로 돌아온 메시지는 배달 횟수가 늘지 않는다)은 앞 배달의 seq 다음부터 센다.
    """

    def __init__(self, execution_id: str, mode: Any, attempt: int, publisher: EventPublisher):
        self.execution_id = execution_id
        self.mode = mode
        self.attempt = attempt
        self.publisher = publisher

    def publish(self, type_: str, snapshot: dict, artifact: Any = None, result_files: dict | None = None) -> bool:
        with run_store.lock:
            seqs = _last_seq.setdefault(self.execution_id, {})
            seq = seqs.get(self.attempt, 0) + 1
            seqs[self.attempt] = seq

        event = {
            "execution_id": self.execution_id,
            "mode": self.mode,
            "attempt": self.attempt,
            "seq": seq,
            "type": type_,
            "occurred_at": _utc_now(),
            "snapshot": snapshot,
            "artifact": artifact,
            "result_files": result_files,
        }
        message_id = f"{self.execution_id}:{self.attempt}:{seq}"

        try:
            self.publisher.publish(f"run.{type_}", event, message_id)
        except Exception as e:      # confirm 실패 · 끊김. 마지막 이벤트면 호출자가 requeue 로 돌린다
            log.warning("RunEvent 발행 실패 %s %s: %s", message_id, type_, e)
            return False

        return True


def handle_submit(body: bytes, delivery_count: int, publisher: EventPublisher) -> str:
    """SubmitRun 하나를 끝까지 처리하고 "ack" | "requeue" | "reject" 를 돌려준다. 실행은 이 호출 안에서 동기로 돈다."""
    message = _parse(body)

    if message is None:
        return "reject"         # R12 — 다시 넣어도 읽을 수 없다. dead letter 로 보낸다

    execution_id = message["execution_id"]
    delivery = _Delivery(execution_id, message.get("mode"), delivery_count + 1, publisher)
    waited = False

    while True:
        state, found = _claim(execution_id)

        if state != "running":
            break

        found.wait()            # R14 — 두 번째로 돌리지 않고 도는 실행이 끝나기를 기다린다
        waited = True

    if state == "cancelled":
        return "ack"            # R16 — 받기 전에 취소됐다. 돌리지도 알리지도 않는다

    if state == "finished":
        if waited and found["confirmed"]:
            return "ack"        # R14 — 도는 실행이 끝을 알렸다. 이 배달도 함께 ack
        return _republish(found, delivery)          # R13 — 끝난 실행. 마지막 이벤트만 이번 attempt 로 다시

    try:
        return _process(message, delivery)
    finally:
        with run_store.lock:
            _inflight.pop(execution_id, None)
        found.set()


def handle_cancel(body: bytes) -> None:
    """CancelRun 하나를 처리한다. 도는 실행은 다음 노드부터 멈추고, 아직 받지 않은 실행은 기억해 둔다(R15 · R16)."""
    message = _parse(body)

    if message is None:
        log.warning("읽을 수 없는 CancelRun 을 버린다")
        return

    execution_id = message["execution_id"]

    with run_store.lock:
        run = run_store.runs.get(execution_id)
        if run is None:
            _cancelled[execution_id] = time.time()
        elif run.get("status") not in ("succeeded", "failed"):
            run["cancel_requested"] = True


async def declare_topology(channel) -> None:
    """ai/docs/integration-guide.md §4.1 의 exchange · queue · binding 을 선언한다(런타임 전용 control 큐 제외).

    백엔드도 같은 인자로 선언한다(멱등). 한 글자라도 다르면 브로커가 PRECONDITION_FAILED 로 채널을 닫는다.
    숫자 인자는 정수로 보낸다 — 문자열은 다른 인자로 취급된다.
    """
    runs = await channel.declare_exchange(RUNS_EXCHANGE, ExchangeType.DIRECT, durable=True)
    runs_dlx = await channel.declare_exchange(RUNS_DLX, ExchangeType.FANOUT, durable=True)
    await channel.declare_exchange(CONTROL_EXCHANGE, ExchangeType.FANOUT, durable=True)
    events = await channel.declare_exchange(EVENTS_EXCHANGE, ExchangeType.TOPIC, durable=True)
    events_dlx = await channel.declare_exchange(EVENTS_DLX, ExchangeType.FANOUT, durable=True)

    submit = await channel.declare_queue(SUBMIT_QUEUE, durable=True, arguments={
        "x-queue-type": "quorum", "x-message-ttl": 600_000, "x-delivery-limit": 1,
        "x-dead-letter-exchange": RUNS_DLX})
    dead = await channel.declare_queue(DEAD_QUEUE, durable=True, arguments={"x-queue-type": "quorum"})
    backend = await channel.declare_queue(EVENTS_QUEUE, durable=True, arguments={
        "x-queue-type": "quorum", "x-single-active-consumer": True,
        "x-dead-letter-exchange": EVENTS_DLX})
    dlq = await channel.declare_queue(EVENTS_DLQ, durable=True, arguments={"x-queue-type": "quorum"})

    await submit.bind(runs, routing_key="submit")
    await dead.bind(runs_dlx, routing_key="")
    await backend.bind(events, routing_key="run.#")
    await dlq.bind(events_dlx, routing_key="")


def evict_expired(retention_seconds: float) -> None:
    """보관 기한이 지난 끝난 실행을 메모리 · out/ · 결과 볼륨에서 지우고, 같은 기한이 지난 취소 기억도 지운다(R21)."""
    now = time.time()
    expired: list[tuple[str, dict | None]] = []

    with run_store.lock:
        for run_id, finished in list(run_store.finished_at.items()):
            if now - finished < retention_seconds:
                continue
            run_store.finished_at.pop(run_id, None)
            _finals.pop(run_id, None)
            _last_seq.pop(run_id, None)
            expired.append((run_id, run_store.runs.pop(run_id, None)))

        for execution_id, at in list(_cancelled.items()):
            if now - at >= retention_seconds:
                _cancelled.pop(execution_id, None)

    for run_id, run in expired:
        if run and run.get("out_dir"):
            shutil.rmtree(run["out_dir"], ignore_errors=True)
        shutil.rmtree(RESULTS_ROOT / run_id, ignore_errors=True)       # run_id 는 받을 때 형식을 검사했다


# --- 처리 ----------------------------------------------------------------------

def _claim(execution_id: str) -> tuple[str, Any]:
    """이 실행을 이 배달이 맡아도 되는가. 맡으면 끝날 때 켤 표시를 남긴다.

    ("running", 끝나면 켜지는 표시) · ("finished", 마지막 이벤트 기록) · ("cancelled", None) · ("new", 이 배달의 표시)
    """
    with run_store.lock:
        if execution_id in _inflight:
            return "running", _inflight[execution_id]

        if execution_id in _finals:
            return "finished", _finals[execution_id]

        if execution_id in _cancelled:
            return "cancelled", None

        done = _inflight[execution_id] = threading.Event()
        return "new", done


def _process(message: dict, delivery: _Delivery) -> str:
    execution_id = delivery.execution_id

    try:
        plan = _plan(message)
    except _Rejected as rejection:
        return _finish(_rejected_run(message, rejection), delivery, "rejected")

    run = run_store.queued(execution_id, plan.workflow, mock=plan.mock, mode=plan.mode)

    with run_store.lock:
        run["cancel_requested"] = execution_id in _cancelled     # 받는 사이에 온 취소
        run_store.runs[execution_id] = run

    delivery.publish("accepted", run_store.public(run))
    _execute(run, plan, delivery)

    if run["status"] != "succeeded":
        return _finish(run, delivery, "failed")

    try:
        result_files = _write_results(run)
    except OSError as e:        # 결과 볼륨에 못 쓰면 백엔드가 결과물을 읽을 수 없다 — 플랫폼 탓 실패로 닫는다
        log.exception("결과 볼륨에 쓰지 못했다: %s", execution_id)
        _fail_platform(run, "RUNTIME_INTERNAL", f"결과 볼륨에 쓰지 못했습니다: {e}")
        return _finish(run, delivery, "failed")

    return _finish(run, delivery, "succeeded", artifact=run["result"].get("envelope"), result_files=result_files)


def _execute(run: dict, plan: _Plan, delivery: _Delivery) -> None:
    """러너를 돌린다. 실행 시작 · 노드 시작 · 성공 · 실패마다 progress 를 낸다(발행 실패는 실행을 막지 않는다)."""

    def on_event(name: str, _payload: dict) -> None:
        if name not in PROGRESS_EVENTS:
            return
        try:
            delivery.publish("progress", run_store.public(run))
        except Exception:       # 진행 알림이 실행을 깨뜨리지 않게
            log.exception("progress 를 만들지 못했다: %s", delivery.execution_id)

    try:
        runner.execute(plan.workflow, comment=plan.comment, files=plan.files or None, mock=plan.mock,
                       run_id=delivery.execution_id, input_payload=plan.input_payload, state=run,
                       mode=plan.mode, ref_files=plan.ref_files or None, on_event=on_event)
    except Exception as e:      # 러너가 삼키지 못한 예외까지 방어
        log.exception("실행 중 내부 오류: %s", delivery.execution_id)
        _fail_platform(run, "RUNTIME_INTERNAL", str(e))


def _finish(run: dict, delivery: _Delivery, type_: str, *, artifact: Any = None,
            result_files: dict | None = None) -> str:
    """마지막 이벤트를 기록하고 낸다. confirm 되면 "ack", 아니면 "requeue" — 다시 오면 기록한 것을 다시 낸다."""
    snapshot = json.loads(json.dumps(run_store.public(run, include_node_output=True), ensure_ascii=False))
    final = {"event": {"type": type_, "snapshot": snapshot, "artifact": artifact, "result_files": result_files},
             "confirmed": False}

    with run_store.lock:
        run_store.runs[delivery.execution_id] = run
        run_store.finished_at[delivery.execution_id] = time.time()
        _finals[delivery.execution_id] = final

    if not delivery.publish(type_, snapshot, artifact, result_files):
        return "requeue"

    with run_store.lock:
        final["confirmed"] = True

    return "ack"


def _republish(final: dict, delivery: _Delivery) -> str:
    event = final["event"]

    if not delivery.publish(event["type"], event["snapshot"], event["artifact"], event["result_files"]):
        return "requeue"

    with run_store.lock:
        final["confirmed"] = True

    return "ack"


def _write_results(run: dict) -> dict:
    """succeeded 를 내기 전에 결과 볼륨에 쓴다(ai/docs/integration-guide.md §4.5). 돌려주는 값은 있는 파일만 담은 result_files."""
    run_id = run["run_id"]
    folder = RESULTS_ROOT / run_id
    shutil.rmtree(folder, ignore_errors=True)       # 앞선 시도가 남긴 파일이 이번 결과로 읽히지 않게
    folder.mkdir(parents=True)

    result = run["result"] or {}
    (folder / "artifact.json").write_text(json.dumps(result.get("envelope"), ensure_ascii=False, indent=2),
                                          encoding="utf-8")
    files = {"json": f"{run_id}/artifact.json"}

    for key, name in (("pdf", "report.pdf"), ("html", "report.html")):
        source = result.get(key)
        if source and Path(source).is_file():
            shutil.copyfile(source, folder / name)
            files[key] = f"{run_id}/{name}"

    return files


def _fail_platform(run: dict, code: str, detail: str) -> None:
    """러너 밖에서 난 실패. 플랫폼 탓이라 백엔드가 전액 환원한다."""
    run.update(status="failed", finished_at=runner._now(),
               error={**errors.describe(code, detail=detail), "node_id": None},
               failure={"node_id": None, "error_code": code, "fault": "platform", "affects_success_rate": False})


def _rejected_run(message: dict, rejection: _Rejected) -> dict:
    described = errors.describe(rejection.code, detail=rejection.detail)
    error = {**described, "node_id": None}

    if rejection.errors is not None:
        error["errors"] = rejection.errors

    failure = {"node_id": None, "error_code": rejection.code, "fault": described["fault"],
               "affects_success_rate": False}

    return run_store.rejected(message["execution_id"], message.get("workflow"),
                              mock=message.get("mock") is True, mode=message.get("mode"),
                              error=error, failure=failure, finished=runner._now())


# --- 받은 메시지 읽기 · 검사 ---------------------------------------------------------

def _parse(body: bytes) -> dict | None:
    """JSON 객체이고 execution_id 가 형식에 맞으면 그 dict. 아니면 None(R12)."""
    try:
        message = json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, ValueError):
        return None

    if not isinstance(message, dict):
        return None

    execution_id = message.get("execution_id")

    if not isinstance(execution_id, str) or not EXECUTION_ID.fullmatch(execution_id):
        return None

    return message


def _plan(message: dict) -> _Plan:
    """실행 전에 걸리는 오류를 거른다(R7 ~ R11). 통과하면 러너에 넘길 값."""
    mode = message.get("mode")

    if mode not in MODES:
        raise _Rejected("INVALID_MODE", f"mode 는 run 또는 test 입니다 (받은 값: {mode!r})")

    wf = message.get("workflow")

    if not isinstance(wf, dict):
        raise _Rejected("INVALID_WORKFLOW", f"workflow 는 객체여야 합니다 (받은 형식: {type(wf).__name__})")

    try:
        report = graph.validate(wf)
    except Exception as e:
        raise _Rejected("INVALID_WORKFLOW", f"정의를 읽지 못했습니다: {e}") from e

    if not report["valid"]:
        first = report["errors"][0]
        raise _Rejected("INVALID_WORKFLOW", f"{first['code']}: {first['message']}", report["errors"])

    input_payload = message.get("input")

    if input_payload is not None and not isinstance(input_payload, dict):
        raise _Rejected("INVALID_INPUT_JSON",
                        f"input 은 JSON 객체 또는 null 이어야 합니다 (받은 형식: {type(input_payload).__name__})")

    files = [_resolve(raw) for raw in _path_list(message.get("files"), "files")]
    ref_files = _ref_files(message.get("ref_files"))
    _check_user_files(files)
    comment = message.get("comment")

    return _Plan(workflow=wf, mode=mode, mock=message.get("mock") is True, input_payload=input_payload,
                 comment=comment if isinstance(comment, str) else "",
                 files=[str(p) for p in files],
                 ref_files={node: [str(p) for p in paths] for node, paths in ref_files.items()})


def _path_list(value: Any, name: str) -> list:
    if value is None:
        return []

    if not isinstance(value, list):
        raise _Rejected("FILE_PATH_REJECTED", f"{name} 는 경로 배열이어야 합니다")

    return value


def _ref_files(value: Any) -> dict[str, list[Path]]:
    if value is None:
        return {}

    if not isinstance(value, dict):
        raise _Rejected("FILE_PATH_REJECTED", "ref_files 는 노드 id → 경로 배열 객체여야 합니다")

    return {str(node): [_resolve(raw) for raw in _path_list(paths, f"ref_files.{node}")]
            for node, paths in value.items()}


def _resolve(raw: Any) -> Path:
    """file-store 기준 상대 경로를 푼다. 절대 경로 · `..` · 볼륨 밖 · 없는 파일은 거절한다(R10).

    `..` 는 풀어서 안쪽이어도 받지 않는다. 경로는 백엔드가 만들므로 이런 경로는 플랫폼 쪽 잘못이다.
    """
    if not isinstance(raw, str) or not raw.strip():
        raise _Rejected("FILE_PATH_REJECTED", f"경로가 비었거나 문자열이 아닙니다: {raw!r}")

    if raw.startswith(("/", "\\")) or PureWindowsPath(raw).drive or Path(raw).is_absolute():
        raise _Rejected("FILE_PATH_REJECTED", f"절대 경로는 받지 않습니다: {raw}")

    if ".." in re.split(r"[\\/]+", raw):
        raise _Rejected("FILE_PATH_REJECTED", f"'..' 가 든 경로는 받지 않습니다: {raw}")

    root = FILE_STORE_ROOT.resolve()
    path = (root / raw).resolve()       # 심볼릭 링크까지 풀어 본다

    if not path.is_relative_to(root):
        raise _Rejected("FILE_PATH_REJECTED", f"file-store 밖을 가리킵니다: {raw}")

    if not path.is_file():
        raise _Rejected("FILE_PATH_REJECTED", f"파일이 없습니다: {raw}")

    return path


def _check_user_files(files: list[Path]) -> None:
    """사용자 파일 형식 · 합계 크기(R11). 참고 파일은 실행 중 러너가 보고 REF_FILE_REJECTED 로 다룬다."""
    for path in files:
        if path.suffix.lower() not in runner.FILE_SUFFIXES:
            raise _Rejected("UNSUPPORTED_FILE_TYPE",
                            f"허용 확장자: {', '.join(sorted(runner.FILE_SUFFIXES))} (받은 값: {path.suffix or '없음'})")

    total = sum(path.stat().st_size for path in files)

    if total > runner.MAX_FILE_BYTES:
        raise _Rejected("FILE_TOO_LARGE",
                        f"사용자 파일 합계 최대 {runner.MAX_FILE_BYTES // 1024 // 1024}MB "
                        f"(받은 값: {total / 1024 / 1024:.1f}MB)")


def _utc_now() -> str:
    return datetime.now(UTC).isoformat(timespec="seconds").replace("+00:00", "Z")
