"""서식(템플릿) — 결과 JSON 에 서식을 입힌 HTML · PDF · 디자인 변환. 계약 v2.2 §5.5 · §7.2 · §7.4 · §7.8.

안전망 3중: 자리표시자 대조(빠지면 AI 가 한 번 짝짓기) → StrictUndefined → 기본 서식 폴백.
서식 · PDF 실패는 실행 실패가 아니다.
"""
from __future__ import annotations

import json

from fastapi.testclient import TestClient

from src import render, runner, template_import
from src.api import app
from src.llm import LLM, LLMResult
from tests.messaging_support import submit, submit_message
from tests.test_output import BRIEF

PAYLOAD = {"headline": "매일 쓰는 물건은 성능이 먼저", "positioning": ["대나무 손잡이", "미세모 2배"],
           "messages": {"core": "성능 먼저", "sub": "자연 소재"}, "summary_md": "## 총평\n\n**좋다**", "memo": None}
USER_TPL = """<!DOCTYPE html><html><head><meta charset="utf-8"><style>body{color:#123}</style></head>
<body><h1>{{ headline }}</h1><ul>{% for p in positioning %}<li>{{ p }}</li>{% endfor %}</ul>
<p>{{ messages.core }}</p>{{ summary_md | md }}</body></html>"""


class MapLLM:
    """짝짓기 질문에 정해진 답을 주는 가짜 모델."""
    mock = False

    def __init__(self, answer: dict):
        self.answer, self.calls = answer, []

    def generate(self, system, user, label="", response_schema=None, mock_json=None, attachments=None):
        self.calls.append({"label": label, "user": user, "attachments": attachments or []})
        return LLMResult(text=json.dumps(self.answer, ensure_ascii=False), model="fake", input_tokens=5, output_tokens=5, duration_ms=1)


# --- 기본 서식 ---------------------------------------------------------------

def test_서식이_없으면_기본_문서형으로_키가_제목이_된다():
    r = render.apply(None, PAYLOAD, title="브랜드 전략")
    assert r.applied == "default" and r.template_name == "document"
    assert "<h1>브랜드 전략</h1>" in r.html and "<h2>headline</h2>" in r.html and "<h2>positioning</h2>" in r.html
    assert "<li>대나무 손잡이</li>" in r.html and "<b>core</b>: 성능 먼저" in r.html
    assert "<strong>좋다</strong>" in r.html                 # _md 키는 마크다운으로
    assert "<h2>memo</h2>" not in r.html                    # 비어 있는 선택 항목은 뺀다


def test_카드형_기본_서식과_글_출력():
    r = render.apply({"default": "card"}, PAYLOAD)
    assert r.template_name == "card" and 'class="card"' in r.html
    t = render.apply(None, {"text": "# 메모\n\n본문"}, title="메모")
    assert "<h1>메모</h1>" in t.html and "<p>본문</p>" in t.html and "<h2>" not in t.html.replace("<h1>", "")


# --- 사용자 서식 --------------------------------------------------------------

def test_키가_같으면_AI_없이_사용자_서식에_붓는다():
    r = render.apply({"name": "내 서식", "html": USER_TPL}, PAYLOAD, llm=MapLLM({}))
    assert r.applied == "user" and r.template_name == "내 서식" and r.mapping == {} and r.fallback_reason is None
    assert "<h1>매일 쓰는 물건은 성능이 먼저</h1>" in r.html and "<li>미세모 2배</li>" in r.html and "<strong>좋다</strong>" in r.html


def test_키가_다르면_AI_가_한_번_짝짓는다():
    tpl = USER_TPL.replace("headline", "제목").replace("positioning", "포지셔닝")
    llm = MapLLM({"제목": "headline", "포지셔닝": "positioning"})
    r = render.apply({"name": "한글 서식", "html": tpl}, PAYLOAD, llm=llm)
    assert r.applied == "user" and r.mapping == {"제목": "headline", "포지셔닝": "positioning"}
    assert len(llm.calls) == 1 and llm.calls[0]["label"] == "template-map"
    assert "<h1>매일 쓰는 물건은 성능이 먼저</h1>" in r.html and any("짝지어" in w for w in r.warnings)


