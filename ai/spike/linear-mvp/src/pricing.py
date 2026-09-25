"""실측 원가 — 토큰 × 모델 공시 단가 = USD (NOD-007 · EXE-013). 계약 v2.0 §8.1.

    cost_usd = (입력 토큰 × 입력 단가 + 출력 토큰 × 출력 단가) / 1,000,000

**Runtime 은 ABC 를 모른다.** ABC 환산율 · Base Cost · 프리미엄 · 단가 · 정산은 전부 백엔드다.
여기서는 토큰을 USD 로 바꾸고, 노드별 값을 에이전트(판매자)별 · 전체로 합산하는 것까지만 한다.
**교정 재요청분 토큰도 포함한다** — 노드 실행기가 시도별 토큰을 누적해 넘기므로 합계만 받는다.
계산은 전부 코드다. LLM 에게 원가를 묻지 않는다(설계 원칙 1).

단가표는 모델 레지스트리 설정에 둔다. 지금은 상수 + 환경변수로 덮어쓴다.
    LLM_PRICE_IN    입력 100만 토큰당 USD
    LLM_PRICE_OUT   출력 100만 토큰당 USD
"""
from __future__ import annotations

import os
from typing import Any

# 모델별 공시 단가(USD / 1M tokens). 모델을 늘리면 여기에 추가한다.
MODEL_PRICES: dict[str, tuple[float, float]] = {
    "gemini-2.5-flash": (0.30, 2.50),
}
DEFAULT_PRICE = MODEL_PRICES["gemini-2.5-flash"]
USD_DECIMALS = 6                       # 노드 1회가 0.001 USD 대라 소수 6자리는 있어야 한다


def prices_for(model: str) -> tuple[float, float]:
    env_in, env_out = os.getenv("LLM_PRICE_IN"), os.getenv("LLM_PRICE_OUT")
    if env_in and env_out:
        return float(env_in), float(env_out)
    return MODEL_PRICES.get(model, DEFAULT_PRICE)


def cost_usd(model: str, input_tokens: int, output_tokens: int) -> float:
    """노드 1회 실행의 실측 원가(USD). mock 은 모델을 부르지 않았으므로 0 이다."""
    if model == "mock":
        return 0.0
    price_in, price_out = prices_for(model)
    return round((input_tokens * price_in + output_tokens * price_out) / 1_000_000, USD_DECIMALS)


def aggregate(nodes: list[dict[str, Any]]) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    """노드 항목들에서 전체 합계(`totals`)와 에이전트별 합계(`agents[]`)를 만든다. 계약 §7.2.

    에이전트 밖 노드는 `agent_id: null` 로 한 묶음이다. 분기 노드(토큰 0)도 그 묶음의
    `node_ids` 에는 넣지 않는다 — 과금 대상이 아니라서 목록이 곧 "돈 노드"여야 한다.
    """
    totals = {"input_tokens": 0, "output_tokens": 0, "cost_usd": 0.0}
    groups: dict[Any, dict[str, Any]] = {}
    for n in nodes:
        if n.get("type") == "branch" or n.get("branch_taken") is not None:
            continue
        key = n.get("agent_id")
        group = groups.setdefault(key, {"agent_id": key, "owner_id": n.get("owner_id"), "node_ids": [],
                                        "input_tokens": 0, "output_tokens": 0, "cost_usd": 0.0})
        group["node_ids"].append(n["id"])
        for k in ("input_tokens", "output_tokens"):
            value = n.get(k) or 0
            group[k] += value
            totals[k] += value
        usd = n.get("cost_usd") or 0.0
        group["cost_usd"] = round(group["cost_usd"] + usd, USD_DECIMALS)
        totals["cost_usd"] = round(totals["cost_usd"] + usd, USD_DECIMALS)
    return totals, list(groups.values())
