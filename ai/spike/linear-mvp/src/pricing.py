"""실측 원가 → ABC 환산 (NOD-007 · EXE-013).

가이드 §11.5 그대로.

    원가 = Σ(입력 토큰 × 모델 입력 단가 + 출력 토큰 × 모델 출력 단가) × ABC 환산율

**교정 재요청분 토큰도 포함한다** — 노드 실행기가 시도별 토큰을 누적해 넘기므로
여기서는 합계만 받는다. 계산은 전부 코드다. LLM에게 원가를 묻지 않는다(설계 원칙 1).

단가표는 모델 레지스트리 설정에 둔다. 지금은 상수 + 환경변수로 덮어쓴다.
    LLM_PRICE_IN    입력 100만 토큰당 USD
    LLM_PRICE_OUT   출력 100만 토큰당 USD
    ABC_PER_USD     1 USD 가 몇 ABC 인가
"""
from __future__ import annotations

import os

# 모델별 공시 단가(USD / 1M tokens). 모델을 늘리면 여기에 추가한다.
MODEL_PRICES: dict[str, tuple[float, float]] = {
    "gemini-2.5-flash": (0.30, 2.50),
}
DEFAULT_PRICE = MODEL_PRICES["gemini-2.5-flash"]

# 🔶 제안값 — 1 ABC = 0.001 USD. 기획 · 백엔드 확정 전까지의 기준이다.
# 이 값이면 노드 1회(약 6,000 토큰) 실측 원가가 3 ~ 4 ABC 로, 시연 워크플로우의
# base_cost(3.0 ~ 4.0)와 자릿수가 맞는다. 확정되면 환경변수로 덮어쓰면 된다.
DEFAULT_ABC_PER_USD = 1_000.0


def prices_for(model: str) -> tuple[float, float]:
    env_in, env_out = os.getenv("LLM_PRICE_IN"), os.getenv("LLM_PRICE_OUT")
    if env_in and env_out:
        return float(env_in), float(env_out)
    return MODEL_PRICES.get(model, DEFAULT_PRICE)


def abc_per_usd() -> float:
    return float(os.getenv("ABC_PER_USD") or DEFAULT_ABC_PER_USD)


def cost_usd(model: str, input_tokens: int, output_tokens: int) -> float:
    price_in, price_out = prices_for(model)
    return (input_tokens * price_in + output_tokens * price_out) / 1_000_000


def cost_abc(model: str, input_tokens: int, output_tokens: int) -> float:
    """노드 1회 실행의 실측 원가(ABC). mock 은 모델을 부르지 않았으므로 0 이다."""
    if model == "mock":
        return 0.0
    return round(cost_usd(model, input_tokens, output_tokens) * abc_per_usd(), 4)
