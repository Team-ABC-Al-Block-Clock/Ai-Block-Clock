"""참고 파일(계약 §7.1 `ref_files`)과 실패 노드의 부분 토큰(§8.5).

판매자가 노드에 붙인 참고 자료는 텍스트 계열이면 "## 참고 자료" 절로, PDF 면 Gemini inline 첨부로
간다. 형식 · 크기 위반은 REF_FILE_REJECTED · supplier, 상한 초과는 INPUT_TOO_LARGE · platform.
교정 호출 중 예외가 나도 앞 호출의 토큰 · USD 는 실패 항목에 남는다 — 백엔드 환불 계산의 입력이다.
"""
from __future__ import annotations

import json

import pytest
from fastapi.testclient import TestClient

from src import nodes, runner
from src.api import app
from src.llm import LLMResult
from tests.test_nodes import MARKET_EXAMPLE, FakeLLM
from tests.test_output import BRIEF


def _memo_wf() -> dict:
    return {"id": "memo", "name": "검토 메모",
            "nodes": [{"id": "memo", "name": "검토 메모", "prompt": "브리프를 검토한다",
                       "output": {"kind": "text"}, "owner_id": "u_law"}],
            "edges": [], "output": {"type": "pdf", "title": "메모"}}


class RecordingLLM:
    """첨부를 기록하고 글을 돌려주는 가짜 모델."""

    mock = False

    def __init__(self, **_):
        self.calls: list[dict] = []

    def generate(self, system, user, label="", response_schema=None, mock_json=None, attachments=None):
        self.calls.append({"user": user, "attachments": attachments or []})
        return LLMResult(text="검토 결과", model="fake", input_tokens=10, output_tokens=5, duration_ms=1)


def _request_md(run: dict, node_id: str) -> str:
    return (runner.OUT_ROOT / run["run_id"] / "nodes" / f"{node_id}.request.md").read_text(encoding="utf-8")


def test_텍스트_참고_파일은_메시지의_절로_들어간다(tmp_path):
    ref = tmp_path / "체크리스트.md"
    ref.write_text("- 상표 등록 여부\n- 유사 상표 조사", encoding="utf-8")
    run = runner.execute(_memo_wf(), mock=True, comment="칫솔", ref_files={"memo": [ref]})
    assert run["status"] == "succeeded"
    req = _request_md(run, "memo")
    assert "## 참고 자료" in req and "### 체크리스트.md" in req and "유사 상표 조사" in req
    assert req.index("## 입력") < req.index("## 참고 자료")          # 입력 뒤에 참고 자료


def test_PDF_는_첨부로_넘어간다(tmp_path, monkeypatch):
    rec = RecordingLLM()
    monkeypatch.setattr(runner, "LLM", lambda **kw: rec)
    pdf = tmp_path / "브랜드 가이드.pdf"
    pdf.write_bytes(b"%PDF-1.4 fake")
    run = runner.execute(_memo_wf(), comment="칫솔", ref_files={"memo": [pdf]})
    assert run["status"] == "succeeded"
    (data, mime, name), = rec.calls[0]["attachments"]
    assert mime == "application/pdf" and name == "브랜드 가이드.pdf" and data.startswith(b"%PDF")
    assert "브랜드 가이드.pdf" in rec.calls[0]["user"]                # 본문에도 첨부 사실을 적는다


def test_허용하지_않는_형식은_공급자_책임으로_실패(tmp_path):
    bad = tmp_path / "표.xlsx"
    bad.write_bytes(b"x")
    run = runner.execute(_memo_wf(), mock=True, ref_files={"memo": [bad]})
    assert run["status"] == "failed" and run["error"]["code"] == "REF_FILE_REJECTED"
    assert run["failure"]["fault"] == "supplier" and run["nodes"][0]["status"] == "failed"


