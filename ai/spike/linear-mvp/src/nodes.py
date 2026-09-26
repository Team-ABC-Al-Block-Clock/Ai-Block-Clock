"""LLM 노드 한 개를 "판매자가 준 모양"으로 실행한다 — 구조화 출력 · 검사 · 교정 · 봉투. 계약 v2.1.

흐름
  1) 판매자의 출력 JSON 예시로 응답 스키마를 만들어 LLM에게 준다(구조화 출력, `src/shape`)
  2) 돌아온 JSON을 뜯는다. 코드블록으로 감싸 오는 경우까지만 관용한다
  3) 예시의 모양(키 · 타입)을 지켰는지 코드로 검사한다(EXE-009)
  4) 틀리면 어긋난 곳을 알려주고 다시 요청한다. **교정은 2회까지**
  5) 통과하면 Runtime이 봉투를 씌운다

판정은 전부 코드가 한다. 교정 2회를 넘기면 `SCHEMA_VALIDATION_FAILED` · **공급자 책임**이고
(계약 §9.2), 진입 노드의 사용자 입력이 입력 예시와 안 맞으면 같은 코드에 **수요자 책임**이다.
"""
from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from typing import Any

from . import envelope, pricing, shape
from .llm import LLM

MAX_REPAIRS = 2                      # 계약 §6.2 — 교정은 2회까지
TEXT_SCHEMA = "text"                 # 글 출력 노드의 봉투 schema_id (계약 v2.1 §5.5)
JSON_SCHEMA = "json"                 # JSON 출력 노드의 봉투 schema_id — 모양은 노드의 example
_FENCE = re.compile(r"```(?:json)?\s*(.+?)\s*```", re.S)


@dataclass
class NodeRun:
    """노드 한 개의 실행 결과."""

    ok: bool
    envelope: dict[str, Any]
    payload: dict[str, Any] | None = None
    repair_count: int = 0
    attempts: list[str] = field(default_factory=list)   # 각 시도의 실패 사유(성공은 빈 칸)

    @property
    def error(self) -> dict[str, Any] | None:
        return self.envelope.get("error")


def check_input(example: dict | None, payload: Any) -> str | None:
    """진입 노드의 사용자 입력이 입력 예시(`input.example`)의 모양인지. 맞으면 None, 아니면 사유.

    여기서 실패하면 **수요자 책임**이다 — 입력한 쪽이 틀린 것이다(PAY-011). 예시가 없으면 검사하지 않는다.
    """
    if not example:
        return None
    if not isinstance(payload, dict):
        return "입력이 JSON 객체가 아닙니다"
    found = shape.problems(payload, example)
    return " / ".join(found) if found else None


