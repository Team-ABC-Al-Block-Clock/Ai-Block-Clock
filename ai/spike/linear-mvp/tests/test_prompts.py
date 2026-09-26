"""트랙 B 프롬프트 형식 해석 — 마커 3블록 · 자리표시자 · 견본 연결.

B 의 준수율 검사기와 같은 규칙으로 렌더돼야 한다. 다르면 준수율 수치가 실행과 어긋난다.
"""
from __future__ import annotations

import json
from pathlib import Path

import pytest

from src import prompts, runner

ROOT = Path(__file__).resolve().parents[1]
BRIEF = {"brand_name": "초록소반", "category": "간편식", "target": "1인 가구 30대",
         "price_tier": "중가", "differentiator": "국내산 재료"}

SAMPLE = """# 설명 문서

이 줄은 모델에 들어가지 않는다.

<!-- prompt:system -->
```
당신은 분석가다.
```

<!-- prompt:user -->
```
[브랜드명]
{brand_name}

[시장]
{market}

[요청]
{comment}
```

<!-- prompt:fewshot -->
```
예시 하나.
```
"""


# --- 해석 --------------------------------------------------------------------

def test_마커_3블록을_뽑고_설명_문서는_버린다():
    p = prompts.parse(SAMPLE)
    assert p.system == "당신은 분석가다."
    assert p.templated and "{brand_name}" in p.user_template
    assert p.fewshot == "예시 하나."
    assert "이 줄은 모델에 들어가지 않는다" not in p.system


def test_마커가_없으면_전체가_시스템_프롬프트다():
    p = prompts.parse("그냥 평문 프롬프트")
    assert p.system == "그냥 평문 프롬프트" and not p.templated


def test_자리표시자_목록():
    assert prompts.parse(SAMPLE).placeholders() == ["brand_name", "comment", "market"]


# --- 렌더 (B 검사기와 같은 규칙) ------------------------------------------------

def test_dict_는_JSON_으로_comment_없으면_없음():
    user = prompts.parse(SAMPLE).render_user({"brand_name": "초록소반", "market": {"level": "high"}})
    assert "초록소반" in user
    assert '{"level": "high"}' in user
    assert "[요청]\n없음" in user
    assert user.endswith("예시 하나.")                     # fewshot 은 뒤에 붙는다


def test_요구하는_값이_없으면_누락_오류():
    with pytest.raises(prompts.PlaceholderMissing) as e:
        prompts.parse(SAMPLE).render_user({"brand_name": "x"})
    assert e.value.names == ["market"]


def test_값_우선순위_브리프_받은것_input_코멘트():
    values = prompts.values_for(
        {"approach": "차별화", "positioning": "P"}, input_payload=BRIEF, comment="가을 신제품")
    assert values["brand_name"] == "초록소반"               # 1. 브리프
    assert values["approach"] == "차별화"                   # 2. 받은 dict 의 키
    assert values["input"]["positioning"] == "P"           # 3. {input} = 받은 것 전체
    assert values["comment"] == "가을 신제품"               # 4. 코멘트


# --- 견본 연결 ----------------------------------------------------------------

def test_견본_3개가_트랙_B_프롬프트_파일을_가리키고_전부_존재한다():
    for wf_id in ("brand_agent", "brand_campaign"):
        wf = runner.load_workflow(wf_id)
        nodes = []
        for n in wf["nodes"]:
            nodes += (n.get("agent") or {}).get("nodes") or [n]
        for n in nodes:
            if n.get("type") == "branch":
                continue
            assert n["prompt"].startswith("../../prompts/brand/0"), (wf_id, n["id"])
            assert (ROOT / n["prompt"]).exists(), n["prompt"]


def test_에이전트_안의_견본_프롬프트도_파일에서_읽힌다():
    wf = runner.resolve_prompts(runner.load_workflow("brand_campaign"))
    inner = wf["nodes"][0]["agent"]["nodes"][0]
    assert "<!-- prompt:system -->" in inner["prompt"]      # 경로가 아니라 본문이 들어왔다


def test_실행하면_user_블록이_브리프_값으로_채워진다():
    run = runner.execute(runner.load_workflow("brand_agent"), comment="가을 신제품",
                         mock=True, input_payload=BRIEF)
    assert run["status"] == "succeeded"
    out = Path(run["out_dir"]) / "nodes"
    n1 = (out / "n1.request.md").read_text(encoding="utf-8")
    assert "초록소반" in n1 and "가을 신제품" in n1
    assert "{brand_name}" not in n1                       # 자리표시자가 남지 않는다
    assert "<!-- prompt:" not in n1                        # 마커 · 설명 문서가 모델에 가지 않는다
    n4 = (out / "n4.request.md").read_text(encoding="utf-8")
    assert '"approach"' in n4                              # {strategy_draft} 에 상위 payload 가 JSON 으로


def test_병합_받는_노드는_port_이름으로_채워진다():
    run = runner.execute(runner.load_workflow("brand_agent"), mock=True, input_payload=BRIEF)
    n3a = (Path(run["out_dir"]) / "nodes" / "n3a.request.md").read_text(encoding="utf-8")
    assert '"competition_level"' in n3a and '"strengths"' in n3a   # {market} · {product}


def test_자리표시자를_못_채우면_공급자_책임으로_실패한다():
    wf = runner.load_workflow("brand_agent")
    wf["nodes"][0]["prompt"] = SAMPLE.replace("{market}", "{없는값}")   # n1 이 없는 값을 요구
    run = runner.execute(wf, mock=True, input_payload=BRIEF)
    assert run["status"] == "failed"
    assert run["error"]["code"] == "PROMPT_TEMPLATE_INVALID"
    assert run["error"]["fault"] == "supplier"
    assert "없는값" in run["error"]["detail"]
