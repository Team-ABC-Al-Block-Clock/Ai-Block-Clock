"""메모리에 든 실행과 그 조회 모양(스냅샷).

메시지 처리(src/messaging.py)가 실행을 넣고 러너가 제자리에서 갱신한다. HTTP 조회(src/api.py)와 RunEvent 의
snapshot 은 같은 `public` 으로 만든다 — 그래야 이벤트와 `GET /v1/runs/{id}` 가 한 글자도 어긋나지 않는다.
단일 프로세스 인메모리라 재시작하면 사라진다.
"""
from __future__ import annotations

import threading
from typing import Any

from . import graph

runs: dict[str, dict[str, Any]] = {}
finished_at: dict[str, float] = {}      # run_id → 끝난 시각(epoch). 보관 기한 판정용
lock = threading.Lock()                 # runs · finished_at 과 메시지 처리의 기록을 함께 지킨다. 짧게만 잡는다

PUBLIC_NODE_KEYS = ("id", "name", "type", "status", "output", "agent_id", "owner_id",
                    "started_at", "finished_at", "duration_ms", "input_tokens", "output_tokens",
                    "retries", "repair_count", "model", "cost_usd", "skip_reason", "branch_taken")


def _workflow_summary(wf: Any) -> dict:
    wf = wf if isinstance(wf, dict) else {}
    nodes = wf.get("nodes") if isinstance(wf.get("nodes"), list) else []
    return {"id": wf.get("id"), "name": wf.get("name"), "node_count": len(nodes)}


def _empty_totals() -> dict:
    return {"input_tokens": 0, "output_tokens": 0, "cost_usd": 0.0}


def queued(run_id: str, wf: dict, *, mock: bool, mode: str) -> dict:
    """받아서 돌리기 직전의 실행. 러너가 이 dict 를 state 로 받아 제자리에서 채운다."""
    return {
        "run_id": run_id,
        "cancel_requested": False,
        "workflow": _workflow_summary(wf),
        "mock": mock, "mode": mode, "status": "queued", "started_at": None, "finished_at": None,
        "total_duration_ms": None,
        "nodes": [{"id": n.get("id"), "name": n.get("name") or n.get("id"), "status": "pending",
                   "type": n.get("type") or "llm", "output": graph.public_output(graph.output_spec_of(n)),
                   "duration_ms": None, "input_tokens": None,
                   "output_tokens": None, "retries": 0, "output_payload": None,
                   "model": None, "cost_usd": None, "owner_id": n.get("owner_id")}
                  for n in wf.get("nodes", [])],
        "totals": _empty_totals(),
        "agents": [],
        "contribution": [], "not_executed": [], "failure": None,
        "result": None, "warnings": [], "error": None,
    }


def rejected(run_id: str, wf: Any, *, mock: bool, mode: Any, error: dict, failure: dict,
             finished: str) -> dict:
    """실행 전에 거절한 실행. 노드는 하나도 돌지 않았다(ai/docs/integration-guide.md §4.4 rejected)."""
    return {
        "run_id": run_id,
        "cancel_requested": False,
        "workflow": _workflow_summary(wf),
        "mock": mock, "mode": mode, "status": "failed", "started_at": None, "finished_at": finished,
        "total_duration_ms": 0,
        "nodes": [],
        "totals": _empty_totals(),
        "agents": [],
        "contribution": [], "not_executed": [], "failure": failure,
        "result": None, "warnings": [], "error": error,
    }


def public(run: dict, include_node_output: bool = False) -> dict:
    """백엔드에 돌려줄 형태 — 계약 §7.2 그대로. 노드 출력 전문은 요청할 때만 포함한다.

    러너 내부 상태에는 프롬프트 · 마크다운 · 파일 경로가 있지만 밖으로는 내지 않는다.
    필드를 더하면 계약 문서도 같은 MR 에서 고친다.
    """
    nodes = []
    for n in run["nodes"]:
        item = {k: n.get(k) for k in PUBLIC_NODE_KEYS}
        item["type"] = n.get("type") or ("branch" if n.get("branch_taken") is not None else "llm")
        if include_node_output:
            item["output_payload"] = n.get("output_payload")
        nodes.append(item)

    # 진행률은 "돌아야 하는 LLM 노드" 기준이다 — 분기 노드와 건너뛴 노드는 세지 않는다(계약 §7.2)
    countable = [n for n in run["nodes"]
                 if n.get("type") != "branch" and n.get("branch_taken") is None
                 and n.get("status") != "skipped"]
    done = sum(1 for n in countable if n["status"] == "succeeded")
    total = len(countable)
    current = next((i for i, n in enumerate(run["nodes"]) if n["status"] == "running"), None)
    cur_node = run["nodes"][current] if current is not None else None

    result = None
    if run.get("result"):
        has_pdf = bool(run["result"].get("pdf"))
        has_html = bool(run["result"].get("html"))
        has_envelope = bool(run["result"].get("envelope"))
        result = {
            "type": run["result"].get("type"),
            "output": run["result"].get("output"),          # 종단 노드의 선언 {kind, example}
            "artifact_url": f"/v1/runs/{run['run_id']}/artifact" if has_envelope else None,
            "html_url": f"/v1/runs/{run['run_id']}/html" if has_html else None,      # 서식을 입힌 HTML (계약 v2.2)
            "pdf_url": f"/v1/runs/{run['run_id']}/pdf" if has_pdf else None,        # 그 HTML 의 PDF. 실패면 null
            "template": run["result"].get("template"),      # {applied: user|default, name, fallback_reason, mapping}
        }

    return {
        "run_id": run["run_id"],
        "status": run["status"],                     # queued | running | succeeded | failed
        "mode": run.get("mode", "run"),              # run | test — test 는 contribution 이 항상 []
        "workflow": run["workflow"],
        "mock": run["mock"],
        "progress": {
            "done": done,
            "total": total,
            "percent": int(done * 100 / total) if total else 0,
            "current_index": current,
            "current_node_id": cur_node["id"] if cur_node else None,
            "current_node_name": cur_node["name"] if cur_node else None,
        },
        "nodes": nodes,
        "agents": run.get("agents", []),             # 에이전트(판매자)별 토큰 · USD 합계 (계약 §7.2)
        "totals": run["totals"],
        "started_at": run["started_at"],
        "finished_at": run["finished_at"],
        "total_duration_ms": run["total_duration_ms"],
        # 정산 근거 (계약 §8)
        "contribution": run.get("contribution", []),
        "not_executed": run.get("not_executed", []),
        "failure": run.get("failure"),
        "result": result,
        "warnings": run.get("warnings", []),          # 성공했지만 알릴 것 — 서식 폴백 · 짝짓기 · PDF 실패
        "error": run["error"],
    }
