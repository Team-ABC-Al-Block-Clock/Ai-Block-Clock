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

from .excel_to_text import excel_to_markdown
from .llm import LLM
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
    for node in wf.get("nodes", []):
        p = str(node.get("prompt", ""))
        if p.endswith(".md"):
            candidate = ROOT / p
            if candidate.exists():
                node["prompt"] = candidate.read_text(encoding="utf-8")
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


def execute(
    wf: dict,
    comment: str = "",
    file_path: str | Path | None = None,
    mock: bool = False,
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
             "input_tokens": None, "output_tokens": None, "retries": 0, "output": None}
            for n in nodes
        ],
        "totals": {"input_tokens": 0, "output_tokens": 0},
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
        _save(out_dir, result)
        emit("run.failed", {"run_id": run_id, "error": result["error"]})
        return result

    user_input = build_user_input(comment, file_path)
    (out_dir / "input.md").write_text(user_input, encoding="utf-8")
    _save(out_dir, result)
    emit("run.started", {"run_id": run_id, "total": len(nodes)})

    started = time.perf_counter()
    prev_output: str | None = None

    for i, node in enumerate(nodes):
        entry = result["nodes"][i]
        entry["status"] = "running"
        _save(out_dir, result)
        emit("node.started", {"run_id": run_id, "index": i, "node_id": node.get("id")})
        if verbose:
            print(f"[{i+1}/{len(nodes)}] {entry['name']} … ", end="", flush=True)

        message = build_message(node, user_input, prev_output)
        (out_dir / "nodes" / f"{node['id']}.request.md").write_text(
            f"# system\n{node['prompt']}\n\n# user\n{message}", encoding="utf-8")

        try:
            res = llm.generate(system=node["prompt"], user=message, label=node.get("id", ""))
        except Exception as e:
            entry.update(status="failed")
            result.update(status="failed", finished_at=_now(),
                          total_duration_ms=int((time.perf_counter() - started) * 1000),
                          error={"code": _error_code(e), "message": str(e)[:500], "node_id": node.get("id")})
            _save(out_dir, result)
            emit("node.failed", {"run_id": run_id, "index": i, "node_id": node.get("id")})
            emit("run.failed", {"run_id": run_id, "error": result["error"]})
            if verbose:
                print(f"실패: {e}")
            return result

        (out_dir / "nodes" / f"{node['id']}.md").write_text(res.text, encoding="utf-8")
        entry.update(status="succeeded", duration_ms=res.duration_ms, input_tokens=res.input_tokens,
                     output_tokens=res.output_tokens, retries=res.retries, output=res.text)
        result["totals"]["input_tokens"] += res.input_tokens
        result["totals"]["output_tokens"] += res.output_tokens
        prev_output = res.text
        _save(out_dir, result)
        emit("node.succeeded", {"run_id": run_id, "index": i, "node_id": node.get("id"),
                                "duration_ms": res.duration_ms})
        if verbose:
            print(f"{res.duration_ms/1000:.1f}s  in {res.input_tokens} / out {res.output_tokens} tokens")

    output_cfg = wf.get("output", {}) or {}
    result["result"] = {"type": output_cfg.get("type", "pdf"), "markdown": prev_output, "pdf": None}
    if output_cfg.get("type", "pdf") == "pdf" and prev_output:
        try:
            pdf_path = markdown_to_pdf(prev_output, output_cfg.get("title") or wf.get("name", "리포트"),
                                       out_dir / "report.pdf")
            result["result"]["pdf"] = str(pdf_path)
        except Exception as e:
            result.update(status="failed", finished_at=_now(),
                          total_duration_ms=int((time.perf_counter() - started) * 1000),
                          error={"code": "PDF_RENDER_FAILED", "message": str(e)[:500], "node_id": None})
            _save(out_dir, result)
            emit("run.failed", {"run_id": run_id, "error": result["error"]})
            return result

    result.update(status="succeeded", finished_at=_now(),
                  total_duration_ms=int((time.perf_counter() - started) * 1000))
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


def _save(out_dir: Path, result: dict) -> None:
    (out_dir / "run.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")


def run_workflow(workflow_id: str, comment: str, file_path: str | None, mock: bool = False) -> dict:
    return execute(load_workflow(workflow_id), comment=comment, file_path=file_path, mock=mock, verbose=True)


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser(description="선형 워크플로우 스파이크 러너")
    ap.add_argument("--workflow", default="report")
    ap.add_argument("--file", default=None, help="엑셀/CSV 경로")
    ap.add_argument("--comment", default="")
    ap.add_argument("--mock", action="store_true", help="LLM 호출 없이 파이프라인만 확인")
    args = ap.parse_args()

    run = run_workflow(args.workflow, args.comment, args.file, mock=args.mock)
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
