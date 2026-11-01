"""봉투 — 노드 결과를 감싸는 겉포장. 연동 계약 §6.2 그대로.

**LLM은 `payload`만 만들고 봉투는 Runtime이 씌운다.** 다음 노드에는 payload(+ 코멘트)만
넘어가고, 봉투는 백엔드가 이력 · 정산 · 결과물 조회에 쓴다.

    {
      "a2a_version": "2.0", "run_id": …, "node_id": …, "produced_at": …,
      "schema_id": "biz.market_research@1",
      "payload": { … },
      "meta": { "model", "usage": {input_tokens, output_tokens},
                "cost_usd", "duration_ms", "retry_count", "repair_count" },
      "error": null
    }

시각 표기는 러너(`src/runner.py::_now`)와 같은 ISO-8601 UTC(`+00:00`)를 쓴다.
"""
from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

A2A_VERSION = "2.0"


def now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def meta(model: str, *, input_tokens: int = 0, output_tokens: int = 0, duration_ms: int = 0,
         retry_count: int = 0, repair_count: int = 0, cost_usd: float | None = None) -> dict[str, Any]:
    """봉투의 meta. `cost_usd`는 토큰 × 공시 단가(`pricing.cost_usd`). ABC 는 여기 없다(계약 v2.0 §8.1)."""
    return {
        "model": model,
        "usage": {"input_tokens": input_tokens, "output_tokens": output_tokens},
        "cost_usd": cost_usd,
        "duration_ms": duration_ms,
        "retry_count": retry_count,
        "repair_count": repair_count,
    }


def build(run_id: str, node_id: str, schema_id: str, payload: dict[str, Any],
          meta_: dict[str, Any]) -> dict[str, Any]:
    return {
        "a2a_version": A2A_VERSION,
        "run_id": run_id,
        "node_id": node_id,
        "produced_at": now(),
        "schema_id": schema_id,
        "payload": payload,
        "meta": meta_,
        "error": None,
    }


def failed(run_id: str, node_id: str, schema_id: str, *, code: str, message: str, fault: str,
           meta_: dict[str, Any]) -> dict[str, Any]:
    """실패 봉투. `fault`는 supplier | consumer | platform (계약 §8.5 · §9.2)."""
    env = build(run_id, node_id, schema_id, {}, meta_)
    env["payload"] = None
    env["error"] = {"code": code, "message": message, "fault": fault}
    return env
