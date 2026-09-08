"""선형 워크플로우 러너 — 노드 순차 실행 · 이전 산출물 전달 · 로그 · PDF.

사용:
  uv run python -m src.runner --workflow report --file data/demo_scores.xlsx \
      --comment "이번 달은 전반적으로 난이도가 높았음" [--mock]

노드 정의(workflows/<id>.json)의 순서가 곧 실행 순서다. 그래프 · 분기 · 스키마 없음.
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from datetime import datetime
from pathlib import Path

from dotenv import load_dotenv

from .excel_to_text import excel_to_markdown
from .llm import LLM
from .pdf import markdown_to_pdf

ROOT = Path(__file__).resolve().parents[1]
load_dotenv(ROOT / ".env")


def load_workflow(workflow_id: str) -> dict:
    path = ROOT / "workflows" / f"{workflow_id}.json"
    wf = json.loads(path.read_text(encoding="utf-8"))
    for node in wf["nodes"]:
        p = node["prompt"]
        if p.endswith(".md") and (ROOT / p).exists():          # 파일 참조 허용
            node["prompt"] = (ROOT / p).read_text(encoding="utf-8")
    return wf


def build_user_input(comment: str, file_path: str | None) -> str:
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


def run_workflow(workflow_id: str, comment: str, file_path: str | None, mock: bool = False,
                 out_root: Path | None = None) -> dict:
    wf = load_workflow(workflow_id)
    run_id = datetime.now().strftime("%Y%m%d-%H%M%S")
    out_dir = (out_root or ROOT / "out") / run_id
    (out_dir / "nodes").mkdir(parents=True, exist_ok=True)

    llm = LLM(mock=mock, model=wf.get("model"))
    user_input = build_user_input(comment, file_path)
    (out_dir / "input.md").write_text(user_input, encoding="utf-8")

    started = time.perf_counter()
    prev_output: str | None = None
    node_logs = []
    for i, node in enumerate(wf["nodes"], 1):
        message = build_message(node, user_input, prev_output)
        print(f"[{i}/{len(wf['nodes'])}] {node['name']} … ", end="", flush=True)
        result = llm.generate(system=node["prompt"], user=message, label=node["id"])
        print(f"{result.duration_ms/1000:.1f}s  in {result.input_tokens} / out {result.output_tokens} tokens")
        (out_dir / "nodes" / f"{node['id']}.md").write_text(result.text, encoding="utf-8")
        (out_dir / "nodes" / f"{node['id']}.request.md").write_text(
            f"# system\n{node['prompt']}\n\n# user\n{message}", encoding="utf-8")
        node_logs.append({
            "id": node["id"], "name": node["name"], "model": result.model, "context": node.get("context", "prev"),
            "input_tokens": result.input_tokens, "output_tokens": result.output_tokens,
            "duration_ms": result.duration_ms, "retries": result.retries, "output_chars": len(result.text),
        })
        prev_output = result.text

    pdf_path = None
    output_cfg = wf.get("output", {})
    if output_cfg.get("type") == "pdf" and prev_output:
        pdf_path = markdown_to_pdf(prev_output, output_cfg.get("title", wf["name"]), out_dir / "report.pdf")

    run = {
        "run_id": run_id, "workflow": wf["id"], "mock": mock,
        "comment": comment, "file": str(file_path) if file_path else None,
        "total_duration_ms": int((time.perf_counter() - started) * 1000),
        "total_input_tokens": sum(n["input_tokens"] for n in node_logs),
        "total_output_tokens": sum(n["output_tokens"] for n in node_logs),
        "nodes": node_logs,
        "final_output": prev_output,
        "pdf": str(pdf_path) if pdf_path else None,
        "out_dir": str(out_dir),
    }
    (out_dir / "run.json").write_text(json.dumps(run, ensure_ascii=False, indent=2), encoding="utf-8")
    return run


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser(description="선형 워크플로우 스파이크 러너")
    ap.add_argument("--workflow", default="report")
    ap.add_argument("--file", default=None, help="엑셀/CSV 경로")
    ap.add_argument("--comment", default="")
    ap.add_argument("--mock", action="store_true", help="LLM 호출 없이 파이프라인만 확인")
    args = ap.parse_args()

    run = run_workflow(args.workflow, args.comment, args.file, mock=args.mock)
    print(f"\n완료  {run['total_duration_ms']/1000:.1f}s  tokens in {run['total_input_tokens']} / out {run['total_output_tokens']}")
    print(f"출력  {run['out_dir']}")
    if run["pdf"]:
        print(f"PDF   {run['pdf']}")


if __name__ == "__main__":
    main()