def test_못_채운_빈칸은_비워_두고_경고한다():
    tpl = USER_TPL.replace("headline", "없는자리")
    r = render.apply({"name": "이상한 서식", "html": tpl}, PAYLOAD, llm=MapLLM({"없는자리": None}))
    assert r.applied == "user" and "<h1></h1>" in r.html and any("비워" in w for w in r.warnings)


def test_경로와_반복_항목_속성도_짝짓는다():
    tpl = """<!DOCTYPE html><html><body><p>{{ 메시지_핵심 }}</p><span>{{ 발행일 }}</span>
    {% for 항목 in 포지셔닝목록 %}<b>{{ 항목.title }}</b>{% endfor %}
    {% for r in reasons %}<i>{{ r.name }}</i>{% endfor %}</body></html>"""
    payload = {**PAYLOAD, "reasons": [{"axis": "제품", "detail": "대나무"}]}
    llm = MapLLM({"메시지_핵심": "messages.core", "발행일": "date", "포지셔닝목록": "positioning[]",
                  "포지셔닝목록[].title": ".", "reasons[].name": "axis"})
    r = render.apply({"name": "경로 서식", "html": tpl}, payload, llm=llm)
    assert r.applied == "user"
    assert "<p>성능 먼저</p>" in r.html and "<b>대나무 손잡이</b>" in r.html and "<i>제품</i>" in r.html
    assert "<span>20" in r.html
    asked = llm.calls[0]["user"]
    assert "포지셔닝목록[].title" in asked and "reasons[].name" in asked and "messages.core" in asked


def test_스크립트가_든_서식은_거부하고_폴백한다():
    r = render.apply({"name": "위험", "html": USER_TPL + "<script>alert(1)</script>"}, PAYLOAD, llm=MapLLM({}))
    assert r.applied == "default" and "script" in r.fallback_reason.lower()


def test_자리표시자_추출():
    assert render.placeholders(USER_TPL) == {"headline", "positioning", "messages", "summary_md"}
    assert render.placeholders("{{ title }} {{ date }} {% for f in fields %}{{ f.key }}{% endfor %}") == set()
    assert render.placeholders("{% if not 메모 %}없음{% endif %}{% if 제목 and 부제 %}x{% endif %}") == {"메모", "제목"}


# --- 러너 · API ---------------------------------------------------------------

def test_실행_결과에_HTML_과_PDF_가_같이_나온다():
    wf = runner.load_workflow("brand_agent")
    wf["output"]["template"] = {"name": "내 서식", "html": USER_TPL}
    run = runner.execute(wf, mock=True, input_payload=BRIEF)
    assert run["status"] == "succeeded"
    assert run["result"]["html"] and run["result"]["pdf"]
    assert run["result"]["template"]["applied"] == "user"                  # mock 은 빠진 자리를 순서대로 채운다
    html = (runner.OUT_ROOT / run["run_id"] / "report.html").read_text(encoding="utf-8")
    assert html.startswith("<!DOCTYPE html>") and "<h1>" in html


def test_서식_실패는_실행_실패가_아니다():
    wf = runner.load_workflow("report")
    wf["output"]["template"] = {"name": "깨진 서식", "html": "<!DOCTYPE html><html><body>{{ 없음 "}
    run = runner.execute(wf, mock=True, file_path="data/demo_scores.xlsx")
    assert run["status"] == "succeeded" and run["result"]["template"]["applied"] == "default"
    assert any(w["code"] == "TEMPLATE_RENDER_FAILED" for w in run["warnings"]) and run["result"]["pdf"]


