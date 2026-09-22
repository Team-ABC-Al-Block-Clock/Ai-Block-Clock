"""선형 워크플로우 러너 — 노드 순차 실행 · 이전 산출물 전달 · 로그 · PDF.

CLI 사용:
  uv run python -m src.runner --workflow report --file data/demo_scores.xlsx \
      --comment "이번 달은 전반적으로 난이도가 높았음" [--mock]

워크플로우 정의의 nodes 배열 순서가 곧 실행 순서다. 그래프 · 분기 · 스키마 없음.
API(src/api.py)는 execute() 를 직접 호출하고 on_event 로 진행 상황을 받는다.
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable

from dotenv import load_dotenv

from . import catalog, errors, pricing
from . import expand as expand_mod
from . import graph as graph_mod
from . import nodes as node_runner
from . import prompts
from .excel_to_text import excel_to_common_table, excel_to_markdown
from .llm import LLM
from .payload_md import payload_to_markdown
from .pdf import markdown_to_pdf

ROOT = Path(__file__).resolve().parents[1]
OUT_ROOT = ROOT / "out"
load_dotenv(ROOT / ".env")

ProgressHook = Callable[[str, dict], None]


def _now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def new_run_id() -> str:
    return datetime.now().strftime("%Y%m%d-%H%M%S-") + str(int(time.time() * 1000) % 1000).zfill(3)


def load_workflow(workflow_id: str) -> dict:
    """서버에 있는 예시 워크플로우를 읽는다. 백엔드는 이 형식을 요청 본문으로 그대로 보낸다."""
    return json.loads((ROOT / "workflows" / f"{workflow_id}.json").read_text(encoding="utf-8"))


def list_workflows() -> list[dict]:
    items = []
    for p in sorted((ROOT / "workflows").glob("*.json")):
        wf = json.loads(p.read_text(encoding="utf-8"))
        items.append({"id": wf.get("id", p.stem), "name": wf.get("name"), "node_count": len(wf.get("nodes", []))})
    return items


def resolve_prompts(wf: dict) -> dict:
    """prompt 값이 저장소 안의 .md 경로면 파일 내용으로 바꾼다. 백엔드가 보내는 원문은 그대로 둔다."""
    wf = json.loads(json.dumps(wf))  # 얕은 수정 방지

    def resolve(node: dict) -> None:
        p = str(node.get("prompt", ""))
        if p.endswith(".md"):
            candidate = ROOT / p        # `../../prompts/brand/…` 도 여기서 풀린다(공용 프롬프트)
            if candidate.exists():
                node["prompt"] = candidate.read_text(encoding="utf-8")
        for inner in (node.get("agent") or {}).get("nodes") or []:
            resolve(inner)              # 에이전트 안의 노드도 견본이면 파일을 가리킨다

    for node in wf.get("nodes", []):
        resolve(node)
    return wf


def validate_workflow(wf: dict) -> list[str]:
    errors = []
    nodes = wf.get("nodes")
    if not isinstance(nodes, list) or not nodes:
        return ["nodes 는 최소 1개 이상의 배열이어야 합니다"]
    seen = set()
    for i, n in enumerate(nodes):
        where = f"nodes[{i}]"
        if not n.get("id"):
            errors.append(f"{where}.id 가 없습니다")
        elif n["id"] in seen:
            errors.append(f"{where}.id 가 중복입니다: {n['id']}")
        else:
            seen.add(n["id"])
        if not str(n.get("prompt", "")).strip():
            errors.append(f"{where}.prompt 가 비어 있습니다")
        ctx = n.get("context", "prev")
        if ctx not in {"user", "prev", "prev+user"}:
            errors.append(f"{where}.context 는 user | prev | prev+user 중 하나여야 합니다 (받은 값: {ctx})")
    out_type = wf.get("output", {}).get("type", "pdf")
    if out_type not in {"pdf", "markdown"}:
        errors.append(f"output.type 은 pdf | markdown 이어야 합니다 (받은 값: {out_type})")
    return errors


def build_user_input(comment: str, file_path: str | Path | None) -> str:
    parts = []
    if comment:
        parts.append(f"## 강사 코멘트\n{comment.strip()}")
    if file_path:
        parts.append(f"## 성적표\n{excel_to_markdown(file_path)}")
    return "\n\n".join(parts) if parts else "(입력 없음)"


def build_message(node: dict, user_input: str, prev_output: str | None) -> str:
    ctx = node.get("context", "prev")
    if prev_output is None or ctx == "user":
        return user_input
    msg = f"## 이전 단계 결과\n{prev_output}"
    if ctx == "prev+user":
        msg += f"\n\n## 원본 입력\n{user_input}"
    return msg


_INPUT_JSON_TMPL = """## 입력
```json
{body}
```"""

_INPUT_TEXT_TMPL = """## 입력
{body}"""

_COMMENT_TMPL = """## 사용자 코멘트
{body}"""

_REQUEST_TMPL = """# system
{system}

