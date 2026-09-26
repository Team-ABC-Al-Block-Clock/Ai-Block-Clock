"""워크플로우 러너 — 그래프 실행 · 앞 결과 전체 전달 · 봉투 · 로그 · PDF. 계약 v2.0.

CLI 사용:
  uv run python -m src.runner --workflow report --file data/demo_scores.xlsx \
      --comment "이번 달은 전반적으로 난이도가 높았음" [--mock]

노드는 판매자가 선언한 출력(`output {kind, sample}`)을 내고, 다음 노드는 앞 결과 전체를 받는다.
입력 형식 검증은 없다 — 진입 노드의 사용자 입력 폼(`input.sample`)만 검증한다.
API(src/api.py)는 execute() 를 직접 호출하고 on_event 로 진행 상황을 받는다.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable

from dotenv import load_dotenv

from . import errors, pricing, shape
from . import expand as expand_mod
from . import graph as graph_mod
from . import nodes as node_runner
from . import prompts
from .excel_to_text import excel_to_markdown
from .llm import LLM
from .pdf import markdown_to_pdf

ROOT = Path(__file__).resolve().parents[1]
OUT_ROOT = ROOT / "out"
load_dotenv(ROOT / ".env")

# 계약 v2.0 §5.4 — 앞 결과 전체를 넘기므로 상한이 필요하다. 넘으면 INPUT_TOO_LARGE · platform 책임.
# 약 100k 토큰. 실측 노드당 6k 토큰이라 8노드 체인에서도 여유가 크다. 요약 · 절단은 하지 않는다.
MAX_INPUT_CHARS = int(os.getenv("MAX_INPUT_CHARS") or 400_000)

# 계약 §7.1 — 판매자 참고 파일. mime 4종, 노드당 합계 10MB. 어기면 REF_FILE_REJECTED · supplier.
# 텍스트 계열은 본문("## 참고 자료")에 넣고 PDF 는 Gemini inline 첨부로 보낸다.
REF_MIME = {".pdf": "application/pdf", ".txt": "text/plain", ".md": "text/markdown", ".csv": "text/csv"}
MAX_REF_BYTES = 10 * 1024 * 1024
_PDF_CHARS_PER_BYTE = 0.25              # 상한 계산용 추정치 — PDF 1바이트 ≈ 0.25자 텍스트


class RefFileRejected(Exception):
    pass


def load_ref_files(paths: list) -> tuple[str, list[tuple[bytes, str, str]], int]:
    """참고 파일을 검사해 `(본문 절, PDF 첨부, PDF 추정 글자 수)` 로 돌려준다.

    거부 사유는 판매자 몫이다 — 붙인 사람이 형식 · 크기를 지키지 않은 것(계약 §9.2).
    """
    total = 0
    sections: list[str] = []
    attachments: list[tuple[bytes, str, str]] = []
    pdf_chars = 0
    for raw in paths:
        p = Path(raw)
        mime = REF_MIME.get(p.suffix.lower())
        if not mime:
            raise RefFileRejected(f"허용하지 않는 형식: {p.name} (허용: {', '.join(sorted(REF_MIME))})")
        if not p.exists():
            raise RefFileRejected(f"파일이 없습니다: {p.name}")
        size = p.stat().st_size
        total += size
        if total > MAX_REF_BYTES:
            raise RefFileRejected(f"노드당 합계 {MAX_REF_BYTES // 1024 // 1024}MB 초과 ({p.name} 포함 {total / 1024 / 1024:.1f}MB)")
        if mime == "application/pdf":
            attachments.append((p.read_bytes(), mime, p.name))
            pdf_chars += int(size * _PDF_CHARS_PER_BYTE)
            sections.append(f"### {p.name}\n(PDF 첨부 {size // 1024}KB — 함께 읽는다)")
        else:
            sections.append(f"### {p.name}\n{p.read_text(encoding='utf-8', errors='replace')}")
    return "## 참고 자료\n" + "\n\n".join(sections), attachments, pdf_chars

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


def build_user_input(comment: str, file_path: str | Path | None) -> str:
    """진입 노드에 폼 견본이 없을 때 넘기는 자유 입력 — 코멘트와 올린 파일(표)의 텍스트."""
    parts = []
    if comment:
        parts.append(f"## 코멘트\n{comment.strip()}")
    if file_path:
        parts.append(f"## 올린 파일\n{excel_to_markdown(file_path)}")
    return "\n\n".join(parts) if parts else "(입력 없음)"


_COMMENT_TMPL = """## 사용자 코멘트
{body}"""

_REQUEST_TMPL = """# system
{system}