def run_llm_node(
    llm: LLM,
    *,
    run_id: str,
    node_id: str,
    example: dict,
    system: str,
    message: str,
    mock_overrides: dict[str, Any] | None = None,
    attachments: list[tuple[bytes, str, str]] | None = None,
) -> NodeRun:
    """노드 하나를 실행해 봉투를 돌려준다. 예외를 던지지 않는다.

    `example` 은 판매자가 준 출력 JSON 예시다. `mock_overrides` 는 mock 실행에서 예시 위에 덮을
    값(`{"점.경로": 값}`)이다 — 분기 기준값을 바꿔 양쪽 경로를 모두 돌려 보는 데 쓴다.
    """
    response_schema = shape.response_schema(example)
    mock_json = shape.mock(example, mock_overrides) if llm.mock else None

    tokens_in = tokens_out = duration = retries = 0
    model = "unknown"
    attempts: list[str] = []
    user = message

    for attempt in range(MAX_REPAIRS + 1):
        try:
            res = llm.generate(system=system, user=user, label=node_id,
                               response_schema=response_schema, mock_json=mock_json,
                               **_attach(attachments))
        except Exception as e:
            # 교정 호출에서 타임아웃 · API 오류가 나도 앞 호출의 토큰은 썼다.
            # 백엔드 환불 계산의 입력이므로 예외에 실어 올린다(runner 가 실패 항목에 기록)
            if attempt > 0:
                e.partial_meta = envelope.meta(
                    model, input_tokens=tokens_in, output_tokens=tokens_out, duration_ms=duration,
                    retry_count=retries, repair_count=attempt,
                    cost_usd=pricing.cost_usd(model, tokens_in, tokens_out))
            raise
        model = res.model
        tokens_in += res.input_tokens
        tokens_out += res.output_tokens
        duration += res.duration_ms
        retries += res.retries

        reason = None
        payload = _loads(res.text)
        if payload is None:
            reason = "JSON 객체를 찾지 못했습니다"
        else:
            found = shape.problems(payload, example)
            if found:
                reason = " / ".join(found)
            else:
                attempts.append("")
                meta = envelope.meta(model, input_tokens=tokens_in, output_tokens=tokens_out,
                                     duration_ms=duration, retry_count=retries, repair_count=attempt,
                                     cost_usd=pricing.cost_usd(model, tokens_in, tokens_out))
                return NodeRun(ok=True, payload=payload,
                               envelope=envelope.build(run_id, node_id, JSON_SCHEMA, payload, meta),
                               repair_count=attempt, attempts=attempts)

        attempts.append(reason)
        if attempt < MAX_REPAIRS:
            user = _repair_message(message, res.text, reason)

    # 실패해도 토큰은 썼다. 원가는 기록하고 정산에서 환원 여부를 가린다
    meta = envelope.meta(model, input_tokens=tokens_in, output_tokens=tokens_out,
                         duration_ms=duration, retry_count=retries, repair_count=MAX_REPAIRS,
                         cost_usd=pricing.cost_usd(model, tokens_in, tokens_out))
    return NodeRun(
        ok=False,
        envelope=envelope.failed(run_id, node_id, JSON_SCHEMA,
                                 code="SCHEMA_VALIDATION_FAILED",
                                 message=f"교정 {MAX_REPAIRS}회 후에도 형식을 만족하지 못했습니다 — {attempts[-1]}",
                                 fault="supplier", meta_=meta),
        repair_count=MAX_REPAIRS,
        attempts=attempts,
    )


def run_text_node(llm: LLM, *, run_id: str, node_id: str, system: str, message: str,
                  attachments: list[tuple[bytes, str, str]] | None = None) -> NodeRun:
    """글 출력 노드 — 구조화 없이 한 번 부르고 `{"text": …}` 를 봉투에 담는다(계약 v2.0 §5.5).

    검증 · 교정이 없으므로 `repair_count` 는 항상 0 이다. 모델 API 오류는 호출자가 잡는다.
    """
    res = llm.generate(system=system, user=message, label=node_id, **_attach(attachments))
    meta = envelope.meta(res.model, input_tokens=res.input_tokens, output_tokens=res.output_tokens,
                         duration_ms=res.duration_ms, retry_count=res.retries, repair_count=0,
                         cost_usd=pricing.cost_usd(res.model, res.input_tokens, res.output_tokens))
    payload = {"text": res.text or ""}
    return NodeRun(ok=True, payload=payload,
                   envelope=envelope.build(run_id, node_id, TEXT_SCHEMA, payload, meta),
                   attempts=[""])


def _attach(attachments: list[tuple[bytes, str, str]] | None) -> dict[str, Any]:
    """첨부가 있을 때만 kwargs 로. 없으면 어댑터 시그니처를 건드리지 않는다."""
    return {"attachments": attachments} if attachments else {}


def _loads(text: str) -> dict[str, Any] | None:
    """모델 응답에서 JSON 객체를 꺼낸다. 코드블록으로 감싸 온 경우까지만 관용한다."""
    for candidate in (text, (_FENCE.search(text or "") or [None, None])[1]):
        if not candidate:
            continue
        try:
            value = json.loads(candidate)
        except (json.JSONDecodeError, TypeError):
            continue
        if isinstance(value, dict):
            return value
    return None


def _repair_message(original: str, bad_output: str, reason: str) -> str:
    """교정 요청. 무엇이 틀렸는지만 알려 주고 형식은 다시 스키마로 강제한다."""
    return (
        f"{original}\n\n"
        "# 직전 응답이 형식에 맞지 않았다\n"
        f"어긋난 곳: {reason}\n"
        "같은 내용을 유지하되 형식만 고쳐서 JSON 객체 하나만 다시 출력한다.\n\n"
        "## 직전 응답\n"
        f"{(bad_output or '')[:2000]}"
    )