def test_API_는_html_url_과_warnings_를_준다():
    client = TestClient(app)
    wf = runner.load_workflow("brand_agent")
    wf["output"]["template"] = {"name": "깨진", "html": "<!DOCTYPE html><html><body>{{ 없음 "}
    message = submit_message(wf, input=BRIEF)
    run_id = message["execution_id"]

    outcome, _ = submit(message)

    assert outcome == "ack"
    body = client.get(f"/v1/runs/{run_id}").json()
    assert body["status"] == "succeeded" and body["result"]["html_url"] == f"/v1/runs/{run_id}/html"
    assert body["result"]["template"]["applied"] == "default" and body["warnings"][0]["code"] == "TEMPLATE_RENDER_FAILED"
    page = client.get(f"/v1/runs/{run_id}/html")
    assert page.status_code == 200 and page.text.startswith("<!DOCTYPE html>") and "text/html" in page.headers["content-type"]


# --- 디자인 → 서식 -------------------------------------------------------------

def test_디자인_변환은_AI_가_준_HTML_을_검사하고_자리표시자를_뽑는다():
    good = "<!DOCTYPE html><html><body><h1>{{ 제목 }}</h1>{% for 항목 in 항목목록 %}<p>{{ 항목 }}</p>{% endfor %}</body></html>"
    llm = MapLLM({})
    llm.generate = lambda system, user, label="", response_schema=None, mock_json=None, attachments=None: (
        llm.calls.append({"label": label, "attachments": attachments}) or
        LLMResult(text="```html\n" + good + "\n```", model="fake", input_tokens=100, output_tokens=200, duration_ms=1))
    out = template_import.convert(llm, [(b"%PDF-1.4", "application/pdf", "디자인.pdf")], hint="표지")
    assert out.html == good and out.placeholders == ["제목", "항목목록"] and out.problems == [] and not out.repaired
    assert llm.calls[0]["label"] == "template-convert" and llm.calls[0]["attachments"][0][2] == "디자인.pdf"


def test_디자인_변환은_문제가_있으면_한_번_고친다():
    bad = "<html><body><h1>고정 제목</h1></body></html>"           # doctype 없음 · 자리표시자 없음
    good = "<!DOCTYPE html><html><body><h1>{{ 제목 }}</h1></body></html>"
    replies = iter([bad, good])
    llm = MapLLM({})
    llm.generate = lambda system, user, label="", **k: LLMResult(text=next(replies), model="fake", input_tokens=1, output_tokens=1, duration_ms=1)
    out = template_import.convert(llm, [(b"x", "image/png", "d.png")])
    assert out.repaired and out.problems == [] and out.placeholders == ["제목"]


def test_변환_API_mock_은_AI_없이_기본_서식을_준다():
    client = TestClient(app)
    res = client.post("/v1/templates/convert", data={"mock": "true", "hint": "보고서"},
                      files=[("design", ("d.png", b"\x89PNG", "image/png"))])
    assert res.status_code == 200 and res.json()["html"].startswith("<!DOCTYPE html>") and "제목" in res.json()["placeholders"]
    bad = client.post("/v1/templates/convert", data={"mock": "true"}, files=[("design", ("d.docx", b"x", "application/octet-stream"))])
    assert bad.status_code == 400 and bad.json()["detail"]["code"] == "UNSUPPORTED_FILE_TYPE"


def test_목록_항목을_통째로_찍으면_값을_이어_보여_준다():
    tpl = "<!DOCTYPE html><html><body>{% for r in reasons %}<li>{{ r }}</li>{% endfor %}{% for p in 목적 %}<b>{{ p }}</b>{% endfor %}</body></html>"
    payload = {"reasons": [{"axis": "제품", "detail": "대나무"}], "purpose": [{"label": "가치"}]}
    r = render.apply({"name": "s", "html": tpl}, payload, llm=MapLLM({"목적": "purpose[]"}))
    assert "<li>제품 · 대나무</li>" in r.html and "<b>가치</b>" in r.html and "{'" not in r.html


def test_날짜_제목_빈칸은_AI_가_비워도_채운다():
    tpl = "<!DOCTYPE html><html><body>{{ 발행일 }}|{{ 제목 }}|{{ 기타 }}</body></html>"
    r = render.apply({"name": "s", "html": tpl}, {"a": 1}, title="T", llm=MapLLM({"발행일": None, "제목": None, "기타": None}))
    assert r.mapping == {"발행일": "date", "제목": "title"} and "|T|" in r.html and "20" in r.html