# user
{user}"""


def build_input_message(sections: list[tuple[str, object]], comment: str) -> str:
    """다음 노드 LLM 에게 넘길 "## 입력" 블록(계약 v2.0 §5.4).

    `sections` 는 `(절 제목, 내용)` 목록이다. 단일 상위면 절 하나, 병합이면 port 마다 절.
    JSON 은 코드블록으로, 글(`{"text": …}` 또는 문자열)은 본문 그대로 넣는다.
    """
    parts = []
    for label, body in sections:
        if isinstance(body, dict) and set(body) == {"text"}:
            rendered = str(body["text"])
        elif isinstance(body, str):
            rendered = body
        else:
            rendered = "```json\n" + json.dumps(body, ensure_ascii=False, indent=2) + "\n```"
        parts.append(f"### {label}\n{rendered}")
    message = "## 입력\n" + "\n\n".join(parts) if parts else "(입력 없음)"
    if comment:
        message += "\n\n" + _COMMENT_TMPL.format(body=comment.strip())
    return message


def input_sections(graph, sources: list[tuple[str, str | None]], received: object,
                   input_payload: dict | None, user_input: str) -> list[tuple[str, object]]:
    """`fan_in_sources` 결과를 절 제목이 달린 목록으로. 진입 노드는 사용자 입력이 절 하나다."""
    if not sources:
        return [("사용자 입력", input_payload if input_payload is not None else user_input)]
    if len(sources) == 1 and sources[0][1] is None:
        return [(_source_label(graph, sources[0][0]), received)]
    out = []
    for src, port in sources:
        key = port or src
        body = received.get(key) if isinstance(received, dict) else received
        out.append((f"{key} — {_source_label(graph, src)}", body))
    return out


def _source_label(graph, node_id: str) -> str:
    """절 제목: `시장 조사 (n1 · JSON)`. 글 출력은 `글`."""
    node = graph.node(node_id)
    spec = graph_mod.upstream_output_spec(graph, node_id)
    kind = "JSON" if spec and spec.get("kind") == "json" else "글"
    return f"{node.get('name') or node_id} ({node_id} · {kind})"


def overrides_for(node_id: str, overrides: dict | None) -> dict:
    """mock 값 지정 `{"n1.competition_level": "low"}` 중 이 노드 몫을 `{"competition_level": "low"}` 로.

    에이전트 안 노드(`a1.n1`)는 접두 id 로도, 원래 id(`n1`)로도 가리킬 수 있다.
    """
    out: dict = {}
    names = [node_id] + ([node_id.split(".", 1)[1]] if "." in node_id else [])
    for ref, value in (overrides or {}).items():
        for name in names:
            if ref.startswith(name + "."):
                out[ref[len(name) + 1:]] = value
                break
    return out


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
    mode: str = "run",
    ref_files: dict[str, list] | None = None,
) -> dict:
    """워크플로우 하나를 끝까지 실행한다. 예외를 던지지 않고 결과 dict 의 status 로 성패를 알린다.

    state 를 주면 그 dict 를 제자리에서 갱신한다. 호출자(API)가 같은 객체를 들고 있으면
    실행 도중에도 노드별 진행 상황을 실시간으로 읽을 수 있다.
    `mode="test"` 는 판매자 사전 테스트다(계약 v2.0 §7.1) — 실행 경로 · 토큰 · USD · 결과물은
    같고 정산 입력(`contribution`)만 비운다. 횟수 · 크레딧 제한은 백엔드가 건다.
    `ref_files` 는 노드 id → 참고 파일 경로 목록(계약 §7.1). 전개된 에이전트 안 노드는 `a1.n1` 처럼 접두 id 로.
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
        "mode": mode,
        "status": "running",
        "started_at": _now(),
        "finished_at": None,
        "total_duration_ms": None,
        "inputs": {"comment": comment, "file": Path(file_path).name if file_path else None},
        "nodes": [
            {"id": n.get("id"), "name": n.get("name") or n.get("id"), "status": "pending",
             "type": n.get("type") or "llm",
             "output": graph_mod.public_output(graph_mod.output_spec_of(n)),   # {kind, example}. 분기는 None
             "duration_ms": None, "repair_count": 0,
             "model": None, "cost_usd": None,          # 토큰 · USD 만. ABC 는 백엔드(계약 v2.0 §8.1)
             "agent_id": n.get("agent_id"),
             "input_tokens": None, "output_tokens": None, "retries": 0, "output_payload": None,
             "owner_id": n.get("owner_id")}             # 정산 귀속 키. 단가는 받지 않는다
            for n in nodes
        ],
        "totals": {"input_tokens": 0, "output_tokens": 0, "cost_usd": 0.0},
        "agents": [],
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

    # 정의 자체가 틀렸으면 실행하지 않는다(계약 §5.7). 순환 · 상한 · 출력 선언 · 분기 참조까지 여기서 걸린다
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

    # 진입 노드(edges 의 to 에 없는 노드)는 사용자 입력을 받는다. 입력 예시가 있으면 그 모양인지 본다(계약 v2.1 §5.2)
    first_input_example = next(
        (s for s in (graph_mod.entry_input_example(graph.node(n)) for n in graph.entries) if s), None)

    if first_input_example:
        reason = ("입력값이 없습니다" if input_payload is None
                  else node_runner.check_input(first_input_example, input_payload))
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
    last_envelope: dict | None = None
    last_spec: dict | None = None

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
                         input_tokens=0, output_tokens=0, model=None, cost_usd=0.0, branch_taken=taken)
            _save(out_dir, result)
            emit("node.succeeded", {"run_id": run_id, "index": index, "node_id": node_id,
                                    "duration_ms": 0})
            if verbose:
                print("분기 " + ("예" if taken else "아니오"))
            continue

        # --- LLM 노드: 앞 결과 전체 → 프롬프트 → (json 이면) 구조화 출력 · 검증 · 교정 → 봉투 ---
        spec = graph_mod.output_spec_of(node) or {"kind": "text", "example": None}
        example = spec.get("example") if spec["kind"] == "json" else None

        prompt = prompts.parse(node["prompt"])
        sources = graph_mod.fan_in_sources(graph, node_id, payloads, branch_taken)
        if prompt.templated:
            # 트랙 B 작성 형식 — user 블록의 자리표시자를 fan-in 결과로 채운다
            values = prompts.values_for(received, input_payload=input_payload, comment=comment)
            try:
                message = prompt.render_user(values)
            except prompts.PlaceholderMissing as e:
                entry.update(status="failed", finished_at=_now())
                return fail(node_id, "PROMPT_TEMPLATE_INVALID",
                            f"프롬프트가 요구하는 값이 없습니다: {e}", fault="supplier", index=index)
        else:
            message = build_input_message(
                input_sections(graph, sources, received, input_payload, user_input), comment)
        # 판매자 참고 파일 — 텍스트는 본문에, PDF 는 첨부로. 형식 · 크기 위반은 supplier(계약 §7.1)
        attachments: list = []
        pdf_chars = 0
        if (ref_files or {}).get(node_id):
            try:
                ref_text, attachments, pdf_chars = load_ref_files(ref_files[node_id])
            except RefFileRejected as e:
                entry.update(status="failed", finished_at=_now())
                return fail(node_id, "REF_FILE_REJECTED", str(e), fault="supplier", index=index)
            message += "\n\n" + ref_text
        if len(message) + pdf_chars > MAX_INPUT_CHARS:
            entry.update(status="failed", finished_at=_now())
            return fail(node_id, "INPUT_TOO_LARGE",
                        f"입력 {len(message) + pdf_chars:,}자 > 상한 {MAX_INPUT_CHARS:,}자",
                        fault="platform", index=index)
        system = prompt.system
        (out_dir / "nodes" / f"{node_id}.request.md").write_text(
            _REQUEST_TMPL.format(system=system, user=message), encoding="utf-8")

        try:
            if example:
                nr = node_runner.run_llm_node(
                    llm, run_id=run_id, node_id=node_id, example=example,
                    system=system, message=message,
                    mock_overrides=overrides_for(node_id, mock_overrides),
                    attachments=attachments or None)
            else:
                nr = node_runner.run_text_node(llm, run_id=run_id, node_id=node_id,
                                               system=system, message=message,
                                               attachments=attachments or None)
        except Exception as e:                      # 모델 API 오류 · 타임아웃 · 429
            partial = getattr(e, "partial_meta", None)   # 교정 중 예외 — 앞 호출의 토큰은 남긴다
            if partial:
                entry.update(model=partial["model"], cost_usd=partial["cost_usd"],
                             input_tokens=partial["usage"]["input_tokens"],
                             output_tokens=partial["usage"]["output_tokens"],
                             repair_count=partial["repair_count"], retries=partial["retry_count"])
                _aggregate(result)
            entry.update(status="failed", finished_at=_now())
            return fail(node_id, _error_code(e), str(e)[:500], index=index)

        (out_dir / "nodes" / f"{node_id}.json").write_text(
            json.dumps(nr.envelope, ensure_ascii=False, indent=2), encoding="utf-8")
        meta = nr.envelope["meta"]
        entry.update(duration_ms=meta["duration_ms"], retries=meta["retry_count"],
                     repair_count=meta["repair_count"],
                     model=meta["model"], cost_usd=meta["cost_usd"],
                     input_tokens=meta["usage"]["input_tokens"],
                     output_tokens=meta["usage"]["output_tokens"])
        _aggregate(result)

        if not nr.ok:
            err = nr.error
            entry.update(status="failed", finished_at=_now())
            return fail(node_id, err["code"], err["message"], fault=err["fault"], index=index)

        entry.update(status="succeeded", finished_at=_now(),
                     output_payload=json.dumps(nr.payload, ensure_ascii=False))
        payloads[node_id] = nr.payload
        prev_output = shape.to_markdown(nr.payload) if example else str(nr.payload.get("text", ""))
        last_envelope = nr.envelope
        last_spec = graph_mod.public_output(spec)
        _save(out_dir, result)
        emit("node.succeeded", {"run_id": run_id, "index": index, "node_id": node_id,
                                "duration_ms": meta["duration_ms"]})
        if verbose:
            what = f"JSON  교정 {meta['repair_count']}회" if example else "글"
            print(f"{meta['duration_ms']/1000:.1f}s  {what}  in {meta['usage']['input_tokens']} / out {meta['usage']['output_tokens']} tokens")

    # 산출물 = 종단 노드의 봉투(계약 v2.0 §5.5 · §7.3). 글이면 본문 그대로, 견본이면 라벨로 문서를 만든다
    output_cfg = wf.get("output", {}) or {}
    result["result"] = {"type": output_cfg.get("type", "pdf"), "output": last_spec,
                        "markdown": prev_output, "pdf": None, "envelope": last_envelope}
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


