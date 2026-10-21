"""프롬프트 원문은 어떤 응답에도 나가지 않는다 — SEC-001 · 설계 원칙 2 · 티켓 347.

판매자 프롬프트에 표지 문자열을 심고, 백엔드가 받는 모든 것(RunEvent · 폴링 · 결과 JSON · 결과 볼륨 · 검증 · 실패)을
뒤져 표지가 없음을 확인한다. mock 이라 LLM 이 프롬프트를 되풀이하는 경우는 여기서 다루지 않는다 —
그건 모델의 출력이고, 이 테스트는 **Runtime 이 스스로 흘리지 않는가**를 본다.
"""
from __future__ import annotations

import json

from fastapi.testclient import TestClient

from src import messaging, runner, shape
from src.api import app
from tests.messaging_support import FakePublisher, submit, submit_message
from tests.test_output import BRIEF

SYSTEM_MARK = "SENTINEL-SYS-7f3a 판매자의 비밀 노하우"
USER_MARK = "SENTINEL-USER-91c2 비공개 입력 틀"


def _wf() -> dict:
    templated = (f"<!-- prompt:system -->\n```\n{SYSTEM_MARK}\n```\n"
                 f"<!-- prompt:user -->\n```\n{USER_MARK} {{brand_name}}\n```\n")
    return {"id": "leak", "name": "비노출 점검",
            "nodes": [
                {"id": "a", "name": "시장 조사", "prompt": templated, "input": {"example": shape.LEGACY_EXAMPLES["biz.brand_brief@1"]},
                 "output": {"kind": "json", "example": shape.LEGACY_EXAMPLES["biz.market_research@1"]}, "owner_id": "u_a"},
                {"id": "b", "name": "메모", "prompt": f"{SYSTEM_MARK} — 평문 프롬프트",
                 "output": {"kind": "text"}, "owner_id": "u_b"},
                {"id": "br", "name": "분기", "type": "branch",
                 "condition": {"left": "a.competition_level", "op": "==", "right": "high"}},
                {"id": "c", "name": "보고서", "prompt": f"{SYSTEM_MARK} 보고서", "owner_id": "u_c",
                 "output": {"kind": "json", "example": shape.LEGACY_EXAMPLES["biz.brand_strategy@1"]}},
                {"id": "d", "name": "대안", "prompt": f"{SYSTEM_MARK} 대안", "owner_id": "u_c",
                 "output": {"kind": "text"}},
            ],
            "edges": [{"from": "a", "to": "b"}, {"from": "b", "to": "br"},
                      {"from": "br", "to": "c", "when": True}, {"from": "br", "to": "d", "when": False}],
            "output": {"type": "pdf", "title": "비노출"}}


def _assert_clean(label: str, body) -> None:
    text = body if isinstance(body, str) else json.dumps(body, ensure_ascii=False)
    assert SYSTEM_MARK not in text and USER_MARK not in text, f"{label} 에 프롬프트가 섞였다"


def _assert_events_clean(publisher: FakePublisher) -> None:
    assert publisher.published, "발행된 이벤트가 없다"
    for _, event, message_id in publisher.published:
        _assert_clean(f"RunEvent {message_id}", event)


def test_성공_실행의_모든_응답에_프롬프트가_없다():
    client = TestClient(app)
    wf = _wf()
    v = client.post("/v1/workflows/validate", json={"workflow": wf})
    _assert_clean("validate", v.json())
    message = submit_message(wf, input=BRIEF)
    run_id = message["execution_id"]

    outcome, publisher = submit(message)

    assert outcome == "ack" and publisher.types()[-1] == "succeeded"
    _assert_events_clean(publisher)                             # accepted · progress · succeeded(노드 출력 · 봉투)
    run = client.get(f"/v1/runs/{run_id}?include_node_output=true").json()
    assert run["status"] == "succeeded"
    _assert_clean("폴링(노드 출력 포함)", run)
    _assert_clean("artifact", client.get(f"/v1/runs/{run_id}/artifact").json())
    for name in ("artifact.json", "report.html"):
        _assert_clean(f"결과 볼륨 {name}", (messaging.RESULTS_ROOT / run_id / name).read_text(encoding="utf-8"))


def test_실패_응답에도_프롬프트가_없다():
    client = TestClient(app)
    wf = _wf()
    wf["nodes"][0]["prompt"] = wf["nodes"][0]["prompt"].replace("{brand_name}", "{없는_자리표시자}")
    message = submit_message(wf, input=BRIEF)

    outcome, publisher = submit(message)

    run = client.get(f"/v1/runs/{message['execution_id']}?include_node_output=true").json()
    assert outcome == "ack" and publisher.types()[-1] == "failed"
    assert run["status"] == "failed" and run["error"]["code"] == "PROMPT_TEMPLATE_INVALID"
    _assert_events_clean(publisher)
    _assert_clean("실패 응답", run)


def test_정의_오류_응답에도_프롬프트가_없다():
    client = TestClient(app)
    wf = _wf()
    wf["nodes"][2]["condition"]["left"] = "b.text"            # 글 노드로 분기 → 저장 거부
    v = client.post("/v1/workflows/validate", json={"workflow": wf}).json()
    assert not v["valid"]
    _assert_clean("validate 오류", v)

    outcome, publisher = submit(submit_message(wf))

    assert outcome == "ack" and publisher.types() == ["rejected"]
    _assert_events_clean(publisher)


def test_러너_결과_사본에도_프롬프트가_없다():
    """`out/<run_id>/run.json` 은 서버 임시 파일이지만 응답의 원본이므로 같이 본다.
    노드별 `*.request.md` 는 디버그용으로 프롬프트를 담는다 — 응답 · API 로 내보내지 않는다."""
    run = runner.execute(_wf(), mock=True, input_payload=BRIEF)
    _assert_clean("run.json", (runner.OUT_ROOT / run["run_id"] / "run.json").read_text(encoding="utf-8"))