# user
{user}"""


def build_json_message(payload_in: dict | None, comment: str, user_input: str) -> str:
    """JSON 노드에 넘길 메시지. 앞 노드의 payload + 사용자 코멘트만 넘어간다(계약 §6.2)."""
    parts = []
    if payload_in is not None:
        parts.append(_INPUT_JSON_TMPL.format(body=json.dumps(payload_in, ensure_ascii=False, indent=2)))
    elif user_input:
        parts.append(_INPUT_TEXT_TMPL.format(body=user_input))
    if comment:
        parts.append(_COMMENT_TMPL.format(body=comment.strip()))
    return "\n\n".join(parts) if parts else "(입력 없음)"



def _runtime_values(schema_id: str, node: dict) -> dict:
    """Runtime 이 채우는 필드. LLM 에게 날짜 · 기관명을 지어내게 하지 않는다."""
    fields = catalog.runtime_fields(schema_id)
    values: dict = {}
    if "issued_on" in fields:
        values["issued_on"] = datetime.now().strftime("%Y년 %m월 %d일")
    if "author_org" in fields:
        values["author_org"] = node.get("owner_name") or node.get("owner_id") or "ABC"
    return values


def execute(
    wf: dict,
    comment: str = "",
    file_path: str | Path | None = None,
    mock: bool = False,
    input_payload: dict | None = None,
    mock_overrides: dict | None = None,
    run_id: str | None = None,
    on_event: ProgressHook | None = None,
    verbose: bool = False,
    state: dict | None = None,
) -> dict:
    """워크플로우 하나를 끝까지 실행한다. 예외를 던지지 않고 결과 dict 의 status 로 성패를 알린다.

    state 를 주면 그 dict 를 제자리에서 갱신한다. 호출자(API)가 같은 객체를 들고 있으면
    실행 도중에도 노드별 진행 상황을 실시간으로 읽을 수 있다.
    """
    wf = resolve_prompts(wf)
    wf_canvas = wf                                      # 검증 · 상한은 사용자가 놓은 캔버스 기준으로 본다
    try:
        wf, _expand_stats = expand_mod.expand(wf)       # 실행은 펼친 그래프로 한다(계약 §5.2)
    except expand_mod.ExpandError:
        pass                                            # 아래 정의 검증이 같은 오류를 잡아 보고한다
    run_id = run_id or new_run_id()
    out_dir = OUT_ROOT / run_id
    (out_dir / "nodes").mkdir(parents=True, exist_ok=True)

    def emit(event: str, payload: dict) -> None:
        if on_event:
            on_event(event, payload)

    nodes = wf.get("nodes", [])
    result: dict = {
        "run_id": run_id,
        "workflow": {"id": wf.get("id"), "name": wf.get("name"), "node_count": len(nodes)},
        "mock": mock,
        "status": "running",
        "started_at": _now(),
        "finished_at": None,
        "total_duration_ms": None,
        "inputs": {"comment": comment, "file": Path(file_path).name if file_path else None},
        "nodes": [
            {"id": n.get("id"), "name": n.get("name") or n.get("id"), "status": "pending",
             "context": n.get("context", "prev"), "duration_ms": None,
             "schema_id": n.get("output_schema_id"), "repair_count": 0, "cost_abc": None,
             "agent_id": n.get("agent_id"),
             "input_tokens": None, "output_tokens": None, "retries": 0, "output": None,
             # 정산 입력값. 백엔드가 요청에 실어 보내면 그대로 되돌려준다
             "owner_id": n.get("owner_id"), "unit_price": n.get("unit_price"),
             "base_cost": n.get("base_cost"), "premium_rate": n.get("premium_rate")}
            for n in nodes
        ],
        "totals": {"input_tokens": 0, "output_tokens": 0, "cost_abc": 0.0},
        "contribution": [],
        "not_executed": [],
        "failure": None,
        "result": None,
        "error": None,
        "out_dir": str(out_dir),
    }
    if state is not None:          # 호출자가 준 객체를 그대로 쓰면 진행 상황이 실시간으로 보인다
        state.update(result)
        result = state

    try:
        llm = LLM(mock=mock, model=wf.get("model"))
    except Exception as e:
        result.update(status="failed", finished_at=_now(),
                      error={"code": "LLM_INIT_FAILED", "message": str(e), "node_id": None})
        _settlement(result, 0, "LLM_INIT_FAILED")
        _save(out_dir, result)
        emit("run.failed", {"run_id": run_id, "error": result["error"]})
        return result

    try:
        user_input = build_user_input(comment, file_path)
    except Exception as e:
        result.update(status="failed", finished_at=_now(),
                      error={"code": "FILE_PARSE_FAILED", "message": str(e)[:500], "node_id": None})
        _settlement(result, 0, "FILE_PARSE_FAILED")
        _save(out_dir, result)
        emit("run.failed", {"run_id": run_id, "error": result["error"]})
        return result
    (out_dir / "input.md").write_text(user_input, encoding="utf-8")
    _save(out_dir, result)
    emit("run.started", {"run_id": run_id, "total": len(nodes)})

    # 정의 자체가 틀렸으면 실행하지 않는다(계약 §5.7). 순환 · 상한 · 스키마 호환까지 여기서 걸린다
    report = graph_mod.validate(wf_canvas)
    if not report["valid"]:
        first = report["errors"][0]
        result.update(status="failed", finished_at=_now(), total_duration_ms=0,
                      error={**errors.describe("SCHEMA_VALIDATION_FAILED", fault="consumer",
                                               detail=f"{first['code']}: {first['message']}"),
                             "code": "INVALID_WORKFLOW", "node_id": first.get("node_id"),
                             "errors": report["errors"]})
        _settlement(result, 0, "SCHEMA_VALIDATION_FAILED", fault="consumer")
        _save(out_dir, result)
        emit("run.failed", {"run_id": run_id, "error": result["error"]})
        return result
    graph = graph_mod.build(wf)

    # 진입 노드(edges 의 to 에 없는 노드)는 사용자 입력을 받는다
    entry_schemas = [(n, graph.node(n).get("input_schema_id")) for n in graph.entries]
    first_input_schema = next((sid for _, sid in entry_schemas if sid), None)

    # 엑셀 · CSV 를 올렸고 진입 노드가 표를 받는다면 그대로 payload 로 바꾼다(H2A 최소 경로)
    if input_payload is None and file_path and first_input_schema == "common.table@1":
        try:
            input_payload = excel_to_common_table(file_path)
        except Exception as e:
            result.update(status="failed", finished_at=_now(), total_duration_ms=0,
                          error={**errors.describe("FILE_PARSE_FAILED", detail=str(e)[:500]),
                                 "node_id": graph.entries[0] if graph.entries else None})
            _settlement(result, 0, "FILE_PARSE_FAILED")
            _save(out_dir, result)
            emit("run.failed", {"run_id": run_id, "error": result["error"]})
            return result
    if first_input_schema:
        reason = ("입력값이 없습니다" if input_payload is None
                  else node_runner.check_input(first_input_schema, input_payload))
        if reason:
            result.update(status="failed", finished_at=_now(), total_duration_ms=0,
                          error={**errors.describe("SCHEMA_VALIDATION_FAILED", fault="consumer",
                                                   detail=reason),
                                 "node_id": graph.entries[0] if graph.entries else None})
            _settlement(result, 0, "SCHEMA_VALIDATION_FAILED", fault="consumer")
            _save(out_dir, result)
            emit("run.failed", {"run_id": run_id, "error": result["error"]})
            return result

    started = time.perf_counter()
    prev_output: str | None = None
    prev_payload: dict | None = input_payload
    last_envelope: dict | None = None

    entry_by_id = {n["id"]: e for n, e in zip(nodes, result["nodes"])}
    node_ids = [n["id"] for n in nodes]
    payloads: dict = {}                 # 노드 id → 낸 payload. fan-in 과 분기 조건이 여기서 읽는다
    branch_taken: dict = {}             # 분기 노드 id → 선택된 쪽
    skipped: dict = {}                  # 실행되지 않는 노드 id → 이유

    def fail(node_id, code, detail, fault=None, index=None):
        """노드 하나가 실패하면 실행을 멈춘다. 이후 노드는 not_executed 로 남는다."""
        result.update(status="failed", finished_at=_now(),
                      total_duration_ms=int((time.perf_counter() - started) * 1000),
                      error={**errors.describe(code, fault=fault, detail=detail),
                             "node_id": node_id})
        _settlement(result, index, code, fault=fault)
        _save(out_dir, result)
        emit("node.failed", {"run_id": run_id, "node_id": node_id})
        emit("run.failed", {"run_id": run_id, "error": result["error"]})
        if verbose:
            print(f"실패: {result['error']['message']}")
        return result

    for position, node_id in enumerate(graph.order):
        node = graph.node(node_id)
        entry = entry_by_id[node_id]
        index = node_ids.index(node_id)

        # 상위가 모두 죽은 노드는 돌지 않는다 — 분기 미선택이거나 상위 실패다
        if graph.incoming.get(node_id) and not graph_mod.live_edges(graph, node_id, payloads, branch_taken):
            reason = graph_mod.skip_reason(graph, node_id, payloads, branch_taken, skipped)
            skipped[node_id] = reason
            entry.update(status="skipped", skip_reason=reason)
            _save(out_dir, result)
            if verbose:
                print(f"[{position+1}/{len(graph.order)}] {entry['name']} … 건너뜀({reason})")
            continue

        entry.update(status="running", started_at=_now())
        _save(out_dir, result)
        emit("node.started", {"run_id": run_id, "index": index, "node_id": node_id})
        if verbose:
            print(f"[{position+1}/{len(graph.order)}] {entry['name']} … ", end="", flush=True)

        received = graph_mod.fan_in(graph, node_id, payloads, branch_taken)
        if received is None:
            received = input_payload

        # --- 분기 노드: LLM 을 부르지 않는다. 판정은 코드가 한다 ---
        if node.get("type") == "branch":
            try:
                taken = graph_mod.evaluate(node.get("condition") or {}, payloads)
            except graph_mod.ConditionInvalid as e:
                entry.update(status="failed", finished_at=_now())
                return fail(node_id, "BRANCH_CONDITION_INVALID", str(e), fault="consumer", index=index)
            branch_taken[node_id] = taken
            payloads[node_id] = received              # 받은 것을 그대로 넘긴다
            entry.update(status="succeeded", finished_at=_now(), duration_ms=0,
                         input_tokens=0, output_tokens=0, cost_abc=0.0, branch_taken=taken)
            _save(out_dir, result)
            emit("node.succeeded", {"run_id": run_id, "index": index, "node_id": node_id,
                                    "duration_ms": 0})
            if verbose:
                print("분기 " + ("예" if taken else "아니오"))
            continue

        schema_id = node.get("output_schema_id")

        # --- JSON 노드: 구조화 출력 · 검증 · 교정 · 봉투 ---
        if schema_id:
            # 합류(도착한 하나)는 검증하고, 병합(port 로 묶인 객체)은 건너뛴다
            single_upstream = len(graph_mod.live_edges(graph, node_id, payloads, branch_taken)) <= 1
            reason = None
            if received is not None and single_upstream:
                reason = node_runner.check_input(node.get("input_schema_id"), received)
            if reason:
                entry.update(status="failed", finished_at=_now())
                return fail(node_id, "SCHEMA_VALIDATION_FAILED", reason, fault="consumer", index=index)

            prompt = prompts.parse(node["prompt"])
            if prompt.templated:
                # 트랙 B 작성 형식 — user 블록의 자리표시자를 fan-in 결과로 채운다
                live = graph_mod.live_edges(graph, node_id, payloads, branch_taken)
                upstream_schema = (graph_mod.output_schema_of(graph, live[0]["from"])
                                   if len(live) == 1 else None)
                values = prompts.values_for(received, input_payload=input_payload, comment=comment,
                                            upstream_schema_id=upstream_schema)
                try:
                    message = prompt.render_user(values)
                except prompts.PlaceholderMissing as e:
                    entry.update(status="failed", finished_at=_now())
                    return fail(node_id, "PROMPT_TEMPLATE_INVALID",
                                f"프롬프트가 요구하는 값이 없습니다: {e}", fault="supplier", index=index)
            else:
                message = build_json_message(received, comment, user_input)
            system = prompt.system
            (out_dir / "nodes" / f"{node_id}.request.md").write_text(
                _REQUEST_TMPL.format(system=system, user=message), encoding="utf-8")
            nr = node_runner.run_llm_node(
                llm, run_id=run_id, node_id=node_id, schema_id=schema_id,
                system=system, message=message,
                runtime_values=_runtime_values(schema_id, node),
                mock_overrides=(mock_overrides or {}).get(schema_id))

            (out_dir / "nodes" / f"{node_id}.json").write_text(
                json.dumps(nr.envelope, ensure_ascii=False, indent=2), encoding="utf-8")
            meta = nr.envelope["meta"]
            entry.update(duration_ms=meta["duration_ms"], retries=meta["retry_count"],
                         repair_count=meta["repair_count"], schema_id=schema_id,
                         cost_abc=meta["cost_abc"],
                         input_tokens=meta["usage"]["input_tokens"],
                         output_tokens=meta["usage"]["output_tokens"])
            result["totals"]["input_tokens"] += meta["usage"]["input_tokens"]
            result["totals"]["output_tokens"] += meta["usage"]["output_tokens"]
            result["totals"]["cost_abc"] = round(result["totals"]["cost_abc"] + (meta["cost_abc"] or 0), 4)

            if not nr.ok:
                err = nr.error
                entry.update(status="failed", finished_at=_now())
                return fail(node_id, err["code"], err["message"], fault=err["fault"], index=index)

            boundary = expand_mod.boundary_of(node)
            if boundary:
                violation = node_runner.check_input(boundary["schema_id"], nr.payload)
                if violation:
                    entry.update(status="failed", finished_at=_now())
                    return fail(node_id, "AGENT_BOUNDARY_VIOLATION", violation,
                                fault="supplier", index=index)

            entry.update(status="succeeded", finished_at=_now(),
                         output=json.dumps(nr.payload, ensure_ascii=False))
            payloads[node_id] = nr.payload
            prev_output = payload_to_markdown(schema_id, nr.payload)
            last_envelope = nr.envelope
            _save(out_dir, result)
            emit("node.succeeded", {"run_id": run_id, "index": index, "node_id": node_id,
                                    "duration_ms": meta["duration_ms"]})
            if verbose:
                print(f"{meta['duration_ms']/1000:.1f}s  JSON {schema_id}  교정 {meta['repair_count']}회")
            continue

        # --- 스키마가 없는 노드: 기존 자유 텍스트 경로(하위 호환) ---
        message = build_message(node, user_input, prev_output)
        (out_dir / "nodes" / f"{node_id}.request.md").write_text(
            _REQUEST_TMPL.format(system=node["prompt"], user=message), encoding="utf-8")
        try:
            res = llm.generate(system=node["prompt"], user=message, label=node_id)
        except Exception as e:
            entry.update(status="failed", finished_at=_now())
            return fail(node_id, _error_code(e), str(e)[:500], index=index)

        (out_dir / "nodes" / f"{node_id}.md").write_text(res.text, encoding="utf-8")
        node_cost = pricing.cost_abc(res.model, res.input_tokens, res.output_tokens)
        entry.update(status="succeeded", finished_at=_now(), duration_ms=res.duration_ms,
                     input_tokens=res.input_tokens, output_tokens=res.output_tokens,
                     retries=res.retries, output=res.text, cost_abc=node_cost)
        result["totals"]["input_tokens"] += res.input_tokens
        result["totals"]["output_tokens"] += res.output_tokens
        result["totals"]["cost_abc"] = round(result["totals"]["cost_abc"] + node_cost, 4)
        prev_output = res.text
        payloads[node_id] = None
        _save(out_dir, result)
        emit("node.succeeded", {"run_id": run_id, "index": index, "node_id": node_id,
                                "duration_ms": res.duration_ms})
        if verbose:
            print(f"{res.duration_ms/1000:.1f}s  in {res.input_tokens} / out {res.output_tokens} tokens")


    output_cfg = wf.get("output", {}) or {}
    result["result"] = {"type": output_cfg.get("type", "pdf"), "markdown": prev_output, "pdf": None}
    if output_cfg.get("schema_id") and last_envelope:
        # 종단 봉투가 그대로 /artifact 로 나간다(계약 §6.2 · §7.5)
        result["result"]["schema_id"] = output_cfg["schema_id"]
        result["result"]["envelope"] = last_envelope
    if output_cfg.get("type", "pdf") == "pdf" and prev_output:
        try:
            pdf_path = markdown_to_pdf(prev_output, output_cfg.get("title") or wf.get("name", "리포트"),
                                       out_dir / "report.pdf")
            result["result"]["pdf"] = str(pdf_path)
        except Exception as e:
            result.update(status="failed", finished_at=_now(),
                          total_duration_ms=int((time.perf_counter() - started) * 1000),
                          error={"code": "PDF_RENDER_FAILED", "message": str(e)[:500], "node_id": None})
            _settlement(result, len(nodes) - 1, "PDF_RENDER_FAILED")
            _save(out_dir, result)
            emit("run.failed", {"run_id": run_id, "error": result["error"]})
            return result

    result.update(status="succeeded", finished_at=_now(),
                  total_duration_ms=int((time.perf_counter() - started) * 1000))
    _settlement(result, None, None)
    _save(out_dir, result)
    emit("run.succeeded", {"run_id": run_id})
    return result


def _error_code(e: Exception) -> str:
    code = getattr(e, "code", None) or getattr(e, "status_code", None)
    if code == 429:
        return "RATE_LIMITED"
    if code in {500, 502, 503, 504}:
        return "MODEL_API_ERROR"
    if "timeout" in str(e).lower():
        return "MODEL_TIMEOUT"
    return "MODEL_API_ERROR"


# 책임 구분 · 사용자 문구는 src/errors.py 하나에서 나온다(계약 §9.2).
def _fault_of(code: str) -> str:
    return errors.fault_of(code)


def _settlement(result: dict, failed_index: int | None, code: str | None,
                fault: str | None = None) -> None:
    """contribution · not_executed · failure 를 채운다.

    성공 노드는 원작자에게 정산하고, 실패 노드와 그 이후 미실행 구간은 수요자에게 환원한다.
    platform 책임일 때는 수요자에게 전액을 환원하되 성공 노드의 정산은 유지한다.
    플랫폼 과실로 공급자가 손해를 보지 않게 하려는 것이며, 차액은 플랫폼이 부담한다.
    """
    nodes = result["nodes"]
    result["contribution"] = [
        {"node_id": n["id"], "owner_id": n.get("owner_id"), "agent_id": n.get("agent_id"),
         "charged_abc": n.get("unit_price")}
        for n in nodes if n["status"] == "succeeded" and n.get("branch_taken") is None
    ]
    result["not_executed"] = [
        {"node_id": n["id"],
         "reason": n.get("skip_reason") or ("upstream_failed" if failed_index is not None else "not_reached"),
         "refund_abc": n.get("unit_price")}
        for n in nodes if n["status"] in {"pending", "skipped"}
    ]
    if failed_index is None:
        result["failure"] = None
        return

    fault = fault or _fault_of(code or "")
    succeeded = [n["id"] for n in nodes if n["status"] == "succeeded"]
    refund = [n["id"] for n in nodes[failed_index:]] if fault != "platform" else [n["id"] for n in nodes]
    result["failure"] = {
        "node_id": nodes[failed_index]["id"] if 0 <= failed_index < len(nodes) else None,
        "error_code": code,
        "fault": fault,
        "affects_success_rate": fault == "supplier",
        "refund_scope": {"refund_node_ids": refund, "settled_node_ids": succeeded},
    }


def _save(out_dir: Path, result: dict) -> None:
    (out_dir / "run.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")


def run_workflow(workflow_id: str, comment: str, file_path: str | None, mock: bool = False,
                 input_payload: dict | None = None, mock_overrides: dict | None = None) -> dict:
    return execute(load_workflow(workflow_id), comment=comment, file_path=file_path, mock=mock,
                   input_payload=input_payload, mock_overrides=mock_overrides, verbose=True)


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser(description="선형 워크플로우 스파이크 러너")
    ap.add_argument("--workflow", default="report")
    ap.add_argument("--file", default=None, help="엑셀/CSV 경로")
    ap.add_argument("--comment", default="")
    ap.add_argument("--mock", action="store_true", help="LLM 호출 없이 파이프라인만 확인")
    ap.add_argument("--input", default=None, help="첫 노드 입력 payload JSON 파일 경로")
    ap.add_argument("--mock-set", default=None, metavar="SCHEMA_ID.FIELD=VALUE",
                    help="mock 값 지정. 예: biz.market_research@1.competition_level=low")
    args = ap.parse_args()

    payload = json.loads(Path(args.input).read_text(encoding="utf-8")) if args.input else None
    overrides = None
    if args.mock_set:
        target, _, value = args.mock_set.partition("=")
        schema_id, _, field_name = target.rpartition(".")
        overrides = {schema_id: {field_name: value}}
    run = run_workflow(args.workflow, args.comment, args.file, mock=args.mock,
                       input_payload=payload, mock_overrides=overrides)
    if run["status"] != "succeeded":
        print(f"\n실패  {run['error']}")
        sys.exit(1)
    print(f"\n완료  {run['total_duration_ms']/1000:.1f}s  "
          f"tokens in {run['totals']['input_tokens']} / out {run['totals']['output_tokens']}")
    print(f"출력  {run['out_dir']}")
    if run["result"] and run["result"].get("pdf"):
        print(f"PDF   {run['result']['pdf']}")


if __name__ == "__main__":
    main()
