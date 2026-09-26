"""출력 모양(`src/shape`) — JSON 예시 하나로 스키마 · 검사 · 분기 필드 · mock · 문서. 계약 v2.1 §5.5."""
from __future__ import annotations

from src import shape

EX = {"제목": "브랜드 전략", "점수": 7, "확정": True, "항목": [{"이름": "a", "값": 1}], "태그": ["x"], "메모": None}


def test_예시에서_모양을_만든다():
    s = shape.schema_of(EX)
    assert s["type"] == "object" and s["required"] == [k for k in EX if k != "메모"]   # null = 선택 항목
    assert s["properties"]["점수"] == {"type": "number"} and s["properties"]["확정"] == {"type": "boolean"}
    assert s["properties"]["항목"]["items"]["properties"]["이름"] == {"type": "string"}
    assert s["properties"]["메모"] == {"type": "string", "nullable": True}
    assert shape.schema_of({"빈목록": []})["properties"]["빈목록"] == {"type": "array", "items": {"type": "string"}}


def test_응답_스키마는_키_순서를_고정한다():
    r = shape.response_schema(EX)
    assert r["propertyOrdering"] == list(EX)
    assert r["properties"]["항목"]["items"]["propertyOrdering"] == ["이름", "값"]


def test_모양_검사는_빠진_키와_타입을_잡는다():
    ok = {**EX, "덤": 1}
    assert shape.problems(ok, EX) == []
    bad = {"제목": 3, "확정": "예", "항목": [{"이름": "a"}], "태그": [], "메모": None}
    found = shape.problems(bad, EX)
    assert any("제목" in f and "string" in f for f in found)
    assert any(f.startswith("점수: 없습니다") for f in found)
    assert any("확정" in f and "boolean" in f for f in found)
    assert any("항목.0.값" in f for f in found)
    assert len(found) <= 5


def test_참거짓은_숫자가_아니다():
    assert shape.problems({"n": True}, {"n": 1}) == ["n: number 이어야 하는데 boolean 입니다"]


def test_예시_자체를_검사한다():
    assert shape.check_example(EX) is None
    assert shape.check_example({}) and shape.check_example([1]) and shape.check_example("x")
    assert "빈 객체" in shape.check_example({"a": {}})
    deep = {"a": {"b": {"c": {"d": {"e": {"f": 1}}}}}}
    assert "중첩" in shape.check_example(deep)


def test_분기_필드_종류():
    assert shape.field_kind(EX, "제목") == "string"
    assert shape.field_kind(EX, "점수") == "number"
    assert shape.field_kind(EX, "항목") == "array"
    assert shape.field_kind(EX, "항목.0.이름") == "string"
    assert shape.field_kind(EX, "없는필드") is None


def test_mock_은_예시에_값을_덮는다():
    out = shape.mock(EX, {"점수": 9, "항목.0.이름": "b"})
    assert out["점수"] == 9 and out["항목"][0]["이름"] == "b" and EX["점수"] == 7     # 원본은 그대로


def test_문서는_키를_제목으로_쓴다():
    md = shape.to_markdown({"포지셔닝": "대나무 칫솔", "전략": ["구독", "밀도"], "메시지": {"핵심": "성능 먼저"}})
    assert "## 포지셔닝\n\n대나무 칫솔" in md and "- 구독" in md and "- **핵심**: 성능 먼저" in md


def test_null_로_둔_키는_없어도_된다():
    assert shape.problems({"제목": "x"}, {"제목": "예", "메모": None}) == []
    assert shape.problems({"제목": "x", "메모": 3}, {"제목": "예", "메모": None}) == ["메모: string 이어야 하는데 number 입니다"]
    assert shape.problems({"메모": "x"}, {"제목": "예", "메모": None}) == ["제목: 없습니다"]