def _aggregate(result: dict) -> None:
    """노드 항목에서 `totals` · `agents[]` 를 다시 센다(계약 §7.2). 노드가 끝날 때마다 부른다."""
    result["totals"], result["agents"] = pricing.aggregate(result["nodes"])


def _settlement(result: dict, failed_index: int | None, code: str | None,
                fault: str | None = None) -> None:
    """contribution · not_executed · failure 를 채운다.

    성공 노드는 원작자에게 정산하고, 실패 노드와 그 이후 미실행 구간은 수요자에게 환원한다.
    platform 책임일 때는 수요자에게 전액을 환원하되 성공 노드의 정산은 유지한다.
    플랫폼 과실로 공급자가 손해를 보지 않게 하려는 것이며, 차액은 플랫폼이 부담한다.
    """
    nodes = result["nodes"]
    # 금액은 없다 — 누구에게 귀속되는 어느 노드가 성공했는지만. 단가는 백엔드가 붙인다(계약 v2.0 §8.2)
    # 테스트 실행(mode=test)은 정산 입력을 내지 않는다(계약 v2.0 §7.1) — 돈이 움직이지 않는다
    result["contribution"] = [] if result.get("mode") == "test" else [
        {"node_id": n["id"], "owner_id": n.get("owner_id"), "agent_id": n.get("agent_id")}
        for n in nodes if n["status"] == "succeeded" and n.get("branch_taken") is None
    ]
    result["not_executed"] = [
        {"node_id": n["id"],
         "reason": n.get("skip_reason") or ("upstream_failed" if failed_index is not None else "not_reached")}
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
                 input_payload: dict | None = None, mock_overrides: dict | None = None, ref_files: dict | None = None) -> dict:
    return execute(load_workflow(workflow_id), comment=comment, file_path=file_path, mock=mock,
                   input_payload=input_payload, mock_overrides=mock_overrides, verbose=True, ref_files=ref_files)


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser(description="워크플로우 러너 (계약 v2.0)")
    ap.add_argument("--workflow", default="report")
    ap.add_argument("--file", default=None, help="엑셀/CSV 경로")
    ap.add_argument("--comment", default="")
    ap.add_argument("--mock", action="store_true", help="LLM 호출 없이 파이프라인만 확인")
    ap.add_argument("--input", default=None, help="진입 노드 입력 payload JSON 파일 경로")
    ap.add_argument("--mock-set", action="append", default=[], metavar="NODE_ID.FIELD=VALUE",
                    help="mock 값 지정. 여러 번 가능. 예: n1.competition_level=low (에이전트 안 노드는 a1.n1. 도 된다)")
    ap.add_argument("--ref", action="append", default=[], metavar="NODE_ID=PATH",
                    help="노드 참고 파일. 여러 번 지정 가능. 예: --ref n1=data/checklist.md")
    args = ap.parse_args()
    ref_files: dict[str, list] = {}
    for item in args.ref:
        node_id, _, path = item.partition("=")
        ref_files.setdefault(node_id, []).append(path)

    payload = json.loads(Path(args.input).read_text(encoding="utf-8")) if args.input else None
    overrides = {}
    for item in args.mock_set:
        target, _, value = item.partition("=")
        overrides[target] = value
    run = run_workflow(args.workflow, args.comment, args.file, mock=args.mock,
                       input_payload=payload, mock_overrides=overrides, ref_files=ref_files or None)
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
