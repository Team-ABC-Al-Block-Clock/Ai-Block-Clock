"""사용자가 올리는 파일(계약 §7.1 `file`) — 표 · 글 · PDF · 사진, 여러 개, 입력란과 함께, 모든 LLM 노드에.

판매자 참고 파일도 같은 규칙으로 사진 · 엑셀을 받는다. 위반 책임은 올린 쪽 — 사용자 파일은 consumer,
참고 파일은 supplier.
"""
from __future__ import annotations

from fastapi.testclient import TestClient

from src import runner
from src.api import app
from src.llm import LLMResult
from tests.messaging_support import submit, submit_message

PNG = bytes.fromhex("89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4890000000d4944415478da63f8ff"
                    "ff3f0300050001019cc3a0e70000000049454e44ae426082")            # 1×1 투명 PNG


class RecordingLLM:
    mock = False

    def __init__(self, **_):
        self.calls: list[dict] = []

    def generate(self, system, user, label="", response_schema=None, mock_json=None, attachments=None):
        self.calls.append({"label": label, "user": user, "attachments": attachments or []})
        return LLMResult(text="결과", model="fake", input_tokens=10, output_tokens=5, duration_ms=1)


def _two_text_nodes() -> dict:
    return {"id": "two", "name": "글 두 노드",
            "nodes": [{"id": "a", "name": "요약", "prompt": "요약한다", "owner_id": "u",
                       "input": {"example": {"brand_name": "x", "comment": None}}, "output": {"kind": "text"}},
                      {"id": "b", "name": "카피", "prompt": "카피를 쓴다", "owner_id": "u", "output": {"kind": "text"}}],
            "edges": [{"from": "a", "to": "b"}], "output": {"type": "pdf", "title": "t"}}


def _request(run: dict, node_id: str) -> str:
    return (runner.OUT_ROOT / run["run_id"] / "nodes" / f"{node_id}.request.md").read_text(encoding="utf-8")


def test_입력란을_채워도_올린_표가_같이_들어간다():
    run = runner.execute(_two_text_nodes(), mock=True, input_payload={"brand_name": "소소칫솔"},
                         files=["data/demo_scores.xlsx"])
    assert run["status"] == "succeeded"
    req = _request(run, "a")
    assert '"brand_name": "소소칫솔"' in req and "## 올린 파일" in req and "### demo_scores.xlsx" in req and "이름" in req
    assert run["inputs"]["files"] == ["demo_scores.xlsx"]


def test_올린_파일은_모든_LLM_노드에_간다(tmp_path, monkeypatch):
    rec = RecordingLLM()
    monkeypatch.setattr(runner, "LLM", lambda **kw: rec)
    photo = tmp_path / "시제품.png"
    photo.write_bytes(PNG)
    note = tmp_path / "메모.md"
    note.write_text("손잡이는 대나무", encoding="utf-8")
    run = runner.execute(_two_text_nodes(), input_payload={"brand_name": "x"}, files=[photo, note])
    assert run["status"] == "succeeded"
    for call in rec.calls:                                   # a · b 둘 다
        assert [(m, n) for _, m, n in call["attachments"]] == [("image/png", "시제품.png")]
        assert "## 올린 파일" in call["user"] and "손잡이는 대나무" in call["user"] and "(사진 첨부" in call["user"]
    assert "- 시제품.png (image/png)" in _request(run, "b")


def test_자리표시자_프롬프트에도_올린_파일이_붙는다(tmp_path):
    wf = _two_text_nodes()
    wf["nodes"][0]["prompt"] = "<!-- prompt:system -->\n```\n요약가\n```\n<!-- prompt:user -->\n```\n브랜드 {brand_name}\n```\n"
    note = tmp_path / "요청.txt"
    note.write_text("포장은 종이", encoding="utf-8")
    run = runner.execute(wf, mock=True, input_payload={"brand_name": "x"}, files=[note])
    assert run["status"] == "succeeded"
    req = _request(run, "a")
    assert "브랜드 x" in req and "## 올린 파일\n### 요청.txt\n포장은 종이" in req


def test_허용하지_않는_사용자_파일은_수요자_책임(tmp_path):
    bad = tmp_path / "기획.docx"
    bad.write_bytes(b"x")
    run = runner.execute(_two_text_nodes(), mock=True, input_payload={"brand_name": "x"}, files=[bad])
    assert run["status"] == "failed" and run["error"]["code"] == "FILE_PARSE_FAILED"
    assert run["failure"]["fault"] == "consumer" and "docx" in run["error"]["detail"]


def test_메시지는_files_를_여러_개_받고_형식을_거른다(volumes):
    client = TestClient(app)
    folder = volumes.store / "executions" / "e1"                       # 백엔드 file-store 에 둔 사용자 파일
    folder.mkdir(parents=True)
    (folder / "시제품.png").write_bytes(PNG)
    (folder / "메모.md").write_text("색은 초록", encoding="utf-8")
    (folder / "기획.docx").write_bytes(b"x")
    ok = submit_message(_two_text_nodes(), input={"brand_name": "x"},
                        files=["executions/e1/시제품.png", "executions/e1/메모.md"])
    bad = submit_message(_two_text_nodes(), files=["executions/e1/기획.docx"])
    run_id = ok["execution_id"]

    ok_outcome, _ = submit(ok)
    bad_outcome, bad_publisher = submit(bad)

    assert ok_outcome == "ack"
    assert client.get(f"/v1/runs/{run_id}").json()["status"] == "succeeded"
    req = (runner.OUT_ROOT / run_id / "nodes" / "b.request.md").read_text(encoding="utf-8")
    assert "### 메모.md\n색은 초록" in req and "- 시제품.png (image/png)" in req
    assert bad_outcome == "ack" and bad_publisher.types() == ["rejected"]
    assert bad_publisher.last()["snapshot"]["error"]["code"] == "UNSUPPORTED_FILE_TYPE"


def test_참고_파일도_사진과_엑셀을_받는다(tmp_path, monkeypatch):
    rec = RecordingLLM()
    monkeypatch.setattr(runner, "LLM", lambda **kw: rec)
    guide = tmp_path / "가이드.png"
    guide.write_bytes(PNG)
    run = runner.execute(_two_text_nodes(), input_payload={"brand_name": "x"},
                         ref_files={"b": [guide, "data/demo_scores.xlsx"]})
    assert run["status"] == "succeeded"
    a, b = rec.calls
    assert a["attachments"] == []                                          # a 에는 안 간다 — 노드별 참고 파일
    assert [(m, n) for _, m, n in b["attachments"]] == [("image/png", "가이드.png")]
    assert "## 참고 자료" in b["user"] and "### demo_scores.xlsx" in b["user"] and "이름" in b["user"]


def test_사진은_입력_상한에_천_자로_센다(tmp_path, monkeypatch):
    monkeypatch.setattr(runner, "MAX_INPUT_CHARS", 900)
    photo = tmp_path / "p.png"
    photo.write_bytes(PNG)
    run = runner.execute(_two_text_nodes(), mock=True, input_payload={"brand_name": "x"}, files=[photo])
    assert run["error"]["code"] == "INPUT_TOO_LARGE" and run["failure"]["fault"] == "platform"