def test_노드당_합계_상한을_넘으면_거부(tmp_path, monkeypatch):
    monkeypatch.setattr(runner, "MAX_REF_BYTES", 100)
    a, b = tmp_path / "a.txt", tmp_path / "b.txt"
    a.write_bytes(b"x" * 60)
    b.write_bytes(b"x" * 60)
    run = runner.execute(_memo_wf(), mock=True, ref_files={"memo": [a, b]})
    assert run["error"]["code"] == "REF_FILE_REJECTED" and "b.txt" in run["error"]["detail"]


def test_참고_파일도_입력_상한에_들어간다(tmp_path, monkeypatch):
    monkeypatch.setattr(runner, "MAX_INPUT_CHARS", 200)
    ref = tmp_path / "긴글.txt"
    ref.write_text("가" * 500, encoding="utf-8")
    run = runner.execute(_memo_wf(), mock=True, ref_files={"memo": [ref]})
    assert run["error"]["code"] == "INPUT_TOO_LARGE" and run["failure"]["fault"] == "platform"


def test_참고_파일이_없는_노드는_그대로_돈다():
    run = runner.execute(_memo_wf(), mock=True, comment="칫솔", ref_files={"other": ["없는파일.md"]})
    assert run["status"] == "succeeded" and "## 참고 자료" not in _request_md(run, "memo")


def test_API_는_ref_file_필드를_노드별로_묶는다():
    client = TestClient(app)
    res = client.post(
        "/v1/runs",
        data={"workflow": json.dumps(_memo_wf(), ensure_ascii=False), "mock": "true"},
        files={"ref_file_memo_0": ("가이드.md", "## 브랜드 가이드\n색은 초록".encode("utf-8"), "text/markdown")},
    )
    assert res.status_code == 202
    run_id = res.json()["run_id"]
    assert client.get(f"/v1/runs/{run_id}").json()["status"] == "succeeded"
    req = (runner.OUT_ROOT / run_id / "nodes" / "memo.request.md").read_text(encoding="utf-8")
    assert "### 가이드.md" in req and "색은 초록" in req


# ── 작업 A · 교정 중 예외 → 앞 호출의 토큰 보존 ──────────────────────────────

class FlakyLLM(FakeLLM):
    """첫 호출은 답하고 두 번째 호출(교정)에서 타임아웃을 낸다."""

    def generate(self, *a, **k):
        if len(self.calls) == 1:
            self.calls.append({"raised": True})
            raise TimeoutError("deadline exceeded: timeout")
        return super().generate(*a, **k)


def test_교정_중_예외가_나도_앞_호출의_토큰이_예외에_실린다():
    llm = FlakyLLM("이건 JSON 이 아님", "unused")
    with pytest.raises(TimeoutError) as ei:
        nodes.run_llm_node(llm, run_id="r", node_id="n1", example=MARKET_EXAMPLE,
                           system="s", message="m")
    meta = ei.value.partial_meta
    assert meta["usage"] == {"input_tokens": 10, "output_tokens": 20} and meta["repair_count"] == 1
    assert meta["cost_usd"] > 0


def test_첫_호출부터_예외면_부분_토큰이_없다():
    class Dead(FakeLLM):
        def generate(self, *a, **k):
            raise TimeoutError("timeout")
    with pytest.raises(TimeoutError) as ei:
        nodes.run_llm_node(Dead(), run_id="r", node_id="n1", example=MARKET_EXAMPLE,
                           system="s", message="m")
    assert not hasattr(ei.value, "partial_meta")


def test_실패_노드_항목과_totals_에_부분_토큰과_USD_가_실린다(monkeypatch):
    llm = FlakyLLM("이건 JSON 이 아님", "unused")
    monkeypatch.setattr(runner, "LLM", lambda **kw: llm)
    run = runner.execute(runner.load_workflow("brand_agent"), input_payload=BRIEF)
    assert run["status"] == "failed" and run["error"]["code"] == "MODEL_TIMEOUT"
    failed = next(n for n in run["nodes"] if n["status"] == "failed")
    assert failed["input_tokens"] == 10 and failed["output_tokens"] == 20
    assert failed["cost_usd"] > 0 and failed["repair_count"] == 1 and failed["model"] == "fake"
    assert run["totals"]["input_tokens"] == 10 and run["failure"]["fault"] == "supplier"
